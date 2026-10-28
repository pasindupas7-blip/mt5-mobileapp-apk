package com.example.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SriLankaMarketHours
import com.example.model.TradeHitOutcome
import com.example.ui.components.MT5HamburgerButton
import com.example.ui.theme.MT5RobotoCondensedFontFamily
import com.example.ui.theme.MT5RobotoFontFamily
import com.example.ui.trade.MT5SortArrowsIcon
import com.example.viewmodel.TradingViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

enum class HistorySegment(val label: String) {
    POSITIONS("POSITIONS"),
    ORDERS("ORDERS"),
    DEALS("DEALS")
}

enum class HistoryPeriod(
    val title: String,
    val subtitle: String?
) {
    TODAY("Today", "25/10/2026"),
    LAST_WEEK("Last week", "18/10/2026 - 25/10/2026"),
    LAST_MONTH("Last month", "25/09/2026 - 25/10/2026"),
    LAST_3_MONTHS("Last 3 months", "25/07/2026 - 25/10/2026"),
    CUSTOM("Custom period", null)
}

private enum class LeftStripeType {
    NONE,
    GREEN,
    RED
}

private data class DisplayHistoryItem(
    val id: Long,
    val ticketId: String,
    val symbol: String,
    val isBuy: Boolean,
    val lots: Double,
    val openPrice: Double,
    val closePrice: Double,
    val openTime: String,
    val closeTime: String,
    val sl: Double?,
    val tp: Double?,
    val swap: Double = 0.00,
    val commission: Double = 0.00,
    val profit: Double,
    val digits: Int,
    val stripe: LeftStripeType,
    val isWithdrawal: Boolean = false,
    val comment: String = ""
)

@Composable
fun HistoryScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val userHistory by viewModel.history.collectAsState()
    val account by viewModel.account.collectAsState()
    val quotes by viewModel.quotes.collectAsState()

    var activeSegment by remember { mutableStateOf(HistorySegment.POSITIONS) }
    var selectedPeriod by remember { mutableStateOf(HistoryPeriod.LAST_3_MONTHS) }
    var showPeriodMenu by remember { mutableStateOf(false) }
    var expandedTradeIds by remember { mutableStateOf(setOf<Long>()) }
    var sortAscending by remember { mutableStateOf(true) }

    // Map all repository history items (including seeded NDX100 trades, user trades, and withdrawals)
    val allItems = remember(userHistory, quotes) {
        val quoteDigitsMap = quotes.associate { it.symbol to it.digits }
        userHistory.map { trade ->
            val digits = quoteDigitsMap[trade.symbol]
                ?: if (trade.symbol.contains("XAU") || trade.symbol.contains("BTC") || trade.symbol.contains("NDX")) 2 else 5
            val resolvedHitOutcome = viewModel.resolveTradeHitOutcome(trade)
            val stripeType = when {
                trade.isWithdrawal -> LeftStripeType.NONE
                resolvedHitOutcome == TradeHitOutcome.TP_HIT -> LeftStripeType.GREEN
                resolvedHitOutcome == TradeHitOutcome.SL_HIT -> LeftStripeType.RED
                else -> LeftStripeType.NONE
            }
            DisplayHistoryItem(
                id = trade.id,
                ticketId = trade.ticketId.ifBlank { "#${385000000L + trade.id}" },
                symbol = trade.symbol,
                isBuy = trade.isBuy,
                lots = trade.lots,
                openPrice = trade.openPrice,
                closePrice = trade.closePrice,
                openTime = trade.openTime.ifBlank { trade.closeTime },
                closeTime = trade.closeTime,
                sl = trade.sl,
                tp = trade.tp,
                swap = trade.swap,
                commission = trade.commission,
                profit = trade.profit,
                digits = digits,
                stripe = stripeType,
                isWithdrawal = trade.isWithdrawal,
                comment = trade.comment
            )
        }
    }

    val displayedItems = remember(selectedPeriod, allItems, sortAscending) {
        val filtered = allItems.filter { item ->
            val dateToFilter = item.closeTime.ifBlank { item.openTime }
            SriLankaMarketHours.isWithinHistoryPeriod(dateToFilter, selectedPeriod.name)
        }
        val sorted = filtered.sortedWith(
            compareBy<DisplayHistoryItem> {
                SriLankaMarketHours.parseMt5DateTimeMillis(it.closeTime.ifBlank { it.openTime })
            }.thenBy { it.id }
        )
        if (sortAscending) sorted else sorted.reversed()
    }

    val totalProfit = remember(displayedItems) {
        kotlin.math.round(displayedItems.filter { !it.isWithdrawal }.sumOf { it.profit } * 100.0) / 100.0
    }
    val totalWithdrawal = remember(displayedItems) {
        kotlin.math.round(displayedItems.filter { it.isWithdrawal }.sumOf { abs(it.profit) } * 100.0) / 100.0
    }
    val depositAmount = account.deposit
    val calculatedBalance = kotlin.math.round((depositAmount + totalProfit - totalWithdrawal) * 100.0) / 100.0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Android MT5 Top Bar:
            // Left: [≡  History / All symbols]   Right: [($)Sync   ↓↑   Calendar]
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "History",
                            fontFamily = MT5RobotoCondensedFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF111111),
                            lineHeight = 14.sp
                        )
                        Text(
                            text = "All symbols",
                            fontFamily = MT5RobotoCondensedFontFamily,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF6E7378),
                            lineHeight = 19.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Dollar-Sync Icon ($)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { viewModel.syncCloudNow() }
                            .testTag("history_cloud_sync_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        MT5DollarSyncIcon(color = Color(0xFF494D52))
                    }

                    // 2. Sort Down/Up Arrows Icon (↓↑) — toggles chronological date order
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { sortAscending = !sortAscending }
                            .testTag("history_sort_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        MT5SortArrowsIcon(color = if (sortAscending) Color(0xFF494D52) else Color(0xFF1A73E8))
                    }

                    // 3. Calendar Period Filter Icon
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showPeriodMenu = true }
                            .testTag("history_calendar_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        when (selectedPeriod) {
                            HistoryPeriod.LAST_3_MONTHS -> MT5CalendarMonthIcon(color = Color(0xFF494D52))
                            HistoryPeriod.LAST_WEEK -> MT5CalendarWeekIcon(color = Color(0xFF494D52))
                            HistoryPeriod.LAST_MONTH -> MT5CalendarMonthIcon(color = Color(0xFF494D52))
                            HistoryPeriod.CUSTOM -> MT5CalendarCustomIcon(color = Color(0xFF494D52))
                            HistoryPeriod.TODAY -> MT5CalendarTodayIcon(color = Color(0xFF494D52))
                        }
                    }
                }
            }

            // 3 Uppercase Material Tabs: POSITIONS | ORDERS | DEALS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color.White)
            ) {
                HistorySegment.values().forEach { segment ->
                    val isSelected = segment == activeSegment
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { activeSegment = segment },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = segment.label,
                            fontFamily = MT5RobotoCondensedFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isSelected) Color(0xFF111111) else Color(0xFF70757A)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isSelected) Color(0xFF1E88E5) else Color.Transparent)
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFE5E7EB))

            // Main History Content
            val isEmpty = displayedItems.isEmpty() || activeSegment == HistorySegment.ORDERS
            if (isEmpty) {
                // Exact Empty History graphic & label matching Screenshot 5
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MT5EmptyHistoryTrayIcon(
                            modifier = Modifier.size(116.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Empty history",
                            fontFamily = MT5RobotoFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    // Top Summary Block: Profit:, Deposit, Withdrawal (if > 0), Swap:, Commission:, Balance:
                    item(key = "history_summary_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)
                        ) {
                            HistoryDottedSummaryRow(
                                label = "Profit:",
                                value = String.format(Locale.US, "%.2f", totalProfit),
                                valueColor = if (totalProfit < 0) Color(0xFFB71C1C) else Color(0xFF0D62C6)
                            )
                            Spacer(modifier = Modifier.height(7.5.dp))
                            HistoryDottedSummaryRow(
                                label = "Deposit",
                                value = String.format(Locale.US, "%.2f", depositAmount),
                                valueColor = Color(0xFF111111)
                            )
                            if (totalWithdrawal > 0.0) {
                                Spacer(modifier = Modifier.height(7.5.dp))
                                HistoryDottedSummaryRow(
                                    label = "Withdrawal:",
                                    value = String.format(Locale.US, "-%.2f", totalWithdrawal),
                                    valueColor = Color(0xFFB71C1C)
                                )
                            }
                            Spacer(modifier = Modifier.height(7.5.dp))
                            HistoryDottedSummaryRow(
                                label = "Swap:",
                                value = "0.00",
                                valueColor = Color(0xFF111111)
                            )
                            Spacer(modifier = Modifier.height(7.5.dp))
                            HistoryDottedSummaryRow(
                                label = "Commission:",
                                value = "0.00",
                                valueColor = Color(0xFF111111)
                            )
                            Spacer(modifier = Modifier.height(7.5.dp))
                            HistoryDottedSummaryRow(
                                label = "Balance:",
                                value = String.format(Locale.US, "%.2f", calculatedBalance),
                                valueColor = Color(0xFF111111)
                            )
                        }
                        HorizontalDivider(thickness = 0.7.dp, color = Color(0xFFE5E7EB))
                    }

                    // Closed Position & Withdrawal Rows (tapping toggles expanded details; tapping again collapses)
                    items(displayedItems, key = { "${it.id}_${it.closeTime}" }) { item ->
                        val isExpanded = item.id in expandedTradeIds
                        MT5HistoryPositionRow(
                            item = item,
                            isExpanded = isExpanded,
                            onClick = {
                                expandedTradeIds = if (isExpanded) {
                                    expandedTradeIds - item.id
                                } else {
                                    expandedTradeIds + item.id
                                }
                            }
                        )
                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = Color(0xFFE8EAED)
                        )
                    }
                }
            }
        }

        // Top-Right Calendar Period Popup Menu matching Screenshot 3
        if (showPeriodMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showPeriodMenu = false }
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .width(262.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume inside clicks */ }
                        .testTag("history_period_popup"),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HistoryPeriod.values().forEachIndexed { index, period ->
                            val isSelected = period == selectedPeriod
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPeriod = period
                                        showPeriodMenu = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                                    .testTag("history_period_${period.name.lowercase()}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Period Calendar Icon
                                Box(
                                    modifier = Modifier.size(26.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (period) {
                                        HistoryPeriod.TODAY -> MT5CalendarTodayIcon(color = Color(0xFF494D52))
                                        HistoryPeriod.LAST_WEEK -> MT5CalendarWeekIcon(color = Color(0xFF494D52))
                                        HistoryPeriod.LAST_MONTH -> MT5CalendarMonthIcon(color = Color(0xFF494D52))
                                        HistoryPeriod.LAST_3_MONTHS -> MT5Calendar3Icon(color = Color(0xFF494D52))
                                        HistoryPeriod.CUSTOM -> MT5CalendarCustomIcon(color = Color(0xFF494D52))
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Title & Date Range Subtitle
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = period.title,
                                        fontFamily = MT5RobotoFontFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF1C1C1E)
                                    )
                                    if (period.subtitle != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = period.subtitle,
                                            fontFamily = MT5RobotoFontFamily,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = Color(0xFF70757A)
                                        )
                                    }
                                }

                                // Blue Checkmark on right if selected
                                if (isSelected) {
                                    Canvas(modifier = Modifier.size(18.dp)) {
                                        val w = size.width
                                        val h = size.height
                                        val checkPath = Path().apply {
                                            moveTo(w * 0.12f, h * 0.54f)
                                            lineTo(w * 0.40f, h * 0.80f)
                                            lineTo(w * 0.90f, h * 0.24f)
                                        }
                                        drawPath(
                                            path = checkPath,
                                            color = Color(0xFF1A73E8),
                                            style = Stroke(
                                                width = 1.8.dp.toPx(),
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                            }

                            if (index < HistoryPeriod.values().lastIndex) {
                                HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFEEEEEE))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDottedSummaryRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = label,
            fontFamily = MT5RobotoFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111111),
            lineHeight = 18.sp
        )

        // Dotted leader line (. . . . . . .)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .padding(horizontal = 8.dp)
        ) {
            val y = size.height - 3.dp.toPx()
            val step = 6.dp.toPx()
            var x = 2.dp.toPx()
            while (x < size.width - 2.dp.toPx()) {
                drawCircle(
                    color = Color(0xFFD5D8DC),
                    radius = 0.85.dp.toPx(),
                    center = Offset(x, y)
                )
                x += step
            }
        }

        Text(
            text = value,
            fontFamily = MT5RobotoFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun MT5HistoryPositionRow(
    item: DisplayHistoryItem,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val stripeColor = when (item.stripe) {
        LeftStripeType.GREEN -> Color(0xFF26A641)
        LeftStripeType.RED -> Color(0xFFE53935)
        LeftStripeType.NONE -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("history_item_${item.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left vertical colored indicator bar (Green = T/P Hit, Red = S/L Hit, Transparent = Manual Close / None)
        Box(
            modifier = Modifier
                .width(4.5.dp)
                .fillMaxHeight()
                .background(stripeColor)
                .testTag("history_stripe_${item.id}_${item.stripe.name.lowercase(Locale.US)}")
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
        ) {
            if (item.isWithdrawal) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Balance",
                        fontFamily = MT5RobotoCondensedFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111),
                        lineHeight = 19.sp,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = item.closeTime,
                        fontFamily = MT5RobotoCondensedFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF6E7378),
                        lineHeight = 16.sp,
                        modifier = Modifier.alignByBaseline()
                    )
                }

                Spacer(modifier = Modifier.height(1.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = item.comment.ifBlank { "Withdrawal" },
                        fontFamily = MT5RobotoCondensedFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6E7378),
                        lineHeight = 18.sp,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = String.format(Locale.US, "-%.2f", abs(item.profit)),
                        fontFamily = MT5RobotoFontFamily,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB71C1C),
                        lineHeight = 17.sp,
                        modifier = Modifier.alignByBaseline()
                    )
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.ticketId,
                            fontFamily = MT5RobotoCondensedFontFamily,
                            fontSize = 13.sp,
                            color = Color(0xFF70757A)
                        )
                        Text(
                            text = "Deal: ${item.comment.ifBlank { "Withdrawal" }}",
                            fontFamily = MT5RobotoCondensedFontFamily,
                            fontSize = 13.sp,
                            color = Color(0xFF70757A)
                        )
                    }
                }
            } else {
                // Line 1: Left = [NDX100, sell 0.01] (Roboto Condensed Bold 17sp) | Right = [2026.09.30 16:55:07] (Roboto Condensed Regular 13.5sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(
                        modifier = Modifier.alignByBaseline(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "${item.symbol}, ",
                            fontFamily = MT5RobotoCondensedFontFamily,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111111),
                            lineHeight = 19.sp,
                            modifier = Modifier.alignByBaseline()
                        )
                        val typeColor = if (item.isBuy) Color(0xFF0D62C6) else Color(0xFFB71C1C)
                        Text(
                            text = "${if (item.isBuy) "buy" else "sell"} ${String.format(Locale.US, "%.2f", item.lots)}",
                            fontFamily = MT5RobotoCondensedFontFamily,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeColor,
                            lineHeight = 19.sp,
                            modifier = Modifier.alignByBaseline()
                        )
                    }

                    Text(
                        text = item.closeTime,
                        fontFamily = MT5RobotoCondensedFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF6E7378),
                        lineHeight = 16.sp,
                        modifier = Modifier.alignByBaseline()
                    )
                }

                Spacer(modifier = Modifier.height(1.dp))

                // Line 2: Left = [30 453.85 → 30 516.23] (Roboto Condensed Bold 16.5sp) | Right = [-12.48] (Roboto Bold 14.5sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "${formatMT5HistoryPrice(item.openPrice, item.digits)} \u2192 ${formatMT5HistoryPrice(item.closePrice, item.digits)}",
                        fontFamily = MT5RobotoCondensedFontFamily,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6E7378),
                        lineHeight = 18.5.sp,
                        modifier = Modifier.alignByBaseline()
                    )

                    val profitColor = if (item.profit >= 0.0) Color(0xFF0D62C6) else Color(0xFFB71C1C)
                    Text(
                        text = String.format(Locale.US, "%.2f", item.profit),
                        fontFamily = MT5RobotoFontFamily,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = profitColor,
                        lineHeight = 17.sp,
                        modifier = Modifier.alignByBaseline()
                    )
                }

                // Expanded Detail Section matching Screenshot
                if (isExpanded) {
                    val detailColor = Color(0xFF70757A)
                    val detailFontSize = 13.sp
                    val slText = item.sl?.let { formatMT5HistoryPrice(it, item.digits) } ?: "\u2013"
                    val tpText = item.tp?.let { formatMT5HistoryPrice(it, item.digits) } ?: "\u2013"

                    Spacer(modifier = Modifier.height(7.dp))

                    // Detail Row 1: [#385193325]     |  [Open:          2026.07.28 17:11:04]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(0.46f)) {
                            Text(
                                text = item.ticketId,
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                        }
                        Spacer(modifier = Modifier.width(18.dp))
                        Row(
                            modifier = Modifier.weight(0.54f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Open:",
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                            Text(
                                text = item.openTime,
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Detail Row 2: [S / L:        27 625.57]  |  [Swap:                    0.00]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(0.46f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "S / L:",
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                            Text(
                                text = slText,
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                        }
                        Spacer(modifier = Modifier.width(18.dp))
                        Row(
                            modifier = Modifier.weight(0.54f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Swap:",
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f", item.swap),
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Detail Row 3: [T / P:        27 395.47]  |  [Commission:              0.00]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(0.46f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "T / P:",
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                            Text(
                                text = tpText,
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                        }
                        Spacer(modifier = Modifier.width(18.dp))
                        Row(
                            modifier = Modifier.weight(0.54f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Commission:",
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f", item.commission),
                                fontFamily = MT5RobotoCondensedFontFamily,
                                fontSize = detailFontSize,
                                fontWeight = FontWeight.Normal,
                                color = detailColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMT5HistoryPrice(price: Double, digits: Int): String {
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ' '
        decimalSeparator = '.'
    }
    val pattern = if (digits > 0) {
        "#,##0." + "0".repeat(digits)
    } else {
        "#,##0"
    }
    return DecimalFormat(pattern, symbols).format(price)
}

@Composable
private fun MT5EmptyHistoryTrayIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val trayColor = Color(0xFFDADCE0)

        val outerPath = Path().apply {
            moveTo(w * 0.20f, h * 0.14f)
            lineTo(w * 0.80f, h * 0.14f)
            lineTo(w * 0.94f, h * 0.52f)
            lineTo(w * 0.94f, h * 0.84f)
            quadraticTo(w * 0.94f, h * 0.92f, w * 0.86f, h * 0.92f)
            lineTo(w * 0.14f, h * 0.92f)
            quadraticTo(w * 0.06f, h * 0.92f, w * 0.06f, h * 0.84f)
            lineTo(w * 0.06f, h * 0.52f)
            close()
        }
        drawPath(path = outerPath, color = trayColor)

        val innerPath = Path().apply {
            moveTo(w * 0.25f, h * 0.21f)
            lineTo(w * 0.75f, h * 0.21f)
            lineTo(w * 0.86f, h * 0.52f)
            lineTo(w * 0.63f, h * 0.52f)
            arcTo(
                rect = Rect(
                    left = w * 0.37f,
                    top = h * 0.39f,
                    right = w * 0.63f,
                    bottom = h * 0.68f
                ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(w * 0.14f, h * 0.52f)
            close()
        }
        drawPath(path = innerPath, color = Color.White)
    }
}

@Composable
private fun MT5DollarSyncIcon(color: Color) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        drawArc(
            color = color,
            startAngle = -150f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 30f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        drawText(
            textMeasurer = textMeasurer,
            text = "$",
            topLeft = Offset(w * 0.34f, h * 0.18f),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

@Composable
private fun MT5CalendarTodayIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.18f),
            size = Size(w * 0.80f, h * 0.72f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke, join = StrokeJoin.Round)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.10f, h * 0.38f),
            end = Offset(w * 0.90f, h * 0.38f),
            strokeWidth = stroke
        )
        drawLine(color = color, start = Offset(w * 0.30f, h * 0.08f), end = Offset(w * 0.30f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.70f, h * 0.08f), end = Offset(w * 0.70f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)

        val r = 1.1.dp.toPx()
        for (col in 0..3) {
            for (row in 0..1) {
                drawCircle(
                    color = color,
                    radius = r,
                    center = Offset(w * (0.25f + col * 0.16f), h * (0.54f + row * 0.18f))
                )
            }
        }
    }
}

@Composable
private fun MT5CalendarWeekIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.18f),
            size = Size(w * 0.80f, h * 0.72f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke, join = StrokeJoin.Round)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.10f, h * 0.38f),
            end = Offset(w * 0.90f, h * 0.38f),
            strokeWidth = stroke
        )
        drawLine(color = color, start = Offset(w * 0.30f, h * 0.08f), end = Offset(w * 0.30f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.70f, h * 0.08f), end = Offset(w * 0.70f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Week bar on first row
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.22f, h * 0.49f),
            size = Size(w * 0.56f, h * 0.10f),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )
        // Dots on second row
        val r = 1.1.dp.toPx()
        for (col in 0..3) {
            drawCircle(
                color = color,
                radius = r,
                center = Offset(w * (0.25f + col * 0.16f), h * 0.72f)
            )
        }
    }
}

@Composable
private fun MT5CalendarMonthIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.18f),
            size = Size(w * 0.80f, h * 0.72f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke, join = StrokeJoin.Round)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.10f, h * 0.38f),
            end = Offset(w * 0.90f, h * 0.38f),
            strokeWidth = stroke
        )
        drawLine(color = color, start = Offset(w * 0.30f, h * 0.08f), end = Offset(w * 0.30f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.70f, h * 0.08f), end = Offset(w * 0.70f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Filled inner block representing full month
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.22f, h * 0.48f),
            size = Size(w * 0.56f, h * 0.30f),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )
    }
}

@Composable
private fun MT5Calendar3Icon(color: Color) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        // Filled dark calendar body
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.18f),
            size = Size(w * 0.80f, h * 0.74f),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
        )
        // Two top rings
        drawLine(color = color, start = Offset(w * 0.30f, h * 0.07f), end = Offset(w * 0.30f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.70f, h * 0.07f), end = Offset(w * 0.70f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)

        // White top header line cutout
        drawLine(
            color = Color.White,
            start = Offset(w * 0.16f, h * 0.34f),
            end = Offset(w * 0.84f, h * 0.34f),
            strokeWidth = 1.2.dp.toPx()
        )

        // Centered white "3"
        val layout = textMeasurer.measure(
            text = "3",
            style = TextStyle(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                x = (w - layout.size.width) / 2f,
                y = h * 0.38f
            )
        )
    }
}

@Composable
private fun MT5CalendarCustomIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        // Filled dark calendar body
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.18f),
            size = Size(w * 0.80f, h * 0.74f),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
        )
        // Two top rings
        drawLine(color = color, start = Offset(w * 0.30f, h * 0.07f), end = Offset(w * 0.30f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.70f, h * 0.07f), end = Offset(w * 0.70f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)

        // White top header line cutout
        drawLine(
            color = Color.White,
            start = Offset(w * 0.16f, h * 0.34f),
            end = Offset(w * 0.84f, h * 0.34f),
            strokeWidth = 1.2.dp.toPx()
        )

        // White person/keyhole silhouette inside
        drawCircle(
            color = Color.White,
            radius = w * 0.09f,
            center = Offset(w * 0.50f, h * 0.52f)
        )
        val bodyPath = Path().apply {
            moveTo(w * 0.36f, h * 0.80f)
            lineTo(w * 0.64f, h * 0.80f)
            lineTo(w * 0.58f, h * 0.62f)
            lineTo(w * 0.42f, h * 0.62f)
            close()
        }
        drawPath(path = bodyPath, color = Color.White)
    }
}
