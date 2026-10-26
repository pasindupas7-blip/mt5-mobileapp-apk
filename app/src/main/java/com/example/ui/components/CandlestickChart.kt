package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Candle
import com.example.model.HistoryTrade
import com.example.model.SymbolQuote
import com.example.model.TradePosition
import com.example.ui.theme.ChartMA
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plain non-observable holder for layout/draw metrics so Canvas never writes to Compose MutableState
 * during the Draw phase.
 */
private class ChartGestureMetrics {
    var minPanBound: Float = -120f
    var maxPanBound: Float = 10000f
    var chartWidthPx: Float = 800f
    var candleOriginXPx: Float = 700f
    var totalCandleSlotPx: Float = 24f
    var hoveredCandleIndex: Int = -1
}

private data class ResolvedChartHistoryTrade(
    val trade: HistoryTrade,
    val openCandleIdx: Int,
    val closeCandleIdx: Int
)

private fun findCandleIndexForTimestamp(
    candles: List<Candle>,
    targetMs: Long,
    tfIntervalMs: Long
): Int {
    if (targetMs <= 0L || candles.isEmpty()) return -1
    val clampedMs = com.example.data.SriLankaMarketHours.clampToLastOpenMarketTimestamp(targetMs)
    val targetBucket = (clampedMs / tfIntervalMs) * tfIntervalMs

    // Binary search or direct scan for matching bucket
    var bestIdx = -1
    var bestDiff = Long.MAX_VALUE
    for (i in candles.indices) {
        val ts = candles[i].timestamp
        if (ts <= 0L) continue
        val bucket = (ts / tfIntervalMs) * tfIntervalMs
        if (bucket == targetBucket) {
            return i
        }
        val diff = abs(bucket - targetBucket)
        if (diff < bestDiff) {
            bestDiff = diff
            bestIdx = i
        }
    }
    return if (bestDiff <= tfIntervalMs * 4L) bestIdx else -1
}

@Composable
fun CandlestickChart(
    quote: SymbolQuote,
    candles: List<Candle>,
    positions: List<TradePosition> = emptyList(),
    historyTrades: List<HistoryTrade> = emptyList(),
    timeframeLabel: String = "M1",
    isDarkMode: Boolean = false,
    showMA: Boolean = false,
    maPeriod: Int = 20,
    showCrosshair: Boolean = false,
    fit14DaysMode: Boolean = false,
    onFit14DaysChange: ((Boolean) -> Unit)? = null,
    onSymbolClick: (() -> Unit)? = null,
    onMoreOptionsClick: (() -> Unit)? = null,
    onCanvasTouch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope()

    var panOffsetPx by remember(quote.symbol) { mutableFloatStateOf(0f) }
    var zoomScale by remember(quote.symbol) { mutableFloatStateOf(1.0f) }
    var isFit14Days by remember { mutableStateOf(fit14DaysMode) }
    var crosshairPos by remember { mutableStateOf<Offset?>(null) }

    val metrics = remember(quote.symbol) { ChartGestureMetrics() }
    var flingJob by remember { mutableStateOf<Job?>(null) }

    val activeSymbolPositions = remember(positions, quote.symbol) {
        positions.filter { it.symbol.equals(quote.symbol, ignoreCase = true) }
    }
    val activeSymbolHistory = remember(historyTrades, quote.symbol) {
        historyTrades.filter { !it.isWithdrawal && it.symbol.equals(quote.symbol, ignoreCase = true) }
    }

    val tfIntervalMs = remember(timeframeLabel) {
        val mins = when (timeframeLabel.uppercase(Locale.US)) {
            "M1" -> 1L
            "M5" -> 5L
            "M15" -> 15L
            "M30" -> 30L
            "H1" -> 60L
            "H4" -> 240L
            "D1" -> 1440L
            "W1" -> 10080L
            "MN" -> 43200L
            else -> 1L
        }
        mins * 60_000L
    }

    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val candleCountdownStr = remember(nowMillis, timeframeLabel) {
        val periodSec = when (timeframeLabel.uppercase(Locale.US)) {
            "M1" -> 60L
            "M5" -> 300L
            "M15" -> 900L
            "M30" -> 1800L
            "H1" -> 3600L
            "H4" -> 14400L
            "D1" -> 86400L
            "W1" -> 604800L
            "MN" -> 2592000L
            else -> 900L
        }
        val nowSec = nowMillis / 1000L
        val remSec = (periodSec - (nowSec % periodSec)).coerceAtLeast(0L)
        if (periodSec <= 3600L) {
            String.format(Locale.US, "%02d:%02d", remSec / 60L, remSec % 60L)
        } else {
            String.format(Locale.US, "%02d:%02d:%02d", remSec / 3600L, (remSec % 3600L) / 60L, remSec % 60L)
        }
    }

    LaunchedEffect(fit14DaysMode) {
        if (isFit14Days != fit14DaysMode) {
            isFit14Days = fit14DaysMode
            if (fit14DaysMode) {
                flingJob?.cancel()
                panOffsetPx = 0f
            }
        }
    }

    val axisDateFormat = remember {
        SimpleDateFormat("dd MMM HH:mm", Locale.US).apply {
            timeZone = com.example.data.SriLankaMarketHours.SRI_LANKA_TIME_ZONE
        }
    }

    // Exact MT5 Dark Mode & Normal (Light) Mode colors from reference screenshots
    val bgColor = if (isDarkMode) Color(0xFF000000) else Color(0xFFFFFFFF)
    val gridColor = if (isDarkMode) Color(0xFF1E222A) else Color(0xFFE5E7EB)
    val axisBorderColor = if (isDarkMode) Color(0xFF2C313A) else Color(0xFFD1D5DB)
    val axisTextColor = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val overlayTitleColor = if (isDarkMode) Color(0xFFE5E7EB) else Color(0xFF374151)
    val overlaySubColor = if (isDarkMode) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    val bullishColor = Color(0xFF26A69A)
    val bearishColor = Color(0xFFEF5350)
    val currentPriceTagColor = Color(0xFF26A69A)

    val displayCandles = remember(candles, isFit14Days) {
        if (!isFit14Days || candles.size <= 110) {
            candles
        } else {
            val historicalPart = candles.dropLast(1)
            val activeLast = candles.last()
            val targetBars = 95
            val chunkSize = (historicalPart.size / targetBars).coerceAtLeast(2)
            val aggregated = historicalPart.chunked(chunkSize).map { chunk ->
                val first = chunk.first()
                val last = chunk.last()
                Candle(
                    time = first.time,
                    timestamp = first.timestamp,
                    open = first.open,
                    high = chunk.maxOf { max(it.high, max(it.open, it.close)) },
                    low = chunk.minOf { min(it.low, min(it.open, it.close)) },
                    close = last.close
                )
            }
            aggregated + activeLast
        }
    }

    val resolvedHistoryMarkers = remember(displayCandles, activeSymbolHistory, tfIntervalMs) {
        if (displayCandles.isEmpty() || activeSymbolHistory.isEmpty()) {
            emptyList()
        } else {
            activeSymbolHistory.mapNotNull { trade ->
                val openMs = com.example.data.SriLankaMarketHours.parseMt5DateTimeMillis(trade.openTime)
                val closeMs = com.example.data.SriLankaMarketHours.parseMt5DateTimeMillis(trade.closeTime)
                val openIdx = findCandleIndexForTimestamp(displayCandles, openMs, tfIntervalMs)
                val closeIdx = findCandleIndexForTimestamp(displayCandles, closeMs, tfIntervalMs)
                if (openIdx >= 0 || closeIdx >= 0) {
                    ResolvedChartHistoryTrade(
                        trade = trade,
                        openCandleIdx = if (openIdx >= 0) openIdx else closeIdx,
                        closeCandleIdx = if (closeIdx >= 0) closeIdx else openIdx
                    )
                } else {
                    null
                }
            }
        }
    }

    // Smooth 60fps interpolation for the live forming candle & price line
    val latestCandle = displayCandles.lastOrNull()
    val baseOpen = latestCandle?.open ?: quote.bid
    val activeTimestamp = latestCandle?.timestamp ?: 0L

    val pointMultiplier = when (quote.digits) {
        5 -> 100000.0
        3 -> 1000.0
        2 -> 100.0
        else -> 10000.0
    }

    val (animClosePoints, animHighPoints, animLowPoints) = androidx.compose.runtime.key(quote.symbol, activeTimestamp) {
        val cPts by animateFloatAsState(
            targetValue = (((latestCandle?.close ?: baseOpen) - baseOpen) * pointMultiplier).toFloat(),
            animationSpec = tween(durationMillis = 220, easing = LinearEasing),
            visibilityThreshold = 0.001f,
            label = "animClosePoints"
        )
        val hPts by animateFloatAsState(
            targetValue = (((latestCandle?.high ?: baseOpen) - baseOpen) * pointMultiplier).toFloat(),
            animationSpec = tween(durationMillis = 220, easing = LinearEasing),
            visibilityThreshold = 0.001f,
            label = "animHighPoints"
        )
        val lPts by animateFloatAsState(
            targetValue = (((latestCandle?.low ?: baseOpen) - baseOpen) * pointMultiplier).toFloat(),
            animationSpec = tween(durationMillis = 220, easing = LinearEasing),
            visibilityThreshold = 0.001f,
            label = "animLowPoints"
        )
        Triple(cPts, hPts, lPts)
    }

    val animBidPoints = androidx.compose.runtime.key(quote.symbol, activeTimestamp) {
        val bPts by animateFloatAsState(
            targetValue = ((quote.bid - baseOpen) * pointMultiplier).toFloat(),
            animationSpec = tween(durationMillis = 220, easing = LinearEasing),
            visibilityThreshold = 0.001f,
            label = "animBidPoints"
        )
        bPts
    }

    val animOpen = baseOpen
    val animClose = baseOpen + (animClosePoints.toDouble() / pointMultiplier)
    val animHigh = max(baseOpen + (animHighPoints.toDouble() / pointMultiplier), max(animOpen, animClose))
    val animLow = min(baseOpen + (animLowPoints.toDouble() / pointMultiplier), min(animOpen, animClose))
    val animBid = baseOpen + (animBidPoints.toDouble() / pointMultiplier)

    val currentShowCrosshair by rememberUpdatedState(showCrosshair)
    val currentIsFit14Days by rememberUpdatedState(isFit14Days)
    val currentOnFit14DaysChange by rememberUpdatedState(onFit14DaysChange)
    val currentOnCanvasTouch by rememberUpdatedState(onCanvasTouch)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(quote.symbol) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnCanvasTouch?.invoke()
                    flingJob?.cancel()

                    if (currentShowCrosshair) {
                        crosshairPos = down.position
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                crosshairPos = change.position
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    } else {
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount == 0) break

                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (pressedCount >= 2 && abs(zoomChange - 1f) > 0.004f) {
                                if (currentIsFit14Days) {
                                    isFit14Days = false
                                    currentOnFit14DaysChange?.invoke(false)
                                }
                                val oldZoom = zoomScale
                                val newZoom = (oldZoom * zoomChange).coerceIn(0.25f, 3.0f)
                                val actualRatio = newZoom / oldZoom
                                zoomScale = newZoom

                                val centroid = event.calculateCentroid(useCurrent = false)
                                val focusX = if (centroid != Offset.Unspecified) centroid.x else metrics.chartWidthPx * 0.5f
                                val originX = metrics.candleOriginXPx
                                panOffsetPx = ((originX + panOffsetPx - focusX) * actualRatio - originX + focusX)
                                    .coerceIn(metrics.minPanBound, metrics.maxPanBound)

                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (abs(panChange.x) > 0.1f) {
                                if (currentIsFit14Days) {
                                    val defaultSlotPx = 9.dp.toPx()
                                    if (defaultSlotPx > 0f && metrics.totalCandleSlotPx > 0f) {
                                        zoomScale = (metrics.totalCandleSlotPx / defaultSlotPx).coerceIn(0.25f, 3.0f)
                                    }
                                    isFit14Days = false
                                    currentOnFit14DaysChange?.invoke(false)
                                }

                                panOffsetPx = (panOffsetPx + panChange.x)
                                    .coerceIn(metrics.minPanBound, metrics.maxPanBound)

                                val primaryChange = event.changes.firstOrNull { it.pressed }
                                if (primaryChange != null) {
                                    velocityTracker.addPosition(primaryChange.uptimeMillis, primaryChange.position)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        val velocityX = velocityTracker.calculateVelocity().x
                        if (abs(velocityX) > 140f && !currentIsFit14Days) {
                            flingJob = coroutineScope.launch {
                                var v = velocityX.coerceIn(-4500f, 4500f)
                                var lastTime = 0L
                                while (isActive && abs(v) > 25f) {
                                    withFrameNanos { nanos ->
                                        val dt = if (lastTime == 0L) 0.016f else ((nanos - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                                        lastTime = nanos
                                        val nextPan = (panOffsetPx + v * dt).coerceIn(metrics.minPanBound, metrics.maxPanBound)
                                        if (abs(nextPan - panOffsetPx) < 0.05f) {
                                            v = 0f
                                        } else {
                                            panOffsetPx = nextPan
                                            v *= exp(-4.8f * dt)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(showCrosshair) {
                detectTapGestures { offset ->
                    if (showCrosshair) {
                        crosshairPos = if (crosshairPos == null) offset else null
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (displayCandles.isEmpty()) return@Canvas

            val rightAxisWidth = 58.dp.toPx()
            val bottomAxisHeight = 24.dp.toPx()
            val chartWidth = size.width - rightAxisWidth
            val chartHeight = size.height - bottomAxisHeight

            if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

            val rightMargin = if (isFit14Days) 20.dp.toPx() else 18.dp.toPx()
            val candleOriginX = chartWidth - rightMargin

            val usableWidth = (chartWidth - rightMargin - 8.dp.toPx()).coerceAtLeast(60f)
            val totalCandleSlot = if (isFit14Days) {
                (usableWidth / displayCandles.size.coerceAtLeast(1)).coerceIn(2.5.dp.toPx(), 16.dp.toPx())
            } else {
                (8.5.dp.toPx() * zoomScale).coerceIn(2.5.dp.toPx(), 26.dp.toPx())
            }
            val candleWidth = (totalCandleSlot * 0.66f).coerceAtLeast(2.0.dp.toPx())

            val n = displayCandles.size
            val computedMinPan = if (isFit14Days) 0f else -(chartWidth * 0.35f)
            val computedMaxPan = if (isFit14Days) {
                0f
            } else {
                max(0f, ((n - 1) * totalCandleSlot) - (candleOriginX - 24.dp.toPx()))
            }

            metrics.minPanBound = computedMinPan
            metrics.maxPanBound = computedMaxPan
            metrics.chartWidthPx = chartWidth
            metrics.candleOriginXPx = candleOriginX
            metrics.totalCandleSlotPx = totalCandleSlot

            val clampedPan = if (isFit14Days) 0f else panOffsetPx.coerceIn(computedMinPan, computedMaxPan)

            val floatLastIdx = (n - 1).toFloat()
            val idxAtLeftEdge = floatLastIdx - ((candleOriginX + clampedPan + totalCandleSlot * 2f) / totalCandleSlot)
            val idxAtRightEdge = floatLastIdx - ((candleOriginX + clampedPan - (chartWidth + totalCandleSlot * 2f)) / totalCandleSlot)

            val leftIndex = if (isFit14Days) 0 else floor(idxAtLeftEdge).toInt().coerceIn(0, n - 1)
            val rightIndex = if (isFit14Days) n - 1 else ceil(idxAtRightEdge).toInt().coerceIn(leftIndex, n - 1)

            var minPrice = Double.MAX_VALUE
            var maxPrice = -Double.MAX_VALUE

            for (globalIdx in leftIndex..rightIndex) {
                val x = candleOriginX - ((n - 1 - globalIdx) * totalCandleSlot) + clampedPan
                if (x < -totalCandleSlot * 1.5f || x > chartWidth + totalCandleSlot * 1.5f) continue

                val c = displayCandles[globalIdx]
                val isLast = globalIdx == n - 1
                val cOpen = if (isLast) animOpen else c.open
                val cClose = if (isLast) animClose else c.close
                val cHigh = max(if (isLast) animHigh else c.high, max(cOpen, cClose))
                val cLow = min(if (isLast) animLow else c.low, min(cOpen, cClose))

                if (cLow > 0.0 && cHigh > 0.0) {
                    minPrice = min(minPrice, cLow)
                    maxPrice = max(maxPrice, cHigh)
                }
            }

            if (minPrice == Double.MAX_VALUE || maxPrice <= 0.0) {
                for (globalIdx in leftIndex..rightIndex) {
                    val c = displayCandles[globalIdx]
                    minPrice = min(minPrice, c.low)
                    maxPrice = max(maxPrice, c.high)
                }
            }

            val liveCandleX = candleOriginX + clampedPan
            if (liveCandleX >= 0f && liveCandleX <= chartWidth + totalCandleSlot * 2f) {
                val candleSpan = (maxPrice - minPrice).coerceAtLeast(0.0001)
                if (abs(animBid - maxPrice) <= candleSpan * 3.0) {
                    minPrice = min(minPrice, animBid)
                    maxPrice = max(maxPrice, animBid)
                }

                activeSymbolPositions.forEach { pos ->
                    if (abs(pos.openPrice - maxPrice) <= candleSpan * 3.0) {
                        minPrice = min(minPrice, pos.openPrice)
                        maxPrice = max(maxPrice, pos.openPrice)
                    }
                    pos.sl?.let { sl ->
                        if (sl > 0.0 && abs(sl - pos.openPrice) <= candleSpan * 3.0) {
                            minPrice = min(minPrice, sl)
                            maxPrice = max(maxPrice, sl)
                        }
                    }
                    pos.tp?.let { tp ->
                        if (tp > 0.0 && abs(tp - pos.openPrice) <= candleSpan * 3.0) {
                            minPrice = min(minPrice, tp)
                            maxPrice = max(maxPrice, tp)
                        }
                    }
                }
            }

            // Include visible historical trades in minPrice/maxPrice so trade arrows are always within the viewport
            if (resolvedHistoryMarkers.isNotEmpty()) {
                val visibleSpan = (maxPrice - minPrice).coerceAtLeast(0.0001)
                resolvedHistoryMarkers.forEach { marker ->
                    val minIdx = min(marker.openCandleIdx, marker.closeCandleIdx)
                    val maxIdx = max(marker.openCandleIdx, marker.closeCandleIdx)
                    if (maxIdx >= leftIndex && minIdx <= rightIndex) {
                        val oPrice = marker.trade.openPrice
                        val cPrice = marker.trade.closePrice
                        if (oPrice > 0.0 && abs(oPrice - maxPrice) <= visibleSpan * 4.0) {
                            minPrice = min(minPrice, oPrice)
                            maxPrice = max(maxPrice, oPrice)
                        }
                        if (cPrice > 0.0 && abs(cPrice - maxPrice) <= visibleSpan * 4.0) {
                            minPrice = min(minPrice, cPrice)
                            maxPrice = max(maxPrice, cPrice)
                        }
                    }
                }
            }

            val rawRange = maxPrice - minPrice
            val effectiveRange = if (rawRange <= 0.00001) {
                val defaultStep = when (quote.digits) {
                    5 -> 0.0010
                    3 -> 0.10
                    2 -> 1.00
                    else -> 0.01
                }
                defaultStep * 10
            } else {
                rawRange
            }
            val padding = effectiveRange * 0.12
            val priceRange = effectiveRange + (padding * 2)
            val baseMin = (minPrice - padding).coerceAtLeast(0.0)

            fun priceToY(price: Double): Float {
                return (chartHeight - ((price - baseMin) / priceRange * chartHeight)).toFloat()
            }

            fun yToPrice(y: Float): Double {
                val ratio = (chartHeight - y) / chartHeight
                return baseMin + (ratio * priceRange)
            }

            val gridDash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)

            // Draw 12 horizontal dashed price grid lines and right-axis price labels
            val gridStepCount = 12
            for (i in 0..gridStepCount) {
                val y = (chartHeight / gridStepCount) * i
                if (i in 1 until gridStepCount) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = gridDash
                    )
                }

                // Skip very bottom label so ••• icon has clean space just like in the MT5 screenshot
                if (i < gridStepCount) {
                    val priceAtGrid = yToPrice(y)
                    val priceStr = String.format(Locale.US, "%.${quote.digits}f", priceAtGrid)
                    val labelY = (y - 6.dp.toPx()).coerceAtLeast(2.dp.toPx())
                    drawText(
                        textMeasurer = textMeasurer,
                        text = priceStr,
                        topLeft = Offset(chartWidth + 5.dp.toPx(), labelY),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = axisTextColor,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }
            }

            // Draw 4 vertical dashed time grid lines and bottom-axis timestamps ("23 Oct 22:58")
            val verticalFractions = floatArrayOf(0.11f, 0.37f, 0.63f, 0.89f)
            for (frac in verticalFractions) {
                val x = chartWidth * frac
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, chartHeight),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = gridDash
                )

                val floatIdx = floatLastIdx - ((candleOriginX + clampedPan - x) / totalCandleSlot)
                val candleIdx = floatIdx.toInt().coerceIn(0, n - 1)
                val c = displayCandles[candleIdx]
                val timeLabel = if (c.timestamp > 0L) {
                    axisDateFormat.format(Date(c.timestamp))
                } else {
                    c.time
                }
                val labelLeft = (x - 28.dp.toPx()).coerceIn(2.dp.toPx(), chartWidth - 62.dp.toPx())
                drawText(
                    textMeasurer = textMeasurer,
                    text = timeLabel,
                    topLeft = Offset(labelLeft, chartHeight + 5.dp.toPx()),
                    style = TextStyle(
                        fontSize = 9.5.sp,
                        color = axisTextColor,
                        fontWeight = FontWeight.Normal
                    )
                )
            }

            // Draw Candlesticks and optional Moving Average inside plot clipRect
            metrics.hoveredCandleIndex = -1

            clipRect(left = 0f, top = 0f, right = chartWidth, bottom = chartHeight) {
                for (globalIdx in leftIndex..rightIndex) {
                    val candle = displayCandles[globalIdx]
                    val x = candleOriginX - ((n - 1 - globalIdx) * totalCandleSlot) + clampedPan

                    if (x < -candleWidth || x > chartWidth + candleWidth) continue

                    val isLastCandle = globalIdx == n - 1
                    val cOpen = if (isLastCandle) animOpen else candle.open
                    val cClose = if (isLastCandle) animClose else candle.close
                    val cHigh = max(if (isLastCandle) animHigh else candle.high, max(cOpen, cClose))
                    val cLow = min(if (isLastCandle) animLow else candle.low, min(cOpen, cClose))

                    val openY = priceToY(cOpen)
                    val closeY = priceToY(cClose)
                    val highY = priceToY(cHigh)
                    val lowY = priceToY(cLow)

                    val isBullish = cClose >= cOpen
                    val candleColor = if (isBullish) bullishColor else bearishColor

                    // Wick
                    drawLine(
                        color = candleColor,
                        start = Offset(x, highY),
                        end = Offset(x, lowY),
                        strokeWidth = 1.2.dp.toPx()
                    )

                    // Body
                    val bodyTop = min(openY, closeY)
                    val bodyBottom = max(openY, closeY)
                    val bodyHeight = max(2.0f, bodyBottom - bodyTop)

                    drawRect(
                        color = candleColor,
                        topLeft = Offset(x - candleWidth / 2f, bodyTop),
                        size = Size(candleWidth, bodyHeight)
                    )

                    crosshairPos?.let { pos ->
                        if (pos.x >= x - totalCandleSlot / 2f && pos.x <= x + totalCandleSlot / 2f) {
                            metrics.hoveredCandleIndex = globalIdx
                        }
                    }
                }

                // Moving Average Overlay (only when toggled via 'f' indicator button)
                val effectiveMaPeriod = min(maPeriod, max(3, n / 3))
                if (showMA && n >= effectiveMaPeriod) {
                    val maPath = Path()
                    var started = false

                    for (i in leftIndex..rightIndex) {
                        if (i < effectiveMaPeriod - 1) continue

                        var sum = 0.0
                        for (k in 0 until effectiveMaPeriod) {
                            val idx = i - k
                            val cl = if (idx == n - 1) animClose else displayCandles[idx].close
                            sum += cl
                        }
                        val maVal = sum / effectiveMaPeriod
                        val maY = priceToY(maVal)
                        val x = candleOriginX - ((n - 1 - i) * totalCandleSlot) + clampedPan

                        if (!started) {
                            maPath.moveTo(x, maY)
                            started = true
                        } else {
                            maPath.lineTo(x, maY)
                        }
                    }

                    drawPath(
                        path = maPath,
                        color = ChartMA,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }

                // =========================================================================
                // MT5 Trade History Markers on Chart (matching Screenshot 863219104):
                // - Red Down-Pointing Block Arrow for SELL deals (opening a SELL or closing a BUY)
                // - Blue Up-Pointing Block Arrow for BUY deals (opening a BUY or closing a SELL)
                // - Dashed connector line between (openX, openY) and (closeX, closeY)
                // =========================================================================
                fun drawMt5DealArrow(tipX: Float, tipY: Float, isUpArrow: Boolean) {
                    val headHalfW = 10.5.dp.toPx()
                    val headH = 11.dp.toPx()
                    val shaftHalfW = 4.5.dp.toPx()
                    val totalH = 19.dp.toPx()

                    val arrowPath = Path().apply {
                        if (isUpArrow) {
                            // Blue Up-Pointing Arrow: tip at (tipX, tipY), body extends downward
                            moveTo(tipX, tipY)
                            lineTo(tipX - headHalfW, tipY + headH)
                            lineTo(tipX - shaftHalfW, tipY + headH)
                            lineTo(tipX - shaftHalfW, tipY + totalH)
                            lineTo(tipX + shaftHalfW, tipY + totalH)
                            lineTo(tipX + shaftHalfW, tipY + headH)
                            lineTo(tipX + headHalfW, tipY + headH)
                            close()
                        } else {
                            // Red Down-Pointing Arrow: tip at (tipX, tipY), body extends upward
                            moveTo(tipX, tipY)
                            lineTo(tipX - headHalfW, tipY - headH)
                            lineTo(tipX - shaftHalfW, tipY - headH)
                            lineTo(tipX - shaftHalfW, tipY - totalH)
                            lineTo(tipX + shaftHalfW, tipY - totalH)
                            lineTo(tipX + shaftHalfW, tipY - headH)
                            lineTo(tipX + headHalfW, tipY - headH)
                            close()
                        }
                    }

                    val fillColor = if (isUpArrow) Color(0xFF2979FF) else Color(0xFFEA4C46)
                    val borderColor = if (isUpArrow) Color(0xFF1565C0) else Color(0xFFC62828)

                    // White outer halo so the arrow stands out cleanly over red/green candle bodies
                    drawPath(
                        path = arrowPath,
                        color = Color.White,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                    // Solid vibrant fill
                    drawPath(
                        path = arrowPath,
                        color = fillColor
                    )
                    // Crisp inner outline
                    drawPath(
                        path = arrowPath,
                        color = borderColor,
                        style = Stroke(width = 0.7.dp.toPx())
                    )
                }

                val tradeConnectorDash = PathEffect.dashPathEffect(
                    floatArrayOf(4.5.dp.toPx(), 3.5.dp.toPx()),
                    0f
                )

                // 1. Draw closed HistoryTrade arrows and dashed connector lines
                resolvedHistoryMarkers.forEach { marker ->
                    val openIdx = marker.openCandleIdx
                    val closeIdx = marker.closeCandleIdx
                    val openX = candleOriginX - ((n - 1 - openIdx) * totalCandleSlot) + clampedPan
                    val closeX = candleOriginX - ((n - 1 - closeIdx) * totalCandleSlot) + clampedPan

                    val minX = min(openX, closeX)
                    val maxX = max(openX, closeX)
                    if (maxX < -40.dp.toPx() || minX > chartWidth + 40.dp.toPx()) return@forEach

                    val openY = priceToY(marker.trade.openPrice)
                    val closeY = priceToY(marker.trade.closePrice)

                    // Diagonal dashed connector line between entry and exit
                    drawLine(
                        color = Color(0xFF7A7370),
                        start = Offset(openX, openY),
                        end = Offset(closeX, closeY),
                        strokeWidth = 1.1.dp.toPx(),
                        pathEffect = tradeConnectorDash
                    )

                    // Entry deal arrow: BUY trade opens with Blue Up Arrow; SELL trade opens with Red Down Arrow
                    drawMt5DealArrow(
                        tipX = openX,
                        tipY = openY,
                        isUpArrow = marker.trade.isBuy
                    )

                    // Exit deal arrow: closing a BUY trade is a SELL deal (Red Down Arrow);
                    // closing a SELL trade is a BUY deal (Blue Up Arrow)
                    drawMt5DealArrow(
                        tipX = closeX,
                        tipY = closeY,
                        isUpArrow = !marker.trade.isBuy
                    )
                }
            }

            // Right price axis separator & bottom time axis separator
            drawLine(
                color = axisBorderColor,
                start = Offset(chartWidth, 0f),
                end = Offset(chartWidth, chartHeight),
                strokeWidth = 0.8.dp.toPx()
            )
            drawLine(
                color = axisBorderColor,
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = 0.8.dp.toPx()
            )

            // Draw Open Trade Lines (Entry Price, Stop Loss, Take Profit) matching Screenshot 3
            activeSymbolPositions.forEachIndexed { index, pos ->
                pos.sl?.let { slPrice ->
                    if (slPrice > 0.0) {
                        val slY = priceToY(slPrice)
                        if (slY in 0f..chartHeight) {
                            val slColor = Color(0xFFD32F2F)
                            drawLine(
                                color = slColor,
                                start = Offset(0f, slY),
                                end = Offset(chartWidth, slY),
                                strokeWidth = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)
                            )
                            val slStr = String.format(Locale.US, "%.${quote.digits}f", slPrice)
                            drawRect(
                                color = slColor,
                                topLeft = Offset(chartWidth, slY - 8.dp.toPx()),
                                size = Size(rightAxisWidth, 16.dp.toPx())
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = slStr,
                                topLeft = Offset(chartWidth + 4.dp.toPx(), slY - 6.dp.toPx()),
                                style = TextStyle(
                                    fontSize = 9.5.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                pos.tp?.let { tpPrice ->
                    if (tpPrice > 0.0) {
                        val tpY = priceToY(tpPrice)
                        if (tpY in 0f..chartHeight) {
                            val tpColor = Color(0xFF26A69A)
                            drawLine(
                                color = tpColor,
                                start = Offset(0f, tpY),
                                end = Offset(chartWidth, tpY),
                                strokeWidth = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)
                            )
                            val tpStr = String.format(Locale.US, "%.${quote.digits}f", tpPrice)
                            drawRect(
                                color = tpColor,
                                topLeft = Offset(chartWidth, tpY - 8.dp.toPx()),
                                size = Size(rightAxisWidth, 16.dp.toPx())
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = tpStr,
                                topLeft = Offset(chartWidth + 4.dp.toPx(), tpY - 6.dp.toPx()),
                                style = TextStyle(
                                    fontSize = 9.5.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Open Position Dashed Line & Two-Color Inline Label ("SELL 0.01, +0.43 USD") matching Screenshot 3
                val rawOpenY = priceToY(pos.openPrice)
                val openY = rawOpenY.coerceIn(16.dp.toPx(), chartHeight - 10.dp.toPx())
                val tradeDashLineColor = Color(0xFF5F6368)
                val sideTextColor = if (pos.isBuy) Color(0xFF0A60C2) else Color(0xFFC62828)
                val pnlTextColor = if (pos.profit >= 0.0) Color(0xFF0A60C2) else Color(0xFFC62828)

                drawLine(
                    color = tradeDashLineColor,
                    start = Offset(0f, openY),
                    end = Offset(chartWidth, openY),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)
                )

                val typeStr = if (pos.isBuy) "BUY" else "SELL"
                val lotsStr = String.format(Locale.US, "%.2f", pos.lots)
                val pnlSign = if (pos.profit >= 0.0) "+" else ""
                val pnlStr = "$pnlSign${String.format(Locale.US, "%.2f", pos.profit)} USD"
                val stackOffsetY = (index % 3) * 14.dp.toPx()
                val labelY = (openY - 15.dp.toPx() - stackOffsetY).coerceIn(2.dp.toPx(), chartHeight - 16.dp.toPx())

                val annotatedTradeLabel = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = sideTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("$typeStr $lotsStr, ")
                    }
                    withStyle(
                        SpanStyle(
                            color = pnlTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(pnlStr)
                    }
                }

                drawText(
                    textMeasurer = textMeasurer,
                    text = annotatedTradeLabel,
                    topLeft = Offset(5.dp.toPx(), labelY)
                )
            }

            // Current Ask Line (Red #EF5350) & Bid Line (Teal #26A69A) with Right-Axis Badges (matching Screenshot 863219104)
            val spreadDiff = (quote.ask - quote.bid).coerceAtLeast(0.0)
            val animAsk = animBid + spreadDiff
            val askY = priceToY(animAsk)
            val bidY = priceToY(animBid)
            val askTagHeight = 16.dp.toPx()
            val isLiveMarketOrOpenTrade = activeSymbolPositions.isNotEmpty() ||
                com.example.data.SriLankaMarketHours.isMarketOpen(nowMillis)

            if (askY in 0f..chartHeight) {
                drawLine(
                    color = bearishColor,
                    start = Offset(0f, askY),
                    end = Offset(chartWidth, askY),
                    strokeWidth = 1.dp.toPx()
                )
            }
            if (bidY in 0f..chartHeight) {
                drawLine(
                    color = currentPriceTagColor,
                    start = Offset(0f, bidY),
                    end = Offset(chartWidth, bidY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (bidY in 0f..chartHeight) {
                if (isLiveMarketOrOpenTrade) {
                    val twoLineTagHeight = 28.dp.toPx()
                    val bidTagTop = (bidY - twoLineTagHeight / 2f).coerceIn(askTagHeight, chartHeight - twoLineTagHeight)
                    val rawAskTagTop = (askY - askTagHeight / 2f).coerceIn(0f, chartHeight - twoLineTagHeight - askTagHeight)
                    val askTagTop = min(rawAskTagTop, bidTagTop - askTagHeight)

                    // Red Ask Badge on Right Axis right above the 2-line Green Bid Badge
                    drawRect(
                        color = bearishColor,
                        topLeft = Offset(chartWidth, askTagTop),
                        size = Size(rightAxisWidth, askTagHeight)
                    )
                    val askStr = String.format(Locale.US, "%.${quote.digits}f", animAsk)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = askStr,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), askTagTop + 1.dp.toPx()),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    // 2-Line Teal Bid + Candle Countdown Badge on Right Axis
                    drawRect(
                        color = currentPriceTagColor,
                        topLeft = Offset(chartWidth, bidTagTop),
                        size = Size(rightAxisWidth, twoLineTagHeight)
                    )
                    val bidStr = String.format(Locale.US, "%.${quote.digits}f", animBid)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = bidStr,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), bidTagTop + 1.5.dp.toPx()),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = candleCountdownStr,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), bidTagTop + 14.dp.toPx()),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    )
                } else {
                    val bidTagTop = (bidY - askTagHeight / 2f).coerceIn(askTagHeight, chartHeight - askTagHeight)
                    val rawAskTagTop = (askY - askTagHeight / 2f).coerceIn(0f, chartHeight - askTagHeight * 2f)
                    val askTagTop = min(rawAskTagTop, bidTagTop - askTagHeight)

                    // Red Ask Badge on Right Axis
                    drawRect(
                        color = bearishColor,
                        topLeft = Offset(chartWidth, askTagTop),
                        size = Size(rightAxisWidth, askTagHeight)
                    )
                    val askStr = String.format(Locale.US, "%.${quote.digits}f", animAsk)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = askStr,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), askTagTop + 1.dp.toPx()),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    // Teal Bid Badge on Right Axis
                    drawRect(
                        color = currentPriceTagColor,
                        topLeft = Offset(chartWidth, bidTagTop),
                        size = Size(rightAxisWidth, askTagHeight)
                    )
                    val bidStr = String.format(Locale.US, "%.${quote.digits}f", animBid)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = bidStr,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), bidTagTop + 1.dp.toPx()),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // Draw Crosshair if active
            crosshairPos?.let { pos ->
                if (pos.x in 0f..chartWidth && pos.y in 0f..chartHeight) {
                    drawLine(
                        color = axisTextColor,
                        start = Offset(pos.x, 0f),
                        end = Offset(pos.x, chartHeight),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    drawLine(
                        color = axisTextColor,
                        start = Offset(0f, pos.y),
                        end = Offset(chartWidth, pos.y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    val inspectedPrice = yToPrice(pos.y)
                    val inspectedPriceStr = String.format(Locale.US, "%.${quote.digits}f", inspectedPrice)
                    drawRect(
                        color = if (isDarkMode) Color(0xFF374151) else Color(0xFF4B5563),
                        topLeft = Offset(chartWidth, pos.y - 8.dp.toPx()),
                        size = Size(rightAxisWidth, 16.dp.toPx())
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = inspectedPriceStr,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), pos.y - 6.dp.toPx()),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        // Top-Left Clean 3-Line Overlay inside Chart Canvas matching Screenshot 2:
        // Line 1: "EURUSD ▾ " (Blue) + "M15" (Black)
        // Line 2: "Euro vs US Dollar" (Black)
        // Line 3: "Market closed" (Black)
        val hoveredCandle = if (showCrosshair && metrics.hoveredCandleIndex in displayCandles.indices) {
            displayCandles[metrics.hoveredCandleIndex]
        } else null

        val primaryTextCol = if (isDarkMode) Color(0xFFE5E7EB) else Color(0xFF1C1C1E)

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 4.dp)
                .clickable { onSymbolClick?.invoke() }
                .testTag("chart_symbol_overlay")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${quote.symbol} ▾ ",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0),
                    lineHeight = 16.sp
                )
                Text(
                    text = timeframeLabel,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextCol,
                    lineHeight = 16.sp
                )
            }
            Text(
                text = quote.description,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = primaryTextCol,
                lineHeight = 15.sp
            )
            if (hoveredCandle != null) {
                Text(
                    text = "O: %.${quote.digits}f  H: %.${quote.digits}f  L: %.${quote.digits}f  C: %.${quote.digits}f".format(
                        Locale.US,
                        hoveredCandle.open,
                        hoveredCandle.high,
                        hoveredCandle.low,
                        hoveredCandle.close
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextCol,
                    lineHeight = 15.sp
                )
            }
        }

        // Bottom-Right "•••" Icon at the bottom of the right price axis (matching Screenshot 2)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 26.dp)
                .clickable { onMoreOptionsClick?.invoke() }
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("chart_more_dots_button")
        ) {
            Text(
                text = "•••",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = primaryTextCol,
                letterSpacing = 1.sp
            )
        }
    }
}
