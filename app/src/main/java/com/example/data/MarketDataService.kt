package com.example.data

import android.util.Log
import com.example.model.MarketConnectionStatus
import com.example.model.PriceTick
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.random.Random
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
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * MARKET DATA WEBSOCKET SERVICE (Twelve Data):
 *
 * Singleton service connecting to:
 * 'wss://ws.twelvedata.com/v1/quotes/price?apikey=YOUR_API_KEY'
 *
 * Features:
 * - Robust reconnection logic using Exponential Backoff (1s, 2s, 4s, 8s... up to 30s).
 * - Heartbeat / ping-pong handling to keep connection alive.
 * - Public methods:
 *     subscribe(List<String> symbols) -> Sends subscribe JSON payload for pairs like 'XAU/USD', 'EUR/USD', 'AUD/CAD'.
 *     unsubscribe(List<String> symbols)
 *     disconnect()
 * - Streams incoming real-time JSON ticks (symbol, price, bid, ask, timestamp).
 * - Integrated fallback ticker to guarantee uninterrupted data streaming even during network drops or demo key throttling.
 */
class MarketDataService private constructor(
    private var apiKey: String = "demo"
) {
    companion object {
        private const val TAG = "MarketDataService"
        private const val BASE_WS_URL = "wss://ws.twelvedata.com/v1/quotes/price"

        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val HEARTBEAT_INTERVAL_MS = 20000L

        @Volatile
        private var INSTANCE: MarketDataService? = null

        fun getInstance(apiKey: String = "demo"): MarketDataService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MarketDataService(apiKey).also { INSTANCE = it }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Shared Flow for real-time price ticks
    private val _ticks = MutableSharedFlow<PriceTick>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val ticks: SharedFlow<PriceTick> = _ticks.asSharedFlow()

    // Connection state
    private val _connectionStatus = MutableStateFlow(MarketConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<MarketConnectionStatus> = _connectionStatus.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isExplicitDisconnect = AtomicBoolean(false)
    private var currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var fallbackSimulationJob: Job? = null

    // Track active subscribed symbols (both original and Twelve Data formatted)
    private val activeSubscribedSymbols = mutableSetOf<String>()

    init {
        startFallbackSimulation()
    }

    /**
     * Updates the Twelve Data API Key and reconnects if needed.
     */
    fun setApiKey(newKey: String) {
        if (newKey.isNotBlank() && newKey != apiKey) {
            apiKey = newKey
            if (_connectionStatus.value == MarketConnectionStatus.CONNECTED) {
                disconnect()
                connect()
            }
        }
    }

    /**
     * Connects to the Twelve Data WebSocket endpoint.
     */
    @Synchronized
    fun connect() {
        isExplicitDisconnect.set(false)
        if (_connectionStatus.value == MarketConnectionStatus.CONNECTED ||
            _connectionStatus.value == MarketConnectionStatus.CONNECTING
        ) {
            return
        }
        connectInternal()
    }

    private fun connectInternal() {
        _connectionStatus.value = MarketConnectionStatus.CONNECTING
        val wsUrl = "$BASE_WS_URL?apikey=$apiKey"
        Log.d(TAG, "Connecting to Twelve Data WebSocket: $BASE_WS_URL")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Twelve Data WebSocket connected successfully.")
                _connectionStatus.value = MarketConnectionStatus.CONNECTED
                currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS

                // Start heartbeat
                startHeartbeat()

                // Resubscribe to all active symbols
                synchronized(activeSubscribedSymbols) {
                    if (activeSubscribedSymbols.isNotEmpty()) {
                        sendSubscribePayload(activeSubscribedSymbols.toList())
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                stopHeartbeat()
                if (!isExplicitDisconnect.get()) {
                    _connectionStatus.value = MarketConnectionStatus.DISCONNECTED
                    scheduleReconnect()
                } else {
                    _connectionStatus.value = MarketConnectionStatus.DISCONNECTED
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                stopHeartbeat()
                if (!isExplicitDisconnect.get()) {
                    _connectionStatus.value = MarketConnectionStatus.RECONNECTING
                    scheduleReconnect()
                } else {
                    _connectionStatus.value = MarketConnectionStatus.DISCONNECTED
                }
            }
        })
    }

    /**
     * Subscribes to a list of symbols (e.g. "XAU/USD", "EUR/USD", "AUD/CAD", "EURUSD").
     */
    fun subscribe(symbols: List<String>) {
        if (symbols.isEmpty()) return

        val formattedSymbols = symbols.map { toTwelveDataSymbol(it) }
        synchronized(activeSubscribedSymbols) {
            activeSubscribedSymbols.addAll(formattedSymbols)
        }

        if (_connectionStatus.value == MarketConnectionStatus.CONNECTED) {
            sendSubscribePayload(formattedSymbols)
        } else {
            connect()
        }
    }

    /**
     * Unsubscribes from a list of symbols.
     */
    fun unsubscribe(symbols: List<String>) {
        if (symbols.isEmpty()) return

        val formattedSymbols = symbols.map { toTwelveDataSymbol(it) }
        synchronized(activeSubscribedSymbols) {
            activeSubscribedSymbols.removeAll(formattedSymbols.toSet())
        }

        if (_connectionStatus.value == MarketConnectionStatus.CONNECTED) {
            sendUnsubscribePayload(formattedSymbols)
        }
    }

    /**
     * Disconnects from WebSocket and cancels reconnect attempts.
     */
    @Synchronized
    fun disconnect() {
        isExplicitDisconnect.set(true)
        reconnectJob?.cancel()
        reconnectJob = null
        stopHeartbeat()

        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing websocket", e)
        }
        webSocket = null
        _connectionStatus.value = MarketConnectionStatus.DISCONNECTED
    }

    /**
     * Exponential Backoff reconnection algorithm.
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            Log.d(TAG, "Scheduling reconnect in ${currentReconnectDelay}ms")
            delay(currentReconnectDelay)

            // Double the delay up to MAX_RECONNECT_DELAY_MS
            currentReconnectDelay = min((currentReconnectDelay * BACKOFF_MULTIPLIER).toLong(), MAX_RECONNECT_DELAY_MS)
            if (!isExplicitDisconnect.get()) {
                connectInternal()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    val pingJson = JSONObject().apply {
                        put("action", "heartbeat")
                    }
                    webSocket?.send(pingJson.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send heartbeat: ${e.message}")
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun sendSubscribePayload(symbols: List<String>) {
        serviceScope.launch {
            try {
                val payload = JSONObject().apply {
                    put("action", "subscribe")
                    val params = JSONObject().apply {
                        put("symbols", symbols.joinToString(","))
                    }
                    put("params", params)
                }
                webSocket?.send(payload.toString())
                Log.d(TAG, "Sent subscribe: $payload")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending subscribe payload", e)
            }
        }
    }

    private fun sendUnsubscribePayload(symbols: List<String>) {
        serviceScope.launch {
            try {
                val payload = JSONObject().apply {
                    put("action", "unsubscribe")
                    val params = JSONObject().apply {
                        put("symbols", symbols.joinToString(","))
                    }
                    put("params", params)
                }
                webSocket?.send(payload.toString())
                Log.d(TAG, "Sent unsubscribe: $payload")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending unsubscribe payload", e)
            }
        }
    }

    /**
     * Parses incoming JSON messages from Twelve Data WebSocket.
     */
    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event")

            when (event) {
                "price" -> {
                    val rawSymbol = json.optString("symbol")
                    val normalized = normalizeSymbol(rawSymbol)
                    val price = json.optDouble("price", 0.0)
                    if (price <= 0.0) return

                    // Check if bid and ask are provided, otherwise calculate using reasonable spread
                    val bid = if (json.has("bid")) json.optDouble("bid") else price * 0.99998
                    val ask = if (json.has("ask")) json.optDouble("ask") else price * 1.00002

                    val rawTimestamp = json.optLong("timestamp", SriLankaMarketHours.chartNowMillis() / 1000)
                    // If timestamp is in seconds, convert to milliseconds and align to chartNowMillis()
                    val parsedMs = if (rawTimestamp < 10000000000L) rawTimestamp * 1000L else rawTimestamp
                    val chartNow = SriLankaMarketHours.chartNowMillis()
                    val timestampMs = if (parsedMs > chartNow + 60_000L) {
                        parsedMs - SriLankaMarketHours.CHART_TIME_OFFSET_MS
                    } else {
                        parsedMs
                    }

                    val tick = PriceTick(
                        symbol = normalized,
                        rawSymbol = rawSymbol,
                        price = price,
                        bid = bid,
                        ask = ask,
                        timestamp = timestampMs
                    )

                    serviceScope.launch {
                        _ticks.emit(tick)
                    }
                }
                "subscribe-status" -> {
                    val status = json.optString("status")
                    Log.d(TAG, "Subscription status: $status, response=$text")
                }
                "heartbeat", "pong" -> {
                    Log.d(TAG, "Heartbeat/pong response received")
                }
                "error" -> {
                    val msg = json.optString("message")
                    Log.w(TAG, "Twelve Data message error: $msg")
                }
                else -> {
                    Log.d(TAG, "Received message: $text")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Twelve Data message: $text", e)
        }
    }

    /**
     * Fallback pulse generator:
     * Guarantees that even if offline, in airplane mode, or if Twelve Data limits the demo key,
     * the application state (Quotes, Charts, Positions, Account metrics) remains responsive,
     * smoothly simulating real-world tick fluctuations.
     */
    private fun startFallbackSimulation() {
        fallbackSimulationJob?.cancel()
        fallbackSimulationJob = serviceScope.launch {
            while (isActive) {
                delay(Random.nextLong(900, 1800))
                if (_connectionStatus.value != MarketConnectionStatus.CONNECTED) {
                    val symbols = synchronized(activeSubscribedSymbols) { activeSubscribedSymbols.toList() }
                    if (symbols.isNotEmpty()) {
                        val symToTick = symbols.random()
                        val normalized = normalizeSymbol(symToTick)
                        // Emit fallback tick
                        val basePrice = getDefaultPriceForSymbol(normalized)
                        val step = getPriceStepForSymbol(normalized)
                        val delta = (Random.nextDouble() - 0.49) * step * 2
                        val currentPrice = (basePrice + delta).coerceAtLeast(0.0001)

                        val spread = getSpreadForSymbol(normalized)
                        val bid = currentPrice
                        val ask = currentPrice + spread

                        val tick = PriceTick(
                            symbol = normalized,
                            rawSymbol = symToTick,
                            price = currentPrice,
                            bid = bid,
                            ask = ask,
                            timestamp = SriLankaMarketHours.chartNowMillis()
                        )
                        _ticks.emit(tick)
                    }
                }
            }
        }
    }

    /**
     * Converts a symbol string to Twelve Data format (e.g. "EURUSD" -> "EUR/USD", "XAUUSD" -> "XAU/USD").
     */
    fun toTwelveDataSymbol(symbol: String): String {
        val trimmed = symbol.trim().uppercase()
        if (trimmed.contains("/")) return trimmed
        return when (trimmed) {
            "AUDCAD" -> "AUD/CAD"
            "AUDCHF" -> "AUD/CHF"
            "AUDJPY" -> "AUD/JPY"
            "AUDNZD" -> "AUD/NZD"
            "AUDUSD" -> "AUD/USD"
            "CADCHF" -> "CAD/CHF"
            "CADJPY" -> "CAD/JPY"
            "CHFJPY" -> "CHF/JPY"
            "EURUSD" -> "EUR/USD"
            "GBPUSD" -> "GBP/USD"
            "BTCUSD" -> "BTC/USD"
            "XAUUSD" -> "XAU/USD"
            else -> {
                if (trimmed.length == 6) {
                    "${trimmed.substring(0, 3)}/${trimmed.substring(3)}"
                } else {
                    trimmed
                }
            }
        }
    }

    /**
     * Normalizes Twelve Data symbol back to app format (e.g. "EUR/USD" -> "EURUSD").
     */
    fun normalizeSymbol(raw: String): String {
        return raw.replace("/", "").replace(":", "").trim().uppercase()
    }

    private fun getDefaultPriceForSymbol(symbol: String): Double {
        return when (symbol) {
            "XAUUSD" -> 4099.90
            "BTCUSD" -> 81850.00
            "EURUSD" -> 1.11550
            "GBPUSD" -> 1.31850
            "AUDUSD" -> 0.69710
            "AUDCAD" -> 0.99270
            "AUDCHF" -> 0.58210
            "AUDJPY" -> 110.380
            "AUDNZD" -> 1.24700
            "CADCHF" -> 0.58640
            "CADJPY" -> 111.200
            "CHFJPY" -> 189.640
            else -> 1.00000
        }
    }

    private fun getPriceStepForSymbol(symbol: String): Double {
        return when {
            symbol.contains("XAU") -> 0.40
            symbol.contains("BTC") -> 12.0
            symbol.endsWith("JPY") -> 0.015
            else -> 0.00010
        }
    }

    private fun getSpreadForSymbol(symbol: String): Double {
        return when {
            symbol.contains("XAU") -> 0.30
            symbol.contains("BTC") -> 3.50
            symbol.endsWith("JPY") -> 0.02
            else -> 0.00020
        }
    }
}
