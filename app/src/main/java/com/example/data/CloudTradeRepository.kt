package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AccountInfo
import com.example.model.HistoryTrade
import com.example.model.TradeHitOutcome
import com.example.model.TradePosition
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@IgnoreExtraProperties
data class TradePositionDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ticketId: Long = 0L,
    val symbol: String = "",
    val isBuy: Boolean = true,
    val lots: Double = 0.0,
    val openPrice: Double = 0.0,
    val currentPrice: Double = 0.0,
    val sl: Double? = null,
    val tp: Double? = null,
    val profit: Double = 0.0,
    val openTime: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): TradePosition = TradePosition(
        id = if (ticketId > 0L) ticketId else (id.filter { it.isDigit() }.toLongOrNull() ?: 1001L),
        symbol = symbol,
        isBuy = isBuy,
        lots = lots,
        openPrice = openPrice,
        currentPrice = currentPrice,
        sl = sl,
        tp = tp,
        profit = profit,
        openTime = openTime
    )
}

@IgnoreExtraProperties
data class HistoryTradeDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ticketId: Long = 0L,
    val symbol: String = "",
    val isBuy: Boolean = true,
    val lots: Double = 0.0,
    val openPrice: Double = 0.0,
    val closePrice: Double = 0.0,
    val profit: Double = 0.0,
    val openTime: String = "",
    val closeTime: String = "",
    val commission: Double = 0.0,
    val swap: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): HistoryTrade {
        val domainId = if (ticketId > 0L) ticketId else (id.filter { it.isDigit() }.toLongOrNull() ?: 2001L)
        val isWdr = symbol.startsWith("WDR:") || symbol.equals("WITHDRAWAL", ignoreCase = true)

        if (isWdr) {
            val wdrComment = if (symbol.startsWith("WDR:")) {
                symbol.removePrefix("WDR:").ifBlank { "Withdrawal" }
            } else {
                "Withdrawal"
            }
            val openParts = openTime.split("|")
            val parsedOpenTime = openParts.firstOrNull()?.ifBlank { closeTime } ?: closeTime
            val parsedTicket = openParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "#${385000000L + domainId}"
            return HistoryTrade(
                id = domainId,
                symbol = "Balance",
                isBuy = false,
                lots = lots,
                openPrice = openPrice,
                closePrice = closePrice,
                profit = -abs(profit),
                openTime = parsedOpenTime,
                closeTime = closeTime,
                commission = 0.0,
                swap = 0.0,
                sl = null,
                tp = null,
                ticketId = parsedTicket,
                isWithdrawal = true,
                comment = wdrComment,
                hitOutcome = TradeHitOutcome.NONE
            )
        }

        // Standard closed trade: openTime may optionally encode "openTime|sl|tp|ticketId|hitCode"
        val parts = openTime.split("|")
        val cleanOpenTime = parts.firstOrNull()?.ifBlank { closeTime } ?: closeTime
        val parsedSl = parts.getOrNull(1)?.toDoubleOrNull()
        val parsedTp = parts.getOrNull(2)?.toDoubleOrNull()
        val parsedTicket = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "#${385000000L + domainId}"
        val parsedHitOutcome = when (parts.getOrNull(4)?.trim()?.uppercase()) {
            "TP", "TP_HIT" -> TradeHitOutcome.TP_HIT
            "SL", "SL_HIT" -> TradeHitOutcome.SL_HIT
            "NO", "NONE" -> TradeHitOutcome.NONE
            else -> null
        }

        return HistoryTrade(
            id = domainId,
            symbol = symbol,
            isBuy = isBuy,
            lots = lots,
            openPrice = openPrice,
            closePrice = closePrice,
            profit = profit,
            openTime = cleanOpenTime,
            closeTime = closeTime,
            commission = commission,
            swap = swap,
            sl = parsedSl,
            tp = parsedTp,
            ticketId = parsedTicket,
            isWithdrawal = false,
            comment = "",
            hitOutcome = parsedHitOutcome
        )
    }
}

@IgnoreExtraProperties
data class AccountDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val balance: Double = 524.74,
    val deposit: Double = 500.00,
    val realizedProfit: Double = 24.74,
    val totalSwap: Double = 0.00,
    val totalCommission: Double = 0.00,
    val accountName: String = "Pasindu Jeewantha Pelanage",
    val brokerName: String = "Moneta Markets (Pty) Ltd",
    val accountId: String = "36054607 - MonetaMarkets-Live",
    val serverName: String = "AS08, Hedge",
    val isReal: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(
        equity: Double = balance,
        margin: Double = 0.0,
        freeMargin: Double = balance,
        marginLevel: Double = 0.0,
        floatingPnL: Double = 0.0
    ): AccountInfo = AccountInfo(
        balance = balance,
        equity = equity,
        margin = margin,
        freeMargin = freeMargin,
        marginLevel = marginLevel,
        floatingPnL = floatingPnL,
        accountName = accountName,
        brokerName = brokerName,
        accountId = accountId,
        serverName = serverName,
        isReal = isReal,
        deposit = deposit,
        realizedProfit = realizedProfit,
        totalSwap = totalSwap,
        totalCommission = totalCommission
    )
}

class CloudTradeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeOpenPositions(userId: String = requireUserId()): Flow<List<TradePosition>> = callbackFlow {
        val path = "users/$userId/positions"
        val registration = db.collection("users").document(userId).collection("positions")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.toObjects(
                        TradePositionDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                        .map { it.toDomain() }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeTradeHistory(userId: String = requireUserId()): Flow<List<HistoryTrade>> = callbackFlow {
        val path = "users/$userId/history"
        val registration = db.collection("users").document(userId).collection("history")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.toObjects(
                        HistoryTradeDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                        .sortedWith(
                            compareBy<HistoryTrade> {
                                SriLankaMarketHours.parseMt5DateTimeMillis(it.closeTime)
                            }.thenBy { it.id }
                        )
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeAccountDoc(userId: String = requireUserId()): Flow<AccountDoc?> = callbackFlow {
        val path = "users/$userId/accounts/main"
        val registration = db.collection("users").document(userId).collection("accounts").document("main")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(
                        snapshot.toObject(
                            AccountDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    )
                } else {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveOpenPosition(position: TradePosition, userId: String = requireUserId()): String {
        val docId = "pos_${position.id}"
        val docRef = db.collection("users").document(userId).collection("positions").document(docId)
        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "ticketId" to position.id.coerceAtLeast(1L),
            "symbol" to position.symbol.ifBlank { "EURUSD" }.take(32),
            "isBuy" to position.isBuy,
            "lots" to position.lots.coerceIn(0.01, 1000.0),
            "openPrice" to position.openPrice.coerceAtLeast(0.00001),
            "currentPrice" to position.currentPrice.coerceAtLeast(0.00001),
            "profit" to position.profit,
            "openTime" to position.openTime.ifBlank { SriLankaMarketHours.formatDateTime() }.take(64),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        position.sl?.let { if (it > 0.0) payload["sl"] = it }
        position.tp?.let { if (it > 0.0) payload["tp"] = it }

        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.delete().await()
            }
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateOpenPositionPartial(
        positionId: Long,
        newLots: Double,
        currentPrice: Double,
        newProfit: Double,
        userId: String = requireUserId()
    ) {
        val docId = "pos_$positionId"
        val docRef = db.collection("users").document(userId).collection("positions").document(docId)
        val updates = mapOf(
            "lots" to newLots.coerceIn(0.01, 1000.0),
            "currentPrice" to currentPrice.coerceAtLeast(0.00001),
            "profit" to newProfit,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteOpenPosition(positionId: Long, userId: String = requireUserId()) {
        val docId = "pos_$positionId"
        val docRef = db.collection("users").document(userId).collection("positions").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private fun buildHistoryPayload(trade: HistoryTrade, userId: String): Map<String, Any> {
        val safeTicketId = trade.id.coerceAtLeast(1L)
        val safeCloseTime = trade.closeTime.ifBlank { SriLankaMarketHours.formatDateTime() }.take(64)

        val encodedSymbol: String
        val encodedOpenTime: String
        val safeLots: Double
        val safeOpenPrice: Double
        val safeClosePrice: Double
        val safeProfit: Double

        if (trade.isWithdrawal) {
            val cleanComment = trade.comment.ifBlank { "Withdrawal" }.replace("|", " ").take(24)
            encodedSymbol = "WDR:$cleanComment"
            encodedOpenTime = "$safeCloseTime|${trade.ticketId}".take(64)
            safeLots = 0.01
            safeOpenPrice = 1.0
            safeClosePrice = 1.0
            safeProfit = -abs(trade.profit)
        } else {
            encodedSymbol = trade.symbol.ifBlank { "EURUSD" }.take(32)
            val baseOpen = trade.openTime.ifBlank { safeCloseTime }.take(22)
            val slStr = trade.sl?.toString() ?: ""
            val tpStr = trade.tp?.toString() ?: ""
            val ticketStr = trade.ticketId.take(14)
            val hitCode = when (trade.hitOutcome) {
                TradeHitOutcome.TP_HIT -> "TP"
                TradeHitOutcome.SL_HIT -> "SL"
                TradeHitOutcome.NONE -> "NO"
                null -> ""
            }
            val candidateWithHit = "$baseOpen|$slStr|$tpStr|$ticketStr|$hitCode"
            val candidateOpen = "$baseOpen|$slStr|$tpStr|$ticketStr"
            encodedOpenTime = when {
                hitCode.isNotEmpty() && candidateWithHit.length <= 64 -> candidateWithHit
                candidateOpen.length <= 64 -> candidateOpen
                else -> baseOpen.take(64)
            }
            safeLots = trade.lots.coerceIn(0.01, 1000.0)
            safeOpenPrice = trade.openPrice.coerceAtLeast(0.00001)
            safeClosePrice = trade.closePrice.coerceAtLeast(0.00001)
            safeProfit = trade.profit
        }

        return mapOf(
            "userId" to userId,
            "ticketId" to safeTicketId,
            "symbol" to encodedSymbol,
            "isBuy" to trade.isBuy,
            "lots" to safeLots,
            "openPrice" to safeOpenPrice,
            "closePrice" to safeClosePrice,
            "profit" to safeProfit,
            "openTime" to encodedOpenTime,
            "closeTime" to safeCloseTime,
            "commission" to trade.commission,
            "swap" to trade.swap,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    suspend fun addHistoryTrade(trade: HistoryTrade, docSuffix: String = "", userId: String = requireUserId()): String {
        val docId = if (docSuffix.isNotBlank()) "hist_${trade.id}_$docSuffix" else "hist_${trade.id}"
        val docRef = db.collection("users").document(userId).collection("history").document(docId)
        val payload = buildHistoryPayload(trade, userId)
        try {
            // If replacing an edited trade with the same ID, delete first so Firestore create rule passes cleanly
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.delete().await()
            }
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteHistoryTrade(tradeId: Long, userId: String = requireUserId()) {
        val colRef = db.collection("users").document(userId).collection("history")
        val primaryDocRef = colRef.document("hist_$tradeId")
        try {
            primaryDocRef.delete().await()
            val matchingDocs = colRef
                .whereEqualTo("userId", userId)
                .whereEqualTo("ticketId", tradeId)
                .get()
                .await()
            for (doc in matchingDocs.documents) {
                doc.reference.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, primaryDocRef.path)
        }
    }

    suspend fun getHistoryTradeById(targetUserId: String, docId: String): HistoryTradeDoc? {
        val docRef = db.collection("users").document(targetUserId).collection("history").document(docId)
        try {
            val snap = docRef.get().await()
            return snap.toObject(
                HistoryTradeDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun saveOrUpdateAccount(account: AccountInfo, userId: String = requireUserId()) {
        val docRef = db.collection("users").document(userId).collection("accounts").document("main")
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mapOf(
                    "userId" to userId,
                    "balance" to account.balance,
                    "deposit" to account.deposit,
                    "realizedProfit" to account.realizedProfit,
                    "totalSwap" to account.totalSwap,
                    "totalCommission" to account.totalCommission,
                    "accountName" to account.accountName.ifBlank { "Pasindu Jeewantha Pelanage" }.take(64),
                    "brokerName" to account.brokerName.ifBlank { "Moneta Markets (Pty) Ltd" }.take(128),
                    "accountId" to account.accountId.ifBlank { "36054607 - MonetaMarkets-Live" }.take(128),
                    "serverName" to account.serverName.ifBlank { "AS08, Hedge" }.take(128),
                    "isReal" to account.isReal,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "balance" to account.balance,
                    "deposit" to account.deposit,
                    "realizedProfit" to account.realizedProfit,
                    "totalSwap" to account.totalSwap,
                    "totalCommission" to account.totalCommission,
                    "accountName" to account.accountName.ifBlank { "Pasindu Jeewantha Pelanage" }.take(64),
                    "brokerName" to account.brokerName.ifBlank { "Moneta Markets (Pty) Ltd" }.take(128),
                    "accountId" to account.accountId.ifBlank { "36054607 - MonetaMarkets-Live" }.take(128),
                    "serverName" to account.serverName.ifBlank { "AS08, Hedge" }.take(128),
                    "isReal" to account.isReal,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun clearUserPositions(userId: String = requireUserId()) {
        val colRef = db.collection("users").document(userId).collection("positions")
        try {
            val snap = colRef.whereEqualTo("userId", userId).get().await()
            for (doc in snap.documents) {
                doc.reference.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
        }
    }

    suspend fun clearUserHistory(userId: String = requireUserId()) {
        val colRef = db.collection("users").document(userId).collection("history")
        try {
            val snap = colRef.whereEqualTo("userId", userId).get().await()
            for (doc in snap.documents) {
                doc.reference.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
        }
    }
}
