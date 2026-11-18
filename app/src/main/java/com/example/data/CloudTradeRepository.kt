package com.example.data

import android.content.Context
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
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.math.BigDecimal
import java.util.Date
import java.util.Locale
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

private fun Any?.toSafeDouble(default: Double = 0.0): Double {
    val raw = when (this) {
        is Number -> this.toDouble()
        is String -> this.trim().replace(" ", "").replace(",", "").toDoubleOrNull() ?: default
        else -> default
    }
    return raw.safeFinite(default)
}

private fun Any?.toSafeNullablePositiveDouble(): Double? {
    if (this == null) return null
    val v = this.toSafeDouble(0.0)
    return if (v > 0.0) v else null
}

private fun Any?.toSafeLong(default: Long = 0L): Long {
    return when (this) {
        is Number -> this.toLong()
        is String -> {
            val cleaned = this.trim().removePrefix("#").trim()
            cleaned.toLongOrNull()
                ?: cleaned.toDoubleOrNull()?.toLong()
                ?: cleaned.filter { it.isDigit() }.takeLast(12).toLongOrNull()
                ?: default
        }
        else -> default
    }
}

private fun Any?.toSafeBoolean(): Boolean? {
    return when (this) {
        is Boolean -> this
        is Number -> this.toInt() != 0
        is String -> when (this.trim().uppercase(Locale.US)) {
            "TRUE", "1", "BUY", "B", "LONG", "REAL", "LIVE", "YES" -> true
            "FALSE", "0", "SELL", "S", "SHORT", "DEMO", "NO" -> false
            else -> null
        }
        else -> null
    }
}

private fun Any?.toSafeTimestamp(): Timestamp? {
    return try {
        when (this) {
            is Timestamp -> this
            is Date -> Timestamp(this)
            is Number -> {
                val n = this.toLong()
                if (n > 10_000_000_000L) {
                    Timestamp(Date(n))
                } else if (n > 0L) {
                    Timestamp(n, 0)
                } else {
                    null
                }
            }
            else -> null
        }
    } catch (t: Throwable) {
        null
    }
}

private fun Any?.toSafeDateTimeString(): String {
    return try {
        when (this) {
            is String -> this.trim()
            is Timestamp -> SriLankaMarketHours.formatDateTime(this.toDate().time)
            is Date -> SriLankaMarketHours.formatDateTime(this.time)
            is Number -> {
                val n = this.toLong()
                val ms = if (n in 1..9_999_999_999L) n * 1000L else n
                if (ms > 0L) SriLankaMarketHours.formatDateTime(ms) else ""
            }
            else -> ""
        }
    } catch (t: Throwable) {
        ""
    }
}

private fun resolveUniqueDocDomainId(docId: String, rawTicketId: Any?, fallbackBase: Long): Long {
    val fromTicket = rawTicketId.toSafeLong(0L)
    if (fromTicket > 0L) return fromTicket

    val afterUnderscore = docId.substringAfterLast("_").toLongOrNull()
    if (afterUnderscore != null && afterUnderscore > 0L) return afterUnderscore

    val digitsTail = docId.filter { it.isDigit() }.takeLast(12).toLongOrNull()
    if (digitsTail != null && digitsTail > 0L) return digitsTail

    val positiveHash = (docId.hashCode().toLong() and 0x7FFFFFFFL)
    return if (positiveHash > 0L) fallbackBase + positiveHash else fallbackBase
}

/**
 * Resolves the accurate BUY/SELL direction for a trade.
 * Repairs trades whose boolean `isBuy` field was previously defaulted to `true` by Firestore's JavaBean
 * mapper when `openPrice != closePrice` and `profit != 0.0` (or when S/L or T/P clearly indicates side).
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
    val safeOpen = openPrice.safeFinite(0.0)
    val safeClose = closePrice.safeFinite(0.0)
    val safeProfit = profit.safeFinite(0.0)
    // 1. Definitive mathematical verification from Entry Price, Exit/Current Price, and Profit:
    //    - Price rose (close > open) & profit > 0  => BUY
    //    - Price fell (close < open) & profit > 0  => SELL
    //    - Price fell (close < open) & profit < 0  => BUY
    //    - Price rose (close > open) & profit < 0  => SELL
    if (safeOpen > 0.0 && safeClose > 0.0 && abs(safeClose - safeOpen) > 1e-6 && abs(safeProfit) >= 0.005) {
        return if (safeProfit > 0.0) {
            safeClose > safeOpen
        } else {
            safeClose < safeOpen
        }
    }
    // 2. Definitive verification from Take Profit (T/P) or Stop Loss (S/L) relative to Entry Price:
    val validTp = tp?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
    if (safeOpen > 0.0 && validTp != null && abs(validTp - safeOpen) > 1e-6) {
        return validTp > safeOpen
    }
    val validSl = sl?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
    if (safeOpen > 0.0 && validSl != null && abs(validSl - safeOpen) > 1e-6) {
        return validSl < safeOpen
    }
    // 3. Fallback to explicit side metadata or stored boolean when profit is 0.0 and no SL/TP is set:
    when (explicitSideCode?.trim()?.uppercase(Locale.US)) {
        "SELL", "S", "0", "FALSE", "SHORT" -> return false
        "BUY", "B", "1", "TRUE", "LONG" -> return true
    }
    return storedIsBuy
}

@IgnoreExtraProperties
data class TradePositionDoc(
    val id: String = "",
    val userId: String = "",
    val ticketId: Long = 0L,
    val symbol: String = "",
    @get:PropertyName("isBuy") @set:PropertyName("isBuy") var isBuy: Boolean = true,
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
    fun toDomain(explicitIsBuy: Boolean? = null, explicitSideCode: String? = null): TradePosition {
        val resolvedId = resolveUniqueDocDomainId(id, ticketId, 100_000L)
        val safeOpen = openPrice.safeFinite(1.0).coerceAtLeast(0.00001)
        val safeCurrent = currentPrice.safeFinite(safeOpen).coerceAtLeast(0.00001)
        val safeSl = sl?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
        val safeTp = tp?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
        val safeProfit = profit.safeFinite(0.0)
        val resolvedBuy = resolveAccurateTradeIsBuy(
            storedIsBuy = explicitIsBuy ?: isBuy,
            openPrice = safeOpen,
            closePrice = safeCurrent,
            profit = safeProfit,
            sl = safeSl,
            tp = safeTp,
            explicitSideCode = explicitSideCode
        )
        return TradePosition(
            id = resolvedId,
            symbol = symbol.ifBlank { "XAUUSD" },
            isBuy = resolvedBuy,
            lots = lots.safeFinite(0.01).coerceAtLeast(0.01),
            openPrice = safeOpen,
            currentPrice = safeCurrent,
            sl = safeSl,
            tp = safeTp,
            profit = safeProfit,
            openTime = openTime.ifBlank { SriLankaMarketHours.formatDateTime() }
        )
    }
}

@IgnoreExtraProperties
data class HistoryTradeDoc(
    val id: String = "",
    val userId: String = "",
    val ticketId: Long = 0L,
    val symbol: String = "",
    @get:PropertyName("isBuy") @set:PropertyName("isBuy") var isBuy: Boolean = true,
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
    fun toDomain(
        explicitIsBuy: Boolean? = null,
        explicitSl: Double? = null,
        explicitTp: Double? = null,
        explicitTicketStr: String? = null,
        explicitHitCode: String? = null,
        explicitSideCode: String? = null,
        explicitIsWithdrawal: Boolean? = null,
        explicitComment: String? = null
    ): HistoryTrade {
        val domainId = resolveUniqueDocDomainId(id, ticketId, 200_000L)
        val isWdr = explicitIsWithdrawal == true ||
            symbol.startsWith("WDR:") ||
            symbol.equals("WITHDRAWAL", ignoreCase = true) ||
            symbol.equals("Balance", ignoreCase = true) && profit < 0.0

        if (isWdr) {
            val wdrComment = when {
                !explicitComment.isNullOrBlank() -> explicitComment
                symbol.startsWith("WDR:") -> symbol.removePrefix("WDR:").ifBlank { "Withdrawal" }
                else -> "Withdrawal"
            }
            val openParts = openTime.split("|")
            val parsedOpenTime = openParts.firstOrNull()?.ifBlank { closeTime } ?: closeTime
            val parsedTicket = openParts.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?: explicitTicketStr?.takeIf { it.isNotBlank() }
                ?: "#${if (domainId >= 100000000L) domainId else 385000000L + domainId}"
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

        // Standard closed trade: openTime may optionally encode "openTime|sl|tp|ticketId|hitCode|sideCode"
        val parts = openTime.split("|")
        val cleanOpenTime = parts.firstOrNull()?.ifBlank { closeTime } ?: closeTime
        val parsedSl = parts.getOrNull(1)?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
            ?: explicitSl
        val parsedTp = parts.getOrNull(2)?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
            ?: explicitTp
        val rawTicketPart = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
            ?: explicitTicketStr?.takeIf { it.isNotBlank() }
        val parsedTicket = if (rawTicketPart != null) {
            if (rawTicketPart.startsWith("#")) rawTicketPart else "#$rawTicketPart"
        } else {
            "#${if (domainId >= 100000000L) domainId else 385000000L + domainId}"
        }
        val rawHitCode = parts.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() } ?: explicitHitCode
        val parsedHitOutcome = when (rawHitCode?.trim()?.uppercase(Locale.US)) {
            "TP", "TP_HIT" -> TradeHitOutcome.TP_HIT
            "SL", "SL_HIT" -> TradeHitOutcome.SL_HIT
            "NO", "NONE" -> TradeHitOutcome.NONE
            else -> null
        }
        val parsedSideCode = parts.getOrNull(5)?.trim()?.takeIf { it.isNotBlank() } ?: explicitSideCode
        val safeOpenPrice = openPrice.safeFinite(1.0).coerceAtLeast(0.00001)
        val safeClosePrice = closePrice.safeFinite(1.0).coerceAtLeast(0.00001)
        val safeProfit = profit.safeFinite(0.0)
        val resolvedIsBuy = resolveAccurateTradeIsBuy(
            storedIsBuy = explicitIsBuy ?: isBuy,
            openPrice = safeOpenPrice,
            closePrice = safeClosePrice,
            profit = safeProfit,
            sl = parsedSl,
            tp = parsedTp,
            explicitSideCode = parsedSideCode
        )

        return HistoryTrade(
            id = domainId,
            symbol = symbol.ifBlank { "XAUUSD" },
            isBuy = resolvedIsBuy,
            lots = lots.safeFinite(0.01).coerceAtLeast(0.01),
            openPrice = safeOpenPrice,
            closePrice = safeClosePrice,
            profit = safeProfit,
            openTime = cleanOpenTime,
            closeTime = closeTime,
            commission = commission.safeFinite(0.0),
            swap = swap.safeFinite(0.0),
            sl = parsedSl,
            tp = parsedTp,
            ticketId = parsedTicket,
            isWithdrawal = false,
            comment = explicitComment ?: "",
            hitOutcome = parsedHitOutcome
        )
    }
}

@IgnoreExtraProperties
data class AccountDoc(
    val id: String = "",
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
    @get:PropertyName("isReal") @set:PropertyName("isReal") var isReal: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val explicitDepositDate: String? = null,
    val explicitLogoType: String? = null
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
        val parsedDepositDate = serverParts.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: explicitDepositDate?.takeIf { it.isNotBlank() }
            ?: "2026.07.15 09:00:00"
        val parsedLogoType = serverParts.getOrNull(2)?.takeIf { it.isNotBlank() }
            ?: explicitLogoType?.takeIf { it.isNotBlank() }
            ?: "AUTO"

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
     * Correctly handles both "main" (`pos_1001` or iOS unprefixed IDs) and 3-part secondary account keys (`pos_acc_1762620000000_456_1001`).
     */
    fun isDocForAccount(docId: String, prefix: String, accountKey: String, data: Map<String, Any?>? = null): Boolean {
        val safeKey = accountKey.ifBlank { "main" }
        val explicitAccountKey = (data?.get("accountKey") as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: (data?.get("key") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        if (explicitAccountKey != null) {
            return explicitAccountKey == safeKey
        }
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

    /**
     * Crash-proof manual parser for open positions that works identically for Android and iOS Firestore documents.
     * Supports both plain `openTime` strings with top-level `sl`/`tp` fields and pipe-encoded `"openTime|sl|tp|ticketId"` strings,
     * and reads numeric fields via `(as? Number)?.toDouble()` so integer Long values from the iOS Web SDK are never dropped.
     */
    private fun parsePositionSnapshot(snap: DocumentSnapshot): Pair<TradePositionDoc, TradePosition>? {
        return try {
            if (!snap.exists()) return null
            val data = snap.data ?: return null
            val docId = snap.id

            val rawOpenTimeFull = (snap.getString("openTime") ?: (data["openTime"] ?: data["entryTime"] ?: data["time"]).toSafeDateTimeString()).trim()
            val openParts = rawOpenTimeFull.split("|")
            val cleanOpenTime = openParts.firstOrNull()?.trim().orEmpty()
            val pipeSl = openParts.getOrNull(1)?.trim()?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
            val pipeTp = openParts.getOrNull(2)?.trim()?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
            val pipeTicket = openParts.getOrNull(3)?.removePrefix("#")?.trim()?.takeIf { it.isNotEmpty() }
            val pipeSide = openParts.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }
                ?: openParts.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() }

            val extractedTicketStr = snap.getString("ticketId")?.removePrefix("#")?.trim()?.takeIf { it.isNotEmpty() }
                ?: (snap.get("ticketId") as? Number)?.toLong()?.toString()
                ?: pipeTicket
                ?: docId.removePrefix("pos_").substringAfterLast("_").removePrefix("#").trim()

            val rawTicket: Any = extractedTicketStr.ifEmpty { data["ticketId"] ?: data["ticket"] ?: data["id"] ?: docId.removePrefix("pos_") }
            val ticketLong = resolveUniqueDocDomainId(docId, rawTicket, 100_000L)

            val rawSymbol = (snap.getString("symbol") ?: (data["symbol"] ?: data["pair"] ?: data["instrument"])?.toString())?.trim().orEmpty()
            val rawSideStr = (data["side"] ?: data["type"] ?: data["orderType"] ?: data["direction"] ?: data["action"])?.toString() ?: pipeSide
            val rawBuyBool = (snap.get("isBuy") as? Boolean)
                ?: (snap.get("buy") as? Boolean)
                ?: data["isBuy"].toSafeBoolean()
                ?: data["buy"].toSafeBoolean()
                ?: rawSideStr.toSafeBoolean()

            val lots = ((snap.get("lots") as? Number)?.toDouble() ?: (data["lots"] ?: data["volume"] ?: data["lotSize"]).toSafeDouble(0.01))
                .safeFinite(0.01).coerceAtLeast(0.01)
            val openPrice = ((snap.get("openPrice") as? Number)?.toDouble() ?: (data["openPrice"] ?: data["entryPrice"] ?: data["price"]).toSafeDouble(1.0))
                .safeFinite(1.0).coerceAtLeast(0.00001)
            val currentPrice = ((snap.get("currentPrice") as? Number)?.toDouble() ?: (data["currentPrice"] ?: data["closePrice"] ?: data["marketPrice"]).toSafeDouble(openPrice))
                .safeFinite(openPrice).coerceAtLeast(0.00001)
            val sl = ((snap.get("sl") as? Number)?.toDouble() ?: (data["sl"] ?: data["stopLoss"]).toSafeDouble(0.0))
                .safeFinite(0.0).takeIf { it > 0.0 } ?: pipeSl
            val tp = ((snap.get("tp") as? Number)?.toDouble() ?: (data["tp"] ?: data["takeProfit"]).toSafeDouble(0.0))
                .safeFinite(0.0).takeIf { it > 0.0 } ?: pipeTp
            val profit = ((snap.get("profit") as? Number)?.toDouble() ?: (data["profit"] ?: data["pnl"] ?: data["floatingPnL"]).toSafeDouble(0.0))
                .safeFinite(0.0)
            val createdAt = data["createdAt"].toSafeTimestamp()
            val updatedAt = data["updatedAt"].toSafeTimestamp()

            val doc = TradePositionDoc(
                id = docId,
                userId = (data["userId"] as? String).orEmpty(),
                ticketId = ticketLong,
                symbol = rawSymbol,
                isBuy = rawBuyBool ?: true,
                lots = lots,
                openPrice = openPrice,
                currentPrice = currentPrice,
                sl = sl,
                tp = tp,
                profit = profit,
                openTime = cleanOpenTime,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
            val domain = doc.toDomain(
                explicitIsBuy = rawBuyBool,
                explicitSideCode = rawSideStr
            )
            Pair(doc, domain)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Crash-proof manual parser for historical trades that works identically for Android and iOS Firestore documents.
     * Avoids `DocumentSnapshot.toObject()` so documents containing an `"id"` field or differing iOS types never throw RuntimeException.
     */
    private fun parseHistorySnapshot(snap: DocumentSnapshot): Pair<HistoryTradeDoc, HistoryTrade>? {
        return try {
            if (!snap.exists()) return null
            val data = snap.data ?: return null
            val docId = snap.id
            val extractedTicketStr = snap.getString("ticketId")?.removePrefix("#")?.trim()?.takeIf { it.isNotEmpty() }
                ?: (snap.get("ticketId") as? Number)?.toLong()?.toString()
                ?: docId.removePrefix("hist_").substringAfterLast("_").removePrefix("#").trim()
            val rawTicket = data["ticketId"] ?: data["ticket"] ?: data["orderId"] ?: data["id"] ?: extractedTicketStr
            val ticketLong = resolveUniqueDocDomainId(docId, rawTicket, 200_000L)
            val explicitTicketStr = when {
                extractedTicketStr.isNotEmpty() && extractedTicketStr.all { it.isDigit() } -> "#$extractedTicketStr"
                rawTicket is String && rawTicket.trim().isNotEmpty() -> {
                    val clean = rawTicket.trim().removePrefix("#").trim()
                    if (clean.all { it.isDigit() }) "#$clean" else null
                }
                rawTicket is Number -> "#${rawTicket.toLong()}"
                else -> null
            }
            val rawSymbol = (snap.getString("symbol") ?: (data["symbol"] ?: data["pair"] ?: data["instrument"])?.toString())?.trim().orEmpty()
            val rawSideStr = (data["side"] ?: data["type"] ?: data["orderType"] ?: data["direction"] ?: data["action"])?.toString()
            val rawBuyBool = (snap.get("isBuy") as? Boolean)
                ?: (snap.get("buy") as? Boolean)
                ?: data["isBuy"].toSafeBoolean()
                ?: data["buy"].toSafeBoolean()
                ?: rawSideStr.toSafeBoolean()
            val lots = ((snap.get("lots") as? Number)?.toDouble() ?: (data["lots"] ?: data["volume"] ?: data["lotSize"]).toSafeDouble(0.01))
                .safeFinite(0.01).coerceAtLeast(0.01)
            val openPrice = ((snap.get("openPrice") as? Number)?.toDouble() ?: (data["openPrice"] ?: data["entryPrice"]).toSafeDouble(1.0))
                .safeFinite(1.0).coerceAtLeast(0.00001)
            val closePrice = ((snap.get("closePrice") as? Number)?.toDouble() ?: (data["closePrice"] ?: data["exitPrice"] ?: data["currentPrice"]).toSafeDouble(openPrice))
                .safeFinite(openPrice).coerceAtLeast(0.00001)
            val profit = ((snap.get("profit") as? Number)?.toDouble() ?: (data["profit"] ?: data["pnl"] ?: data["realizedProfit"]).toSafeDouble(0.0))
                .safeFinite(0.0)
            val openTime = (data["openTime"] ?: data["entryTime"]).toSafeDateTimeString()
            val closeTime = (data["closeTime"] ?: data["exitTime"] ?: data["time"]).toSafeDateTimeString()
                .ifBlank { openTime.substringBefore("|") }
            val commission = ((snap.get("commission") as? Number)?.toDouble() ?: (data["commission"] ?: data["comm"]).toSafeDouble(0.0))
                .safeFinite(0.0)
            val swap = ((snap.get("swap") as? Number)?.toDouble() ?: (data["swap"] ?: data["rollover"]).toSafeDouble(0.0))
                .safeFinite(0.0)
            val explicitSl = ((snap.get("sl") as? Number)?.toDouble() ?: (data["sl"] ?: data["stopLoss"]).toSafeDouble(0.0))
                .safeFinite(0.0).takeIf { it > 0.0 }
            val explicitTp = ((snap.get("tp") as? Number)?.toDouble() ?: (data["tp"] ?: data["takeProfit"]).toSafeDouble(0.0))
                .safeFinite(0.0).takeIf { it > 0.0 }
            val explicitHitCode = (data["hitOutcome"] ?: data["hit"] ?: data["closeReason"])?.toString()
            val explicitIsWithdrawal = (snap.get("isWithdrawal") as? Boolean)
                ?: (data["isWithdrawal"] ?: data["withdrawal"]).toSafeBoolean()
                ?: (rawSideStr?.trim()?.equals("WITHDRAWAL", ignoreCase = true) == true)
            val explicitComment = (data["comment"] ?: data["note"] ?: data["description"])?.toString()?.trim()
            val createdAt = data["createdAt"].toSafeTimestamp()
            val updatedAt = data["updatedAt"].toSafeTimestamp()

            val doc = HistoryTradeDoc(
                id = docId,
                userId = (data["userId"] as? String).orEmpty(),
                ticketId = ticketLong,
                symbol = rawSymbol,
                isBuy = rawBuyBool ?: true,
                lots = lots,
                openPrice = openPrice,
                closePrice = closePrice,
                profit = profit,
                openTime = openTime,
                closeTime = closeTime,
                commission = commission,
                swap = swap,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
            val domain = doc.toDomain(
                explicitIsBuy = rawBuyBool,
                explicitSl = explicitSl,
                explicitTp = explicitTp,
                explicitTicketStr = explicitTicketStr,
                explicitHitCode = explicitHitCode,
                explicitSideCode = rawSideStr,
                explicitIsWithdrawal = explicitIsWithdrawal,
                explicitComment = explicitComment
            )
            Pair(doc, domain)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Crash-proof manual parser for account documents that works identically for Android and iOS Firestore documents.
     */
    private fun parseAccountSnapshot(snap: DocumentSnapshot): AccountDoc? {
        return try {
            if (!snap.exists()) return null
            val data = snap.data ?: return null
            val docId = (data["key"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: snap.id.ifBlank { "main" }
            val balance = ((snap.get("balance") as? Number)?.toDouble() ?: (data["balance"]).toSafeDouble(524.74)).safeFinite(524.74)
            val deposit = ((snap.get("deposit") as? Number)?.toDouble() ?: (data["deposit"] ?: data["initialDeposit"] ?: data["baseDeposit"]).toSafeDouble(500.00)).safeFinite(500.00)
            val realizedProfit = ((snap.get("realizedProfit") as? Number)?.toDouble() ?: (data["realizedProfit"] ?: data["profit"]).toSafeDouble(24.74)).safeFinite(24.74)
            val totalSwap = ((snap.get("totalSwap") as? Number)?.toDouble() ?: (data["totalSwap"] ?: data["swap"]).toSafeDouble(0.0)).safeFinite(0.0)
            val totalCommission = ((snap.get("totalCommission") as? Number)?.toDouble() ?: (data["totalCommission"] ?: data["commission"]).toSafeDouble(0.0)).safeFinite(0.0)
            val accountName = (data["accountName"] ?: data["name"])?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "Pasindu Jeewantha Pelanage"
            val brokerName = (data["brokerName"] ?: data["broker"] ?: data["company"])?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "Moneta Markets (Pty) Ltd"
            val accountId = (data["accountId"] ?: data["login"] ?: data["accountNumber"])?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "36054607 - MonetaMarkets-Live"
            val serverName = (data["serverName"] ?: data["server"])?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "AS08, Hedge"
            val isReal = (snap.get("isReal") as? Boolean)
                ?: (snap.get("real") as? Boolean)
                ?: data["isReal"].toSafeBoolean()
                ?: data["real"].toSafeBoolean()
                ?: (data["accountType"]?.toString()?.equals("Demo", ignoreCase = true)?.not())
                ?: true
            val explicitDepositDate = (data["depositDate"] ?: data["initialDepositDate"]).toSafeDateTimeString()
                .takeIf { it.isNotEmpty() }
            val explicitLogoType = (data["logoType"] ?: data["brokerLogo"] ?: data["logo"])?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
            val createdAt = data["createdAt"].toSafeTimestamp()
            val updatedAt = data["updatedAt"].toSafeTimestamp()

            AccountDoc(
                id = docId,
                userId = (data["userId"] as? String).orEmpty(),
                balance = balance,
                deposit = deposit,
                realizedProfit = realizedProfit,
                totalSwap = totalSwap,
                totalCommission = totalCommission,
                accountName = accountName,
                brokerName = brokerName,
                accountId = accountId,
                serverName = serverName,
                isReal = isReal,
                createdAt = createdAt,
                updatedAt = updatedAt,
                explicitDepositDate = explicitDepositDate,
                explicitLogoType = explicitLogoType
            )
        } catch (t: Throwable) {
            null
        }
    }

    private fun mapPositionDocuments(
        documents: List<DocumentSnapshot>,
        safeKey: String
    ): List<TradePosition> {
        return try {
            documents
                .filter { isDocForAccount(it.id, "pos", safeKey, it.data) }
                .mapNotNull { snap -> parsePositionSnapshot(snap) }
                .sortedByDescending { (doc, _) -> doc.createdAt ?: Timestamp(0, 0) }
                .map { (_, domain) -> domain }
                .distinctBy { it.id }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun mapHistoryDocuments(
        documents: List<DocumentSnapshot>,
        safeKey: String
    ): List<HistoryTrade> {
        return try {
            val pairs = documents
                .filter { isDocForAccount(it.id, "hist", safeKey, it.data) }
                .mapNotNull { snap -> parseHistorySnapshot(snap) }
                .sortedBy { (doc, _) -> doc.updatedAt ?: doc.createdAt ?: Timestamp(0, 0) }

            val byDomainId = LinkedHashMap<Long, HistoryTrade>()
            for ((_, domain) in pairs) {
                byDomainId[domain.id] = domain
            }

            byDomainId.values.sortedWith(
                compareBy<HistoryTrade> {
                    SriLankaMarketHours.parseMt5DateTimeMillis(it.closeTime)
                }.thenBy { it.id }
            )
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun observeOpenPositions(
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): Flow<List<TradePosition>> = callbackFlow {
        val safeKey = accountKey.ifBlank { "main" }
        val path = "users/$userId/positions"
        val registration = try {
            db.collection("users").document(userId).collection("positions")
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            runCatching { handleFirestoreError(error, OperationType.LIST, path) }
                            runCatching { close(error) }
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(mapPositionDocuments(snapshot.documents, safeKey))
                        }
                    } catch (ignored: Throwable) {
                    }
                }
        } catch (t: Throwable) {
            runCatching { close(t) }
            null
        }
        awaitClose {
            runCatching { registration?.remove() }
        }
    }

    fun observeTradeHistory(
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): Flow<List<HistoryTrade>> = callbackFlow {
        val safeKey = accountKey.ifBlank { "main" }
        val path = "users/$userId/history"
        val registration = try {
            db.collection("users").document(userId).collection("history")
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            runCatching { handleFirestoreError(error, OperationType.LIST, path) }
                            runCatching { close(error) }
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(mapHistoryDocuments(snapshot.documents, safeKey))
                        }
                    } catch (ignored: Throwable) {
                    }
                }
        } catch (t: Throwable) {
            runCatching { close(t) }
            null
        }
        awaitClose {
            runCatching { registration?.remove() }
        }
    }

    suspend fun fetchOpenPositionsOnce(
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): List<TradePosition> {
        val safeKey = accountKey.ifBlank { "main" }
        val path = "users/$userId/positions"
        return try {
            val col = db.collection("users").document(userId).collection("positions")
            val snap = col.get().await()
            mapPositionDocuments(snap.documents, safeKey)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (t is Exception) {
                runCatching { handleFirestoreError(t, OperationType.LIST, path) }
            }
            emptyList()
        }
    }

    suspend fun fetchTradeHistoryOnce(
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): List<HistoryTrade> {
        val safeKey = accountKey.ifBlank { "main" }
        val path = "users/$userId/history"
        return try {
            val col = db.collection("users").document(userId).collection("history")
            val snap = col.get().await()
            mapHistoryDocuments(snap.documents, safeKey)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (t is Exception) {
                runCatching { handleFirestoreError(t, OperationType.LIST, path) }
            }
            emptyList()
        }
    }

    suspend fun fetchAllAccountsOnce(userId: String = requireUserId()): List<AccountDoc> {
        val path = "users/$userId/accounts"
        return try {
            val col = db.collection("users").document(userId).collection("accounts")
            val snap = col.get().await()
            snap.documents.mapNotNull { parseAccountSnapshot(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (t is Exception) {
                runCatching { handleFirestoreError(t, OperationType.LIST, path) }
            }
            emptyList()
        }
    }

    fun observeAccountDoc(
        userId: String = requireUserId(),
        accountKey: String = "main"
    ): Flow<AccountDoc?> = callbackFlow {
        val safeKey = accountKey.ifBlank { "main" }
        val path = "users/$userId/accounts/$safeKey"
        val registration = try {
            db.collection("users").document(userId).collection("accounts").document(safeKey)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            runCatching { handleFirestoreError(error, OperationType.GET, path) }
                            runCatching { close(error) }
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            trySend(parseAccountSnapshot(snapshot))
                        } else {
                            trySend(null)
                        }
                    } catch (ignored: Throwable) {
                    }
                }
        } catch (t: Throwable) {
            runCatching { close(t) }
            null
        }
        awaitClose {
            runCatching { registration?.remove() }
        }
    }

    fun observeAllAccountDocs(userId: String = requireUserId()): Flow<List<AccountDoc>> = callbackFlow {
        val path = "users/$userId/accounts"
        val registration = try {
            db.collection("users").document(userId).collection("accounts")
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            runCatching { handleFirestoreError(error, OperationType.LIST, path) }
                            runCatching { close(error) }
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val docs = snapshot.documents.mapNotNull { parseAccountSnapshot(it) }
                            trySend(docs)
                        }
                    } catch (ignored: Throwable) {
                    }
                }
        } catch (t: Throwable) {
            runCatching { close(t) }
            null
        }
        awaitClose {
            runCatching { registration?.remove() }
        }
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
        val safeProfit = position.profit.safeFinite(0.0)
        val accurateBuy = resolveAccurateTradeIsBuy(
            storedIsBuy = position.isBuy,
            openPrice = safeOpenPrice,
            closePrice = safeCurrentPrice,
            profit = safeProfit,
            sl = position.sl,
            tp = position.tp
        )

        try {
            val payload = mutableMapOf<String, Any>(
                "userId" to userId,
                "ticketId" to position.id.coerceAtLeast(1L),
                "symbol" to position.symbol.ifBlank { "EURUSD" }.take(32),
                "isBuy" to accurateBuy,
                "lots" to position.lots.safeFinite(0.01).coerceIn(0.01, 1000.0),
                "openPrice" to safeOpenPrice,
                "currentPrice" to safeCurrentPrice,
                "profit" to safeProfit,
                "openTime" to position.openTime.substringBefore("|").ifBlank { SriLankaMarketHours.formatDateTime() }.take(64),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            position.sl?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }?.let { payload["sl"] = it }
            position.tp?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }?.let { payload["tp"] = it }

            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { handleFirestoreError(e, OperationType.WRITE, docRef.path) }
            return docId
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
            runCatching { handleFirestoreError(e, OperationType.UPDATE, docRef.path) }
        }
    }

    suspend fun deleteOpenPosition(
        positionId: Long,
        userId: String = requireUserId(),
        accountKey: String = "main"
    ) {
        val safeKey = accountKey.ifBlank { "main" }
        val cleanTicketId = positionId.toString().removePrefix("#").trim()
        val colRef = db.collection("users").document(userId).collection("positions")
        val candidateIds = linkedSetOf(
            cleanTicketId,
            "pos_$cleanTicketId",
            positionDocId(positionId, safeKey)
        )
        if (positionId in 1L..99_999_999L) {
            val mt5Ticket = (385000000L + positionId).toString()
            candidateIds.add(mt5Ticket)
            candidateIds.add("pos_$mt5Ticket")
        }
        try {
            for (docId in candidateIds) {
                runCatching { colRef.document(docId).delete().await() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { handleFirestoreError(e, OperationType.DELETE, "${colRef.path}/$cleanTicketId") }
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
        val accurateBuy: Boolean

        if (trade.isWithdrawal) {
            val cleanComment = trade.comment.ifBlank { "Withdrawal" }.replace("|", " ").take(24)
            encodedSymbol = "WDR:$cleanComment"
            encodedOpenTime = "$safeCloseTime|${trade.ticketId}".take(160)
            safeLots = 0.01
            safeOpenPrice = 1.0
            safeClosePrice = 1.0
            safeProfit = -abs(trade.profit.safeFinite(0.0))
            accurateBuy = false
        } else {
            safeLots = trade.lots.safeFinite(0.01).coerceIn(0.01, 1000.0)
            safeOpenPrice = trade.openPrice.safeFinite(1.0).coerceAtLeast(0.00001)
            safeClosePrice = trade.closePrice.safeFinite(1.0).coerceAtLeast(0.00001)
            safeProfit = trade.profit.safeFinite(0.0)
            accurateBuy = resolveAccurateTradeIsBuy(
                storedIsBuy = trade.isBuy,
                openPrice = safeOpenPrice,
                closePrice = safeClosePrice,
                profit = safeProfit,
                sl = trade.sl,
                tp = trade.tp
            )
            encodedSymbol = trade.symbol.ifBlank { "XAUUSD" }.take(32)
            val baseOpen = trade.openTime.substringBefore("|").ifBlank { safeCloseTime }.take(24)
            val slStr = formatCompactDouble(trade.sl)
            val tpStr = formatCompactDouble(trade.tp)
            val ticketStr = trade.ticketId.take(18)
            val hitCode = when (trade.hitOutcome) {
                TradeHitOutcome.TP_HIT -> "TP"
                TradeHitOutcome.SL_HIT -> "SL"
                TradeHitOutcome.NONE -> "NO"
                null -> ""
            }
            val sideCode = if (accurateBuy) "BUY" else "SELL"
            val candidateWithSide = "$baseOpen|$slStr|$tpStr|$ticketStr|$hitCode|$sideCode"
            val candidateWithHit = "$baseOpen|$slStr|$tpStr|$ticketStr|$hitCode"
            val candidateOpen = "$baseOpen|$slStr|$tpStr|$ticketStr"
            encodedOpenTime = when {
                candidateWithSide.length <= 160 -> candidateWithSide
                hitCode.isNotEmpty() && candidateWithHit.length <= 160 -> candidateWithHit
                candidateOpen.length <= 160 -> candidateOpen
                else -> baseOpen.take(64)
            }
        }

        return mapOf(
            "userId" to userId,
            "ticketId" to safeTicketId,
            "symbol" to encodedSymbol,
            "isBuy" to accurateBuy,
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
            val payload = buildHistoryPayload(trade, userId, FieldValue.serverTimestamp())
            docRef.set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { handleFirestoreError(e, OperationType.WRITE, docRef.path) }
            return docId
        }
    }

    suspend fun deleteHistoryTrade(
        tradeId: Long,
        userId: String = requireUserId(),
        accountKey: String = "main"
    ) {
        val safeKey = accountKey.ifBlank { "main" }
        val cleanTicketId = tradeId.toString().removePrefix("#").trim()
        val colRef = db.collection("users").document(userId).collection("history")
        val candidateIds = linkedSetOf(
            cleanTicketId,
            "hist_$cleanTicketId",
            historyDocId(tradeId, "", safeKey)
        )
        if (tradeId in 1L..99_999_999L) {
            val mt5Ticket = (385000000L + tradeId).toString()
            candidateIds.add(mt5Ticket)
            candidateIds.add("hist_$mt5Ticket")
        }
        try {
            for (docId in candidateIds) {
                runCatching { colRef.document(docId).delete().await() }
            }
            val matchingDocs = runCatching {
                colRef.whereEqualTo("ticketId", tradeId).get().await().documents
            }.getOrDefault(emptyList())
            for (doc in matchingDocs) {
                if (isDocForAccount(doc.id, "hist", safeKey, doc.data)) {
                    runCatching { doc.reference.delete().await() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { handleFirestoreError(e, OperationType.DELETE, "${colRef.path}/$cleanTicketId") }
        }
    }

    suspend fun getHistoryTradeById(targetUserId: String, docId: String): HistoryTradeDoc? {
        val docRef = db.collection("users").document(targetUserId).collection("history").document(docId)
        try {
            val snap = docRef.get().await()
            return parseHistorySnapshot(snap)?.first
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { handleFirestoreError(e, OperationType.GET, docRef.path) }
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
            val payload = mapOf(
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
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { handleFirestoreError(e, OperationType.WRITE, docRef.path) }
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
            runCatching { handleFirestoreError(e, OperationType.DELETE, accRef.path) }
        }
    }

    suspend fun clearUserPositions(
        userId: String = requireUserId(),
        accountKey: String? = null
    ) {
        val colRef = db.collection("users").document(userId).collection("positions")
        try {
            val snap = colRef.get().await()
            for (doc in snap.documents) {
                if (accountKey == null || isDocForAccount(doc.id, "pos", accountKey, doc.data)) {
                    doc.reference.delete().await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { handleFirestoreError(e, OperationType.DELETE, colRef.path) }
        }
    }

    suspend fun clearUserHistory(
        userId: String = requireUserId(),
        accountKey: String? = null
    ) {
        val colRef = db.collection("users").document(userId).collection("history")
        try {
            val snap = colRef.get().await()
            for (doc in snap.documents) {
                if (accountKey == null || isDocForAccount(doc.id, "hist", accountKey, doc.data)) {
                    doc.reference.delete().await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { handleFirestoreError(e, OperationType.DELETE, colRef.path) }
        }
    }
}
