package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

enum class MT5Tab(val label: String) {
    QUOTES("Quotes"),
    CHART("Charts"),
    TRADE("Trade"),
    HISTORY("History"),
    SETTINGS("Messages")
}

@Composable
fun MT5BottomBar(
    currentTab: MT5Tab,
    onTabSelected: (MT5Tab) -> Unit,
    floatingPnL: Double = 0.00,
    accountBalance: Double = 0.00,
    hasOpenPositions: Boolean = false,
    isDarkMode: Boolean = false,
    hasActiveSelection: Boolean = true,
    modifier: Modifier = Modifier
) {
    val barBg = Color(0xFFFFFFFF)
    val dividerColor = Color(0xFFE5E7EB)
    val selectedBlue = Color(0xFF1A73E8)
    val inactiveColor = Color(0xFF3C4043)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HorizontalDivider(thickness = 0.6.dp, color = dividerColor)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MT5Tab.values().forEach { tab ->
                val isSelected = hasActiveSelection && (tab == currentTab)
                val contentColor = if (isSelected) selectedBlue else inactiveColor

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) }
                        .testTag("tab_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (tab) {
                        MT5Tab.QUOTES -> {
                            QuotesArrowsTabIcon(color = contentColor)
                        }
                        MT5Tab.CHART -> {
                            CandlestickTabIcon(color = contentColor)
                        }
                        MT5Tab.TRADE -> {
                            MT5TrendingNodesIcon(
                                color = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        MT5Tab.HISTORY -> {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = contentColor,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        MT5Tab.SETTINGS -> {
                            MessagesTabIcon(color = contentColor)
                        }
                    }

                    if (tab == MT5Tab.TRADE && hasOpenPositions) {
                        val isPositive = floatingPnL >= 0.0
                        val pillBg = if (isPositive) Color(0xFFE1F0FF) else Color(0xFFFCE8E6)
                        val pillTextColor = if (isPositive) Color(0xFF0A60C2) else Color(0xFFC62828)
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(pillBg)
                                .padding(horizontal = 10.dp, vertical = 1.dp)
                                .testTag("bottom_bar_trade_pnl_pill")
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.2f", floatingPnL),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = pillTextColor,
                                maxLines = 1
                            )
                        }
                    } else if (tab == MT5Tab.TRADE && currentTab != MT5Tab.QUOTES && accountBalance > 0.0) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEBEEF2))
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                                .testTag("bottom_bar_trade_balance_pill")
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.2f", accountBalance),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) selectedBlue else Color(0xFF5F6368),
                                maxLines = 1
                            )
                        }
                    } else {
                        Text(
                            text = tab.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotesArrowsTabIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Left Up Arrow (↑)
        val lx = w * 0.32f
        drawLine(
            color = color,
            start = Offset(lx, h * 0.20f),
            end = Offset(lx, h * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(lx - w * 0.14f, h * 0.36f),
            end = Offset(lx, h * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(lx + w * 0.14f, h * 0.36f),
            end = Offset(lx, h * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Right Down Arrow (↓)
        val rx = w * 0.68f
        drawLine(
            color = color,
            start = Offset(rx, h * 0.28f),
            end = Offset(rx, h * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(rx - w * 0.14f, h * 0.64f),
            end = Offset(rx, h * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(rx + w * 0.14f, h * 0.64f),
            end = Offset(rx, h * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CandlestickTabIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()

        // Left Candle (outlined hollow body + top/bottom wicks)
        val lx = w * 0.33f
        val bw = w * 0.22f
        drawLine(color = color, start = Offset(lx, h * 0.16f), end = Offset(lx, h * 0.32f), strokeWidth = stroke)
        drawRect(
            color = color,
            topLeft = Offset(lx - bw / 2f, h * 0.32f),
            size = Size(bw, h * 0.38f),
            style = Stroke(width = stroke)
        )
        drawLine(color = color, start = Offset(lx, h * 0.70f), end = Offset(lx, h * 0.86f), strokeWidth = stroke)

        // Right Candle (outlined hollow body + top/bottom wicks)
        val rx = w * 0.69f
        drawLine(color = color, start = Offset(rx, h * 0.12f), end = Offset(rx, h * 0.24f), strokeWidth = stroke)
        drawRect(
            color = color,
            topLeft = Offset(rx - bw / 2f, h * 0.24f),
            size = Size(bw, h * 0.48f),
            style = Stroke(width = stroke)
        )
        drawLine(color = color, start = Offset(rx, h * 0.72f), end = Offset(rx, h * 0.88f), strokeWidth = stroke)
    }
}

@Composable
private fun MessagesTabIcon(color: Color) {
    Box(
        modifier = Modifier.size(width = 28.dp, height = 23.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.7.dp.toPx()

            // Back/Left speech bubble
            val backPath = Path().apply {
                moveTo(w * 0.56f, h * 0.16f)
                lineTo(w * 0.10f, h * 0.16f)
                lineTo(w * 0.10f, h * 0.62f)
                lineTo(w * 0.24f, h * 0.62f)
                lineTo(w * 0.24f, h * 0.76f)
                lineTo(w * 0.38f, h * 0.62f)
            }
            drawPath(
                path = backPath,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Front/Right speech bubble
            val frontPath = Path().apply {
                moveTo(w * 0.38f, h * 0.34f)
                lineTo(w * 0.90f, h * 0.34f)
                lineTo(w * 0.90f, h * 0.78f)
                lineTo(w * 0.76f, h * 0.78f)
                lineTo(w * 0.76f, h * 0.92f)
                lineTo(w * 0.60f, h * 0.78f)
                lineTo(w * 0.38f, h * 0.78f)
                close()
            }
            drawPath(
                path = frontPath,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Red notification badge "1" at top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-2).dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFFE54D42)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "1",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 9.sp
            )
        }
    }
}
