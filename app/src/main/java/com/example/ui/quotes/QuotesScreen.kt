package com.example.ui.quotes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SriLankaMarketHours
import com.example.model.SymbolQuote
import com.example.ui.components.MT5HamburgerButton
import com.example.ui.components.PipDisplay
import com.example.ui.theme.MT5Blue
import com.example.ui.theme.MT5SearchBg
import com.example.ui.theme.MT5Separator
import com.example.ui.theme.MT5Surface
import com.example.ui.theme.MT5TextPrimary
import com.example.ui.theme.MT5TextSecondary
import com.example.viewmodel.TradingViewModel
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val quotes by viewModel.filteredQuotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showSearchBar by remember { mutableStateOf(false) }
    var selectedQuoteForAction by remember { mutableStateOf<SymbolQuote?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isMarketOpen by remember {
        mutableStateOf(SriLankaMarketHours.isMarketOpen(System.currentTimeMillis()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            isMarketOpen = SriLankaMarketHours.isMarketOpen(System.currentTimeMillis())
            delay(1000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // Android MT5 Top Bar: [≡ Quotes] on left, [+  ✎] on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MT5HamburgerButton(
                    onClick = { viewModel.openDrawer() }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Quotes",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1C1C1E)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) {
                            viewModel.onSearchQueryChange("")
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("quotes_add_search_button")
                ) {
                    Icon(
                        imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Add Symbol",
                        tint = Color(0xFF3C4043),
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = { showSearchBar = !showSearchBar },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit quotes",
                        tint = Color(0xFF3C4043),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Expandable Search Bar when '+' is clicked
        AnimatedVisibility(visible = showSearchBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MT5SearchBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MT5TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = MT5TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quote_search_input"),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Enter symbol for search",
                                    fontSize = 15.sp,
                                    color = MT5TextSecondary
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }

        // Quotes List matching MT5 Android live Quotes screen
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            items(quotes, key = { it.symbol }) { quote ->
                QuoteListItem(
                    quote = quote,
                    isMarketOpen = isMarketOpen,
                    onClick = {
                        selectedQuoteForAction = quote
                    }
                )
            }
        }
    }

    // Modal Bottom Sheet on Quote Click
    selectedQuoteForAction?.let { q ->
        ModalBottomSheet(
            onDismissRequest = { selectedQuoteForAction = null },
            sheetState = sheetState,
            containerColor = MT5Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "${q.symbol}: ${q.description}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MT5TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Action: New Order
                Surface(
                    color = Color.White,
                    onClick = {
                        val target = q.symbol
                        selectedQuoteForAction = null
                        viewModel.openNewOrderDialog(target)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("New order", fontSize = 16.sp, color = Color(0xFF1C1C1E), fontWeight = FontWeight.Medium)
                    }
                }
                HorizontalDivider(color = MT5Separator, thickness = 0.5.dp)

                // Action: Chart
                Surface(
                    color = Color.White,
                    onClick = {
                        val target = q.symbol
                        selectedQuoteForAction = null
                        viewModel.selectSymbol(target, navigateToChart = true)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chart", fontSize = 16.sp, color = Color(0xFF1C1C1E), fontWeight = FontWeight.Medium)
                    }
                }
                HorizontalDivider(color = MT5Separator, thickness = 0.5.dp)

                // Action: Properties
                Surface(
                    color = Color.White,
                    onClick = {
                        selectedQuoteForAction = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Symbol properties", fontSize = 16.sp, color = MT5Blue, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun QuoteListItem(
    quote: SymbolQuote,
    isMarketOpen: Boolean = true,
    onClick: () -> Unit
) {
    val isClosedSessionSymbol = !isMarketOpen ||
        (quote.changePoints == 0 && quote.changePercent == 0.0) ||
        quote.symbol.equals("INRUSD", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("quote_item_${quote.symbol}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Change points/percent, Symbol name, Time & Spread
        Column(
            modifier = Modifier.weight(1.08f)
        ) {
            // Line 1: Change points (gray) + percentage (blue/red/gray)
            val ptsSign = if (quote.changePoints > 0) "+" else ""
            val pctColor = when {
                isClosedSessionSymbol -> Color(0xFF757575)
                quote.changePercent > 0.0 -> Color(0xFF0A60C2)
                quote.changePercent < 0.0 -> Color(0xFFC62828)
                else -> Color(0xFF757575)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$ptsSign${quote.changePoints} ",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF757575)
                )
                Text(
                    text = "${String.format(Locale.US, "%.2f", quote.changePercent)}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = pctColor
                )
            }

            // Line 2: Symbol Name
            Text(
                text = quote.symbol,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isClosedSessionSymbol) Color(0xFF5F6368) else Color(0xFF1C1C1E),
                modifier = Modifier.padding(vertical = 1.dp)
            )

            // Line 3: Time + MT5 Spread symbol |↔| + Spread value
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = quote.time,
                    fontSize = 12.sp,
                    color = Color(0xFF757575)
                )
                Spacer(modifier = Modifier.width(5.dp))
                MT5SpreadIcon(color = Color(0xFF757575))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${quote.spread}",
                    fontSize = 12.sp,
                    color = Color(0xFF757575),
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Right Column: Bid and Ask columns with live Blue/Red tick colors during market open hours
        val forcedNeutralColor = if (isClosedSessionSymbol) Color(0xFF5F6368) else null

        Row(
            modifier = Modifier.weight(1.92f),
            horizontalArrangement = Arrangement.End
        ) {
            // Bid column
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 14.dp)
            ) {
                PipDisplay(
                    price = quote.bid,
                    digits = quote.digits,
                    tickDirection = quote.bidDirection,
                    lastTickTime = quote.lastTickTime,
                    baseFontSize = 17.sp,
                    pipFontSize = 25.sp,
                    superFontSize = 13.sp,
                    forceColor = forcedNeutralColor,
                    enableFlashBackground = false
                )
                Text(
                    text = "L: ${formatLowHighPrice(quote.low, quote.digits)}",
                    fontSize = 12.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            // Ask column
            Column(
                horizontalAlignment = Alignment.End
            ) {
                PipDisplay(
                    price = quote.ask,
                    digits = quote.digits,
                    tickDirection = quote.askDirection,
                    lastTickTime = quote.lastTickTime,
                    baseFontSize = 17.sp,
                    pipFontSize = 25.sp,
                    superFontSize = 13.sp,
                    forceColor = forcedNeutralColor,
                    enableFlashBackground = false
                )
                Text(
                    text = "H: ${formatLowHighPrice(quote.high, quote.digits)}",
                    fontSize = 12.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

private fun formatLowHighPrice(value: Double, digits: Int): String {
    val raw = String.format(Locale.US, "%.${digits}f", value)
    // Add space thousands separator for 4+ integer digits like "3 140.22" or "18 077.7" in Screenshot 3
    val dotIdx = raw.indexOf('.')
    val intPart = if (dotIdx != -1) raw.substring(0, dotIdx) else raw
    val fracPart = if (dotIdx != -1) raw.substring(dotIdx) else ""
    return if (intPart.length >= 4) {
        val prefix = intPart.substring(0, intPart.length - 3)
        val suffix = intPart.substring(intPart.length - 3)
        "$prefix $suffix$fracPart"
    } else {
        raw
    }
}

@Composable
private fun MT5SpreadIcon(color: Color) {
    Canvas(modifier = Modifier.size(width = 9.dp, height = 9.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.1.dp.toPx()
        // Left vertical bar
        drawLine(color = color, start = Offset(1f, 1f), end = Offset(1f, h - 1f), strokeWidth = stroke)
        // Right vertical bar
        drawLine(color = color, start = Offset(w - 1f, 1f), end = Offset(w - 1f, h - 1f), strokeWidth = stroke)
        // Two inner horizontal bars
        drawLine(color = color, start = Offset(1f, h * 0.36f), end = Offset(w - 1f, h * 0.36f), strokeWidth = stroke)
        drawLine(color = color, start = Offset(1f, h * 0.64f), end = Offset(w - 1f, h * 0.64f), strokeWidth = stroke)
    }
}
