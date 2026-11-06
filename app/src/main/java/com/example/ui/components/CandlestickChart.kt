package com.example.ui.components

import android.util.LruCache
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.text.TextLayoutResult
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

private data class EditableRunningPositionState(
    val originalPosition: TradePosition,
    val isBuy: Boolean,
    val openCandleIdx: Int,
    val openPrice: Double
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

private data class RenderedRunningPositionMarker(
    val position: TradePosition,
    val isBuy: Boolean,
    val openCandleIdx: Int,
    val openPrice: Double,
    val openTipX: Float,
    val openY: Float,
    val openBodyCenterY: Float
)

private data class HitHistoryMarkerResult(
    val marker: RenderedHistoryMarker,
    val handle: HistoryDragHandle,
    val distancePx: Float
)

private data class HitRunningPositionResult(
    val marker: RenderedRunningPositionMarker,
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
    var hasGridAnchor: Boolean = false
    var lastTopGridPrice: Double = 0.0
    var gridScrollOffsetYPx: Float = 0f
    var smoothedMinPrice: Double = Double.NaN
    var smoothedMaxPrice: Double = Double.NaN
    var smoothedMinBodyPrice: Double = Double.NaN
    var smoothedMaxBodyPrice: Double = Double.NaN
    var lastPanForScalePx: Float = Float.NaN
    var hoveredCandleIndex: Int = -1
    val renderedHistoryMarkers = ArrayList<RenderedHistoryMarker>(32)
    val renderedRunningPositions = ArrayList<RenderedRunningPositionMarker>(16)
    val drawnArrowSpots = ArrayList<Triple<Float, Float, Boolean>>(64)
    val reusableMaPath = Path()
    val reusableArrowPath = Path()

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

private fun findHitRunningPosition(
    pos: Offset,
    markers: List<RenderedRunningPositionMarker>,
    arrowRadiusPx: Float,
    lineRadiusPx: Float,
    chartWidthPx: Float,
    prioritizePositionId: Long? = null
): HitRunningPositionResult? {
    if (markers.isEmpty()) return null
    val ordered = if (prioritizePositionId != null) {
        markers.sortedByDescending { it.position.id == prioritizePositionId }
    } else {
        markers
    }

    var bestArrowOrBoxHit: HitRunningPositionResult? = null
    var bestLineHit: HitRunningPositionResult? = null

    for (m in ordered) {
        val isPrioritized = prioritizePositionId != null && m.position.id == prioritizePositionId
        val dTip = hypot(pos.x - m.openTipX, pos.y - m.openY)
        val dBody = hypot(pos.x - m.openTipX, pos.y - m.openBodyCenterY)
        val arrowDist = min(dTip, dBody)
        val effectiveArrowRadius = if (isPrioritized) arrowRadiusPx * 1.35f else arrowRadiusPx

        // 1. Direct hit on the entry arrow on the candle
        if (arrowDist <= effectiveArrowRadius) {
            val effectiveDist = if (isPrioritized) arrowDist * 0.35f else arrowDist * 0.7f
            if (bestArrowOrBoxHit == null || effectiveDist < bestArrowOrBoxHit.distancePx) {
                bestArrowOrBoxHit = HitRunningPositionResult(m, effectiveDist)
            }
            continue
        }

        // 2. Direct hit on the left inline "BUY/SELL 0.01, +X.XX USD" label button area or right-axis "[29734.23]" price tag box
        val dyAbove = m.openY - pos.y
        val isOnLeftTradeLabel = pos.x in 0f..(chartWidthPx * 0.58f) &&
            dyAbove in (-lineRadiusPx * 0.85f)..(lineRadiusPx * 1.55f)
        val isOnRightAxisPriceBox = pos.x >= (chartWidthPx - lineRadiusPx * 0.5f) &&
            abs(pos.y - m.openY) <= lineRadiusPx * 0.95f

        if (isOnLeftTradeLabel || isOnRightAxisPriceBox) {
            val labelCenterY = if (isOnLeftTradeLabel) m.openY - (lineRadiusPx * 0.32f) else m.openY
            val boxDist = abs(pos.y - labelCenterY) * 0.45f
            val effectiveDist = if (isPrioritized) boxDist * 0.35f else boxDist
            if (bestArrowOrBoxHit == null || effectiveDist < bestArrowOrBoxHit.distancePx) {
                bestArrowOrBoxHit = HitRunningPositionResult(m, effectiveDist)
            }
            continue
        }

        // 3. Hit along the horizontal dashed running trade line across the chart
        val effectiveLineRadius = if (isPrioritized) lineRadiusPx * 1.35f else lineRadiusPx
        if (pos.x <= chartWidthPx && abs(pos.y - m.openY) <= effectiveLineRadius) {
            val lineDist = abs(pos.y - m.openY)
            val effectiveDist = if (isPrioritized) lineDist * 0.4f else lineDist
            if (bestLineHit == null || effectiveDist < bestLineHit.distancePx) {
                bestLineHit = HitRunningPositionResult(m, effectiveDist)
            }
        }
    }

    return bestArrowOrBoxHit ?: bestLineHit
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

private data class ResolvedChartRunningPosition(
    val position: TradePosition,
    val openCandleIdx: Int
)

private fun findCandleIndexForTimestamp(
    candles: List<Candle>,
    targetMs: Long,
    tfIntervalMs: Long
): Int {
    if (targetMs <= 0L || candles.isEmpty()) return -1
    val safeTf = tfIntervalMs.coerceAtLeast(60_000L)
    val clampedMs = com.example.data.SriLankaMarketHours.clampToLastOpenMarketTimestamp(targetMs)
    val targetBucket = (clampedMs / safeTf) * safeTf

    // Fast O(log N) binary search across chronological candles list
    var low = 0
    var high = candles.lastIndex
    var bestIdx = -1
    var bestDiff = Long.MAX_VALUE

    while (low <= high) {
        val mid = (low + high) ushr 1
        val ts = candles[mid].timestamp
        if (ts <= 0L) {
            low = mid + 1
            continue
        }
        val bucket = (ts / safeTf) * safeTf
        val diff = abs(bucket - targetBucket)
        if (diff < bestDiff) {
            bestDiff = diff
            bestIdx = mid
        }
        when {
            bucket < targetBucket -> low = mid + 1
            bucket > targetBucket -> high = mid - 1
            else -> return mid
        }
    }

    if (bestDiff <= safeTf * 4L) return bestIdx

    // Fallback scan only if timestamps were non-monotonic
    for (i in candles.indices) {
        val ts = candles[i].timestamp
        if (ts <= 0L) continue
        val bucket = (ts / safeTf) * safeTf
        if (bucket == targetBucket) {
            return i
        }
        val diff = abs(bucket - targetBucket)
        if (diff < bestDiff) {
            bestDiff = diff
            bestIdx = i
        }
    }
    return if (bestDiff <= safeTf * 4L) bestIdx else -1
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
    showGrid: Boolean = true,
    showSymbolDescription: Boolean = true,
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
    onSaveRunningPositionEdit: ((
        position: TradePosition,
        isBuy: Boolean,
        openPrice: Double,
        openTime: String
    ) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope()
    val localView = LocalView.current

    // Hold viewport state in primitive State objects and read them ONLY inside Canvas / pointerInput
    // so dragging, flinging, or zooming NEVER triggers recomposition of the CandlestickChart Composable tree!
    val panOffsetState = remember(quote.symbol, timeframeLabel) { mutableFloatStateOf(initialViewportState.panOffsetPx) }
    val zoomScaleState = remember(quote.symbol, timeframeLabel) { mutableFloatStateOf(initialViewportState.zoomScale) }
    val priceAxisZoomState = remember(quote.symbol, timeframeLabel) { mutableFloatStateOf(initialViewportState.priceAxisZoom) }
    val verticalPanPriceOffsetState = remember(quote.symbol, timeframeLabel) { mutableDoubleStateOf(initialViewportState.verticalPanPriceOffset) }

    var isFit14Days by remember { mutableStateOf(fit14DaysMode) }
    var crosshairPos by remember { mutableStateOf<Offset?>(null) }
    var editingTradeState by remember(quote.symbol, timeframeLabel) {
        mutableStateOf<EditableHistoryTradeState?>(null)
    }
    var editingRunningPositionState by remember(quote.symbol, timeframeLabel) {
        mutableStateOf<EditableRunningPositionState?>(null)
    }

    val currentOnViewportStateChange by rememberUpdatedState(onViewportStateChange)
    fun syncViewportState() {
        currentOnViewportStateChange?.invoke(
            ChartViewportState(
                panOffsetPx = panOffsetState.floatValue,
                zoomScale = zoomScaleState.floatValue,
                priceAxisZoom = priceAxisZoomState.floatValue,
                verticalPanPriceOffset = verticalPanPriceOffsetState.doubleValue
            )
        )
    }

    LaunchedEffect(enableHistoryTradeMove) {
        if (!enableHistoryTradeMove) {
            editingTradeState = null
            editingRunningPositionState = null
        }
    }

    LaunchedEffect(positions, quote.symbol) {
        val currentEditPos = editingRunningPositionState
        if (currentEditPos != null && positions.none { it.id == currentEditPos.originalPosition.id }) {
            editingRunningPositionState = null
        }
    }

    val metrics = remember(quote.symbol, timeframeLabel) { ChartGestureMetrics() }
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

    val isMarketCurrentlyOpen = remember(nowMillis / 60_000L) {
        com.example.data.SriLankaMarketHours.isMarketOpen(nowMillis)
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
                panOffsetState.floatValue = 0f
                syncViewportState()
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

    // Fast caches so Canvas never allocates SimpleDateFormat/Date or MultiParagraph layouts during 60/120fps panning
    val formattedTimeCache = remember(timeframeLabel) { HashMap<Long, String>(512) }
    val priceAxisLayoutCache = remember(isDarkMode, quote.digits) { LruCache<String, TextLayoutResult>(256) }
    val timeAxisLayoutCache = remember(isDarkMode, timeframeLabel) { LruCache<String, TextLayoutResult>(256) }
    val badgeTextLayoutCache = remember(isDarkMode, quote.digits) { LruCache<String, TextLayoutResult>(128) }

    // Exact MT5 Dark Mode & Normal (Light) Mode colors from reference screenshots
    val bgColor = if (isDarkMode) Color(0xFF000000) else Color(0xFFFFFFFF)
    val gridColor = if (isDarkMode) Color(0xFF1F242D) else Color(0xFFEBEBEB)
    val axisBorderColor = if (isDarkMode) Color(0xFF2C313A) else Color(0xFFD5D7DB)
    val axisTextColor = if (isDarkMode) Color(0xFFE5E7EB) else Color(0xFF1A1A1A)

    val bullishColor = Color(0xFF26A69A)
    val bearishColor = Color(0xFFEF5350)
    val currentPriceTagColor = Color(0xFF26A69A)

    val historicalAggregatedCandles = remember(
        candles.size,
        candles.firstOrNull()?.timestamp,
        candles.lastOrNull()?.timestamp,
        isFit14Days
    ) {
        if (!isFit14Days || candles.size <= 110) {
            emptyList()
        } else {
            val historicalPart = candles.dropLast(1)
            val targetBars = 95
            val chunkSize = (historicalPart.size / targetBars).coerceAtLeast(2)
            historicalPart.chunked(chunkSize).map { chunk ->
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
        }
    }

    val displayCandles = remember(candles, isFit14Days, historicalAggregatedCandles) {
        if (!isFit14Days || candles.size <= 110 || historicalAggregatedCandles.isEmpty()) {
            candles
        } else {
            historicalAggregatedCandles + candles.last()
        }
    }

    val firstDisplayTs = displayCandles.firstOrNull()?.timestamp ?: 0L
    val lastDisplayTs = displayCandles.lastOrNull()?.timestamp ?: 0L

    // Keyed on candle count & boundary timestamps rather than the entire 20,000-element list reference
    // so live ticks on the forming candle never re-run timestamp parsing or index lookups!
    val resolvedHistoryMarkers = remember(
        displayCandles.size,
        firstDisplayTs,
        lastDisplayTs,
        activeSymbolHistory,
        tfIntervalMs
    ) {
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

    val resolvedRunningPositions = remember(
        displayCandles.size,
        firstDisplayTs,
        lastDisplayTs,
        activeSymbolPositions,
        tfIntervalMs
    ) {
        if (displayCandles.isEmpty() || activeSymbolPositions.isEmpty()) {
            emptyList()
        } else {
            val n = displayCandles.size
            activeSymbolPositions.map { pos ->
                val openMs = com.example.data.SriLankaMarketHours.parseMt5DateTimeMillis(pos.openTime)
                val idx = findCandleIndexForTimestamp(displayCandles, openMs, tfIntervalMs)
                val safeIdx = if (idx in 0 until n) idx else (n - 1).coerceAtLeast(0)
                ResolvedChartRunningPosition(position = pos, openCandleIdx = safeIdx)
            }
        }
    }

    // Smooth 60fps interpolation for the live forming candle & price line.
    // Keep State<Float> references and read `.value` inside Canvas so tick animations only redraw Canvas!
    val latestCandle = displayCandles.lastOrNull()
    val baseOpen = latestCandle?.open ?: quote.bid
    val activeTimestamp = latestCandle?.timestamp ?: 0L

    val pointMultiplier = when (quote.digits) {
        5 -> 100000.0
        3 -> 1000.0
        2 -> 100.0
        else -> 10000.0
    }

    val (animClosePointsState, animHighPointsState, animLowPointsState) = androidx.compose.runtime.key(quote.symbol, activeTimestamp) {
        val cPts = animateFloatAsState(
            targetValue = (((latestCandle?.close ?: baseOpen) - baseOpen) * pointMultiplier).toFloat(),
            animationSpec = tween(durationMillis = 180, easing = LinearEasing),
            visibilityThreshold = 0.001f,
            label = "animClosePoints"
        )
        val hPts = animateFloatAsState(
            targetValue = (((latestCandle?.high ?: baseOpen) - baseOpen) * pointMultiplier).toFloat(),
            animationSpec = tween(durationMillis = 180, easing = LinearEasing),
            visibilityThreshold = 0.001f,
            label = "animHighPoints"
        )
        val lPts = animateFloatAsState(
            targetValue = (((latestCandle?.low ?: baseOpen) - baseOpen) * pointMultiplier).toFloat(),
            animationSpec = tween(durationMillis = 180, easing = LinearEasing),
            visibilityThreshold = 0.001f,
            label = "animLowPoints"
        )
        Triple(cPts, hPts, lPts)
    }

    val animBidPointsState = androidx.compose.runtime.key(quote.symbol, activeTimestamp) {
        animateFloatAsState(
            targetValue = ((quote.bid - baseOpen) * pointMultiplier).toFloat(),
            animationSpec = tween(durationMillis = 180, easing = LinearEasing),
            visibilityThreshold = 0.001f,
            label = "animBidPoints"
        )
    }

    val currentBaseOpen by rememberUpdatedState(baseOpen)
    val currentPointMultiplier by rememberUpdatedState(pointMultiplier)
    val currentShowCrosshair by rememberUpdatedState(showCrosshair)
    val currentIsFit14Days by rememberUpdatedState(isFit14Days)
    val currentOnFit14DaysChange by rememberUpdatedState(onFit14DaysChange)
    val currentEnableHistoryTradeMove by rememberUpdatedState(enableHistoryTradeMove)
    val currentOnCanvasTouch by rememberUpdatedState(onCanvasTouch)
    val currentDisplayCandles by rememberUpdatedState(displayCandles)
    val currentResolvedRunningPositions by rememberUpdatedState(resolvedRunningPositions)
    val currentEditingTradeState by rememberUpdatedState(editingTradeState)
    val currentEditingRunningPositionState by rememberUpdatedState(editingRunningPositionState)
    val currentOnSaveHistoryTradeEdit by rememberUpdatedState(onSaveHistoryTradeEdit)
    val currentOnSaveRunningPositionEdit by rememberUpdatedState(onSaveRunningPositionEdit)

    fun getCandleLowHigh(idx: Int, candlesList: List<Candle>): Pair<Double, Double> {
        if (candlesList.isEmpty()) return Pair(quote.bid, quote.ask)
        val safeIdx = idx.coerceIn(0, candlesList.lastIndex)
        val c = candlesList[safeIdx]
        val isLast = safeIdx == candlesList.lastIndex
        val bOpen = currentBaseOpen
        val pMult = currentPointMultiplier
        val cOpen = if (isLast) bOpen else c.open
        val cClose = if (isLast) bOpen + (animClosePointsState.value.toDouble() / pMult) else c.close
        val cHigh = max(
            if (isLast) bOpen + (animHighPointsState.value.toDouble() / pMult) else c.high,
            max(cOpen, cClose)
        )
        val cLow = min(
            if (isLast) bOpen + (animLowPointsState.value.toDouble() / pMult) else c.low,
            min(cOpen, cClose)
        )
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
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(quote.symbol, timeframeLabel) {
                    val arrowHitRadiusPx = 42.dp.toPx()
                    val segmentHitRadiusPx = 24.dp.toPx()
                    val runningLineHitRadiusPx = 30.dp.toPx()
                    val dragActivationSlopPx = 6.dp.toPx()
                    val holdToEditDurationMs = 210L
                    var lastRightAxisTapTimeMs = 0L

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
                            return@awaitEachGesture
                        }

                        // 1. Check if touch is near a Running Trade (Open Position) or History Trade marker on the chart
                        val activeEdit = if (currentEnableHistoryTradeMove) currentEditingTradeState else null
                        val activeRunningEdit = if (currentEnableHistoryTradeMove) currentEditingRunningPositionState else null

                        val effectiveRunningMarkers = if (metrics.renderedRunningPositions.isNotEmpty()) {
                            metrics.renderedRunningPositions
                        } else {
                            val candlesNow = currentDisplayCandles
                            val nNow = candlesNow.size
                            val slotNow = metrics.totalCandleSlotPx.coerceAtLeast(1f)
                            currentResolvedRunningPositions.map { resolved ->
                                val pos = resolved.position
                                val isEdited = activeRunningEdit != null && activeRunningEdit.originalPosition.id == pos.id
                                val effBuy = if (isEdited && activeRunningEdit != null) activeRunningEdit.isBuy else pos.isBuy
                                val effOpenP = if (isEdited && activeRunningEdit != null) activeRunningEdit.openPrice else pos.openPrice
                                val safeIdx = if (isEdited && activeRunningEdit != null) {
                                    activeRunningEdit.openCandleIdx.coerceIn(0, (nNow - 1).coerceAtLeast(0))
                                } else {
                                    resolved.openCandleIdx.coerceIn(0, (nNow - 1).coerceAtLeast(0))
                                }
                                val ox = metrics.candleOriginXPx - ((nNow - 1 - safeIdx) * slotNow) + metrics.clampedPanPx
                                val oy = metrics.priceToY(effOpenP)
                                RenderedRunningPositionMarker(
                                    position = pos,
                                    isBuy = effBuy,
                                    openCandleIdx = safeIdx,
                                    openPrice = effOpenP,
                                    openTipX = ox,
                                    openY = oy,
                                    openBodyCenterY = oy
                                )
                            }
                        }

                        val hitRunningResult = if (currentEnableHistoryTradeMove) {
                            findHitRunningPosition(
                                pos = down.position,
                                markers = effectiveRunningMarkers,
                                arrowRadiusPx = arrowHitRadiusPx,
                                lineRadiusPx = runningLineHitRadiusPx,
                                chartWidthPx = metrics.chartWidthPx,
                                prioritizePositionId = activeRunningEdit?.originalPosition?.id
                            )
                        } else null

                        val hitResult = if (currentEnableHistoryTradeMove && down.position.x <= metrics.chartWidthPx) {
                            findHitHistoryMarker(
                                pos = down.position,
                                markers = metrics.renderedHistoryMarkers,
                                arrowRadiusPx = arrowHitRadiusPx,
                                segmentRadiusPx = segmentHitRadiusPx,
                                prioritizeTradeId = activeEdit?.originalTrade?.id
                            )
                        } else null

                        val preferRunningHit = hitRunningResult != null && (
                            activeEdit == null ||
                                (activeRunningEdit != null && hitRunningResult.marker.position.id == activeRunningEdit.originalPosition.id) ||
                                hitResult == null ||
                                hitRunningResult.distancePx <= hitResult.distancePx
                            )

                        // 2. Right-Side Price Axis vertical drag zoom & double-tap reset
                        val isTouchOnRightPriceAxis = down.position.x >= (metrics.chartWidthPx - 10.dp.toPx()) &&
                            down.position.y <= (metrics.chartHeightPx - 28.dp.toPx()) &&
                            !preferRunningHit

                        if (isTouchOnRightPriceAxis) {
                            val nowTapMs = System.currentTimeMillis()
                            if (nowTapMs - lastRightAxisTapTimeMs <= 320L) {
                                priceAxisZoomState.floatValue = 1.0f
                                verticalPanPriceOffsetState.doubleValue = 0.0
                                lastRightAxisTapTimeMs = 0L
                                syncViewportState()
                                return@awaitEachGesture
                            }
                            lastRightAxisTapTimeMs = nowTapMs

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                val panChange = event.calculatePan()
                                if (abs(panChange.y) > 0.05f) {
                                    val updatedZoom = (priceAxisZoomState.floatValue * exp(-panChange.y / 155f)).coerceIn(0.25f, 6.0f)
                                    priceAxisZoomState.floatValue = updatedZoom
                                    if (abs(updatedZoom - 1.0f) < 0.03f) {
                                        verticalPanPriceOffsetState.doubleValue = 0.0
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            syncViewportState()
                            return@awaitEachGesture
                        }

                        var draggingRunningPosition = false
                        var draggingHistoryTrade = false
                        var dragHandle = HistoryDragHandle.OPEN

                        val alreadyEditingHitRunning = preferRunningHit && hitRunningResult != null &&
                            activeRunningEdit != null && activeRunningEdit.originalPosition.id == hitRunningResult.marker.position.id
                        val alreadyEditingHitHistory = !preferRunningHit && hitResult != null &&
                            activeEdit != null && activeEdit.originalTrade.id == hitResult.marker.trade.id

                        // If user is ALREADY editing this specific trade, grab its handle immediately on touch-down.
                        // Otherwise, if a trade marker was touched while browsing the chart, disambiguate:
                        // - Immediate swipe/drag (< 210ms) -> Pan the chart smoothly without hijacking the gesture!
                        // - Tap OR press-and-hold (>= 210ms) -> Open Trade Edit Mode (and allow immediate dragging).
                        var initialPanCarryDelta = Offset.Zero
                        if (alreadyEditingHitRunning) {
                            draggingRunningPosition = true
                        } else if (alreadyEditingHitHistory && hitResult != null) {
                            dragHandle = hitResult.handle
                            editingTradeState = activeEdit?.copy(activeHandle = dragHandle)
                            draggingHistoryTrade = true
                        } else if ((preferRunningHit && hitRunningResult != null) || hitResult != null) {
                            val downTimeMs = System.currentTimeMillis()
                            var decidedChartPan = false
                            var decidedTradeEdit = false
                            var fingerReleasedAsTap = false

                            while (!decidedChartPan && !decidedTradeEdit && !fingerReleasedAsTap) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    fingerReleasedAsTap = true
                                    break
                                }
                                if (pressed.size >= 2) {
                                    decidedChartPan = true
                                    break
                                }
                                val primary = pressed.first()
                                val moveDist = hypot(
                                    primary.position.x - down.position.x,
                                    primary.position.y - down.position.y
                                )
                                val elapsedMs = System.currentTimeMillis() - downTimeMs
                                if (moveDist > dragActivationSlopPx) {
                                    if (elapsedMs < holdToEditDurationMs) {
                                        decidedChartPan = true
                                        initialPanCarryDelta = primary.position - down.position
                                    } else {
                                        decidedTradeEdit = true
                                    }
                                    break
                                } else if (elapsedMs >= holdToEditDurationMs) {
                                    decidedTradeEdit = true
                                    break
                                }
                            }

                            if (fingerReleasedAsTap || decidedTradeEdit) {
                                val candlesList = currentDisplayCandles
                                if (preferRunningHit && hitRunningResult != null) {
                                    val m = hitRunningResult.marker
                                    localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    val safeOpenIdx = if (m.openCandleIdx in candlesList.indices) {
                                        m.openCandleIdx
                                    } else {
                                        (candlesList.size - 1).coerceAtLeast(0)
                                    }
                                    val clampedOpenP = clampPriceStrictlyInsideCandle(m.openPrice, safeOpenIdx, candlesList)
                                    editingTradeState = null
                                    editingRunningPositionState = EditableRunningPositionState(
                                        originalPosition = m.position,
                                        isBuy = m.isBuy,
                                        openCandleIdx = safeOpenIdx,
                                        openPrice = clampedOpenP
                                    )
                                    if (decidedTradeEdit) {
                                        draggingRunningPosition = true
                                    } else {
                                        return@awaitEachGesture
                                    }
                                } else if (hitResult != null) {
                                    val m = hitResult.marker
                                    dragHandle = hitResult.handle
                                    localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    val safeOpenIdx = m.openCandleIdx.coerceIn(0, (candlesList.size - 1).coerceAtLeast(0))
                                    val safeCloseIdx = m.closeCandleIdx.coerceIn(0, (candlesList.size - 1).coerceAtLeast(0))
                                    val clampedOpenP = clampPriceStrictlyInsideCandle(m.openPrice, safeOpenIdx, candlesList)
                                    val clampedCloseP = clampPriceStrictlyInsideCandle(m.closePrice, safeCloseIdx, candlesList)

                                    editingRunningPositionState = null
                                    editingTradeState = EditableHistoryTradeState(
                                        originalTrade = m.trade,
                                        isBuy = m.isBuy,
                                        openCandleIdx = safeOpenIdx,
                                        closeCandleIdx = safeCloseIdx,
                                        openPrice = clampedOpenP,
                                        closePrice = clampedCloseP,
                                        activeHandle = dragHandle
                                    )
                                    if (decidedTradeEdit) {
                                        draggingHistoryTrade = true
                                    } else {
                                        return@awaitEachGesture
                                    }
                                }
                            }
                        }

                        if (draggingRunningPosition) {
                            val startRunningEdit = editingRunningPositionState ?: return@awaitEachGesture
                            val candlesInit = currentDisplayCandles
                            val startOpenIdx = startRunningEdit.openCandleIdx
                            val anchorTouchCandleIdx = xToNearestCandleIndex(down.position.x, candlesInit.size)
                            val touchedNearEntryArrow = hitRunningResult != null &&
                                abs(down.position.x - hitRunningResult.marker.openTipX) <= 44.dp.toPx() &&
                                down.position.x <= metrics.chartWidthPx
                            var hasMovedBeyondSlop = false

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) break

                                val primary = pressed.first()
                                val moveDist = hypot(
                                    primary.position.x - down.position.x,
                                    primary.position.y - down.position.y
                                )
                                if (moveDist >= dragActivationSlopPx) {
                                    hasMovedBeyondSlop = true
                                }

                                if (hasMovedBeyondSlop) {
                                    val curCandles = currentDisplayCandles
                                    if (curCandles.isNotEmpty()) {
                                        val rawFingerIdx = xToNearestCandleIndex(primary.position.x, curCandles.size)
                                        val targetIdx = if (touchedNearEntryArrow || primary.position.x in (metrics.chartWidthPx * 0.35f)..metrics.chartWidthPx) {
                                            rawFingerIdx
                                        } else {
                                            val deltaIdx = rawFingerIdx - anchorTouchCandleIdx
                                            (startOpenIdx + deltaIdx).coerceIn(0, curCandles.lastIndex)
                                        }
                                        val rawTouchPrice = metrics.yToPrice(primary.position.y)
                                        val currentState = editingRunningPositionState ?: startRunningEdit
                                        val newOpenPrice = clampPriceStrictlyInsideCandle(rawTouchPrice, targetIdx, curCandles)
                                        editingRunningPositionState = currentState.copy(
                                            openCandleIdx = targetIdx,
                                            openPrice = newOpenPrice
                                        )
                                    }
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
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
                            var hasMovedBeyondSlop = false

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) break

                                val primary = pressed.first()
                                val moveDist = hypot(
                                    primary.position.x - down.position.x,
                                    primary.position.y - down.position.y
                                )
                                if (moveDist >= dragActivationSlopPx) {
                                    hasMovedBeyondSlop = true
                                }

                                val curCandles = currentDisplayCandles
                                if (hasMovedBeyondSlop && curCandles.isNotEmpty()) {
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

                        // 3. Ultra-smooth 60/120fps chart pan + 2-finger pinch-to-zoom gesture loop
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        val initialPanAtGestureStart = panOffsetState.floatValue
                        var wasMultiTouchZoom = false

                        // Apply any initial pan delta carried over from trade-marker disambiguation
                        if (abs(initialPanCarryDelta.x) > 0.05f) {
                            panOffsetState.floatValue = (panOffsetState.floatValue + initialPanCarryDelta.x)
                                .coerceIn(metrics.minPanBound, metrics.maxPanBound)
                        }

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
                                    if (abs(initialPanAtGestureStart) <= 24f) {
                                        panOffsetState.floatValue = 0f
                                    }
                                }
                                if (abs(zoomChange - 1f) > 0.001f) {
                                    if (currentIsFit14Days) {
                                        isFit14Days = false
                                        currentOnFit14DaysChange?.invoke(false)
                                    }
                                    val oldZoom = zoomScaleState.floatValue
                                    val newZoom = (oldZoom * zoomChange).coerceIn(0.14f, 3.2f)
                                    val actualRatio = newZoom / oldZoom
                                    zoomScaleState.floatValue = newZoom

                                    val curPan = panOffsetState.floatValue
                                    if (abs(curPan) <= 24f) {
                                        panOffsetState.floatValue = 0f
                                    } else {
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        val focusX = if (centroid != Offset.Unspecified) centroid.x else metrics.chartWidthPx * 0.5f
                                        val originX = metrics.candleOriginXPx
                                        panOffsetState.floatValue = ((originX + curPan - focusX) * actualRatio - originX + focusX)
                                            .coerceIn(metrics.minPanBound, metrics.maxPanBound)
                                    }
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (!wasMultiTouchZoom) {
                                val primaryChange = pressedChanges.firstOrNull()
                                if (primaryChange != null) {
                                    velocityTracker.addPosition(primaryChange.uptimeMillis, primaryChange.position)
                                }

                                if (abs(panChange.x) > 0.01f) {
                                    if (currentIsFit14Days) {
                                        val defaultSlotPx = 9.5.dp.toPx()
                                        if (defaultSlotPx > 0f && metrics.totalCandleSlotPx > 0f) {
                                            zoomScaleState.floatValue = (metrics.totalCandleSlotPx / defaultSlotPx).coerceIn(0.14f, 3.2f)
                                        }
                                        isFit14Days = false
                                        currentOnFit14DaysChange?.invoke(false)
                                    }

                                    panOffsetState.floatValue = (panOffsetState.floatValue + panChange.x)
                                        .coerceIn(metrics.minPanBound, metrics.maxPanBound)

                                    // When panning horizontally, gently relax any manual vertical offset back toward 0.0
                                    // so historical candles never get stuck off-center at the top/bottom of the viewport
                                    if (abs(panChange.x) > abs(panChange.y) * 1.15f && abs(verticalPanPriceOffsetState.doubleValue) > 1e-8) {
                                        val relaxFactor = (1.0 - (abs(panChange.x).toDouble() / 900.0).coerceIn(0.0, 0.12))
                                        verticalPanPriceOffsetState.doubleValue *= relaxFactor
                                    }
                                }

                                // Only apply vertical pan when the user's finger movement is predominantly vertical
                                // or when vertical price axis zoom is active, preventing accidental vertical jitter during horizontal swipes
                                val isIntentionalVerticalDrag = abs(panChange.y) > abs(panChange.x) * 1.35f ||
                                    abs(priceAxisZoomState.floatValue - 1.0f) > 0.05f
                                if (isIntentionalVerticalDrag && abs(panChange.y) > 0.2f && down.position.y < metrics.chartHeightPx) {
                                    val priceDelta = (panChange.y / metrics.gridSpanPx.coerceAtLeast(1f)) * metrics.totalGridPriceSpanVal
                                    verticalPanPriceOffsetState.doubleValue = (verticalPanPriceOffsetState.doubleValue + priceDelta).coerceIn(
                                        -metrics.totalGridPriceSpanVal * 1.2,
                                        metrics.totalGridPriceSpanVal * 1.2
                                    )
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        val velocityX = velocityTracker.calculateVelocity().x
                        if (!wasMultiTouchZoom && abs(velocityX) > 95f && !currentIsFit14Days) {
                            flingJob = coroutineScope.launch {
                                var v = velocityX.coerceIn(-7500f, 7500f)
                                var lastTime = 0L
                                while (isActive && abs(v) > 14f) {
                                    withFrameNanos { nanos ->
                                        val dt = if (lastTime == 0L) 0.016f else ((nanos - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                                        lastTime = nanos
                                        val curPan = panOffsetState.floatValue
                                        val nextPan = (curPan + v * dt).coerceIn(metrics.minPanBound, metrics.maxPanBound)
                                        if (abs(nextPan - curPan) < 0.02f) {
                                            v = 0f
                                        } else {
                                            panOffsetState.floatValue = nextPan
                                            if (abs(verticalPanPriceOffsetState.doubleValue) > 1e-8) {
                                                verticalPanPriceOffsetState.doubleValue *= (1.0 - (abs(v * dt).toDouble() / 1100.0).coerceIn(0.0, 0.08))
                                            }
                                            v *= exp(-3.6f * dt)
                                        }
                                    }
                                }
                                syncViewportState()
                            }
                        } else {
                            syncViewportState()
                        }
                    }
                }
        ) {
            if (displayCandles.isEmpty()) return@Canvas

            val panOffsetPx = panOffsetState.floatValue
            val zoomScale = zoomScaleState.floatValue
            val priceAxisZoom = priceAxisZoomState.floatValue
            val verticalPanPriceOffset = verticalPanPriceOffsetState.doubleValue

            val animOpen = baseOpen
            val animClose = baseOpen + (animClosePointsState.value.toDouble() / pointMultiplier)
            val animHigh = max(baseOpen + (animHighPointsState.value.toDouble() / pointMultiplier), max(animOpen, animClose))
            val animLow = min(baseOpen + (animLowPointsState.value.toDouble() / pointMultiplier), min(animOpen, animClose))
            val animBid = baseOpen + (animBidPointsState.value.toDouble() / pointMultiplier)

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

            // Smooth edge-blended price range calculation:
            // Candles in the inner viewport have weight 1.0, while candles entering/exiting within `edgeBlendPx`
            // of the left/right edges smoothly blend in/out via a cubic Hermite (smoothstep) curve so minPrice/maxPrice
            // NEVER jump in discrete steps when crossing a candle boundary during horizontal panning!
            val edgeBlendPx = (totalCandleSlot * 4.5f).coerceAtLeast(24.dp.toPx())
            val floatLastIdx = (n - 1).toFloat()
            val idxAtLeftEdge = floatLastIdx - ((candleOriginX + clampedPan + edgeBlendPx) / totalCandleSlot)
            val idxAtRightEdge = floatLastIdx - ((candleOriginX + clampedPan - (chartWidth + edgeBlendPx)) / totalCandleSlot)

            val leftIndex = if (isFit14Days) 0 else floor(idxAtLeftEdge).toInt().coerceIn(0, n - 1)
            val rightIndex = if (isFit14Days) n - 1 else ceil(idxAtRightEdge).toInt().coerceIn(leftIndex, n - 1)

            var coreMinPrice = Double.MAX_VALUE
            var coreMaxPrice = -Double.MAX_VALUE
            var coreMinBodyPrice = Double.MAX_VALUE
            var coreMaxBodyPrice = -Double.MAX_VALUE

            val innerLeftX = edgeBlendPx * 0.65f
            val innerRightX = chartWidth - edgeBlendPx * 0.65f

            for (globalIdx in leftIndex..rightIndex) {
                val x = candleOriginX - ((n - 1 - globalIdx) * totalCandleSlot) + clampedPan
                if (x < innerLeftX || x > innerRightX) continue

                val c = displayCandles[globalIdx]
                val isLast = globalIdx == n - 1
                val cOpen = if (isLast) animOpen else c.open
                val cClose = if (isLast) animClose else c.close
                val cHigh = max(if (isLast) animHigh else c.high, max(cOpen, cClose))
                val cLow = min(if (isLast) animLow else c.low, min(cOpen, cClose))

                if (cLow > 0.0 && cHigh > 0.0) {
                    coreMinPrice = min(coreMinPrice, cLow)
                    coreMaxPrice = max(coreMaxPrice, cHigh)
                    coreMinBodyPrice = min(coreMinBodyPrice, min(cOpen, cClose))
                    coreMaxBodyPrice = max(coreMaxBodyPrice, max(cOpen, cClose))
                }
            }

            if (coreMinPrice == Double.MAX_VALUE || coreMaxPrice <= 0.0) {
                val fallbackIdx = ((leftIndex + rightIndex) / 2).coerceIn(0, n - 1)
                val fc = displayCandles[fallbackIdx]
                coreMinPrice = fc.low
                coreMaxPrice = fc.high
                coreMinBodyPrice = min(fc.open, fc.close)
                coreMaxBodyPrice = max(fc.open, fc.close)
            }

            var minPrice = coreMinPrice
            var maxPrice = coreMaxPrice
            var minBodyPrice = coreMinBodyPrice
            var maxBodyPrice = coreMaxBodyPrice

            for (globalIdx in leftIndex..rightIndex) {
                val x = candleOriginX - ((n - 1 - globalIdx) * totalCandleSlot) + clampedPan
                if (x in innerLeftX..innerRightX) continue
                if (x <= -edgeBlendPx || x >= chartWidth + edgeBlendPx) continue

                val rawT = if (x < innerLeftX) {
                    (x + edgeBlendPx) / (innerLeftX + edgeBlendPx)
                } else {
                    (chartWidth + edgeBlendPx - x) / (chartWidth + edgeBlendPx - innerRightX)
                }.coerceIn(0f, 1f)
                val weight = (rawT * rawT * (3f - 2f * rawT)).toDouble()

                val c = displayCandles[globalIdx]
                val isLast = globalIdx == n - 1
                val cOpen = if (isLast) animOpen else c.open
                val cClose = if (isLast) animClose else c.close
                val cHigh = max(if (isLast) animHigh else c.high, max(cOpen, cClose))
                val cLow = min(if (isLast) animLow else c.low, min(cOpen, cClose))

                if (cLow > 0.0 && cHigh > 0.0) {
                    val bLow = coreMinPrice + (cLow - coreMinPrice) * weight
                    val bHigh = coreMaxPrice + (cHigh - coreMaxPrice) * weight
                    val bodyL = min(cOpen, cClose)
                    val bodyH = max(cOpen, cClose)
                    val bBodyLow = coreMinBodyPrice + (bodyL - coreMinBodyPrice) * weight
                    val bBodyHigh = coreMaxBodyPrice + (bodyH - coreMaxBodyPrice) * weight
                    minPrice = min(minPrice, bLow)
                    maxPrice = max(maxPrice, bHigh)
                    minBodyPrice = min(minBodyPrice, bBodyLow)
                    maxBodyPrice = max(maxBodyPrice, bBodyHigh)
                }
            }

            val liveCandleX = candleOriginX + clampedPan
            if (liveCandleX >= -edgeBlendPx && liveCandleX <= chartWidth + edgeBlendPx) {
                val liveT = when {
                    liveCandleX < innerLeftX -> ((liveCandleX + edgeBlendPx) / (innerLeftX + edgeBlendPx)).coerceIn(0f, 1f)
                    liveCandleX > innerRightX -> ((chartWidth + edgeBlendPx - liveCandleX) / (chartWidth + edgeBlendPx - innerRightX)).coerceIn(0f, 1f)
                    else -> 1f
                }
                val liveW = (liveT * liveT * (3f - 2f * liveT)).toDouble()
                val candleSpan = (maxPrice - minPrice).coerceAtLeast(0.0001)
                if (abs(animBid - maxPrice) <= candleSpan * 3.0) {
                    minPrice = min(minPrice, minPrice + (animBid - minPrice) * liveW)
                    maxPrice = max(maxPrice, maxPrice + (animBid - maxPrice) * liveW)
                }
            }

            // Additional frame-to-frame temporal smoothing during horizontal panning so rapid flicks feel silky smooth
            if (metrics.smoothedMinPrice.isNaN() || metrics.smoothedMaxPrice.isNaN() || isFit14Days) {
                metrics.smoothedMinPrice = minPrice
                metrics.smoothedMaxPrice = maxPrice
                metrics.smoothedMinBodyPrice = minBodyPrice
                metrics.smoothedMaxBodyPrice = maxBodyPrice
            } else {
                val panDelta = if (metrics.lastPanForScalePx.isNaN()) 0f else abs(clampedPan - metrics.lastPanForScalePx)
                val alpha = if (panDelta > 0.05f) 0.28 else 0.45
                metrics.smoothedMinPrice += (minPrice - metrics.smoothedMinPrice) * alpha
                metrics.smoothedMaxPrice += (maxPrice - metrics.smoothedMaxPrice) * alpha
                metrics.smoothedMinBodyPrice += (minBodyPrice - metrics.smoothedMinBodyPrice) * alpha
                metrics.smoothedMaxBodyPrice += (maxBodyPrice - metrics.smoothedMaxBodyPrice) * alpha
            }
            metrics.lastPanForScalePx = clampedPan

            val smoothMinP = metrics.smoothedMinPrice
            val smoothMaxP = metrics.smoothedMaxPrice
            val smoothMinBodyP = metrics.smoothedMinBodyPrice
            val smoothMaxBodyP = metrics.smoothedMaxBodyPrice

            val rawRange = smoothMaxP - smoothMinP
            val minPointStep = 1.0 / pointMultiplier
            val effectiveRange = if (rawRange <= minPointStep * 10.0) {
                minPointStep * 180.0
            } else {
                rawRange
            }

            val basePaddingFactor = if (isFit14Days) 0.22 else 0.40
            val rawPadding = effectiveRange * basePaddingFactor
            val numHorizontalLines = 18
            val topGridMargin = 13.dp.toPx()
            val bottomGridMargin = 20.dp.toPx()
            val gridSpanPx = (chartHeight - topGridMargin - bottomGridMargin).coerceAtLeast(100f)

            // Keep totalGridPriceSpan and topGridPrice continuous (un-quantized) so priceToY never steps/snaps!
            val unzoomedGridPriceSpan = effectiveRange + rawPadding * 2.0
            val totalGridPriceSpan = (unzoomedGridPriceSpan / priceAxisZoom.toDouble())
                .coerceAtLeast(minPointStep * (numHorizontalLines - 1).toDouble())

            val wickMidPrice = (smoothMinP + smoothMaxP) * 0.5
            val bodyMidPrice = if (smoothMinBodyP < Double.MAX_VALUE && smoothMaxBodyP > 0.0) {
                (smoothMinBodyP + smoothMaxBodyP) * 0.5
            } else {
                wickMidPrice
            }
            val zoomInCenterBlend = ((priceAxisZoom - 1.0f) / 0.85f).coerceIn(0f, 1f).toDouble()
            val midPrice = wickMidPrice + (bodyMidPrice - wickMidPrice) * zoomInCenterBlend + verticalPanPriceOffset
            val topGridPrice = midPrice + totalGridPriceSpan * 0.5

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

            // Draw horizontal dashed price grid lines (moving smoothly in lockstep with candlesticks)
            // and crisp cached right-axis price labels
            val rowSpacingPx = gridSpanPx / (numHorizontalLines - 1).toFloat()
            if (!metrics.hasGridAnchor || (abs(clampedPan) < 0.5f && abs(verticalPanPriceOffset) < 1e-7)) {
                metrics.hasGridAnchor = true
                metrics.lastTopGridPrice = topGridPrice
                metrics.gridScrollOffsetYPx = 0f
            } else {
                val deltaTopPrice = topGridPrice - metrics.lastTopGridPrice
                metrics.lastTopGridPrice = topGridPrice
                if (totalGridPriceSpan > 0.0 && rowSpacingPx > 1f) {
                    val deltaYPx = ((deltaTopPrice / totalGridPriceSpan) * gridSpanPx.toDouble()).toFloat()
                    var nextOffset = (metrics.gridScrollOffsetYPx + deltaYPx) % rowSpacingPx
                    if (nextOffset < 0f) nextOffset += rowSpacingPx
                    metrics.gridScrollOffsetYPx = nextOffset
                }
            }
            val subRowOffsetPx = metrics.gridScrollOffsetYPx

            val priceAxisTextStyle = TextStyle(
                fontSize = 10.2.sp,
                color = axisTextColor,
                fontWeight = FontWeight.Normal
            )

            for (row in -1..numHorizontalLines) {
                val y = topGridMargin + row * rowSpacingPx + subRowOffsetPx
                if (y < 2.dp.toPx() || y > chartHeight - 2.dp.toPx()) continue

                if (showGrid) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 0.85.dp.toPx(),
                        pathEffect = gridDash
                    )
                }

                val priceAtRow = yToPrice(y)
                val labelY = y - 6.5.dp.toPx()
                if (labelY >= 1.dp.toPx() && labelY <= chartHeight - 16.dp.toPx()) {
                    val priceStr = String.format(Locale.US, "%.${quote.digits}f", priceAtRow)
                    var layout = priceAxisLayoutCache.get(priceStr)
                    if (layout == null) {
                        layout = textMeasurer.measure(text = priceStr, style = priceAxisTextStyle)
                        priceAxisLayoutCache.put(priceStr, layout)
                    }
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(chartWidth + 4.5.dp.toPx(), labelY)
                    )
                }
            }

            // Draw vertical dashed time grid lines and bottom-axis timestamps moving smoothly with clampedPan
            val lastCandleTs = displayCandles.last().timestamp
            val firstCandleTs = displayCandles.first().timestamp
            val baseGridX = firstVertGridX + clampedPan
            val minCol = floor((-baseGridX) / vertGridSpacingPx).toInt() - 1
            val maxCol = ceil((chartWidth - baseGridX) / vertGridSpacingPx).toInt() + 1

            val timeAxisTextStyle = TextStyle(
                fontSize = 10.sp,
                color = axisTextColor,
                fontWeight = FontWeight.Normal
            )

            for (col in minCol..maxCol) {
                val x = baseGridX + col * vertGridSpacingPx
                if (x < 1.dp.toPx() || x > chartWidth - 1.dp.toPx()) continue

                if (showGrid) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, chartHeight),
                        strokeWidth = 0.85.dp.toPx(),
                        pathEffect = gridDash
                    )
                }

                if (col.mod(3) == 0) {
                    val gridCandleIdx = round(
                        (n - 1).toFloat() - ((candleOriginX + clampedPan - x) / totalCandleSlot)
                    ).toInt()
                    val tsAtGrid = when {
                        gridCandleIdx in 0 until n -> displayCandles[gridCandleIdx].timestamp
                        gridCandleIdx >= n && lastCandleTs > 0L -> lastCandleTs + (gridCandleIdx - (n - 1)).toLong() * tfIntervalMs
                        gridCandleIdx < 0 && firstCandleTs > 0L -> firstCandleTs - (0 - gridCandleIdx).toLong() * tfIntervalMs
                        else -> lastCandleTs
                    }
                    val timeLabel = if (tsAtGrid > 0L) {
                        formattedTimeCache.getOrPut(tsAtGrid) {
                            axisDateFormat.format(Date(tsAtGrid))
                        }
                    } else {
                        displayCandles.last().time
                    }
                    var textLayout = timeAxisLayoutCache.get(timeLabel)
                    if (textLayout == null) {
                        textLayout = textMeasurer.measure(text = timeLabel, style = timeAxisTextStyle)
                        timeAxisLayoutCache.put(timeLabel, textLayout)
                    }
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
                    val maPath = metrics.reusableMaPath
                    maPath.reset()
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
                // MT5 Trade History Markers on Chart
                // =========================================================================
                val arrowScale = ((totalCandleSlot - 8.0.dp.toPx()) / (7.5.dp.toPx())).coerceIn(0f, 1f)
                val headHalfW = 4.0.dp.toPx() + (2.25.dp.toPx() * arrowScale)
                val headH = 4.6.dp.toPx() + (3.2.dp.toPx() * arrowScale)
                val shaftHalfW = 2.4.dp.toPx() + (0.95.dp.toPx() * arrowScale)
                val totalH = 7.1.dp.toPx() + (4.7.dp.toPx() * arrowScale)
                val haloStrokeW = 1.1.dp.toPx() + (0.45.dp.toPx() * arrowScale)
                val innerBorderW = 0.5.dp.toPx() + (0.15.dp.toPx() * arrowScale)

                fun drawMt5DealArrow(tipX: Float, tipY: Float, isUpArrow: Boolean) {
                    val arrowPath = metrics.reusableArrowPath
                    arrowPath.reset()
                    if (isUpArrow) {
                        arrowPath.moveTo(tipX, tipY)
                        arrowPath.lineTo(tipX - headHalfW, tipY + headH)
                        arrowPath.lineTo(tipX - shaftHalfW, tipY + headH)
                        arrowPath.lineTo(tipX - shaftHalfW, tipY + totalH)
                        arrowPath.lineTo(tipX + shaftHalfW, tipY + totalH)
                        arrowPath.lineTo(tipX + shaftHalfW, tipY + headH)
                        arrowPath.lineTo(tipX + headHalfW, tipY + headH)
                        arrowPath.close()
                    } else {
                        arrowPath.moveTo(tipX, tipY)
                        arrowPath.lineTo(tipX - headHalfW, tipY - headH)
                        arrowPath.lineTo(tipX - shaftHalfW, tipY - headH)
                        arrowPath.lineTo(tipX - shaftHalfW, tipY - totalH)
                        arrowPath.lineTo(tipX + shaftHalfW, tipY - totalH)
                        arrowPath.lineTo(tipX + shaftHalfW, tipY - headH)
                        arrowPath.lineTo(tipX + headHalfW, tipY - headH)
                        arrowPath.close()
                    }

                    val fillColor = if (isUpArrow) Color(0xFF2F88EC) else Color(0xFFE84E4C)
                    val borderColor = if (isUpArrow) Color(0xFF1F6BC4) else Color(0xFFC03937)

                    drawPath(
                        path = arrowPath,
                        color = Color.White,
                        style = Stroke(width = haloStrokeW)
                    )
                    drawPath(
                        path = arrowPath,
                        color = fillColor
                    )
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
                val activeRunningEdit = editingRunningPositionState
                metrics.renderedHistoryMarkers.clear()
                metrics.renderedRunningPositions.clear()

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
                val drawnArrowSpots = metrics.drawnArrowSpots
                drawnArrowSpots.clear()
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

                    drawDealArrowDeduplicated(
                        tipX = rm.openTipX,
                        tipY = rm.openTipY,
                        isUpArrow = rm.isBuy,
                        forceDraw = isBeingEdited
                    )

                    drawDealArrowDeduplicated(
                        tipX = rm.closeTipX,
                        tipY = rm.closeTipY,
                        isUpArrow = !rm.isBuy,
                        forceDraw = isBeingEdited
                    )
                }

                // Draw Open Trade Dashed Lines (SL, TP, Entry) ON TOP OF Candlesticks
                val openTradeDashEffect = PathEffect.dashPathEffect(
                    floatArrayOf(3.5.dp.toPx(), 2.5.dp.toPx()),
                    0f
                )
                activeSymbolPositions.forEach { pos ->
                    val isBeingEdited = activeRunningEdit != null && activeRunningEdit.originalPosition.id == pos.id
                    val effectiveIsBuy = if (isBeingEdited && activeRunningEdit != null) activeRunningEdit.isBuy else pos.isBuy
                    val effectiveOpenPrice = if (isBeingEdited && activeRunningEdit != null) activeRunningEdit.openPrice else pos.openPrice
                    val liveClosePrice = if (effectiveIsBuy) quote.bid else quote.ask
                    val effectiveProfit = if (isBeingEdited) {
                        onCalculateHistoryProfit?.invoke(
                            effectiveIsBuy,
                            pos.lots,
                            effectiveOpenPrice,
                            liveClosePrice
                        ) ?: pos.profit
                    } else {
                        pos.profit
                    }

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

                    val openY = priceToY(effectiveOpenPrice)
                    if (openY in -20.dp.toPx()..(chartHeight + 20.dp.toPx())) {
                        val tradeDashLineColor = if (!effectiveIsBuy || effectiveProfit < 0.0) {
                            Color(0xFFA94438)
                        } else {
                            Color(0xFF0D62C6)
                        }

                        drawLine(
                            color = tradeDashLineColor,
                            start = Offset(0f, openY),
                            end = Offset(chartWidth, openY),
                            strokeWidth = if (isBeingEdited) 1.6.dp.toPx() else 1.1.dp.toPx(),
                            pathEffect = openTradeDashEffect
                        )
                    }
                }

                // Draw the entry deal arrow for each currently open position on top of candlesticks & dashed line
                resolvedRunningPositions.forEach { resolved ->
                    val pos = resolved.position
                    val isBeingEdited = activeRunningEdit != null && activeRunningEdit.originalPosition.id == pos.id
                    val effectiveIsBuy = if (isBeingEdited && activeRunningEdit != null) activeRunningEdit.isBuy else pos.isBuy
                    val effectiveOpenPrice = if (isBeingEdited && activeRunningEdit != null) activeRunningEdit.openPrice else pos.openPrice
                    val safeOpenIdx = if (isBeingEdited && activeRunningEdit != null) {
                        activeRunningEdit.openCandleIdx.coerceIn(0, (n - 1).coerceAtLeast(0))
                    } else {
                        resolved.openCandleIdx.coerceIn(0, (n - 1).coerceAtLeast(0))
                    }
                    val openX = candleOriginX - ((n - 1 - safeOpenIdx) * totalCandleSlot) + clampedPan
                    val openY = priceToY(effectiveOpenPrice)
                    val openBodyCenterY = if (effectiveIsBuy) openY + totalH * 0.5f else openY - totalH * 0.5f

                    metrics.renderedRunningPositions.add(
                        RenderedRunningPositionMarker(
                            position = pos,
                            isBuy = effectiveIsBuy,
                            openCandleIdx = safeOpenIdx,
                            openPrice = effectiveOpenPrice,
                            openTipX = openX,
                            openY = openY,
                            openBodyCenterY = openBodyCenterY
                        )
                    )

                    if (openX >= -40.dp.toPx() && openX <= chartWidth + 40.dp.toPx()) {
                        if (isBeingEdited) {
                            val openGuideColor = if (effectiveIsBuy) Color(0xFF0D62C6) else Color(0xFFD32F2F)
                            drawCircle(
                                color = openGuideColor.copy(alpha = 0.24f),
                                radius = 14.dp.toPx(),
                                center = Offset(openX, openBodyCenterY)
                            )
                            drawCircle(
                                color = openGuideColor,
                                radius = 14.dp.toPx(),
                                center = Offset(openX, openBodyCenterY),
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                        }
                        drawDealArrowDeduplicated(
                            tipX = openX,
                            tipY = openY,
                            isUpArrow = effectiveIsBuy,
                            forceDraw = true
                        )
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
            val activeRunningEditForAxis = editingRunningPositionState
            val slTpTagStyle = TextStyle(
                fontSize = 9.5.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            activeSymbolPositions.forEach { pos ->
                val isBeingEdited = activeRunningEditForAxis != null && activeRunningEditForAxis.originalPosition.id == pos.id
                val effectiveIsBuy = if (isBeingEdited && activeRunningEditForAxis != null) activeRunningEditForAxis.isBuy else pos.isBuy
                val effectiveOpenPrice = if (isBeingEdited && activeRunningEditForAxis != null) activeRunningEditForAxis.openPrice else pos.openPrice
                val liveClosePrice = if (effectiveIsBuy) quote.bid else quote.ask
                val effectiveProfit = if (isBeingEdited) {
                    onCalculateHistoryProfit?.invoke(
                        effectiveIsBuy,
                        pos.lots,
                        effectiveOpenPrice,
                        liveClosePrice
                    ) ?: pos.profit
                } else {
                    pos.profit
                }

                pos.sl?.let { slPrice ->
                    if (slPrice > 0.0) {
                        val slY = priceToY(slPrice)
                        if (slY in 0f..chartHeight) {
                            val slColor = Color(0xFFD32F2F)
                            val slStr = String.format(Locale.US, "%.${quote.digits}f", slPrice)
                            val key = "SL_$slStr"
                            var layout = badgeTextLayoutCache.get(key)
                            if (layout == null) {
                                layout = textMeasurer.measure(text = slStr, style = slTpTagStyle)
                                badgeTextLayoutCache.put(key, layout)
                            }
                            drawRect(
                                color = slColor,
                                topLeft = Offset(chartWidth, slY - 8.dp.toPx()),
                                size = Size(rightAxisWidth, 16.dp.toPx())
                            )
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(chartWidth + 4.dp.toPx(), slY - 6.dp.toPx())
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
                            val key = "TP_$tpStr"
                            var layout = badgeTextLayoutCache.get(key)
                            if (layout == null) {
                                layout = textMeasurer.measure(text = tpStr, style = slTpTagStyle)
                                badgeTextLayoutCache.put(key, layout)
                            }
                            drawRect(
                                color = tpColor,
                                topLeft = Offset(chartWidth, tpY - 8.dp.toPx()),
                                size = Size(rightAxisWidth, 16.dp.toPx())
                            )
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(chartWidth + 4.dp.toPx(), tpY - 6.dp.toPx())
                            )
                        }
                    }
                }

                val openY = priceToY(effectiveOpenPrice)
                if (openY in 0f..chartHeight) {
                    val isRedTag = !effectiveIsBuy || effectiveProfit < 0.0
                    val tagBorderColor = if (isRedTag) {
                        Color(0xFFA94438)
                    } else {
                        Color(0xFF0D62C6)
                    }
                    val tagHeight = 16.dp.toPx()
                    val tagTop = (openY - tagHeight / 2f).coerceIn(0f, chartHeight - tagHeight)
                    val openPriceStr = String.format(Locale.US, "%.${quote.digits}f", effectiveOpenPrice)
                    val key = "OPEN_${isRedTag}_$openPriceStr"
                    var layout = badgeTextLayoutCache.get(key)
                    if (layout == null) {
                        layout = textMeasurer.measure(
                            text = openPriceStr,
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = tagBorderColor,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        badgeTextLayoutCache.put(key, layout)
                    }

                    drawRect(
                        color = Color.White,
                        topLeft = Offset(chartWidth + 0.5.dp.toPx(), tagTop),
                        size = Size(rightAxisWidth - 1.dp.toPx(), tagHeight)
                    )
                    drawRect(
                        color = tagBorderColor,
                        topLeft = Offset(chartWidth + 0.5.dp.toPx(), tagTop),
                        size = Size(rightAxisWidth - 1.dp.toPx(), tagHeight),
                        style = Stroke(width = if (isBeingEdited) 1.5.dp.toPx() else 1.dp.toPx())
                    )
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), tagTop + 1.dp.toPx())
                    )
                }
            }

            // Current Ask Line (Red #EF5350) & Bid Line (Teal #26A69A) with Right-Axis Badges
            val spreadDiff = (quote.ask - quote.bid).coerceAtLeast(0.0)
            val animAsk = animBid + spreadDiff
            val askY = priceToY(animAsk)
            val bidY = priceToY(animBid)
            val askTagHeight = 16.dp.toPx()
            val isLiveMarketOrOpenTrade = activeSymbolPositions.isNotEmpty() || isMarketCurrentlyOpen

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

            // Draw Open Trade Inline Text (e.g. "BUY 0.01, +0.30 USD") on top of candlesticks, arrows, and Bid/Ask lines
            clipRect(left = 0f, top = 0f, right = chartWidth, bottom = chartHeight) {
                activeSymbolPositions.forEach { pos ->
                    val isBeingEdited = activeRunningEditForAxis != null && activeRunningEditForAxis.originalPosition.id == pos.id
                    val effectiveIsBuy = if (isBeingEdited && activeRunningEditForAxis != null) activeRunningEditForAxis.isBuy else pos.isBuy
                    val effectiveOpenPrice = if (isBeingEdited && activeRunningEditForAxis != null) activeRunningEditForAxis.openPrice else pos.openPrice
                    val liveClosePrice = if (effectiveIsBuy) quote.bid else quote.ask
                    val effectiveProfit = if (isBeingEdited) {
                        onCalculateHistoryProfit?.invoke(
                            effectiveIsBuy,
                            pos.lots,
                            effectiveOpenPrice,
                            liveClosePrice
                        ) ?: pos.profit
                    } else {
                        pos.profit
                    }

                    val openY = priceToY(effectiveOpenPrice)
                    if (openY in -20.dp.toPx()..(chartHeight + 20.dp.toPx())) {
                        val sideTextColor = if (effectiveIsBuy) Color(0xFF0D62C6) else Color(0xFFB71C1C)
                        val pnlTextColor = if (effectiveProfit >= 0.0) Color(0xFF0D62C6) else Color(0xFFB71C1C)

                        val typeStr = if (effectiveIsBuy) "BUY" else "SELL"
                        val lotsStr = String.format(Locale.US, "%.2f", pos.lots)
                        val pnlSign = if (effectiveProfit >= 0.0) "+" else ""
                        val pnlStr = "$pnlSign${String.format(Locale.US, "%.2f", effectiveProfit)} USD"
                        val cacheKey = "INLINE_${typeStr}_${lotsStr}_$pnlStr"

                        var measuredLabel = badgeTextLayoutCache.get(cacheKey)
                        if (measuredLabel == null) {
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
                            measuredLabel = textMeasurer.measure(text = annotatedTradeLabel)
                            badgeTextLayoutCache.put(cacheKey, measuredLabel)
                        }
                        val labelY = openY - measuredLabel.size.height - 1.dp.toPx()

                        if (isBeingEdited) {
                            drawRoundRect(
                                color = sideTextColor.copy(alpha = 0.12f),
                                topLeft = Offset(5.dp.toPx(), labelY - 2.dp.toPx()),
                                size = Size(
                                    measuredLabel.size.width + 8.dp.toPx(),
                                    measuredLabel.size.height + 4.dp.toPx()
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            drawRoundRect(
                                color = sideTextColor.copy(alpha = 0.75f),
                                topLeft = Offset(5.dp.toPx(), labelY - 2.dp.toPx()),
                                size = Size(
                                    measuredLabel.size.width + 8.dp.toPx(),
                                    measuredLabel.size.height + 4.dp.toPx()
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }

                        drawText(
                            textLayoutResult = measuredLabel,
                            topLeft = Offset(8.dp.toPx(), labelY)
                        )
                    }
                }
            }

            if (bidY in 0f..chartHeight) {
                val badgeWhiteStyle = TextStyle(
                    fontSize = 10.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                val askStr = String.format(Locale.US, "%.${quote.digits}f", animAsk)
                val bidStr = String.format(Locale.US, "%.${quote.digits}f", animBid)

                var askLayout = badgeTextLayoutCache.get("ASK_$askStr")
                if (askLayout == null) {
                    askLayout = textMeasurer.measure(text = askStr, style = badgeWhiteStyle)
                    badgeTextLayoutCache.put("ASK_$askStr", askLayout)
                }
                var bidLayout = badgeTextLayoutCache.get("BID_$bidStr")
                if (bidLayout == null) {
                    bidLayout = textMeasurer.measure(text = bidStr, style = badgeWhiteStyle)
                    badgeTextLayoutCache.put("BID_$bidStr", bidLayout)
                }

                if (isLiveMarketOrOpenTrade) {
                    val twoLineTagHeight = 28.dp.toPx()
                    val bidTagTop = (bidY - twoLineTagHeight / 2f).coerceIn(askTagHeight, chartHeight - twoLineTagHeight)
                    val rawAskTagTop = (askY - askTagHeight / 2f).coerceIn(0f, chartHeight - twoLineTagHeight - askTagHeight)
                    val askTagTop = min(rawAskTagTop, bidTagTop - askTagHeight)

                    var cdLayout = badgeTextLayoutCache.get("CD_$candleCountdownStr")
                    if (cdLayout == null) {
                        cdLayout = textMeasurer.measure(text = candleCountdownStr, style = badgeWhiteStyle)
                        badgeTextLayoutCache.put("CD_$candleCountdownStr", cdLayout)
                    }

                    // Red Ask Badge on Right Axis right above the 2-line Green Bid Badge
                    drawRect(
                        color = bearishColor,
                        topLeft = Offset(chartWidth, askTagTop),
                        size = Size(rightAxisWidth, askTagHeight)
                    )
                    drawText(
                        textLayoutResult = askLayout,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), askTagTop + 1.dp.toPx())
                    )

                    // 2-Line Teal Bid + Candle Countdown Badge on Right Axis
                    drawRect(
                        color = currentPriceTagColor,
                        topLeft = Offset(chartWidth, bidTagTop),
                        size = Size(rightAxisWidth, twoLineTagHeight)
                    )
                    drawText(
                        textLayoutResult = bidLayout,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), bidTagTop + 1.5.dp.toPx())
                    )
                    drawText(
                        textLayoutResult = cdLayout,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), bidTagTop + 14.dp.toPx())
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
                    drawText(
                        textLayoutResult = askLayout,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), askTagTop + 1.dp.toPx())
                    )

                    // Teal Bid Badge on Right Axis
                    drawRect(
                        color = currentPriceTagColor,
                        topLeft = Offset(chartWidth, bidTagTop),
                        size = Size(rightAxisWidth, askTagHeight)
                    )
                    drawText(
                        textLayoutResult = bidLayout,
                        topLeft = Offset(chartWidth + 4.dp.toPx(), bidTagTop + 1.dp.toPx())
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
            if (showSymbolDescription && quote.description.isNotBlank()) {
                Text(
                    text = quote.description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextCol,
                    lineHeight = 15.sp,
                    modifier = Modifier.testTag("chart_symbol_description_text")
                )
            }
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
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 6.dp, end = 58.dp, bottom = 26.dp)
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFFD0D7DE),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { /* Consume stray touches on card background so they never hit chart */ }
                    }
                    .testTag("chart_history_edit_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp)
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
                                    .defaultMinSize(minWidth = 36.dp, minHeight = 34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE9ECEF))
                                    .clickable {
                                        localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        editingTradeState = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("chart_history_cancel_edit_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✕",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF343A40)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: Handle Selector (Entry / Exit / Both) + BUY/SELL Toggle Button right next to Save Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Drag Handle Mode Pills
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            listOf(
                                HistoryDragHandle.OPEN to "Entry",
                                HistoryDragHandle.CLOSE to "Exit",
                                HistoryDragHandle.BOTH to "Both"
                            ).forEach { (handleMode, label) ->
                                val selected = editState.activeHandle == handleMode
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 36.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            if (selected) Color(0xFFE7F1FF) else Color(0xFFF1F3F5)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) Color(0xFF0D62C6) else Color(0xFFDADCE0),
                                            shape = RoundedCornerShape(7.dp)
                                        )
                                        .clickable {
                                            localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            currentEditingTradeState?.let { latest ->
                                                editingTradeState = latest.copy(activeHandle = handleMode)
                                            }
                                        }
                                        .padding(horizontal = 9.dp, vertical = 7.dp)
                                        .testTag("chart_history_handle_${label.lowercase(Locale.US)}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color(0xFF0D62C6) else Color(0xFF495057)
                                    )
                                }
                            }
                        }

                        // Right: Order Type BUY / SELL Change Button right next to Save Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            // BUY / SELL Toggle Button right next to Save button
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 68.dp, minHeight = 38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(orderSideColor)
                                    .clickable {
                                        localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        currentEditingTradeState?.let { latest ->
                                            editingTradeState = latest.copy(isBuy = !latest.isBuy)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("chart_history_toggle_buy_sell_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (editState.isBuy) "⇄ BUY" else "⇄ SELL",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Save Button
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 64.dp, minHeight = 38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF198754))
                                    .clickable {
                                        localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        val latestEdit = currentEditingTradeState
                                        val curCandles = currentDisplayCandles
                                        if (latestEdit != null && curCandles.isNotEmpty()) {
                                            val safeOpenIdx = latestEdit.openCandleIdx.coerceIn(0, curCandles.lastIndex)
                                            val safeCloseIdx = latestEdit.closeCandleIdx.coerceIn(safeOpenIdx, curCandles.lastIndex)
                                            val finalOpenPrice = clampPriceStrictlyInsideCandle(latestEdit.openPrice, safeOpenIdx, curCandles)
                                            val finalClosePrice = clampPriceStrictlyInsideCandle(latestEdit.closePrice, safeCloseIdx, curCandles)

                                            val origOpenMs = com.example.data.SriLankaMarketHours.parseMt5DateTimeMillis(latestEdit.originalTrade.openTime)
                                            val origCloseMs = com.example.data.SriLankaMarketHours.parseMt5DateTimeMillis(latestEdit.originalTrade.closeTime)
                                            val openSec = if (origOpenMs > 0L) (origOpenMs / 1000L % 60L).coerceIn(2L, 54L) else (5..24).random().toLong()
                                            val prevCloseSec = if (origCloseMs > 0L) (origCloseMs / 1000L % 60L) else -1L

                                            val openCandleBaseMs = (curCandles[safeOpenIdx].timestamp / 60_000L) * 60_000L
                                            val closeCandleBaseMs = (curCandles[safeCloseIdx].timestamp / 60_000L) * 60_000L

                                            val newOpenMs: Long
                                            val newCloseMs: Long
                                            if (closeCandleBaseMs <= openCandleBaseMs) {
                                                val s1 = min(openSec, 22L)
                                                val minS2 = (s1 + 4L).coerceAtMost(56L).toInt()
                                                val candidates = (minS2..58).filter { it.toLong() != prevCloseSec && it.toLong() != s1 }
                                                val s2 = (if (candidates.isNotEmpty()) candidates.random() else (minS2..58).random()).toLong()
                                                newOpenMs = openCandleBaseMs + s1 * 1000L
                                                newCloseMs = openCandleBaseMs + s2 * 1000L
                                            } else {
                                                val candidates = (1..58).filter { it.toLong() != prevCloseSec && it.toLong() != openSec }
                                                val randomCloseSec = (if (candidates.isNotEmpty()) candidates.random() else (1..58).random()).toLong()
                                                newOpenMs = openCandleBaseMs + openSec * 1000L
                                                newCloseMs = closeCandleBaseMs + randomCloseSec * 1000L
                                            }

                                            val newOpenTimeStr = com.example.data.SriLankaMarketHours.formatHistoryTimestamp(newOpenMs)
                                            val newCloseTimeStr = com.example.data.SriLankaMarketHours.formatHistoryTimestamp(newCloseMs)

                                            currentOnSaveHistoryTradeEdit?.invoke(
                                                latestEdit.originalTrade,
                                                latestEdit.isBuy,
                                                finalOpenPrice,
                                                finalClosePrice,
                                                newOpenTimeStr,
                                                newCloseTimeStr
                                            )
                                        }
                                        editingTradeState = null
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("chart_history_save_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Save",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // Floating Running Trade (Open Position) Move & Edit Control Bar
        // Appears when the user long-presses ("obagena idapuwama") a running trade on the chart.
        // Includes:
        // - Live auto-calculated floating profit & moved candle entry price
        // - BUY / SELL order type change button right next to the Save button
        // - Save button that auto-updates the Running Trade & Account Equity/Margin
        // =========================================================================
        editingRunningPositionState?.let { editPosState ->
            val liveClosePrice = if (editPosState.isBuy) quote.bid else quote.ask
            val liveProfit = onCalculateHistoryProfit?.invoke(
                editPosState.isBuy,
                editPosState.originalPosition.lots,
                editPosState.openPrice,
                liveClosePrice
            ) ?: run {
                val diff = if (editPosState.isBuy) {
                    liveClosePrice - editPosState.openPrice
                } else {
                    editPosState.openPrice - liveClosePrice
                }
                round(diff * editPosState.originalPosition.lots * 100.0 * 100.0) / 100.0
            }

            val openPriceFormatted = String.format(Locale.US, "%.${quote.digits}f", editPosState.openPrice)
            val closePriceFormatted = String.format(Locale.US, "%.${quote.digits}f", liveClosePrice)
            val profitFormatted = String.format(
                Locale.US,
                "%s%.2f USD",
                if (liveProfit >= 0.0) "+" else "",
                liveProfit
            )
            val profitColor = if (liveProfit >= 0.0) Color(0xFF0D62C6) else Color(0xFFD32F2F)
            val orderSideColor = if (editPosState.isBuy) Color(0xFF0D62C6) else Color(0xFFD32F2F)

            Surface(
                color = Color(0xFFFDFDFE),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 6.dp, end = 58.dp, bottom = 26.dp)
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFFD0D7DE),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { /* Consume stray touches on card background so they never hit chart */ }
                    }
                    .testTag("chart_running_edit_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    // Row 1: Running Trade Summary (Order Side + Lots, Open -> Live Price, Live Floating Profit, Cancel Button)
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
                                text = "${if (editPosState.isBuy) "buy" else "sell"} ${String.format(Locale.US, "%.2f", editPosState.originalPosition.lots)}",
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
                                    .defaultMinSize(minWidth = 36.dp, minHeight = 34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE9ECEF))
                                    .clickable {
                                        localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        editingRunningPositionState = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("chart_running_cancel_edit_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✕",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF343A40)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: Running Trade Mode Badge + BUY/SELL Toggle Button right next to Save Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFFE7F1FF))
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF0D62C6),
                                    shape = RoundedCornerShape(7.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Running Entry",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D62C6)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            // BUY / SELL Toggle Button right next to Save button
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 68.dp, minHeight = 38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(orderSideColor)
                                    .clickable {
                                        localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        currentEditingRunningPositionState?.let { latest ->
                                            editingRunningPositionState = latest.copy(isBuy = !latest.isBuy)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("chart_running_toggle_buy_sell_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (editPosState.isBuy) "⇄ BUY" else "⇄ SELL",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Save Button
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 64.dp, minHeight = 38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF198754))
                                    .clickable {
                                        localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        val latestEdit = currentEditingRunningPositionState
                                        val curCandles = currentDisplayCandles
                                        if (latestEdit != null && curCandles.isNotEmpty()) {
                                            val safeOpenIdx = latestEdit.openCandleIdx.coerceIn(0, curCandles.lastIndex)
                                            val finalOpenPrice = clampPriceStrictlyInsideCandle(latestEdit.openPrice, safeOpenIdx, curCandles)

                                            val origOpenMs = com.example.data.SriLankaMarketHours.parseMt5DateTimeMillis(latestEdit.originalPosition.openTime)
                                            val openSec = if (origOpenMs > 0L) (origOpenMs / 1000L % 60L).coerceIn(2L, 55L) else 15L
                                            val openCandleBaseMs = (curCandles[safeOpenIdx].timestamp / 60_000L) * 60_000L
                                            val newOpenMs = openCandleBaseMs + openSec * 1000L
                                            val newOpenTimeStr = com.example.data.SriLankaMarketHours.formatHistoryTimestamp(newOpenMs)

                                            currentOnSaveRunningPositionEdit?.invoke(
                                                latestEdit.originalPosition,
                                                latestEdit.isBuy,
                                                finalOpenPrice,
                                                newOpenTimeStr
                                            )
                                        }
                                        editingRunningPositionState = null
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("chart_running_save_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Save",
                                    fontSize = 12.5.sp,
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
