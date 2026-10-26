package com.example.ui.trade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TradePosition
import com.example.ui.components.MT5HamburgerButton
import com.example.ui.theme.MT5Separator
import com.example.viewmodel.TradingViewModel
import java.util.Locale

private val MT5TradeBlue = Color(0xFF0A60C2)
private val MT5TradeRed = Color(0xFFC62828)

/**
 * Formats numbers with MT5's space thousands separator (e.g. 4917.16 -> "4 917.16", 28910.88 -> "28 910.88").
 */
fun formatMt5SpaceNumber(value: Double, decimals: Int = 2): String {
    val raw = String.format(Locale.US, "%.${decimals}f", value)
    val isNegative = raw.startsWith("-")
    val unsigned = if (isNegative) raw.substring(1) else raw
    val parts = unsigned.split(".")
    val intPart = parts[0]
    val fracPart = if (parts.size > 1) ".${parts[1]}" else ""
    val sb = StringBuilder()
    for (i in intPart.indices) {
        if (i > 0 && (intPart.length - i) % 3 == 0) {
            sb.append(' ')
        }
        sb.append(intPart[i])
    }
    return (if (isNegative) "-" else "") + sb.toString() + fracPart
}

@Composable
fun TradeScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val account by viewModel.account.collectAsState()
    val positions by viewModel.positions.collectAsState()
    val quotes by viewModel.quotes.collectAsState()
    val digitsMap = remember(quotes) { quotes.associate { it.symbol to it.digits } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // Android MT5 Top Bar matching Screenshots 1 & 2:
        // When positions are open: Left shows stacked "Trade" + "0.68 USD" (blue) or "-0.50 USD" (red)
        // Right shows [↓↑   [+]PageIcon]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MT5HamburgerButton(
                    onClick = { viewModel.openDrawer() }
                )
                Spacer(modifier = Modifier.width(18.dp))
                if (positions.isEmpty()) {
                    Text(
                        text = "Trade",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1C1C1E),
                        modifier = Modifier.testTag("net_floating_pnl")
                    )
                } else {
                    val pnl = account.floatingPnL
                    val pnlColor = if (pnl >= 0.0) MT5TradeBlue else MT5TradeRed
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Trade",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF1C1C1E),
                            lineHeight = 14.sp
                        )
                        Text(
                            text = "${formatMt5SpaceNumber(pnl, 2)} USD",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = pnlColor,
                            lineHeight = 22.sp,
                            modifier = Modifier.testTag("net_floating_pnl")
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (positions.isEmpty()) {
                    // Summary Card Icon (opens TopUp / Demo Balance dialog when empty)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { viewModel.openTopUpDialog() },
                        contentAlignment = Alignment.Center
                    ) {
                        MT5SummaryCardIcon(color = Color(0xFF3C4043))
                    }
                }

                // Sort Down/Up Arrows Icon (↓↑)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Sort positions */ },
                    contentAlignment = Alignment.Center
                ) {
                    MT5SortArrowsIcon(color = Color(0xFF3C4043))
                }

                // New Order Page+ Icon (opens New Order dialog)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.openNewOrderDialog() }
                        .testTag("add_trade_button"),
                    contentAlignment = Alignment.Center
                ) {
                    MT5NewOrderDocIcon(color = Color(0xFF3C4043))
                }
            }
        }

        // Account Summary Metrics with Dotted Leaders & Space-Separated Thousands
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.openTopUpDialog() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            DottedMetricRow(
                label = "Balance:",
                value = formatMt5SpaceNumber(account.balance, 2)
            )
            Spacer(modifier = Modifier.height(6.dp))
            DottedMetricRow(
                label = "Equity:",
                value = formatMt5SpaceNumber(account.equity, 2)
            )
            if (positions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                DottedMetricRow(
                    label = "Margin:",
                    value = formatMt5SpaceNumber(account.margin, 2)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            DottedMetricRow(
                label = "Free margin:",
                value = formatMt5SpaceNumber(account.freeMargin, 2)
            )
            if (positions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                DottedMetricRow(
                    label = "Margin Level (%):",
                    value = formatMt5SpaceNumber(account.marginLevel, 2)
                )
            }
        }

        // Open Positions Section
        if (positions.isEmpty()) {
            HorizontalDivider(thickness = 0.8.dp, color = Color(0xFFE5E7EB))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        } else {
            // "Positions" Section Header matching Screenshots 1 & 2
            HorizontalDivider(thickness = 0.7.dp, color = Color(0xFFE0E3E7))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(Color(0xFFF6F7F9))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Positions",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5F6368)
                )
                Text(
                    text = "•••",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF80868B),
                    letterSpacing = 1.sp
                )
            }
            HorizontalDivider(thickness = 0.7.dp, color = Color(0xFFE0E3E7))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                items(positions, key = { it.id }) { pos ->
                    val digits = digitsMap[pos.symbol]
                        ?: if (pos.symbol.contains("XAU") || pos.symbol.contains("BTC") || pos.symbol.contains("NDX")) 2 else 5
                    PositionRow(
                        position = pos,
                        digits = digits,
                        onClick = { viewModel.openClosePositionDialog(pos) }
                    )
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MT5Separator
                    )
                }
            }
        }
    }
}

@Composable
fun DottedMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = Color(0xFF1C1C1E),
            fontWeight = FontWeight.Bold
        )

        // Dotted leader line between label and value
        Canvas(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(14.dp)
        ) {
            val dotRadius = 1.1.dp.toPx()
            val step = 6.5.dp.toPx()
            val y = size.height * 0.72f
            var x = step
            while (x < size.width - step / 2f) {
                drawCircle(
                    color = Color(0xFFD0D5DD),
                    radius = dotRadius,
                    center = Offset(x, y)
                )
                x += step
            }
        }

        Text(
            text = value,
            fontSize = 15.sp,
            color = Color(0xFF1C1C1E),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PositionRow(
    position: TradePosition,
    digits: Int = 5,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("position_item_${position.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${position.symbol}, ",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1C1E)
                )
                val typeColor = if (position.isBuy) MT5TradeBlue else MT5TradeRed
                Text(
                    text = "${if (position.isBuy) "buy" else "sell"} ${String.format(Locale.US, "%.2f", position.lots)}",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = typeColor
                )
            }

            Text(
                text = "${formatMt5SpaceNumber(position.openPrice, digits)} \u2192 ${formatMt5SpaceNumber(position.currentPrice, digits)}",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6E7378),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        val isProfit = position.profit >= 0.0
        val profitColor = if (isProfit) MT5TradeBlue else MT5TradeRed

        Text(
            text = formatMt5SpaceNumber(position.profit, 2),
            fontSize = 16.5.sp,
            fontWeight = FontWeight.Bold,
            color = profitColor
        )
    }
}

@Composable
private fun MT5SummaryCardIcon(color: Color) {
    Canvas(modifier = Modifier.size(21.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.08f, h * 0.20f),
            size = Size(w * 0.84f, h * 0.60f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.08f, h * 0.42f),
            end = Offset(w * 0.92f, h * 0.42f),
            strokeWidth = stroke
        )
    }
}

@Composable
fun MT5SortArrowsIcon(color: Color) {
    Canvas(modifier = Modifier.size(21.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()

        // Left Down Arrow (↓)
        val lx = w * 0.30f
        drawLine(color = color, start = Offset(lx, h * 0.16f), end = Offset(lx, h * 0.84f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(lx - w * 0.16f, h * 0.66f), end = Offset(lx, h * 0.84f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(lx + w * 0.16f, h * 0.66f), end = Offset(lx, h * 0.84f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Right Up Arrow (↑)
        val rx = w * 0.70f
        drawLine(color = color, start = Offset(rx, h * 0.16f), end = Offset(rx, h * 0.84f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(rx - w * 0.16f, h * 0.34f), end = Offset(rx, h * 0.16f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(rx + w * 0.16f, h * 0.34f), end = Offset(rx, h * 0.16f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun MT5NewOrderDocIcon(color: Color) {
    Canvas(modifier = Modifier.size(21.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()

        // Document outline with folded top-right corner
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.16f, h * 0.10f)
            lineTo(w * 0.66f, h * 0.10f)
            lineTo(w * 0.84f, h * 0.28f)
            lineTo(w * 0.84f, h * 0.90f)
            lineTo(w * 0.16f, h * 0.90f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke)
        )

        // Plus '+' inside center
        val cx = w * 0.50f
        val cy = h * 0.54f
        val arm = w * 0.15f
        drawLine(color = color, start = Offset(cx - arm, cy), end = Offset(cx + arm, cy), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(cx, cy - arm), end = Offset(cx, cy + arm), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
