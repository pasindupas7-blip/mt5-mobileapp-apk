package com.example.data

import android.content.Context
import com.example.model.AccountInfo
import com.example.model.Candle
import com.example.model.HistoryTrade
import com.example.model.MarketConnectionStatus
import com.example.model.MinuteCandleLookupResult
import com.example.model.PriceTick
import com.example.model.SymbolQuote
import com.example.model.TickDirection
import com.example.model.Timeframe
import com.example.model.TpSlChartCheckResult
import com.example.model.TradeHitOutcome
import com.example.model.TradePosition
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TradingRepository(private val scope: CoroutineScope) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
        timeZone = SriLankaMarketHours.SRI_LANKA_TIME_ZONE
    }
    private val dateTimeFormat = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.US).apply {
        timeZone = SriLankaMarketHours.SRI_LANKA_TIME_ZONE
    }

    // Initial Quotes ordered to match the MT5 Android Quotes screen screenshots
    private val initialQuotes = listOf(
        SymbolQuote(
            symbol = "EURUSD",
            description = "Euro vs US Dollar",
            digits = 5,
            bid = 1.12198,
            ask = 1.12200,
            low = 1.12022,
            high = 1.12267,
            changePoints = 259,
            changePercent = 0.23,
            spread = 2,
            time = "06:09:06",
            tickDirection = TickDirection.DOWN,
            bidDirection = TickDirection.DOWN,
            askDirection = TickDirection.DOWN
        ),
        SymbolQuote(
            symbol = "GBPUSD",
            description = "Great Britain Pound vs US Dollar",
            digits = 5,
            bid = 1.33804,
            ask = 1.33809,
            low = 1.33518,
            high = 1.33876,
            changePoints = 140,
            changePercent = 0.10,
            spread = 5,
            time = "06:09:06",
            tickDirection = TickDirection.DOWN,
            bidDirection = TickDirection.DOWN,
            askDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "USDCHF",
            description = "US Dollar vs Swiss Franc",
            digits = 5,
            bid = 0.82374,
            ask = 0.82377,
            low = 0.82321,
            high = 0.82522,
            changePoints = -158,
            changePercent = -0.19,
            spread = 3,
            time = "06:09:06",
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
            askDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "USDJPY",
            description = "US Dollar vs Japanese Yen",
            digits = 3,
            bid = 156.813,
            ask = 156.813,
            low = 156.652,
            high = 157.220,
            changePoints = -13,
            changePercent = -0.01,
            spread = 0,
            time = "06:09:06",
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
            askDirection = TickDirection.DOWN
        ),
        SymbolQuote(
            symbol = "USDCAD",
            description = "US Dollar vs Canadian Dollar",
            digits = 5,
            bid = 1.40054,
            ask = 1.40056,
            low = 1.39987,
            high = 1.40435,
            changePoints = -41,
            changePercent = -0.03,
            spread = 2,
            time = "06:09:10",
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
            askDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "AUDUSD",
            description = "Australian Dollar vs US Dollar",
            digits = 5,
            bid = 0.70307,
            ask = 0.70309,
            low = 0.70188,
            high = 0.70398,
            changePoints = -42,
            changePercent = -0.06,
            spread = 2,
            time = "06:09:08",
            tickDirection = TickDirection.DOWN,
            bidDirection = TickDirection.DOWN,
            askDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "NZDUSD",
            description = "New Zealand Dollar vs US Dollar",
            digits = 5,
            bid = 0.57025,
            ask = 0.57026,
            low = 0.56953,
            high = 0.57072,
            changePoints = 35,
            changePercent = 0.06,
            spread = 1,
            time = "06:09:06",
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
            askDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "AUDNZD",
            description = "Australian Dollar vs New Zealand Dollar",
            digits = 5,
            bid = 1.23286,
            ask = 1.23297,
            low = 1.23134,
            high = 1.23442,
            changePoints = -134,
            changePercent = -0.11,
            spread = 11,
            time = "06:09:08",
            tickDirection = TickDirection.DOWN,
            bidDirection = TickDirection.DOWN,
            askDirection = TickDirection.DOWN
        ),
        SymbolQuote(
            symbol = "AUDCAD",
            description = "Australian Dollar vs Canadian Dollar",
            digits = 5,
            bid = 0.98459,
            ask = 0.98468,
            low = 0.98359,
            high = 0.98775,
            changePoints = -87,
            changePercent = -0.09,
            spread = 9,
            time = "06:09:08",
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
            askDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "XAUUSD",
            description = "Gold Spot (1 Lot = 100 oz)",
            digits = 2,
            bid = 4001.44,
            ask = 4001.59,
            low = 3981.96,
            high = 4017.86,
            changePoints = 698,
            changePercent = 0.42,
            spread = 15,
            time = "06:09:08",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "NDX100",
            description = "US Tech 100 Cash Index",
            digits = 2,
            bid = 29763.58,
            ask = 29765.08,
            low = 27395.47,
            high = 29960.00,
            changePoints = 1043,
            changePercent = 0.35,
            spread = 150,
            time = "06:09:08",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "BTCUSD",
            description = "Bitcoin vs US Dollar",
            digits = 2,
            bid = 81850.00,
            ask = 81853.50,
            low = 79800.00,
            high = 82900.00,
            changePoints = 1450,
            changePercent = 1.80,
            spread = 350,
            time = "06:09:08",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "AUDCHF",
            description = "Australian Dollar vs Swiss Franc",
            digits = 5,
            bid = 0.58177,
            ask = 0.58179,
            low = 0.58126,
            high = 0.58518,
            changePoints = -299,
            changePercent = -0.51,
            spread = 2,
            time = "06:09:08",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "AUDJPY",
            description = "Australian Dollar vs Japanese Yen",
            digits = 3,
            bid = 110.427,
            ask = 110.429,
            low = 110.231,
            high = 111.014,
            changePoints = -401,
            changePercent = -0.36,
            spread = 2,
            time = "06:09:08",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "CADCHF",
            description = "Canadian Dollar vs Swiss Franc",
            digits = 5,
            bid = 0.58718,
            ask = 0.58720,
            low = 0.58552,
            high = 0.58910,
            changePoints = 32,
            changePercent = 0.05,
            spread = 2,
            time = "06:09:08",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "CADJPY",
            description = "Canadian Dollar vs Japanese Yen",
            digits = 3,
            bid = 111.455,
            ask = 111.457,
            low = 111.052,
            high = 111.909,
            changePoints = 235,
            changePercent = 0.21,
            spread = 2,
            time = "06:09:08",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "CHFJPY",
            description = "Swiss Franc vs Japanese Yen",
            digits = 3,
            bid = 189.810,
            ask = 189.814,
            low = 189.365,
            high = 190.103,
            changePoints = 314,
            changePercent = 0.17,
            spread = 4,
            time = "06:09:08",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "EURAUD",
            description = "Euro vs Australian Dollar",
            digits = 5,
            bid = 1.62109,
            ask = 1.62112,
            low = 1.61100,
            high = 1.62340,
            changePoints = 957,
            changePercent = 0.59,
            spread = 3,
            time = "06:09:08",
            tickDirection = TickDirection.DOWN
        ),
        SymbolQuote(
            symbol = "GBXUSD",
            description = "Great Britain Pence vs US Dollar",
            digits = 5,
            bid = 0.01330,
            ask = 0.01340,
            low = 0.01330,
            high = 0.01330,
            changePoints = 0,
            changePercent = 0.00,
            spread = 10,
            time = "10:05:02",
            tickDirection = TickDirection.NEUTRAL
        ),
        SymbolQuote(
            symbol = "USDCLP",
            description = "US Dollar vs Chilean Peso",
            digits = 2,
            bid = 992.85,
            ask = 993.91,
            low = 986.85,
            high = 993.78,
            changePoints = 755,
            changePercent = 0.77,
            spread = 106,
            time = "19:09:59",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "USDCOP",
            description = "US Dollar vs Colombian Peso",
            digits = 2,
            bid = 3153.63,
            ask = 3170.54,
            low = 3140.22,
            high = 3199.54,
            changePoints = -3871,
            changePercent = -1.21,
            spread = 1691,
            time = "20:49:37",
            tickDirection = TickDirection.DOWN
        ),
        SymbolQuote(
            symbol = "USDIDR",
            description = "US Dollar vs Indonesian Rupiah",
            digits = 1,
            bid = 18114.2,
            ask = 18124.8,
            low = 18077.7,
            high = 18142.5,
            changePoints = 17,
            changePercent = 0.01,
            spread = 106,
            time = "22:59:50",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "USDKRW",
            description = "US Dollar vs South Korean Won",
            digits = 2,
            bid = 1323.29,
            ask = 1323.80,
            low = 1319.91,
            high = 1325.60,
            changePoints = -169,
            changePercent = -0.13,
            spread = 51,
            time = "22:59:55",
            tickDirection = TickDirection.DOWN
        ),
        SymbolQuote(
            symbol = "USDTHB",
            description = "US Dollar vs Thai Baht",
            digits = 5,
            bid = 33.73700,
            ask = 33.82300,
            low = 33.67200,
            high = 33.77800,
            changePoints = 4600,
            changePercent = 0.14,
            spread = 8600,
            time = "23:59:59",
            tickDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "USDTWD",
            description = "US Dollar vs Taiwan Dollar",
            digits = 3,
            bid = 32.589,
            ask = 32.608,
            low = 32.564,
            high = 32.621,
            changePoints = -25,
            changePercent = -0.08,
            spread = 19,
            time = "21:59:54",
            tickDirection = TickDirection.DOWN
        )
    )

    private val dailyOpenReferenceBySymbol: MutableMap<String, Double> = initialQuotes.associate { q ->
        val mult = when (q.digits) {
            7 -> 10_000_000.0
            5 -> 100_000.0
            3 -> 1_000.0
            2 -> 100.0
            1 -> 10.0
            else -> 10_000.0
        }
        q.symbol to (q.bid - (q.changePoints.toDouble() / mult))
    }.toMutableMap()

    private val _quotes = MutableStateFlow(initialQuotes)
    val quotes: StateFlow<List<SymbolQuote>> = _quotes.asStateFlow()

    // Start with no open trades; only trades explicitly opened by the user are active
    private val initialPositions = emptyList<TradePosition>()

    private val _positions = MutableStateFlow(initialPositions)
    val positions: StateFlow<List<TradePosition>> = _positions.asStateFlow()

    // Initial historical trades (editable & deletable from About -> Trade History Control)
    private val initialHistory = listOf(
        HistoryTrade(
            id = 9001L,
            symbol = "NDX100",
            isBuy = false,
            lots = 0.01,
            openPrice = 27548.11,
            closePrice = 27471.52,
            profit = 15.32,
            openTime = "2026.07.28 17:11:04",
            closeTime = "2026.07.28 17:14:53",
            commission = 0.00,
            swap = 0.00,
            sl = 27625.57,
            tp = 27395.47,
            ticketId = "#385193325",
            hitOutcome = TradeHitOutcome.NONE
        ),
        HistoryTrade(
            id = 9002L,
            symbol = "NDX100",
            isBuy = false,
            lots = 0.01,
            openPrice = 27493.22,
            closePrice = 27482.51,
            profit = 2.14,
            openTime = "2026.07.28 17:15:17",
            closeTime = "2026.07.28 17:15:37",
            commission = 0.00,
            swap = 0.00,
            sl = null,
            tp = 27483.18,
            ticketId = "#385195553",
            hitOutcome = TradeHitOutcome.TP_HIT
        ),
        HistoryTrade(
            id = 9003L,
            symbol = "NDX100",
            isBuy = true,
            lots = 0.01,
            openPrice = 27729.94,
            closePrice = 27692.72,
            profit = -7.44,
            openTime = "2026.07.29 16:41:08",
            closeTime = "2026.07.29 16:47:14",
            commission = 0.00,
            swap = 0.00,
            sl = 27692.72,
            tp = 27810.00,
            ticketId = "#385341892",
            hitOutcome = TradeHitOutcome.SL_HIT
        ),
        HistoryTrade(
            id = 9004L,
            symbol = "NDX100",
            isBuy = true,
            lots = 0.01,
            openPrice = 28105.58,
            closePrice = 28187.63,
            profit = 16.41,
            openTime = "2026.07.31 17:48:12",
            closeTime = "2026.07.31 17:56:44",
            commission = 0.00,
            swap = 0.00,
            sl = 28020.00,
            tp = 28200.00,
            ticketId = "#385619044",
            hitOutcome = TradeHitOutcome.NONE
        ),
        HistoryTrade(
            id = 9005L,
            symbol = "NDX100",
            isBuy = true,
            lots = 0.01,
            openPrice = 29884.47,
            closePrice = 29881.87,
            profit = -0.52,
            openTime = "2026.08.05 17:12:03",
            closeTime = "2026.08.05 17:15:14",
            commission = 0.00,
            swap = 0.00,
            sl = 29881.87,
            tp = 29960.00,
            ticketId = "#386104218",
            hitOutcome = TradeHitOutcome.SL_HIT
        ),
        HistoryTrade(
            id = 9006L,
            symbol = "NDX100",
            isBuy = true,
            lots = 0.01,
            openPrice = 29617.99,
            closePrice = 29670.14,
            profit = 10.43,
            openTime = "2026.08.07 16:39:45",
            closeTime = "2026.08.07 16:51:22",
            commission = 0.00,
            swap = 0.00,
            sl = 29550.00,
            tp = 29700.00,
            ticketId = "#386419830",
            hitOutcome = TradeHitOutcome.NONE
        ),
        HistoryTrade(
            id = 9007L,
            symbol = "NDX100",
            isBuy = true,
            lots = 0.01,
            openPrice = 29821.56,
            closePrice = 29763.58,
            profit = -11.60,
            openTime = "2026.08.12 16:44:21",
            closeTime = "2026.08.12 16:56:39",
            commission = 0.00,
            swap = 0.00,
            sl = 29763.58,
            tp = 29910.00,
            ticketId = "#386902115",
            hitOutcome = TradeHitOutcome.SL_HIT
        )
    )

    private val _history = MutableStateFlow(initialHistory)
    val history: StateFlow<List<HistoryTrade>> = _history.asStateFlow()

    private val _account = MutableStateFlow(AccountInfo())
    val account: StateFlow<AccountInfo> = _account.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountInfo>>(listOf(AccountInfo()))
    val accounts: StateFlow<List<AccountInfo>> = _accounts.asStateFlow()

    private var activeAccountKey: String = "main"
    private val positionsByAccount = java.util.concurrent.ConcurrentHashMap<String, List<TradePosition>>()
    private val historyByAccount = java.util.concurrent.ConcurrentHashMap<String, List<HistoryTrade>>()

    private val _isCloudSynced = MutableStateFlow(false)
    val isCloudSynced: StateFlow<Boolean> = _isCloudSynced.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private var localStorage: LocalTradeStorage? = null
    private var cloudRepository: CloudTradeRepository? = null
    private var firebaseAuth: FirebaseAuth? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var cloudPositionsJob: Job? = null
    private var cloudHistoryJob: Job? = null
    private var cloudAccountJob: Job? = null
    private var storageInitialized = false

    private val _chartHistoryMoveEnabled = MutableStateFlow(true)
    val chartHistoryMoveEnabled: StateFlow<Boolean> = _chartHistoryMoveEnabled.asStateFlow()

    val fastForexService: FastForexService = FastForexService.getInstance()
    val marketDataService: MarketDataService = MarketDataService.getInstance()
    private val candleAggregator = CandleAggregator(Timeframe.M5)
    val marketConnectionStatus: StateFlow<MarketConnectionStatus> = fastForexService.connectionStatus

    // Chart Candlestick data cache for each symbol and timeframe (14-day history)
    private val candleMap = java.util.concurrent.ConcurrentHashMap<String, MutableList<Candle>>()
    private val loaded14DayKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val _selectedPairCandles = MutableStateFlow<List<Candle>>(emptyList())
    val selectedPairCandles: StateFlow<List<Candle>> = _selectedPairCandles.asStateFlow()

    private var currentActiveSymbol: String = "XAUUSD"
    private var currentActiveTimeframe: Timeframe = Timeframe.M5
    private val formatLock = Any()

    private fun formatTickTime(timestamp: Long): String = synchronized(formatLock) {
        timeFormat.format(Date(timestamp))
    }

    private fun cacheKey(symbol: String, tf: Timeframe): String = "${symbol}_${tf.name}"

    init {
        // Generate initial candlestick history only for the active symbol on startup to keep MainActivity first frame < 5ms
        val activeQuote = initialQuotes.find { it.symbol == currentActiveSymbol } ?: initialQuotes.first()
        val activeLiveBid = fastForexService.getLivePrice(activeQuote.symbol)
        val initialActiveCandles = generateInitialCandles(activeQuote.copy(bid = activeLiveBid), currentActiveTimeframe)
        candleMap[cacheKey(currentActiveSymbol, currentActiveTimeframe)] = initialActiveCandles.toMutableList()
        candleAggregator.seedHistory(initialActiveCandles)
        _selectedPairCandles.value = candleAggregator.getCandles()

        // Pre-populate remaining symbols asynchronously off the main thread
        scope.launch(Dispatchers.Default) {
            initialQuotes.forEach { q ->
                if (q.symbol != currentActiveSymbol) {
                    val key = cacheKey(q.symbol, currentActiveTimeframe)
                    if (!candleMap.containsKey(key)) {
                        val liveBid = fastForexService.getLivePrice(q.symbol)
                        candleMap[key] = generateInitialCandles(q.copy(bid = liveBid), currentActiveTimeframe).toMutableList()
                    }
                }
            }
        }

        // Fetch real 14-day historical OHLC & FastForex time-series in background
        load14DayHistoricalCandles(currentActiveSymbol, currentActiveTimeframe)

        // Prioritize real-time FastForex.io API data feed for the chart and quotes
        fastForexService.setActiveSymbol(currentActiveSymbol)
        fastForexService.setActiveTimeframe(currentActiveTimeframe)
        fastForexService.startPolling()

        scope.launch(Dispatchers.Default) {
            fastForexService.ticks.collect { tick ->
                try {
                    processRealtimeTick(tick)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    // Never allow a single tick exception to terminate the real-time collector
                }
            }
        }

        // Only connect Twelve Data WebSocket feed as secondary stream if actually connected
        scope.launch(Dispatchers.Default) {
            marketDataService.ticks.collect { tick ->
                try {
                    if (marketDataService.connectionStatus.value == MarketConnectionStatus.CONNECTED) {
                        processRealtimeTick(tick)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                }
            }
        }
    }

    private fun mergeCandleListsByBucket(
        baseList: List<Candle>,
        overlayList: List<Candle>,
        timeframe: Timeframe
    ): List<Candle> {
        if (overlayList.isEmpty()) return baseList
        if (baseList.isEmpty()) return overlayList
        val tfIntervalMs = (timeframe.minutes * 60_000L).coerceAtLeast(60_000L)
        val byBucket = LinkedHashMap<Long, Candle>(baseList.size + overlayList.size)
        for (c in baseList) {
            if (c.timestamp > 0L && SriLankaMarketHours.isMarketOpen(c.timestamp)) {
                val b = (c.timestamp / tfIntervalMs) * tfIntervalMs
                byBucket[b] = c.copy(timestamp = b, time = fastForexService.formatCandleTimestamp(b, timeframe))
            }
        }
        for (c in overlayList) {
            if (c.timestamp > 0L && SriLankaMarketHours.isMarketOpen(c.timestamp)) {
                val b = (c.timestamp / tfIntervalMs) * tfIntervalMs
                byBucket[b] = c.copy(timestamp = b, time = fastForexService.formatCandleTimestamp(b, timeframe))
            }
        }
        val sorted = byBucket.values.sortedBy { it.timestamp }.toMutableList()
        // Ensure continuous open == prevClose where adjacent buckets are 1 interval apart in the same price neighborhood
        val unitRange = fastForexService.getNaturalM1Range(currentActiveSymbol) *
            fastForexService.getTimeframeTickScale(timeframe)
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            if (cur.timestamp - prev.timestamp == tfIntervalMs &&
                abs(cur.open - prev.close) > 1e-7 &&
                abs(cur.open - prev.close) <= unitRange * 6.0
            ) {
                val newOpen = prev.close
                sorted[i] = cur.copy(
                    open = newOpen,
                    high = max(cur.high, max(newOpen, cur.close)),
                    low = min(cur.low, min(newOpen, cur.close))
                )
            }
        }
        return sorted
    }

    private fun load14DayHistoricalCandles(symbol: String, timeframe: Timeframe) {
        scope.launch(Dispatchers.IO) {
            try {
                val quote = _quotes.value.find { it.symbol == symbol } ?: initialQuotes.first()
                val liveBid = fastForexService.getLivePrice(symbol).takeIf { it > 0.0 } ?: quote.bid
                val real14dCandles = fastForexService.fetch14DayCandles(symbol, timeframe, liveBid)
                if (real14dCandles.isNotEmpty()) {
                    val key = cacheKey(symbol, timeframe)
                    val unitRange = fastForexService.getNaturalM1Range(symbol) *
                        fastForexService.getTimeframeTickScale(timeframe)
                    val lastReal = real14dCandles.last()

                    // Only append live session candles that formed strictly after the latest real API bucket
                    // and whose open connects cleanly to the latest real API close
                    val existingInMemory = if (currentActiveSymbol == symbol && currentActiveTimeframe == timeframe) {
                        candleAggregator.getCandles().ifEmpty { candleMap[key].orEmpty() }
                    } else {
                        candleMap[key].orEmpty()
                    }
                    val recentLiveOverlay = existingInMemory.filter { c ->
                        c.timestamp > lastReal.timestamp && abs(c.open - lastReal.close) <= unitRange * 2.0
                    }

                    val mergedBase = if (recentLiveOverlay.isNotEmpty()) {
                        mergeCandleListsByBucket(real14dCandles, recentLiveOverlay, timeframe)
                    } else {
                        real14dCandles
                    }
                    val syncedCandles = syncTradesIntoCandles(symbol, timeframe, mergedBase)
                    loaded14DayKeys.add(key)
                    candleMap[key] = syncedCandles.toMutableList()
                    localStorage?.saveCandles(symbol, timeframe.name, syncedCandles)

                    if (currentActiveSymbol == symbol && currentActiveTimeframe == timeframe) {
                        candleAggregator.seedHistory(syncedCandles)
                        val merged = candleAggregator.getCandles()
                        candleMap[key] = merged.toMutableList()
                        _selectedPairCandles.value = merged
                    }

                    val latestRealClose = syncedCandles.last().close
                    if (latestRealClose > 0.0) {
                        val recentWindow = syncedCandles.takeLast(60)
                        val realLow = recentWindow.minOf { it.low }
                        val realHigh = recentWindow.maxOf { it.high }
                        _quotes.update { list ->
                            list.map { q ->
                                if (q.symbol == symbol) {
                                    val spreadVal = (q.ask - q.bid).coerceAtLeast(0.0)
                                    q.copy(
                                        bid = latestRealClose,
                                        ask = latestRealClose + spreadVal,
                                        low = realLow,
                                        high = realHigh
                                    )
                                } else q
                            }
                        }
                    }
                }
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun filterCandlesToChartNow(candles: List<Candle>, timeframe: Timeframe): List<Candle> {
        val intervalMs = (timeframe.minutes * 60_000L).coerceAtLeast(60_000L)
        val clampedNow = SriLankaMarketHours.clampToLastOpenMarketTimestamp(SriLankaMarketHours.chartNowMillis())
        val maxBucket = (clampedNow / intervalMs) * intervalMs
        return candles.filter { it.timestamp <= 0L || it.timestamp <= maxBucket }
    }

    fun setChartHistoryMoveEnabled(enabled: Boolean) {
        _chartHistoryMoveEnabled.value = enabled
        localStorage?.saveChartHistoryMoveEnabled(enabled)
    }

    fun setSelectedSymbol(symbol: String) {
        val cleanSymbol = symbol.trim().uppercase(Locale.US)
        if (cleanSymbol == currentActiveSymbol && _selectedPairCandles.value.isNotEmpty()) {
            return
        }
        val outgoingKey = cacheKey(currentActiveSymbol, currentActiveTimeframe)
        val outgoingCandles = candleAggregator.getCandles()
        if (outgoingCandles.isNotEmpty()) {
            candleMap[outgoingKey] = outgoingCandles.toMutableList()
            localStorage?.saveCandles(currentActiveSymbol, currentActiveTimeframe.name, outgoingCandles)
        }

        currentActiveSymbol = cleanSymbol
        fastForexService.setActiveSymbol(cleanSymbol)
        val key = cacheKey(cleanSymbol, currentActiveTimeframe)
        val q = _quotes.value.find { it.symbol == cleanSymbol } ?: initialQuotes.first()
        val liveBid = fastForexService.getLivePrice(cleanSymbol).takeIf { it > 0.0 } ?: q.bid
        val cached = candleMap[key]?.let { filterCandlesToChartNow(it, currentActiveTimeframe) }
        val saved = filterCandlesToChartNow(
            localStorage?.loadCandles(cleanSymbol, currentActiveTimeframe.name).orEmpty(),
            currentActiveTimeframe
        )
        val initialDisplay = when {
            cached != null && cached.isNotEmpty() -> syncTradesIntoCandles(cleanSymbol, currentActiveTimeframe, cached)
            saved.isNotEmpty() -> {
                syncTradesIntoCandles(cleanSymbol, currentActiveTimeframe, saved)
            }
            else -> {
                val generated = generateInitialCandles(q.copy(bid = liveBid), currentActiveTimeframe)
                candleMap[key] = generated.toMutableList()
                generated
            }
        }
        candleMap[key] = initialDisplay.toMutableList()
        candleAggregator.seedHistory(initialDisplay)
        _selectedPairCandles.value = candleAggregator.getCandles()
        if (!loaded14DayKeys.contains(key)) {
            load14DayHistoricalCandles(cleanSymbol, currentActiveTimeframe)
        }
    }

    fun forceRefreshMarketData() {
        val activeKey = cacheKey(currentActiveSymbol, currentActiveTimeframe)
        loaded14DayKeys.remove(activeKey)
        fastForexService.forceRefresh(currentActiveSymbol)
        load14DayHistoricalCandles(currentActiveSymbol, currentActiveTimeframe)
    }

    fun setSelectedTimeframe(timeframe: Timeframe) {
        if (timeframe == currentActiveTimeframe && _selectedPairCandles.value.isNotEmpty()) {
            return
        }
        val outgoingKey = cacheKey(currentActiveSymbol, currentActiveTimeframe)
        val outgoingCandles = candleAggregator.getCandles()
        if (outgoingCandles.isNotEmpty()) {
            candleMap[outgoingKey] = outgoingCandles.toMutableList()
            localStorage?.saveCandles(currentActiveSymbol, currentActiveTimeframe.name, outgoingCandles)
        }

        currentActiveTimeframe = timeframe
        candleAggregator.setTimeframe(timeframe)
        fastForexService.setActiveTimeframe(timeframe)
        val key = cacheKey(currentActiveSymbol, timeframe)
        val q = _quotes.value.find { it.symbol == currentActiveSymbol } ?: initialQuotes.first()
        val liveBid = fastForexService.getLivePrice(currentActiveSymbol).takeIf { it > 0.0 } ?: q.bid
        val cached = candleMap[key]?.let { filterCandlesToChartNow(it, timeframe) }
        val saved = filterCandlesToChartNow(
            localStorage?.loadCandles(currentActiveSymbol, timeframe.name).orEmpty(),
            timeframe
        )
        val initialDisplay = when {
            cached != null && cached.isNotEmpty() -> syncTradesIntoCandles(currentActiveSymbol, timeframe, cached)
            saved.isNotEmpty() -> {
                syncTradesIntoCandles(currentActiveSymbol, timeframe, saved)
            }
            else -> {
                val generated = generateInitialCandles(q.copy(bid = liveBid), timeframe)
                candleMap[key] = generated.toMutableList()
                generated
            }
        }
        candleMap[key] = initialDisplay.toMutableList()
        candleAggregator.seedHistory(initialDisplay)
        _selectedPairCandles.value = candleAggregator.getCandles()
        if (!loaded14DayKeys.contains(key)) {
            load14DayHistoricalCandles(currentActiveSymbol, timeframe)
        }
    }

    private fun generateInitialCandles(quote: SymbolQuote, tf: Timeframe = Timeframe.M1): List<Candle> {
        val base = fastForexService.build14DayCandlesFromAnchors(
            symbol = quote.symbol,
            timeframe = tf,
            livePrice = quote.bid,
            ffDailySeries = emptyList()
        )
        return syncTradesIntoCandles(quote.symbol, tf, base)
    }

    /**
     * Synchronizes historical trades (`_history`) and running open positions (`_positions`) for [symbol]
     * into the candlestick series for [timeframe] with:
     * 1. Continuous open-market timestamps (zero missing minutes/hours/days on the X-axis).
     * 2. Natural alternating bullish/bearish candlesticks (`buildConnectedCandleBridge`) during and between
     *    trades so there are never robotic single-color diagonal staircases (`//////` or `\\\\\\`) or
     *    disconnected 10-candle islands.
     */
    fun syncTradesIntoCandles(
        symbol: String,
        timeframe: Timeframe,
        baseCandles: List<Candle>
    ): List<Candle> {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        val symbolTrades = _history.value
            .filter { !it.isWithdrawal && it.symbol.equals(cleanSym, ignoreCase = true) }
            .sortedBy { SriLankaMarketHours.parseMt5DateTimeMillis(it.openTime) }
        val symbolPositions = _positions.value.filter { it.symbol.equals(cleanSym, ignoreCase = true) }
        if (symbolTrades.isEmpty() && symbolPositions.isEmpty()) return baseCandles

        val tfIntervalMs = (timeframe.minutes * 60_000L).coerceAtLeast(60_000L)
        val minPoint = fastForexService.getMinPoint(cleanSym)
        val unitRange = fastForexService.getNaturalM1Range(cleanSym) * fastForexService.getTimeframeTickScale(timeframe)

        val bucketMap = LinkedHashMap<Long, Candle>(baseCandles.size + 512)
        for (c in baseCandles) {
            if (c.timestamp > 0L && SriLankaMarketHours.isMarketOpen(c.timestamp)) {
                val b = (c.timestamp / tfIntervalMs) * tfIntervalMs
                if (SriLankaMarketHours.isMarketOpen(b)) {
                    bucketMap[b] = c.copy(timestamp = b, time = fastForexService.formatCandleTimestamp(b, timeframe))
                }
            }
        }

        // Collect valid trade bucket ranges
        data class TradeSpan(
            val openBucket: Long,
            val closeBucket: Long,
            val openPrice: Double,
            val closePrice: Double
        )

        val tradeSpans = ArrayList<TradeSpan>(symbolTrades.size)
        for (trade in symbolTrades) {
            val rawOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(trade.openTime)
            val rawCloseMs = SriLankaMarketHours.parseMt5DateTimeMillis(trade.closeTime)
            if (rawOpenMs <= 0L || trade.openPrice <= 0.0 || trade.closePrice <= 0.0) continue
            val clampedOpenMs = SriLankaMarketHours.clampToLastOpenMarketTimestamp(rawOpenMs)
            var openBucket = (clampedOpenMs / tfIntervalMs) * tfIntervalMs
            while (!SriLankaMarketHours.isMarketOpen(openBucket)) {
                openBucket = SriLankaMarketHours.stepBackwardOpenMarketBucket(openBucket, tfIntervalMs)
            }
            val clampedCloseMs = if (rawCloseMs > 0L) {
                SriLankaMarketHours.clampToLastOpenMarketTimestamp(rawCloseMs)
            } else {
                clampedOpenMs
            }
            var closeBucket = max(openBucket, (clampedCloseMs / tfIntervalMs) * tfIntervalMs)
            while (!SriLankaMarketHours.isMarketOpen(closeBucket)) {
                closeBucket = SriLankaMarketHours.stepBackwardOpenMarketBucket(closeBucket, tfIntervalMs)
            }
            if (closeBucket < openBucket) closeBucket = openBucket
            tradeSpans.add(
                TradeSpan(
                    openBucket = openBucket,
                    closeBucket = closeBucket,
                    openPrice = trade.openPrice,
                    closePrice = trade.closePrice
                )
            )
        }

        // Step 1: If any trade starts earlier than the earliest bucket in bucketMap, prepend continuous
        // open-market buckets back to 45 bars before the earliest trade so there are no missing dates/hours.
        val earliestTradeBucket = tradeSpans.minOfOrNull { it.openBucket }
        val currentEarliestBucket = bucketMap.keys.minOrNull()
        if (earliestTradeBucket != null && currentEarliestBucket != null && earliestTradeBucket < currentEarliestBucket) {
            var leadStartBucket: Long = earliestTradeBucket
            for (k in 0 until 45) {
                leadStartBucket = SriLankaMarketHours.stepBackwardOpenMarketBucket(leadStartBucket, tfIntervalMs)
            }
            val missingBefore = SriLankaMarketHours.buildOpenMarketTimestampsBetween(
                startExclusiveMs = SriLankaMarketHours.stepBackwardOpenMarketBucket(leadStartBucket, tfIntervalMs),
                endExclusiveMs = currentEarliestBucket,
                intervalMs = tfIntervalMs,
                maxSteps = 12_000
            )
            if (missingBefore.isNotEmpty()) {
                val firstTradePrice = tradeSpans.minByOrNull { it.openBucket }?.openPrice
                    ?: bucketMap[currentEarliestBucket]?.open
                    ?: fastForexService.getDefaultPrice(cleanSym)
                val endAnchorPrice = bucketMap[currentEarliestBucket]?.open ?: firstTradePrice
                val bridgedPre = fastForexService.buildConnectedCandleBridge(
                    symbol = cleanSym,
                    timeframe = timeframe,
                    openBuckets = missingBefore,
                    startPrice = firstTradePrice,
                    endPrice = endAnchorPrice
                )
                for (c in bridgedPre) {
                    if (!bucketMap.containsKey(c.timestamp)) {
                        bucketMap[c.timestamp] = c
                    }
                }
            }
        }

        // Step 2: Sort buckets chronologically and fill any internal open-market timestamp gaps
        val sortedInitial = bucketMap.values.sortedBy { it.timestamp }
        val continuousList = ArrayList<Candle>(sortedInitial.size + 256)
        for (c in sortedInitial) {
            val prev = continuousList.lastOrNull()
            if (prev != null && timeframe.minutes < Timeframe.D1.minutes) {
                val gapBuckets = SriLankaMarketHours.buildOpenMarketTimestampsBetween(
                    startExclusiveMs = prev.timestamp,
                    endExclusiveMs = c.timestamp,
                    intervalMs = tfIntervalMs,
                    maxSteps = 8_000
                )
                if (gapBuckets.isNotEmpty()) {
                    val bridged = fastForexService.buildConnectedCandleBridge(
                        symbol = cleanSym,
                        timeframe = timeframe,
                        openBuckets = gapBuckets,
                        startPrice = prev.close,
                        endPrice = c.open
                    )
                    continuousList.addAll(bridged)
                }
            }
            continuousList.add(c)
        }

        if (continuousList.isEmpty()) return baseCandles

        val indexByBucket = HashMap<Long, Int>(continuousList.size * 2)
        for (i in continuousList.indices) {
            indexByBucket[continuousList[i].timestamp] = i
        }

        val isTradeLocked = BooleanArray(continuousList.size)

        // Step 3: Anchor each trade's [openBucket..closeBucket] with realistic alternating candles
        for (span in tradeSpans) {
            val openIdx = indexByBucket[span.openBucket] ?: continue
            val closeIdx = indexByBucket[span.closeBucket] ?: openIdx
            val safeCloseIdx = max(openIdx, closeIdx)

            if (openIdx == safeCloseIdx) {
                val existing = continuousList[openIdx]
                val barOpen = span.openPrice
                val barClose = span.closePrice
                val bodyTop = max(barOpen, barClose)
                val bodyBot = min(barOpen, barClose)
                val wickPad = max(minPoint * 2.0, unitRange * 0.22)
                continuousList[openIdx] = existing.copy(
                    open = barOpen,
                    close = barClose,
                    high = max(bodyTop + wickPad, span.openPrice),
                    low = min(bodyBot - wickPad, span.openPrice).coerceAtLeast(minPoint)
                )
                isTradeLocked[openIdx] = true
            } else {
                val count = safeCloseIdx - openIdx + 1
                val subBuckets = LongArray(count) { k -> continuousList[openIdx + k].timestamp }
                val bridgedTrade = fastForexService.buildConnectedCandleBridge(
                    symbol = cleanSym,
                    timeframe = timeframe,
                    openBuckets = subBuckets,
                    startPrice = span.openPrice,
                    endPrice = span.closePrice
                )
                for (k in bridgedTrade.indices) {
                    val targetIdx = openIdx + k
                    var bar = bridgedTrade[k]
                    if (k == 0) {
                        bar = bar.copy(
                            open = span.openPrice,
                            high = max(bar.high, max(span.openPrice, bar.close)),
                            low = min(bar.low, min(span.openPrice, bar.close)).coerceAtLeast(minPoint)
                        )
                    }
                    if (k == bridgedTrade.lastIndex) {
                        bar = bar.copy(
                            close = span.closePrice,
                            high = max(bar.high, max(bar.open, span.closePrice)),
                            low = min(bar.low, min(bar.open, span.closePrice)).coerceAtLeast(minPoint)
                        )
                    }
                    continuousList[targetIdx] = bar
                    isTradeLocked[targetIdx] = true
                }
            }
        }

        // Step 4: Smoothly bridge lead-in before first trade, gaps between trades, and transition after last trade
        val lockedRanges = ArrayList<IntRange>()
        var idx = 0
        while (idx < continuousList.size) {
            if (isTradeLocked[idx]) {
                val start = idx
                while (idx + 1 < continuousList.size && isTradeLocked[idx + 1]) {
                    idx++
                }
                lockedRanges.add(start..idx)
            }
            idx++
        }

        if (lockedRanges.isNotEmpty() && timeframe.minutes < Timeframe.D1.minutes) {
            // 4a. Bridge lead-in before the very first locked trade range
            val firstRange = lockedRanges.first()
            val firstIdx = firstRange.first
            if (firstIdx > 0) {
                val targetOpen = continuousList[firstIdx].open
                val prevClose = continuousList[firstIdx - 1].close
                val priceGap = abs(targetOpen - prevClose)
                if (priceGap > minPoint * 2.0) {
                    val neededLead = (priceGap / (unitRange * 0.55)).toInt().coerceIn(25, 240)
                    val leadStart = max(0, firstIdx - neededLead)
                    val leadCount = firstIdx - leadStart
                    if (leadCount > 0) {
                        val leadBuckets = LongArray(leadCount) { k -> continuousList[leadStart + k].timestamp }
                        val leadStartPrice = if (leadStart == 0) {
                            fastForexService.deterministicCloseAtBucket(
                                symbol = cleanSym,
                                timeframe = timeframe,
                                bucketTimestampMs = SriLankaMarketHours.stepBackwardOpenMarketBucket(leadBuckets[0], tfIntervalMs),
                                customAnchorPrice = targetOpen
                            )
                        } else {
                            continuousList[leadStart - 1].close
                        }
                        val bridgedLead = fastForexService.buildConnectedCandleBridge(
                            symbol = cleanSym,
                            timeframe = timeframe,
                            openBuckets = leadBuckets,
                            startPrice = leadStartPrice,
                            endPrice = targetOpen
                        )
                        for (k in bridgedLead.indices) {
                            continuousList[leadStart + k] = bridgedLead[k]
                        }
                    }
                }
            }

            // 4b. Bridge between consecutive locked trade ranges
            for (rIdx in 0 until lockedRanges.size - 1) {
                val curRange = lockedRanges[rIdx]
                val nextRange = lockedRanges[rIdx + 1]
                val gapStart = curRange.last + 1
                val gapEnd = nextRange.first - 1
                val startP = continuousList[curRange.last].close
                val endP = continuousList[nextRange.first].open

                if (gapStart > gapEnd) {
                    // Adjacent locked ranges: connect nextRange.first.open to curRange.last.close
                    val nextBar = continuousList[nextRange.first]
                    continuousList[nextRange.first] = nextBar.copy(
                        open = startP,
                        high = max(nextBar.high, max(startP, nextBar.close)),
                        low = min(nextBar.low, min(startP, nextBar.close)).coerceAtLeast(minPoint)
                    )
                } else {
                    val gapCount = gapEnd - gapStart + 1
                    val gapBuckets = LongArray(gapCount) { k -> continuousList[gapStart + k].timestamp }
                    val bridgedMid = fastForexService.buildConnectedCandleBridge(
                        symbol = cleanSym,
                        timeframe = timeframe,
                        openBuckets = gapBuckets,
                        startPrice = startP,
                        endPrice = endP
                    )
                    for (k in bridgedMid.indices) {
                        continuousList[gapStart + k] = bridgedMid[k]
                    }
                }
            }

            // 4c. Bridge transition after the final locked trade range into the subsequent market series
            val lastRange = lockedRanges.last()
            val lastLockedIdx = lastRange.last
            if (lastLockedIdx < continuousList.lastIndex) {
                val startP = continuousList[lastLockedIdx].close
                val nextOpen = continuousList[lastLockedIdx + 1].open
                val priceGap = abs(nextOpen - startP)
                if (priceGap > minPoint * 2.0) {
                    val availableAfter = continuousList.lastIndex - lastLockedIdx
                    val neededAfter = (priceGap / (unitRange * 0.55)).toInt().coerceIn(25, 300)
                    val bridgeCount = min(availableAfter, neededAfter)
                    if (bridgeCount > 0) {
                        val postStart = lastLockedIdx + 1
                        val postEnd = postStart + bridgeCount - 1
                        val targetEndPrice = if (postEnd < continuousList.lastIndex) {
                            continuousList[postEnd + 1].open
                        } else {
                            continuousList.last().close
                        }
                        val postBuckets = LongArray(bridgeCount) { k -> continuousList[postStart + k].timestamp }
                        val bridgedPost = fastForexService.buildConnectedCandleBridge(
                            symbol = cleanSym,
                            timeframe = timeframe,
                            openBuckets = postBuckets,
                            startPrice = startP,
                            endPrice = targetEndPrice
                        )
                        for (k in bridgedPost.indices) {
                            continuousList[postStart + k] = bridgedPost[k]
                        }
                    }
                }
            }
        }

        // Step 5: Anchor running open positions for this symbol
        for (pos in symbolPositions) {
            val rawOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(pos.openTime)
            if (rawOpenMs <= 0L) continue
            val clampedOpenMs = SriLankaMarketHours.clampToLastOpenMarketTimestamp(rawOpenMs)
            val openBucket = (clampedOpenMs / tfIntervalMs) * tfIntervalMs
            val posIdx = indexByBucket[openBucket]
            if (posIdx != null) {
                val existing = continuousList[posIdx]
                if (abs(existing.close - pos.openPrice) <= unitRange * 15.0) {
                    continuousList[posIdx] = existing.copy(
                        high = max(existing.high, pos.openPrice),
                        low = min(existing.low, pos.openPrice).coerceAtLeast(minPoint)
                    )
                }
            }
        }

        // Final continuity pass for intraday candles
        if (timeframe.minutes < Timeframe.D1.minutes && continuousList.size > 1) {
            for (i in 1 until continuousList.size) {
                val prevClose = continuousList[i - 1].close
                val cur = continuousList[i]
                if (cur.open != prevClose && abs(cur.open - prevClose) <= unitRange * 15.0) {
                    continuousList[i] = cur.copy(
                        open = prevClose,
                        high = max(cur.high, max(prevClose, cur.close)),
                        low = min(cur.low, min(prevClose, cur.close)).coerceAtLeast(minPoint)
                    )
                }
            }
        }

        return continuousList
    }

    /**
     * Re-applies trade anchors across cached timeframes for [symbol], persists the updated candles
     * locally, and immediately refreshes the active chart if [symbol] is currently selected.
     */
    fun refreshSymbolCandlesWithTrades(symbol: String) {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        Timeframe.values().forEach { tf ->
            val key = cacheKey(cleanSym, tf)
            val currentList = if (cleanSym == currentActiveSymbol && tf == currentActiveTimeframe) {
                candleAggregator.getCandles().ifEmpty { candleMap[key] }
            } else {
                candleMap[key]
            }
            if (currentList != null && currentList.isNotEmpty()) {
                val synced = syncTradesIntoCandles(cleanSym, tf, currentList)
                candleMap[key] = synced.toMutableList()
                localStorage?.saveCandles(cleanSym, tf.name, synced)
                if (cleanSym == currentActiveSymbol && tf == currentActiveTimeframe) {
                    candleAggregator.seedHistory(synced)
                    _selectedPairCandles.value = candleAggregator.getCandles()
                }
            }
        }
    }

    /**
     * Returns the most recent M1 (1-minute) chart candles for [symbol] (ensuring the M1 cache is populated
     * and synchronized with trades).
     */
    fun getRecentSymbolM1Candles(symbol: String, limit: Int = 30): List<Candle> {
        val cleanSym = symbol.trim().uppercase(Locale.US).ifBlank { "NDX100" }
        val m1Key = cacheKey(cleanSym, Timeframe.M1)
        if (cleanSym == currentActiveSymbol && currentActiveTimeframe == Timeframe.M1) {
            val liveCandles = candleAggregator.getCandles()
            if (liveCandles.isNotEmpty()) {
                candleMap[m1Key] = liveCandles.toMutableList()
            }
        }
        var m1Candles = candleMap[m1Key]
        if (m1Candles == null || m1Candles.isEmpty()) {
            val quote = _quotes.value.find { it.symbol.equals(cleanSym, ignoreCase = true) }
                ?: initialQuotes.first()
            val liveBid = fastForexService.getLivePrice(cleanSym).takeIf { it > 0.0 } ?: quote.bid
            val generated = generateInitialCandles(quote.copy(bid = liveBid), Timeframe.M1)
            candleMap[m1Key] = generated.toMutableList()
            m1Candles = candleMap[m1Key]
        }
        return (m1Candles ?: emptyList()).takeLast(limit).reversed()
    }

    /**
     * Looks up the exact 1-minute (M1) chart candlestick for [symbol] at [dateTimeStr]
     * and computes the intra-minute price movement & second timestamps (`:00s..:59s`) within that 1-minute candle.
     */
    fun lookupMinuteChartCandle(
        symbol: String,
        dateTimeStr: String,
        isOpen: Boolean
    ): MinuteCandleLookupResult? {
        val rawMs = SriLankaMarketHours.parseMt5DateTimeMillis(dateTimeStr)
        if (rawMs <= 0L) return null

        val cleanSym = symbol.trim().uppercase(Locale.US).ifBlank { "NDX100" }
        val quote = _quotes.value.find { it.symbol.equals(cleanSym, ignoreCase = true) }
        val digits = quote?.digits ?: when {
            cleanSym.contains("JPY") -> 3
            cleanSym.contains("XAU") || cleanSym.contains("NDX") || cleanSym.contains("BTC") -> 2
            else -> 5
        }

        val validMs = SriLankaMarketHours.clampToLastOpenMarketTimestamp(rawMs)
        val minuteBucketMs = (validMs / 60_000L) * 60_000L

        val m1Key = cacheKey(cleanSym, Timeframe.M1)
        if (cleanSym == currentActiveSymbol && currentActiveTimeframe == Timeframe.M1) {
            val liveCandles = candleAggregator.getCandles()
            if (liveCandles.isNotEmpty()) {
                candleMap[m1Key] = liveCandles.toMutableList()
            }
        }
        var m1List = candleMap[m1Key]
        if (m1List == null || m1List.isEmpty()) {
            val q = quote ?: initialQuotes.first()
            val liveBid = fastForexService.getLivePrice(cleanSym).takeIf { it > 0.0 } ?: q.bid
            val generated = generateInitialCandles(q.copy(bid = liveBid), Timeframe.M1)
            candleMap[m1Key] = generated.toMutableList()
            m1List = candleMap[m1Key]
        }

        val existingCandle = m1List?.find { (it.timestamp / 60_000L) * 60_000L == minuteBucketMs }
        val candle = if (existingCandle != null) {
            existingCandle
        } else {
            val anchor = m1List?.minByOrNull { abs(it.timestamp - minuteBucketMs) }?.close
                ?: quote?.bid
                ?: fastForexService.getDefaultPrice(cleanSym)
            fastForexService.buildSingleM1CandleAt(cleanSym, minuteBucketMs, anchor)
        }

        // Deterministic intra-minute price movement path across seconds :00 .. :59
        val seed = ((minuteBucketMs / 60_000L) xor cleanSym.hashCode().toLong()).toInt()
        val openSec = 3 + abs(seed % 6)                 // :03s .. :08s
        val firstPeakSec = 16 + abs((seed shr 4) % 12)  // :16s .. :27s
        val secondPeakSec = 35 + abs((seed shr 8) % 13) // :35s .. :47s
        val closeSec = 51 + abs((seed shr 12) % 7)      // :51s .. :57s

        val isBullish = candle.close >= candle.open
        val firstPeakPrice = if (isBullish) candle.low else candle.high
        val secondPeakPrice = if (isBullish) candle.high else candle.low
        val highSec = if (isBullish) secondPeakSec else firstPeakSec
        val lowSec = if (isBullish) firstPeakSec else secondPeakSec

        val hasExplicitSeconds = dateTimeStr.trim().count { it == ':' } >= 2
        val typedSec = ((validMs - minuteBucketMs) / 1000L).toInt().coerceIn(0, 59)
        val effectiveSec = if (hasExplicitSeconds) {
            typedSec
        } else {
            if (isOpen) firstPeakSec else secondPeakSec
        }

        val rawPriceAtSec = when {
            effectiveSec <= openSec -> candle.open
            effectiveSec <= firstPeakSec -> {
                val ratio = (effectiveSec - openSec).toDouble() / (firstPeakSec - openSec).coerceAtLeast(1).toDouble()
                candle.open + (firstPeakPrice - candle.open) * ratio
            }
            effectiveSec <= secondPeakSec -> {
                val ratio = (effectiveSec - firstPeakSec).toDouble() / (secondPeakSec - firstPeakSec).coerceAtLeast(1).toDouble()
                firstPeakPrice + (secondPeakPrice - firstPeakPrice) * ratio
            }
            effectiveSec <= closeSec -> {
                val ratio = (effectiveSec - secondPeakSec).toDouble() / (closeSec - secondPeakSec).coerceAtLeast(1).toDouble()
                secondPeakPrice + (candle.close - secondPeakPrice) * ratio
            }
            else -> candle.close
        }

        val scale = Math.pow(10.0, digits.toDouble())
        fun roundDigits(v: Double): Double = kotlin.math.round(v * scale) / scale

        val minuteFmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).apply {
            timeZone = SriLankaMarketHours.SRI_LANKA_TIME_ZONE
        }
        val minuteLabel = minuteFmt.format(Date(minuteBucketMs))

        return MinuteCandleLookupResult(
            symbol = cleanSym,
            digits = digits,
            minuteBucketMs = minuteBucketMs,
            minuteLabel = minuteLabel,
            suggestedTimestamp = String.format(Locale.US, "%s:%02d", minuteLabel, effectiveSec),
            priceAtSecond = roundDigits(rawPriceAtSec),
            open = roundDigits(candle.open),
            high = roundDigits(candle.high),
            low = roundDigits(candle.low),
            close = roundDigits(candle.close),
            openSecondTimestamp = String.format(Locale.US, "%s:%02d", minuteLabel, openSec),
            highSecondTimestamp = String.format(Locale.US, "%s:%02d", minuteLabel, highSec),
            lowSecondTimestamp = String.format(Locale.US, "%s:%02d", minuteLabel, lowSec),
            closeSecondTimestamp = String.format(Locale.US, "%s:%02d", minuteLabel, closeSec)
        )
    }

    /**
     * Fast price-based inference of whether a trade hit T/P, S/L, or closed manually (NONE).
     */
    fun inferHitOutcomeFromPrices(
        symbol: String,
        isBuy: Boolean,
        openPrice: Double,
        closePrice: Double,
        sl: Double?,
        tp: Double?
    ): TradeHitOutcome {
        val cleanSym = symbol.trim().uppercase(Locale.US).ifBlank { "NDX100" }
        val minPoint = fastForexService.getMinPoint(cleanSym)
        val tol = minPoint * 2.0
        val validSl = sl?.takeIf { it > 0.0 }
        val validTp = tp?.takeIf { it > 0.0 }

        val tpHit = validTp != null && (if (isBuy) closePrice >= validTp - tol else closePrice <= validTp + tol)
        val slHit = validSl != null && (if (isBuy) closePrice <= validSl + tol else closePrice >= validSl - tol)

        return when {
            tpHit && !slHit -> TradeHitOutcome.TP_HIT
            slHit && !tpHit -> TradeHitOutcome.SL_HIT
            tpHit && slHit -> {
                if (abs(closePrice - (validTp ?: 0.0)) <= abs(closePrice - (validSl ?: 0.0))) {
                    TradeHitOutcome.TP_HIT
                } else {
                    TradeHitOutcome.SL_HIT
                }
            }
            else -> TradeHitOutcome.NONE
        }
    }

    /**
     * Resolves the definitive [TradeHitOutcome] for a [HistoryTrade].
     * Uses the trade's stored [HistoryTrade.hitOutcome] if set, or evaluates against chart & trade data.
     */
    fun resolveTradeHitOutcome(trade: HistoryTrade): TradeHitOutcome {
        if (trade.isWithdrawal) return TradeHitOutcome.NONE
        trade.hitOutcome?.let { return it }
        return checkTpSlHitFromChartData(
            symbol = trade.symbol,
            isBuy = trade.isBuy,
            openPrice = trade.openPrice,
            closePrice = trade.closePrice,
            openTime = trade.openTime,
            closeTime = trade.closeTime,
            sl = trade.sl,
            tp = trade.tp,
            strictWithinTradeWindow = true
        ).outcome
    }

    /**
     * Checks the 1-minute (M1) chart candlestick data chronologically starting from [openTime]
     * (at [openPrice]) through [closeTime] (at [closePrice]) — and optionally forward in the chart —
     * to determine whether T/P or S/L was hit FIRST (`mulima`).
     */
    fun checkTpSlHitFromChartData(
        symbol: String,
        isBuy: Boolean,
        openPrice: Double,
        closePrice: Double,
        openTime: String,
        closeTime: String,
        sl: Double?,
        tp: Double?,
        strictWithinTradeWindow: Boolean = false
    ): TpSlChartCheckResult {
        val cleanSym = symbol.trim().uppercase(Locale.US).ifBlank { "NDX100" }
        val validSl = sl?.takeIf { it > 0.0 }
        val validTp = tp?.takeIf { it > 0.0 }

        if (validSl == null && validTp == null) {
            return TpSlChartCheckResult(
                outcome = TradeHitOutcome.NONE,
                summaryMessage = "No T/P or S/L entered • Manual Close (No side line in History)"
            )
        }

        val quote = _quotes.value.find { it.symbol.equals(cleanSym, ignoreCase = true) }
        val digits = quote?.digits ?: when {
            cleanSym.contains("JPY") -> 3
            cleanSym.contains("XAU") || cleanSym.contains("NDX") || cleanSym.contains("BTC") -> 2
            else -> 5
        }
        val minPoint = fastForexService.getMinPoint(cleanSym)
        val tol = minPoint * 2.0
        val naturalRange = fastForexService.getNaturalM1Range(cleanSym)

        val rawOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(openTime)
        val rawCloseMs = SriLankaMarketHours.parseMt5DateTimeMillis(closeTime)
        val validOpenMs = if (rawOpenMs > 0L) {
            SriLankaMarketHours.clampToLastOpenMarketTimestamp(rawOpenMs)
        } else {
            SriLankaMarketHours.clampToLastOpenMarketTimestamp(SriLankaMarketHours.chartNowMillis())
        }
        val validCloseMs = if (rawCloseMs > 0L) {
            SriLankaMarketHours.clampToLastOpenMarketTimestamp(rawCloseMs)
        } else {
            validOpenMs
        }

        val openBucket = (validOpenMs / 60_000L) * 60_000L
        val closeBucket = max(openBucket, (validCloseMs / 60_000L) * 60_000L)
        val openSec = ((validOpenMs - openBucket) / 1000L).toInt().coerceIn(0, 59)
        val closeSec = if (validCloseMs >= closeBucket) {
            ((validCloseMs - closeBucket) / 1000L).toInt().coerceIn(0, 59)
        } else 55

        val m1Key = cacheKey(cleanSym, Timeframe.M1)
        var m1List = candleMap[m1Key]
        if (m1List == null || m1List.isEmpty()) {
            val q = quote ?: initialQuotes.first()
            val liveBid = fastForexService.getLivePrice(cleanSym).takeIf { it > 0.0 } ?: q.bid
            val generated = generateInitialCandles(q.copy(bid = liveBid), Timeframe.M1)
            candleMap[m1Key] = generated.toMutableList()
            m1List = candleMap[m1Key]
        }

        val m1ByBucket = HashMap<Long, Candle>((m1List?.size ?: 0) + 16)
        m1List?.forEach { c ->
            if (c.timestamp > 0L) {
                m1ByBucket[(c.timestamp / 60_000L) * 60_000L] = c
            }
        }

        val minuteFmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).apply {
            timeZone = SriLankaMarketHours.SRI_LANKA_TIME_ZONE
        }
        val fmtPrice = "%.${digits}f"

        fun checkSegmentHit(
            bucketMs: Long,
            t0: Int,
            p0: Double,
            t1: Int,
            p1: Double
        ): Triple<TradeHitOutcome, Double, String>? {
            val segMax = max(p0, p1)
            val segMin = min(p0, p1)
            val tpCrossed = validTp != null && (if (isBuy) segMax >= validTp - tol else segMin <= validTp + tol)
            val slCrossed = validSl != null && (if (isBuy) segMin <= validSl + tol else segMax >= validSl - tol)

            if (!tpCrossed && !slCrossed) return null

            val chosenOutcome: TradeHitOutcome
            val chosenPrice: Double
            if (tpCrossed && !slCrossed) {
                chosenOutcome = TradeHitOutcome.TP_HIT
                chosenPrice = validTp!!
            } else if (slCrossed && !tpCrossed) {
                chosenOutcome = TradeHitOutcome.SL_HIT
                chosenPrice = validSl!!
            } else {
                val distTp = abs((validTp ?: p0) - p0)
                val distSl = abs((validSl ?: p0) - p0)
                if (distTp <= distSl) {
                    chosenOutcome = TradeHitOutcome.TP_HIT
                    chosenPrice = validTp!!
                } else {
                    chosenOutcome = TradeHitOutcome.SL_HIT
                    chosenPrice = validSl!!
                }
            }

            val totalMove = abs(p1 - p0)
            val frac = if (totalMove > 1e-9) {
                (abs(chosenPrice - p0) / totalMove).coerceIn(0.0, 1.0)
            } else {
                0.5
            }
            val hitSec = (t0 + ((t1 - t0) * frac).toInt()).coerceIn(0, 59)
            val minLabel = minuteFmt.format(Date(bucketMs))
            val hitTs = String.format(Locale.US, "%s:%02d", minLabel, hitSec)
            return Triple(chosenOutcome, chosenPrice, hitTs)
        }

        fun buildCandleWaypoints(bucketMs: Long, candle: Candle): List<Pair<Int, Double>> {
            val seed = ((bucketMs / 60_000L) xor cleanSym.hashCode().toLong()).toInt()
            val cOpenSec = 3 + abs(seed % 6)
            val firstPeakSec = 16 + abs((seed shr 4) % 12)
            val secondPeakSec = 35 + abs((seed shr 8) % 13)
            val cCloseSec = 51 + abs((seed shr 12) % 7)
            val isBullish = candle.close >= candle.open
            val firstPeakPrice = if (isBullish) candle.low else candle.high
            val secondPeakPrice = if (isBullish) candle.high else candle.low
            return listOf(
                Pair(cOpenSec, candle.open),
                Pair(firstPeakSec, firstPeakPrice),
                Pair(secondPeakSec, secondPeakPrice),
                Pair(cCloseSec, candle.close)
            )
        }

        val closeHitsTp = validTp != null && (if (isBuy) closePrice >= validTp - tol else closePrice <= validTp + tol)
        val closeHitsSl = validSl != null && (if (isBuy) closePrice <= validSl + tol else closePrice >= validSl - tol)

        val openCandleInCache = m1ByBucket[openBucket]
        val hasRealChartSeriesAtOpen = openCandleInCache != null &&
            abs(openCandleInCache.open - openPrice) <= naturalRange * 6.0

        val stepMinutes = ((closeBucket - openBucket) / 60_000L).coerceIn(0L, 240L).toInt()
        var windowHit: Triple<TradeHitOutcome, Double, String>? = null
        var prevSec = openSec
        var prevPrice = openPrice

        for (step in 0..stepMinutes) {
            val b = openBucket + step * 60_000L
            if (!SriLankaMarketHours.isMarketOpen(b)) continue

            val candleForMinute: Candle = if (hasRealChartSeriesAtOpen && m1ByBucket.containsKey(b) && !strictWithinTradeWindow) {
                val rawC = m1ByBucket[b]!!
                val shift = openPrice - openCandleInCache!!.open
                if (abs(shift) <= naturalRange * 0.5) {
                    rawC
                } else {
                    rawC.copy(
                        open = rawC.open + shift,
                        high = rawC.high + shift,
                        low = (rawC.low + shift).coerceAtLeast(minPoint),
                        close = rawC.close + shift
                    )
                }
            } else {
                val totalSteps = stepMinutes.coerceAtLeast(1)
                val sOpen = if (step == 0) openPrice else openPrice + (closePrice - openPrice) * (step.toDouble() / totalSteps.toDouble())
                val sClose = if (step == stepMinutes) closePrice else openPrice + (closePrice - openPrice) * ((step + 1).toDouble() / totalSteps.toDouble())
                val wick = if (!closeHitsTp && !closeHitsSl) 0.0 else max(minPoint * 2.0, naturalRange * 0.12)
                Candle(
                    time = minuteFmt.format(Date(b)),
                    timestamp = b,
                    open = sOpen,
                    high = max(sOpen, sClose) + wick,
                    low = (min(sOpen, sClose) - wick).coerceAtLeast(minPoint),
                    close = sClose
                )
            }

            val rawWaypoints = buildCandleWaypoints(b, candleForMinute)
            val filteredWaypoints = mutableListOf<Pair<Int, Double>>()

            if (step == 0) {
                filteredWaypoints.add(Pair(openSec, openPrice))
                if (stepMinutes > 0 || (hasRealChartSeriesAtOpen && !strictWithinTradeWindow)) {
                    for (wp in rawWaypoints) {
                        if (wp.first > openSec && (step < stepMinutes || wp.first < closeSec)) {
                            filteredWaypoints.add(wp)
                        }
                    }
                }
            } else {
                for (wp in rawWaypoints) {
                    if (step < stepMinutes || wp.first < closeSec) {
                        filteredWaypoints.add(wp)
                    }
                }
            }

            if (step == stepMinutes) {
                val finalSec = if (step == 0) max(openSec + 1, closeSec).coerceIn(0, 59) else closeSec
                filteredWaypoints.add(Pair(finalSec, closePrice))
            }

            for (idx in 0 until filteredWaypoints.size) {
                val (curSec, curP) = filteredWaypoints[idx]
                val tStart = if (idx == 0) (if (step == 0) openSec else 0) else filteredWaypoints[idx - 1].first
                val pStart = if (idx == 0) prevPrice else filteredWaypoints[idx - 1].second
                val hit = checkSegmentHit(b, tStart, pStart, max(tStart, curSec), curP)
                if (hit != null) {
                    windowHit = hit
                    break
                }
            }

            if (windowHit != null) break
            if (filteredWaypoints.isNotEmpty()) {
                prevSec = filteredWaypoints.last().first
                prevPrice = filteredWaypoints.last().second
            }
        }

        // Also scan forward in the M1 chart candles after closeBucket (up to 180 minutes from openBucket)
        var forwardHit: Triple<TradeHitOutcome, Double, String>? = null
        if (windowHit == null && !strictWithinTradeWindow) {
            val baseAnchor = openCandleInCache?.open ?: m1List?.lastOrNull()?.close ?: openPrice
            val priceShift = openPrice - baseAnchor
            var lastP = closePrice
            for (fwdStep in 1..180) {
                val b = closeBucket + fwdStep * 60_000L
                if (!SriLankaMarketHours.isMarketOpen(b)) continue
                val rawC = m1ByBucket[b] ?: fastForexService.buildSingleM1CandleAt(cleanSym, b, lastP)
                val shiftedC = if (m1ByBucket.containsKey(b) && abs(priceShift) <= naturalRange * 8.0) {
                    rawC.copy(
                        open = rawC.open + priceShift,
                        high = rawC.high + priceShift,
                        low = (rawC.low + priceShift).coerceAtLeast(minPoint),
                        close = rawC.close + priceShift
                    )
                } else {
                    rawC
                }
                val wps = buildCandleWaypoints(b, shiftedC)
                var segStartSec = 0
                var segStartP = lastP
                for ((sec, p) in wps) {
                    val h = checkSegmentHit(b, segStartSec, segStartP, max(segStartSec, sec), p)
                    if (h != null) {
                        forwardHit = h
                        break
                    }
                    segStartSec = sec
                    segStartP = p
                }
                if (forwardHit != null) break
                lastP = shiftedC.close
            }
        }

        if (windowHit != null) {
            val (out, hp, ht) = windowHit
            val pFormatted = String.format(Locale.US, fmtPrice, hp)
            val msg = if (out == TradeHitOutcome.TP_HIT) {
                "Chart Check: T/P ($pFormatted) hit first at $ht \u2192 Green Line"
            } else {
                "Chart Check: S/L ($pFormatted) hit first at $ht \u2192 Red Line"
            }
            return TpSlChartCheckResult(
                outcome = out,
                hitPrice = hp,
                hitTime = ht,
                forwardHitOutcome = out,
                forwardHitPrice = hp,
                forwardHitTime = ht,
                summaryMessage = msg
            )
        }

        val forwardSummary = if (forwardHit != null) {
            val (fOut, fPrice, fTime) = forwardHit
            val fpStr = String.format(Locale.US, fmtPrice, fPrice)
            val label = if (fOut == TradeHitOutcome.TP_HIT) "T/P ($fpStr)" else "S/L ($fpStr)"
            "Within Open..Close: Manual Close (No Line) \u2022 Chart hits $label first at $fTime"
        } else {
            "Chart Check: Neither T/P nor S/L hit \u2192 Manual Close (No Line in History)"
        }

        return TpSlChartCheckResult(
            outcome = TradeHitOutcome.NONE,
            hitPrice = null,
            hitTime = null,
            forwardHitOutcome = forwardHit?.first ?: TradeHitOutcome.NONE,
            forwardHitPrice = forwardHit?.second,
            forwardHitTime = forwardHit?.third,
            summaryMessage = forwardSummary
        )
    }

    /**
     * Dispatches real-time FastForex & market ticks across the application:
     * 1. Quotes Screen: Updates Bid/Ask prices instantly with green/red flash and daily high/low.
     * 2. Chart Screen: Feeds CandleAggregator to update active candlestick and Moving Average.
     * 3. Trade Screen: Recalculates Floating PnL for each open position and net account metrics.
     */
    private fun processRealtimeTick(tick: PriceTick) {
        // Respect Sri Lanka market open hours (Monday 03:30 AM .. Saturday 02:29 AM Asia/Colombo)
        if (!SriLankaMarketHours.isMarketOpen(tick.timestamp)) return
        if (tick.symbol.equals("INRUSD", ignoreCase = true) || tick.symbol.equals("GBXUSD", ignoreCase = true)) return

        val currentQuotes = _quotes.value
        val targetIndex = currentQuotes.indexOfFirst { it.symbol == tick.symbol }
        if (targetIndex == -1) return

        val quote = currentQuotes[targetIndex]
        val spreadDiff = (tick.ask - tick.bid).coerceAtLeast(0.0)
        var effectiveBid = tick.bid
        var effectiveAsk = tick.ask

        if (tick.symbol == currentActiveSymbol) {
            val naturalRange = fastForexService.getNaturalM1Range(tick.symbol) *
                fastForexService.getTimeframeTickScale(currentActiveTimeframe)
            val maxExcursion = naturalRange * 2.5

            val prevCount = candleAggregator.getCandles().size
            effectiveBid = candleAggregator.onTick(tick.bid, tick.timestamp, maxExcursion)
            effectiveAsk = effectiveBid + spreadDiff
            val updatedCandles = candleAggregator.getCandles()
            _selectedPairCandles.value = updatedCandles
            candleMap[cacheKey(currentActiveSymbol, currentActiveTimeframe)] = updatedCandles.toMutableList()
            if (updatedCandles.size != prevCount) {
                localStorage?.saveCandles(currentActiveSymbol, currentActiveTimeframe.name, updatedCandles)
            }
        }

        val pipMultiplier = when (quote.digits) {
            7 -> 10_000_000.0
            5 -> 100_000.0
            3 -> 1_000.0
            2 -> 100.0
            1 -> 10.0
            else -> 10_000.0
        }

        effectiveBid = kotlin.math.round(effectiveBid * pipMultiplier) / pipMultiplier
        effectiveAsk = kotlin.math.round(effectiveAsk * pipMultiplier) / pipMultiplier
        if (effectiveAsk < effectiveBid) {
            effectiveAsk = effectiveBid
        }

        val bidDir = when {
            effectiveBid > quote.bid + 1e-9 -> TickDirection.UP
            effectiveBid < quote.bid - 1e-9 -> TickDirection.DOWN
            else -> if (quote.bidDirection == TickDirection.NEUTRAL) TickDirection.UP else quote.bidDirection
        }

        val askDir = when {
            effectiveAsk > quote.ask + 1e-9 -> TickDirection.UP
            effectiveAsk < quote.ask - 1e-9 -> TickDirection.DOWN
            else -> if (quote.askDirection == TickDirection.NEUTRAL) bidDir else quote.askDirection
        }

        val largePriceShift = abs(effectiveBid - quote.bid) > quote.bid * 0.02
        if (largePriceShift) {
            val prevPct = quote.changePercent / 100.0
            dailyOpenReferenceBySymbol[quote.symbol] = effectiveBid / (1.0 + prevPct).coerceAtLeast(0.5)
        }
        val dailyOpen = dailyOpenReferenceBySymbol[quote.symbol] ?: quote.bid
        val updatedChangePoints = kotlin.math.round((effectiveBid - dailyOpen) * pipMultiplier).toInt()
        val updatedChangePercent = if (dailyOpen > 0.0) {
            kotlin.math.round(((effectiveBid - dailyOpen) / dailyOpen) * 10000.0) / 100.0
        } else {
            quote.changePercent
        }

        val low = if (largePriceShift) effectiveBid else min(quote.low, effectiveBid)
        val high = if (largePriceShift) effectiveAsk else max(quote.high, effectiveAsk)

        val spread = kotlin.math.round((effectiveAsk - effectiveBid) * pipMultiplier).toInt().coerceAtLeast(0)
        val timeStr = formatTickTime(tick.timestamp)

        val updatedQuote = quote.copy(
            bid = effectiveBid,
            ask = effectiveAsk,
            lastBid = quote.bid,
            lastAsk = quote.ask,
            low = low,
            high = high,
            changePoints = updatedChangePoints,
            changePercent = updatedChangePercent,
            spread = spread,
            time = timeStr,
            tickDirection = bidDir,
            bidDirection = bidDir,
            askDirection = askDir,
            lastTickTime = tick.timestamp
        )

        val newQuotesList = currentQuotes.toMutableList()
        newQuotesList[targetIndex] = updatedQuote
        _quotes.value = newQuotesList

        // Trade Screen: dynamically recalculate Floating PnL and account metrics on every tick
        recalculatePositionsAndAccount(newQuotesList)
    }

    private fun roundTo2(value: Double): Double {
        return kotlin.math.round(value * 100.0) / 100.0
    }

    private fun currencyToUsdRate(currency: String, quoteMap: Map<String, SymbolQuote>): Double {
        return when (currency) {
            "USD" -> 1.0
            "EUR" -> quoteMap["EURUSD"]?.bid ?: 1.12400
            "GBP" -> quoteMap["GBPUSD"]?.bid ?: 1.33300
            "AUD" -> quoteMap["AUDUSD"]?.bid ?: 0.71713
            "NZD" -> (quoteMap["AUDUSD"]?.bid ?: 0.71713) / (quoteMap["AUDNZD"]?.bid ?: 1.23458)
            "CAD" -> (quoteMap["AUDUSD"]?.bid ?: 0.71713) / (quoteMap["AUDCAD"]?.bid ?: 0.99077)
            "CHF" -> (quoteMap["AUDUSD"]?.bid ?: 0.71713) / (quoteMap["AUDCHF"]?.bid ?: 0.58177)
            "JPY" -> (quoteMap["AUDUSD"]?.bid ?: 0.71713) / (quoteMap["AUDJPY"]?.bid ?: 110.427)
            "CLP" -> 1.0 / (quoteMap["USDCLP"]?.bid ?: 992.85)
            "COP" -> 1.0 / (quoteMap["USDCOP"]?.bid ?: 3153.63)
            "IDR" -> 1.0 / (quoteMap["USDIDR"]?.bid ?: 18114.2)
            "KRW" -> 1.0 / (quoteMap["USDKRW"]?.bid ?: 1323.29)
            "THB" -> 1.0 / (quoteMap["USDTHB"]?.bid ?: 33.73700)
            "TWD" -> 1.0 / (quoteMap["USDTWD"]?.bid ?: 32.589)
            "GBX" -> quoteMap["GBXUSD"]?.bid ?: 0.01330
            else -> 1.0
        }
    }

    /**
     * Exact 1:500 Leverage Margin (in USD) for any open position:
     * Margin = (Notional Contract Value in USD) / 500.0
     */
    private fun calculatePositionMarginUsd(pos: TradePosition, quoteMap: Map<String, SymbolQuote>): Double {
        val sym = pos.symbol.uppercase(Locale.US)
        val rawMargin = when {
            sym.contains("NDX") || sym.contains("US30") -> {
                // Index CFD: contract size 20 per 1.00 lot with 1:20 index margin (28910.88 * 0.01 = 289.11)
                pos.openPrice * pos.lots
            }
            sym.contains("XAU") -> {
                // Gold: 100 oz per 1.00 lot, Leverage 1:500
                (pos.openPrice * pos.lots * 100.0) / LEVERAGE
            }
            sym.contains("XAG") -> {
                // Silver: 5,000 oz per 1.00 lot, Leverage 1:500
                (pos.openPrice * pos.lots * 5000.0) / LEVERAGE
            }
            sym.contains("BTC") -> {
                // Bitcoin: 1 BTC per 1.00 lot, Leverage 1:500
                (pos.openPrice * pos.lots * 1.0) / LEVERAGE
            }
            sym.contains("ETH") -> {
                // Ethereum: 10 ETH per 1.00 lot, Leverage 1:500
                (pos.openPrice * pos.lots * 10.0) / LEVERAGE
            }
            sym.length == 6 -> {
                val base = sym.substring(0, 3)
                val quote = sym.substring(3, 6)
                val notionalUsd = when {
                    base == "USD" -> pos.lots * 100000.0
                    quote == "USD" -> pos.openPrice * pos.lots * 100000.0
                    else -> pos.lots * 100000.0 * currencyToUsdRate(base, quoteMap)
                }
                notionalUsd / LEVERAGE
            }
            else -> (pos.openPrice * pos.lots * 100000.0) / LEVERAGE
        }
        return roundTo2(rawMargin)
    }

    /**
     * Exact Floating Profit/Loss (in USD) for any open position
     */
    private fun calculatePositionProfitUsd(
        pos: TradePosition,
        currentPrice: Double,
        quoteMap: Map<String, SymbolQuote>
    ): Double {
        val sym = pos.symbol.uppercase(Locale.US)
        val priceDiff = if (pos.isBuy) (currentPrice - pos.openPrice) else (pos.openPrice - currentPrice)
        val rawProfit = when {
            sym.contains("NDX") || sym.contains("US30") -> {
                priceDiff * pos.lots * 20.0
            }
            sym.contains("XAU") -> {
                priceDiff * pos.lots * 100.0
            }
            sym.contains("XAG") -> {
                priceDiff * pos.lots * 5000.0
            }
            sym.contains("BTC") -> {
                priceDiff * pos.lots * 1.0
            }
            sym.contains("ETH") -> {
                priceDiff * pos.lots * 10.0
            }
            sym.length == 6 -> {
                val base = sym.substring(0, 3)
                val quote = sym.substring(3, 6)
                val profitInQuote = priceDiff * pos.lots * 100000.0
                when {
                    quote == "USD" -> profitInQuote
                    base == "USD" -> if (currentPrice > 0.0) profitInQuote / currentPrice else 0.0
                    else -> profitInQuote * currencyToUsdRate(quote, quoteMap)
                }
            }
            else -> priceDiff * pos.lots * 100000.0
        }
        return roundTo2(rawProfit)
    }

    private fun recalculatePositionsAndAccount(currentQuotes: List<SymbolQuote>) {
        val quoteMap = currentQuotes.associateBy { it.symbol }
        var totalFloatingPnL = 0.0
        var totalMargin = 0.0

        val updatedPositions = _positions.value.map { pos ->
            val quote = quoteMap[pos.symbol]
            if (quote != null) {
                val currentPrice = if (pos.isBuy) quote.bid else quote.ask
                val profit = calculatePositionProfitUsd(pos, currentPrice, quoteMap)
                val positionMargin = calculatePositionMarginUsd(pos, quoteMap)
                totalMargin += positionMargin
                totalFloatingPnL += profit

                pos.copy(currentPrice = currentPrice, profit = profit)
            } else {
                val positionMargin = calculatePositionMarginUsd(pos, quoteMap)
                totalMargin += positionMargin
                totalFloatingPnL += pos.profit
                pos
            }
        }

        _positions.value = updatedPositions

        val currentAccount = _account.value
        val deposit = currentAccount.deposit.coerceAtLeast(0.0)
        val currentHistory = _history.value
        val totalRealizedProfit = roundTo2(
            currentHistory.filter { !it.isWithdrawal }.sumOf { it.profit }
        )
        val totalWithdrawals = roundTo2(
            currentHistory.filter { it.isWithdrawal }.sumOf { abs(it.profit) }
        )
        val balance = roundTo2(deposit + totalRealizedProfit - totalWithdrawals)
        val roundedFloating = roundTo2(totalFloatingPnL)
        val roundedMargin = roundTo2(totalMargin)
        val equity = roundTo2(balance + roundedFloating)
        val freeMargin = roundTo2(equity - roundedMargin)
        val marginLevel = if (roundedMargin > 0.0) roundTo2((equity / roundedMargin) * 100.0) else 0.0

        val updatedActiveAccount = currentAccount.copy(
            key = activeAccountKey,
            deposit = deposit,
            realizedProfit = totalRealizedProfit,
            totalWithdrawal = totalWithdrawals,
            balance = balance,
            equity = equity,
            margin = roundedMargin,
            freeMargin = freeMargin,
            marginLevel = marginLevel,
            floatingPnL = roundedFloating,
            totalSwap = 0.00,
            totalCommission = 0.00
        )
        _account.value = updatedActiveAccount
        positionsByAccount[activeAccountKey] = updatedPositions
        historyByAccount[activeAccountKey] = currentHistory

        _accounts.update { list ->
            if (list.any { it.key == activeAccountKey }) {
                list.map { if (it.key == activeAccountKey) updatedActiveAccount else it }
            } else {
                listOf(updatedActiveAccount) + list
            }
        }
    }

    private fun persistActiveAccountLocal() {
        val local = localStorage ?: return
        val key = activeAccountKey.ifBlank { "main" }
        positionsByAccount[key] = _positions.value
        historyByAccount[key] = _history.value
        local.savePositionsForAccount(key, _positions.value)
        local.saveHistoryForAccount(key, _history.value)
        local.saveAccount(_account.value)
        local.saveAccountsList(_accounts.value)
        local.saveActiveAccountKey(key)
    }

    private fun sortHistoryChronologically(list: List<HistoryTrade>): List<HistoryTrade> {
        return list.sortedWith(
            compareBy<HistoryTrade> {
                SriLankaMarketHours.parseMt5DateTimeMillis(it.closeTime)
            }.thenBy { it.id }
        )
    }

    private var nextPositionId = 1001L

    fun initializeStorage(context: Context) {
        if (storageInitialized) return
        storageInitialized = true

        val appContext = context.applicationContext
        val local = LocalTradeStorage(appContext)
        localStorage = local
        _chartHistoryMoveEnabled.value = local.loadChartHistoryMoveEnabled()

        // 1. Restore all accounts and each account's isolated positions & history from local storage
        val savedAccounts = local.loadAccountsList()
        val savedActiveKey = local.loadActiveAccountKey()

        val mainPositions = local.loadPositionsForAccount("main")
        val rawMainHistory = local.loadHistoryForAccount("main")
        val mainHistory = if (!local.hasSeededDefaultHistory()) {
            val existingIds = rawMainHistory.map { it.id }.toSet()
            val merged = initialHistory.filter { it.id !in existingIds } + rawMainHistory
            val sorted = sortHistoryChronologically(merged)
            local.saveHistoryForAccount("main", sorted)
            local.setSeededDefaultHistory(true)
            sorted
        } else {
            sortHistoryChronologically(rawMainHistory)
        }
        positionsByAccount["main"] = mainPositions
        historyByAccount["main"] = mainHistory

        val baseAccounts = if (savedAccounts.isNotEmpty()) {
            savedAccounts
        } else {
            listOf(AccountInfo(key = "main"))
        }

        val hydratedAccounts = baseAccounts.map { acc ->
            val accKey = acc.key.ifBlank { "main" }
            val accPos = if (accKey == "main") {
                mainPositions
            } else {
                local.loadPositionsForAccount(accKey).also { positionsByAccount[accKey] = it }
            }
            val accHist = if (accKey == "main") {
                mainHistory
            } else {
                sortHistoryChronologically(local.loadHistoryForAccount(accKey)).also { historyByAccount[accKey] = it }
            }
            val realized = roundTo2(accHist.filter { !it.isWithdrawal }.sumOf { it.profit })
            val withdrawals = roundTo2(accHist.filter { it.isWithdrawal }.sumOf { abs(it.profit) })
            val dep = acc.deposit.coerceAtLeast(0.0)
            val bal = roundTo2(dep + realized - withdrawals)
            acc.copy(
                key = accKey,
                deposit = dep,
                realizedProfit = realized,
                totalWithdrawal = withdrawals,
                balance = bal,
                equity = bal + accPos.sumOf { it.profit },
                freeMargin = bal + accPos.sumOf { it.profit }
            )
        }

        val resolvedActive = hydratedAccounts.find { it.key == savedActiveKey } ?: hydratedAccounts.first()
        activeAccountKey = resolvedActive.key
        _accounts.value = hydratedAccounts
        _account.value = resolvedActive
        _positions.value = positionsByAccount[activeAccountKey].orEmpty()
        _history.value = historyByAccount[activeAccountKey].orEmpty()

        val maxLocalId = max(
            positionsByAccount.values.flatten().maxOfOrNull { it.id } ?: 1000L,
            historyByAccount.values.flatten().maxOfOrNull { it.id } ?: 9007L
        )
        nextPositionId = max(local.loadNextPositionId(), maxLocalId + 1L)
        recalculatePositionsAndAccount(_quotes.value)
        persistActiveAccountLocal()

        // Restore persisted real chart candles for the active symbol & any traded symbols immediately
        val tradedSymbols = buildSet {
            add(currentActiveSymbol)
            _positions.value.forEach { add(it.symbol.uppercase(Locale.US)) }
            _history.value.filter { !it.isWithdrawal }.forEach { add(it.symbol.uppercase(Locale.US)) }
        }
        for (sym in tradedSymbols) {
            var hasSavedCandlesForSymbol = false
            Timeframe.values().forEach { tf ->
                val savedCandles = filterCandlesToChartNow(local.loadCandles(sym, tf.name), tf)
                if (savedCandles.isNotEmpty()) {
                    hasSavedCandlesForSymbol = true
                    val key = cacheKey(sym, tf)
                    candleMap[key] = savedCandles.toMutableList()
                    if (sym == currentActiveSymbol && tf == currentActiveTimeframe) {
                        candleAggregator.seedHistory(savedCandles)
                        _selectedPairCandles.value = candleAggregator.getCandles()
                    }
                }
            }
            if (hasSavedCandlesForSymbol) {
                refreshSymbolCandlesWithTrades(sym)
            }
        }
        val activeKey = cacheKey(currentActiveSymbol, currentActiveTimeframe)
        if (!loaded14DayKeys.contains(activeKey)) {
            load14DayHistoricalCandles(currentActiveSymbol, currentActiveTimeframe)
        }

        // 2. Attach Firebase Auth & Firestore Cloud Server sync off the main UI thread
        scope.launch(Dispatchers.IO) {
            try {
                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    FirebaseApp.initializeApp(appContext)
                }
                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    val auth = FirebaseAuth.getInstance()
                    val cloud = CloudTradeRepository(appContext)
                    firebaseAuth = auth
                    cloudRepository = cloud

                    val listener = FirebaseAuth.AuthStateListener { fbAuth ->
                        val user = fbAuth.currentUser
                        if (user != null) {
                            _currentUserEmail.value = user.email ?: user.displayName ?: "Cloud Account"
                            _isCloudSynced.value = true
                            startCloudListeners(user.uid, cloud)
                        } else {
                            stopCloudListeners()
                            _currentUserEmail.value = null
                            _isCloudSynced.value = false
                        }
                    }
                    authStateListener = listener
                    auth.addAuthStateListener(listener)
                }
            } catch (e: Exception) {
                // Safely continue with local persistence if Firebase is unavailable in unit test environment
            }
        }
    }

    private fun startCloudListeners(uid: String, cloud: CloudTradeRepository) {
        stopCloudListeners()

        val targetAccKey = activeAccountKey.ifBlank { "main" }
        val initialPositionsSnapshot = _positions.value
        val initialHistorySnapshot = _history.value
        val initialAccountSnapshot = _account.value

        cloudPositionsJob = scope.launch(Dispatchers.IO) {
            for (pos in initialPositionsSnapshot) {
                try {
                    cloud.saveOpenPosition(pos, uid, targetAccKey)
                } catch (ignored: Exception) {
                }
            }

            cloud.observeOpenPositions(uid, targetAccKey)
                .catch { /* Handled by handleFirestoreError */ }
                .collect { serverPositions ->
                    if (activeAccountKey == targetAccKey) {
                        _positions.value = serverPositions
                        val maxId = serverPositions.maxOfOrNull { it.id } ?: 1000L
                        if (maxId >= nextPositionId) {
                            nextPositionId = maxId + 1L
                            localStorage?.saveNextPositionId(nextPositionId)
                        }
                        recalculatePositionsAndAccount(_quotes.value)
                        persistActiveAccountLocal()
                    }
                }
        }

        cloudHistoryJob = scope.launch(Dispatchers.IO) {
            for (hist in initialHistorySnapshot) {
                try {
                    cloud.addHistoryTrade(hist, userId = uid, accountKey = targetAccKey)
                } catch (ignored: Exception) {
                }
            }

            cloud.observeTradeHistory(uid, targetAccKey)
                .catch { /* Handled by handleFirestoreError */ }
                .collect { serverHistory ->
                    if (activeAccountKey == targetAccKey) {
                        val sortedServerHistory = sortHistoryChronologically(serverHistory)
                        _history.value = sortedServerHistory
                        val maxId = sortedServerHistory.maxOfOrNull { it.id } ?: 1000L
                        if (maxId >= nextPositionId) {
                            nextPositionId = maxId + 1L
                            localStorage?.saveNextPositionId(nextPositionId)
                        }
                        recalculatePositionsAndAccount(_quotes.value)
                        persistActiveAccountLocal()
                        refreshSymbolCandlesWithTrades(currentActiveSymbol)
                    }
                }
        }

        cloudAccountJob = scope.launch(Dispatchers.IO) {
            try {
                for (acc in _accounts.value) {
                    cloud.saveOrUpdateAccount(acc, uid, acc.key)
                }
            } catch (ignored: Exception) {
            }

            cloud.observeAccountDoc(uid, targetAccKey)
                .catch { /* Handled by handleFirestoreError */ }
                .collect { accountDoc ->
                    if (accountDoc != null && activeAccountKey == targetAccKey) {
                        val current = _account.value
                        val merged = accountDoc.toDomain(
                            equity = current.equity,
                            margin = current.margin,
                            freeMargin = current.freeMargin,
                            marginLevel = current.marginLevel,
                            floatingPnL = current.floatingPnL
                        ).copy(
                            key = targetAccKey,
                            logoType = current.logoType
                        )
                        _account.value = merged.copy(deposit = merged.deposit.coerceAtLeast(0.0))
                        recalculatePositionsAndAccount(_quotes.value)
                        persistActiveAccountLocal()
                    } else if (accountDoc == null && activeAccountKey == targetAccKey) {
                        try {
                            cloud.saveOrUpdateAccount(_account.value, uid, targetAccKey)
                        } catch (ignored: Exception) {
                        }
                    }
                }
        }
    }

    private fun stopCloudListeners() {
        cloudPositionsJob?.cancel()
        cloudHistoryJob?.cancel()
        cloudAccountJob?.cancel()
        cloudPositionsJob = null
        cloudHistoryJob = null
        cloudAccountJob = null
    }

    fun syncNowWithServer() {
        val uid = firebaseAuth?.currentUser?.uid ?: return
        val cloud = cloudRepository ?: return
        startCloudListeners(uid, cloud)
    }

    private val issuedTicketNumbers = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private var ticketSequenceCursor: Long = 387100000L + ((System.currentTimeMillis() / 1000L) % 1500000L)

    /**
     * Generates a strictly unique 9-digit MT5 Ticket Number (e.g., 387xxxxxx) that is never repeated
     * across any existing history record, open position, or prior generation in this session.
     */
    @Synchronized
    fun generateUniqueTicketNumber(): Long {
        val existingNumbers = buildSet {
            addAll(issuedTicketNumbers)
            _history.value.forEach { item ->
                item.ticketId.removePrefix("#").trim().toLongOrNull()?.let { add(it) }
                add(item.id)
                add(385000000L + item.id)
            }
            _positions.value.forEach { pos ->
                add(pos.id)
                add(if (pos.id >= 100000000L) pos.id else 385000000L + pos.id)
            }
        }
        val maxExisting = existingNumbers.filter { it in 380000000L..999999999L }.maxOrNull() ?: 387100000L
        if (ticketSequenceCursor <= maxExisting) {
            ticketSequenceCursor = maxExisting + (19L..287L).random()
        } else {
            ticketSequenceCursor += (23L..419L).random()
        }
        while (existingNumbers.contains(ticketSequenceCursor)) {
            ticketSequenceCursor += (7L..89L).random()
        }
        issuedTicketNumbers.add(ticketSequenceCursor)
        if (nextPositionId <= ticketSequenceCursor) {
            nextPositionId = ticketSequenceCursor + 1L
            localStorage?.saveNextPositionId(nextPositionId)
        }
        return ticketSequenceCursor
    }

    /**
     * Generates a formatted unique Ticket ID string (e.g., "#387248912").
     */
    fun generateUniqueTicketId(): String {
        return "#${generateUniqueTicketNumber()}"
    }

    // Trade execution functions
    fun executeOrder(symbol: String, isBuy: Boolean, lots: Double, sl: Double? = null, tp: Double? = null): TradePosition {
        val quote = _quotes.value.find { it.symbol == symbol } ?: initialQuotes.first()
        val entryPrice = if (isBuy) quote.ask else quote.bid
        val closePriceNow = if (isBuy) quote.bid else quote.ask
        val newId = generateUniqueTicketNumber()

        val nowFormatted = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val newPos = TradePosition(
            id = newId,
            symbol = symbol,
            isBuy = isBuy,
            lots = lots,
            openPrice = entryPrice,
            currentPrice = closePriceNow,
            sl = sl,
            tp = tp,
            profit = 0.0,
            openTime = nowFormatted
        )

        _positions.update { listOf(newPos) + it }
        recalculatePositionsAndAccount(_quotes.value)
        refreshSymbolCandlesWithTrades(symbol)
        persistActiveAccountLocal()

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val accKey = activeAccountKey
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.saveOpenPosition(_positions.value.first { it.id == newId }, uid, accKey)
                } catch (ignored: Exception) {
                }
            }
        }
        return _positions.value.first { it.id == newId }
    }

    fun closePosition(positionId: Long) {
        val pos = _positions.value.find { it.id == positionId } ?: return
        val quoteMap = _quotes.value.associateBy { it.symbol }
        val quote = quoteMap[pos.symbol]
        val closePrice = if (quote != null) (if (pos.isBuy) quote.bid else quote.ask) else pos.currentPrice
        val exactProfit = calculatePositionProfitUsd(pos, closePrice, quoteMap)
        val closeTimeFormatted = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val formattedTicket = "#${if (pos.id >= 100000000L) pos.id else 385000000L + pos.id}"

        val closedTrade = HistoryTrade(
            id = pos.id,
            symbol = pos.symbol,
            isBuy = pos.isBuy,
            lots = pos.lots,
            openPrice = pos.openPrice,
            closePrice = closePrice,
            profit = exactProfit,
            openTime = pos.openTime,
            closeTime = closeTimeFormatted,
            commission = 0.00,
            swap = 0.00,
            sl = pos.sl,
            tp = pos.tp,
            ticketId = formattedTicket
        )

        _positions.update { it.filter { p -> p.id != positionId } }
        _history.update { sortHistoryChronologically(it + closedTrade) }

        recalculatePositionsAndAccount(_quotes.value)
        refreshSymbolCandlesWithTrades(pos.symbol)
        persistActiveAccountLocal()

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val updatedAccount = _account.value
        val accKey = activeAccountKey
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.deleteOpenPosition(positionId, uid, accKey)
                    cloud.addHistoryTrade(closedTrade, userId = uid, accountKey = accKey)
                    cloud.saveOrUpdateAccount(updatedAccount, uid, accKey)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    fun closePositionPartial(positionId: Long, closeLots: Double) {
        val pos = _positions.value.find { it.id == positionId } ?: return
        val quoteMap = _quotes.value.associateBy { it.symbol }
        val quote = quoteMap[pos.symbol]
        val closePrice = if (quote != null) (if (pos.isBuy) quote.bid else quote.ask) else pos.currentPrice
        val remainingLots = roundTo2(pos.lots - closeLots)
        val closedPartPos = pos.copy(lots = closeLots)
        val proportionalProfit = calculatePositionProfitUsd(closedPartPos, closePrice, quoteMap)
        val closeTimeFormatted = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val formattedTicket = "#${if (pos.id >= 100000000L) pos.id else 385000000L + pos.id}"

        val closedTrade = HistoryTrade(
            id = pos.id,
            symbol = pos.symbol,
            isBuy = pos.isBuy,
            lots = closeLots,
            openPrice = pos.openPrice,
            closePrice = closePrice,
            profit = proportionalProfit,
            openTime = pos.openTime,
            closeTime = closeTimeFormatted,
            commission = 0.00,
            swap = 0.00,
            sl = pos.sl,
            tp = pos.tp,
            ticketId = formattedTicket
        )

        _history.update { sortHistoryChronologically(it + closedTrade) }

        if (remainingLots <= 0.005) {
            _positions.update { it.filter { p -> p.id != positionId } }
        } else {
            _positions.update { list ->
                list.map {
                    if (it.id == positionId) {
                        it.copy(lots = remainingLots)
                    } else it
                }
            }
        }

        recalculatePositionsAndAccount(_quotes.value)
        refreshSymbolCandlesWithTrades(pos.symbol)
        persistActiveAccountLocal()

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val updatedAccount = _account.value
        val suffix = System.currentTimeMillis().toString()
        val accKey = activeAccountKey
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    if (remainingLots <= 0.005) {
                        cloud.deleteOpenPosition(positionId, uid, accKey)
                    } else {
                        val updatedPos = _positions.value.find { it.id == positionId }
                        cloud.updateOpenPositionPartial(
                            positionId = positionId,
                            newLots = remainingLots,
                            currentPrice = closePrice,
                            newProfit = updatedPos?.profit ?: 0.0,
                            userId = uid,
                            accountKey = accKey
                        )
                    }
                    cloud.addHistoryTrade(closedTrade, docSuffix = suffix, userId = uid, accountKey = accKey)
                    cloud.saveOrUpdateAccount(updatedAccount, uid, accKey)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    /**
     * Calculates exact USD profit for a closed historical trade using 1:500 contract math.
     */
    fun calculateHistoricalTradeProfit(
        symbol: String,
        isBuy: Boolean,
        lots: Double,
        openPrice: Double,
        closePrice: Double
    ): Double {
        val quoteMap = _quotes.value.associateBy { it.symbol }
        val dummyPos = TradePosition(
            id = 0L,
            symbol = symbol.trim().uppercase(Locale.US),
            isBuy = isBuy,
            lots = lots,
            openPrice = openPrice,
            currentPrice = closePrice,
            openTime = ""
        )
        return calculatePositionProfitUsd(dummyPos, closePrice, quoteMap)
    }

    /**
     * Adds or updates a running open position (trade without a close time) from the About control tab.
     * If the trade was previously a closed HistoryTrade (`existingId` present in `_history`), it is
     * moved from `_history` into active `_positions` and synced to the Cloud Trade Server.
     */
    fun saveOrUpdateRunningPosition(
        existingId: Long? = null,
        symbol: String,
        isBuy: Boolean,
        lots: Double,
        openPrice: Double,
        openTime: String,
        sl: Double? = null,
        tp: Double? = null,
        customTicketId: String? = null
    ): TradePosition {
        val cleanSymbol = symbol.trim().uppercase(Locale.US).ifBlank { "NDX100" }
        val safeLots = roundTo2(lots.coerceAtLeast(0.01))
        val nowFormatted = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val cleanOpenTime = openTime.trim().ifBlank { nowFormatted }

        val parsedTicketNum = customTicketId?.removePrefix("#")?.trim()?.toLongOrNull()
        val targetId = when {
            existingId != null -> existingId
            parsedTicketNum != null &&
                _positions.value.none { it.id == parsedTicketNum } &&
                _history.value.none { it.id == parsedTicketNum || it.ticketId == "#$parsedTicketNum" } -> {
                issuedTicketNumbers.add(parsedTicketNum)
                parsedTicketNum
            }
            else -> generateUniqueTicketNumber()
        }

        val quote = _quotes.value.find { it.symbol.equals(cleanSymbol, ignoreCase = true) }
        val liveClosePrice = if (quote != null) {
            if (isBuy) quote.bid else quote.ask
        } else {
            openPrice
        }

        val wasInHistory = existingId != null && _history.value.any { it.id == existingId }
        if (wasInHistory && existingId != null) {
            _history.update { list -> list.filter { it.id != existingId } }
        }

        val runningPos = TradePosition(
            id = targetId,
            symbol = cleanSymbol,
            isBuy = isBuy,
            lots = safeLots,
            openPrice = openPrice,
            currentPrice = liveClosePrice,
            sl = sl?.takeIf { it > 0.0 },
            tp = tp?.takeIf { it > 0.0 },
            profit = 0.0,
            openTime = cleanOpenTime
        )

        _positions.update { currentList ->
            if (currentList.any { it.id == targetId }) {
                currentList.map { if (it.id == targetId) runningPos else it }
            } else {
                listOf(runningPos) + currentList
            }
        }

        recalculatePositionsAndAccount(_quotes.value)
        refreshSymbolCandlesWithTrades(cleanSymbol)
        persistActiveAccountLocal()

        val savedPosition = _positions.value.find { it.id == targetId } ?: runningPos
        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val updatedAccount = _account.value
        val accKey = activeAccountKey
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    if (wasInHistory && existingId != null) {
                        cloud.deleteHistoryTrade(existingId, uid, accKey)
                    }
                    cloud.saveOpenPosition(savedPosition, uid, accKey)
                    cloud.saveOrUpdateAccount(updatedAccount, uid, accKey)
                } catch (ignored: Exception) {
                }
            }
        }
        return savedPosition
    }

    /**
     * Deletes an open running position without saving it to closed history, and syncs with Cloud.
     */
    fun deleteRunningPosition(positionId: Long) {
        val removedSym = _positions.value.find { it.id == positionId }?.symbol
        _positions.update { list -> list.filter { it.id != positionId } }
        recalculatePositionsAndAccount(_quotes.value)
        if (removedSym != null) {
            refreshSymbolCandlesWithTrades(removedSym)
        }
        persistActiveAccountLocal()

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val updatedAccount = _account.value
        val accKey = activeAccountKey
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.deleteOpenPosition(positionId, uid, accKey)
                    cloud.saveOrUpdateAccount(updatedAccount, uid, accKey)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    /**
     * Adds a new trade or edits an existing trade in the History list, keeps the list sorted by date,
     * and auto-saves to both Local Storage and the Cloud Trade Server (Firestore).
     */
    fun saveOrUpdateHistoryTrade(
        existingId: Long? = null,
        symbol: String,
        isBuy: Boolean,
        lots: Double,
        openPrice: Double,
        closePrice: Double,
        openTime: String,
        closeTime: String,
        sl: Double? = null,
        tp: Double? = null,
        customProfit: Double? = null,
        customTicketId: String? = null,
        hitOutcome: TradeHitOutcome? = null
    ): HistoryTrade {
        val uniqueTicketNum = if (existingId == null) generateUniqueTicketNumber() else null
        val targetId = existingId ?: uniqueTicketNum ?: nextPositionId++
        if (existingId == null) {
            localStorage?.saveNextPositionId(nextPositionId)
        }
        val cleanSymbol = symbol.trim().uppercase(Locale.US).ifBlank { "NDX100" }
        val safeLots = roundTo2(lots.coerceAtLeast(0.01))
        val computedProfit = customProfit?.let { roundTo2(it) }
            ?: calculateHistoricalTradeProfit(cleanSymbol, isBuy, safeLots, openPrice, closePrice)
        val nowFormatted = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val cleanCloseTime = closeTime.trim().ifBlank { nowFormatted }
        val cleanOpenTime = openTime.trim().ifBlank { cleanCloseTime }

        val candidateTicket = customTicketId?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("#")) it else "#$it"
        }
        val ticketTakenByOther = candidateTicket != null && (
            _history.value.any { it.id != targetId && it.ticketId.equals(candidateTicket, ignoreCase = true) } ||
                _positions.value.any {
                    it.id != targetId &&
                        "#${if (it.id >= 100000000L) it.id else 385000000L + it.id}".equals(candidateTicket, ignoreCase = true)
                }
            )
        val formattedTicket = if (candidateTicket != null && !ticketTakenByOther) {
            candidateTicket.removePrefix("#").toLongOrNull()?.let { issuedTicketNumbers.add(it) }
            candidateTicket
        } else {
            val freshNum = uniqueTicketNum ?: generateUniqueTicketNumber()
            "#$freshNum"
        }

        // If this trade was previously a running open position, remove it from open positions
        val wasRunningPosition = existingId != null && _positions.value.any { it.id == existingId }
        if (wasRunningPosition && existingId != null) {
            _positions.update { list -> list.filter { it.id != existingId } }
        }

        val cleanSl = sl?.takeIf { it > 0.0 }
        val cleanTp = tp?.takeIf { it > 0.0 }
        val resolvedOutcome = hitOutcome ?: checkTpSlHitFromChartData(
            symbol = cleanSymbol,
            isBuy = isBuy,
            openPrice = openPrice,
            closePrice = closePrice,
            openTime = cleanOpenTime,
            closeTime = cleanCloseTime,
            sl = cleanSl,
            tp = cleanTp
        ).outcome

        val tradeRecord = HistoryTrade(
            id = targetId,
            symbol = cleanSymbol,
            isBuy = isBuy,
            lots = safeLots,
            openPrice = openPrice,
            closePrice = closePrice,
            profit = computedProfit,
            openTime = cleanOpenTime,
            closeTime = cleanCloseTime,
            commission = 0.00,
            swap = 0.00,
            sl = cleanSl,
            tp = cleanTp,
            ticketId = formattedTicket,
            isWithdrawal = false,
            comment = "",
            hitOutcome = resolvedOutcome
        )

        _history.update { currentList ->
            val withoutOld = currentList.filter { it.id != targetId }
            sortHistoryChronologically(withoutOld + tradeRecord)
        }

        recalculatePositionsAndAccount(_quotes.value)
        refreshSymbolCandlesWithTrades(cleanSymbol)
        persistActiveAccountLocal()

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val updatedAccount = _account.value
        val accKey = activeAccountKey
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    if (wasRunningPosition && existingId != null) {
                        cloud.deleteOpenPosition(existingId, uid, accKey)
                    }
                    cloud.addHistoryTrade(tradeRecord, userId = uid, accountKey = accKey)
                    cloud.saveOrUpdateAccount(updatedAccount, uid, accKey)
                } catch (ignored: Exception) {
                }
            }
        }
        return tradeRecord
    }

    /**
     * Adds a new Withdrawal or edits an existing Withdrawal in the History list, keeps the list sorted
     * by date, and auto-saves to both Local Storage and the Cloud Trade Server (Firestore).
     */
    fun saveOrUpdateWithdrawal(
        existingId: Long? = null,
        amount: Double,
        closeTime: String,
        comment: String = "Withdrawal",
        customTicketId: String? = null
    ): HistoryTrade {
        val uniqueTicketNum = if (existingId == null) generateUniqueTicketNumber() else null
        val targetId = existingId ?: uniqueTicketNum ?: nextPositionId++
        if (existingId == null) {
            localStorage?.saveNextPositionId(nextPositionId)
        }
        val cleanAmount = roundTo2(abs(amount).coerceAtLeast(0.01))
        val nowFormatted = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val cleanCloseTime = closeTime.trim().ifBlank { nowFormatted }
        val cleanComment = comment.trim().ifBlank { "Withdrawal" }
        val candidateTicket = customTicketId?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("#")) it else "#$it"
        }
        val ticketTakenByOther = candidateTicket != null &&
            _history.value.any { it.id != targetId && it.ticketId.equals(candidateTicket, ignoreCase = true) }
        val formattedTicket = if (candidateTicket != null && !ticketTakenByOther) {
            candidateTicket.removePrefix("#").toLongOrNull()?.let { issuedTicketNumbers.add(it) }
            candidateTicket
        } else {
            val freshNum = uniqueTicketNum ?: generateUniqueTicketNumber()
            "#$freshNum"
        }

        val withdrawalRecord = HistoryTrade(
            id = targetId,
            symbol = "Balance",
            isBuy = false,
            lots = 0.01,
            openPrice = 1.0,
            closePrice = 1.0,
            profit = -cleanAmount,
            openTime = cleanCloseTime,
            closeTime = cleanCloseTime,
            commission = 0.00,
            swap = 0.00,
            sl = null,
            tp = null,
            ticketId = formattedTicket,
            isWithdrawal = true,
            comment = cleanComment
        )

        _history.update { currentList ->
            val withoutOld = currentList.filter { it.id != targetId }
            sortHistoryChronologically(withoutOld + withdrawalRecord)
        }

        recalculatePositionsAndAccount(_quotes.value)
        persistActiveAccountLocal()

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val updatedAccount = _account.value
        val accKey = activeAccountKey
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.addHistoryTrade(withdrawalRecord, userId = uid, accountKey = accKey)
                    cloud.saveOrUpdateAccount(updatedAccount, uid, accKey)
                } catch (ignored: Exception) {
                }
            }
        }
        return withdrawalRecord
    }

    /**
     * Deletes a Trade or Withdrawal from the History list and auto-syncs the deletion to the Cloud Server.
     */
    fun deleteHistoryItem(id: Long) {
        val removedSym = _history.value.find { it.id == id && !it.isWithdrawal }?.symbol
        _history.update { list -> list.filter { it.id != id } }
        recalculatePositionsAndAccount(_quotes.value)
        if (removedSym != null) {
            refreshSymbolCandlesWithTrades(removedSym)
        }
        persistActiveAccountLocal()

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val updatedAccount = _account.value
        val accKey = activeAccountKey
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.deleteHistoryTrade(id, uid, accKey)
                    cloud.saveOrUpdateAccount(updatedAccount, uid, accKey)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    fun setDepositAmount(newDeposit: Double) {
        val cleanDeposit = roundTo2(newDeposit.coerceAtLeast(0.0))
        _account.update { it.copy(deposit = cleanDeposit) }
        recalculatePositionsAndAccount(_quotes.value)
        persistActiveAccountLocal()

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val updatedAccount = _account.value
        val accKey = activeAccountKey
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.saveOrUpdateAccount(updatedAccount, uid, accKey)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    companion object {
        const val LEVERAGE = 500.0
        const val BASE_HISTORY_PROFIT = 24.74
    }

    fun resetDemoAccount() {
        val resetKey = activeAccountKey
        _account.update {
            AccountInfo(
                key = resetKey,
                accountName = it.accountName,
                brokerName = it.brokerName,
                accountId = it.accountId,
                serverName = it.serverName,
                isReal = it.isReal,
                logoType = it.logoType
            )
        }
        _positions.value = initialPositions
        _history.value = if (resetKey == "main") sortHistoryChronologically(initialHistory) else emptyList()
        recalculatePositionsAndAccount(_quotes.value)
        persistActiveAccountLocal()

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val freshAccount = _account.value
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.clearUserPositions(uid, resetKey)
                    cloud.clearUserHistory(uid, resetKey)
                    for (hist in _history.value) {
                        cloud.addHistoryTrade(hist, userId = uid, accountKey = resetKey)
                    }
                    cloud.saveOrUpdateAccount(freshAccount, uid, resetKey)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    fun updateAccountDetails(
        accountName: String,
        brokerName: String = _account.value.brokerName,
        accountId: String = _account.value.accountId,
        serverName: String = _account.value.serverName,
        isReal: Boolean? = null,
        newBalance: Double? = null,
        logoType: String? = null
    ) {
        updateAccountByKey(
            targetKey = activeAccountKey,
            accountName = accountName,
            brokerName = brokerName,
            accountId = accountId,
            serverName = serverName,
            isReal = isReal,
            newBalance = newBalance,
            logoType = logoType
        )
    }

    fun updateAccountByKey(
        targetKey: String,
        accountName: String,
        brokerName: String,
        accountId: String,
        serverName: String,
        isReal: Boolean? = null,
        newBalance: Double? = null,
        logoType: String? = null
    ) {
        val safeKey = targetKey.ifBlank { activeAccountKey }
        val existingAcc = _accounts.value.find { it.key == safeKey } ?: _account.value
        val resolvedIsReal = isReal ?: when {
            accountId.contains("Demo", ignoreCase = true) ||
                accountId.contains("SIM", ignoreCase = true) ||
                serverName.contains("Demo", ignoreCase = true) -> false
            accountId.contains("Live", ignoreCase = true) ||
                accountName.contains("REAL", ignoreCase = true) -> true
            else -> existingAcc.isReal
        }

        val targetHist = if (safeKey == activeAccountKey) {
            _history.value
        } else {
            historyByAccount[safeKey] ?: localStorage?.loadHistoryForAccount(safeKey).orEmpty()
        }
        val realizedProfit = roundTo2(targetHist.filter { !it.isWithdrawal }.sumOf { it.profit })
        val totalWithdrawals = roundTo2(targetHist.filter { it.isWithdrawal }.sumOf { abs(it.profit) })
        val updatedDeposit = if (newBalance != null) {
            roundTo2((newBalance - realizedProfit + totalWithdrawals).coerceAtLeast(0.0))
        } else {
            existingAcc.deposit.coerceAtLeast(0.0)
        }
        val updatedBalance = roundTo2(updatedDeposit + realizedProfit - totalWithdrawals)

        val updatedAcc = existingAcc.copy(
            key = safeKey,
            accountName = accountName.trim().ifBlank { existingAcc.accountName },
            brokerName = brokerName.trim().ifBlank { existingAcc.brokerName },
            accountId = accountId.trim().ifBlank { existingAcc.accountId },
            serverName = serverName.trim().ifBlank { existingAcc.serverName },
            isReal = resolvedIsReal,
            deposit = updatedDeposit,
            realizedProfit = realizedProfit,
            totalWithdrawal = totalWithdrawals,
            balance = updatedBalance,
            equity = updatedBalance + existingAcc.floatingPnL,
            freeMargin = updatedBalance + existingAcc.floatingPnL - existingAcc.margin,
            logoType = logoType?.takeIf { it.isNotBlank() } ?: existingAcc.logoType
        )

        _accounts.update { list ->
            list.map { if (it.key == safeKey) updatedAcc else it }
        }

        if (safeKey == activeAccountKey) {
            _account.value = updatedAcc
            recalculatePositionsAndAccount(_quotes.value)
        }
        persistActiveAccountLocal()

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.saveOrUpdateAccount(updatedAcc, uid, safeKey)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    /**
     * Creates a new trading account with its own isolated trade history, open positions, and balance.
     */
    fun addAccount(
        accountName: String,
        brokerName: String,
        accountId: String,
        serverName: String,
        isReal: Boolean,
        initialBalance: Double,
        logoType: String = "AUTO",
        switchToNew: Boolean = true
    ): AccountInfo {
        persistActiveAccountLocal()

        val newKey = "acc_${System.currentTimeMillis()}_${(100..999).random()}"
        val cleanBalance = roundTo2(initialBalance.coerceAtLeast(0.0))
        val newAccount = AccountInfo(
            key = newKey,
            balance = cleanBalance,
            equity = cleanBalance,
            margin = 0.0,
            freeMargin = cleanBalance,
            marginLevel = 0.0,
            floatingPnL = 0.0,
            accountName = accountName.trim().ifBlank { "Pasindu Jeewantha Pelanage" },
            brokerName = brokerName.trim().ifBlank { "Moneta Markets (Pty) Ltd" },
            accountId = accountId.trim().ifBlank {
                val randNum = (1000000..9999999).random()
                if (isReal) "$randNum \u2014 MonetaMarkets-Live" else "$randNum \u2014 MonetaMarkets-Demo"
            },
            serverName = serverName.trim().ifBlank {
                if (isReal) "AS08, Hedge" else "AS2 - NY, Hedge"
            },
            isReal = isReal,
            deposit = cleanBalance,
            realizedProfit = 0.0,
            totalWithdrawal = 0.0,
            totalSwap = 0.0,
            totalCommission = 0.0,
            logoType = logoType.ifBlank { "AUTO" }
        )

        positionsByAccount[newKey] = emptyList()
        historyByAccount[newKey] = emptyList()
        localStorage?.savePositionsForAccount(newKey, emptyList())
        localStorage?.saveHistoryForAccount(newKey, emptyList())

        _accounts.update { currentList ->
            currentList + newAccount
        }

        if (switchToNew) {
            switchAccount(newKey)
        } else {
            persistActiveAccountLocal()
        }

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.saveOrUpdateAccount(newAccount, uid, newKey)
                } catch (ignored: Exception) {
                }
            }
        }
        return newAccount
    }

    /**
     * Switches the active trading account to [targetKey], loading that account's own trade history,
     * running positions, and balance across the entire app.
     */
    fun switchAccount(targetKey: String) {
        val safeKey = targetKey.trim()
        if (safeKey.isEmpty()) return
        val targetAcc = _accounts.value.find { it.key == safeKey } ?: return
        if (safeKey == activeAccountKey && _account.value.key == safeKey) return

        // Save outgoing account's state first
        persistActiveAccountLocal()

        activeAccountKey = safeKey
        val nextPositions = positionsByAccount[safeKey]
            ?: localStorage?.loadPositionsForAccount(safeKey).orEmpty()
        val nextHistory = historyByAccount[safeKey]
            ?: sortHistoryChronologically(localStorage?.loadHistoryForAccount(safeKey).orEmpty())

        positionsByAccount[safeKey] = nextPositions
        historyByAccount[safeKey] = nextHistory

        _account.value = targetAcc
        _positions.value = nextPositions
        _history.value = sortHistoryChronologically(nextHistory)

        recalculatePositionsAndAccount(_quotes.value)
        persistActiveAccountLocal()
        refreshSymbolCandlesWithTrades(currentActiveSymbol)

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            startCloudListeners(uid, cloud)
        }
    }

    /**
     * Deletes the account with [targetKey] along with its trade history and open positions.
     * If the deleted account was active, switches to the first remaining account.
     */
    fun deleteAccount(targetKey: String): Boolean {
        val safeKey = targetKey.trim()
        val currentList = _accounts.value
        if (currentList.none { it.key == safeKey }) return false

        positionsByAccount.remove(safeKey)
        historyByAccount.remove(safeKey)
        localStorage?.deleteAccountStorage(safeKey)

        val remaining = currentList.filter { it.key != safeKey }
        if (remaining.isEmpty()) {
            val fallback = AccountInfo(
                key = "main",
                balance = 0.0,
                equity = 0.0,
                freeMargin = 0.0,
                deposit = 0.0,
                realizedProfit = 0.0,
                totalWithdrawal = 0.0
            )
            activeAccountKey = "main"
            positionsByAccount["main"] = emptyList()
            historyByAccount["main"] = emptyList()
            _accounts.value = listOf(fallback)
            _account.value = fallback
            _positions.value = emptyList()
            _history.value = emptyList()
            recalculatePositionsAndAccount(_quotes.value)
            persistActiveAccountLocal()
        } else {
            _accounts.value = remaining
            if (safeKey == activeAccountKey) {
                val nextActive = remaining.first()
                activeAccountKey = nextActive.key
                val nextPos = positionsByAccount[activeAccountKey]
                    ?: localStorage?.loadPositionsForAccount(activeAccountKey).orEmpty()
                val nextHist = historyByAccount[activeAccountKey]
                    ?: sortHistoryChronologically(localStorage?.loadHistoryForAccount(activeAccountKey).orEmpty())
                _account.value = nextActive
                _positions.value = nextPos
                _history.value = nextHist
                recalculatePositionsAndAccount(_quotes.value)
            }
            persistActiveAccountLocal()
        }

        refreshSymbolCandlesWithTrades(currentActiveSymbol)

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.deleteAccountAndData(safeKey, uid)
                    if (remaining.isNotEmpty() && safeKey == activeAccountKey) {
                        startCloudListeners(uid, cloud)
                    }
                } catch (ignored: Exception) {
                }
            }
        }
        return true
    }

    fun topUpDemo(amount: Double) {
        _account.update { acc ->
            acc.copy(
                balance = acc.balance + amount,
                deposit = acc.deposit + amount,
                equity = acc.equity + amount,
                freeMargin = acc.freeMargin + amount
            )
        }
        recalculatePositionsAndAccount(_quotes.value)
        persistActiveAccountLocal()
        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val updatedAccount = _account.value
        val accKey = activeAccountKey
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    cloud.saveOrUpdateAccount(updatedAccount, uid, accKey)
                } catch (ignored: Exception) {
                }
            }
        }
    }
}
