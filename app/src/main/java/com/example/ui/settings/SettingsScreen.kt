package com.example.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AccountInfo
import com.example.ui.components.MT5HamburgerButton
import com.example.ui.theme.MT5Background
import com.example.ui.theme.MT5Blue
import com.example.ui.theme.MT5Green
import com.example.ui.theme.MT5Red
import com.example.ui.theme.MT5Separator
import com.example.ui.theme.MT5Surface
import com.example.ui.theme.MT5TextPrimary
import com.example.ui.theme.MT5TextSecondary
import com.example.viewmodel.TradingViewModel

@Composable
fun SettingsScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.closeSettingsPage()
    }

    val context = LocalContext.current
    val account by viewModel.account.collectAsState()
    val isCloudSynced by viewModel.isCloudSynced.collectAsState()
    val currentUserEmail by viewModel.currentUserEmail.collectAsState()
    val chartHistoryMoveEnabled by viewModel.chartHistoryMoveEnabled.collectAsState()
    var showEditAccountDialog by remember { mutableStateOf(false) }

    if (showEditAccountDialog) {
        com.example.ui.accounts.AddOrEditAccountDialog(
            initialAccount = account,
            onDismiss = { showEditAccountDialog = false },
            onSave = { name, broker, id, server, isReal, balance, logoType ->
                viewModel.updateAccountDetails(
                    accountName = name,
                    brokerName = broker,
                    accountId = id,
                    serverName = server,
                    isReal = isReal,
                    newBalance = balance,
                    logoType = logoType
                )
                showEditAccountDialog = false
            },
            onDelete = {
                showEditAccountDialog = false
                viewModel.deleteAccount(account.key)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MT5Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Top Title Bar with Hamburger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MT5HamburgerButton(
                onClick = { viewModel.openDrawer() },
                tint = Color(0xFF5F6368)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MT5TextPrimary
            )
        }

        // Header Account Card (Real / Demo, Broker, Account ID)
        val isReal = account.isReal || account.accountName.contains("REAL", ignoreCase = true)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Surface(
                color = MT5Surface,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEditAccountDialog = true }
                    .testTag("account_header_card")
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = account.accountName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MT5TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Account",
                                tint = MT5Blue,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = account.brokerName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MT5TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = account.accountId,
                            fontSize = 13.sp,
                            color = MT5TextSecondary
                        )
                        Text(
                            text = account.serverName,
                            fontSize = 12.sp,
                            color = MT5TextSecondary
                        )
                    }

                    // Real / Demo ribbon at top right
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isReal) Color(0xFFFF9500) else MT5Green)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isReal) "Real" else "Demo",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Chevron arrow at right center
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Details",
                        tint = MT5TextSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Cloud Server & Trade History Sync Group
        SettingsGroup {
            if (isCloudSynced) {
                SettingsItem(
                    icon = Icons.Default.CloudDone,
                    iconBg = MT5Green,
                    title = "Cloud Trade Server Connected",
                    subtitle = "Signed in as ${currentUserEmail ?: "Google User"} • Auto-saving trades & history",
                    onClick = { viewModel.syncCloudNow() }
                )
                HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))
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

            HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleChartHistoryMove() }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("settings_chart_history_move_toggle_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (chartHistoryMoveEnabled) MT5Green else Color(0xFF8E8E93)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Chart Trade History Move",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chart Trade History Move",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MT5TextPrimary
                    )
                    Text(
                        text = if (chartHistoryMoveEnabled) {
                            "ON • Long-press trade on chart to move & edit"
                        } else {
                            "OFF • Chart trade history move is disabled"
                        },
                        fontSize = 12.sp,
                        color = MT5TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = if (chartHistoryMoveEnabled) Color(0xFF198754) else Color(0xFF6C757D),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .clickable { viewModel.toggleChartHistoryMove() }
                        .testTag("settings_chart_history_move_button")
                ) {
                    Text(
                        text = if (chartHistoryMoveEnabled) "ON" else "OFF",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Group 1: New Account, Mailbox, News, Tradays
        SettingsGroup {
            SettingsItem(
                icon = Icons.Default.PersonAdd,
                iconBg = Color(0xFF34C759),
                title = "New Account",
                onClick = { viewModel.openTopUpDialog() }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.Default.Email,
                iconBg = Color(0xFF007AFF),
                title = "Mailbox",
                subtitle = "You have registered a new account - Fusion Markets Pt..."
            )
            HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                iconBg = Color(0xFFFF9500),
                title = "News"
            )
            HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.Default.Today,
                iconBg = Color(0xFFFF3B30),
                title = "Tradays",
                subtitle = "Economic calendar"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Group 2: Community and Algo
        SettingsGroup {
            SettingsItem(
                icon = Icons.Default.Email,
                iconBg = Color(0xFF5856D6),
                title = "Chat and Messages",
                subtitle = "Sign in to MQL5.community!",
                badgeText = "1"
            )
            HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.Default.Language,
                iconBg = Color(0xFF007AFF),
                title = "Traders Community"
            )
            HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Send,
                iconBg = Color(0xFF32ADE6),
                title = "MQL5 Algo Trading"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Group 3: OTP, Interface, Charts
        SettingsGroup {
            SettingsItem(
                icon = Icons.Default.Security,
                iconBg = Color(0xFF34C759),
                title = "OTP",
                subtitle = "One-time password generator"
            )
            HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.Default.Language,
                iconBg = Color(0xFF007AFF),
                title = "Interface",
                subtitle = "English"
            )
            HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.Default.CandlestickChart,
                iconBg = Color(0xFF007AFF),
                title = "Charts"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Group 4: Demo Account Management & About
        SettingsGroup {
            SettingsItem(
                icon = Icons.Default.AccountBalanceWallet,
                iconBg = Color(0xFF34C759),
                title = "Top Up Demo Balance",
                subtitle = "Add +$1,000 USD to balance",
                onClick = { viewModel.topUpBalance(1000.0) }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.Default.Refresh,
                iconBg = Color(0xFFFF3B30),
                title = "Reset Demo Account",
                subtitle = "Restore initial MT5 demo portfolio",
                onClick = { viewModel.resetDemo() }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MT5Separator, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.Default.Edit,
                iconBg = MT5Blue,
                title = "About & Trade History Control",
                subtitle = "Open full About page & Trade / Withdrawal editor",
                onClick = { viewModel.openAboutPage() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Embedded Trade History & Withdrawal Control Tab at bottom of Settings/About
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 0.5.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            com.example.ui.about.TradeHistoryControlSection(viewModel = viewModel)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        color = MT5Surface,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with colored rounded square
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MT5TextPrimary
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MT5TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        // Notification Badge if any (e.g. '1')
        badgeText?.let {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MT5Red)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MT5TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun EditAccountDialog(
    currentAccount: AccountInfo,
    onDismiss: () -> Unit,
    onSave: (name: String, broker: String, id: String, server: String) -> Unit
) {
    var name by remember { mutableStateOf(currentAccount.accountName) }
    var broker by remember { mutableStateOf(currentAccount.brokerName) }
    var id by remember { mutableStateOf(currentAccount.accountId) }
    var server by remember { mutableStateOf(currentAccount.serverName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Account Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MT5TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name") },
                    placeholder = { Text("e.g. REAL 21") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = broker,
                    onValueChange = { broker = it },
                    label = { Text("Broker") },
                    placeholder = { Text("e.g. Fusion Markets Pty Ltd") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Account ID") },
                    placeholder = { Text("e.g. 420784 - FusionMarkets-Real") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Server") },
                    placeholder = { Text("e.g. Local Access Server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, broker, id, server)
                    }
                }
            ) {
                Text("Save", fontWeight = FontWeight.Bold, color = MT5Blue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MT5TextSecondary)
            }
        },
        containerColor = MT5Surface
    )
}

