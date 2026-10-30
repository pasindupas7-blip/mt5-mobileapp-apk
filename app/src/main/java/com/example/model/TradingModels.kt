package com.example.model

enum class TickDirection {
    UP, DOWN, NEUTRAL
}

enum class Timeframe(val label: String, val minutes: Int) {
    M1("M1", 1),
    M5("M5", 5),
    M15("M15", 15),
    M30("M30", 30),
    H1("H1", 60),
    H4("H4", 240),
    D1("D1", 1440),
    W1("W1", 10080),
    MN("MN", 43200)
}

data class SymbolQuote(
    val symbol: String,
    val description: String,
    val digits: Int,
    val bid: Double,
    val ask: Double,
    val lastBid: Double = bid,
    val lastAsk: Double = ask,
    val low: Double,
    val high: Double,
    val changePoints: Int,
    val changePercent: Double,
    val spread: Int,
    val time: String,
    val tickDirection: TickDirection = TickDirection.NEUTRAL,
    val bidDirection: TickDirection = tickDirection,
    val askDirection: TickDirection = tickDirection,
    val lastTickTime: Long = System.currentTimeMillis()
)

data class TradePosition(
    val id: Long,
    val symbol: String,
    val isBuy: Boolean,
    val lots: Double,
    val openPrice: Double,
    val currentPrice: Double,
    val sl: Double? = null,
    val tp: Double? = null,
    val profit: Double = 0.0,
    val openTime: String
)

enum class TradeHitOutcome {
    NONE,   // Manual close or neither T/P nor S/L hit (no side line in History)
    TP_HIT, // Take Profit hit first (green line on left in History)
    SL_HIT  // Stop Loss hit first (red line on left in History)
}

data class HistoryTrade(
    val id: Long,
    val symbol: String,
    val isBuy: Boolean,
    val lots: Double,
    val openPrice: Double,
    val closePrice: Double,
    val profit: Double,
    val openTime: String,
    val closeTime: String,
    val commission: Double = 0.00,
    val swap: Double = 0.00,
    val sl: Double? = null,
    val tp: Double? = null,
    val ticketId: String = "#${385000000L + id}",
    val isWithdrawal: Boolean = false,
    val comment: String = "",
    val hitOutcome: TradeHitOutcome? = null
)

data class Candle(
    val time: String,
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

data class AccountInfo(
    val balance: Double = 524.74,
    val equity: Double = 524.74,
    val margin: Double = 0.00,
    val freeMargin: Double = 524.74,
    val marginLevel: Double = 0.00,
    val floatingPnL: Double = 0.00,
    val accountName: String = "Pasindu Jeewantha Pelanage",
    val brokerName: String = "Moneta Markets (Pty) Ltd",
    val accountId: String = "36054607 - MonetaMarkets-Live",
    val serverName: String = "AS08, Hedge",
    val isReal: Boolean = true,
    val deposit: Double = 500.00,
    val realizedProfit: Double = 24.74,
    val totalWithdrawal: Double = 0.00,
    val totalSwap: Double = 0.00,
    val totalCommission: Double = 0.00,
    val key: String = "main",
    val logoType: String = "AUTO"
)

enum class MarketConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    SIMULATED
}

data class PriceTick(
    val symbol: String,
    val rawSymbol: String,
    val price: Double,
    val bid: Double,
    val ask: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents the 1-minute (M1) chart candlestick and intra-minute price movement timestamps
 * for a given symbol and minute bucket, used to synchronize Trade History entry/close times
 * and prices with the live/historical Chart candlesticks.
 */
data class MinuteCandleLookupResult(
    val symbol: String,
    val digits: Int,
    val minuteBucketMs: Long,
    val minuteLabel: String,
    val suggestedTimestamp: String,
    val priceAtSecond: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val openSecondTimestamp: String,
    val highSecondTimestamp: String,
    val lowSecondTimestamp: String,
    val closeSecondTimestamp: String
)

/**
 * Result of scanning 1-minute (M1) chart candlesticks and trade prices from entry time onwards
 * to determine whether T/P or S/L was hit first.
 */
data class TpSlChartCheckResult(
    val outcome: TradeHitOutcome,
    val hitPrice: Double? = null,
    val hitTime: String? = null,
    val forwardHitOutcome: TradeHitOutcome = TradeHitOutcome.NONE,
    val forwardHitPrice: Double? = null,
    val forwardHitTime: String? = null,
    val summaryMessage: String
)


