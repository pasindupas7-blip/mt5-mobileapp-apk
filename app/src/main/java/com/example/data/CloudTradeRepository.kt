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
import java.math.BigDecimal
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private fun Double.safeFinite(default: Double = 0.0): Double =
    if (isNaN() || isInfinite()) default else this

private fun formatCompactDouble(value: Double?): String {
    if (value == null || value <= 0.0 || value.isNaN() || value.isInfinite()) return ""
    return try {
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    } catch (e: Exception) {
        value.toString()
    }
}

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
    fun toDomain(): TradePosition {
        val fallbackId = id.substringAfterLast("_").toLongOrNull()
            ?: id.filter { it.isDigit() }.takeLast(12).toLongOrNull()
            ?: 1001L
        val resolvedId = if (ticketId > 0L) ticketId else fallbackId
        return TradePosition(
            id = resolvedId,
            symbol = symbol.ifBlank { "XAUUSD" },
            isBuy = isBuy,
            lots = lots.safeFinite(0.01).coerceAtLeast(0.01),
            openPrice = openPrice.safeFinite(1.0).coerceAtLeast(0.00001),
            currentPrice = currentPrice.safeFinite(openPrice.safeFinite(1.0)).coerceAtLeast(0.00001),
            sl = sl?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 },
            tp = tp?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 },
            profit = profit.safeFinite(0.0),
            openTime = openTime.ifBlank { SriLankaMarketHours.formatDateTime() }
        )
    }
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
        val fallbackId = id.substringAfterLast("_").toLongOrNull()
            ?: id.filter { it.isDigit() }.takeLast(12).toLongOrNull()
            ?: 2001L
        val domainId = if (ticketId > 0L) ticketId else fallbackId
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
                lots = lots.safeFinite(0.01).coerceAtLeast(0.01),
                openPrice = openPrice.safeFinite(1.0).coerceAtLeast(0.00001),
                closePrice = closePrice.safeFinite(1.0).coerceAtLeast(0.00001),
                profit = -abs(profit.safeFinite(0.0)),
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
        val parsedSl = parts.getOrNull(1)?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
        val parsedTp = parts.getOrNull(2)?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
        val parsedTicket = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
            ?: "#${if (domainId >= 100000000L) domainId else 385000000L + domainId}"
        val parsedHitOutcome = when (parts.getOrNull(4)?.trim()?.uppercase()) {
            "TP", "TP_HIT" -> TradeHitOutcome.TP_HIT
            "SL", "SL_HIT" -> TradeHitOutcome.SL_HIT
            "NO", "NONE" -> TradeHitOutcome.NONE
            else -> null
        }

        return HistoryTrade(
            id = domainId,
            symbol = symbol.ifBlank { "XAUUSD" },
            isBuy = isBuy,
            lots = lots.safeFinite(0.01).coerceAtLeast(0.01),
            openPrice = openPrice.safeFinite(1.0).coerceAtLeast(0.00001),
            closePrice = closePrice.safeFinite(1.0).coerceAtLeast(0.00001),
            profit = profit.safeFinite(0.0),
            openTime = cleanOpenTime,
            closeTime = closeTime,
            commission = commission.safeFinite(0.0),
            swap = swap.safeFinite(0.0),
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
    ): AccountInfo {
        val serverParts = serverName.split("|")
        val cleanServer = serverParts.firstOrNull()?.ifBlank { "AS08, Hedge" } ?: "AS08, Hedge"
        val parsedDepositDate = serverParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "2026.07.15 09:00:00"
        val parsedLogoType = serverParts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "AUTO"

        return AccountInfo(
            balance = balance.safeFinite(524.74),
            equity = equity.safeFinite(balance.safeFinite(524.74)),
            margin = margin.safeFinite(0.0),
            freeMargin = freeMargin.safeFinite(balance.safeFinite(524.74)),
            marginLevel = marginLevel.safeFinite(0.0),
            floatingPnL = floatingPnL.safeFinite(0.0),
            accountName = accountName.ifBlank { "Pasindu Jeewantha Pelanage" },
            brokerName = brokerName.ifBlank { "Moneta Markets (Pty) Ltd" },
            accountId = accountId.ifBlank { "36054607 - MonetaMarkets-Live" },
            serverName = cleanServer,
            isReal = isReal,
            deposit = deposit.safeFinite(500.0),
            depositDate = parsedDepositDate,
            realizedProfit = realizedProfit.safeFinite(0.0),
            totalSwap = totalSwap.safeFinite(0.0),
            totalCommission = totalCommission.safeFinite(0.0),
            logoType = parsedLogoType,
            key = id.ifBlank { "main" }
        )
    }
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

    /**
     * Checks whether [docId] (with [prefix] = "pos" or "hist") belongs to [accountKey].
     * Correctly handles both "main" (`pos_1001`) and 3-part secondary account keys (`pos_acc_1762620000000_456_1001`).
     */
    fun isDocForAccount(docId: String, prefix: String, accountKey: String): Boolean {
        val safeKey = accountKey.ifBlank { "main" }
        val body = docId.removePrefix("${prefix}_")
        return if (safeKey == "main") {
            !body.startsWith("acc_")
        } else {
            body.startsWith("${safeKey}_")
        }
    }

    fun extractAccountKeyFromDocId(docId: String, prefix: String): String {
        val body = docId.removePrefix("${prefix}_")
        if (body.startsWith("acc_")) {
            val parts = body.split("_")
            if (parts.size >= 4 && parts[2].length == 3 && parts[2].all { it.isDigit() }) {
                return "${parts[0]}_${parts[1]}_${parts[2]}"
            }
            if (parts.size >= 3) {
                return "${parts[0]}_${parts[1]}"
            }
        }
        return "main"
    }

    private fun positionDocId(positionId: Long, accountKey: String): String {
        val safeKey = accountKey.ifBlank { "main" }
        return if (safeKey == "main") "pos_$positionId" else "pos_${safeKey}_$positionId"
    }

    private fun historyDocId(tradeId: Long, docSuffix: String, accountKey: String): String {
        val safeKey = accountKey.ifBlank { "main" }
        val base = if (safeKey == "main") "hist_$tradeId" else "hist_${safeKey}_$tradeId"
        return if (docSuffix.isNotBlank()) "${base}_$docSuffix" else base
    }

    fun observeOpenPositions(
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): Flow<List<TradePosition>> = callbackFlow {
        val safeKey = accountKey.ifBlank { "main" }
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
                    ).filter { isDocForAccount(it.id, "pos", safeKey) }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                        .map { it.toDomain() }
                        .distinctBy { it.id }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeTradeHistory(
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): Flow<List<HistoryTrade>> = callbackFlow {
        val safeKey = accountKey.ifBlank { "main" }
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
                    val docs = snapshot.toObjects(
                        HistoryTradeDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { isDocForAccount(it.id, "hist", safeKey) }
                        .sortedBy { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }

                    // Deduplicate by domain trade ID, keeping the most recently updated document
                    val byDomainId = LinkedHashMap<Long, HistoryTrade>()
                    for (doc in docs) {
                        val domain = doc.toDomain()
                        byDomainId[domain.id] = domain
                    }

                    val items = byDomainId.values.sortedWith(
                        compareBy<HistoryTrade> {
                            SriLankaMarketHours.parseMt5DateTimeMillis(it.closeTime)
                        }.thenBy { it.id }
                    )
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun fetchOpenPositionsOnce(
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): List<TradePosition> {
        val safeKey = accountKey.ifBlank { "main" }
        val path = "users/$userId/positions"
        return try {
            val snap = db.collection("users").document(userId).collection("positions")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            snap.toObjects(
                TradePositionDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { isDocForAccount(it.id, "pos", safeKey) }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                .map { it.toDomain() }
                .distinctBy { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun fetchTradeHistoryOnce(
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): List<HistoryTrade> {
        val safeKey = accountKey.ifBlank { "main" }
        val path = "users/$userId/history"
        return try {
            val snap = db.collection("users").document(userId).collection("history")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val docs = snap.toObjects(
                HistoryTradeDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { isDocForAccount(it.id, "hist", safeKey) }
                .sortedBy { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }

            val byDomainId = LinkedHashMap<Long, HistoryTrade>()
            for (doc in docs) {
                val domain = doc.toDomain()
                byDomainId[domain.id] = domain
            }
            byDomainId.values.sortedWith(
                compareBy<HistoryTrade> {
                    SriLankaMarketHours.parseMt5DateTimeMillis(it.closeTime)
                }.thenBy { it.id }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun fetchAllAccountsOnce(userId: String = requireUserId()): List<AccountDoc> {
        val path = "users/$userId/accounts"
        return try {
            val snap = db.collection("users").document(userId).collection("accounts")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            snap.toObjects(
                AccountDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    fun observeAccountDoc(
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): Flow<AccountDoc?> = callbackFlow {
        val safeKey = accountKey.ifBlank { "main" }
        val path = "users/$userId/accounts/$safeKey"
        val registration = db.collection("users").document(userId).collection("accounts").document(safeKey)
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

    fun observeAllAccountDocs(userId: String = requireUserId()): Flow<List<AccountDoc>> = callbackFlow {
        val path = "users/$userId/accounts"
        val registration = db.collection("users").document(userId).collection("accounts")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val docs = snapshot.toObjects(
                        AccountDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    trySend(docs)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveOpenPosition(
        position: TradePosition,
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): String {
        val docId = positionDocId(position.id, accountKey)
        val docRef = db.collection("users").document(userId).collection("positions").document(docId)
        val safeOpenPrice = position.openPrice.safeFinite(1.0).coerceAtLeast(0.00001)
        val safeCurrentPrice = position.currentPrice.safeFinite(safeOpenPrice).coerceAtLeast(0.00001)

        try {
            val existing = docRef.get().await()
            val createdAtValue: Any = if (existing.exists()) {
                existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                FieldValue.serverTimestamp()
            }
            val payload = mutableMapOf<String, Any>(
                "userId" to userId,
                "ticketId" to position.id.coerceAtLeast(1L),
                "symbol" to position.symbol.ifBlank { "EURUSD" }.take(32),
                "isBuy" to position.isBuy,
                "lots" to position.lots.safeFinite(0.01).coerceIn(0.01, 1000.0),
                "openPrice" to safeOpenPrice,
                "currentPrice" to safeCurrentPrice,
                "profit" to position.profit.safeFinite(0.0),
                "openTime" to position.openTime.ifBlank { SriLankaMarketHours.formatDateTime() }.take(64),
                "createdAt" to createdAtValue,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            position.sl?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }?.let { payload["sl"] = it }
            position.tp?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }?.let { payload["tp"] = it }

            // Atomic create or in-place update without deleting first (prevents transient delete snapshots)
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateOpenPositionPartial(
        positionId: Long,
        newLots: Double,
        currentPrice: Double,
        newProfit: Double,
        userId: String = requireUserId(),
        accountKey: String = "main"
    ) {
        val docId = positionDocId(positionId, accountKey)
        val docRef = db.collection("users").document(userId).collection("positions").document(docId)
        val updates = mapOf(
            "lots" to newLots.safeFinite(0.01).coerceIn(0.01, 1000.0),
            "currentPrice" to currentPrice.safeFinite(1.0).coerceAtLeast(0.00001),
            "profit" to newProfit.safeFinite(0.0),
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

    suspend fun deleteOpenPosition(
        positionId: Long,
        userId: String = requireUserId(),
        accountKey: String = "main"
    ) {
        val docId = positionDocId(positionId, accountKey)
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

    private fun buildHistoryPayload(
        trade: HistoryTrade,
        userId: String,
        createdAtValue: Any = FieldValue.serverTimestamp()
    ): Map<String, Any> {
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
            encodedOpenTime = "$safeCloseTime|${trade.ticketId}".take(160)
            safeLots = 0.01
            safeOpenPrice = 1.0
            safeClosePrice = 1.0
            safeProfit = -abs(trade.profit.safeFinite(0.0))
        } else {
            encodedSymbol = trade.symbol.ifBlank { "EURUSD" }.take(32)
            val baseOpen = trade.openTime.ifBlank { safeCloseTime }.take(24)
            val slStr = formatCompactDouble(trade.sl)
            val tpStr = formatCompactDouble(trade.tp)
            val ticketStr = trade.ticketId.take(18)
            val hitCode = when (trade.hitOutcome) {
                TradeHitOutcome.TP_HIT -> "TP"
                TradeHitOutcome.SL_HIT -> "SL"
                TradeHitOutcome.NONE -> "NO"
                null -> ""
            }
            val candidateWithHit = "$baseOpen|$slStr|$tpStr|$ticketStr|$hitCode"
            val candidateOpen = "$baseOpen|$slStr|$tpStr|$ticketStr"
            encodedOpenTime = when {
                hitCode.isNotEmpty() && candidateWithHit.length <= 160 -> candidateWithHit
                candidateOpen.length <= 160 -> candidateOpen
                else -> baseOpen.take(64)
            }
            safeLots = trade.lots.safeFinite(0.01).coerceIn(0.01, 1000.0)
            safeOpenPrice = trade.openPrice.safeFinite(1.0).coerceAtLeast(0.00001)
            safeClosePrice = trade.closePrice.safeFinite(1.0).coerceAtLeast(0.00001)
            safeProfit = trade.profit.safeFinite(0.0)
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
            "commission" to trade.commission.safeFinite(0.0),
            "swap" to trade.swap.safeFinite(0.0),
            "createdAt" to createdAtValue,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    suspend fun addHistoryTrade(
        trade: HistoryTrade,
        docSuffix: String = "",
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): String {
        val docId = historyDocId(trade.id, docSuffix, accountKey)
        val docRef = db.collection("users").document(userId).collection("history").document(docId)
        try {
            val existing = docRef.get().await()
            val createdAtValue: Any = if (existing.exists()) {
                existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                FieldValue.serverTimestamp()
            }
            val payload = buildHistoryPayload(trade, userId, createdAtValue)
            // Atomic create or in-place update without deleting first (prevents transient delete snapshots)
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteHistoryTrade(
        tradeId: Long,
        userId: String = requireUserId(),
        accountKey: String = "main"
    ) {
        val safeKey = accountKey.ifBlank { "main" }
        val colRef = db.collection("users").document(userId).collection("history")
        val primaryDocRef = colRef.document(historyDocId(tradeId, "", safeKey))
        try {
            primaryDocRef.delete().await()
            val matchingDocs = colRef
                .whereEqualTo("userId", userId)
                .whereEqualTo("ticketId", tradeId)
                .get()
                .await()
            for (doc in matchingDocs.documents) {
                if (isDocForAccount(doc.id, "hist", safeKey)) {
                    doc.reference.delete().await()
                }
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

    suspend fun saveOrUpdateAccount(
        account: AccountInfo,
        userId: String = requireUserId(),
        accountKey: String = account.key.ifBlank { "main" }
    ) {
        val safeKey = accountKey.ifBlank { "main" }
        val docRef = db.collection("users").document(userId).collection("accounts").document(safeKey)
        val cleanServer = account.serverName.split("|").firstOrNull()?.ifBlank { "AS08, Hedge" } ?: "AS08, Hedge"
        val cleanDepositDate = account.depositDate.replace("|", " ").ifBlank { "2026.07.15 09:00:00" }
        val cleanLogoType = account.logoType.replace("|", " ").ifBlank { "AUTO" }
        val encodedServerName = "$cleanServer|$cleanDepositDate|$cleanLogoType".take(128)

        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mapOf(
                    "userId" to userId,
                    "balance" to account.balance.safeFinite(0.0),
                    "deposit" to account.deposit.safeFinite(0.0),
                    "realizedProfit" to account.realizedProfit.safeFinite(0.0),
                    "totalSwap" to account.totalSwap.safeFinite(0.0),
                    "totalCommission" to account.totalCommission.safeFinite(0.0),
                    "accountName" to account.accountName.ifBlank { "Pasindu Jeewantha Pelanage" }.take(64),
                    "brokerName" to account.brokerName.ifBlank { "Moneta Markets (Pty) Ltd" }.take(128),
                    "accountId" to account.accountId.ifBlank { "36054607 - MonetaMarkets-Live" }.take(128),
                    "serverName" to encodedServerName,
                    "isReal" to account.isReal,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "balance" to account.balance.safeFinite(0.0),
                    "deposit" to account.deposit.safeFinite(0.0),
                    "realizedProfit" to account.realizedProfit.safeFinite(0.0),
                    "totalSwap" to account.totalSwap.safeFinite(0.0),
                    "totalCommission" to account.totalCommission.safeFinite(0.0),
                    "accountName" to account.accountName.ifBlank { "Pasindu Jeewantha Pelanage" }.take(64),
                    "brokerName" to account.brokerName.ifBlank { "Moneta Markets (Pty) Ltd" }.take(128),
                    "accountId" to account.accountId.ifBlank { "36054607 - MonetaMarkets-Live" }.take(128),
                    "serverName" to encodedServerName,
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

    suspend fun deleteAccountAndData(
        accountKey: String,
        userId: String = requireUserId()
    ) {
        val safeKey = accountKey.ifBlank { "main" }
        val accRef = db.collection("users").document(userId).collection("accounts").document(safeKey)
        try {
            accRef.delete().await()
            clearUserPositions(userId, safeKey)
            clearUserHistory(userId, safeKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, accRef.path)
        }
    }

    suspend fun clearUserPositions(
        userId: String = requireUserId(),
        accountKey: String? = null
    ) {
        val colRef = db.collection("users").document(userId).collection("positions")
        try {
            val snap = colRef.whereEqualTo("userId", userId).get().await()
            for (doc in snap.documents) {
                if (accountKey == null || isDocForAccount(doc.id, "pos", accountKey)) {
                    doc.reference.delete().await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
        }
    }

    suspend fun clearUserHistory(
        userId: String = requireUserId(),
        accountKey: String? = null
    ) {
        val colRef = db.collection("users").document(userId).collection("history")
        try {
            val snap = colRef.whereEqualTo("userId", userId).get().await()
            for (doc in snap.documents) {
                if (accountKey == null || isDocForAccount(doc.id, "hist", accountKey)) {
                    doc.reference.delete().await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
        }
    }
}
