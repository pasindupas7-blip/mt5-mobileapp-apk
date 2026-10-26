package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.TickDirection
import com.example.ui.about.AboutScreen
import com.example.ui.accounts.AccountsScreen
import com.example.ui.chart.ChartScreen
import com.example.ui.components.ClosePositionDialog
import com.example.ui.components.MT5BottomBar
import com.example.ui.components.MT5NavigationDrawerOverlay
import com.example.ui.components.MT5Tab
import com.example.ui.components.NewOrderDialog
import com.example.ui.history.HistoryScreen
import com.example.ui.messages.MessagesScreen
import com.example.ui.quotes.QuotesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MT5Blue
import com.example.ui.theme.MT5Green
import com.example.ui.theme.MT5Separator
import com.example.ui.theme.MT5Surface
import com.example.ui.theme.MT5TextPrimary
import com.example.ui.theme.MT5TextSecondary
import com.example.ui.trade.TradeScreen
import com.example.viewmodel.ActiveDialog
import com.example.viewmodel.TradingViewModel
import java.util.Locale

@Composable
fun MainTradingScreen(
    viewModel: TradingViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.initializeStorage(context)
    }

    val currentTab by viewModel.currentTab.collectAsState()
    val activeDialog by viewModel.activeDialog.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val quotes by viewModel.quotes.collectAsState()
    val positions by viewModel.positions.collectAsState()
    val account by viewModel.account.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val showSettingsPage by viewModel.showSettingsPage.collectAsState()
    val showAccountsPage by viewModel.showAccountsPage.collectAsState()
    val showAboutPage by viewModel.showAboutPage.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFFFFFFFF),
            bottomBar = {
                MT5BottomBar(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    isDarkMode = false,
                    floatingPnL = account.floatingPnL,
                    accountBalance = account.balance,
                    hasOpenPositions = positions.isNotEmpty(),
                    hasActiveSelection = !showAccountsPage && !showSettingsPage && !showAboutPage
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding())
            ) {
                // Main Tab Content, Accounts Page, Settings Page, or About Page
                when {
                    showAboutPage -> AboutScreen(viewModel = viewModel)
                    showAccountsPage -> AccountsScreen(viewModel = viewModel)
                    showSettingsPage -> SettingsScreen(viewModel = viewModel)
                    else -> {
                        when (currentTab) {
                            MT5Tab.QUOTES -> QuotesScreen(viewModel = viewModel)
                            MT5Tab.CHART -> ChartScreen(viewModel = viewModel)
                            MT5Tab.TRADE -> TradeScreen(viewModel = viewModel)
                            MT5Tab.HISTORY -> HistoryScreen(viewModel = viewModel)
                            MT5Tab.SETTINGS -> MessagesScreen(viewModel = viewModel)
                        }
                    }
                }

                // Top Notification Toast pill
                AnimatedVisibility(
                    visible = toastMessage != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 44.dp)
                ) {
                    toastMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xEE1C1C1E),
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MT5Green,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = msg,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Active Dialog Handler
                when (val dialog = activeDialog) {
                    is ActiveDialog.NewOrder -> {
                        val quote = quotes.find { it.symbol == dialog.symbol } ?: quotes.first()
                        NewOrderDialog(
                            quote = quote,
                            onDismiss = { viewModel.dismissDialog() },
                            onExecuteOrder = { symbol, isBuy, lots, sl, tp ->
                                viewModel.executeDialogOrder(symbol, isBuy, lots, sl, tp)
                            }
                        )
                    }
                    is ActiveDialog.ClosePosition -> {
                        val quote = quotes.find { it.symbol == dialog.position.symbol }
                        val currentPrice = if (quote != null) {
                            if (dialog.position.isBuy) quote.bid else quote.ask
                        } else dialog.position.currentPrice
                        val digits = quote?.digits ?: 2

                        ClosePositionDialog(
                            position = dialog.position,
                            currentPrice = currentPrice,
                            digits = digits,
                            onDismiss = { viewModel.dismissDialog() },
                            onCloseFull = { viewModel.confirmCloseFull(dialog.position.id) },
                            onClosePartial = { lots -> viewModel.confirmClosePartial(dialog.position.id, lots) }
                        )
                    }
                    is ActiveDialog.TopUp -> {
                        TopUpDialog(
                            currentBalance = dialog.currentBalance,
                            onDismiss = { viewModel.dismissDialog() },
                            onTopUp = { amount -> viewModel.topUpBalance(amount) }
                        )
                    }
                    is ActiveDialog.None -> { /* No Dialog */ }
                }
            }
        }

        // Full-height Left Navigation Drawer Overlay (slides over top bar & bottom bar)
        MT5NavigationDrawerOverlay(
            isOpen = isDrawerOpen,
            account = account,
            onDismiss = { viewModel.closeDrawer() },
            onManageAccountsClick = {
                viewModel.openAccountsPage()
            },
            onTradeClick = {
                viewModel.selectTab(MT5Tab.QUOTES)
            },
            onNewsClick = {
                viewModel.selectTab(MT5Tab.SETTINGS)
            },
            onMailboxClick = {
                viewModel.selectTab(MT5Tab.SETTINGS)
            },
            onJournalClick = {
                viewModel.selectTab(MT5Tab.HISTORY)
            },
            onSettingsClick = {
                viewModel.openSettingsPage()
            },
            onCommunityClick = {
                viewModel.selectTab(MT5Tab.SETTINGS)
            },
            onAlgoTradingClick = {
                viewModel.selectTab(MT5Tab.CHART)
            },
            onAboutClick = {
                viewModel.openAboutPage()
            }
        )
    }
}

@Composable
fun TopUpDialog(
    currentBalance: Double,
    onDismiss: () -> Unit,
    onTopUp: (Double) -> Unit
) {
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
                    Text(
                        text = "Demo Account Funds",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MT5TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MT5TextSecondary)
                    }
                }

                Text(
                    text = "Current Balance: $${String.format(Locale.US, "%.2f", currentBalance)} USD",
                    fontSize = 14.sp,
                    color = MT5TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Text(
                    text = "Select deposit amount:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MT5TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onTopUp(1000.0) },
                        colors = ButtonDefaults.buttonColors(containerColor = MT5Blue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+$1,000", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onTopUp(5000.0) },
                        colors = ButtonDefaults.buttonColors(containerColor = MT5Blue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+$5,000", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onTopUp(10000.0) },
                        colors = ButtonDefaults.buttonColors(containerColor = MT5Blue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+$10,000", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
