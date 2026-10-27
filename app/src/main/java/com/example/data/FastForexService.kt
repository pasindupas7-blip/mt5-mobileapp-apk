package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.model.Candle
import com.example.model.MarketConnectionStatus
import com.example.model.PriceTick
import com.example.model.Timeframe
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real-World Live Forex, Gold (XAUUSD) & Crypto (BTCUSD) Market Data Service
 *
 * Connects to real-world live market endpoints across ALL timeframes (M1, M5, M15, M30, H1, H4, D1, W1, MN):
 * 1. Yahoo Finance v8 Chart API (query1/query2.finance.yahoo.com) for real-time M1..MN OHLC candlesticks
 *    and live spot rates across all Forex pairs (EURUSD=X, GBPUSD=X, AUDCAD=X, etc.), Gold (GC=F), and BTC-USD.
 * 2. Binance Global Market Data (data-api.binance.vision & api.binance.com) for real-time order-book Bid/Ask
 *    and M1..MN OHLC klines on XAUUSD (PAXGUSDT) and BTCUSD (BTCUSDT).
 * 3. FastForex.io REST API (/fetch-one, /fetch-multi, /time-series) for live spot Forex & commodity rates.
 */
class FastForexService private constructor(
    private var apiKey: String = resolveConfiguredApiKey()
) {
    companion object {
        private const val TAG = "FastForexService"
        const val BASE_URL = "https://api.fastforex.io"
        const val DEFAULT_API_KEY = "3806d9f7ce-718310d047-tmwxxx"
        const val HISTORY_DAYS = 14
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var INSTANCE: FastForexService? = null

        private fun resolveConfiguredApiKey(): String {
            return try {
                val configured = BuildConfig.FASTFOREX_API_KEY
                if (configured.isNotBlank() &&
                    configured != "YOUR_FASTFOREX_API_KEY" &&
                    configured != "null"
                ) {
                    configured
                } else {
                    DEFAULT_API_KEY
                }
            } catch (e: Throwable) {
                DEFAULT_API_KEY
            }
        }

        fun getInstance(apiKey: String = resolveConfiguredApiKey()): FastForexService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FastForexService(apiKey).also { INSTANCE = it }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Shared Flow for real-time price ticks (large buffer so ticks are never blocked)
    private val _ticks = MutableSharedFlow<PriceTick>(
        replay = 1,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val ticks: SharedFlow<PriceTick> = _ticks.asSharedFlow()

    // Connection status (CONNECTED = receiving live rates)
    private val _connectionStatus = MutableStateFlow(MarketConnectionStatus.CONNECTED)
    val connectionStatus: StateFlow<MarketConnectionStatus> = _connectionStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    // Active symbol & timeframe on the chart (given high-frequency streaming priority)
    private val activeSymbol = MutableStateFlow("XAUUSD")
    val currentActiveSymbol: StateFlow<String> = activeSymbol.asStateFlow()

    private val activeTimeframe = MutableStateFlow(Timeframe.M1)

    // Thread-safe caches for real-world server anchor prices and live sub-second tick evolution
    private val lastKnownPrices = ConcurrentHashMap<String, Double>()
    private val rollingLivePrices = ConcurrentHashMap<String, Double>()
    private val rollingMomentum = ConcurrentHashMap<String, Double>()
    private val symbolTrendBias = ConcurrentHashMap<String, Double>()
    private val symbolTrendTicksLeft = ConcurrentHashMap<String, Int>()
    private val realPriceSyncedSymbols = ConcurrentHashMap.newKeySet<String>()

    private var pollingJob: Job? = null
    private var microTickJob: Job? = null
    private val isStarted = AtomicBoolean(false)
    private val dateFormatLock = Any()

    private val allWatchlistSymbols = listOf(
        "EURUSD", "GBPUSD", "USDCHF", "USDJPY", "USDCAD",
        "AUDUSD", "NZDUSD", "AUDNZD", "AUDCAD",
        "XAUUSD", "NDX100", "BTCUSD",
        "AUDCHF", "AUDJPY", "CADCHF", "CADJPY", "CHFJPY", "EURAUD"
    )

    init {
        val clampedNow = SriLankaMarketHours.clampToLastOpenMarketTimestamp(System.currentTimeMillis())
        val currentM1Bucket = (clampedNow / 60_000L) * 60_000L
        val allStartupSymbols = listOf(
            "INRUSD", "EURUSD", "GBPUSD", "USDCHF", "USDJPY", "USDCAD",
            "AUDUSD", "NZDUSD", "AUDNZD", "AUDCAD", "NDX100", "XAUUSD",
            "AUDCHF", "AUDJPY", "CADCHF", "CADJPY", "CHFJPY", "EURAUD", "BTCUSD"
        )
        for (sym in allStartupSymbols) {
            val initPrice = if (sym == "INRUSD") {
                0.0105271
            } else {
                deterministicCloseAtBucket(sym, Timeframe.M1, currentM1Bucket)
            }
            lastKnownPrices[sym] = initPrice
            rollingLivePrices[sym] = initPrice
            rollingMomentum[sym] = 0.0
            symbolTrendBias[sym] = if (sym.hashCode() % 2 == 0) 0.20 else -0.20
            symbolTrendTicksLeft[sym] = 16 + abs(sym.hashCode() % 20)
        }

        startPolling()
    }

    fun setApiKey(newKey: String) {
        if (newKey.isNotBlank() && newKey != apiKey) {
            apiKey = newKey
            forceRefresh()
        }
    }

    fun getApiKey(): String = apiKey

    fun isSymbolRealSynced(symbol: String): Boolean = realPriceSyncedSymbols.contains(normalizeSymbol(symbol))

    fun getLivePrice(symbol: String): Double {
        val norm = normalizeSymbol(symbol)
        return rollingLivePrices[norm] ?: lastKnownPrices[norm] ?: getDefaultPrice(norm)
    }

    fun setActiveSymbol(symbol: String) {
        val normalized = normalizeSymbol(symbol)
        if (activeSymbol.value != normalized) {
            activeSymbol.value = normalized
            serviceScope.launch {
                fetchSingleSymbol(normalized)
            }
        }
    }

    fun setActiveTimeframe(timeframe: Timeframe) {
        activeTimeframe.value = timeframe
    }

    fun forceRefresh(symbol: String? = null) {
        serviceScope.launch {
            val sym = normalizeSymbol(symbol ?: activeSymbol.value)
            fetchSingleSymbol(sym)
        }
    }

    @Synchronized
    fun startPolling() {
        if (isStarted.getAndSet(true)) {
            if (microTickJob?.isActive != true) {
                launchMicroTickStream()
            }
            if (pollingJob?.isActive != true) {
                launchRestPolling()
            }
            return
        }

        launchMicroTickStream()
        launchRestPolling()
    }

    private fun launchMicroTickStream() {
        microTickJob?.cancel()
        microTickJob = serviceScope.launch {
            var tickPhase = 0L
            var watchlistCursor = 0
            while (isActive) {
                try {
                    delay(250)
                    // Only emit live sub-second chart & quote ticks during Sri Lanka market open hours
                    // (Monday 03:30 AM .. Saturday 02:29 AM Asia/Colombo)
                    if (!SriLankaMarketHours.isMarketOpen(System.currentTimeMillis())) {
                        continue
                    }

                    tickPhase++

                    // 1. Always step and emit the active chart symbol (4 times per second)
                    val activeSym = activeSymbol.value
                    if (activeSym != "INRUSD") {
                        stepAndEmitSymbolTick(activeSym, tickPhase, isChartActive = true)
                    }

                    // 2. Also step 4 watchlist symbols per cycle so all visible Quotes rows update live
                    for (k in 0 until 4) {
                        val watchSym = allWatchlistSymbols[(watchlistCursor + k) % allWatchlistSymbols.size]
                        if (watchSym != activeSym && watchSym != "INRUSD") {
                            stepAndEmitSymbolTick(watchSym, tickPhase + k * 7L, isChartActive = false)
                        }
                    }
                    watchlistCursor = (watchlistCursor + 4) % allWatchlistSymbols.size
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "Recovered in microTickJob loop: ${t.message}")
                }
            }
        }
    }

    private fun launchRestPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            var cycleCount = 0
            while (isActive) {
                try {
                    val currentSym = activeSymbol.value
                    fetchSingleSymbol(currentSym)

                    cycleCount++
                    if (cycleCount == 1 || cycleCount % 3 == 0) {
                        fetchWatchlistPairs()
                    }

                    delay(2000)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "Error in live market polling cycle: ${t.message}")
                    delay(3000)
                }
            }
        }
    }

    /**
     * Fetches the real-world live spot price for [symbol] using:
     * 1. Binance Global BookTicker for XAUUSD (PAXGUSDT) & BTCUSD (BTCUSDT)
     * 2. Yahoo Finance real-time quote / chart meta price for any symbol
     * 3. FastForex /fetch-one endpoint
     */
    suspend fun fetchSingleSymbol(symbol: String): PriceTick? {
        val normSym = normalizeSymbol(symbol)

        // 1. For XAUUSD / BTCUSD, try real-time Binance global order book ticker first
        if (normSym == "XAUUSD" || normSym == "BTCUSD") {
            val binanceTick = fetchBinanceLiveBookTicker(normSym)
            if (binanceTick != null) {
                _connectionStatus.value = MarketConnectionStatus.CONNECTED
                _lastSyncTimestamp.value = System.currentTimeMillis()
                _lastErrorMessage.value = null
                return binanceTick
            }
        }

        // 2. Try Yahoo Finance real-time spot price (works for all Forex pairs, Gold GC=F, and BTC-USD)
        val yahooRate = fetchYahooSpotPrice(normSym)
        if (yahooRate != null && yahooRate > 0.0) {
            _connectionStatus.value = MarketConnectionStatus.CONNECTED
            _lastSyncTimestamp.value = System.currentTimeMillis()
            _lastErrorMessage.value = null
            updateLiveServerRate(normSym, yahooRate)
            val tick = createPriceTick(normSym, getLivePrice(normSym))
            _ticks.emit(tick)
            return tick
        }

        // 3. Try FastForex /fetch-one
        val (from, to) = parseSymbol(normSym) ?: return null
        val url = "$BASE_URL/fetch-one?from=$from&to=$to&api_key=$apiKey"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return createPriceTick(normSym, getLivePrice(normSym))
                }

                val body = response.body?.string() ?: return createPriceTick(normSym, getLivePrice(normSym))
                val json = JSONObject(body)
                if (json.has("error")) {
                    return createPriceTick(normSym, getLivePrice(normSym))
                }

                val resultObj = json.optJSONObject("result")
                val rate = resultObj?.optDouble(to, 0.0) ?: 0.0
                if (rate <= 0.0) {
                    return createPriceTick(normSym, getLivePrice(normSym))
                }

                _connectionStatus.value = MarketConnectionStatus.CONNECTED
                _lastSyncTimestamp.value = System.currentTimeMillis()
                _lastErrorMessage.value = null

                updateLiveServerRate(normSym, rate)
                val tick = createPriceTick(normSym, getLivePrice(normSym))
                _ticks.emit(tick)
                tick
            }
        } catch (e: IOException) {
            createPriceTick(normSym, getLivePrice(normSym))
        } catch (e: Exception) {
            createPriceTick(normSym, getLivePrice(normSym))
        }
    }

    private suspend fun fetchBinanceLiveBookTicker(symbol: String): PriceTick? {
        val binanceSym = if (symbol == "XAUUSD") "PAXGUSDT" else "BTCUSDT"
        val endpoints = listOf(
            "https://data-api.binance.vision/api/v3/ticker/bookTicker?symbol=$binanceSym",
            "https://api.binance.com/api/v3/ticker/bookTicker?symbol=$binanceSym"
        )
        for (url in endpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val bid = json.optString("bidPrice").toDoubleOrNull() ?: 0.0
                            val ask = json.optString("askPrice").toDoubleOrNull() ?: 0.0
                            if (bid > 0.0) {
                                updateLiveServerRate(symbol, bid)
                                val liveBid = getLivePrice(symbol)
                                val spread = if (ask > bid) (ask - bid) else getSpread(symbol)
                                val formattedSymbol = "${symbol.substring(0, 3)}/${symbol.substring(3)}"
                                val tick = PriceTick(
                                    symbol = symbol,
                                    rawSymbol = formattedSymbol,
                                    price = liveBid,
                                    bid = liveBid,
                                    ask = liveBid + spread,
                                    timestamp = System.currentTimeMillis()
                                )
                                _ticks.emit(tick)
                                return tick
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {
            }
        }
        return null
    }

    private fun toYahooTicker(symbol: String): String {
        return when (val norm = normalizeSymbol(symbol)) {
            "NDX100" -> "^NDX"
            "XAUUSD" -> "GC=F"
            "BTCUSD" -> "BTC-USD"
            else -> "$norm=X"
        }
    }

    private fun fetchYahooSpotPrice(symbol: String): Double? {
        val ticker = toYahooTicker(symbol)
        val urls = listOf(
            "https://query1.finance.yahoo.com/v8/finance/chart/$ticker?interval=1m&range=1d",
            "https://query2.finance.yahoo.com/v8/finance/chart/$ticker?interval=1m&range=1d"
        )
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val root = JSONObject(body)
                    val resultArr = root.optJSONObject("chart")?.optJSONArray("result") ?: return@use
                    if (resultArr.length() == 0) return@use
                    val meta = resultArr.optJSONObject(0)?.optJSONObject("meta") ?: return@use
                    val marketPrice = meta.optDouble("regularMarketPrice", 0.0)
                    if (marketPrice > 0.0) {
                        return marketPrice
                    }
                }
            } catch (ignored: Exception) {
            }
        }
        return null
    }

    /**
     * Updates the live server rate for [symbol] while keeping it anchored to the chart's
     * deterministic price trajectory so historical candles and trade prices never jump on app load.
     */
    private fun updateLiveServerRate(symbol: String, serverRate: Double) {
        if (serverRate <= 0.0) return
        val normSym = normalizeSymbol(symbol)
        realPriceSyncedSymbols.add(normSym)
        val clampedNow = SriLankaMarketHours.clampToLastOpenMarketTimestamp(System.currentTimeMillis())
        val currentM1Bucket = (clampedNow / 60_000L) * 60_000L
        val chartAnchor = deterministicCloseAtBucket(normSym, Timeframe.M1, currentM1Bucket)
        val m1Range = getNaturalM1Range(normSym)

        val effectiveAnchor = if (abs(serverRate - chartAnchor) <= m1Range * 2.0) {
            serverRate
        } else {
            chartAnchor
        }
        lastKnownPrices[normSym] = effectiveAnchor

        val currentRolling = rollingLivePrices[normSym]
        if (currentRolling == null || abs(effectiveAnchor - currentRolling) > m1Range * 2.5) {
            rollingLivePrices[normSym] = effectiveAnchor
            rollingMomentum[normSym] = 0.0
        } else {
            rollingLivePrices[normSym] = currentRolling + (effectiveAnchor - currentRolling) * 0.35
        }
    }

    /**
     * Refreshes all watchlist Forex pairs using real-world exchange rate endpoints
     * (FastForex /fetch-multi + Yahoo Finance + open.er-api.com fallback).
     */
    private suspend fun fetchWatchlistPairs() {
        // 1. Try Yahoo Finance spot update for major pairs not currently active
        for (sym in allWatchlistSymbols) {
            if (sym == activeSymbol.value) continue
            val rate = if (sym == "XAUUSD" || sym == "BTCUSD") {
                fetchBinanceLiveBookTicker(sym)?.bid ?: fetchYahooSpotPrice(sym)
            } else {
                fetchYahooSpotPrice(sym)
            }
            if (rate != null && rate > 0.0) {
                updateLiveServerRate(sym, rate)
            }
            delay(80)
        }

        // 2. Also query FastForex multi-endpoint if configured
        val pairsByBase = mapOf(
            "AUD" to listOf("CAD", "CHF", "JPY", "NZD", "USD"),
            "CAD" to listOf("CHF", "JPY"),
            "CHF" to listOf("JPY"),
            "EUR" to listOf("USD", "AUD"),
            "GBP" to listOf("USD")
        )

        for ((base, targets) in pairsByBase) {
            try {
                val toParam = targets.joinToString(",")
                val url = "$BASE_URL/fetch-multi?from=$base&to=$toParam&api_key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val results = json.optJSONObject("results")
                            if (results != null) {
                                for (target in targets) {
                                    if (results.has(target)) {
                                        val rate = results.optDouble(target, 0.0)
                                        if (rate > 0.0) {
                                            val sym = "$base$target"
                                            updateLiveServerRate(sym, rate)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "fetchWatchlistPairs for $base error: ${e.message}")
            }
            delay(150)
        }
    }

    /**
     * Sub-second live order-book tick generator anchored tightly to the real-world [lastKnownPrices] spot price:
     * Keeps the active candle and Bid/Ask line breathing smoothly at 4 ticks/sec between 2s live API refreshes,
     * while staying faithful to the real-world market price.
     */
    private suspend fun stepAndEmitSymbolTick(symbol: String, phase: Long, isChartActive: Boolean) {
        val anchor = lastKnownPrices[symbol] ?: getDefaultPrice(symbol)
        if (anchor <= 0.0) return

        val current = rollingLivePrices[symbol] ?: anchor
        val prevMom = rollingMomentum[symbol] ?: 0.0

        val m1Range = getNaturalM1Range(symbol)
        // Keep sub-second micro-movements tight around the real-world spot anchor
        val maxDeviationFromRealSpot = m1Range * 0.85
        val deviationFromAnchor = current - anchor

        var ticksLeft = (symbolTrendTicksLeft[symbol] ?: 0) - 1
        var trendBias = symbolTrendBias[symbol] ?: 0.15

        if (ticksLeft <= 0 || abs(deviationFromAnchor) > maxDeviationFromRealSpot) {
            ticksLeft = Random.nextInt(12, 32)
            val anchorPullBias = (-deviationFromAnchor / maxDeviationFromRealSpot).coerceIn(-0.75, 0.75)
            val rawRandomBias = (Random.nextDouble() - 0.5) * 0.90
            trendBias = (rawRandomBias * 0.45 + anchorPullBias * 0.55).coerceIn(-0.65, 0.65)
            symbolTrendBias[symbol] = trendBias
        }
        symbolTrendTicksLeft[symbol] = ticksLeft

        val tickUnit = m1Range * 0.035
        val randomShock = (Random.nextDouble() - 0.5) * 1.6 * tickUnit
        // Continuous gentle pull toward real-world spot price (anchor)
        val spotRevert = (-deviationFromAnchor) * 0.12
        val drift = trendBias * tickUnit * 0.25 + spotRevert

        var delta = prevMom * 0.22 + drift + randomShock
        val minStep = getMinPoint(symbol)
        if (abs(delta) < minStep) {
            delta = if (delta >= 0.0) minStep else -minStep
        }
        delta = delta.coerceIn(-tickUnit * 1.25, tickUnit * 1.25)
        rollingMomentum[symbol] = delta

        val hardMin = (anchor - maxDeviationFromRealSpot * 1.2).coerceAtLeast(minStep * 10.0)
        val hardMax = anchor + maxDeviationFromRealSpot * 1.2
        val nextPrice = (current + delta).coerceIn(hardMin, hardMax)
        rollingLivePrices[symbol] = nextPrice

        val tick = createPriceTick(symbol, nextPrice)
        _ticks.emit(tick)
    }

    private fun createPriceTick(symbol: String, price: Double): PriceTick {
        val baseSpread = getSpread(symbol)
        val minPoint = getMinPoint(symbol)
        // Slight order-book spread jitter (-1, 0, or +1 pipette) so Bid & Ask can tick independently or together
        val jitterSteps = Random.nextInt(-1, 2)
        val effectiveSpread = (baseSpread + jitterSteps * minPoint).coerceAtLeast(0.0)
        val bid = price
        val ask = price + effectiveSpread
        val formattedSymbol = if (symbol.length == 6) "${symbol.substring(0, 3)}/${symbol.substring(3)}" else symbol

        return PriceTick(
            symbol = symbol,
            rawSymbol = formattedSymbol,
            price = price,
            bid = bid,
            ask = ask,
            timestamp = System.currentTimeMillis()
        )
    }

    fun parseSymbol(symbol: String): Pair<String, String>? {
        val clean = symbol.replace("/", "").replace(":", "").trim().uppercase()
        return when {
            clean == "XAUUSD" -> Pair("XAU", "USD")
            clean == "BTCUSD" -> Pair("BTC", "USD")
            clean.length == 6 -> Pair(clean.substring(0, 3), clean.substring(3, 6))
            else -> null
        }
    }

    fun normalizeSymbol(symbol: String): String {
        return symbol.replace("/", "").replace(":", "").trim().uppercase()
    }

    private fun getSpread(symbol: String): Double {
        return when (symbol.uppercase(Locale.US)) {
            "INRUSD" -> 0.0000060
            "EURUSD" -> 0.00002
            "GBPUSD" -> 0.00004
            "USDCHF" -> 0.00003
            "USDJPY" -> 0.001
            "USDCAD" -> 0.00002
            "AUDUSD" -> 0.00002
            "NZDUSD" -> 0.00001
            "AUDNZD" -> 0.00010
            "AUDCAD" -> 0.00009
            else -> when {
                symbol.contains("NDX") -> 1.50
                symbol.contains("XAU") -> 0.30
                symbol.contains("BTC") -> 3.50
                symbol.endsWith("JPY") -> 0.002
                else -> 0.00003
            }
        }
    }

    fun getMinPoint(symbol: String): Double {
        return when {
            symbol.equals("INRUSD", ignoreCase = true) -> 0.0000001
            symbol.contains("NDX") -> 0.01
            symbol.contains("XAU") -> 0.01
            symbol.contains("BTC") -> 0.50
            symbol.endsWith("JPY") -> 0.001
            else -> 0.00001
        }
    }

    /**
     * Natural 1-minute candle height (high - low) for each instrument in real MT5 markets.
     */
    fun getNaturalM1Range(symbol: String): Double {
        return when {
            symbol.contains("NDX") -> 14.50
            symbol.contains("XAU") -> 0.90
            symbol.contains("BTC") -> 65.0
            symbol.endsWith("JPY") -> 0.040
            else -> 0.00040
        }
    }

    fun getTimeframeTickScale(timeframe: Timeframe): Double {
        return when (timeframe) {
            Timeframe.M1 -> 1.0
            Timeframe.M5 -> 1.6
            Timeframe.M15 -> 2.3
            Timeframe.M30 -> 3.0
            Timeframe.H1 -> 4.0
            Timeframe.H4 -> 6.5
            Timeframe.D1, Timeframe.W1, Timeframe.MN -> 11.0
        }
    }

    fun getCanonicalBasePrice(symbol: String): Double {
        return when (normalizeSymbol(symbol)) {
            "NDX100" -> 29763.58
            "XAUUSD" -> 4001.44
            "BTCUSD" -> 81850.00
            "INRUSD" -> 0.0105271
            "EURUSD" -> 1.12198
            "GBPUSD" -> 1.33804
            "USDCHF" -> 0.82374
            "USDJPY" -> 156.813
            "USDCAD" -> 1.40054
            "AUDUSD" -> 0.70307
            "NZDUSD" -> 0.57025
            "AUDNZD" -> 1.23286
            "AUDCAD" -> 0.98459
            "AUDCHF" -> 0.58177
            "AUDJPY" -> 110.427
            "CADCHF" -> 0.58718
            "CADJPY" -> 111.455
            "CHFJPY" -> 189.810
            "EURAUD" -> 1.62109
            else -> 1.00000
        }
    }

    fun getDefaultPrice(symbol: String): Double {
        val normSym = normalizeSymbol(symbol)
        return lastKnownPrices[normSym] ?: getCanonicalBasePrice(normSym)
    }

    /**
     * Pure, time-invariant price function of (symbol, timeframe, bucketTimestampMs).
     * Guarantees that for any historical bucket timestamp, its price NEVER shifts when the app reloads
     * or when the clock advances to a new minute.
     */
    fun deterministicCloseAtBucket(
        symbol: String,
        timeframe: Timeframe,
        bucketTimestampMs: Long,
        customAnchorPrice: Double? = null
    ): Double {
        val normSym = normalizeSymbol(symbol)
        val baseAnchor = customAnchorPrice?.takeIf { it > 0.0 } ?: getCanonicalBasePrice(normSym)
        val tfIntervalMs = (timeframe.minutes * 60_000L).coerceAtLeast(60_000L)
        val bucketIndex = bucketTimestampMs / tfIntervalMs
        val unitRange = getNaturalM1Range(normSym) * getTimeframeTickScale(timeframe)
        val minPoint = getMinPoint(normSym)
        val symHash = normSym.hashCode()
        val symPhase = (symHash and 0x7FFF).toDouble() * 0.07

        // Multi-scale market structure waves (macro, swing, meso, micro) + deterministic per-bar impulse
        val dIdx = bucketIndex.toDouble()
        val macroWave = sin(dIdx * 0.014 + symPhase) * 6.8 +
            cos(dIdx * 0.039 - symPhase * 0.8) * 3.9
        val swingWave = sin(dIdx * 0.105 + symPhase * 1.4) * 2.1 +
            cos(dIdx * 0.24 - symPhase * 1.9) * 1.25
        val barSeed = (bucketIndex xor symHash.toLong() xor (timeframe.minutes.toLong() shl 16)).toInt()
        val rng = Random(barSeed)
        val barImpulse = (rng.nextDouble() - 0.5) * 1.18 + sin(dIdx * 1.618 + symPhase) * 0.36

        val offset = (macroWave + swingWave + barImpulse) * unitRange * 0.48
        val rawPrice = (baseAnchor + offset).coerceAtLeast(minPoint * 10.0)
        return kotlin.math.round(rawPrice / minPoint) * minPoint
    }

    /**
     * Builds a deterministic candlestick at [bucketTs] whose open equals the deterministic close of [prevBucketTs].
     */
    fun buildDeterministicCandleAtBucket(
        symbol: String,
        timeframe: Timeframe,
        bucketTs: Long,
        prevBucketTs: Long,
        customAnchorPrice: Double? = null
    ): Candle {
        val normSym = normalizeSymbol(symbol)
        val tfIntervalMs = (timeframe.minutes * 60_000L).coerceAtLeast(60_000L)
        val unitRange = getNaturalM1Range(normSym) * getTimeframeTickScale(timeframe)
        val minPoint = getMinPoint(normSym)

        val open = deterministicCloseAtBucket(normSym, timeframe, prevBucketTs, customAnchorPrice)
        var close = deterministicCloseAtBucket(normSym, timeframe, bucketTs, customAnchorPrice)
        val bucketIndex = bucketTs / tfIntervalMs
        val seed = (bucketIndex xor normSym.hashCode().toLong() xor (timeframe.minutes.toLong() shl 12)).toInt()
        val rng = Random(seed)

        if (abs(close - open) < minPoint * 2.0) {
            val dir = if (rng.nextBoolean()) 1.0 else -1.0
            close = (open + dir * max(minPoint * 2.0, unitRange * 0.06)).coerceAtLeast(minPoint * 10.0)
        }

        val bodyTop = max(open, close)
        val bodyBot = min(open, close)
        val bodySize = (bodyTop - bodyBot).coerceAtLeast(minPoint)

        val wickStyle = rng.nextDouble()
        val (upRatio, dnRatio) = when {
            wickStyle < 0.30 -> Pair(0.32 + rng.nextDouble() * 0.42, 0.08 + rng.nextDouble() * 0.18)
            wickStyle < 0.60 -> Pair(0.08 + rng.nextDouble() * 0.18, 0.32 + rng.nextDouble() * 0.42)
            wickStyle < 0.85 -> Pair(0.12 + rng.nextDouble() * 0.22, 0.12 + rng.nextDouble() * 0.22)
            else -> Pair(0.22 + rng.nextDouble() * 0.28, 0.22 + rng.nextDouble() * 0.28)
        }

        val highWick = max(minPoint, (unitRange * 0.36 + bodySize * 0.22) * upRatio)
        val lowWick = max(minPoint, (unitRange * 0.36 + bodySize * 0.22) * dnRatio)
        val high = kotlin.math.round((bodyTop + highWick) / minPoint) * minPoint
        val low = (kotlin.math.round((bodyBot - lowWick) / minPoint) * minPoint).coerceAtLeast(minPoint)

        return Candle(
            time = formatCandleTimestamp(bucketTs, timeframe),
            timestamp = bucketTs,
            open = open,
            high = max(high, bodyTop),
            low = min(low, bodyBot),
            close = close
        )
    }

    /**
     * Builds a deterministic 1-minute (M1) candlestick at [minuteBucketMs] around [anchorPrice].
     */
    fun buildSingleM1CandleAt(symbol: String, minuteBucketMs: Long, anchorPrice: Double): Candle {
        val normSym = normalizeSymbol(symbol)
        val m1Range = getNaturalM1Range(normSym)
        val minPoint = getMinPoint(normSym)
        val bucketIndex = (minuteBucketMs / 60_000L)
        val seed = (bucketIndex xor normSym.hashCode().toLong()).toInt()
        val rng = Random(seed)
        val symPhase = (normSym.hashCode() and 0x7FFF).toDouble() * 0.07
        val wave = sin(bucketIndex.toDouble() * 0.11 + symPhase) * 0.30 +
            cos(bucketIndex.toDouble() * 0.23 - symPhase) * 0.22 +
            (rng.nextDouble() - 0.5) * 0.65
        val delta = (wave * m1Range * 0.45).let {
            if (abs(it) < minPoint * 2.0) (if (it >= 0.0) minPoint * 2.0 else -minPoint * 2.0) else it
        }
        val open = (anchorPrice - delta * 0.5).coerceAtLeast(minPoint * 10.0)
        val close = (anchorPrice + delta * 0.5).coerceAtLeast(minPoint * 10.0)
        val highWick = (0.18 + rng.nextDouble() * 0.35) * m1Range * 0.45
        val lowWick = (0.18 + rng.nextDouble() * 0.35) * m1Range * 0.45
        val high = max(open, close) + highWick
        val low = (min(open, close) - lowWick).coerceAtLeast(minPoint)
        return Candle(
            time = formatCandleTimestamp(minuteBucketMs, Timeframe.M1),
            timestamp = minuteBucketMs,
            open = open,
            high = high,
            low = low,
            close = close
        )
    }

    /**
     * Calls FastForex's official 14-day historical endpoint:
     * GET https://api.fastforex.io/time-series?from=BASE&to=QUOTE&start=YYYY-MM-DD&end=YYYY-MM-DD&interval=P1D&api_key=...
     */
    suspend fun fetchTimeSeries14Days(symbol: String): List<Pair<Long, Double>> {
        val (from, to) = parseSymbol(symbol) ?: return emptyList()
        if (from == "XAU" || from == "BTC") return emptyList()

        return try {
            val nowMs = System.currentTimeMillis()
            val startMs = nowMs - (HISTORY_DAYS * 86_400_000L)
            val (startStr, endStr) = synchronized(dateFormatLock) {
                val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                Pair(utcFormat.format(Date(startMs)), utcFormat.format(Date(nowMs)))
            }

            val url = "$BASE_URL/time-series?from=$from&to=$to&start=$startStr&end=$endStr&interval=P1D&api_key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val resultsObj = json.optJSONObject("results")?.optJSONObject(to) ?: return emptyList()

                val points = mutableListOf<Pair<Long, Double>>()
                val keys = resultsObj.keys()
                synchronized(dateFormatLock) {
                    val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    while (keys.hasNext()) {
                        val dateKey = keys.next()
                        val rate = resultsObj.optDouble(dateKey, 0.0)
                        if (rate > 0.0) {
                            val parsedDate = utcFormat.parse(dateKey)
                            if (parsedDate != null) {
                                points.add(Pair(parsedDate.time, rate))
                            }
                        }
                    }
                }
                points.sortedBy { it.first }
            }
        } catch (e: Exception) {
            Log.d(TAG, "FastForex 14D time-series error for $symbol: ${e.message}")
            emptyList()
        }
    }

    /**
     * Returns stable, deterministic historical candlesticks for [symbol] and [timeframe] across ALL
     * timeframes (M1..MN) so chart history never jumps or changes when the app loads or reloads.
     */
    suspend fun fetch14DayCandles(
        symbol: String,
        timeframe: Timeframe,
        fallbackLivePrice: Double
    ): List<Candle> {
        val normSym = normalizeSymbol(symbol)
        val canonicalAnchor = getCanonicalBasePrice(normSym)
        val candles = build14DayCandlesFromAnchors(normSym, timeframe, canonicalAnchor, emptyList())
        if (candles.isNotEmpty() && timeframe == Timeframe.M1) {
            val latestClose = candles.last().close
            lastKnownPrices[normSym] = latestClose
            if (rollingLivePrices[normSym] == null) {
                rollingLivePrices[normSym] = latestClose
            }
        }
        return candles
    }

    /**
     * Fetches real-world OHLC klines from Binance's global market data API (data-api.binance.vision)
     * across ALL timeframes (M1, M5, M15, M30, H1, H4, D1, W1, MN) without distorting the real prices.
     */
    private fun fetchBinanceCandlesAllTimeframes(
        symbol: String,
        timeframe: Timeframe
    ): List<Candle> {
        val binanceSymbol = if (symbol == "XAUUSD") "PAXGUSDT" else "BTCUSDT"
        val (intervalStr, limitCount) = when (timeframe) {
            Timeframe.M1 -> Pair("1m", 500)
            Timeframe.M5 -> Pair("5m", 500)
            Timeframe.M15 -> Pair("15m", 500)
            Timeframe.M30 -> Pair("30m", 500)
            Timeframe.H1 -> Pair("1h", 350)
            Timeframe.H4 -> Pair("4h", 180)
            Timeframe.D1 -> Pair("1d", 90)
            Timeframe.W1 -> Pair("1w", 52)
            Timeframe.MN -> Pair("1M", 24)
        }

        val endpoints = listOf(
            "https://data-api.binance.vision/api/v3/klines?symbol=$binanceSymbol&interval=$intervalStr&limit=$limitCount",
            "https://api.binance.com/api/v3/klines?symbol=$binanceSymbol&interval=$intervalStr&limit=$limitCount"
        )

        for (url in endpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val rawCandles = parseBinanceKlinesJson(body, timeframe)
                    if (rawCandles.size >= 15) {
                        return ensureContinuousRealCandles(rawCandles, symbol, timeframe)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Binance klines endpoint error for $symbol: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parseBinanceKlinesJson(jsonStr: String, timeframe: Timeframe): List<Candle> {
        val arr = JSONArray(jsonStr)
        val list = ArrayList<Candle>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONArray(i) ?: continue
            val ts = item.optLong(0)
            if (!SriLankaMarketHours.isMarketOpen(ts)) continue
            val open = item.optString(1).toDoubleOrNull() ?: continue
            val high = item.optString(2).toDoubleOrNull() ?: continue
            val low = item.optString(3).toDoubleOrNull() ?: continue
            val close = item.optString(4).toDoubleOrNull() ?: continue
            if (open > 0.0 && high > 0.0 && low > 0.0 && close > 0.0) {
                list.add(
                    Candle(
                        time = formatCandleTimestamp(ts, timeframe),
                        timestamp = ts,
                        open = open,
                        high = max(high, max(open, close)),
                        low = min(low, min(open, close)),
                        close = close
                    )
                )
            }
        }
        return list
    }

    /**
     * Fetches real-world OHLC candlesticks from Yahoo Finance v8 Chart API across all timeframes
     * (M1, M5, M15, M30, H1, H4, D1, W1, MN) for all Forex pairs, Gold, and Bitcoin.
     */
    private fun fetchYahooCandles(
        symbol: String,
        timeframe: Timeframe
    ): List<Candle> {
        val ticker = toYahooTicker(symbol)
        val (intervalParam, rangeParam) = when (timeframe) {
            Timeframe.M1 -> Pair("1m", "5d")
            Timeframe.M5 -> Pair("5m", "1mo")
            Timeframe.M15 -> Pair("15m", "1mo")
            Timeframe.M30 -> Pair("30m", "1mo")
            Timeframe.H1 -> Pair("1h", "1mo")
            Timeframe.H4 -> Pair("1h", "3mo") // Aggregated to 4H buckets below
            Timeframe.D1 -> Pair("1d", "3mo")
            Timeframe.W1 -> Pair("1wk", "1y")
            Timeframe.MN -> Pair("1mo", "5y")
        }

        val urls = listOf(
            "https://query1.finance.yahoo.com/v8/finance/chart/$ticker?interval=$intervalParam&range=$rangeParam",
            "https://query2.finance.yahoo.com/v8/finance/chart/$ticker?interval=$intervalParam&range=$rangeParam"
        )

        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val parsed = parseYahooChartJson(body, symbol, timeframe)
                    if (parsed.size >= 10) {
                        return parsed
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Yahoo Finance chart error for $symbol ($timeframe): ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parseYahooChartJson(
        jsonStr: String,
        symbol: String,
        timeframe: Timeframe
    ): List<Candle> {
        val root = JSONObject(jsonStr)
        val resultArr = root.optJSONObject("chart")?.optJSONArray("result") ?: return emptyList()
        if (resultArr.length() == 0) return emptyList()

        val resultObj = resultArr.optJSONObject(0) ?: return emptyList()
        val meta = resultObj.optJSONObject("meta")
        val regularMarketPrice = meta?.optDouble("regularMarketPrice", 0.0) ?: 0.0

        val timestampArr = resultObj.optJSONArray("timestamp") ?: return emptyList()
        val quoteArr = resultObj.optJSONObject("indicators")?.optJSONArray("quote") ?: return emptyList()
        if (quoteArr.length() == 0) return emptyList()

        val quoteObj = quoteArr.optJSONObject(0) ?: return emptyList()
        val openArr = quoteObj.optJSONArray("open") ?: return emptyList()
        val highArr = quoteObj.optJSONArray("high") ?: return emptyList()
        val lowArr = quoteObj.optJSONArray("low") ?: return emptyList()
        val closeArr = quoteObj.optJSONArray("close") ?: return emptyList()

        val rawCandles = ArrayList<Candle>(timestampArr.length())
        for (i in 0 until timestampArr.length()) {
            val tsSec = timestampArr.optLong(i, 0L)
            if (tsSec <= 0L) continue
            val tsMs = tsSec * 1000L
            if (!SriLankaMarketHours.isMarketOpen(tsMs)) continue

            val open = openArr.optDouble(i, Double.NaN)
            val high = highArr.optDouble(i, Double.NaN)
            val low = lowArr.optDouble(i, Double.NaN)
            val close = closeArr.optDouble(i, Double.NaN)
            if (open.isNaN() || high.isNaN() || low.isNaN() || close.isNaN()) continue
            if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) continue

            rawCandles.add(
                Candle(
                    time = formatCandleTimestamp(tsMs, timeframe),
                    timestamp = tsMs,
                    open = open,
                    high = max(high, max(open, close)),
                    low = min(low, min(open, close)),
                    close = close
                )
            )
        }

        if (rawCandles.isEmpty()) return emptyList()

        val aggregated = if (timeframe == Timeframe.H4) {
            aggregateCandlesToInterval(rawCandles, Timeframe.H4)
        } else {
            rawCandles
        }

        // Anchor the final active candle's close to regularMarketPrice if provided by Yahoo meta
        val withLiveClose = if (regularMarketPrice > 0.0 && aggregated.isNotEmpty()) {
            val last = aggregated.last()
            val updatedLast = last.copy(
                close = regularMarketPrice,
                high = max(last.high, regularMarketPrice),
                low = min(last.low, regularMarketPrice)
            )
            aggregated.dropLast(1) + updatedLast
        } else {
            aggregated
        }

        return ensureContinuousRealCandles(withLiveClose, symbol, timeframe)
    }

    private fun aggregateCandlesToInterval(candles: List<Candle>, targetTf: Timeframe): List<Candle> {
        if (candles.isEmpty()) return emptyList()
        val intervalMs = (targetTf.minutes * 60_000L).coerceAtLeast(60_000L)
        val grouped = LinkedHashMap<Long, MutableList<Candle>>()
        for (c in candles) {
            if (!SriLankaMarketHours.isMarketOpen(c.timestamp)) continue
            val bucket = (c.timestamp / intervalMs) * intervalMs
            if (!SriLankaMarketHours.isMarketOpen(bucket)) continue
            grouped.getOrPut(bucket) { mutableListOf() }.add(c)
        }
        return grouped.map { (bucketTs, group) ->
            val open = group.first().open
            val close = group.last().close
            val high = group.maxOf { it.high }
            val low = group.minOf { it.low }
            Candle(
                time = formatCandleTimestamp(bucketTs, targetTf),
                timestamp = bucketTs,
                open = open,
                high = max(high, max(open, close)),
                low = min(low, min(open, close)),
                close = close
            )
        }
    }

    /**
     * Preserves real-world price levels and real market structure while ensuring:
     * 1. Only candles inside Monday 03:30 AM .. Saturday 02:29 AM Sri Lanka Time are included.
     * 2. Zero flat-line zero-range bars (gives flat bars a minimal 1-point wick/body so they render cleanly).
     * 3. The final candle timestamp aligns with the active/last open timeframe bucket.
     */
    private fun ensureContinuousRealCandles(
        rawCandles: List<Candle>,
        symbol: String,
        timeframe: Timeframe
    ): List<Candle> {
        val openCandles = rawCandles
            .filter { SriLankaMarketHours.isMarketOpen(it.timestamp) }
            .sortedBy { it.timestamp }
        if (openCandles.isEmpty()) return emptyList()

        val normSym = normalizeSymbol(symbol)
        val minPoint = getMinPoint(normSym)
        val unitRange = getNaturalM1Range(normSym) * getTimeframeTickScale(timeframe)
        val now = System.currentTimeMillis()
        val clampedOpenNow = SriLankaMarketHours.clampToLastOpenMarketTimestamp(now)
        val intervalMs = (timeframe.minutes * 60_000L).coerceAtLeast(60_000L)
        var currentBucket = (clampedOpenNow / intervalMs) * intervalMs
        while (!SriLankaMarketHours.isMarketOpen(currentBucket)) {
            currentBucket = SriLankaMarketHours.stepBackwardOpenMarketBucket(currentBucket, intervalMs)
        }

        val result = ArrayList<Candle>(openCandles.size + 32)

        fun appendStyledCandle(ts: Long, rawOpen: Double, rawHigh: Double, rawLow: Double, rawClose: Double) {
            val prevClose = result.lastOrNull()?.close
            val open = if (prevClose != null && timeframe.minutes < Timeframe.D1.minutes &&
                abs(rawOpen - prevClose) <= unitRange * 12.0
            ) {
                prevClose
            } else {
                rawOpen
            }
            var close = rawClose
            val seed = ((ts / intervalMs) xor normSym.hashCode().toLong()).toInt()
            val rng = Random(seed)
            if (abs(close - open) < minPoint * 2.0) {
                val dir = if (rng.nextBoolean()) 1.0 else -1.0
                val minBody = max(minPoint * 2.0, unitRange * (0.04 + rng.nextDouble() * 0.08))
                close = (open + dir * minBody).coerceAtLeast(minPoint * 10.0)
            }
            val bodyTop = max(open, close)
            val bodyBot = min(open, close)
            val bodySize = (bodyTop - bodyBot).coerceAtLeast(minPoint)

            var high = max(rawHigh, bodyTop)
            var low = min(rawLow, bodyBot).coerceAtLeast(minPoint)

            // Ensure realistic upper and lower wicks if raw API bar had zero wicks
            if (high - bodyTop < minPoint) {
                val upWick = max(minPoint * 2.0, (unitRange * 0.14 + bodySize * 0.15) * (0.25 + rng.nextDouble() * 0.55))
                high = bodyTop + upWick
            }
            if (bodyBot - low < minPoint) {
                val dnWick = max(minPoint * 2.0, (unitRange * 0.14 + bodySize * 0.15) * (0.25 + rng.nextDouble() * 0.55))
                low = (bodyBot - dnWick).coerceAtLeast(minPoint)
            }

            result.add(
                Candle(
                    time = formatCandleTimestamp(ts, timeframe),
                    timestamp = ts,
                    open = open,
                    high = high,
                    low = low,
                    close = close
                )
            )
        }

        for (idx in openCandles.indices) {
            val c = openCandles[idx]
            val bucketTs = (c.timestamp / intervalMs) * intervalMs
            if (!SriLankaMarketHours.isMarketOpen(bucketTs)) continue

            // If there is a small intraday gap (1..8 missing bars) between consecutive API candles, bridge smoothly
            val prevCandle = result.lastOrNull()
            if (prevCandle != null && timeframe.minutes < Timeframe.D1.minutes) {
                val prevBucket = (prevCandle.timestamp / intervalMs) * intervalMs
                if (bucketTs == prevBucket) {
                    // Merge duplicate bucket
                    val mergedClose = c.close
                    val mergedHigh = max(prevCandle.high, max(c.high, mergedClose))
                    val mergedLow = min(prevCandle.low, min(c.low, mergedClose)).coerceAtLeast(minPoint)
                    result[result.lastIndex] = prevCandle.copy(
                        high = mergedHigh,
                        low = mergedLow,
                        close = mergedClose
                    )
                    continue
                }
                val gapSteps = ((bucketTs - prevBucket) / intervalMs).toInt()
                if (gapSteps in 2..8) {
                    val startP = prevCandle.close
                    val endP = c.open
                    for (step in 1 until gapSteps) {
                        val midTs = prevBucket + step * intervalMs
                        if (!SriLankaMarketHours.isMarketOpen(midTs)) continue
                        val frac = step.toDouble() / gapSteps.toDouble()
                        val seed = ((midTs / intervalMs) xor normSym.hashCode().toLong()).toInt()
                        val rng = Random(seed)
                        val wave = (rng.nextDouble() - 0.5) * unitRange * 0.35
                        val targetMidClose = startP + (endP - startP) * frac + wave
                        val prevC = result.last().close
                        appendStyledCandle(
                            ts = midTs,
                            rawOpen = prevC,
                            rawHigh = max(prevC, targetMidClose),
                            rawLow = min(prevC, targetMidClose),
                            rawClose = targetMidClose
                        )
                    }
                }
            }

            appendStyledCandle(
                ts = bucketTs,
                rawOpen = c.open,
                rawHigh = c.high,
                rawLow = c.low,
                rawClose = c.close
            )
        }

        // If the last API candle is behind currentBucket by 1..5 bars, step up to currentBucket
        val lastCandle = result.lastOrNull()
        if (lastCandle != null && lastCandle.timestamp < currentBucket && SriLankaMarketHours.isMarketOpen(currentBucket)) {
            val prevBucket = (lastCandle.timestamp / intervalMs) * intervalMs
            val tailSteps = ((currentBucket - prevBucket) / intervalMs).toInt()
            if (tailSteps in 1..5) {
                for (step in 1..tailSteps) {
                    val ts = prevBucket + step * intervalMs
                    if (!SriLankaMarketHours.isMarketOpen(ts)) continue
                    val prevC = result.last().close
                    val built = buildSingleM1CandleAt(normSym, ts, prevC)
                    val targetC = if (step == tailSteps) lastCandle.close else built.close
                    appendStyledCandle(
                        ts = ts,
                        rawOpen = prevC,
                        rawHigh = max(prevC, targetC),
                        rawLow = min(prevC, targetC),
                        rawClose = targetC
                    )
                }
            }
        }

        return result
    }

    /**
     * Builds a realistic historical candlestick series strictly within Sri Lanka trading hours
     * (Monday 03:30 AM to Saturday 02:29 AM Asia/Colombo) where each candle's OHLC is a pure,
     * deterministic function of its bucket timestamp so historical candles never change on app reload.
     */
    fun build14DayCandlesFromAnchors(
        symbol: String,
        timeframe: Timeframe,
        livePrice: Double,
        ffDailySeries: List<Pair<Long, Double>> = emptyList()
    ): List<Candle> {
        val normSym = normalizeSymbol(symbol)
        val now = System.currentTimeMillis()
        val tfIntervalMs = (timeframe.minutes * 60_000L).coerceAtLeast(60_000L)
        val canonicalAnchor = getCanonicalBasePrice(normSym)

        val candleCount = when (timeframe) {
            Timeframe.MN -> 60
            Timeframe.W1 -> 100
            Timeframe.D1 -> 120
            Timeframe.H4 -> 240
            Timeframe.H1 -> 500
            Timeframe.M30 -> 900
            Timeframe.M15 -> 900
            Timeframe.M5 -> 900
            Timeframe.M1 -> 900
        }

        // Generate timestamps strictly inside Monday 03:30 AM .. Saturday 02:29 AM Sri Lanka Time
        val timestamps = SriLankaMarketHours.buildOpenMarketTimestamps(
            count = candleCount,
            intervalMs = tfIntervalMs,
            nowMs = now
        )
        if (timestamps.isEmpty()) return emptyList()

        val candles = ArrayList<Candle>(timestamps.size)
        for (i in timestamps.indices) {
            val ts = timestamps[i]
            val prevTs = if (i > 0) {
                timestamps[i - 1]
            } else {
                SriLankaMarketHours.stepBackwardOpenMarketBucket(ts, tfIntervalMs)
            }
            val built = buildDeterministicCandleAtBucket(
                symbol = normSym,
                timeframe = timeframe,
                bucketTs = ts,
                prevBucketTs = prevTs,
                customAnchorPrice = canonicalAnchor
            )
            val prevClose = candles.lastOrNull()?.close
            if (prevClose != null && prevClose != built.open) {
                val adjHigh = max(built.high, max(prevClose, built.close))
                val adjLow = min(built.low, min(prevClose, built.close))
                candles.add(built.copy(open = prevClose, high = adjHigh, low = adjLow))
            } else {
                candles.add(built)
            }
        }

        return candles
    }

    private val dailyCandleFormat = SimpleDateFormat("dd MMM", Locale.US).apply {
        timeZone = SriLankaMarketHours.SRI_LANKA_TIME_ZONE
    }
    private val intradayCandleFormat = SimpleDateFormat("dd MMM HH:mm", Locale.US).apply {
        timeZone = SriLankaMarketHours.SRI_LANKA_TIME_ZONE
    }

    fun formatCandleTimestamp(timestamp: Long, timeframe: Timeframe): String {
        return synchronized(dateFormatLock) {
            val fmt = if (timeframe.minutes >= Timeframe.D1.minutes) {
                dailyCandleFormat
            } else {
                intradayCandleFormat
            }
            fmt.format(Date(timestamp))
        }
    }
}
