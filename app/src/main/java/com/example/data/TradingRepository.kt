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
import java.util.Calendar
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

    // Initial Quotes ordered to match the Fusion Markets & Moneta Markets chart/symbol list
    private val initialQuotes = listOf(
        SymbolQuote(
            symbol = "XAUUSD",
            description = "Gold Spot (1 Lot = 100 oz)",
            digits = 2,
            bid = 4001.44,
            ask = 4001.59,
            low = 3981.96,
            high = 4017.86,
            changePoints = -698,
            changePercent = -0.42,
            spread = 15,
            time = "06:09:08",
            tickDirection = TickDirection.DOWN,
            bidDirection = TickDirection.DOWN,
            askDirection = TickDirection.DOWN
        ),
        SymbolQuote(
            symbol = "USDSEK",
            description = "US Dollar vs Swedish Krona",
            digits = 5,
            bid = 10.68450,
            ask = 10.68480,
            low = 10.64120,
            high = 10.71240,
            changePoints = 412,
            changePercent = 0.39,
            spread = 30,
            time = "06:09:08",
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
            askDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "USDJPY",
            description = "US Dollar vs Japanese Yen",
            digits = 3,
            bid = 156.813,
            ask = 156.814,
            low = 156.652,
            high = 157.220,
            changePoints = 185,
            changePercent = 0.12,
            spread = 1,
            time = "06:09:06",
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
            askDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "USDCNH",
            description = "US Dollar vs Chinese Yuan",
            digits = 5,
            bid = 7.24680,
            ask = 7.24705,
            low = 7.23410,
            high = 7.25890,
            changePoints = 215,
            changePercent = 0.30,
            spread = 25,
            time = "06:09:08",
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
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
            changePoints = 158,
            changePercent = 0.19,
            spread = 3,
            time = "06:09:06",
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
            askDirection = TickDirection.UP
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
            tickDirection = TickDirection.DOWN,
            bidDirection = TickDirection.DOWN,
            askDirection = TickDirection.DOWN
        ),
        SymbolQuote(
            symbol = "NZDUSD",
            description = "New Zealand Dollar vs US Dollar",
            digits = 5,
            bid = 0.57025,
            ask = 0.57026,
            low = 0.56953,
            high = 0.57072,
            changePoints = -35,
            changePercent = -0.06,
            spread = 1,
            time = "06:09:06",
            tickDirection = TickDirection.DOWN,
            bidDirection = TickDirection.DOWN,
            askDirection = TickDirection.DOWN
        ),
        SymbolQuote(
            symbol = "NAS100",
            description = "US Tech 100 Cash Index",
            digits = 2,
            bid = 29763.58,
            ask = 29765.08,
            low = 27395.47,
            high = 29960.00,
            changePoints = -1043,
            changePercent = -0.35,
            spread = 150,
            time = "06:09:08",
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
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
            askDirection = TickDirection.UP
        ),
        SymbolQuote(
            symbol = "EURUSD",
            description = "Euro vs US Dollar",
            digits = 5,
            bid = 1.12198,
            ask = 1.12200,
            low = 1.12022,
            high = 1.12267,
            changePoints = -259,
            changePercent = -0.23,
            spread = 2,
            time = "06:09:06",
            tickDirection = TickDirection.DOWN,
            bidDirection = TickDirection.DOWN,
            askDirection = TickDirection.DOWN
        ),
        SymbolQuote(
            symbol = "AUDUSD",
            description = "Australian Dollar vs US Dollar",
            digits = 5,
            bid = 0.70307,
            ask = 0.70309,
            low = 0.70188,
            high = 0.70398,
            changePoints = 42,
            changePercent = 0.06,
            spread = 2,
            time = "06:09:08",
            tickDirection = TickDirection.UP,
            bidDirection = TickDirection.UP,
            askDirection = TickDirection.UP
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

    private val _positions = MutableStateFlow<List<TradePosition>>(emptyList())
    val positions: StateFlow<List<TradePosition>> = _positions.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryTrade>>(emptyList())
    val history: StateFlow<List<HistoryTrade>> = _history.asStateFlow()

    private val _account = MutableStateFlow(EMPTY_LOGGED_OUT_ACCOUNT)
    val account: StateFlow<AccountInfo> = _account.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountInfo>>(emptyList())
    val accounts: StateFlow<List<AccountInfo>> = _accounts.asStateFlow()

    private var activeAccountKey: String = ""
    @Volatile
    private var cloudAuthoritativeBalance: Double = CloudUserAccount.DEFAULT_BALANCE
    @Volatile
    private var cloudLeverage: Double = LEVERAGE
    private val openTradeDocIdById = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private val historyDocIdById = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private val _isAuthInitialized = MutableStateFlow(false)
    val isAuthInitialized: StateFlow<Boolean> = _isAuthInitialized.asStateFlow()

    private val _isCloudSynced = MutableStateFlow(false)
    val isCloudSynced: StateFlow<Boolean> = _isCloudSynced.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _currentUserDisplayName = MutableStateFlow<String?>(null)
    val currentUserDisplayName: StateFlow<String?> = _currentUserDisplayName.asStateFlow()

    private val _currentUserPhotoUrl = MutableStateFlow<String?>(null)
    val currentUserPhotoUrl: StateFlow<String?> = _currentUserPhotoUrl.asStateFlow()

    private var localStorage: LocalTradeStorage? = null
    private var cloudRepository: CloudTradeRepository? = null
    private var firebaseAuth: FirebaseAuth? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var cloudBootstrapJob: Job? = null
    private var cloudPositionsJob: Job? = null
    private var cloudHistoryJob: Job? = null
    private var cloudAccountJob: Job? = null
    private var storageInitialized = false

    private val _chartHistoryMoveEnabled = MutableStateFlow(true)
    val chartHistoryMoveEnabled: StateFlow<Boolean> = _chartHistoryMoveEnabled.asStateFlow()

    private val _showChartGrid = MutableStateFlow(true)
    val showChartGrid: StateFlow<Boolean> = _showChartGrid.asStateFlow()

    private val _showSymbolDescription = MutableStateFlow(true)
    val showSymbolDescription: StateFlow<Boolean> = _showSymbolDescription.asStateFlow()

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
                    loaded14DayKeys.add(key)
                    candleMap[key] = mergedBase.toMutableList()
                    localStorage?.saveCandles(symbol, timeframe.name, mergedBase)

                    if (currentActiveSymbol == symbol && currentActiveTimeframe == timeframe) {
                        candleAggregator.seedHistory(mergedBase)
                        val merged = candleAggregator.getCandles()
                        candleMap[key] = merged.toMutableList()
                        _selectedPairCandles.value = merged
                    }

                    val latestRealClose = mergedBase.last().close
                    if (latestRealClose > 0.0) {
                        val recentWindow = mergedBase.takeLast(60)
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

    fun setChartGridEnabled(enabled: Boolean) {
        _showChartGrid.value = enabled
        localStorage?.saveChartGridEnabled(enabled)
    }

    fun setSymbolDescriptionEnabled(enabled: Boolean) {
        _showSymbolDescription.value = enabled
        localStorage?.saveChartSymbolDescriptionEnabled(enabled)
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
            cached != null && cached.isNotEmpty() -> cached
            saved.isNotEmpty() -> saved
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
            cached != null && cached.isNotEmpty() -> cached
            saved.isNotEmpty() -> saved
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
        return fastForexService.build14DayCandlesFromAnchors(
            symbol = quote.symbol,
            timeframe = tf,
            livePrice = quote.bid,
            ffDailySeries = emptyList()
        )
    }

    /**
     * Preserves 100% of the original market candlestick data (`open`, `high`, `low`, `close`, `timestamp`)
     * without ever mutating, overwriting, or regenerating candles when trade history or positions are edited.
     */
    fun syncTradesIntoCandles(
        symbol: String,
        timeframe: Timeframe,
        baseCandles: List<Candle>
    ): List<Candle> {
        return baseCandles
    }

    /**
     * No-op: editing, adding, or deleting trades must never alter the chart's original candlestick data.
     */
    fun refreshSymbolCandlesWithTrades(symbol: String) {
        // Intentionally left empty so original chart candlestick OHLC data never changes
    }

    /**
     * Returns the most recent M1 (1-minute) chart candles for [symbol] (ensuring the M1 cache is populated
     * and synchronized with trades).
     */
    fun getRecentSymbolM1Candles(symbol: String, limit: Int = 30): List<Candle> {
        val rawSym = symbol.trim().uppercase(Locale.US).ifBlank { "NAS100" }
        val cleanSym = if (rawSym == "NDX100") "NAS100" else rawSym
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

        val rawSym = symbol.trim().uppercase(Locale.US).ifBlank { "NAS100" }
        val cleanSym = if (rawSym == "NDX100") "NAS100" else rawSym
        val quote = _quotes.value.find { it.symbol.equals(cleanSym, ignoreCase = true) }
        val digits = quote?.digits ?: when {
            cleanSym.contains("JPY") -> 3
            cleanSym.contains("XAU") || cleanSym.contains("NAS") || cleanSym.contains("NDX") || cleanSym.contains("BTC") -> 2
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
        val usedSecs = buildSet {
            _history.value.forEach { h ->
                if (!h.isWithdrawal) {
                    val hOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(h.openTime)
                    if (hOpenMs > 0L) {
                        add(((hOpenMs % 60_000L) / 1000L).toInt())
                    }
                    val hCloseMs = SriLankaMarketHours.parseMt5DateTimeMillis(h.closeTime)
                    if (hCloseMs > 0L) {
                        add(((hCloseMs % 60_000L) / 1000L).toInt())
                    }
                }
            }
            _positions.value.forEach { p ->
                val pOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(p.openTime)
                if (pOpenMs > 0L) {
                    add(((pOpenMs % 60_000L) / 1000L).toInt())
                }
            }
        }
        val randomOpenSec = (1..52).filter { it !in usedSecs }.randomOrNull()
            ?: (1..52).random()
        val randomCloseSec = (1..58).filter { it !in usedSecs && it != randomOpenSec }.randomOrNull()
            ?: (1..58).filter { it != randomOpenSec }.random()
        val effectiveSec = if (hasExplicitSeconds) {
            typedSec
        } else {
            if (isOpen) randomOpenSec else randomCloseSec
        }
        val chipOpenSec = if (isOpen) randomOpenSec else openSec
        val chipCloseSec = if (isOpen) closeSec else randomCloseSec

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
            openSecondTimestamp = String.format(Locale.US, "%s:%02d", minuteLabel, chipOpenSec),
            highSecondTimestamp = String.format(Locale.US, "%s:%02d", minuteLabel, highSec),
            lowSecondTimestamp = String.format(Locale.US, "%s:%02d", minuteLabel, lowSec),
            closeSecondTimestamp = String.format(Locale.US, "%s:%02d", minuteLabel, chipCloseSec)
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
        val rawSym = symbol.trim().uppercase(Locale.US).ifBlank { "NAS100" }
        val cleanSym = if (rawSym == "NDX100") "NAS100" else rawSym
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
            cleanSym.contains("XAU") || cleanSym.contains("NAS") || cleanSym.contains("NDX") || cleanSym.contains("BTC") -> 2
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
            val baseHitSec = (t0 + ((t1 - t0) * frac).toInt()).coerceIn(1, 58)
            val minAllowedSec = if (bucketMs == openBucket) (openSec + 2).coerceAtMost(58) else 1
            val usedCloseSecs = buildSet {
                _history.value.forEach { h ->
                    if (!h.isWithdrawal) {
                        val hCloseMs = SriLankaMarketHours.parseMt5DateTimeMillis(h.closeTime)
                        if (hCloseMs > 0L) add(((hCloseMs % 60_000L) / 1000L).toInt())
                    }
                }
                add(openSec)
            }
            val hitSec = if (baseHitSec >= minAllowedSec && baseHitSec !in usedCloseSecs) {
                baseHitSec
            } else {
                (minAllowedSec..58).filter { it !in usedCloseSecs }.randomOrNull()
                    ?: (minAllowedSec..58).random()
            }
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

            val prevCount = candleAggregator.getCandleCount()
            effectiveBid = candleAggregator.onTick(tick.bid, tick.timestamp, maxExcursion)
            effectiveAsk = effectiveBid + spreadDiff
            val updatedCandles = candleAggregator.getCandles()
            _selectedPairCandles.value = updatedCandles
            val key = cacheKey(currentActiveSymbol, currentActiveTimeframe)
            val existingCached = candleMap[key]
            if (existingCached != null && existingCached.size == updatedCandles.size && updatedCandles.isNotEmpty()) {
                existingCached[existingCached.lastIndex] = updatedCandles.last()
            } else {
                candleMap[key] = updatedCandles.toMutableList()
            }
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
            "EUR" -> quoteMap["EURUSD"]?.bid ?: 1.12198
            "GBP" -> quoteMap["GBPUSD"]?.bid ?: 1.33804
            "AUD" -> quoteMap["AUDUSD"]?.bid ?: 0.70307
            "NZD" -> quoteMap["NZDUSD"]?.bid ?: 0.57025
            "CAD" -> 1.0 / (quoteMap["USDCAD"]?.bid ?: 1.40054)
            "CHF" -> 1.0 / (quoteMap["USDCHF"]?.bid ?: 0.82374)
            "JPY" -> 1.0 / (quoteMap["USDJPY"]?.bid ?: 156.813)
            "SEK" -> 1.0 / (quoteMap["USDSEK"]?.bid ?: 10.68450)
            "CNH" -> 1.0 / (quoteMap["USDCNH"]?.bid ?: 7.24680)
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
            sym.contains("NAS") || sym.contains("NDX") || sym.contains("US30") -> {
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
            sym.contains("NAS") || sym.contains("NDX") || sym.contains("US30") -> {
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

    /**
     * Returns true if the active or specified account belongs to Fusion Markets (Zero Account),
     * or false for Moneta Markets (Prime ECN / default).
     */
    fun isFusionBroker(account: AccountInfo = _account.value): Boolean {
        return account.logoType.equals("FUSION", ignoreCase = true) ||
            account.brokerName.contains("Fusion", ignoreCase = true) ||
            account.accountId.contains("Fusion", ignoreCase = true)
    }

    /**
     * Returns true for index CFD instruments (NAS100, NDX100, US30, SPX500, GER40, UK100),
     * which have $0.00 extra commission on both Fusion Markets and Moneta Markets.
     */
    fun isIndexInstrument(symbol: String): Boolean {
        val sym = symbol.trim().uppercase(Locale.US)
        return sym.contains("NAS") ||
            sym.contains("NDX") ||
            sym.contains("US30") ||
            sym.contains("SPX") ||
            sym.contains("GER") ||
            sym.contains("UK100") ||
            sym.contains("US500")
    }

    /**
     * Returns the round-turn commission rate in USD per 1.00 Standard Lot:
     * - Indices (NAS100 / NDX100 / US30): $0.00
     * - Fusion Markets (Zero Account): $4.50 / Standard Lot ($2.25 open + $2.25 close)
     * - Moneta Markets (Prime ECN): $6.00 / Standard Lot ($3.00 open + $3.00 close)
     */
    fun getBrokerRoundTurnCommissionRate(
        symbol: String,
        account: AccountInfo = _account.value
    ): Double {
        if (isIndexInstrument(symbol)) return 0.00
        return if (isFusionBroker(account)) 4.50 else 6.00
    }

    /**
     * Calculates MT5 Commission in USD (negative deduction):
     * Commission (USD) = -(Lot Size * Round Turn Rate)
     * If [isOpenOnly] is true (for a newly opened running position), deducts half of the round-turn commission
     * at entry, and the remaining half upon close.
     */
    fun calculateCommissionUsd(
        symbol: String,
        lots: Double,
        isOpenOnly: Boolean = false,
        account: AccountInfo = _account.value
    ): Double {
        val safeLots = lots.coerceAtLeast(0.0)
        val roundTurnRate = getBrokerRoundTurnCommissionRate(symbol, account)
        if (roundTurnRate <= 0.0 || safeLots <= 0.0) return 0.00
        val factor = if (isOpenOnly) 0.5 else 1.0
        return roundTo2(-(safeLots * roundTurnRate * factor))
    }

    /**
     * Returns the broker's indicative Daily Swap Rate in USD per 1.00 Standard Lot:
     * - EURUSD:
     *     Long (Buy):  Fusion -$6.50 | Moneta -$7.20
     *     Short (Sell): Fusion +$1.80 | Moneta +$1.50
     * - XAUUSD (Gold):
     *     Long (Buy):  Fusion -$24.50 | Moneta -$27.00
     *     Short (Sell): Fusion +$9.80  | Moneta +$8.50
     * - NAS100 / NDX100:
     *     Long (Buy):  Fusion -$3.20 | Moneta -$3.50
     *     Short (Sell): Fusion -$1.90 | Moneta -$2.10
     */
    fun getDailySwapRatePerLot(
        symbol: String,
        isBuy: Boolean,
        account: AccountInfo = _account.value
    ): Double {
        val sym = symbol.trim().uppercase(Locale.US)
        val fusion = isFusionBroker(account)
        return when {
            sym.contains("XAU") -> {
                if (isBuy) {
                    if (fusion) -24.50 else -27.00
                } else {
                    if (fusion) 9.80 else 8.50
                }
            }
            sym.contains("EURUSD") -> {
                if (isBuy) {
                    if (fusion) -6.50 else -7.20
                } else {
                    if (fusion) 1.80 else 1.50
                }
            }
            sym.contains("NAS") || sym.contains("NDX") || isIndexInstrument(sym) -> {
                if (isBuy) {
                    if (fusion) -3.20 else -3.50
                } else {
                    if (fusion) -1.90 else -2.10
                }
            }
            sym.contains("XAG") -> {
                if (isBuy) {
                    if (fusion) -8.50 else -9.50
                } else {
                    if (fusion) 2.40 else 2.10
                }
            }
            sym.contains("BTC") || sym.contains("ETH") -> {
                if (fusion) -14.00 else -16.00
            }
            else -> {
                if (isBuy) {
                    if (fusion) -6.50 else -7.20
                } else {
                    if (fusion) 1.80 else 1.50
                }
            }
        }
    }

    /**
     * Counts the number of daily server rollovers (00:00 Server Time) crossed between [openTimeStr]
     * and [closeTimeStr], applying the 3x Triple Swap Rule on Wednesday night rollover to cover weekend holding.
     */
    fun calculateRolloverSwapDays(openTimeStr: String, closeTimeStr: String): Int {
        val openMs = SriLankaMarketHours.parseMt5DateTimeMillis(openTimeStr)
        val closeMs = SriLankaMarketHours.parseMt5DateTimeMillis(closeTimeStr)
        if (openMs <= 0L || closeMs <= 0L || closeMs <= openMs) return 0

        val openCal = Calendar.getInstance(SriLankaMarketHours.SRI_LANKA_TIME_ZONE, Locale.US).apply {
            timeInMillis = openMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val closeCal = Calendar.getInstance(SriLankaMarketHours.SRI_LANKA_TIME_ZONE, Locale.US).apply {
            timeInMillis = closeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (closeCal.timeInMillis <= openCal.timeInMillis) return 0

        var rolloverDays = 0
        var rawNightsCrossed = 0
        val cursor = openCal.clone() as Calendar
        while (cursor.timeInMillis < closeCal.timeInMillis && rawNightsCrossed < 3650) {
            rawNightsCrossed++
            when (cursor.get(Calendar.DAY_OF_WEEK)) {
                Calendar.WEDNESDAY -> rolloverDays += 3 // Triple Swap Rule on Wednesday night
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY -> rolloverDays += 1
                else -> {
                    // Weekend nights (Sat/Sun) are covered by Wednesday's 3x Triple Swap
                }
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (rawNightsCrossed > 0 && rolloverDays == 0) {
            rolloverDays = rawNightsCrossed
        }
        return rolloverDays
    }

    /**
     * Calculates MT5 Swap in USD:
     * Swap (USD) = Lots * Daily Swap Rate * Rollover Days (with 3x Wednesday Triple Swap)
     */
    fun calculateSwapUsd(
        symbol: String,
        isBuy: Boolean,
        lots: Double,
        openTimeStr: String,
        closeTimeStr: String,
        account: AccountInfo = _account.value
    ): Double {
        val safeLots = lots.coerceAtLeast(0.0)
        if (safeLots <= 0.0) return 0.00
        val rolloverDays = calculateRolloverSwapDays(openTimeStr, closeTimeStr)
        if (rolloverDays <= 0) return 0.00
        val dailyRate = getDailySwapRatePerLot(symbol, isBuy, account)
        return roundTo2(safeLots * dailyRate * rolloverDays)
    }

    /**
     * Ensures a trade's openTime has a realistic random second (:01 .. :58) that:
     * 1. Never repeats the same second (:ss) as other trades in History or Open Positions,
     * 2. Is strictly before closeTime when open and close are within the same minute,
     * 3. Generates a fresh random second when [forceNewRandomSecond] is true or when the input
     *    has no explicit seconds / has :00 / collides with an existing trade's second.
     */
    fun randomizeOpenTimeSecond(
        openTimeStr: String,
        closeTimeStr: String? = null,
        excludeTradeId: Long? = null,
        forceNewRandomSecond: Boolean = false
    ): String {
        val rawOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(openTimeStr)
        if (rawOpenMs <= 0L) return openTimeStr
        val rawCloseMs = closeTimeStr?.takeIf { it.isNotBlank() }?.let {
            SriLankaMarketHours.parseMt5DateTimeMillis(it)
        } ?: 0L

        val openBucketMs = (rawOpenMs / 60_000L) * 60_000L
        val closeBucketMs = if (rawCloseMs > 0L) (rawCloseMs / 60_000L) * 60_000L else 0L
        val currentOpenSec = ((rawOpenMs - openBucketMs) / 1000L).toInt().coerceIn(0, 59)
        val closeSec = if (rawCloseMs > 0L) ((rawCloseMs - closeBucketMs) / 1000L).toInt().coerceIn(0, 59) else -1

        val usedSecs = buildSet {
            _history.value.forEach { h ->
                if (!h.isWithdrawal && h.id != excludeTradeId) {
                    val hOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(h.openTime)
                    if (hOpenMs > 0L) {
                        add(((hOpenMs % 60_000L) / 1000L).toInt())
                    }
                    val hCloseMs = SriLankaMarketHours.parseMt5DateTimeMillis(h.closeTime)
                    if (hCloseMs > 0L) {
                        add(((hCloseMs % 60_000L) / 1000L).toInt())
                    }
                }
            }
            _positions.value.forEach { p ->
                if (p.id != excludeTradeId) {
                    val pOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(p.openTime)
                    if (pOpenMs > 0L) {
                        add(((pOpenMs % 60_000L) / 1000L).toInt())
                    }
                }
            }
            if (closeSec in 0..59) add(closeSec)
        }

        val hasExplicitSeconds = openTimeStr.trim().count { it == ':' } >= 2
        val sameMinute = rawCloseMs > 0L && openBucketMs == closeBucketMs
        val sameMinuteInvalid = sameMinute && closeSec > 0 && currentOpenSec >= closeSec
        val needsRandom = forceNewRandomSecond ||
            !hasExplicitSeconds ||
            currentOpenSec == 0 ||
            currentOpenSec in usedSecs ||
            sameMinuteInvalid

        val finalSec = if (!needsRandom) {
            currentOpenSec
        } else {
            val maxSec = if (sameMinute && closeSec > 2) {
                (closeSec - 2).coerceIn(1, 56)
            } else if (sameMinute) {
                45
            } else {
                58
            }
            val minSec = 1
            val primaryPool = (minSec..maxSec).filter { it !in usedSecs && it != currentOpenSec }
            val fallbackPool = (minSec..maxSec).filter { it != currentOpenSec && it != closeSec }
            when {
                primaryPool.isNotEmpty() -> primaryPool.random()
                fallbackPool.isNotEmpty() -> fallbackPool.random()
                else -> (minSec..maxSec).random()
            }
        }

        val minuteFmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).apply {
            timeZone = SriLankaMarketHours.SRI_LANKA_TIME_ZONE
        }
        val minuteLabel = minuteFmt.format(Date(openBucketMs))
        return String.format(Locale.US, "%s:%02d", minuteLabel, finalSec)
    }

    /**
     * Ensures a trade's closeTime has a realistic random second (:01 .. :58) that:
     * 1. Never repeats the same second (:ss) as other trades in History or its own openTime,
     * 2. Is strictly after openTime when open and close are within the same minute,
     * 3. Generates a fresh random second when [forceNewRandomSecond] is true or when the input
     *    has no explicit seconds / has :00 / collides with an existing trade's close second.
     */
    fun randomizeCloseTimeSecond(
        openTimeStr: String,
        closeTimeStr: String,
        excludeTradeId: Long? = null,
        forceNewRandomSecond: Boolean = false
    ): String {
        val rawCloseMs = SriLankaMarketHours.parseMt5DateTimeMillis(closeTimeStr)
        if (rawCloseMs <= 0L) return closeTimeStr
        val rawOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(openTimeStr)

        val closeBucketMs = (rawCloseMs / 60_000L) * 60_000L
        val openBucketMs = if (rawOpenMs > 0L) (rawOpenMs / 60_000L) * 60_000L else 0L
        val openSec = if (rawOpenMs > 0L) ((rawOpenMs - openBucketMs) / 1000L).toInt().coerceIn(0, 59) else -1
        val currentCloseSec = ((rawCloseMs - closeBucketMs) / 1000L).toInt().coerceIn(0, 59)

        val usedCloseSecs = buildSet {
            _history.value.forEach { h ->
                if (!h.isWithdrawal && h.id != excludeTradeId) {
                    val hCloseMs = SriLankaMarketHours.parseMt5DateTimeMillis(h.closeTime)
                    if (hCloseMs > 0L) {
                        add(((hCloseMs % 60_000L) / 1000L).toInt())
                    }
                    val hOpenMs = SriLankaMarketHours.parseMt5DateTimeMillis(h.openTime)
                    if (hOpenMs > 0L) {
                        add(((hOpenMs % 60_000L) / 1000L).toInt())
                    }
                }
            }
            if (openSec in 0..59) add(openSec)
        }

        val hasExplicitSeconds = closeTimeStr.trim().count { it == ':' } >= 2
        val sameMinuteInvalid = openBucketMs == closeBucketMs && openSec >= 0 && currentCloseSec <= openSec
        val needsRandom = forceNewRandomSecond ||
            !hasExplicitSeconds ||
            currentCloseSec == 0 ||
            currentCloseSec in usedCloseSecs ||
            sameMinuteInvalid

        val finalSec = if (!needsRandom) {
            currentCloseSec
        } else {
            val minSec = if (openBucketMs == closeBucketMs && openSec >= 0) {
                (openSec + 2).coerceAtMost(58)
            } else {
                1
            }
            val maxSec = if (minSec >= 58) 59 else 58
            val primaryPool = (minSec..maxSec).filter { it !in usedCloseSecs && it != currentCloseSec }
            val fallbackPool = (minSec..maxSec).filter { it != currentCloseSec && it != openSec }
            when {
                primaryPool.isNotEmpty() -> primaryPool.random()
                fallbackPool.isNotEmpty() -> fallbackPool.random()
                else -> (minSec..maxSec).random()
            }
        }

        val minuteFmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).apply {
            timeZone = SriLankaMarketHours.SRI_LANKA_TIME_ZONE
        }
        val minuteLabel = minuteFmt.format(Date(closeBucketMs))
        return String.format(Locale.US, "%s:%02d", minuteLabel, finalSec)
    }

    /**
     * Recalculates floating P&L, equity, margin, free margin, and margin level strictly locally
     * in memory from live market quotes. NEVER writes ticks or floating P&L to Firestore.
     */
    private fun recalculatePositionsAndAccount(currentQuotes: List<SymbolQuote>) {
        if (!_isCloudSynced.value || activeAccountKey.isBlank() || _accounts.value.isEmpty()) {
            return
        }
        val quoteMap = currentQuotes.associateBy { it.symbol }
        val currentAccount = _account.value
        val nowTimeStr = synchronized(dateTimeFormat) {
            dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis()))
        }
        val leverageRatio = (LEVERAGE / cloudLeverage.coerceAtLeast(1.0))

        var totalFloatingPnL = 0.0
        var totalOpenSwap = 0.0
        var totalOpenCommission = 0.0
        var totalMargin = 0.0

        val updatedPositions = _positions.value.map { pos ->
            val quote = quoteMap[pos.symbol]
            val openComm = if (pos.commission != 0.0) {
                pos.commission
            } else {
                calculateCommissionUsd(pos.symbol, pos.lots, isOpenOnly = true, account = currentAccount)
            }
            val openSwap = if (pos.swap != 0.0) {
                pos.swap
            } else {
                calculateSwapUsd(pos.symbol, pos.isBuy, pos.lots, pos.openTime, nowTimeStr, account = currentAccount)
            }
            totalOpenSwap += openSwap
            totalOpenCommission += openComm

            val positionMargin = calculatePositionMarginUsd(pos, quoteMap) * leverageRatio
            totalMargin += positionMargin

            if (quote != null) {
                val currentPrice = if (pos.isBuy) quote.bid else quote.ask
                val profit = calculatePositionProfitUsd(pos, currentPrice, quoteMap)
                totalFloatingPnL += profit

                pos.copy(
                    currentPrice = currentPrice,
                    profit = profit,
                    commission = openComm,
                    swap = openSwap
                )
            } else {
                totalFloatingPnL += pos.profit
                pos.copy(
                    commission = openComm,
                    swap = openSwap
                )
            }
        }

        _positions.value = updatedPositions

        val currentHistory = _history.value
        val closedTrades = currentHistory.filter { !it.isWithdrawal }
        val totalRealizedProfit = roundTo2(closedTrades.sumOf { it.profit })
        val totalSwap = roundTo2(closedTrades.sumOf { it.swap })
        val totalCommission = roundTo2(closedTrades.sumOf { it.commission })
        val totalWithdrawals = roundTo2(
            currentHistory.filter { it.isWithdrawal }.sumOf { abs(it.profit) }
        )
        // Balance comes directly from Firestore users/{uid}.balance
        val balance = roundTo2(cloudAuthoritativeBalance)
        val deposit = currentAccount.deposit.coerceAtLeast(0.0)
        val roundedFloating = roundTo2(totalFloatingPnL)
        val roundedMargin = roundTo2(totalMargin)
        val equity = roundTo2(balance + roundedFloating + totalOpenSwap + totalOpenCommission)
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
            totalSwap = totalSwap,
            totalCommission = totalCommission
        )
        _account.value = updatedActiveAccount
        _accounts.value = listOf(updatedActiveAccount)
    }

    /**
     * Repairs an existing [TradePosition] in-place without ever deleting it or altering valid user-entered values:
     * - Fixes `isBuy` (BUY vs SELL) using mathematical price/profit and SL/TP verification.
     * - Ensures `symbol` is never blank (defaults to "XAUUSD" or normalizes "NDX100" -> "NAS100").
     * - Ensures `openTime` has valid `:ss` seconds if seconds were missing or `:00`.
     */
    private fun repairExistingPosition(pos: TradePosition): TradePosition {
        val rawSym = pos.symbol.trim().uppercase(Locale.US).ifBlank { "XAUUSD" }
        val cleanSym = if (rawSym == "NDX100") "NAS100" else rawSym
        val accurateBuy = resolveAccurateTradeIsBuy(
            storedIsBuy = pos.isBuy,
            openPrice = pos.openPrice,
            closePrice = pos.currentPrice,
            profit = pos.profit,
            sl = pos.sl,
            tp = pos.tp
        )
        val rawOpen = pos.openTime.trim()
        val repairedOpenTime = if (rawOpen.isNotBlank() && (rawOpen.count { it == ':' } < 2 || rawOpen.endsWith(":00"))) {
            val posHash = (pos.id xor (pos.id ushr 32)).toInt() and 0x7FFFFFFF
            val detSec = 4 + (posHash % 51) // deterministic :04 .. :54
            String.format(Locale.US, "%s:%02d", rawOpen.take(16), detSec)
        } else {
            rawOpen
        }
        return if (cleanSym != pos.symbol || accurateBuy != pos.isBuy || repairedOpenTime != pos.openTime) {
            pos.copy(
                symbol = cleanSym,
                isBuy = accurateBuy,
                openTime = repairedOpenTime
            )
        } else {
            pos
        }
    }

    /**
     * Repairs an existing [HistoryTrade] in-place without ever deleting it or altering valid user-entered values:
     * - Fixes `isBuy` (BUY vs SELL) using mathematical price/profit and SL/TP verification.
     * - Ensures `symbol` is never blank (defaults to "XAUUSD" or normalizes "NDX100" -> "NAS100").
     * - Ensures `ticketId` is never blank.
     * - Ensures `openTime` and `closeTime` have valid `:ss` seconds if missing or `:00` (while keeping existing valid `:01..:59` seconds untouched).
     * - Repairs `hitOutcome` (TP_HIT / SL_HIT) if a trade's direction was previously corrupted or `closePrice` matches `tp`/`sl`.
     */
    private fun repairExistingHistoryTrade(t: HistoryTrade): HistoryTrade {
        if (t.isWithdrawal) {
            val cleanTicket = t.ticketId.ifBlank { "#${if (t.id >= 100000000L) t.id else 385000000L + t.id}" }
            return t.copy(
                symbol = "Balance",
                isBuy = false,
                ticketId = cleanTicket
            )
        }

        val rawSym = t.symbol.trim().uppercase(Locale.US).ifBlank { "XAUUSD" }
        val cleanSym = if (rawSym == "NDX100") "NAS100" else rawSym
        val accurateBuy = resolveAccurateTradeIsBuy(
            storedIsBuy = t.isBuy,
            openPrice = t.openPrice,
            closePrice = t.closePrice,
            profit = t.profit,
            sl = t.sl,
            tp = t.tp
        )
        val cleanTicket = t.ticketId.ifBlank {
            "#${if (t.id >= 100000000L) t.id else 385000000L + t.id}"
        }

        val rawClose = t.closeTime.trim()
        val repairedCloseTime = if (rawClose.isNotBlank() && (rawClose.count { it == ':' } < 2 || rawClose.endsWith(":00"))) {
            val closeHash = ((t.id * 31L) xor ((t.id * 31L) ushr 32)).toInt() and 0x7FFFFFFF
            val detCloseSec = 18 + (closeHash % 39) // deterministic :18 .. :56
            String.format(Locale.US, "%s:%02d", rawClose.take(16), detCloseSec)
        } else {
            rawClose
        }

        val rawOpen = t.openTime.trim().ifBlank { repairedCloseTime }
        val sameMinute = rawOpen.take(16) == repairedCloseTime.take(16)
        val closeSecVal = repairedCloseTime.takeLast(2).toIntOrNull() ?: 35
        val repairedOpenTime = if (
            rawOpen.isNotBlank() &&
            (rawOpen.count { it == ':' } < 2 || rawOpen.endsWith(":00") || (sameMinute && rawOpen == repairedCloseTime))
        ) {
            val maxOpenSec = if (sameMinute && closeSecVal > 3) (closeSecVal - 2).coerceIn(2, 54) else 52
            val openHash = ((t.id * 17L) xor ((t.id * 17L) ushr 32)).toInt() and 0x7FFFFFFF
            val detOpenSec = 2 + (openHash % (maxOpenSec - 1).coerceAtLeast(1))
            String.format(Locale.US, "%s:%02d", rawOpen.take(16), detOpenSec)
        } else {
            rawOpen
        }

        val priceInferredHit = inferHitOutcomeFromPrices(
            symbol = cleanSym,
            isBuy = accurateBuy,
            openPrice = t.openPrice,
            closePrice = t.closePrice,
            sl = t.sl,
            tp = t.tp
        )
        val repairedHitOutcome = when {
            priceInferredHit != TradeHitOutcome.NONE -> priceInferredHit
            t.isBuy != accurateBuy && (t.sl != null || t.tp != null) -> {
                checkTpSlHitFromChartData(
                    symbol = cleanSym,
                    isBuy = accurateBuy,
                    openPrice = t.openPrice,
                    closePrice = t.closePrice,
                    openTime = repairedOpenTime,
                    closeTime = repairedCloseTime,
                    sl = t.sl,
                    tp = t.tp,
                    strictWithinTradeWindow = true
                ).outcome
            }
            else -> t.hitOutcome
        }

        return if (
            cleanSym != t.symbol ||
            accurateBuy != t.isBuy ||
            cleanTicket != t.ticketId ||
            repairedOpenTime != t.openTime ||
            repairedCloseTime != t.closeTime ||
            repairedHitOutcome != t.hitOutcome
        ) {
            t.copy(
                symbol = cleanSym,
                isBuy = accurateBuy,
                ticketId = cleanTicket,
                openTime = repairedOpenTime,
                closeTime = repairedCloseTime,
                hitOutcome = repairedHitOutcome
            )
        } else {
            t
        }
    }

    private fun mergeTradeRecordsPreservingMetadata(
        primary: HistoryTrade,
        secondary: HistoryTrade?
    ): HistoryTrade {
        if (secondary == null) return repairExistingHistoryTrade(primary)
        val merged = primary.copy(
            sl = primary.sl ?: secondary.sl,
            tp = primary.tp ?: secondary.tp,
            hitOutcome = primary.hitOutcome ?: secondary.hitOutcome,
            ticketId = primary.ticketId.ifBlank { secondary.ticketId },
            openTime = primary.openTime.ifBlank { secondary.openTime }
        )
        return repairExistingHistoryTrade(merged)
    }

    private fun sortHistoryChronologically(list: List<HistoryTrade>): List<HistoryTrade> {
        return list.map { t ->
            repairExistingHistoryTrade(t)
        }.distinctBy { it.id }.sortedWith(
            compareBy<HistoryTrade> {
                SriLankaMarketHours.parseMt5DateTimeMillis(it.closeTime)
            }.thenBy { it.id }
        )
    }

    private var nextPositionId = 1001L

    /**
     * Immediately unsubscribes all active Firestore listeners and clears in-memory
     * account, open trade, and history state. Called before sign-out and on every auth state change.
     */
    @Synchronized
    fun prepareForSignOut() {
        clearLoggedOutState()
    }

    @Synchronized
    private fun clearLoggedOutState() {
        stopCloudListeners()
        _currentUserEmail.value = null
        _currentUserDisplayName.value = null
        _currentUserPhotoUrl.value = null
        _isCloudSynced.value = false
        activeAccountKey = ""
        cloudAuthoritativeBalance = CloudUserAccount.DEFAULT_BALANCE
        cloudLeverage = LEVERAGE
        openTradeDocIdById.clear()
        historyDocIdById.clear()
        _accounts.value = emptyList()
        _account.value = EMPTY_LOGGED_OUT_ACCOUNT
        _positions.value = emptyList()
        _history.value = emptyList()
    }

    fun initializeStorage(context: Context) {
        if (storageInitialized) return
        storageInitialized = true

        val appContext = context.applicationContext
        val local = LocalTradeStorage(appContext)
        localStorage = local
        _chartHistoryMoveEnabled.value = local.loadChartHistoryMoveEnabled()
        _showChartGrid.value = local.loadChartGridEnabled()
        _showSymbolDescription.value = local.loadChartSymbolDescriptionEnabled()

        // Always start cleanly logged-out with zero locally stored account/trade state
        clearLoggedOutState()

        // Restore chart candle cache for the active symbol
        Timeframe.values().forEach { tf ->
            val savedCandles = filterCandlesToChartNow(local.loadCandles(currentActiveSymbol, tf.name), tf)
            if (savedCandles.isNotEmpty()) {
                val key = cacheKey(currentActiveSymbol, tf)
                candleMap[key] = savedCandles.toMutableList()
                if (tf == currentActiveTimeframe) {
                    candleAggregator.seedHistory(savedCandles)
                    _selectedPairCandles.value = candleAggregator.getCandles()
                }
            }
        }
        val activeKey = cacheKey(currentActiveSymbol, currentActiveTimeframe)
        if (!loaded14DayKeys.contains(activeKey)) {
            load14DayHistoricalCandles(currentActiveSymbol, currentActiveTimeframe)
        }

        // Initialize Firebase Auth & Cloud Firestore off the main UI thread
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
                        try {
                            val user = fbAuth.currentUser
                            // Step 1: Always unsubscribe previous Firestore listeners first
                            stopCloudListeners()

                            if (user == null || user.uid.isBlank()) {
                                // Step 2: Logged out -> clear account, open trades, and history state
                                clearLoggedOutState()
                            } else {
                                // Step 3: Logged in -> ensure users/{uid} exists & attach fresh real-time listeners
                                val uid = user.uid
                                val email = user.email?.trim().orEmpty()
                                val displayName = user.displayName?.trim()?.ifBlank { "Trader" } ?: "Trader"
                                val photoUrl = user.photoUrl?.toString()?.trim().orEmpty()
                                _currentUserEmail.value = email.ifBlank { null }
                                _currentUserDisplayName.value = displayName
                                _currentUserPhotoUrl.value = photoUrl.ifBlank { null }
                                _isCloudSynced.value = true
                                activeAccountKey = "main"
                                openTradeDocIdById.clear()
                                historyDocIdById.clear()
                                _positions.value = emptyList()
                                _history.value = emptyList()

                                startCloudListeners(uid, email, displayName, photoUrl, cloud)
                            }
                            _isAuthInitialized.value = true
                        } catch (_: Throwable) {
                            _isAuthInitialized.value = true
                        }
                    }
                    authStateListener = listener
                    auth.addAuthStateListener(listener)
                } else {
                    _isAuthInitialized.value = true
                }
            } catch (_: Throwable) {
                _isAuthInitialized.value = true
            }
        }
    }

    @Volatile
    private var activeListenerUid: String? = null

    @Synchronized
    private fun startCloudListeners(
        uid: String,
        email: String?,
        displayName: String?,
        photoUrl: String?,
        cloud: CloudTradeRepository
    ) {
        val safeUid = uid.trim()
        if (safeUid.isEmpty()) return

        stopCloudListeners()
        activeListenerUid = safeUid

        cloudBootstrapJob = scope.launch(Dispatchers.IO) {
            try {
                // Ensure users/{uid} exists (default 10000 USD, leverage 500) & sync displayName/photoURL
                cloud.ensureUserDocumentExists(
                    uid = safeUid,
                    email = email,
                    displayName = displayName,
                    photoURL = photoUrl
                )

                // Guard against auth change during bootstrap
                if (activeListenerUid != safeUid || firebaseAuth?.currentUser?.uid != safeUid) {
                    return@launch
                }

                // 1. Real-time listener on users/{uid}
                cloudAccountJob = scope.launch(Dispatchers.IO) {
                    cloud.observeUserAccount(safeUid)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is kotlinx.coroutines.CancellationException) throw error
                        }
                        .collect { cloudAcc ->
                            if (activeListenerUid != safeUid) return@collect
                            if (cloudAcc != null) {
                                if (cloudAcc.email.isNotBlank()) {
                                    _currentUserEmail.value = cloudAcc.email
                                }
                                if (cloudAcc.displayName.isNotBlank()) {
                                    _currentUserDisplayName.value = cloudAcc.displayName
                                }
                                if (cloudAcc.photoURL.isNotBlank()) {
                                    _currentUserPhotoUrl.value = cloudAcc.photoURL
                                }
                                cloudAuthoritativeBalance = cloudAcc.balance
                                cloudLeverage = cloudAcc.leverage.toDouble().coerceAtLeast(1.0)
                                val current = _account.value
                                val domainAccount = cloudAcc.toDomain(
                                    equity = current.equity,
                                    margin = current.margin,
                                    freeMargin = current.freeMargin,
                                    marginLevel = current.marginLevel,
                                    floatingPnL = current.floatingPnL
                                )
                                activeAccountKey = "main"
                                _account.value = domainAccount
                                _accounts.value = listOf(domainAccount)
                                recalculatePositionsAndAccount(_quotes.value)
                            }
                        }
                }

                // 2. Real-time listener on users/{uid}/open_trades (ordered by openTime DESC)
                cloudPositionsJob = scope.launch(Dispatchers.IO) {
                    cloud.observeOpenTrades(safeUid)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is kotlinx.coroutines.CancellationException) throw error
                        }
                        .collect { records ->
                            if (activeListenerUid != safeUid) return@collect
                            openTradeDocIdById.clear()
                            val positions = records.map { rec ->
                                openTradeDocIdById[rec.position.id] = rec.docId
                                rec.position
                            }.distinctBy { it.id }

                            _positions.value = positions
                            val maxId = positions.maxOfOrNull { it.id } ?: 1000L
                            if (maxId >= nextPositionId) {
                                nextPositionId = maxId + 1L
                            }
                            recalculatePositionsAndAccount(_quotes.value)
                        }
                }

                // 3. Real-time listener on users/{uid}/trade_history (ordered by closeTime DESC)
                cloudHistoryJob = scope.launch(Dispatchers.IO) {
                    cloud.observeTradeHistory(safeUid)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is kotlinx.coroutines.CancellationException) throw error
                        }
                        .collect { records ->
                            if (activeListenerUid != safeUid) return@collect
                            historyDocIdById.clear()
                            val trades = records.map { rec ->
                                historyDocIdById[rec.trade.id] = rec.docId
                                rec.trade
                            }.distinctBy { it.id }

                            _history.value = sortHistoryChronologically(trades)
                            val maxId = trades.maxOfOrNull { it.id } ?: 1000L
                            if (maxId >= nextPositionId) {
                                nextPositionId = maxId + 1L
                            }
                            recalculatePositionsAndAccount(_quotes.value)
                        }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    @Synchronized
    private fun stopCloudListeners() {
        activeListenerUid = null
        cloudBootstrapJob?.cancel()
        cloudPositionsJob?.cancel()
        cloudHistoryJob?.cancel()
        cloudAccountJob?.cancel()
        cloudBootstrapJob = null
        cloudPositionsJob = null
        cloudHistoryJob = null
        cloudAccountJob = null
    }

    fun syncNowWithServer() {
        val user = firebaseAuth?.currentUser ?: return
        val cloud = cloudRepository ?: return
        if (user.uid.isBlank()) return
        startCloudListeners(
            uid = user.uid,
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl?.toString(),
            cloud = cloud
        )
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
        }
        return ticketSequenceCursor
    }

    /**
     * Generates a formatted unique Ticket ID string (e.g., "#387248912").
     */
    fun generateUniqueTicketId(): String {
        return "#${generateUniqueTicketNumber()}"
    }

    // Trade execution functions — writes ONLY to users/{uid}/open_trades
    fun executeOrder(symbol: String, isBuy: Boolean, lots: Double, sl: Double? = null, tp: Double? = null): TradePosition {
        if (!_isCloudSynced.value || _accounts.value.isEmpty()) {
            return TradePosition(
                id = 0L,
                symbol = symbol,
                isBuy = isBuy,
                lots = lots,
                openPrice = 0.0,
                currentPrice = 0.0,
                openTime = ""
            )
        }
        val quote = _quotes.value.find { it.symbol == symbol } ?: initialQuotes.first()
        val entryPrice = if (isBuy) quote.ask else quote.bid
        val closePriceNow = if (isBuy) quote.bid else quote.ask
        val newId = generateUniqueTicketNumber()

        val nowFormatted = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val openComm = calculateCommissionUsd(symbol, lots, isOpenOnly = true)
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
            openTime = nowFormatted,
            commission = openComm,
            swap = 0.0
        )

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.openTrade(newPos, uid).onSuccess { docId ->
                    openTradeDocIdById[newId] = docId
                }
            }
        }
        return newPos
    }

    /**
     * Atomically closes a position via Firestore runTransaction():
     * verifies open trade exists, deletes from open_trades, saves to trade_history
     * with netProfit = grossProfit - commission + swap, and updates users/{uid} balance.
     */
    fun closePosition(positionId: Long) {
        val pos = _positions.value.find { it.id == positionId } ?: return
        val docId = openTradeDocIdById[positionId] ?: return
        val quoteMap = _quotes.value.associateBy { it.symbol }
        val quote = quoteMap[pos.symbol]
        val closePrice = if (quote != null) (if (pos.isBuy) quote.bid else quote.ask) else pos.currentPrice
        val exactProfit = calculatePositionProfitUsd(pos, closePrice, quoteMap)
        val rawCloseTime = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val roundTurnCommission = calculateCommissionUsd(pos.symbol, pos.lots, isOpenOnly = false)
        val rolloverSwap = calculateSwapUsd(pos.symbol, pos.isBuy, pos.lots, pos.openTime, rawCloseTime)
        val outcome = inferHitOutcomeFromPrices(pos.symbol, pos.isBuy, pos.openPrice, closePrice, pos.sl, pos.tp)

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.closeTradeTransaction(
                    openTradeDocId = docId,
                    closePrice = closePrice,
                    grossProfit = exactProfit,
                    commission = roundTurnCommission,
                    swap = rolloverSwap,
                    closeLots = null,
                    ticketId = pos.id,
                    hitOutcome = outcome,
                    explicitUid = uid
                )
            }
        }
    }

    /**
     * Atomically closes part (or all) of an open position via Firestore runTransaction().
     */
    fun closePositionPartial(positionId: Long, closeLots: Double) {
        val pos = _positions.value.find { it.id == positionId } ?: return
        val docId = openTradeDocIdById[positionId] ?: return
        val quoteMap = _quotes.value.associateBy { it.symbol }
        val quote = quoteMap[pos.symbol]
        val closePrice = if (quote != null) (if (pos.isBuy) quote.bid else quote.ask) else pos.currentPrice
        val safeCloseLots = roundTo2(closeLots.coerceIn(0.01, pos.lots))
        val remainingLots = roundTo2(pos.lots - safeCloseLots)
        val closedPartPos = pos.copy(lots = safeCloseLots)
        val proportionalProfit = calculatePositionProfitUsd(closedPartPos, closePrice, quoteMap)
        val rawCloseTime = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val partialCommission = calculateCommissionUsd(pos.symbol, safeCloseLots, isOpenOnly = false)
        val partialSwap = calculateSwapUsd(pos.symbol, pos.isBuy, safeCloseLots, pos.openTime, rawCloseTime)
        val closedTicketId = if (remainingLots <= 0.005) pos.id else generateUniqueTicketNumber()
        val outcome = inferHitOutcomeFromPrices(pos.symbol, pos.isBuy, pos.openPrice, closePrice, pos.sl, pos.tp)

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.closeTradeTransaction(
                    openTradeDocId = docId,
                    closePrice = closePrice,
                    grossProfit = proportionalProfit,
                    commission = partialCommission,
                    swap = partialSwap,
                    closeLots = if (remainingLots <= 0.005) null else safeCloseLots,
                    ticketId = closedTicketId,
                    hitOutcome = outcome,
                    explicitUid = uid
                )
            }
        }
    }

    /**
     * Calculates exact USD profit for a closed historical trade using contract math.
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
     * Opens a new running trade or modifies SL/TP/details on an existing open trade in `users/{uid}/open_trades`.
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
        val rawSymbol = symbol.trim().uppercase(Locale.US).ifBlank { "NAS100" }
        val cleanSymbol = if (rawSymbol == "NDX100") "NAS100" else rawSymbol
        val safeLots = roundTo2(lots.coerceAtLeast(0.01))
        val nowFormatted = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val rawOpenTime = openTime.trim().ifBlank { nowFormatted }

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

        val openComm = calculateCommissionUsd(cleanSymbol, safeLots, isOpenOnly = true)
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
            openTime = rawOpenTime,
            commission = openComm,
            swap = 0.0
        )

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        val existingOpenDocId = existingId?.let { openTradeDocIdById[it] }
        val existingHistoryDocId = existingId?.let { historyDocIdById[it] }

        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                if (existingOpenDocId != null) {
                    cloud.updateOpenTrade(
                        docId = existingOpenDocId,
                        symbol = cleanSymbol,
                        isBuy = isBuy,
                        lots = safeLots,
                        openPrice = openPrice,
                        sl = sl?.takeIf { it > 0.0 } ?: 0.0,
                        tp = tp?.takeIf { it > 0.0 } ?: 0.0,
                        commission = openComm,
                        swap = 0.0,
                        explicitUid = uid
                    )
                } else {
                    if (existingHistoryDocId != null) {
                        val remainingHistory = _history.value.filter { it.id != existingId }
                        val stats = computeAccountStatsFromHistory(remainingHistory, _account.value.deposit)
                        cloud.deleteHistoryDealAndBalance(
                            dealDocId = existingHistoryDocId,
                            updatedBalance = stats.balance,
                            updatedRealizedProfit = stats.realizedProfit,
                            updatedTotalWithdrawal = stats.totalWithdrawal,
                            updatedTotalSwap = stats.totalSwap,
                            updatedTotalCommission = stats.totalCommission,
                            explicitUid = uid
                        )
                    }
                    cloud.openTrade(runningPos, uid).onSuccess { docId ->
                        openTradeDocIdById[targetId] = docId
                    }
                }
            }
        }
        return runningPos
    }

    /**
     * Deletes an open running position directly from `users/{uid}/open_trades`.
     */
    fun deleteRunningPosition(positionId: Long) {
        val docId = openTradeDocIdById[positionId] ?: return
        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.deleteOpenTrade(docId, uid)
            }
        }
    }

    private data class HistoryStats(
        val balance: Double,
        val realizedProfit: Double,
        val totalWithdrawal: Double,
        val totalSwap: Double,
        val totalCommission: Double
    )

    private fun computeAccountStatsFromHistory(historyList: List<HistoryTrade>, deposit: Double): HistoryStats {
        val closedTrades = historyList.filter { !it.isWithdrawal }
        val realized = roundTo2(closedTrades.sumOf { it.profit })
        val totSwap = roundTo2(closedTrades.sumOf { it.swap })
        val totComm = roundTo2(closedTrades.sumOf { it.commission })
        val totWithdrawals = roundTo2(historyList.filter { it.isWithdrawal }.sumOf { abs(it.profit) })
        val newBalance = roundTo2(deposit.coerceAtLeast(0.0) + realized + totSwap + totComm - totWithdrawals)
        return HistoryStats(
            balance = newBalance,
            realizedProfit = realized,
            totalWithdrawal = totWithdrawals,
            totalSwap = totSwap,
            totalCommission = totComm
        )
    }

    /**
     * Adds or updates a closed trade in `users/{uid}/trade_history` and atomically reconciles `users/{uid}` balance.
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
        hitOutcome: TradeHitOutcome? = null,
        customCommission: Double? = null,
        customSwap: Double? = null
    ): HistoryTrade {
        val uniqueTicketNum = if (existingId == null) generateUniqueTicketNumber() else null
        val targetId = existingId ?: uniqueTicketNum ?: nextPositionId++
        val rawSymbol = symbol.trim().uppercase(Locale.US).ifBlank { "NAS100" }
        val cleanSymbol = if (rawSymbol == "NDX100") "NAS100" else rawSymbol
        val safeLots = roundTo2(lots.coerceAtLeast(0.01))
        val computedProfit = customProfit?.let { roundTo2(it) }
            ?: calculateHistoricalTradeProfit(cleanSymbol, isBuy, safeLots, openPrice, closePrice)
        val nowFormatted = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val rawCloseTime = closeTime.trim().ifBlank { nowFormatted }
        val rawOpenTime = openTime.trim().ifBlank { rawCloseTime }
        val prevTrade = existingId?.let { id -> _history.value.find { it.id == id } }
        val shouldForceRandomOpenSec = prevTrade == null ||
            rawOpenTime == prevTrade.openTime ||
            rawOpenTime.take(16) != prevTrade.openTime.take(16)
        val cleanOpenTime = randomizeOpenTimeSecond(
            openTimeStr = rawOpenTime,
            closeTimeStr = rawCloseTime,
            excludeTradeId = targetId,
            forceNewRandomSecond = shouldForceRandomOpenSec
        )
        val shouldForceRandomCloseSec = prevTrade == null ||
            rawCloseTime == prevTrade.closeTime ||
            rawCloseTime.take(16) != prevTrade.closeTime.take(16)
        val cleanCloseTime = randomizeCloseTimeSecond(
            openTimeStr = cleanOpenTime,
            closeTimeStr = rawCloseTime,
            excludeTradeId = targetId,
            forceNewRandomSecond = shouldForceRandomCloseSec
        )
        val computedCommission = customCommission?.let { roundTo2(it) }
            ?: calculateCommissionUsd(cleanSymbol, safeLots, isOpenOnly = false)
        val computedSwap = customSwap?.let { roundTo2(it) }
            ?: calculateSwapUsd(cleanSymbol, isBuy, safeLots, cleanOpenTime, cleanCloseTime)

        val candidateTicket = customTicketId?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("#")) it else "#$it"
        }
        val formattedTicket = candidateTicket ?: "#$targetId"

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
            commission = computedCommission,
            swap = computedSwap,
            sl = cleanSl,
            tp = cleanTp,
            ticketId = formattedTicket,
            isWithdrawal = false,
            comment = "",
            hitOutcome = resolvedOutcome
        )

        val nextHistory = sortHistoryChronologically(_history.value.filter { it.id != targetId } + tradeRecord)
        val stats = computeAccountStatsFromHistory(nextHistory, _account.value.deposit)
        val existingDocId = existingId?.let { historyDocIdById[it] }
        val deleteOpenDocId = existingId?.let { openTradeDocIdById[it] }

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.saveHistoryDealAndBalance(
                    trade = tradeRecord,
                    existingDocId = existingDocId,
                    updatedBalance = stats.balance,
                    updatedRealizedProfit = stats.realizedProfit,
                    updatedTotalWithdrawal = stats.totalWithdrawal,
                    updatedTotalSwap = stats.totalSwap,
                    updatedTotalCommission = stats.totalCommission,
                    deleteOpenDocId = deleteOpenDocId,
                    explicitUid = uid
                ).onSuccess { docId ->
                    historyDocIdById[targetId] = docId
                }
            }
        }
        return tradeRecord
    }

    /**
     * Adds or updates a Withdrawal record in `users/{uid}/trade_history` and atomically reconciles `users/{uid}` balance.
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
        val cleanAmount = roundTo2(abs(amount).coerceAtLeast(0.01))
        val nowFormatted = synchronized(dateTimeFormat) { dateTimeFormat.format(Date(SriLankaMarketHours.chartNowMillis())) }
        val cleanCloseTime = closeTime.trim().ifBlank { nowFormatted }
        val cleanComment = comment.trim().ifBlank { "Withdrawal" }
        val candidateTicket = customTicketId?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("#")) it else "#$it"
        }
        val formattedTicket = candidateTicket ?: "#$targetId"

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

        val nextHistory = sortHistoryChronologically(_history.value.filter { it.id != targetId } + withdrawalRecord)
        val stats = computeAccountStatsFromHistory(nextHistory, _account.value.deposit)
        val existingDocId = existingId?.let { historyDocIdById[it] }

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.saveHistoryDealAndBalance(
                    trade = withdrawalRecord,
                    existingDocId = existingDocId,
                    updatedBalance = stats.balance,
                    updatedRealizedProfit = stats.realizedProfit,
                    updatedTotalWithdrawal = stats.totalWithdrawal,
                    updatedTotalSwap = stats.totalSwap,
                    updatedTotalCommission = stats.totalCommission,
                    explicitUid = uid
                ).onSuccess { docId ->
                    historyDocIdById[targetId] = docId
                }
            }
        }
        return withdrawalRecord
    }

    /**
     * Deletes a Trade or Withdrawal from `users/{uid}/trade_history` and atomically reconciles `users/{uid}` balance.
     */
    fun deleteHistoryItem(id: Long) {
        val docId = historyDocIdById[id] ?: return
        val nextHistory = _history.value.filter { it.id != id }
        val stats = computeAccountStatsFromHistory(nextHistory, _account.value.deposit)

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.deleteHistoryDealAndBalance(
                    dealDocId = docId,
                    updatedBalance = stats.balance,
                    updatedRealizedProfit = stats.realizedProfit,
                    updatedTotalWithdrawal = stats.totalWithdrawal,
                    updatedTotalSwap = stats.totalSwap,
                    updatedTotalCommission = stats.totalCommission,
                    explicitUid = uid
                )
            }
        }
    }

    fun setDepositAmount(newDeposit: Double, newDepositDate: String? = null) {
        val cleanDeposit = roundTo2(newDeposit.coerceAtLeast(0.0))
        val current = _account.value
        val rawDate = newDepositDate?.trim()?.takeIf { it.isNotBlank() } ?: current.depositDate
        val updatedDate = if (rawDate.count { it == ':' } < 2 || rawDate.endsWith(":00")) {
            randomizeOpenTimeSecond(rawDate, null, null, forceNewRandomSecond = true)
        } else {
            rawDate
        }
        val stats = computeAccountStatsFromHistory(_history.value, cleanDeposit)
        val updatedAccount = current.copy(
            deposit = cleanDeposit,
            depositDate = updatedDate,
            balance = stats.balance,
            realizedProfit = stats.realizedProfit,
            totalWithdrawal = stats.totalWithdrawal,
            totalSwap = stats.totalSwap,
            totalCommission = stats.totalCommission
        )

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.updateUserAccount(updatedAccount, uid)
            }
        }
    }

    companion object {
        const val LEVERAGE = 500.0
        const val BASE_HISTORY_PROFIT = 0.0

        val EMPTY_LOGGED_OUT_ACCOUNT = AccountInfo(
            key = "",
            balance = 0.0,
            equity = 0.0,
            margin = 0.0,
            freeMargin = 0.0,
            marginLevel = 0.0,
            floatingPnL = 0.0,
            accountName = "",
            brokerName = "",
            accountId = "",
            serverName = "",
            isReal = true,
            deposit = 0.0,
            depositDate = "",
            realizedProfit = 0.0,
            totalWithdrawal = 0.0,
            totalSwap = 0.0,
            totalCommission = 0.0,
            logoType = "AUTO"
        )
    }

    fun resetDemoAccount() {
        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.resetUserAccount(uid)
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
        val existingAcc = _account.value
        val resolvedIsReal = isReal ?: when {
            accountId.contains("Demo", ignoreCase = true) ||
                accountId.contains("SIM", ignoreCase = true) ||
                serverName.contains("Demo", ignoreCase = true) -> false
            accountId.contains("Live", ignoreCase = true) ||
                accountName.contains("REAL", ignoreCase = true) -> true
            else -> existingAcc.isReal
        }

        val stats = computeAccountStatsFromHistory(_history.value, existingAcc.deposit)
        val updatedBalance = newBalance?.let { roundTo2(it) } ?: existingAcc.balance
        val updatedDeposit = if (newBalance != null) {
            roundTo2((updatedBalance - stats.realizedProfit - stats.totalSwap - stats.totalCommission + stats.totalWithdrawal).coerceAtLeast(0.0))
        } else {
            existingAcc.deposit.coerceAtLeast(0.0)
        }

        val updatedAcc = existingAcc.copy(
            key = "main",
            accountName = accountName.trim().ifBlank { existingAcc.accountName },
            brokerName = brokerName.trim().ifBlank { existingAcc.brokerName },
            accountId = accountId.trim().ifBlank { existingAcc.accountId },
            serverName = serverName.trim().ifBlank { existingAcc.serverName },
            isReal = resolvedIsReal,
            deposit = updatedDeposit,
            realizedProfit = stats.realizedProfit,
            totalWithdrawal = stats.totalWithdrawal,
            totalSwap = stats.totalSwap,
            totalCommission = stats.totalCommission,
            balance = updatedBalance,
            logoType = logoType?.takeIf { it.isNotBlank() } ?: existingAcc.logoType
        )

        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.updateUserAccount(updatedAcc, uid)
            }
        }
    }

    /**
     * Unified single-account architecture (`users/{uid}`): updates the authenticated user's profile
     * and starting balance directly in Firestore so Android & iOS stay 100% synced.
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
        val cleanBalance = roundTo2(initialBalance.coerceAtLeast(0.0))
        val updatedAcc = _account.value.copy(
            key = "main",
            balance = cleanBalance,
            deposit = cleanBalance,
            accountName = accountName.trim().ifBlank { _account.value.accountName.ifBlank { "Trading Account" } },
            brokerName = brokerName.trim().ifBlank { _account.value.brokerName.ifBlank { "Moneta Markets (Pty) Ltd" } },
            accountId = accountId.trim().ifBlank { _account.value.accountId },
            serverName = serverName.trim().ifBlank { _account.value.serverName },
            isReal = isReal,
            logoType = logoType.ifBlank { "AUTO" }
        )
        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.updateUserAccount(updatedAcc, uid)
            }
        }
        return updatedAcc
    }

    fun switchAccount(targetKey: String) {
        // Unified single-user account under users/{uid}
        syncNowWithServer()
    }

    fun deleteAccount(targetKey: String): Boolean {
        // Reset user's unified account to clean defaults in Firestore
        resetDemoAccount()
        return true
    }

    fun topUpDemo(amount: Double) {
        val cleanAdd = roundTo2(amount.coerceAtLeast(0.0))
        if (cleanAdd <= 0.0) return
        val current = _account.value
        val updatedAccount = current.copy(
            balance = roundTo2(current.balance + cleanAdd),
            deposit = roundTo2(current.deposit + cleanAdd)
        )
        val uid = firebaseAuth?.currentUser?.uid
        val cloud = cloudRepository
        if (uid != null && cloud != null) {
            scope.launch(Dispatchers.IO) {
                cloud.updateUserAccount(updatedAccount, uid)
            }
        }
    }
}
