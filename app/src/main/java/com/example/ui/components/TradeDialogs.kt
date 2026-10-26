package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.SymbolQuote
import com.example.model.TickDirection
import com.example.model.TradePosition
import com.example.ui.theme.MT5Background
import com.example.ui.theme.MT5Blue
import com.example.ui.theme.MT5Red
import com.example.ui.theme.MT5Separator
import com.example.ui.theme.MT5Surface
import com.example.ui.theme.MT5TextPrimary
import com.example.ui.theme.MT5TextSecondary
import java.util.Locale

@Composable
fun QuickTradeBar(
    quote: SymbolQuote,
    currentLot: Double,
    onLotChange: (Double) -> Unit,
    onSellClick: (Double) -> Unit,
    onBuyClick: (Double) -> Unit,
    isDarkMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val buttonColor = when (quote.tickDirection) {
        TickDirection.DOWN -> Color(0xFFE53935)
        TickDirection.UP, TickDirection.NEUTRAL -> Color(0xFF2979FF)
    }
    val centerBg = if (isDarkMode) Color(0xFF000000) else Color(0xFFFFFFFF)
    val centerTextColor = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF3C4043)
    val chevronColor = if (isDarkMode) Color(0xFF6B7280) else Color(0xFF9AA0A6)
    val borderColor = if (isDarkMode) Color(0xFF22262E) else Color(0xFFD0D5DD)

    val formattedLot = remember(currentLot) {
        String.format(Locale.US, "%.2f", currentLot)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(centerBg)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(width = 0.8.dp, color = borderColor, shape = RoundedCornerShape(4.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left SELL Panel
            Box(
                modifier = Modifier
                    .weight(1.05f)
                    .height(48.dp)
                    .background(buttonColor)
                    .clickable { onSellClick(currentLot) }
                    .testTag("quick_sell_button")
            ) {
                Text(
                    text = "SELL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 6.dp, top = 3.dp)
                )
                val bidParts = formatPipParts(quote.bid, quote.digits)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = bidParts.prefix,
                        fontSize = 15.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = bidParts.pip,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.alignByBaseline()
                    )
                    if (bidParts.superscript.isNotEmpty()) {
                        Text(
                            text = bidParts.superscript,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }

            // Center Lot Selector Panel
            Row(
                modifier = Modifier
                    .weight(1.05f)
                    .height(48.dp)
                    .background(centerBg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        val newLot = (currentLot - 0.01).coerceAtLeast(0.01)
                        onLotChange(String.format(Locale.US, "%.2f", newLot).toDouble())
                    },
                    modifier = Modifier.weight(0.85f)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Decrease lot",
                        tint = chevronColor
                    )
                }

                Text(
                    text = formattedLot,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = centerTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1.3f)
                )

                IconButton(
                    onClick = {
                        val newLot = (currentLot + 0.01).coerceAtMost(100.0)
                        onLotChange(String.format(Locale.US, "%.2f", newLot).toDouble())
                    },
                    modifier = Modifier.weight(0.85f)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Increase lot",
                        tint = chevronColor
                    )
                }
            }

            // Right BUY Panel
            Box(
                modifier = Modifier
                    .weight(1.05f)
                    .height(48.dp)
                    .background(buttonColor)
                    .clickable { onBuyClick(currentLot) }
                    .testTag("quick_buy_button")
            ) {
                Text(
                    text = "BUY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 6.dp, top = 3.dp)
                )
                val askParts = formatPipParts(quote.ask, quote.digits)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = askParts.prefix,
                        fontSize = 15.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = askParts.pip,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.alignByBaseline()
                    )
                    if (askParts.superscript.isNotEmpty()) {
                        Text(
                            text = askParts.superscript,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NewOrderDialog(
    quote: SymbolQuote,
    onDismiss: () -> Unit,
    onExecuteOrder: (symbol: String, isBuy: Boolean, lots: Double, sl: Double?, tp: Double?) -> Unit
) {
    var lots by remember { mutableDoubleStateOf(0.10) }
    var slText by remember { mutableStateOf("") }
    var tpText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MT5Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = quote.symbol,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MT5TextPrimary
                        )
                        Text(
                            text = quote.description,
                            fontSize = 12.sp,
                            color = MT5TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MT5TextSecondary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MT5Separator)

                Text(
                    text = "Market Execution",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MT5TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Lot Stepper with -0.1, -0.01, +0.01, +0.1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "-0.1",
                        fontSize = 13.sp,
                        color = MT5Blue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { lots = (lots - 0.1).coerceAtLeast(0.01) }
                            .padding(6.dp)
                    )
                    Text(
                        text = "-0.01",
                        fontSize = 13.sp,
                        color = MT5Blue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { lots = (lots - 0.01).coerceAtLeast(0.01) }
                            .padding(6.dp)
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f", lots),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MT5TextPrimary
                    )
                    Text(
                        text = "+0.01",
                        fontSize = 13.sp,
                        color = MT5Blue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { lots = (lots + 0.01).coerceAtMost(50.0) }
                            .padding(6.dp)
                    )
                    Text(
                        text = "+0.1",
                        fontSize = 13.sp,
                        color = MT5Blue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { lots = (lots + 0.1).coerceAtMost(50.0) }
                            .padding(6.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MT5Separator)

                // Live Bid / Ask comparison
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Bid", fontSize = 12.sp, color = MT5TextSecondary)
                        PipDisplay(
                            price = quote.bid,
                            digits = quote.digits,
                            tickDirection = quote.tickDirection,
                            lastTickTime = quote.lastTickTime,
                            pipFontSize = 24.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ask", fontSize = 12.sp, color = MT5TextSecondary)
                        PipDisplay(
                            price = quote.ask,
                            digits = quote.digits,
                            tickDirection = quote.tickDirection,
                            lastTickTime = quote.lastTickTime,
                            pipFontSize = 24.sp
                        )
                    }
                }

                // Stop Loss & Take Profit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = slText,
                        onValueChange = { slText = it },
                        label = { Text("Stop Loss", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tpText,
                        onValueChange = { tpText = it },
                        label = { Text("Take Profit", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sell and Buy Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val sl = slText.toDoubleOrNull()
                            val tp = tpText.toDoubleOrNull()
                            onExecuteOrder(quote.symbol, false, lots, sl, tp)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MT5Red),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_sell_button")
                    ) {
                        Text("SELL BY MARKET", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val sl = slText.toDoubleOrNull()
                            val tp = tpText.toDoubleOrNull()
                            onExecuteOrder(quote.symbol, true, lots, sl, tp)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MT5Blue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_buy_button")
                    ) {
                        Text("BUY BY MARKET", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ClosePositionDialog(
    position: TradePosition,
    currentPrice: Double,
    digits: Int,
    onDismiss: () -> Unit,
    onCloseFull: () -> Unit,
    onClosePartial: (Double) -> Unit
) {
    var closeLots by remember { mutableDoubleStateOf(position.lots) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MT5Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${position.symbol} ${if (position.isBuy) "buy" else "sell"} ${position.lots}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MT5TextPrimary
                        )
                        Text(
                            text = "Position #${position.id}",
                            fontSize = 12.sp,
                            color = MT5TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MT5TextSecondary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MT5Separator)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Open Price:", color = MT5TextSecondary, fontSize = 14.sp)
                    Text(String.format(Locale.US, "%.${digits}f", position.openPrice), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Current Price:", color = MT5TextSecondary, fontSize = 14.sp)
                    Text(String.format(Locale.US, "%.${digits}f", currentPrice), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Floating Profit:", color = MT5TextSecondary, fontSize = 14.sp)
                    val pnl = position.profit
                    Text(
                        text = "${if (pnl >= 0) "+" else ""}${String.format(Locale.US, "%.2f", pnl)} USD",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (pnl >= 0) MT5Blue else MT5Red
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MT5Separator)

                // Partial close volume selector
                Text("Lots to close:", fontSize = 13.sp, color = MT5TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            closeLots = (closeLots - 0.01).coerceAtLeast(0.01)
                        }
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "-")
                    }

                    Text(
                        text = String.format(Locale.US, "%.2f", closeLots),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            closeLots = (closeLots + 0.01).coerceAtMost(position.lots)
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "+")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Close button with profit/loss preview
                val proportionalProfit = (position.profit * (closeLots / position.lots))
                val profitStr = "${if (proportionalProfit >= 0) "+" else ""}${String.format(Locale.US, "%.2f", proportionalProfit)}"

                Button(
                    onClick = {
                        if (closeLots >= position.lots) {
                            onCloseFull()
                        } else {
                            onClosePartial(closeLots)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_close_button")
                ) {
                    Text(
                        text = "Close with ${if (proportionalProfit >= 0) "Profit" else "Loss"} $profitStr USD",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
