package com.example.ui.chart

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.model.Timeframe
import com.example.ui.components.CandlestickChart
import com.example.ui.components.MT5HamburgerButton
import com.example.ui.components.QuickTradeBar
import com.example.ui.theme.MT5Blue
import com.example.viewmodel.TradingViewModel
import kotlin.math.cos
import kotlin.math.sin

private val PreferredChartSymbolOrder = listOf(
    "EURUSD",
    "GBPUSD",
    "USDCHF",
    "USDJPY",
    "USDCAD",
    "AUDUSD",
    "NZDUSD",
    "AUDNZD",
    "AUDCAD",
    "NDX100",
    "XAUUSD",
    "BTCUSD"
)

@Composable
fun ChartScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val selectedQuote by viewModel.selectedQuote.collectAsState()
    val candles by viewModel.candles.collectAsState()
    val positions by viewModel.positions.collectAsState()
    val history by viewModel.history.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val quickTradeLot by viewModel.quickTradeLot.collectAsState()
    val showMA by viewModel.showMA.collectAsState()
    val showCrosshair by viewModel.showCrosshair.collectAsState()
    val quotes by viewModel.quotes.collectAsState()
    val showQuickTradeBar by viewModel.showQuickTradeBar.collectAsState()
    val fit14DaysMode by viewModel.fit14DaysMode.collectAsState()
    val chartHistoryMoveEnabled by viewModel.chartHistoryMoveEnabled.collectAsState()

    val symbolPositions = remember(positions, selectedQuote?.symbol) {
        positions.filter { it.symbol.equals(selectedQuote?.symbol, ignoreCase = true) }
    }
    val symbolHistory = remember(history, selectedQuote?.symbol) {
        history.filter { !it.isWithdrawal && it.symbol.equals(selectedQuote?.symbol, ignoreCase = true) }
    }

    var showTfBar by remember { mutableStateOf(false) }
    var showSymbolDropdown by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Order symbols so the top 10 match Screenshot 2 (INRUSD..AUDCAD) followed by NDX100, XAUUSD, BTCUSD, etc.
    val orderedChartSymbols = remember(quotes) {
        val byName = quotes.associateBy { it.symbol.uppercase() }
        val result = mutableListOf<String>()
        PreferredChartSymbolOrder.forEach { sym ->
            if (byName.containsKey(sym)) {
                result.add(sym)
            }
        }
        quotes.forEach { q ->
            val upper = q.symbol.uppercase()
            if (!result.contains(upper)) {
                result.add(upper)
            }
        }
        result
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            }
        }
    }

    val bgColor = Color(0xFFFFFFFF)
    val primaryIconColor = Color(0xFF3C4043)
    val topDividerColor = Color(0xFFE5E7EB)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        if (showTfBar) {
            // =========================================================================
            // MT5 Top Timeframe Bar matching Screenshot 1:
            // [ M1   M5   M15   M30   H1   H4   D1   W1   MN   ⚙ ]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(bgColor)
                    .padding(horizontal = 10.dp)
                    .testTag("timeframe_top_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Timeframe.values().forEach { tf ->
                    val isSelected = tf == selectedTimeframe
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.selectTimeframe(tf)
                                showTfBar = false
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .testTag("tf_option_${tf.label}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tf.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF0D6EFD) else Color(0xFF424548)
                        )
                    }
                }

                // Right-side Settings Gear Icon matching Screenshot 1
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showTfBar = false
                        }
                        .testTag("timeframe_settings_gear"),
                    contentAlignment = Alignment.Center
                ) {
                    MT5TimeframeGearIcon(color = Color(0xFF5F6368))
                }
            }
        } else {
            // =========================================================================
            // Standard Android MT5 Top Toolbar:
            // Left: [≡]   Right/Center group: [+   f~   M15   (O)   [|]]
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(bgColor)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Hamburger menu (3 horizontal lines)
                MT5HamburgerButton(
                    onClick = {
                        showSymbolDropdown = false
                        viewModel.openDrawer()
                    }
                )

                // Center & Right Action Icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 1. Crosshair Tool (+)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showSymbolDropdown = false
                                viewModel.toggleCrosshair()
                            }
                            .testTag("chart_crosshair_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        MT5CrosshairIcon(
                            color = if (showCrosshair) MT5Blue else primaryIconColor
                        )
                    }

                    // 2. Indicator 'f' with wave curve
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showSymbolDropdown = false
                                viewModel.toggleMA()
                            }
                            .testTag("chart_indicator_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        MT5IndicatorWaveIcon(
                            color = if (showMA) MT5Blue else primaryIconColor
                        )
                    }

                    // 3. Timeframe text selector ("M15") -> switches top bar into the M1..MN bar
                    Box {
                        Text(
                            text = selectedTimeframe.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryIconColor,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showSymbolDropdown = false
                                    showTfBar = true
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                .testTag("timeframe_selector")
                        )
                    }

                    // 4. Red/Blue Clock Icon (opens New Order with SL / TP)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showSymbolDropdown = false
                                viewModel.openNewOrderDialog(selectedQuote?.symbol)
                            }
                            .testTag("chart_new_order_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        MT5ClockOrderIcon()
                    }

                    // 5. One-Click Trading Toggle Button (opens/closes bottom SELL / BUY bar)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showSymbolDropdown = false
                                viewModel.toggleQuickTradeBar()
                            }
                            .testTag("one_click_trade_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        MT5OneClickTradeIcon(isActive = showQuickTradeBar)
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = topDividerColor
        )

        // Top One-Click Trading Bar (SELL / 0.01 / BUY) — positioned right below the toolbar matching Screenshot 3
        if (showQuickTradeBar && selectedQuote != null) {
            QuickTradeBar(
                quote = selectedQuote!!,
                currentLot = quickTradeLot,
                onLotChange = { viewModel.setQuickTradeLot(it) },
                onSellClick = { lot -> viewModel.executeQuickTrade(false, lot) },
                onBuyClick = { lot -> viewModel.executeQuickTrade(true, lot) },
                isDarkMode = false
            )
        }

        // Main Candlestick Chart Area (White background)
        Box(modifier = Modifier.weight(1f)) {
            selectedQuote?.let { q ->
                val savedViewportState = remember(q.symbol, selectedTimeframe.label) {
                    viewModel.getChartViewportState(q.symbol, selectedTimeframe.label)
                }
                CandlestickChart(
                    quote = q,
                    candles = candles,
                    positions = symbolPositions,
                    historyTrades = symbolHistory,
                    timeframeLabel = selectedTimeframe.label,
                    isDarkMode = false,
                    showMA = showMA,
                    maPeriod = 20,
                    showCrosshair = showCrosshair,
                    fit14DaysMode = fit14DaysMode,
                    onFit14DaysChange = { viewModel.setFit14DaysMode(it) },
                    enableHistoryTradeMove = chartHistoryMoveEnabled,
                    initialViewportState = savedViewportState,
                    onViewportStateChange = { updatedState ->
                        viewModel.updateChartViewportState(q.symbol, selectedTimeframe.label, updatedState)
                    },
                    onSymbolClick = {
                        showTfBar = false
                        showSymbolDropdown = !showSymbolDropdown
                    },
                    onMoreOptionsClick = {
                        showTfBar = false
                        showSymbolDropdown = false
                        showMoreMenu = true
                    },
                    onCanvasTouch = {
                        if (showTfBar) showTfBar = false
                        if (showSymbolDropdown) showSymbolDropdown = false
                    },
                    onCalculateHistoryProfit = { isBuy, lots, openPrice, closePrice ->
                        viewModel.calculateHistoricalTradeProfit(
                            symbol = q.symbol,
                            isBuy = isBuy,
                            lots = lots,
                            openPrice = openPrice,
                            closePrice = closePrice
                        )
                    },
                    onSaveHistoryTradeEdit = { trade, isBuy, openPrice, closePrice, openTime, closeTime ->
                        viewModel.saveOrUpdateHistoryTrade(
                            existingId = trade.id,
                            symbol = trade.symbol,
                            isBuy = isBuy,
                            lots = trade.lots,
                            openPrice = openPrice,
                            closePrice = closePrice,
                            openTime = openTime,
                            closeTime = closeTime,
                            sl = trade.sl,
                            tp = trade.tp,
                            customProfit = null,
                            customTicketId = trade.ticketId,
                            hitOutcome = null
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // =========================================================================
                // MT5 Floating Vertical Symbol Switcher Card matching Screenshot 2:
                // Rounded pill-corner card anchored at top-left below "EURUSD ▾ M15",
                // showing bold blue symbol codes separated by thin horizontal dividers.
                // =========================================================================
                if (showSymbolDropdown) {
                    Surface(
                        color = Color(0xFFF5F6F8),
                        shape = RoundedCornerShape(14.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 6.dp, top = 26.dp)
                            .width(96.dp)
                            .heightIn(max = 408.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = 0.6.dp,
                                color = Color(0xFFE0E3E8),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("chart_symbol_switcher_popup")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            orderedChartSymbols.forEachIndexed { index, sym ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clickable {
                                            viewModel.selectSymbol(sym)
                                            showSymbolDropdown = false
                                        }
                                        .padding(horizontal = 14.dp)
                                        .testTag("symbol_option_$sym"),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = sym,
                                        color = Color(0xFF1565C0),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp
                                    )
                                }
                                if (index < orderedChartSymbols.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.8.dp,
                                        color = Color(0xFFE2E5E9)
                                    )
                                }
                            }
                        }
                    }
                }

                // More Options ("•••") Menu anchored to bottom-right
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 36.dp)
                ) {
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(if (showQuickTradeBar) "Hide Buy / Sell Bar" else "Show Buy / Sell Bar")
                            },
                            onClick = {
                                viewModel.toggleQuickTradeBar()
                                showMoreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Order (with SL / TP)") },
                            onClick = {
                                showMoreMenu = false
                                viewModel.openNewOrderDialog(q.symbol)
                            }
                        )
                        symbolPositions.forEach { pos ->
                            DropdownMenuItem(
                                text = {
                                    Text("Close #${pos.id} (${if (pos.isBuy) "BUY" else "SELL"} ${pos.lots})")
                                },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.openClosePositionDialog(pos)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("User guide") },
                            onClick = {
                                showMoreMenu = false
                                viewModel.openSettingsPage()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = {
                                showMoreMenu = false
                                viewModel.openAboutPage()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MT5TimeframeGearIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val outerR = w * 0.44f
        val innerValleyR = w * 0.34f
        val centerHoleR = w * 0.14f
        val teeth = 8

        val gearPath = Path()
        val totalSteps = teeth * 4
        for (i in 0 until totalSteps) {
            val angleDeg = (i * 360f / totalSteps) - 90f
            val rad = Math.toRadians(angleDeg.toDouble())
            val r = if (i % 4 == 1 || i % 4 == 2) outerR else innerValleyR
            val x = cx + (r * cos(rad)).toFloat()
            val y = cy + (r * sin(rad)).toFloat()
            if (i == 0) {
                gearPath.moveTo(x, y)
            } else {
                gearPath.lineTo(x, y)
            }
        }
        gearPath.close()

        drawPath(
            path = gearPath,
            color = color,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawCircle(
            color = color,
            radius = centerHoleR,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
private fun MT5CrosshairIcon(color: Color) {
    Canvas(modifier = Modifier.size(21.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val gap = 3.2.dp.toPx()
        val stroke = 1.7.dp.toPx()

        // Top arm
        drawLine(color = color, start = Offset(cx, 1.dp.toPx()), end = Offset(cx, cy - gap), strokeWidth = stroke, cap = StrokeCap.Round)
        // Bottom arm
        drawLine(color = color, start = Offset(cx, cy + gap), end = Offset(cx, h - 1.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
        // Left arm
        drawLine(color = color, start = Offset(1.dp.toPx(), cy), end = Offset(cx - gap, cy), strokeWidth = stroke, cap = StrokeCap.Round)
        // Right arm
        drawLine(color = color, start = Offset(cx + gap, cy), end = Offset(w - 1.dp.toPx(), cy), strokeWidth = stroke, cap = StrokeCap.Round)
        // Center dot
        drawCircle(color = color, radius = 1.2.dp.toPx(), center = Offset(cx, cy))
    }
}

@Composable
private fun MT5IndicatorWaveIcon(color: Color) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val wavePath = Path().apply {
                moveTo(w * 0.08f, h * 0.72f)
                cubicTo(
                    w * 0.28f, h * 0.25f,
                    w * 0.65f, h * 0.85f,
                    w * 0.92f, h * 0.22f
                )
            }
            drawPath(
                path = wavePath,
                color = color,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = "f",
            fontSize = 19.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = color
        )
    }
}

@Composable
private fun MT5ClockOrderIcon() {
    val redColor = Color(0xFFE54538)
    val blueColor = Color(0xFF2B79E5)
    Canvas(modifier = Modifier.size(21.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        val inset = stroke / 2f

        // Left half arc (Red)
        drawArc(
            color = redColor,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(w - stroke, h - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )

        // Right half arc (Blue)
        drawArc(
            color = blueColor,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(w - stroke, h - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )

        // Clock hands inside
        val cx = w / 2f
        val cy = h / 2f
        drawLine(
            color = Color(0xFF5F6368),
            start = Offset(cx, cy),
            end = Offset(cx, cy - h * 0.24f),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF5F6368),
            start = Offset(cx, cy),
            end = Offset(cx + w * 0.20f, cy),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MT5OneClickTradeIcon(isActive: Boolean) {
    val redColor = Color(0xFFE54538)
    val blueColor = Color(0xFF2B79E5)
    Canvas(modifier = Modifier.size(width = 23.dp, height = 17.dp)) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())

        // Left half filled red
        clipRect(left = 0f, top = 0f, right = w / 2f, bottom = h) {
            drawRoundRect(
                color = redColor,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = corner
            )
        }

        // Right half filled blue
        clipRect(left = w / 2f, top = 0f, right = w, bottom = h) {
            drawRoundRect(
                color = blueColor,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = corner
            )
        }

        // Center inner rectangle outline
        val boxW = w * 0.44f
        val boxH = h * 0.42f
        drawRoundRect(
            color = Color.White.copy(alpha = if (isActive) 1.0f else 0.92f),
            topLeft = Offset((w - boxW) / 2f, (h - boxH) / 2f),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            style = Stroke(width = 1.3.dp.toPx())
        )
    }
}
