package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalView
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
import com.example.ui.theme.MT5RobotoCondensedFontFamily
import com.example.viewmodel.ChartViewportState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class HistoryDragHandle {
    OPEN,
    CLOSE,
    BOTH
}

private data class EditableHistoryTradeState(
    val originalTrade: HistoryTrade,
    val isBuy: Boolean,
    val openCandleIdx: Int,
    val closeCandleIdx: Int,
    val openPrice: Double,
    val closePrice: Double,
    val activeHandle: HistoryDragHandle = HistoryDragHandle.OPEN
)

private data class RenderedHistoryMarker(
    val trade: HistoryTrade,
    val isBuy: Boolean,
    val openCandleIdx: Int,
    val closeCandleIdx: Int,
    val openPrice: Double,
    val closePrice: Double,
    val openTipX: Float,
    val openTipY: Float,
    val openBodyCenterY: Float,
    val closeTipX: Float,
    val closeTipY: Float,
    val closeBodyCenterY: Float
)

private data class HitHistoryMarkerResult(
    val marker: RenderedHistoryMarker,
    val handle: HistoryDragHandle,
    val distancePx: Float
)

/**
 * Plain non-observable holder for layout/draw metrics so Canvas never writes to Compose MutableState
 * during the Draw phase.
 */
private class ChartGestureMetrics {
    var minPanBound: Float = -120f
    var maxPanBound: Float = 10000f
    var chartWidthPx: Float = 800f
    var chartHeightPx: Float = 1000f
    var candleOriginXPx: Float = 700f
    var totalCandleSlotPx: Float = 24f
    var clampedPanPx: Float = 0f
    var topGridMarginPx: Float = 20f
    var gridSpanPx: Float = 900f
    var topGridPriceVal: Double = 0.0
    var totalGridPriceSpanVal: Double = 1.0
    var hoveredCandleIndex: Int = -1
    val renderedHistoryMarkers = mutableListOf<RenderedHistoryMarker>()

    fun yToPrice(y: Float): Double {
        if (gridSpanPx <= 1f) return topGridPriceVal
        val ratio = (y - topGridMarginPx) / gridSpanPx
        return topGridPriceVal - ratio * totalGridPriceSpanVal
    }

    fun priceToY(price: Double): Float {
        if (totalGridPriceSpanVal <= 0.0) return topGridMarginPx
        val ratio = (topGridPriceVal - price) / totalGridPriceSpanVal
        return (topGridMarginPx + ratio * gridSpanPx).toFloat()
    }
}

private fun distancePointToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lenSq = dx * dx + dy * dy
    if (lenSq <= 0.0001f) {
        return hypot(p.x - a.x, p.y - a.y)
    }
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq).coerceIn(0f, 1f)
    val projX = a.x + t * dx
    val projY = a.y + t * dy
    return hypot(p.x - projX, p.y - projY)
}

private fun findHitHistoryMarker(
    pos: Offset,
    markers: List<RenderedHistoryMarker>,
    arrowRadiusPx: Float,
    segmentRadiusPx: Float,
    prioritizeTradeId: Long? = null
): HitHistoryMarkerResult? {
    if (markers.isEmpty()) return null
    val ordered = if (prioritizeTradeId != null) {
        markers.sortedByDescending { it.trade.id == prioritizeTradeId }
    } else {
        markers
    }

    var bestArrowHit: HitHistoryMarkerResult? = null
    var bestSegmentHit: HitHistoryMarkerResult? = null

    for (m in ordered) {
        val dOpenTip = hypot(pos.x - m.openTipX, pos.y - m.openTipY)
        val dOpenBody = hypot(pos.x - m.openTipX, pos.y - m.openBodyCenterY)
        val dOpen = min(dOpenTip, dOpenBody)

        val dCloseTip = hypot(pos.x - m.closeTipX, pos.y - m.closeTipY)
        val dCloseBody = hypot(pos.x - m.closeTipX, pos.y - m.closeBodyCenterY)
        val dClose = min(dCloseTip, dCloseBody)

        val minArrowDist = min(dOpen, dClose)
        if (minArrowDist <= arrowRadiusPx) {
            val handle = if (dOpen <= dClose) HistoryDragHandle.OPEN else HistoryDragHandle.CLOSE
            val effectiveDist = if (prioritizeTradeId != null && m.trade.id == prioritizeTradeId) {
                minArrowDist * 0.5f
            } else {
                minArrowDist
            }
            if (bestArrowHit == null || effectiveDist < bestArrowHit.distancePx) {
                bestArrowHit = HitHistoryMarkerResult(m, handle, effectiveDist)
            }
        } else {
            val dSeg = distancePointToSegment(
                pos,
                Offset(m.openTipX, m.openTipY),
                Offset(m.closeTipX, m.closeTipY)
            )
            if (dSeg <= segmentRadiusPx) {
                val effectiveDist = if (prioritizeTradeId != null && m.trade.id == prioritizeTradeId) {
                    dSeg * 0.5f
                } else {
                    dSeg
                }
                if (bestSegmentHit == null || effectiveDist < bestSegmentHit.distancePx) {
                    bestSegmentHit = HitHistoryMarkerResult(m, HistoryDragHandle.BOTH, effectiveDist)
                }
            }
        }
    }

    return bestArrowHit ?: bestSegmentHit
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
    timeframeLabel: String = "M5",
    isDarkMode: Boolean = false,
    showMA: Boolean = false,
    maPeriod: Int = 20,
    showCrosshair: Boolean = false,
    fit14DaysMode: Boolean = false,
    onFit14DaysChange: ((Boolean) -> Unit)? = null,
    enableHistoryTradeMove: Boolean = true,
    initialViewportState: ChartViewportState = ChartViewportState(),
    onViewportStateChange: ((ChartViewportState) -> Unit)? = null,
    onSymbolClick: (() -> Unit)? = null,
    onMoreOptionsClick: (() -> Unit)? = null,
    onCanvasTouch: (() -> Unit)? = null,
    onCalculateHistoryProfit: ((isBuy: Boolean, lots: Double, openPrice: Double, closePrice: Double) -> Double)? = null,
    onSaveHistoryTradeEdit: ((
        trade: HistoryTrade,
        isBuy: Boolean,
        openPrice: Double,
        closePrice: Double,
        openTime: String,
        closeTime: String
    ) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope()
    val localView = LocalView.current

    var panOffsetPx by remember(quote.symbol, timeframeLabel) { mutableFloatStateOf(initialViewportState.panOffsetPx) }
    var zoomScale by remember(quote.symbol, timeframeLabel) { mutableFloatStateOf(initialViewportState.zoomScale) }
    var priceAxisZoom by remember(quote.symbol, timeframeLabel) { mutableFloatStateOf(initialViewportState.priceAxisZoom) }
    var verticalPanPriceOffset by remember(quote.symbol, timeframeLabel) { mutableStateOf(initialViewportState.verticalPanPriceOffset) }
    var isFit14Days by remember { mutableStateOf(fit14DaysMode) }
    var crosshairPos by remember { mutableStateOf<Offset?>(null) }
    var editingTradeState by remember(quote.symbol, timeframeLabel) {
        mutableStateOf<EditableHistoryTradeState?>(null)
    }

    LaunchedEffect(enableHistoryTradeMove) {
        if (!enableHistoryTradeMove) {
            editingTradeState = null
        }
    }

    LaunchedEffect(quote.symbol, timeframeLabel, panOffsetPx, zoomScale, priceAxisZoom, verticalPanPriceOffset) {
        onViewportStateChange?.invoke(
            ChartViewportState(
                panOffsetPx = panOffsetPx,
                zoomScale = zoomScale,
                priceAxisZoom = priceAxisZoom,
                verticalPanPriceOffset = verticalPanPriceOffset
            )
        )
    }

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

    var nowMillis by remember { mutableStateOf(com.example.data.SriLankaMarketHours.chartNowMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            nowMillis = com.example.data.SriLankaMarketHours.chartNowMillis()
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

    val axisDateFormat = remember(timeframeLabel) {
        val pattern = if (timeframeLabel.uppercase(Locale.US) in listOf("D1", "W1", "MN")) {
            "d MMM yyyy"
        } else {
            "d MMM HH:mm"
        }
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = com.example.data.SriLankaMarketHours.SRI_LANKA_TIME_ZONE
        }
    }

    // Exact MT5 Dark Mode & Normal (Light) Mode colors from reference screenshots
    val bgColor = if (isDarkMode) Color(0xFF000000) else Color(0xFFFFFFFF)
    val gridColor = if (isDarkMode) Color(0xFF1F242D) else Color(0xFFEBEBEB)
    val axisBorderColor = if (isDarkMode) Color(0xFF2C313A) else Color(0xFFD5D7DB)
    val axisTextColor = if (isDarkMode) Color(0xFFE5E7EB) else Color(0xFF1A1A1A)

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
    val currentEnableHistoryTradeMove by rememberUpdatedState(enableHistoryTradeMove)
    val currentOnCanvasTouch by rememberUpdatedState(onCanvasTouch)
    val currentDisplayCandles by rememberUpdatedState(displayCandles)
    val currentEditingTradeState by rememberUpdatedState(editingTradeState)
    val currentAnimOpen by rememberUpdatedState(animOpen)
    val currentAnimClose by rememberUpdatedState(animClose)
    val currentAnimHigh by rememberUpdatedState(animHigh)
    val currentAnimLow by rememberUpdatedState(animLow)

    fun getCandleLowHigh(idx: Int, candlesList: List<Candle>): Pair<Double, Double> {
        if (candlesList.isEmpty()) return Pair(quote.bid, quote.ask)
        val safeIdx = idx.coerceIn(0, candlesList.lastIndex)
        val c = candlesList[safeIdx]
        val isLast = safeIdx == candlesList.lastIndex
        val cOpen = if (isLast) currentAnimOpen else c.open
        val cClose = if (isLast) currentAnimClose else c.close
        val cHigh = max(if (isLast) currentAnimHigh else c.high, max(cOpen, cClose))
        val cLow = min(if (isLast) currentAnimLow else c.low, min(cOpen, cClose))
        return Pair(cLow, cHigh)
    }

    fun clampPriceStrictlyInsideCandle(rawPrice: Double, idx: Int, candlesList: List<Candle>): Double {
        val (cLow, cHigh) = getCandleLowHigh(idx, candlesList)
        val clamped = rawPrice.coerceIn(cLow, cHigh)
        val rounded = round(clamped * pointMultiplier) / pointMultiplier
        return rounded.coerceIn(cLow, cHigh)
    }

    fun xToNearestCandleIndex(xPx: Float, candlesCount: Int): Int {
        if (candlesCount <= 0) return 0
        val slot = metrics.totalCandleSlotPx.coerceAtLeast(1f)
        val rawIdx = (candlesCount - 1).toFloat() - ((metrics.candleOriginXPx + metrics.clampedPanPx - xPx) / slot)
        return round(rawIdx).toInt().coerceIn(0, candlesCount - 1)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(quote.symbol, timeframeLabel) {
                val arrowHitRadiusPx = 32.dp.toPx()
                val segmentHitRadiusPx = 22.dp.toPx()
                val touchSlopPx = 10.dp.toPx()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnCanvasTouch?.invoke()
                    flingJob?.cancel()

                    // 1. Check if touch started on the Right-Side Price Axis List (for vertical drag zoom:
                    //    dragging from bottom to top zooms IN vertically like 868916349 -> 851708774 -> 869207484 -> 859133655;
                    //    dragging from top to bottom zooms OUT vertically)
                    val isTouchOnRightPriceAxis = down.position.x >= (metrics.chartWidthPx - 10.dp.toPx()) &&
                        down.position.y <= (metrics.chartHeightPx - 28.dp.toPx())

                    if (isTouchOnRightPriceAxis) {
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            val panChange = event.calculatePan()
                            if (abs(panChange.y) > 0.1f) {
                                // Dragging bottom-to-top (panChange.y < 0) increases priceAxisZoom (zooms in vertically)
                                // Dragging top-to-bottom (panChange.y > 0) decreases priceAxisZoom (zooms out vertically)
                                priceAxisZoom = (priceAxisZoom * exp(-panChange.y / 155f)).coerceIn(0.25f, 6.0f)
                                if (abs(priceAxisZoom - 1.0f) < 0.03f) {
                                    verticalPanPriceOffset = 0.0
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        return@awaitEachGesture
                    }

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
                        return@awaitEachGesture
                    }

                    // 2. Check if touch is on a History Trade marker on the chart (only when enabled in About settings)
                    val activeEdit = if (currentEnableHistoryTradeMove) currentEditingTradeState else null
                    val hitResult = if (currentEnableHistoryTradeMove) {
                        findHitHistoryMarker(
                            pos = down.position,
                            markers = metrics.renderedHistoryMarkers,
                            arrowRadiusPx = arrowHitRadiusPx,
                            segmentRadiusPx = segmentHitRadiusPx,
                            prioritizeTradeId = activeEdit?.originalTrade?.id
                        )
                    } else null

                    var draggingHistoryTrade = false
                    var dragHandle = HistoryDragHandle.OPEN

                    if (activeEdit != null && hitResult != null && hitResult.marker.trade.id == activeEdit.originalTrade.id) {
                        // Already in Edit Mode for this trade: allow immediate dragging of its Entry arrow, Exit arrow, or Both!
                        draggingHistoryTrade = true
                        dragHandle = hitResult.handle
                        editingTradeState = activeEdit.copy(activeHandle = dragHandle)
                    } else if (hitResult != null) {
                        // Wait for long-press (~280ms hold) on the history trade marker to activate Move/Edit Mode
                        var movedOrMultiTouch = false
                        var releasedEarly = false
                        val timedOut = withTimeoutOrNull(280L) {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val pressed = ev.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    releasedEarly = true
                                    break
                                }
                                if (pressed.size >= 2) {
                                    movedOrMultiTouch = true
                                    break
                                }
                                val curPos = pressed.first().position
                                if (hypot(curPos.x - down.position.x, curPos.y - down.position.y) > touchSlopPx) {
                                    movedOrMultiTouch = true
                                    break
                                }
                            }
                        } == null

                        if (timedOut && !movedOrMultiTouch && !releasedEarly) {
                            localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val m = hitResult.marker
                            val candlesList = currentDisplayCandles
                            val safeOpenIdx = m.openCandleIdx.coerceIn(0, (candlesList.size - 1).coerceAtLeast(0))
                            val safeCloseIdx = m.closeCandleIdx.coerceIn(0, (candlesList.size - 1).coerceAtLeast(0))
                            val clampedOpenP = clampPriceStrictlyInsideCandle(m.openPrice, safeOpenIdx, candlesList)
                            val clampedCloseP = clampPriceStrictlyInsideCandle(m.closePrice, safeCloseIdx, candlesList)

                            dragHandle = hitResult.handle
                            editingTradeState = EditableHistoryTradeState(
                                originalTrade = m.trade,
                                isBuy = m.isBuy,
                                openCandleIdx = safeOpenIdx,
                                closeCandleIdx = safeCloseIdx,
                                openPrice = clampedOpenP,
                                closePrice = clampedCloseP,
                                activeHandle = dragHandle
                            )
                            draggingHistoryTrade = true
                        } else if (releasedEarly) {
                            return@awaitEachGesture
                        }
                    }

                    if (draggingHistoryTrade) {
                        val startEdit = editingTradeState ?: return@awaitEachGesture
                        val candlesList = currentDisplayCandles
                        val anchorTouchCandleIdx = xToNearestCandleIndex(down.position.x, candlesList.size)
                        val anchorTouchPrice = metrics.yToPrice(down.position.y)
                        val startOpenIdx = startEdit.openCandleIdx
                        val startCloseIdx = startEdit.closeCandleIdx
                        val startOpenPrice = startEdit.openPrice
                        val startClosePrice = startEdit.closePrice

                        do {
                            val event = awaitPointerEvent()
                            val primary = event.changes.firstOrNull { it.pressed } ?: break
                            val curCandles = currentDisplayCandles
                            if (curCandles.isNotEmpty()) {
                                val targetIdx = xToNearestCandleIndex(primary.position.x, curCandles.size)
                                val rawTouchPrice = metrics.yToPrice(primary.position.y)
                                val currentState = editingTradeState ?: startEdit

                                editingTradeState = when (currentState.activeHandle) {
                                    HistoryDragHandle.OPEN -> {
                                        val newOpenIdx = targetIdx
                                        val newOpenPrice = clampPriceStrictlyInsideCandle(rawTouchPrice, newOpenIdx, curCandles)
                                        if (newOpenIdx > currentState.closeCandleIdx) {
                                            val newClosePrice = clampPriceStrictlyInsideCandle(currentState.closePrice, newOpenIdx, curCandles)
                                            currentState.copy(
                                                openCandleIdx = newOpenIdx,
                                                closeCandleIdx = newOpenIdx,
                                                openPrice = newOpenPrice,
                                                closePrice = newClosePrice
                                            )
                                        } else {
                                            currentState.copy(
                                                openCandleIdx = newOpenIdx,
                                                openPrice = newOpenPrice
                                            )
                                        }
                                    }
                                    HistoryDragHandle.CLOSE -> {
                                        val newCloseIdx = targetIdx
                                        val newClosePrice = clampPriceStrictlyInsideCandle(rawTouchPrice, newCloseIdx, curCandles)
                                        if (newCloseIdx < currentState.openCandleIdx) {
                                            val newOpenPrice = clampPriceStrictlyInsideCandle(currentState.openPrice, newCloseIdx, curCandles)
                                            currentState.copy(
                                                openCandleIdx = newCloseIdx,
                                                closeCandleIdx = newCloseIdx,
                                                openPrice = newOpenPrice,
                                                closePrice = newClosePrice
                                            )
                                        } else {
                                            currentState.copy(
                                                closeCandleIdx = newCloseIdx,
                                                closePrice = newClosePrice
                                            )
                                        }
                                    }
                                    HistoryDragHandle.BOTH -> {
                                        val deltaIdx = targetIdx - anchorTouchCandleIdx
                                        val deltaPrice = rawTouchPrice - anchorTouchPrice
                                        val span = (startCloseIdx - startOpenIdx).coerceAtLeast(0)
                                        val maxValidOpen = (curCandles.lastIndex - span).coerceAtLeast(0)
                                        val newOpenIdx = (startOpenIdx + deltaIdx).coerceIn(0, maxValidOpen)
                                        val newCloseIdx = (newOpenIdx + span).coerceIn(newOpenIdx, curCandles.lastIndex)
                                        val newOpenPrice = clampPriceStrictlyInsideCandle(startOpenPrice + deltaPrice, newOpenIdx, curCandles)
                                        val newClosePrice = clampPriceStrictlyInsideCandle(startClosePrice + deltaPrice, newCloseIdx, curCandles)
                                        currentState.copy(
                                            openCandleIdx = newOpenIdx,
                                            closeCandleIdx = newCloseIdx,
                                            openPrice = newOpenPrice,
                                            closePrice = newClosePrice
                                        )
                                    }
                                }
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                        return@awaitEachGesture
                    }

                    // 3. Normal chart & bottom timeline pan + 2-finger pinch-to-zoom gesture loop
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    val initialPanAtGestureStart = panOffsetPx
                    var wasMultiTouchZoom = false

                    do {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }
                        val pressedCount = pressedChanges.size
                        if (pressedCount == 0) break

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pressedCount >= 2) {
                            if (!wasMultiTouchZoom) {
                                wasMultiTouchZoom = true
                                // Restore initial right-edge alignment if finger-1 slightly nudged panOffsetPx before finger-2 landed
                                if (abs(initialPanAtGestureStart) <= 24f) {
                                    panOffsetPx = 0f
                                }
                            }
                            if (abs(zoomChange - 1f) > 0.0015f) {
                                if (currentIsFit14Days) {
                                    isFit14Days = false
                                    currentOnFit14DaysChange?.invoke(false)
                                }
                                val oldZoom = zoomScale
                                val newZoom = (oldZoom * zoomChange).coerceIn(0.14f, 3.2f)
                                val actualRatio = newZoom / oldZoom
                                zoomScale = newZoom

                                if (abs(panOffsetPx) <= 24f) {
                                    // Keep right-edge chart shift anchored when zooming in/out near the live bar (matching 876703870..869442267)
                                    panOffsetPx = 0f
                                } else {
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val focusX = if (centroid != Offset.Unspecified) centroid.x else metrics.chartWidthPx * 0.5f
                                    val originX = metrics.candleOriginXPx
                                    panOffsetPx = ((originX + panOffsetPx - focusX) * actualRatio - originX + focusX)
                                        .coerceIn(metrics.minPanBound, metrics.maxPanBound)
                                }
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (!wasMultiTouchZoom && (abs(panChange.x) > 0.1f || abs(panChange.y) > 0.1f)) {
                            if (abs(panChange.x) > 0.1f) {
                                if (currentIsFit14Days) {
                                    val defaultSlotPx = 9.5.dp.toPx()
                                    if (defaultSlotPx > 0f && metrics.totalCandleSlotPx > 0f) {
                                        zoomScale = (metrics.totalCandleSlotPx / defaultSlotPx).coerceIn(0.14f, 3.2f)
                                    }
                                    isFit14Days = false
                                    currentOnFit14DaysChange?.invoke(false)
                                }

                                panOffsetPx = (panOffsetPx + panChange.x)
                                    .coerceIn(metrics.minPanBound, metrics.maxPanBound)

                                val primaryChange = pressedChanges.firstOrNull()
                                if (primaryChange != null) {
                                    velocityTracker.addPosition(primaryChange.uptimeMillis, primaryChange.position)
                                }
                            }
                            if (abs(panChange.y) > 0.5f && priceAxisZoom > 1.15f && down.position.y < metrics.chartHeightPx) {
                                val priceDelta = (panChange.y / metrics.gridSpanPx.coerceAtLeast(1f)) * metrics.totalGridPriceSpanVal
                                verticalPanPriceOffset = (verticalPanPriceOffset + priceDelta).coerceIn(
                                    -metrics.totalGridPriceSpanVal * 1.5,
                                    metrics.totalGridPriceSpanVal * 1.5
                                )
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    val velocityX = velocityTracker.calculateVelocity().x
                    if (!wasMultiTouchZoom && abs(velocityX) > 140f && !currentIsFit14Days) {
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
            .pointerInput(showCrosshair) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (offset.x >= metrics.chartWidthPx - 12.dp.toPx()) {
                            priceAxisZoom = 1.0f
                            verticalPanPriceOffset = 0.0
                        }
                    },
                    onTap = { offset ->
                        if (showCrosshair) {
                            crosshairPos = if (crosshairPos == null) offset else null
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (displayCandles.isEmpty()) return@Canvas

            val rightAxisWidth = 58.dp.toPx()
            val bottomAxisHeight = 24.dp.toPx()
            val chartWidth = size.width - rightAxisWidth
            val chartHeight = size.height - bottomAxisHeight

            if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

            // 11 evenly-spaced vertical grid columns (col = 0..10) across chartWidth matching all 9 MT5 screenshots:
            // - Column 0, Column 3, Column 6, Column 9 display the 4 bottom timeline labels
            // - Column 7 is the default anchor position for the latest live candle (with Column 6 when zoomed far out)
            val numVertGridCols = 11
            val firstVertGridX = (chartWidth * 0.022f).coerceAtLeast(6.dp.toPx())
            val lastVertGridX = (chartWidth * 0.985f).coerceAtMost(chartWidth - 4.dp.toPx())
            val vertGridSpacingPx = ((lastVertGridX - firstVertGridX) / (numVertGridCols - 1).toFloat()).coerceAtLeast(16f)

            val col7X = firstVertGridX + 7f * vertGridSpacingPx
            val col6X = firstVertGridX + 6f * vertGridSpacingPx
            val zoomOutShiftFraction = if (isFit14Days) {
                0f
            } else if (zoomScale < 0.65f) {
                ((0.65f - zoomScale) / 0.45f).coerceIn(0f, 1f)
            } else {
                0f
            }
            val candleOriginX = if (isFit14Days) {
                chartWidth - 20.dp.toPx()
            } else {
                col7X - (col7X - col6X) * zoomOutShiftFraction
            }
            val rightMargin = (chartWidth - candleOriginX).coerceAtLeast(12.dp.toPx())

            val usableWidth = (chartWidth - rightMargin - 8.dp.toPx()).coerceAtLeast(60f)
            val totalCandleSlot = if (isFit14Days) {
                (usableWidth / displayCandles.size.coerceAtLeast(1)).coerceIn(1.2.dp.toPx(), 16.dp.toPx())
            } else {
                (9.5.dp.toPx() * zoomScale).coerceIn(1.2.dp.toPx(), 30.dp.toPx())
            }
            val candleWidth = when {
                totalCandleSlot < 2.6.dp.toPx() -> (totalCandleSlot * 0.62f).coerceAtLeast(1.0.dp.toPx())
                totalCandleSlot < 5.0.dp.toPx() -> (totalCandleSlot * 0.65f).coerceAtLeast(1.6.dp.toPx())
                else -> (totalCandleSlot * 0.70f).coerceAtLeast(2.2.dp.toPx())
            }
            val wickStrokeWidth = if (totalCandleSlot < 4.0.dp.toPx()) 0.85.dp.toPx() else 1.1.dp.toPx()

            val n = displayCandles.size
            val computedMinPan = if (isFit14Days) 0f else -(chartWidth * 0.42f)
            val computedMaxPan = if (isFit14Days) {
                0f
            } else {
                max(0f, ((n - 1) * totalCandleSlot) - (candleOriginX - 24.dp.toPx()))
            }

            metrics.minPanBound = computedMinPan
            metrics.maxPanBound = computedMaxPan
            metrics.chartWidthPx = chartWidth
            metrics.chartHeightPx = chartHeight
            metrics.candleOriginXPx = candleOriginX
            metrics.totalCandleSlotPx = totalCandleSlot

            val clampedPan = if (isFit14Days) 0f else panOffsetPx.coerceIn(computedMinPan, computedMaxPan)
            metrics.clampedPanPx = clampedPan

            val floatLastIdx = (n - 1).toFloat()
            val idxAtLeftEdge = floatLastIdx - ((candleOriginX + clampedPan + totalCandleSlot * 2f) / totalCandleSlot)
            val idxAtRightEdge = floatLastIdx - ((candleOriginX + clampedPan - (chartWidth + totalCandleSlot * 2f)) / totalCandleSlot)

            val leftIndex = if (isFit14Days) 0 else floor(idxAtLeftEdge).toInt().coerceIn(0, n - 1)
            val rightIndex = if (isFit14Days) n - 1 else ceil(idxAtRightEdge).toInt().coerceIn(leftIndex, n - 1)

            var minPrice = Double.MAX_VALUE
            var maxPrice = -Double.MAX_VALUE
            var minBodyPrice = Double.MAX_VALUE
            var maxBodyPrice = -Double.MAX_VALUE

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
                    minBodyPrice = min(minBodyPrice, min(cOpen, cClose))
                    maxBodyPrice = max(maxBodyPrice, max(cOpen, cClose))
                }
            }

            if (minPrice == Double.MAX_VALUE || maxPrice <= 0.0) {
                for (globalIdx in leftIndex..rightIndex) {
                    val c = displayCandles[globalIdx]
                    minPrice = min(minPrice, c.low)
                    maxPrice = max(maxPrice, c.high)
                    minBodyPrice = min(minBodyPrice, min(c.open, c.close))
                    maxBodyPrice = max(maxBodyPrice, max(c.open, c.close))
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

            // Include visible historical trades in minPrice/maxPrice so trade arrows are within the default viewport
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
            val minPointStep = 1.0 / pointMultiplier
            val effectiveRange = if (rawRange <= minPointStep * 10.0) {
                minPointStep * 180.0
            } else {
                rawRange
            }

            // Default vertical padding (~0.40 on each side so 29255..29602 spans ~29080..29760 like Screenshots 5..9),
            // scaled dynamically by `priceAxisZoom` when the user drags bottom-to-top or top-to-bottom on the right price axis!
            val basePaddingFactor = if (isFit14Days) 0.22 else 0.40
            val rawPadding = effectiveRange * basePaddingFactor
            val numHorizontalLines = 18
            val topGridMargin = 13.dp.toPx()
            val bottomGridMargin = 20.dp.toPx()
            val gridSpanPx = (chartHeight - topGridMargin - bottomGridMargin).coerceAtLeast(100f)

            // Apply right-axis vertical drag zoom (`priceAxisZoom`) to the price span
            val unzoomedGridPriceSpan = effectiveRange + rawPadding * 2.0
            val targetGridPriceSpan = (unzoomedGridPriceSpan / priceAxisZoom.toDouble())
                .coerceAtLeast(minPointStep * (numHorizontalLines - 1).toDouble())
            val rawStepPerLine = targetGridPriceSpan / (numHorizontalLines - 1).toDouble()
            val roundedStepPoints = max(1L, kotlin.math.round(rawStepPerLine * pointMultiplier).toLong())
            val exactPriceStep = roundedStepPoints.toDouble() / pointMultiplier
            val totalGridPriceSpan = exactPriceStep * (numHorizontalLines - 1).toDouble()

            val wickMidPrice = (minPrice + maxPrice) * 0.5
            val bodyMidPrice = if (minBodyPrice < Double.MAX_VALUE && maxBodyPrice > 0.0) {
                (minBodyPrice + maxBodyPrice) * 0.5
            } else {
                wickMidPrice
            }
            val zoomInCenterBlend = ((priceAxisZoom - 1.0f) / 0.85f).coerceIn(0f, 1f).toDouble()
            val midPrice = wickMidPrice + (bodyMidPrice - wickMidPrice) * zoomInCenterBlend + verticalPanPriceOffset
            val topGridPrice = (kotlin.math.round((midPrice + totalGridPriceSpan * 0.5) * pointMultiplier) / pointMultiplier)

            metrics.topGridMarginPx = topGridMargin
            metrics.gridSpanPx = gridSpanPx
            metrics.topGridPriceVal = topGridPrice
            metrics.totalGridPriceSpanVal = totalGridPriceSpan

            fun priceToY(price: Double): Float {
                val ratio = (topGridPrice - price) / totalGridPriceSpan
                return (topGridMargin + ratio * gridSpanPx).toFloat()
            }

            fun yToPrice(y: Float): Double {
                val ratio = (y - topGridMargin) / gridSpanPx
                return topGridPrice - ratio * totalGridPriceSpan
            }

            val gridDash = PathEffect.dashPathEffect(floatArrayOf(3.5.dp.toPx(), 3.5.dp.toPx()), 0f)

            // Draw horizontal dashed price grid lines and crisp right-axis price labels
            val rowSpacingPx = gridSpanPx / (numHorizontalLines - 1).toFloat()
            for (row in -1..numHorizontalLines) {
                val priceAtRow = topGridPrice - row * exactPriceStep
                val y = topGridMargin + rowSpacingPx * row
                if (y < 2.dp.toPx() || y > chartHeight - 2.dp.toPx()) continue

                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 0.85.dp.toPx(),
                    pathEffect = gridDash
                )

                if (row in 0 until numHorizontalLines) {
                    val priceStr = String.format(Locale.US, "%.${quote.digits}f", priceAtRow)
                    val labelY = (y - 6.5.dp.toPx()).coerceIn(1.dp.toPx(), chartHeight - 16.dp.toPx())
                    drawText(
                        textMeasurer = textMeasurer,
                        text = priceStr,
                        topLeft = Offset(chartWidth + 4.5.dp.toPx(), labelY),
                        style = TextStyle(
                            fontSize = 10.2.sp,
                            color = axisTextColor,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }
            }

            // Draw 11 vertical dashed time grid lines (col = 0..10) and bottom-axis timestamps on col = 0, 3, 6, 9:
            // As the user pinches 2 fingers together/apart (`zoomScale` / `totalCandleSlot`), the timestamps at
            // col = 0, 3, 6, 9 dynamically zoom in and out just like in 876703870, 873921828, 866472779, 875028027, 869442267.
            val lastCandleTs = displayCandles.last().timestamp
            val firstCandleTs = displayCandles.first().timestamp

            for (col in 0 until numVertGridCols) {
                val x = firstVertGridX + col * vertGridSpacingPx
                if (x < 1.dp.toPx() || x > chartWidth - 1.dp.toPx()) continue

                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, chartHeight),
                    strokeWidth = 0.85.dp.toPx(),
                    pathEffect = gridDash
                )

                if (col % 3 == 0) {
                    val gridCandleIdx = kotlin.math.round(
                        (n - 1).toFloat() - ((candleOriginX + clampedPan - x) / totalCandleSlot)
                    ).toInt()
                    val tsAtGrid = when {
                        gridCandleIdx in 0 until n -> displayCandles[gridCandleIdx].timestamp
                        gridCandleIdx >= n && lastCandleTs > 0L -> lastCandleTs + (gridCandleIdx - (n - 1)).toLong() * tfIntervalMs
                        gridCandleIdx < 0 && firstCandleTs > 0L -> firstCandleTs - (0 - gridCandleIdx).toLong() * tfIntervalMs
                        else -> lastCandleTs
                    }
                    val timeLabel = if (tsAtGrid > 0L) {
                        axisDateFormat.format(Date(tsAtGrid))
                    } else {
                        displayCandles.last().time
                    }
                    val textLayout = textMeasurer.measure(
                        text = timeLabel,
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = axisTextColor,
                            fontWeight = FontWeight.Normal
                        )
                    )
                    val centeredLeft = x - textLayout.size.width / 2f
                    if (centeredLeft + textLayout.size.width > 2.dp.toPx() && centeredLeft < chartWidth - 2.dp.toPx()) {
                        clipRect(left = 0f, top = chartHeight, right = chartWidth, bottom = size.height) {
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(centeredLeft, chartHeight + 4.dp.toPx())
                            )
                        }
                    }
                }
            }

            // Draw Candlesticks and optional Moving Average inside plot clipRect
            metrics.hoveredCandleIndex = -1

            clipRect(left = 0f, top = 0f, right = chartWidth, bottom = chartHeight) {
                // Draw Open Trade Dashed Lines & Inline Labels BEFORE candlesticks so candlesticks render on top of the text & lines
                // (matching 866760067 & 867873558 where labels sit strictly on their own openY line and only overlap when the lines themselves overlap)
                val openTradeDashEffect = PathEffect.dashPathEffect(
                    floatArrayOf(3.5.dp.toPx(), 2.5.dp.toPx()),
                    0f
                )
                activeSymbolPositions.forEach { pos ->
                    pos.sl?.let { slPrice ->
                        if (slPrice > 0.0) {
                            val slY = priceToY(slPrice)
                            if (slY in 0f..chartHeight) {
                                drawLine(
                                    color = Color(0xFFD32F2F),
                                    start = Offset(0f, slY),
                                    end = Offset(chartWidth, slY),
                                    strokeWidth = 1.1.dp.toPx(),
                                    pathEffect = openTradeDashEffect
                                )
                            }
                        }
                    }

                    pos.tp?.let { tpPrice ->
                        if (tpPrice > 0.0) {
                            val tpY = priceToY(tpPrice)
                            if (tpY in 0f..chartHeight) {
                                drawLine(
                                    color = Color(0xFF26A69A),
                                    start = Offset(0f, tpY),
                                    end = Offset(chartWidth, tpY),
                                    strokeWidth = 1.1.dp.toPx(),
                                    pathEffect = openTradeDashEffect
                                )
                            }
                        }
                    }

                    val openY = priceToY(pos.openPrice)
                    if (openY in -20.dp.toPx()..(chartHeight + 20.dp.toPx())) {
                        val tradeDashLineColor = if (!pos.isBuy || pos.profit < 0.0) {
                            Color(0xFFA94438)
                        } else {
                            Color(0xFF0D62C6)
                        }
                        val sideTextColor = if (pos.isBuy) Color(0xFF0D62C6) else Color(0xFFB71C1C)
                        val pnlTextColor = if (pos.profit >= 0.0) Color(0xFF0D62C6) else Color(0xFFB71C1C)

                        drawLine(
                            color = tradeDashLineColor,
                            start = Offset(0f, openY),
                            end = Offset(chartWidth, openY),
                            strokeWidth = 1.1.dp.toPx(),
                            pathEffect = openTradeDashEffect
                        )

                        val typeStr = if (pos.isBuy) "BUY" else "SELL"
                        val lotsStr = String.format(Locale.US, "%.2f", pos.lots)
                        val pnlSign = if (pos.profit >= 0.0) "+" else ""
                        val pnlStr = "$pnlSign${String.format(Locale.US, "%.2f", pos.profit)} USD"

                        val annotatedTradeLabel = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = sideTextColor,
                                    fontFamily = MT5RobotoCondensedFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("$typeStr $lotsStr, ")
                            }
                            withStyle(
                                SpanStyle(
                                    color = pnlTextColor,
                                    fontFamily = MT5RobotoCondensedFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(pnlStr)
                            }
                        }

                        val measuredLabel = textMeasurer.measure(text = annotatedTradeLabel)
                        val labelY = openY - measuredLabel.size.height - 1.dp.toPx()

                        drawText(
                            textLayoutResult = measuredLabel,
                            topLeft = Offset(8.dp.toPx(), labelY)
                        )
                    }
                }

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
                        strokeWidth = wickStrokeWidth
                    )

                    // Body
                    val bodyTop = min(openY, closeY)
                    val bodyBottom = max(openY, closeY)
                    val bodyHeight = max(1.5f, bodyBottom - bodyTop)

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
                // MT5 Trade History Markers on Chart (matching Zoom-In / Med-Zoom / Zoom-Out Screenshots):
                // - Red Down-Pointing Block Arrow for SELL deals (opening a SELL or closing a BUY)
                // - Blue Up-Pointing Block Arrow for BUY deals (opening a BUY or closing a SELL)
                // - Dashed connector line between (openX, openY) and (closeX, closeY)
                // - Arrow size & dash pattern scale dynamically with zoom level (totalCandleSlot):
                //   * Zoomed In (totalCandleSlot >= 15.5dp): 12.5dp wide × 11.8dp tall
                //   * Medium / Zoomed Out (totalCandleSlot <= 8.0dp): 8.0dp wide × 7.1dp tall
                // =========================================================================
                val arrowScale = ((totalCandleSlot - 8.0.dp.toPx()) / (7.5.dp.toPx())).coerceIn(0f, 1f)
                val headHalfW = 4.0.dp.toPx() + (2.25.dp.toPx() * arrowScale)
                val headH = 4.6.dp.toPx() + (3.2.dp.toPx() * arrowScale)
                val shaftHalfW = 2.4.dp.toPx() + (0.95.dp.toPx() * arrowScale)
                val totalH = 7.1.dp.toPx() + (4.7.dp.toPx() * arrowScale)
                val haloStrokeW = 1.1.dp.toPx() + (0.45.dp.toPx() * arrowScale)
                val innerBorderW = 0.5.dp.toPx() + (0.15.dp.toPx() * arrowScale)

                fun drawMt5DealArrow(tipX: Float, tipY: Float, isUpArrow: Boolean) {
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

                    val fillColor = if (isUpArrow) Color(0xFF2F88EC) else Color(0xFFE84E4C)
                    val borderColor = if (isUpArrow) Color(0xFF1F6BC4) else Color(0xFFC03937)

                    // White outer halo so the arrow stands out cleanly over red/green candle bodies
                    drawPath(
                        path = arrowPath,
                        color = Color.White,
                        style = Stroke(width = haloStrokeW)
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
                        style = Stroke(width = innerBorderW)
                    )
                }

                val dashLen = 2.0.dp.toPx() + (1.2.dp.toPx() * arrowScale)
                val gapLen = 1.6.dp.toPx() + (0.8.dp.toPx() * arrowScale)
                val connectorStrokeW = 0.8.dp.toPx() + (0.25.dp.toPx() * arrowScale)
                val tradeConnectorDash = PathEffect.dashPathEffect(
                    floatArrayOf(dashLen, gapLen),
                    0f
                )

                // Pass 1: Draw all dashed connector lines first so arrows always render cleanly on top
                val activeEdit = editingTradeState
                metrics.renderedHistoryMarkers.clear()

                resolvedHistoryMarkers.forEach { marker ->
                    val isBeingEdited = activeEdit != null && activeEdit.originalTrade.id == marker.trade.id
                    val openIdx = if (isBeingEdited && activeEdit != null) activeEdit.openCandleIdx else marker.openCandleIdx
                    val closeIdx = if (isBeingEdited && activeEdit != null) activeEdit.closeCandleIdx else marker.closeCandleIdx
                    val openPriceVal = if (isBeingEdited && activeEdit != null) activeEdit.openPrice else marker.trade.openPrice
                    val closePriceVal = if (isBeingEdited && activeEdit != null) activeEdit.closePrice else marker.trade.closePrice
                    val isBuyVal = if (isBeingEdited && activeEdit != null) activeEdit.isBuy else marker.trade.isBuy

                    val openX = candleOriginX - ((n - 1 - openIdx) * totalCandleSlot) + clampedPan
                    val closeX = candleOriginX - ((n - 1 - closeIdx) * totalCandleSlot) + clampedPan

                    val minX = min(openX, closeX)
                    val maxX = max(openX, closeX)
                    if (maxX < -40.dp.toPx() || minX > chartWidth + 40.dp.toPx()) return@forEach

                    val openY = priceToY(openPriceVal)
                    val closeY = priceToY(closePriceVal)
                    val openBodyCenterY = if (isBuyVal) openY + totalH * 0.5f else openY - totalH * 0.5f
                    val closeBodyCenterY = if (!isBuyVal) closeY + totalH * 0.5f else closeY - totalH * 0.5f

                    metrics.renderedHistoryMarkers.add(
                        RenderedHistoryMarker(
                            trade = marker.trade,
                            isBuy = isBuyVal,
                            openCandleIdx = openIdx,
                            closeCandleIdx = closeIdx,
                            openPrice = openPriceVal,
                            closePrice = closePriceVal,
                            openTipX = openX,
                            openTipY = openY,
                            openBodyCenterY = openBodyCenterY,
                            closeTipX = closeX,
                            closeTipY = closeY,
                            closeBodyCenterY = closeBodyCenterY
                        )
                    )

                    val dx = closeX - openX
                    val dy = closeY - openY
                    val distPx = hypot(dx, dy)

                    if (distPx >= 1.5.dp.toPx()) {
                        drawLine(
                            color = if (isBeingEdited) Color(0xFF0D62C6) else Color(0xFF5A7D95),
                            start = Offset(openX, openY),
                            end = Offset(closeX, closeY),
                            strokeWidth = if (isBeingEdited) connectorStrokeW * 1.45f else connectorStrokeW,
                            pathEffect = tradeConnectorDash
                        )
                    }
                }

                // Pass 2: Draw Entry & Exit deal arrows on top of the dashed lines
                val drawnArrowSpots = mutableListOf<Triple<Float, Float, Boolean>>()
                fun drawDealArrowDeduplicated(tipX: Float, tipY: Float, isUpArrow: Boolean, forceDraw: Boolean = false) {
                    if (forceDraw) {
                        drawMt5DealArrow(tipX = tipX, tipY = tipY, isUpArrow = isUpArrow)
                        return
                    }
                    val duplicate = drawnArrowSpots.any { (x, y, up) ->
                        up == isUpArrow && abs(x - tipX) < 0.6.dp.toPx() && abs(y - tipY) < 0.6.dp.toPx()
                    }
                    if (!duplicate) {
                        drawnArrowSpots.add(Triple(tipX, tipY, isUpArrow))
                        drawMt5DealArrow(tipX = tipX, tipY = tipY, isUpArrow = isUpArrow)
                    }
                }

                metrics.renderedHistoryMarkers.forEach { rm ->
                    val isBeingEdited = activeEdit != null && activeEdit.originalTrade.id == rm.trade.id

                    if (isBeingEdited && activeEdit != null) {
                        // Draw subtle horizontal price guide lines to right axis and selection halos around Entry & Exit arrows
                        val guideDash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()), 0f)
                        val openGuideColor = if (rm.isBuy) Color(0xFF0D62C6) else Color(0xFFD32F2F)
                        val closeGuideColor = if (!rm.isBuy) Color(0xFF0D62C6) else Color(0xFFD32F2F)

                        drawLine(
                            color = openGuideColor.copy(alpha = 0.65f),
                            start = Offset(rm.openTipX, rm.openTipY),
                            end = Offset(chartWidth, rm.openTipY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = guideDash
                        )
                        drawLine(
                            color = closeGuideColor.copy(alpha = 0.65f),
                            start = Offset(rm.closeTipX, rm.closeTipY),
                            end = Offset(chartWidth, rm.closeTipY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = guideDash
                        )

                        val openActive = activeEdit.activeHandle == HistoryDragHandle.OPEN || activeEdit.activeHandle == HistoryDragHandle.BOTH
                        val closeActive = activeEdit.activeHandle == HistoryDragHandle.CLOSE || activeEdit.activeHandle == HistoryDragHandle.BOTH

                        drawCircle(
                            color = openGuideColor.copy(alpha = if (openActive) 0.22f else 0.10f),
                            radius = 12.dp.toPx(),
                            center = Offset(rm.openTipX, rm.openBodyCenterY)
                        )
                        drawCircle(
                            color = openGuideColor,
                            radius = 12.dp.toPx(),
                            center = Offset(rm.openTipX, rm.openBodyCenterY),
                            style = Stroke(width = if (openActive) 1.6.dp.toPx() else 1.0.dp.toPx())
                        )

                        drawCircle(
                            color = closeGuideColor.copy(alpha = if (closeActive) 0.22f else 0.10f),
                            radius = 12.dp.toPx(),
                            center = Offset(rm.closeTipX, rm.closeBodyCenterY)
                        )
                        drawCircle(
                            color = closeGuideColor,
                            radius = 12.dp.toPx(),
                            center = Offset(rm.closeTipX, rm.closeBodyCenterY),
                            style = Stroke(width = if (closeActive) 1.6.dp.toPx() else 1.0.dp.toPx())
                        )
                    }

                    // Entry deal arrow: BUY trade opens with Blue Up Arrow; SELL trade opens with Red Down Arrow
                    drawDealArrowDeduplicated(
                        tipX = rm.openTipX,
                        tipY = rm.openTipY,
                        isUpArrow = rm.isBuy,
                        forceDraw = isBeingEdited
                    )

                    // Exit deal arrow: closing a BUY trade is a SELL deal (Red Down Arrow);
                    // closing a SELL trade is a BUY deal (Blue Up Arrow)
                    drawDealArrowDeduplicated(
                        tipX = rm.closeTipX,
                        tipY = rm.closeTipY,
                        isUpArrow = !rm.isBuy,
                        forceDraw = isBeingEdited
                    )
                }

                // Also draw the entry deal arrow for each currently open position on its entry candle
                activeSymbolPositions.forEach { pos ->
                    val openMs = com.example.data.SriLankaMarketHours.parseMt5DateTimeMillis(pos.openTime)
                    val openIdx = findCandleIndexForTimestamp(displayCandles, openMs, tfIntervalMs)
                    if (openIdx in 0 until n) {
                        val openX = candleOriginX - ((n - 1 - openIdx) * totalCandleSlot) + clampedPan
                        if (openX >= -40.dp.toPx() && openX <= chartWidth + 40.dp.toPx()) {
                            val openY = priceToY(pos.openPrice)
                            drawDealArrowDeduplicated(
                                tipX = openX,
                                tipY = openY,
                                isUpArrow = pos.isBuy
                            )
                        }
                    }
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

            // Draw Right-Axis Price Tags for Open Positions (SL, TP, and Outlined Open Price Box)
            // Drawn BEFORE the live Red Ask / Green Bid badges so the Green Bid badge covers the open price box
            // when at the same price (866760067) and reveals it when price moves away (867873558)
            activeSymbolPositions.forEach { pos ->
                pos.sl?.let { slPrice ->
                    if (slPrice > 0.0) {
                        val slY = priceToY(slPrice)
                        if (slY in 0f..chartHeight) {
                            val slColor = Color(0xFFD32F2F)
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

                val openY = priceToY(pos.openPrice)
                if (openY in 0f..chartHeight) {
                    val tagBorderColor = if (!pos.isBuy || pos.profit < 0.0) {
                        Color(0xFFA94438)
                    } else {
                        Color(0xFF0D62C6)
                    }
                    val tagHeight = 16.dp.toPx()
                    val tagTop = (openY - tagHeight / 2f).coerceIn(0f, chartHeight - tagHeight)
                    val openPriceStr = String.format(Locale.US, "%.${quote.digits}f", pos.openPrice)

                    drawRect(
                        color = Color.White,
                        topLeft = Offset(chartWidth + 0.5.dp.toPx(), tagTop),
                        size = Size(rightAxisWidth - 1.dp.toPx(), tagHeight)
                    )
                    drawRect(
                        color = tagBorderColor,
                        topLeft = Offset(chartWidth + 0.5.dp.toPx(), tagTop),
                        size = Size(rightAxisWidth - 1.dp.toPx(), tagHeight),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = openPriceStr,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), tagTop + 1.dp.toPx()),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = tagBorderColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
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

        // =========================================================================
        // Floating History Trade Move & Edit Control Bar
        // Appears when the user long-presses ("obagena idapuwama") a history trade on the chart.
        // Includes:
        // - Live auto-calculated profit & moved candle prices
        // - Handle selector (Entry / Exit / Both)
        // - BUY / SELL order type change button right next to the Save button
        // - Save button that auto-updates Trade History & Account Balance
        // =========================================================================
        editingTradeState?.let { editState ->
            val liveProfit = remember(
                editState.isBuy,
                editState.openPrice,
                editState.closePrice,
                editState.originalTrade.lots,
                quote.symbol
            ) {
                onCalculateHistoryProfit?.invoke(
                    editState.isBuy,
                    editState.originalTrade.lots,
                    editState.openPrice,
                    editState.closePrice
                ) ?: run {
                    val diff = if (editState.isBuy) {
                        editState.closePrice - editState.openPrice
                    } else {
                        editState.openPrice - editState.closePrice
                    }
                    round(diff * editState.originalTrade.lots * 100.0 * 100.0) / 100.0
                }
            }

            val openPriceFormatted = String.format(Locale.US, "%.${quote.digits}f", editState.openPrice)
            val closePriceFormatted = String.format(Locale.US, "%.${quote.digits}f", editState.closePrice)
            val profitFormatted = String.format(
                Locale.US,
                "%s%.2f USD",
                if (liveProfit >= 0.0) "+" else "",
                liveProfit
            )
            val profitColor = if (liveProfit >= 0.0) Color(0xFF0D62C6) else Color(0xFFD32F2F)
            val orderSideColor = if (editState.isBuy) Color(0xFF0D62C6) else Color(0xFFD32F2F)

            Surface(
                color = Color(0xFFFDFDFE),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 8.dp, end = 62.dp, bottom = 28.dp)
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFFD0D7DE),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("chart_history_edit_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    // Row 1: Trade Summary (Order Side + Lots, Open -> Close Prices, Live Auto-Calculated Profit, Cancel Button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${if (editState.isBuy) "buy" else "sell"} ${String.format(Locale.US, "%.2f", editState.originalTrade.lots)}",
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = orderSideColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$openPriceFormatted → $closePriceFormatted",
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF424548)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profitFormatted,
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = profitColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF1F3F5))
                                    .clickable { editingTradeState = null }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                                    .testTag("chart_history_cancel_edit_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✕",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF495057)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: Handle Selector (Entry / Exit / Both) + BUY/SELL Toggle Button right next to Save Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Drag Handle Mode Pills
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                HistoryDragHandle.OPEN to "Entry",
                                HistoryDragHandle.CLOSE to "Exit",
                                HistoryDragHandle.BOTH to "Both"
                            ).forEach { (handleMode, label) ->
                                val selected = editState.activeHandle == handleMode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (selected) Color(0xFFE7F1FF) else Color(0xFFF1F3F5)
                                        )
                                        .border(
                                            width = 0.8.dp,
                                            color = if (selected) Color(0xFF0D62C6) else Color(0xFFDADCE0),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            editingTradeState = editState.copy(activeHandle = handleMode)
                                        }
                                        .padding(horizontal = 7.dp, vertical = 5.dp)
                                        .testTag("chart_history_handle_${label.lowercase(Locale.US)}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color(0xFF0D62C6) else Color(0xFF495057)
                                    )
                                }
                            }
                        }

                        // Right: Order Type BUY / SELL Change Button right next to Save Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // BUY / SELL Toggle Button right next to Save button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(orderSideColor)
                                    .clickable {
                                        localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        editingTradeState = editState.copy(isBuy = !editState.isBuy)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("chart_history_toggle_buy_sell_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (editState.isBuy) "⇄ BUY" else "⇄ SELL",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Save Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color(0xFF198754))
                                    .clickable {
                                        val curCandles = displayCandles
                                        if (curCandles.isNotEmpty()) {
                                            val safeOpenIdx = editState.openCandleIdx.coerceIn(0, curCandles.lastIndex)
                                            val safeCloseIdx = editState.closeCandleIdx.coerceIn(safeOpenIdx, curCandles.lastIndex)
                                            val finalOpenPrice = clampPriceStrictlyInsideCandle(editState.openPrice, safeOpenIdx, curCandles)
                                            val finalClosePrice = clampPriceStrictlyInsideCandle(editState.closePrice, safeCloseIdx, curCandles)

                                            val origOpenMs = com.example.data.SriLankaMarketHours.parseMt5DateTimeMillis(editState.originalTrade.openTime)
                                            val origCloseMs = com.example.data.SriLankaMarketHours.parseMt5DateTimeMillis(editState.originalTrade.closeTime)
                                            val openSec = if (origOpenMs > 0L) (origOpenMs / 1000L % 60L).coerceIn(2L, 55L) else 12L
                                            val closeSec = if (origCloseMs > 0L) (origCloseMs / 1000L % 60L).coerceIn(2L, 58L) else 44L

                                            val openCandleBaseMs = (curCandles[safeOpenIdx].timestamp / 60_000L) * 60_000L
                                            val closeCandleBaseMs = (curCandles[safeCloseIdx].timestamp / 60_000L) * 60_000L

                                            val newOpenMs: Long
                                            val newCloseMs: Long
                                            if (closeCandleBaseMs <= openCandleBaseMs) {
                                                val s1 = min(openSec, 22L)
                                                val s2 = max(closeSec, s1 + 18L).coerceAtMost(58L)
                                                newOpenMs = openCandleBaseMs + s1 * 1000L
                                                newCloseMs = openCandleBaseMs + s2 * 1000L
                                            } else {
                                                newOpenMs = openCandleBaseMs + openSec * 1000L
                                                newCloseMs = closeCandleBaseMs + closeSec * 1000L
                                            }

                                            val newOpenTimeStr = com.example.data.SriLankaMarketHours.formatHistoryTimestamp(newOpenMs)
                                            val newCloseTimeStr = com.example.data.SriLankaMarketHours.formatHistoryTimestamp(newCloseMs)

                                            onSaveHistoryTradeEdit?.invoke(
                                                editState.originalTrade,
                                                editState.isBuy,
                                                finalOpenPrice,
                                                finalClosePrice,
                                                newOpenTimeStr,
                                                newCloseTimeStr
                                            )
                                        }
                                        editingTradeState = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("chart_history_save_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Save",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
