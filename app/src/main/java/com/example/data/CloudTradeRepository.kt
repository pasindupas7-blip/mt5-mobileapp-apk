package com.example.data

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.model.AccountInfo
import com.example.model.HistoryTrade
import com.example.model.TradeHitOutcome
import com.example.model.TradePosition
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private fun Double.safeFinite(default: Double = 0.0): Double =
    if (isNaN() || isInfinite()) default else this

private fun round2(value: Double): Double =
    round(value.safeFinite(0.0) * 100.0) / 100.0

private fun Any?.toSafeDouble(default: Double = 0.0): Double {
    val raw = when (this) {
        is Number -> this.toDouble()
        else -> default
    }
    return raw.safeFinite(default)
}

private fun Any?.toSafeLong(default: Long = 0L): Long {
    return when (this) {
        is Number -> this.toLong()
        else -> default
    }
}

private fun resolveUniqueDocDomainId(docId: String, rawTicketId: Any?, fallbackBase: Long): Long {
    val fromTicket = rawTicketId.toSafeLong(0L)
    if (fromTicket > 0L) return fromTicket

    val numericDocId = docId.toLongOrNull()
    if (numericDocId != null && numericDocId > 0L) return numericDocId

    val digitsTail = docId.filter { it.isDigit() }.takeLast(12).toLongOrNull()
    if (digitsTail != null && digitsTail > 0L) return digitsTail

    val positiveHash = (docId.hashCode().toLong() and 0x7FFFFFFFL)
    return if (positiveHash > 0L) fallbackBase + positiveHash else fallbackBase
}

/**
 * Resolves the accurate BUY/SELL direction for a trade.
 * Used by chart, history, and about screens for visual consistency.
 */
fun resolveAccurateTradeIsBuy(
    storedIsBuy: Boolean,
    openPrice: Double,
    closePrice: Double,
    profit: Double,
    sl: Double? = null,
    tp: Double? = null,
    explicitSideCode: String? = null
): Boolean {
    when (explicitSideCode?.trim()?.uppercase(Locale.US)) {
        "SELL", "S", "0", "FALSE", "SHORT" -> return false
        "BUY", "B", "1", "TRUE", "LONG" -> return true
    }
    val safeOpen = openPrice.safeFinite(0.0)
    val safeClose = closePrice.safeFinite(0.0)
    val safeProfit = profit.safeFinite(0.0)
    if (safeOpen > 0.0 && safeClose > 0.0 && abs(safeClose - safeOpen) > 1e-6 && abs(safeProfit) >= 0.005) {
        return if (safeProfit > 0.0) {
            safeClose > safeOpen
        } else {
            safeClose < safeOpen
        }
    }
    val validTp = tp?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
    if (safeOpen > 0.0 && validTp != null && abs(validTp - safeOpen) > 1e-6) {
        return validTp > safeOpen
    }
    val validSl = sl?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
    if (safeOpen > 0.0 && validSl != null && abs(validSl - safeOpen) > 1e-6) {
        return validSl < safeOpen
    }
    return storedIsBuy
}

@Keep
@IgnoreExtraProperties
data class CloudUserAccount(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoURL: String = "",
    val balance: Double = DEFAULT_BALANCE,
    val leverage: Long = DEFAULT_LEVERAGE,
    val currency: String = DEFAULT_CURRENCY,
    val deposit: Double = DEFAULT_BALANCE,
    val realizedProfit: Double = 0.0,
    val totalWithdrawal: Double = 0.0,
    val totalSwap: Double = 0.0,
    val totalCommission: Double = 0.0,
    val accountName: String = "",
    val brokerName: String = "",
    val accountId: String = "",
    val serverName: String = "",
    val isReal: Boolean = true,
    val depositDate: String = "",
    val logoType: String = "AUTO",
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun toDomain(
        equity: Double = balance,
        margin: Double = 0.0,
        freeMargin: Double = balance,
        marginLevel: Double = 0.0,
        floatingPnL: Double = 0.0
    ): AccountInfo {
        val defaults = AccountInfo()
        val displayFallback = displayName.trim().ifBlank {
            email.substringBefore("@").ifBlank { defaults.accountName }
        }
        return AccountInfo(
            key = "main",
            balance = round2(balance),
            equity = round2(equity),
            margin = round2(margin),
            freeMargin = round2(freeMargin),
            marginLevel = round2(marginLevel),
            floatingPnL = round2(floatingPnL),
            accountName = accountName.ifBlank { displayFallback },
            brokerName = brokerName.ifBlank { defaults.brokerName },
            accountId = accountId.ifBlank { defaults.accountId },
            serverName = serverName.ifBlank { defaults.serverName },
            isReal = isReal,
            deposit = round2(deposit.coerceAtLeast(0.0)),
            depositDate = depositDate.ifBlank { SriLankaMarketHours.formatDateTime(createdAtMs) },
            realizedProfit = round2(realizedProfit),
            totalWithdrawal = round2(totalWithdrawal.coerceAtLeast(0.0)),
            totalSwap = round2(totalSwap),
            totalCommission = round2(totalCommission),
            logoType = logoType.ifBlank { "AUTO" }
        )
    }

    companion object {
        const val DEFAULT_BALANCE = 10000.0
        const val DEFAULT_LEVERAGE = 500L
        const val DEFAULT_CURRENCY = "USD"
    }
}

@Keep
data class CloudOpenTradeRecord(
    val docId: String,
    val position: TradePosition,
    val openTimeMs: Long
)

@Keep
data class CloudHistoryDealRecord(
    val docId: String,
    val trade: HistoryTrade,
    val openTimeMs: Long,
    val closeTimeMs: Long
)

/**
 * Clean, unified Cloud Firestore repository for Android & iOS cross-platform sync.
 *
 * Collections:
 * - `users/{uid}`: { email, balance, leverage, currency, createdAt, ... }
 * - `users/{uid}/open_trades/{tradeId}`: { symbol, type, lots, openPrice, sl, tp, commission, swap, openTime }
 * - `users/{uid}/trade_history/{dealId}`: { symbol, type, lots, openPrice, closePrice, profit, commission, swap, netProfit, openTime, closeTime }
 *
 * Free-tier protection:
 * - NEVER writes live/floating P&L or live price ticks to Firestore.
 * - Writes occur ONLY on account init/update, trade open, SL/TP modify, and atomic transaction trade close.
 */
class CloudTradeRepository(
    private val db: FirebaseFirestore
) {
    // CRITICAL: Always initialize Firestore using the provisioned named database ID
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private fun requireUserId(explicitUid: String? = null): String {
        val currentUid = auth.currentUser?.uid
        val resolved = explicitUid?.takeIf { it.isNotBlank() } ?: currentUid
        if (resolved.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in before accessing Firestore.")
        }
        return resolved
    }

    private fun userDocRef(uid: String) = db.collection("users").document(uid)
    private fun openTradesCol(uid: String) = userDocRef(uid).collection("open_trades")
    private fun tradeHistoryCol(uid: String) = userDocRef(uid).collection("trade_history")

    private fun sanitizePhotoUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        return if (trimmed.startsWith("https://", ignoreCase = false) && trimmed.length <= 1024) {
            trimmed
        } else {
            ""
        }
    }

    /**
     * Checks `users/{uid}` in Firestore:
     * - If it does NOT exist (brand-new Google user), creates it with:
     *   `email`, `displayName` (default "Trader"), `photoURL` (default ""), `balance: 10000`,
     *   `leverage: 500`, `currency: "USD"`, and `createdAt: serverTimestamp()`.
     * - If it ALREADY exists (returning user from Android or iOS), preserves their existing
     *   balance/trades and updates `displayName` and `photoURL` only if changed.
     */
    suspend fun ensureUserDocumentExists(
        uid: String,
        email: String?,
        displayName: String? = null,
        photoURL: String? = null
    ): Result<Unit> {
        val safeUid = uid.trim()
        if (safeUid.isEmpty()) return Result.failure(IllegalArgumentException("UID cannot be blank"))
        val path = "users/$safeUid"
        return try {
            val docRef = userDocRef(safeUid)
            val snap = docRef.get().await()
            val safeEmail = email?.trim().orEmpty().take(254)
            val safeDisplayName = displayName?.trim()?.ifBlank { "Trader" }?.take(128) ?: "Trader"
            val safePhotoUrl = sanitizePhotoUrl(photoURL)

            if (!snap.exists()) {
                val payload = mapOf<String, Any>(
                    "email" to safeEmail,
                    "displayName" to safeDisplayName,
                    "photoURL" to safePhotoUrl,
                    "balance" to CloudUserAccount.DEFAULT_BALANCE,
                    "leverage" to CloudUserAccount.DEFAULT_LEVERAGE,
                    "currency" to CloudUserAccount.DEFAULT_CURRENCY,
                    "deposit" to CloudUserAccount.DEFAULT_BALANCE,
                    "realizedProfit" to 0.0,
                    "totalWithdrawal" to 0.0,
                    "totalSwap" to 0.0,
                    "totalCommission" to 0.0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val existingName = snap.getString("displayName").orEmpty()
                val existingPhoto = snap.getString("photoURL").orEmpty()
                val existingEmail = snap.getString("email").orEmpty()
                val updates = linkedMapOf<String, Any>()
                if (!displayName.isNullOrBlank() && safeDisplayName != existingName) {
                    updates["displayName"] = safeDisplayName
                } else if (existingName.isBlank()) {
                    updates["displayName"] = safeDisplayName
                }
                if (safePhotoUrl.isNotEmpty() && safePhotoUrl != existingPhoto) {
                    updates["photoURL"] = safePhotoUrl
                }
                if (safeEmail.isNotEmpty() && existingEmail.isBlank()) {
                    updates["email"] = safeEmail
                }
                if (updates.isNotEmpty()) {
                    updates["updatedAt"] = FieldValue.serverTimestamp()
                    docRef.update(updates).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Real-time listener on `users/{uid}` with safe pending serverTimestamp fallback (`ESTIMATE` + `System.currentTimeMillis()`).
     */
    fun observeUserAccount(uid: String): Flow<CloudUserAccount?> = callbackFlow {
        val safeUid = uid.trim()
        if (safeUid.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val path = "users/$safeUid"
        val registration = userDocRef(safeUid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (auth.currentUser?.uid == safeUid) {
                    handleFirestoreError(error, OperationType.GET, path)
                }
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            try {
                trySend(parseUserAccount(snapshot, safeUid))
            } catch (t: Throwable) {
                handleFirestoreError(t, OperationType.GET, path)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Real-time listener on `users/{uid}/open_trades` ordered by `openTime` DESC.
     * Safely resolves pending serverTimestamp() via ESTIMATE with fallback to System.currentTimeMillis().
     */
    fun observeOpenTrades(uid: String): Flow<List<CloudOpenTradeRecord>> = callbackFlow {
        val safeUid = uid.trim()
        if (safeUid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val path = "users/$safeUid/open_trades"
        val registration = openTradesCol(safeUid)
            .orderBy("openTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser?.uid == safeUid) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    close(error)
                    return@addSnapshotListener
                }
                val records = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        parseOpenTrade(doc)
                    } catch (_: Throwable) {
                        null
                    }
                }.orEmpty()
                trySend(records)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Real-time listener on `users/{uid}/trade_history` ordered by `closeTime` DESC.
     * Safely resolves pending serverTimestamp() via ESTIMATE with fallback to System.currentTimeMillis().
     */
    fun observeTradeHistory(uid: String): Flow<List<CloudHistoryDealRecord>> = callbackFlow {
        val safeUid = uid.trim()
        if (safeUid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val path = "users/$safeUid/trade_history"
        val registration = tradeHistoryCol(safeUid)
            .orderBy("closeTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser?.uid == safeUid) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    close(error)
                    return@addSnapshotListener
                }
                val records = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        parseHistoryDeal(doc)
                    } catch (_: Throwable) {
                        null
                    }
                }.orEmpty()
                trySend(records)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Writes a newly opened trade to `users/{uid}/open_trades/{tradeId}`.
     * Stores strictly numeric values and `openTime: FieldValue.serverTimestamp()`.
     * Never writes floating P&L or live price ticks.
     */
    suspend fun openTrade(
        position: TradePosition,
        explicitUid: String? = null
    ): Result<String> {
        val uid = try {
            requireUserId(explicitUid)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/unauthenticated/open_trades")
            return Result.failure(e)
        }
        val docRef = openTradesCol(uid).document()
        val path = docRef.path
        return try {
            val cleanSymbol = position.symbol.trim().uppercase(Locale.US).ifBlank { "XAUUSD" }.take(32)
            val safeLots = round2(position.lots.safeFinite(0.01).coerceIn(0.01, 1000.0))
            val safeOpenPrice = position.openPrice.safeFinite(1.0).coerceAtLeast(0.00001)
            val safeSl = position.sl?.safeFinite(0.0)?.coerceAtLeast(0.0) ?: 0.0
            val safeTp = position.tp?.safeFinite(0.0)?.coerceAtLeast(0.0) ?: 0.0
            // Store commission magnitude as non-negative numeric value for cross-platform netProfit = gross - commission + swap
            val safeCommission = round2(abs(position.commission.safeFinite(0.0)))
            val safeSwap = round2(position.swap.safeFinite(0.0))

            val payload = linkedMapOf<String, Any>(
                "symbol" to cleanSymbol,
                "type" to if (position.isBuy) "BUY" else "SELL",
                "lots" to safeLots,
                "openPrice" to safeOpenPrice,
                "sl" to safeSl,
                "tp" to safeTp,
                "commission" to safeCommission,
                "swap" to safeSwap,
                "openTime" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (position.id > 0L) {
                payload["ticketId"] = position.id
            }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Modifies an existing open trade (e.g., SL/TP or running position edit) via `.update()`.
     * Preserves immutable `openTime` and never writes live ticks or floating P&L.
     */
    suspend fun updateOpenTrade(
        docId: String,
        symbol: String? = null,
        isBuy: Boolean? = null,
        lots: Double? = null,
        openPrice: Double? = null,
        sl: Double? = null,
        tp: Double? = null,
        commission: Double? = null,
        swap: Double? = null,
        explicitUid: String? = null
    ): Result<Unit> {
        val uid = try {
            requireUserId(explicitUid)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/unauthenticated/open_trades/$docId")
            return Result.failure(e)
        }
        val safeDocId = docId.trim()
        if (safeDocId.isEmpty()) return Result.failure(IllegalArgumentException("tradeId cannot be blank"))
        val docRef = openTradesCol(uid).document(safeDocId)
        val path = docRef.path
        return try {
            val updates = linkedMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            symbol?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() }?.let {
                updates["symbol"] = it.take(32)
            }
            isBuy?.let { updates["type"] = if (it) "BUY" else "SELL" }
            lots?.let { updates["lots"] = round2(it.safeFinite(0.01).coerceIn(0.01, 1000.0)) }
            openPrice?.let { updates["openPrice"] = it.safeFinite(1.0).coerceAtLeast(0.00001) }
            if (sl != null) updates["sl"] = sl.safeFinite(0.0).coerceAtLeast(0.0)
            if (tp != null) updates["tp"] = tp.safeFinite(0.0).coerceAtLeast(0.0)
            commission?.let { updates["commission"] = round2(abs(it.safeFinite(0.0))) }
            swap?.let { updates["swap"] = round2(it.safeFinite(0.0)) }

            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically closes an open trade (full or partial) using Firestore `runTransaction()`:
     * (a) verifies the open trade still exists,
     * (b) deletes it from `open_trades` (or reduces `lots` for partial close),
     * (c) writes the closed deal into `trade_history` with `netProfit = grossProfit - commission + swap`,
     * (d) updates `users/{uid}` balance atomically.
     */
    suspend fun closeTradeTransaction(
        openTradeDocId: String,
        closePrice: Double,
        grossProfit: Double,
        commission: Double,
        swap: Double,
        closeLots: Double? = null,
        ticketId: Long? = null,
        hitOutcome: TradeHitOutcome? = null,
        explicitUid: String? = null
    ): Result<String> {
        val uid = try {
            requireUserId(explicitUid)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/unauthenticated/open_trades/$openTradeDocId")
            return Result.failure(e)
        }
        val safeDocId = openTradeDocId.trim()
        if (safeDocId.isEmpty()) return Result.failure(IllegalArgumentException("openTradeDocId cannot be blank"))
        val userRef = userDocRef(uid)
        val openRef = openTradesCol(uid).document(safeDocId)
        val dealRef = tradeHistoryCol(uid).document()
        val path = openRef.path

        return try {
            val safeClosePrice = closePrice.safeFinite(1.0).coerceAtLeast(0.00001)
            val safeGrossProfit = round2(grossProfit.safeFinite(0.0))
            val safeCommissionFee = round2(abs(commission.safeFinite(0.0)))
            val safeSwap = round2(swap.safeFinite(0.0))
            val netProfit = round2(safeGrossProfit - safeCommissionFee + safeSwap)

            db.runTransaction { transaction ->
                // (a) Read & verify trade still exists + read user account balance
                val openSnap = transaction.get(openRef)
                if (!openSnap.exists()) {
                    throw IllegalStateException("Open trade $safeDocId no longer exists")
                }
                val userSnap = transaction.get(userRef)
                val currentBalance = if (userSnap.exists()) {
                    userSnap.get("balance").toSafeDouble(CloudUserAccount.DEFAULT_BALANCE)
                } else {
                    CloudUserAccount.DEFAULT_BALANCE
                }
                val currentRealized = if (userSnap.exists()) {
                    userSnap.get("realizedProfit").toSafeDouble(0.0)
                } else {
                    0.0
                }
                val currentTotalSwap = if (userSnap.exists()) {
                    userSnap.get("totalSwap").toSafeDouble(0.0)
                } else {
                    0.0
                }
                val currentTotalCommission = if (userSnap.exists()) {
                    userSnap.get("totalCommission").toSafeDouble(0.0)
                } else {
                    0.0
                }

                val openSymbol = (openSnap.getString("symbol") ?: "XAUUSD").trim().uppercase(Locale.US).ifBlank { "XAUUSD" }
                val openType = when (openSnap.getString("type")?.trim()?.uppercase(Locale.US)) {
                    "SELL" -> "SELL"
                    else -> "BUY"
                }
                val openLots = openSnap.get("lots").toSafeDouble(0.01).coerceIn(0.01, 1000.0)
                val openPriceVal = openSnap.get("openPrice").toSafeDouble(safeClosePrice).coerceAtLeast(0.00001)
                val openSl = openSnap.get("sl").toSafeDouble(0.0).coerceAtLeast(0.0)
                val openTp = openSnap.get("tp").toSafeDouble(0.0).coerceAtLeast(0.0)
                val openTimestamp = openSnap.getTimestamp(
                    "openTime",
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: Timestamp(Date(System.currentTimeMillis() - 1000L))
                val storedTicket = ticketId?.takeIf { it > 0L }
                    ?: openSnap.get("ticketId").toSafeLong(0L).takeIf { it > 0L }

                val requestedCloseLots = closeLots?.safeFinite(openLots)?.coerceIn(0.01, openLots) ?: openLots
                val remainingLots = round2(openLots - requestedCloseLots)

                // (b) Delete from open_trades (or update remaining lots if partial close)
                if (remainingLots <= 0.005) {
                    transaction.delete(openRef)
                } else {
                    transaction.update(
                        openRef,
                        mapOf(
                            "lots" to remainingLots,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                // (c) Save closed trade into trade_history with netProfit = grossProfit - commission + swap
                val historyPayload = linkedMapOf<String, Any>(
                    "symbol" to openSymbol.take(32),
                    "type" to openType,
                    "lots" to round2(requestedCloseLots),
                    "openPrice" to openPriceVal,
                    "closePrice" to safeClosePrice,
                    "profit" to safeGrossProfit,
                    "commission" to safeCommissionFee,
                    "swap" to safeSwap,
                    "netProfit" to netProfit,
                    "openTime" to openTimestamp,
                    "closeTime" to FieldValue.serverTimestamp(),
                    "sl" to openSl,
                    "tp" to openTp,
                    "isWithdrawal" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (storedTicket != null) {
                    historyPayload["ticketId"] = storedTicket
                }
                if (hitOutcome != null) {
                    historyPayload["hitOutcome"] = hitOutcome.name
                }
                transaction.set(dealRef, historyPayload)

                // (d) Atomically update users/{uid} balance
                val newBalance = round2(currentBalance + netProfit)
                val newRealized = round2(currentRealized + safeGrossProfit)
                val newTotalSwap = round2(currentTotalSwap + safeSwap)
                val newTotalCommission = round2(currentTotalCommission - safeCommissionFee)

                if (userSnap.exists()) {
                    transaction.update(
                        userRef,
                        mapOf(
                            "balance" to newBalance,
                            "realizedProfit" to newRealized,
                            "totalSwap" to newTotalSwap,
                            "totalCommission" to newTotalCommission,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val userEmail = auth.currentUser?.email?.trim().orEmpty().take(254)
                    transaction.set(
                        userRef,
                        mapOf(
                            "email" to userEmail,
                            "balance" to newBalance,
                            "leverage" to CloudUserAccount.DEFAULT_LEVERAGE,
                            "currency" to CloudUserAccount.DEFAULT_CURRENCY,
                            "deposit" to CloudUserAccount.DEFAULT_BALANCE,
                            "realizedProfit" to newRealized,
                            "totalWithdrawal" to 0.0,
                            "totalSwap" to newTotalSwap,
                            "totalCommission" to newTotalCommission,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                dealRef.id
            }.await()

            Result.success(dealRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Removes an open trade document directly (e.g., when cancelling/deleting a running trade from About).
     */
    suspend fun deleteOpenTrade(docId: String, explicitUid: String? = null): Result<Unit> {
        val uid = try {
            requireUserId(explicitUid)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/unauthenticated/open_trades/$docId")
            return Result.failure(e)
        }
        val safeDocId = docId.trim()
        if (safeDocId.isEmpty()) return Result.failure(IllegalArgumentException("docId cannot be blank"))
        val docRef = openTradesCol(uid).document(safeDocId)
        val path = docRef.path
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Saves or updates a history deal or withdrawal record in `users/{uid}/trade_history`
     * and atomically reconciles `users/{uid}` balance inside a transaction.
     */
    suspend fun saveHistoryDealAndBalance(
        trade: HistoryTrade,
        existingDocId: String? = null,
        updatedBalance: Double,
        updatedRealizedProfit: Double,
        updatedTotalWithdrawal: Double,
        updatedTotalSwap: Double,
        updatedTotalCommission: Double,
        deleteOpenDocId: String? = null,
        explicitUid: String? = null
    ): Result<String> {
        val uid = try {
            requireUserId(explicitUid)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/unauthenticated/trade_history")
            return Result.failure(e)
        }
        val userRef = userDocRef(uid)
        val dealRef = if (!existingDocId.isNullOrBlank()) {
            tradeHistoryCol(uid).document(existingDocId.trim())
        } else {
            tradeHistoryCol(uid).document()
        }
        val path = dealRef.path
        return try {
            val nowSafeMs = System.currentTimeMillis() - 1000L
            val parsedOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(trade.openTime)
                .takeIf { it > 0L }?.coerceAtMost(nowSafeMs) ?: nowSafeMs
            val parsedCloseMs = SriLankaMarketHours.parseMt5DateTimeMillis(trade.closeTime)
                .takeIf { it > 0L }?.coerceAtMost(nowSafeMs) ?: nowSafeMs

            val safeGrossProfit = round2(trade.profit.safeFinite(0.0))
            val safeCommissionFee = round2(abs(trade.commission.safeFinite(0.0)))
            val safeSwap = round2(trade.swap.safeFinite(0.0))
            val netProfit = if (trade.isWithdrawal) {
                safeGrossProfit
            } else {
                round2(safeGrossProfit - safeCommissionFee + safeSwap)
            }
            val dealType = when {
                trade.isWithdrawal -> "WITHDRAWAL"
                trade.isBuy -> "BUY"
                else -> "SELL"
            }

            val payload = linkedMapOf<String, Any>(
                "symbol" to (if (trade.isWithdrawal) "Balance" else trade.symbol.trim().uppercase(Locale.US).ifBlank { "XAUUSD" }).take(32),
                "type" to dealType,
                "lots" to round2(trade.lots.safeFinite(0.01).coerceIn(0.01, 1000.0)),
                "openPrice" to trade.openPrice.safeFinite(1.0).coerceAtLeast(0.00001),
                "closePrice" to trade.closePrice.safeFinite(1.0).coerceAtLeast(0.00001),
                "profit" to safeGrossProfit,
                "commission" to safeCommissionFee,
                "swap" to safeSwap,
                "netProfit" to netProfit,
                "openTime" to Timestamp(Date(parsedOpenMs)),
                "closeTime" to if (existingDocId.isNullOrBlank() && SriLankaMarketHours.parseMt5DateTimeMillis(trade.closeTime) <= 0L) {
                    FieldValue.serverTimestamp()
                } else {
                    Timestamp(Date(parsedCloseMs))
                },
                "sl" to (trade.sl?.safeFinite(0.0)?.coerceAtLeast(0.0) ?: 0.0),
                "tp" to (trade.tp?.safeFinite(0.0)?.coerceAtLeast(0.0) ?: 0.0),
                "isWithdrawal" to trade.isWithdrawal,
                "comment" to trade.comment.trim().take(200),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (trade.id > 0L) {
                payload["ticketId"] = trade.id
            }
            trade.hitOutcome?.let { payload["hitOutcome"] = it.name }

            db.runTransaction { transaction ->
                val userSnap = transaction.get(userRef)
                if (!deleteOpenDocId.isNullOrBlank()) {
                    transaction.delete(openTradesCol(uid).document(deleteOpenDocId.trim()))
                }
                transaction.set(dealRef, payload)
                if (userSnap.exists()) {
                    transaction.update(
                        userRef,
                        mapOf(
                            "balance" to round2(updatedBalance),
                            "realizedProfit" to round2(updatedRealizedProfit),
                            "totalWithdrawal" to round2(updatedTotalWithdrawal.coerceAtLeast(0.0)),
                            "totalSwap" to round2(updatedTotalSwap),
                            "totalCommission" to round2(updatedTotalCommission),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                dealRef.id
            }.await()

            Result.success(dealRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Deletes a deal from `users/{uid}/trade_history/{dealId}` and atomically updates `users/{uid}` balance.
     */
    suspend fun deleteHistoryDealAndBalance(
        dealDocId: String,
        updatedBalance: Double,
        updatedRealizedProfit: Double,
        updatedTotalWithdrawal: Double,
        updatedTotalSwap: Double,
        updatedTotalCommission: Double,
        explicitUid: String? = null
    ): Result<Unit> {
        val uid = try {
            requireUserId(explicitUid)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/unauthenticated/trade_history/$dealDocId")
            return Result.failure(e)
        }
        val safeDocId = dealDocId.trim()
        if (safeDocId.isEmpty()) return Result.failure(IllegalArgumentException("dealDocId cannot be blank"))
        val userRef = userDocRef(uid)
        val dealRef = tradeHistoryCol(uid).document(safeDocId)
        val path = dealRef.path
        return try {
            db.runTransaction { transaction ->
                val userSnap = transaction.get(userRef)
                transaction.delete(dealRef)
                if (userSnap.exists()) {
                    transaction.update(
                        userRef,
                        mapOf(
                            "balance" to round2(updatedBalance),
                            "realizedProfit" to round2(updatedRealizedProfit),
                            "totalWithdrawal" to round2(updatedTotalWithdrawal.coerceAtLeast(0.0)),
                            "totalSwap" to round2(updatedTotalSwap),
                            "totalCommission" to round2(updatedTotalCommission),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Updates account fields on `users/{uid}` (deposit, balance, display metadata).
     * Never writes floating P&L, equity, margin, or freeMargin to Firestore.
     */
    suspend fun updateUserAccount(
        account: AccountInfo,
        explicitUid: String? = null
    ): Result<Unit> {
        val uid = try {
            requireUserId(explicitUid)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/unauthenticated")
            return Result.failure(e)
        }
        val docRef = userDocRef(uid)
        val path = docRef.path
        return try {
            val snap = docRef.get().await()
            val email = auth.currentUser?.email?.trim().orEmpty().take(254)
            if (snap.exists()) {
                val updates = linkedMapOf<String, Any>(
                    "balance" to round2(account.balance),
                    "deposit" to round2(account.deposit.coerceAtLeast(0.0)),
                    "realizedProfit" to round2(account.realizedProfit),
                    "totalWithdrawal" to round2(account.totalWithdrawal.coerceAtLeast(0.0)),
                    "totalSwap" to round2(account.totalSwap),
                    "totalCommission" to round2(account.totalCommission),
                    "accountName" to account.accountName.trim().take(128),
                    "brokerName" to account.brokerName.trim().take(128),
                    "accountId" to account.accountId.trim().take(128),
                    "serverName" to account.serverName.trim().take(128),
                    "isReal" to account.isReal,
                    "depositDate" to account.depositDate.trim().take(64),
                    "logoType" to account.logoType.trim().ifBlank { "AUTO" }.take(32),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (email.isNotEmpty()) {
                    updates["email"] = email
                }
                docRef.update(updates).await()
            } else {
                val payload = linkedMapOf<String, Any>(
                    "email" to email,
                    "balance" to round2(account.balance),
                    "leverage" to CloudUserAccount.DEFAULT_LEVERAGE,
                    "currency" to CloudUserAccount.DEFAULT_CURRENCY,
                    "deposit" to round2(account.deposit.coerceAtLeast(0.0)),
                    "realizedProfit" to round2(account.realizedProfit),
                    "totalWithdrawal" to round2(account.totalWithdrawal.coerceAtLeast(0.0)),
                    "totalSwap" to round2(account.totalSwap),
                    "totalCommission" to round2(account.totalCommission),
                    "accountName" to account.accountName.trim().take(128),
                    "brokerName" to account.brokerName.trim().take(128),
                    "accountId" to account.accountId.trim().take(128),
                    "serverName" to account.serverName.trim().take(128),
                    "isReal" to account.isReal,
                    "depositDate" to account.depositDate.trim().take(64),
                    "logoType" to account.logoType.trim().ifBlank { "AUTO" }.take(32),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Clears open_trades & trade_history and resets `users/{uid}` balance to 10000.0 USD.
     */
    suspend fun resetUserAccount(explicitUid: String? = null): Result<Unit> {
        val uid = try {
            requireUserId(explicitUid)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/unauthenticated")
            return Result.failure(e)
        }
        val path = "users/$uid"
        return try {
            val openSnap = openTradesCol(uid).get().await()
            val histSnap = tradeHistoryCol(uid).get().await()

            val docsToDelete = openSnap.documents + histSnap.documents
            docsToDelete.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            val userRef = userDocRef(uid)
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                userRef.update(
                    mapOf(
                        "balance" to CloudUserAccount.DEFAULT_BALANCE,
                        "deposit" to CloudUserAccount.DEFAULT_BALANCE,
                        "realizedProfit" to 0.0,
                        "totalWithdrawal" to 0.0,
                        "totalSwap" to 0.0,
                        "totalCommission" to 0.0,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ensureUserDocumentExists(uid, auth.currentUser?.email)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun parseUserAccount(doc: DocumentSnapshot, uid: String): CloudUserAccount {
        val createdAtMs = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time
            ?: System.currentTimeMillis()
        val balance = doc.get("balance").toSafeDouble(CloudUserAccount.DEFAULT_BALANCE)
        val deposit = if (doc.contains("deposit")) {
            doc.get("deposit").toSafeDouble(balance)
        } else {
            balance
        }
        return CloudUserAccount(
            uid = uid,
            email = doc.getString("email").orEmpty(),
            displayName = doc.getString("displayName").orEmpty(),
            photoURL = sanitizePhotoUrl(doc.getString("photoURL")),
            balance = round2(balance),
            leverage = doc.get("leverage").toSafeLong(CloudUserAccount.DEFAULT_LEVERAGE).coerceIn(1L, 5000L),
            currency = doc.getString("currency")?.ifBlank { CloudUserAccount.DEFAULT_CURRENCY } ?: CloudUserAccount.DEFAULT_CURRENCY,
            deposit = round2(deposit.coerceAtLeast(0.0)),
            realizedProfit = round2(doc.get("realizedProfit").toSafeDouble(0.0)),
            totalWithdrawal = round2(doc.get("totalWithdrawal").toSafeDouble(0.0).coerceAtLeast(0.0)),
            totalSwap = round2(doc.get("totalSwap").toSafeDouble(0.0)),
            totalCommission = round2(doc.get("totalCommission").toSafeDouble(0.0)),
            accountName = doc.getString("accountName").orEmpty(),
            brokerName = doc.getString("brokerName").orEmpty(),
            accountId = doc.getString("accountId").orEmpty(),
            serverName = doc.getString("serverName").orEmpty(),
            isReal = doc.getBoolean("isReal") ?: true,
            depositDate = doc.getString("depositDate").orEmpty(),
            logoType = doc.getString("logoType")?.ifBlank { "AUTO" } ?: "AUTO",
            createdAtMs = createdAtMs
        )
    }

    private fun parseOpenTrade(doc: DocumentSnapshot): CloudOpenTradeRecord {
        val rawSymbol = (doc.getString("symbol") ?: "XAUUSD").trim().uppercase(Locale.US).ifBlank { "XAUUSD" }
        val cleanSymbol = if (rawSymbol == "NDX100") "NAS100" else rawSymbol
        val typeStr = doc.getString("type")?.trim()?.uppercase(Locale.US) ?: "BUY"
        val isBuy = typeStr != "SELL"
        val lots = round2(doc.get("lots").toSafeDouble(0.01).coerceIn(0.01, 1000.0))
        val openPrice = doc.get("openPrice").toSafeDouble(1.0).coerceAtLeast(0.00001)
        val rawSl = doc.get("sl").toSafeDouble(0.0)
        val rawTp = doc.get("tp").toSafeDouble(0.0)
        val sl = if (rawSl > 0.0) rawSl else null
        val tp = if (rawTp > 0.0) rawTp else null
        // Domain UI expects negative commission display (-fee); Firestore stores numeric fee magnitude
        val storedComm = abs(doc.get("commission").toSafeDouble(0.0))
        val domainComm = if (storedComm > 0.0) -round2(storedComm) else 0.0
        val swap = round2(doc.get("swap").toSafeDouble(0.0))

        // Handle pending serverTimestamp() safely: ESTIMATE -> fallback to System.currentTimeMillis()
        val openTimeMs = doc.getTimestamp("openTime", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time
            ?: System.currentTimeMillis()
        val openTimeFormatted = SriLankaMarketHours.formatDateTime(openTimeMs)
        val domainId = resolveUniqueDocDomainId(doc.id, doc.get("ticketId"), 385_000_000L)

        val position = TradePosition(
            id = domainId,
            symbol = cleanSymbol,
            isBuy = isBuy,
            lots = lots,
            openPrice = openPrice,
            currentPrice = openPrice,
            sl = sl,
            tp = tp,
            profit = 0.0, // Always calculated locally from live market feed
            openTime = openTimeFormatted,
            commission = domainComm,
            swap = swap
        )
        return CloudOpenTradeRecord(
            docId = doc.id,
            position = position,
            openTimeMs = openTimeMs
        )
    }

    private fun parseHistoryDeal(doc: DocumentSnapshot): CloudHistoryDealRecord {
        val typeStr = doc.getString("type")?.trim()?.uppercase(Locale.US) ?: "BUY"
        val isWithdrawal = (doc.getBoolean("isWithdrawal") ?: false) ||
            typeStr == "WITHDRAWAL" ||
            typeStr == "BALANCE"
        val rawSymbol = (doc.getString("symbol") ?: if (isWithdrawal) "Balance" else "XAUUSD")
            .trim()
            .let { if (isWithdrawal) "Balance" else it.uppercase(Locale.US).ifBlank { "XAUUSD" } }
        val cleanSymbol = if (!isWithdrawal && rawSymbol == "NDX100") "NAS100" else rawSymbol
        val isBuy = !isWithdrawal && typeStr != "SELL"
        val lots = round2(doc.get("lots").toSafeDouble(0.01).coerceIn(0.01, 1000.0))
        val openPrice = doc.get("openPrice").toSafeDouble(1.0).coerceAtLeast(0.00001)
        val closePrice = doc.get("closePrice").toSafeDouble(openPrice).coerceAtLeast(0.00001)
        val profit = round2(doc.get("profit").toSafeDouble(0.0))
        val storedComm = abs(doc.get("commission").toSafeDouble(0.0))
        val domainComm = if (storedComm > 0.0) -round2(storedComm) else 0.0
        val swap = round2(doc.get("swap").toSafeDouble(0.0))
        val rawSl = doc.get("sl").toSafeDouble(0.0)
        val rawTp = doc.get("tp").toSafeDouble(0.0)
        val sl = if (rawSl > 0.0) rawSl else null
        val tp = if (rawTp > 0.0) rawTp else null

        val openTimeMs = doc.getTimestamp("openTime", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time
            ?: System.currentTimeMillis()
        val closeTimeMs = doc.getTimestamp("closeTime", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time
            ?: System.currentTimeMillis()

        val domainId = resolveUniqueDocDomainId(doc.id, doc.get("ticketId"), 385_000_000L)
        val ticketLabel = "#${if (domainId >= 100_000_000L) domainId else 385_000_000L + domainId}"
        val rawHit = doc.getString("hitOutcome")?.trim()?.uppercase(Locale.US)
        val hitOutcome = rawHit?.let { name ->
            TradeHitOutcome.entries.firstOrNull { it.name == name }
        }

        val historyTrade = HistoryTrade(
            id = domainId,
            symbol = cleanSymbol,
            isBuy = isBuy,
            lots = lots,
            openPrice = openPrice,
            closePrice = closePrice,
            profit = profit,
            openTime = SriLankaMarketHours.formatDateTime(openTimeMs),
            closeTime = SriLankaMarketHours.formatDateTime(closeTimeMs),
            commission = domainComm,
            swap = swap,
            sl = sl,
            tp = tp,
            ticketId = ticketLabel,
            isWithdrawal = isWithdrawal,
            comment = doc.getString("comment").orEmpty(),
            hitOutcome = hitOutcome
        )
        return CloudHistoryDealRecord(
            docId = doc.id,
            trade = historyTrade,
            openTimeMs = openTimeMs,
            closeTimeMs = closeTimeMs
        )
    }
}
