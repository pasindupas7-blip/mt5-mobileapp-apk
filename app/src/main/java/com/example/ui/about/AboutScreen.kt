package com.example.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.SriLankaMarketHours
import com.example.model.Candle
import com.example.model.HistoryTrade
import com.example.model.MinuteCandleLookupResult
import com.example.model.SymbolQuote
import com.example.model.TpSlChartCheckResult
import com.example.model.TradeHitOutcome
import com.example.ui.settings.EditAccountDialog
import com.example.ui.settings.SettingsGroup
import com.example.ui.settings.SettingsItem
import com.example.ui.theme.MT5Blue
import com.example.ui.theme.MT5Green
import com.example.ui.theme.MT5Separator
import com.example.viewmodel.TradingViewModel
import java.util.Locale
import kotlin.math.abs

private val MtBlue = Color(0xFF2B65CE)
private val MtBlueText = Color(0xFF1A73E8)
private val MtRedText = Color(0xFFD93025)
private val MtGreenText = Color(0xFF1E8E3E)
private val MtDarkText = Color(0xFF212121)
private val MtGrayText = Color(0xFF757575)
private val MtCardBg = Color(0xFFF8F9FA)
private val MtDivider = Color(0xFFE5E7EB)

/**
 * Unified model used strictly inside the About Trade Control tab so that both closed HistoryTrade
 * records and live running TradePosition records (without a close time) can be ordered chronologically
 * from oldest (#1) to newest (#N) and edited/deleted from one place.
 */
data class AboutTradeEntry(
    val id: Long,
    val symbol: String,
    val isBuy: Boolean,
    val lots: Double,
    val openPrice: Double,
    val closeOrCurrentPrice: Double,
    val profit: Double,
    val openTime: String,
    val closeTime: String,
    val sl: Double?,
    val tp: Double?,
    val ticketId: String,
    val isRunning: Boolean,
    val hitOutcome: TradeHitOutcome = TradeHitOutcome.NONE
)

@Composable
fun AboutScreen(
    viewModel: TradingViewModel,
    onOpenDrawer: () -> Unit = { viewModel.openDrawer() },
    onBack: () -> Unit = { viewModel.closeAboutPage() }
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val account by viewModel.account.collectAsState()
    val isCloudSynced by viewModel.isCloudSynced.collectAsState()
    val currentUserEmail by viewModel.currentUserEmail.collectAsState()
    var showEditAccountDialog by remember { mutableStateOf(false) }

    if (showEditAccountDialog) {
        EditAccountDialog(
            currentAccount = account,
            onDismiss = { showEditAccountDialog = false },
            onSave = { name, broker, id, server ->
                viewModel.updateAccountDetails(name, broker, id, server)
                showEditAccountDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("about_screen")
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("about_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF5F6368)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "About & User Guide",
                color = MtDarkText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("about_drawer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Menu",
                    tint = Color(0xFF5F6368)
                )
            }
        }

        HorizontalDivider(color = MtDivider, thickness = 0.8.dp)

        // MT5 Branding & Build Info Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F0FE))
                    .border(2.dp, MtBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MT5",
                    color = MtBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "MetaTrader 5 Android",
                color = MtDarkText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${account.accountName} • ${account.accountId} • 1:500 Leverage",
                color = MtGrayText,
                fontSize = 12.5.sp,
                modifier = Modifier.clickable { showEditAccountDialog = true }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Sri Lanka Time (Asia/Colombo) • Mon 03:30 – Sat 02:29",
                color = MtBlueText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Google Account Login & Cloud Trade Server Tab (Requested by User)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = "GOOGLE ACCOUNT & CLOUD TRADE SERVER",
                color = MtGrayText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            SettingsGroup {
                if (isCloudSynced) {
                    SettingsItem(
                        icon = Icons.Default.CloudDone,
                        iconBg = MT5Green,
                        title = "Cloud Trade Server Connected",
                        subtitle = "Signed in as ${currentUserEmail ?: "Google User"} • Auto-saving trades & history",
                        onClick = { viewModel.syncCloudNow() }
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MT5Separator,
                        modifier = Modifier.padding(start = 52.dp)
                    )
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        iconBg = Color(0xFF8E8E93),
                        title = "Sign Out of Cloud Server",
                        subtitle = "Switch to local device storage only",
                        onClick = { viewModel.signOutCloud(context) }
                    )
                } else {
                    SettingsItem(
                        icon = Icons.Default.CloudSync,
                        iconBg = MT5Blue,
                        title = "Sign in with Google (Cloud Trade Server)",
                        subtitle = "Save & restore open trades and trade history on Firebase server",
                        onClick = { viewModel.signInWithGoogle(context) }
                    )
                }
            }
        }

        HorizontalDivider(color = MtDivider, thickness = 6.dp)

        // Bottom Trade History & Withdrawal Control Tab (Requested by User)
        TradeHistoryControlSection(viewModel = viewModel)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun TradeHistoryControlSection(
    viewModel: TradingViewModel
) {
    val context = LocalContext.current
    val history by viewModel.history.collectAsState()
    val positions by viewModel.positions.collectAsState()
    val account by viewModel.account.collectAsState()
    val isCloudConnected by viewModel.isCloudSynced.collectAsState()
    val currentUserEmail by viewModel.currentUserEmail.collectAsState()

    // 0 = Trades Control Tab, 1 = Withdrawals & Deposit Control Tab
    var selectedControlTab by remember { mutableStateOf(0) }

    // Dialog states
    var showTradeDialog by remember { mutableStateOf(false) }
    var editingTradeEntry by remember { mutableStateOf<AboutTradeEntry?>(null) }

    var showWithdrawalDialog by remember { mutableStateOf(false) }
    var editingWithdrawal by remember { mutableStateOf<HistoryTrade?>(null) }

    var depositInput by remember(account.deposit) {
        mutableStateOf(String.format(Locale.US, "%.2f", account.deposit))
    }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    // Combine closed trades and running open positions, ordered chronologically from OLDEST (#1) to NEWEST (#N)
    val tradeItems = remember(history, positions) {
        val closedEntries = history
            .filter { !it.isWithdrawal }
            .map { t ->
                AboutTradeEntry(
                    id = t.id,
                    symbol = t.symbol,
                    isBuy = t.isBuy,
                    lots = t.lots,
                    openPrice = t.openPrice,
                    closeOrCurrentPrice = t.closePrice,
                    profit = t.profit,
                    openTime = t.openTime,
                    closeTime = t.closeTime,
                    sl = t.sl,
                    tp = t.tp,
                    ticketId = t.ticketId,
                    isRunning = false,
                    hitOutcome = viewModel.resolveTradeHitOutcome(t)
                )
            }
        val runningEntries = positions.map { p ->
            AboutTradeEntry(
                id = p.id,
                symbol = p.symbol,
                isBuy = p.isBuy,
                lots = p.lots,
                openPrice = p.openPrice,
                closeOrCurrentPrice = p.currentPrice,
                profit = p.profit,
                openTime = p.openTime,
                closeTime = "",
                sl = p.sl,
                tp = p.tp,
                ticketId = "#${if (p.id >= 100000000L) p.id else 385000000L + p.id}",
                isRunning = true,
                hitOutcome = TradeHitOutcome.NONE
            )
        }
        (closedEntries + runningEntries).sortedWith(
            compareBy<AboutTradeEntry> {
                SriLankaMarketHours.parseMt5DateTimeMillis(it.openTime.ifBlank { it.closeTime })
            }.thenBy {
                if (it.closeTime.isBlank()) Long.MAX_VALUE
                else SriLankaMarketHours.parseMt5DateTimeMillis(it.closeTime)
            }.thenBy { it.id }
        )
    }

    val withdrawalItems = remember(history) {
        history
            .filter { it.isWithdrawal }
            .sortedWith(
                compareBy<HistoryTrade> {
                    SriLankaMarketHours.parseMt5DateTimeMillis(it.closeTime.ifBlank { it.openTime })
                }.thenBy { it.id }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("trade_history_control_section")
    ) {
        // Header with Cloud Sync / Google Login badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Trade History & Withdrawal Control",
                    color = MtDarkText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isCloudConnected) {
                        "Synced with ${currentUserEmail ?: "Google Account"} • Oldest Trade = #1"
                    } else {
                        "Ordered by Date (Oldest = #1) • Tap badge to Sign in with Google"
                    },
                    color = MtGrayText,
                    fontSize = 12.sp
                )
            }
            Surface(
                color = if (isCloudConnected) Color(0xFFE6F4EA) else Color(0xFFE8F0FE),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .clickable {
                        if (isCloudConnected) {
                            viewModel.syncCloudNow()
                        } else {
                            viewModel.signInWithGoogle(context)
                        }
                    }
                    .testTag("cloud_google_login_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCloudConnected) Icons.Default.CloudDone else Icons.Default.CloudSync,
                        contentDescription = "Google Cloud Sync",
                        tint = if (isCloudConnected) MtGreenText else MtBlueText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCloudConnected) "Cloud Synced" else "Google Login",
                        color = if (isCloudConnected) MtGreenText else MtBlueText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Account Summary Bar
        Surface(
            color = MtCardBg,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MtDivider, RoundedCornerShape(10.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStatColumn("Deposit", String.format(Locale.US, "$%.2f", account.deposit), MtDarkText)
                SummaryStatColumn(
                    "Realized P/L",
                    String.format(
                        Locale.US,
                        "%s$%.2f",
                        if (account.realizedProfit >= 0) "+" else "-",
                        abs(account.realizedProfit)
                    ),
                    if (account.realizedProfit >= 0) MtBlueText else MtRedText
                )
                SummaryStatColumn(
                    "Withdrawals",
                    String.format(Locale.US, "-$%.2f", account.totalWithdrawal),
                    MtRedText
                )
                SummaryStatColumn(
                    "Balance",
                    String.format(Locale.US, "$%.2f", account.balance),
                    MtDarkText
                )
            }
        }

        if (statusBanner != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFFE6F4EA),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusBanner ?: "",
                    color = MtGreenText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sub-Tab Selector: TRADES vs WITHDRAWALS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF1F3F4))
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedControlTab == 0) Color.White else Color.Transparent)
                    .clickable { selectedControlTab = 0 }
                    .testTag("about_tab_trades"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TRADES (${tradeItems.size})",
                    color = if (selectedControlTab == 0) MtBlueText else MtGrayText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedControlTab == 1) Color.White else Color.Transparent)
                    .clickable { selectedControlTab = 1 }
                    .testTag("about_tab_withdrawals"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "WITHDRAWALS (${withdrawalItems.size})",
                    color = if (selectedControlTab == 1) MtBlueText else MtGrayText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedControlTab == 0) {
            // TRADES MANAGEMENT TAB
            Button(
                onClick = {
                    editingTradeEntry = null
                    showTradeDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = MtBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_history_trade_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Trade", tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add New Trade History Item",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (tradeItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No trade history records. Tap above to add a trade.",
                        color = MtGrayText,
                        fontSize = 13.sp
                    )
                }
            } else {
                // Display sequential numbering 1, 2, 3... strictly inside About (Oldest trade = 1)
                tradeItems.forEachIndexed { index, tradeEntry ->
                    EditableTradeHistoryRow(
                        sequenceNumber = index + 1,
                        trade = tradeEntry,
                        onEdit = {
                            editingTradeEntry = tradeEntry
                            showTradeDialog = true
                        },
                        onDelete = {
                            if (tradeEntry.isRunning) {
                                viewModel.deleteRunningPosition(tradeEntry.id)
                                statusBanner = "Deleted running trade #${index + 1} (${tradeEntry.ticketId}) & synced to Cloud"
                            } else {
                                viewModel.deleteHistoryItem(tradeEntry.id)
                                statusBanner = "Deleted trade #${index + 1} (${tradeEntry.ticketId}) & synced to Cloud"
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        } else {
            // WITHDRAWALS & DEPOSIT MANAGEMENT TAB
            Surface(
                color = MtCardBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MtDivider, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = depositInput,
                        onValueChange = { depositInput = it },
                        label = { Text("Base Deposit ($)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("about_deposit_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsed = depositInput.toDoubleOrNull()
                            if (parsed != null && parsed >= 0.0) {
                                viewModel.setDepositAmount(parsed)
                                statusBanner = String.format(Locale.US, "Deposit updated to $%.2f & synced", parsed)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MtBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("save_deposit_button")
                    ) {
                        Text("Update Deposit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    editingWithdrawal = null
                    showWithdrawalDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = MtRedText),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_withdrawal_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Withdrawal", tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Withdrawal Entry",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (withdrawalItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No withdrawals recorded. Tap above to add a withdrawal.",
                        color = MtGrayText,
                        fontSize = 13.sp
                    )
                }
            } else {
                withdrawalItems.forEach { wdr ->
                    EditableWithdrawalRow(
                        withdrawal = wdr,
                        onEdit = {
                            editingWithdrawal = wdr
                            showWithdrawalDialog = true
                        },
                        onDelete = {
                            viewModel.deleteHistoryItem(wdr.id)
                            statusBanner = "Deleted withdrawal ${wdr.ticketId} & synced to Cloud"
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showTradeDialog) {
        TradeHistoryEditDialog(
            initialTrade = editingTradeEntry,
            viewModel = viewModel,
            onDismiss = { showTradeDialog = false },
            onSaveHistoryTrade = { existingId, symbol, isBuy, lots, openPrice, closePrice, openTime, closeTime, sl, tp, customProfit, customTicketId, hitOutcome ->
                viewModel.saveOrUpdateHistoryTrade(
                    existingId = existingId,
                    symbol = symbol,
                    isBuy = isBuy,
                    lots = lots,
                    openPrice = openPrice,
                    closePrice = closePrice,
                    openTime = openTime,
                    closeTime = closeTime,
                    sl = sl,
                    tp = tp,
                    customProfit = customProfit,
                    customTicketId = customTicketId,
                    hitOutcome = hitOutcome
                )
                showTradeDialog = false
                val hitLabel = when (hitOutcome) {
                    TradeHitOutcome.TP_HIT -> "T/P Hit (Green Line)"
                    TradeHitOutcome.SL_HIT -> "S/L Hit (Red Line)"
                    TradeHitOutcome.NONE -> "Manual / No Line"
                }
                statusBanner = "Saved $symbol (${customTicketId ?: ""}) [$hitLabel] & synced to Cloud"
            },
            onSaveRunningTrade = { existingId, symbol, isBuy, lots, openPrice, openTime, sl, tp, customTicketId ->
                viewModel.saveOrUpdateRunningPosition(
                    existingId = existingId,
                    symbol = symbol,
                    isBuy = isBuy,
                    lots = lots,
                    openPrice = openPrice,
                    openTime = openTime,
                    sl = sl,
                    tp = tp,
                    customTicketId = customTicketId
                )
                showTradeDialog = false
                statusBanner = "Added running trade $symbol (No Close Time) & synced to Cloud"
            }
        )
    }

    if (showWithdrawalDialog) {
        WithdrawalEditDialog(
            initialWithdrawal = editingWithdrawal,
            viewModel = viewModel,
            onDismiss = { showWithdrawalDialog = false },
            onSave = { existingId, ticketId, amountUsd, dateTime, comment ->
                viewModel.saveOrUpdateWithdrawal(
                    existingId = existingId,
                    amount = amountUsd,
                    closeTime = dateTime,
                    comment = comment,
                    customTicketId = ticketId
                )
                showWithdrawalDialog = false
                statusBanner = String.format(Locale.US, "Saved withdrawal -$%.2f & auto-saved to Cloud", abs(amountUsd))
            }
        )
    }
}

@Composable
private fun SummaryStatColumn(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, color = MtGrayText, fontSize = 11.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EditableTradeHistoryRow(
    sequenceNumber: Int,
    trade: AboutTradeEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val profitColor = if (trade.profit >= 0.0) MtBlueText else MtRedText
    val profitText = if (trade.profit < 0.0) {
        "-${String.format(Locale.US, "%,.2f", abs(trade.profit))}"
    } else {
        String.format(Locale.US, "%,.2f", trade.profit)
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (trade.isRunning) Color(0xFF1E8E3E).copy(alpha = 0.45f) else MtDivider,
                shape = RoundedCornerShape(8.dp)
            )
            .testTag("about_trade_row_$sequenceNumber")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sequential Number Badge (1, 2, 3... with Oldest Trade = 1, strictly in About screen)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (trade.isRunning) Color(0xFFE6F4EA) else Color(0xFFE8F0FE))
                    .border(
                        width = 1.dp,
                        color = if (trade.isRunning) MtGreenText else MtBlue,
                        shape = CircleShape
                    )
                    .testTag("about_trade_number_$sequenceNumber"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sequenceNumber.toString(),
                    color = if (trade.isRunning) MtGreenText else MtBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${trade.symbol}, ",
                        color = MtDarkText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (trade.isBuy) "buy" else "sell"} ${String.format(Locale.US, "%.2f", trade.lots)}",
                        color = if (trade.isBuy) MtBlueText else MtRedText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = trade.ticketId,
                        color = MtGrayText,
                        fontSize = 11.sp
                    )
                    if (trade.isRunning) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFE6F4EA),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "RUNNING",
                                color = MtGreenText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (trade.hitOutcome == TradeHitOutcome.TP_HIT) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFE6F4EA),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "TP HIT",
                                color = Color(0xFF26A641),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (trade.hitOutcome == TradeHitOutcome.SL_HIT) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFFCE8E6),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "SL HIT",
                                color = Color(0xFFE53935),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${trade.openPrice} \u2192 ${trade.closeOrCurrentPrice}",
                    color = MtGrayText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (trade.isRunning || trade.closeTime.isBlank()) {
                        "Open: ${trade.openTime} | Running (No Close Time)"
                    } else {
                        "Open: ${trade.openTime} | Close: ${trade.closeTime}"
                    },
                    color = if (trade.isRunning) MtGreenText else MtGrayText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = profitText,
                    color = profitColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_trade_${trade.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Trade",
                            tint = MtBlueText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_trade_${trade.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Trade",
                            tint = MtRedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableWithdrawalRow(
    withdrawal: HistoryTrade,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val amountText = "-${String.format(Locale.US, "%,.2f", abs(withdrawal.profit))}"

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MtDivider, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Balance • Withdrawal",
                        color = MtDarkText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = withdrawal.ticketId,
                        color = MtGrayText,
                        fontSize = 11.sp
                    )
                }
                if (withdrawal.comment.isNotBlank()) {
                    Text(
                        text = withdrawal.comment,
                        color = MtGrayText,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = withdrawal.closeTime.ifBlank { withdrawal.openTime },
                    color = MtGrayText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amountText,
                    color = MtRedText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_withdrawal_${withdrawal.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Withdrawal",
                            tint = MtBlueText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_withdrawal_${withdrawal.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Withdrawal",
                            tint = MtRedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatQuotePrice(quote: SymbolQuote, isOpenPrice: Boolean, isBuy: Boolean): String {
    val raw = if (isOpenPrice) {
        if (isBuy) quote.ask else quote.bid
    } else {
        if (isBuy) quote.bid else quote.ask
    }
    return String.format(Locale.US, "%.${quote.digits}f", raw)
}

@Composable
private fun TradeHistoryEditDialog(
    initialTrade: AboutTradeEntry?,
    viewModel: TradingViewModel,
    onDismiss: () -> Unit,
    onSaveHistoryTrade: (
        existingId: Long?,
        symbol: String,
        isBuy: Boolean,
        lots: Double,
        openPrice: Double,
        closePrice: Double,
        openTime: String,
        closeTime: String,
        sl: Double?,
        tp: Double?,
        customProfit: Double?,
        customTicketId: String?,
        hitOutcome: TradeHitOutcome
    ) -> Unit,
    onSaveRunningTrade: (
        existingId: Long?,
        symbol: String,
        isBuy: Boolean,
        lots: Double,
        openPrice: Double,
        openTime: String,
        sl: Double?,
        tp: Double?,
        customTicketId: String?
    ) -> Unit
) {
    val quotes by viewModel.quotes.collectAsState()
    val initialSymbol = initialTrade?.symbol ?: "NDX100"
    val initialBuy = initialTrade?.isBuy ?: true

    val initialRecentM1 = remember(initialSymbol) {
        viewModel.getRecentSymbolM1Candles(initialSymbol, 24)
    }
    val defaultOpenCandle = initialRecentM1.getOrNull(3) ?: initialRecentM1.firstOrNull()
    val defaultCloseCandle = initialRecentM1.firstOrNull()

    val defaultOpenLookup = remember(initialSymbol) {
        val seedTime = if (defaultOpenCandle != null && defaultOpenCandle.timestamp > 0L) {
            SriLankaMarketHours.formatDateTime(defaultOpenCandle.timestamp)
        } else {
            SriLankaMarketHours.formatDateTime()
        }
        viewModel.lookupMinuteChartCandle(initialSymbol, seedTime.take(16), isOpen = true)
    }
    val defaultCloseLookup = remember(initialSymbol) {
        val seedTime = if (defaultCloseCandle != null && defaultCloseCandle.timestamp > 0L) {
            SriLankaMarketHours.formatDateTime(defaultCloseCandle.timestamp)
        } else {
            SriLankaMarketHours.formatDateTime()
        }
        viewModel.lookupMinuteChartCandle(initialSymbol, seedTime.take(16), isOpen = false)
    }

    val defaultNow = remember { SriLankaMarketHours.formatDateTime() }
    val initialQuote = remember(initialSymbol, quotes) {
        quotes.find { it.symbol.equals(initialSymbol, ignoreCase = true) }
    }

    var symbol by remember { mutableStateOf(initialSymbol) }
    var isBuy by remember { mutableStateOf(initialBuy) }
    var isRunningTrade by remember { mutableStateOf(initialTrade?.isRunning ?: false) }
    var lotsText by remember {
        mutableStateOf(initialTrade?.let { String.format(Locale.US, "%.2f", it.lots) } ?: "0.01")
    }
    var openTimeText by remember {
        mutableStateOf(
            initialTrade?.openTime
                ?: defaultOpenLookup?.suggestedTimestamp
                ?: defaultNow
        )
    }
    var closeTimeText by remember {
        mutableStateOf(
            if (initialTrade != null) {
                initialTrade.closeTime
            } else {
                defaultCloseLookup?.suggestedTimestamp ?: defaultNow
            }
        )
    }
    var openPriceText by remember {
        mutableStateOf(
            initialTrade?.openPrice?.toString()
                ?: defaultOpenLookup?.let { String.format(Locale.US, "%.${it.digits}f", it.priceAtSecond) }
                ?: initialQuote?.let { formatQuotePrice(it, isOpenPrice = true, isBuy = initialBuy) }
                ?: "28105.58"
        )
    }
    var closePriceText by remember {
        mutableStateOf(
            initialTrade?.closeOrCurrentPrice?.toString()
                ?: defaultCloseLookup?.let { String.format(Locale.US, "%.${it.digits}f", it.priceAtSecond) }
                ?: initialQuote?.let { formatQuotePrice(it, isOpenPrice = false, isBuy = initialBuy) }
                ?: "28187.63"
        )
    }
    var slText by remember { mutableStateOf(initialTrade?.sl?.toString() ?: "") }
    var tpText by remember { mutableStateOf(initialTrade?.tp?.toString() ?: "") }
    var selectedHitOutcome by remember {
        mutableStateOf(initialTrade?.hitOutcome ?: TradeHitOutcome.NONE)
    }

    // Check chart candlesticks from openTime to closeTime whenever price, S/L, or T/P is entered
    val chartCheckResult = remember(
        symbol,
        isBuy,
        openTimeText,
        closeTimeText,
        openPriceText,
        closePriceText,
        slText,
        tpText,
        quotes
    ) {
        viewModel.checkTpSlHitFromChartData(
            symbol = symbol.trim().uppercase(Locale.US),
            isBuy = isBuy,
            openTime = openTimeText,
            closeTime = closeTimeText,
            openPrice = openPriceText.toDoubleOrNull() ?: 0.0,
            closePrice = closePriceText.toDoubleOrNull() ?: 0.0,
            sl = slText.toDoubleOrNull(),
            tp = tpText.toDoubleOrNull()
        )
    }

    // Whenever user changes S/L, T/P, Open Price, Close Price, or Symbol/Direction, automatically update
    // the selected hit outcome based on which level was hit first in the chart data.
    LaunchedEffect(
        symbol,
        isBuy,
        openPriceText,
        closePriceText,
        slText,
        tpText,
        openTimeText,
        closeTimeText
    ) {
        val hasSlOrTp = slText.toDoubleOrNull() != null || tpText.toDoubleOrNull() != null
        if (hasSlOrTp) {
            selectedHitOutcome = chartCheckResult.outcome
        } else if (initialTrade == null) {
            selectedHitOutcome = TradeHitOutcome.NONE
        }
    }

    // Always generate a brand-new unique Ticket ID every time a new trade dialog opens
    var ticketIdText by remember {
        mutableStateOf(initialTrade?.ticketId ?: viewModel.generateUniqueTicketId())
    }
    var useAutoProfit by remember { mutableStateOf(initialTrade == null || initialTrade.isRunning) }
    var manualProfitText by remember {
        mutableStateOf(initialTrade?.let { String.format(Locale.US, "%.2f", it.profit) } ?: "0.00")
    }

    val recentM1Candles = remember(symbol, quotes) {
        viewModel.getRecentSymbolM1Candles(symbol, 20)
    }

    val openCandleLookup = remember(symbol, openTimeText, quotes) {
        if (openTimeText.trim().length >= 10) {
            viewModel.lookupMinuteChartCandle(symbol, openTimeText, isOpen = true)
        } else null
    }

    val closeCandleLookup = remember(symbol, closeTimeText, quotes) {
        if (closeTimeText.trim().length >= 10) {
            viewModel.lookupMinuteChartCandle(symbol, closeTimeText, isOpen = false)
        } else null
    }

    // Helper to sync Open Price & Close Price to the selected symbol's 1-minute chart candles (or live quote)
    fun syncPricesToSymbolAndTimes(targetSymbol: String, buySide: Boolean) {
        val openLookup = viewModel.lookupMinuteChartCandle(targetSymbol, openTimeText, isOpen = true)
        val closeLookup = if (closeTimeText.isNotBlank()) {
            viewModel.lookupMinuteChartCandle(targetSymbol, closeTimeText, isOpen = false)
        } else null
        val matchedQuote = quotes.find { it.symbol.equals(targetSymbol.trim(), ignoreCase = true) }

        if (openLookup != null) {
            openPriceText = String.format(Locale.US, "%.${openLookup.digits}f", openLookup.priceAtSecond)
        } else if (matchedQuote != null) {
            openPriceText = formatQuotePrice(matchedQuote, isOpenPrice = true, isBuy = buySide)
        }

        if (closeLookup != null && !isRunningTrade) {
            closePriceText = String.format(Locale.US, "%.${closeLookup.digits}f", closeLookup.priceAtSecond)
        } else if (matchedQuote != null) {
            closePriceText = formatQuotePrice(matchedQuote, isOpenPrice = false, isBuy = buySide)
        }
    }

    val activeSymbolQuote = remember(symbol, quotes) {
        quotes.find { it.symbol.equals(symbol.trim(), ignoreCase = true) }
    }

    val calculatedProfit = remember(symbol, isBuy, lotsText, openPriceText, closePriceText) {
        val lots = lotsText.toDoubleOrNull() ?: 0.0
        val openP = openPriceText.toDoubleOrNull() ?: 0.0
        val closeP = closePriceText.toDoubleOrNull() ?: 0.0
        if (lots > 0.0 && openP > 0.0 && closeP > 0.0) {
            viewModel.calculateHistoricalTradeProfit(
                symbol = symbol.trim().uppercase(Locale.US),
                isBuy = isBuy,
                lots = lots,
                openPrice = openP,
                closePrice = closeP
            )
        } else {
            0.0
        }
    }

    LaunchedEffect(calculatedProfit, useAutoProfit) {
        if (useAutoProfit) {
            manualProfitText = String.format(Locale.US, "%.2f", calculatedProfit)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = if (initialTrade == null) "Add New Trade Item" else "Edit Trade Item",
                    color = MtDarkText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Quick Symbol chips — clicking any symbol immediately updates Open & Close prices to its chart price
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("NDX100", "XAUUSD", "EURUSD", "BTCUSD", "GBPUSD", "USDJPY").forEach { preset ->
                        val selected = symbol.equals(preset, ignoreCase = true)
                        Surface(
                            color = if (selected) MtBlue else Color(0xFFF1F3F4),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clickable {
                                    symbol = preset
                                    syncPricesToSymbolAndTimes(preset, isBuy)
                                }
                                .testTag("chip_symbol_$preset")
                        ) {
                            Text(
                                text = preset,
                                color = if (selected) Color.White else MtDarkText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                if (activeSymbolQuote != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Live ${activeSymbolQuote.symbol} Chart Price: Bid ${
                            String.format(Locale.US, "%.${activeSymbolQuote.digits}f", activeSymbolQuote.bid)
                        } / Ask ${
                            String.format(Locale.US, "%.${activeSymbolQuote.digits}f", activeSymbolQuote.ask)
                        }",
                        color = MtBlueText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = symbol,
                    onValueChange = { typed ->
                        val upper = typed.uppercase(Locale.US)
                        symbol = upper
                        syncPricesToSymbolAndTimes(upper, isBuy)
                    },
                    label = { Text("Symbol (e.g. NDX100, XAUUSD)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_trade_symbol")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Buy / Sell toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isBuy = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBuy) MtBlue else Color(0xFFE5E7EB)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_trade_buy_btn")
                    ) {
                        Text(
                            text = "BUY",
                            color = if (isBuy) Color.White else MtDarkText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            isBuy = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isBuy) MtRedText else Color(0xFFE5E7EB)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dialog_trade_sell_btn")
                    ) {
                        Text(
                            text = "SELL",
                            color = if (!isBuy) Color.White else MtDarkText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Running Trade (No Close Time) Toggle
                Surface(
                    color = if (isRunningTrade) Color(0xFFE6F4EA) else Color(0xFFF8F9FA),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isRunningTrade) MtGreenText else MtDivider,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            isRunningTrade = !isRunningTrade
                            if (isRunningTrade) {
                                closeTimeText = ""
                                activeSymbolQuote?.let {
                                    closePriceText = formatQuotePrice(it, isOpenPrice = false, isBuy = isBuy)
                                }
                            } else if (closeTimeText.isBlank()) {
                                val fallbackCloseTime = defaultCloseLookup?.suggestedTimestamp
                                    ?: SriLankaMarketHours.formatDateTime()
                                closeTimeText = fallbackCloseTime
                                viewModel.lookupMinuteChartCandle(symbol, fallbackCloseTime, isOpen = false)?.let { lk ->
                                    closePriceText = String.format(Locale.US, "%.${lk.digits}f", lk.priceAtSecond)
                                }
                            }
                        }
                        .testTag("dialog_running_trade_toggle")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isRunningTrade,
                            onCheckedChange = { checked ->
                                isRunningTrade = checked
                                if (checked) {
                                    closeTimeText = ""
                                    activeSymbolQuote?.let {
                                        closePriceText = formatQuotePrice(it, isOpenPrice = false, isBuy = isBuy)
                                    }
                                } else if (closeTimeText.isBlank()) {
                                    val fallbackCloseTime = defaultCloseLookup?.suggestedTimestamp
                                        ?: SriLankaMarketHours.formatDateTime()
                                    closeTimeText = fallbackCloseTime
                                    viewModel.lookupMinuteChartCandle(symbol, fallbackCloseTime, isOpen = false)?.let { lk ->
                                        closePriceText = String.format(Locale.US, "%.${lk.digits}f", lk.priceAtSecond)
                                    }
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Running Trade (Without Close Time)",
                                color = if (isRunningTrade) MtGreenText else MtDarkText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isRunningTrade) {
                                    "Active in Trade & Chart tabs with live floating profit"
                                } else {
                                    "Check this or clear Close Time to add an open running trade"
                                },
                                color = MtGrayText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = lotsText,
                        onValueChange = { lotsText = it },
                        label = { Text("Lots") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("dialog_trade_lots")
                    )
                    OutlinedTextField(
                        value = ticketIdText,
                        onValueChange = { ticketIdText = it },
                        label = { Text("Unique Ticket ID") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = { ticketIdText = viewModel.generateUniqueTicketId() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "New Unique Ticket ID",
                                    tint = MtBlueText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("dialog_trade_ticket")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ENTRY (OPEN) TIME & AUTO-LOADED 1M CHART PRICE SECTION
                OutlinedTextField(
                    value = openTimeText,
                    onValueChange = { newOpenTime ->
                        openTimeText = newOpenTime
                        // As soon as a valid minute or second timestamp is entered, auto-load the 1m chart price
                        if (newOpenTime.trim().length >= 15) {
                            viewModel.lookupMinuteChartCandle(symbol, newOpenTime, isOpen = true)?.let { lk ->
                                openPriceText = String.format(Locale.US, "%.${lk.digits}f", lk.priceAtSecond)
                            }
                        }
                    },
                    label = { Text("Entry / Open Time (yyyy.MM.dd HH:mm:ss)") },
                    singleLine = true,
                    trailingIcon = {
                        if (openCandleLookup != null) {
                            TextButton(
                                onClick = {
                                    openTimeText = openCandleLookup.suggestedTimestamp
                                    openPriceText = String.format(
                                        Locale.US,
                                        "%.${openCandleLookup.digits}f",
                                        openCandleLookup.priceAtSecond
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Sync 1m", color = MtBlueText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_trade_open_time")
                )

                // 1-Minute Chart Candle Sync & Intra-Minute Price Movement Panel for Open Time
                MinuteCandleSyncPanel(
                    labelPrefix = "Entry 1m Chart Candle",
                    lookup = openCandleLookup,
                    recentCandles = recentM1Candles,
                    isOpen = true,
                    viewModel = viewModel,
                    symbol = symbol,
                    onSelectTimeAndPrice = { selectedTime, selectedPriceStr ->
                        openTimeText = selectedTime
                        openPriceText = selectedPriceStr
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // CLOSE TIME & AUTO-LOADED 1M CHART PRICE SECTION
                if (!isRunningTrade) {
                    OutlinedTextField(
                        value = closeTimeText,
                        onValueChange = { newCloseTime ->
                            closeTimeText = newCloseTime
                            // As soon as a valid minute or second timestamp is entered, auto-load the 1m chart price
                            if (newCloseTime.trim().length >= 15) {
                                viewModel.lookupMinuteChartCandle(symbol, newCloseTime, isOpen = false)?.let { lk ->
                                    closePriceText = String.format(Locale.US, "%.${lk.digits}f", lk.priceAtSecond)
                                }
                            }
                        },
                        label = { Text("Close Time (yyyy.MM.dd HH:mm:ss • Empty = Running)") },
                        singleLine = true,
                        trailingIcon = {
                            if (closeCandleLookup != null) {
                                TextButton(
                                    onClick = {
                                        closeTimeText = closeCandleLookup.suggestedTimestamp
                                        closePriceText = String.format(
                                            Locale.US,
                                            "%.${closeCandleLookup.digits}f",
                                            closeCandleLookup.priceAtSecond
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Sync 1m", color = MtBlueText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_trade_close_time")
                    )

                    MinuteCandleSyncPanel(
                        labelPrefix = "Close 1m Chart Candle",
                        lookup = closeCandleLookup,
                        recentCandles = recentM1Candles,
                        isOpen = false,
                        viewModel = viewModel,
                        symbol = symbol,
                        onSelectTimeAndPrice = { selectedTime, selectedPriceStr ->
                            closeTimeText = selectedTime
                            closePriceText = selectedPriceStr
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Editable Open Price & Close Price (auto-loaded from 1m chart candle, and manually editable)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = openPriceText,
                        onValueChange = { openPriceText = it },
                        label = { Text("Open Price (Editable)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_trade_open_price")
                    )
                    OutlinedTextField(
                        value = closePriceText,
                        onValueChange = { closePriceText = it },
                        label = {
                            Text(if (isRunningTrade) "Current Price (Editable)" else "Close Price (Editable)")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_trade_close_price")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = slText,
                        onValueChange = { slText = it },
                        label = { Text("S/L (Optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tpText,
                        onValueChange = { tpText = it },
                        label = { Text("T/P (Optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!isRunningTrade) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = useAutoProfit,
                            onCheckedChange = { useAutoProfit = it }
                        )
                        Text(
                            text = "Auto-calculate 1:500 Profit ($${String.format(Locale.US, "%.2f", calculatedProfit)})",
                            color = MtDarkText,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedTextField(
                        value = manualProfitText,
                        onValueChange = {
                            useAutoProfit = false
                            manualProfitText = it
                        },
                        label = { Text("Profit USD (+ or -)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_trade_profit")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // =========================================================================
                    // BOTTOM T/P OR S/L HIT SELECTOR & CHART DATA FIRST-HIT CHECKER
                    // =========================================================================
                    TpSlHitOutcomeSelectorSection(
                        selectedOutcome = selectedHitOutcome,
                        chartCheckResult = chartCheckResult,
                        digits = activeSymbolQuote?.digits ?: 2,
                        onSelectOutcome = { chosen ->
                            selectedHitOutcome = chosen
                            val closeVal = closePriceText.toDoubleOrNull()
                            when (chosen) {
                                TradeHitOutcome.TP_HIT -> {
                                    // If user selects T/P Hit and T/P is already entered, optionally align Close Price or fill T/P from Close Price
                                    val tpVal = tpText.toDoubleOrNull()
                                    if (tpVal != null && tpVal > 0.0) {
                                        closePriceText = tpText.trim()
                                    } else if (closeVal != null && closeVal > 0.0) {
                                        tpText = closePriceText.trim()
                                    }
                                }
                                TradeHitOutcome.SL_HIT -> {
                                    val slVal = slText.toDoubleOrNull()
                                    if (slVal != null && slVal > 0.0) {
                                        closePriceText = slText.trim()
                                    } else if (closeVal != null && closeVal > 0.0) {
                                        slText = closePriceText.trim()
                                    }
                                }
                                TradeHitOutcome.NONE -> {
                                    // Manual close / neither hit -> no side line in History tab
                                }
                            }
                        },
                        onApplyChartDetectedHit = {
                            val detectedOutcome = if (chartCheckResult.outcome != TradeHitOutcome.NONE) {
                                chartCheckResult.outcome
                            } else {
                                chartCheckResult.forwardHitOutcome
                            }
                            selectedHitOutcome = detectedOutcome
                            val targetHitPrice = chartCheckResult.hitPrice ?: chartCheckResult.forwardHitPrice
                            val targetHitTime = chartCheckResult.hitTime ?: chartCheckResult.forwardHitTime
                            targetHitPrice?.let { hitP ->
                                val d = activeSymbolQuote?.digits ?: 2
                                closePriceText = String.format(Locale.US, "%.${d}f", hitP)
                            }
                            targetHitTime?.let { hitT ->
                                if (hitT.isNotBlank()) {
                                    closeTimeText = hitT
                                }
                            }
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Live Floating Profit: $${String.format(Locale.US, "%.2f", calculatedProfit)} (updates automatically with live chart ticks)",
                        color = MtGreenText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Cancel", color = MtGrayText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanSymbol = symbol.trim().uppercase(Locale.US).ifEmpty { "NDX100" }
                            val lots = (lotsText.toDoubleOrNull() ?: 0.01).coerceAtLeast(0.01)
                            val fallbackOpen = activeSymbolQuote?.let {
                                if (isBuy) it.ask else it.bid
                            } ?: 100.0
                            val openP = openPriceText.toDoubleOrNull() ?: fallbackOpen
                            val closeP = closePriceText.toDoubleOrNull() ?: openP

                            // If the user entered only the minute ("yyyy.MM.dd HH:mm"), auto-complete the exact second inside that 1m chart candle
                            val rawOpenTime = openTimeText.trim().ifEmpty { defaultNow }
                            val cleanOpenTime = if (rawOpenTime.count { it == ':' } < 2 && openCandleLookup != null) {
                                openCandleLookup.suggestedTimestamp
                            } else {
                                rawOpenTime
                            }

                            val rawCloseTime = closeTimeText.trim()
                            val cleanCloseTime = if (rawCloseTime.isNotEmpty() && rawCloseTime.count { it == ':' } < 2 && closeCandleLookup != null) {
                                closeCandleLookup.suggestedTimestamp
                            } else {
                                rawCloseTime
                            }

                            val formattedTicket = ticketIdText.trim().let {
                                if (it.isEmpty()) viewModel.generateUniqueTicketId()
                                else if (it.startsWith("#")) it
                                else "#$it"
                            }

                            // Save as Running Trade if toggle is checked OR Close Time was left blank
                            if (isRunningTrade || cleanCloseTime.isEmpty()) {
                                onSaveRunningTrade(
                                    initialTrade?.id,
                                    cleanSymbol,
                                    isBuy,
                                    lots,
                                    openP,
                                    cleanOpenTime,
                                    slText.toDoubleOrNull(),
                                    tpText.toDoubleOrNull(),
                                    formattedTicket
                                )
                            } else {
                                val finalProfit = if (useAutoProfit) {
                                    calculatedProfit
                                } else {
                                    manualProfitText.toDoubleOrNull() ?: calculatedProfit
                                }
                                onSaveHistoryTrade(
                                    initialTrade?.id,
                                    cleanSymbol,
                                    isBuy,
                                    lots,
                                    openP,
                                    closeP,
                                    cleanOpenTime,
                                    cleanCloseTime,
                                    slText.toDoubleOrNull(),
                                    tpText.toDoubleOrNull(),
                                    finalProfit,
                                    formattedTicket,
                                    selectedHitOutcome
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunningTrade) MtGreenText else MtBlue
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("dialog_save_trade_btn")
                    ) {
                        Text(
                            text = if (isRunningTrade) "Run Trade & Sync" else "Save & Sync Cloud",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TpSlHitOutcomeSelectorSection(
    selectedOutcome: TradeHitOutcome,
    chartCheckResult: TpSlChartCheckResult,
    digits: Int,
    onSelectOutcome: (TradeHitOutcome) -> Unit,
    onApplyChartDetectedHit: () -> Unit
) {
    val greenStripeColor = Color(0xFF26A641)
    val redStripeColor = Color(0xFFE53935)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8F9FA))
            .border(1.dp, MtDivider, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("tp_sl_hit_selector_section")
    ) {
        Text(
            text = "T/P or S/L Hit Status (History Left Line)",
            color = MtDarkText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Live Chart Data Check Banner
        val checkBgColor = when (chartCheckResult.outcome) {
            TradeHitOutcome.TP_HIT -> Color(0xFFE6F4EA)
            TradeHitOutcome.SL_HIT -> Color(0xFFFCE8E6)
            TradeHitOutcome.NONE -> Color.White
        }
        val checkBorderColor = when (chartCheckResult.outcome) {
            TradeHitOutcome.TP_HIT -> greenStripeColor.copy(alpha = 0.5f)
            TradeHitOutcome.SL_HIT -> redStripeColor.copy(alpha = 0.5f)
            TradeHitOutcome.NONE -> MtDivider
        }
        val checkTextColor = when (chartCheckResult.outcome) {
            TradeHitOutcome.TP_HIT -> greenStripeColor
            TradeHitOutcome.SL_HIT -> redStripeColor
            TradeHitOutcome.NONE -> MtGrayText
        }

        Surface(
            color = checkBgColor,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, checkBorderColor, RoundedCornerShape(6.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Chart Data First-Hit Check:",
                        color = MtDarkText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (chartCheckResult.hitPrice != null || chartCheckResult.forwardHitPrice != null) {
                        Text(
                            text = "Apply Hit Time & Price",
                            color = MtBlueText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onApplyChartDetectedHit() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("apply_chart_hit_btn")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = chartCheckResult.summaryMessage,
                    color = checkTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3-Option Selector Buttons: T/P Hit (Green Line) | S/L Hit (Red Line) | Manual / None (No Line)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Option 1: T/P Hit (Green Line)
            val isTpSelected = selectedOutcome == TradeHitOutcome.TP_HIT
            Surface(
                color = if (isTpSelected) Color(0xFFE6F4EA) else Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .border(
                        width = if (isTpSelected) 2.dp else 1.dp,
                        color = if (isTpSelected) greenStripeColor else MtDivider,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectOutcome(TradeHitOutcome.TP_HIT) }
                    .testTag("hit_selector_tp")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .background(greenStripeColor)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "T/P Hit",
                            color = if (isTpSelected) greenStripeColor else MtDarkText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Green Line",
                            color = greenStripeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Option 2: S/L Hit (Red Line)
            val isSlSelected = selectedOutcome == TradeHitOutcome.SL_HIT
            Surface(
                color = if (isSlSelected) Color(0xFFFCE8E6) else Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .border(
                        width = if (isSlSelected) 2.dp else 1.dp,
                        color = if (isSlSelected) redStripeColor else MtDivider,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectOutcome(TradeHitOutcome.SL_HIT) }
                    .testTag("hit_selector_sl")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .background(redStripeColor)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "S/L Hit",
                            color = if (isSlSelected) redStripeColor else MtDarkText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Red Line",
                            color = redStripeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Option 3: Neither / Manual Close (No Line)
            val isNoneSelected = selectedOutcome == TradeHitOutcome.NONE
            Surface(
                color = if (isNoneSelected) Color(0xFFE8F0FE) else Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .border(
                        width = if (isNoneSelected) 2.dp else 1.dp,
                        color = if (isNoneSelected) MtBlue else MtDivider,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectOutcome(TradeHitOutcome.NONE) }
                    .testTag("hit_selector_none")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Manual / None",
                        color = if (isNoneSelected) MtBlueText else MtDarkText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "No Side Line",
                        color = MtGrayText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MinuteCandleSyncPanel(
    labelPrefix: String,
    lookup: MinuteCandleLookupResult?,
    recentCandles: List<Candle>,
    isOpen: Boolean,
    viewModel: TradingViewModel,
    symbol: String,
    onSelectTimeAndPrice: (selectedTime: String, selectedPriceStr: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8F9FA))
            .border(1.dp, MtDivider, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        if (lookup != null) {
            val fmt = "%.${lookup.digits}f"
            Text(
                text = "$labelPrefix [${lookup.minuteLabel}]: Move Time ${lookup.suggestedTimestamp.takeLast(8)} \u2192 ${
                    String.format(Locale.US, fmt, lookup.priceAtSecond)
                }",
                color = MtBlueText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 4 Intra-Minute OHLC Movement Chips inside this 1-minute candle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val points = listOf(
                    Triple("Open", lookup.openSecondTimestamp, lookup.open),
                    Triple("High", lookup.highSecondTimestamp, lookup.high),
                    Triple("Low", lookup.lowSecondTimestamp, lookup.low),
                    Triple("Close", lookup.closeSecondTimestamp, lookup.close)
                )
                points.forEach { (tag, ts, priceVal) ->
                    val priceStr = String.format(Locale.US, fmt, priceVal)
                    val secPart = ts.takeLast(3) // ":ss"
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier
                            .border(1.dp, Color(0xFFD0D7DE), RoundedCornerShape(5.dp))
                            .clickable {
                                onSelectTimeAndPrice(ts, priceStr)
                            }
                    ) {
                        Text(
                            text = "$tag $secPart ($priceStr)",
                            color = MtDarkText,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (recentCandles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap Chart 1m Candle Time to Auto-Load Time & Price:",
                color = MtGrayText,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                recentCandles.take(14).forEach { c ->
                    if (c.timestamp > 0L) {
                        val candleDateStr = SriLankaMarketHours.formatDateTime(c.timestamp).take(16)
                        val shortLabel = candleDateStr.takeLast(11) // "MM.dd HH:mm"
                        val isSelectedMinute = lookup?.minuteBucketMs == (c.timestamp / 60_000L) * 60_000L
                        Surface(
                            color = if (isSelectedMinute) Color(0xFFE8F0FE) else Color.White,
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (isSelectedMinute) MtBlue else MtDivider,
                                    shape = RoundedCornerShape(5.dp)
                                )
                                .clickable {
                                    viewModel.lookupMinuteChartCandle(symbol, candleDateStr, isOpen = isOpen)?.let { lk ->
                                        val pStr = String.format(Locale.US, "%.${lk.digits}f", lk.priceAtSecond)
                                        onSelectTimeAndPrice(lk.suggestedTimestamp, pStr)
                                    }
                                }
                        ) {
                            Text(
                                text = shortLabel,
                                color = if (isSelectedMinute) MtBlueText else MtDarkText,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelectedMinute) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WithdrawalEditDialog(
    initialWithdrawal: HistoryTrade?,
    viewModel: TradingViewModel,
    onDismiss: () -> Unit,
    onSave: (existingId: Long?, ticketId: String?, amountUsd: Double, dateTime: String, comment: String) -> Unit
) {
    val defaultNow = remember { SriLankaMarketHours.formatDateTime() }
    var amountText by remember {
        mutableStateOf(
            initialWithdrawal?.let { String.format(Locale.US, "%.2f", abs(it.profit)) } ?: "50.00"
        )
    }
    var dateTimeText by remember {
        mutableStateOf(initialWithdrawal?.closeTime?.ifBlank { initialWithdrawal.openTime } ?: defaultNow)
    }
    var commentText by remember {
        mutableStateOf(initialWithdrawal?.comment?.ifBlank { "Withdrawal" } ?: "Withdrawal")
    }
    var ticketIdText by remember {
        mutableStateOf(
            initialWithdrawal?.ticketId ?: viewModel.generateUniqueTicketId()
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (initialWithdrawal == null) "Add Withdrawal" else "Edit Withdrawal",
                    color = MtDarkText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Withdrawal Amount USD ($)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_withdrawal_amount")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dateTimeText,
                    onValueChange = { dateTimeText = it },
                    label = { Text("Date/Time (yyyy.MM.dd HH:mm:ss)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_withdrawal_datetime")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Comment (e.g. Withdrawal)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_withdrawal_comment")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ticketIdText,
                    onValueChange = { ticketIdText = it },
                    label = { Text("Ticket ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_withdrawal_ticket")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Cancel", color = MtGrayText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = abs(amountText.toDoubleOrNull() ?: 0.0)
                            if (amount > 0.0) {
                                val cleanDate = dateTimeText.trim().ifEmpty { defaultNow }
                                val cleanComment = commentText.trim().ifEmpty { "Withdrawal" }
                                val formattedTicket = ticketIdText.trim().let {
                                    if (it.isEmpty()) viewModel.generateUniqueTicketId()
                                    else if (it.startsWith("#")) it
                                    else "#$it"
                                }
                                onSave(initialWithdrawal?.id, formattedTicket, amount, cleanDate, cleanComment)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MtRedText),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("dialog_save_withdrawal_btn")
                    ) {
                        Text("Save Withdrawal", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
