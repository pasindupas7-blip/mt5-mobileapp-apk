package com.example.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AccountInfo
import com.example.ui.components.MT5AccountBrokerLogo
import com.example.ui.components.MT5FundingPipsLogo
import com.example.ui.components.MT5FusionMarketsLogo
import com.example.ui.components.MT5HamburgerButton
import com.example.ui.components.MT5RedRingLogo
import com.example.ui.theme.MT5Blue
import com.example.ui.theme.MT5Red
import com.example.ui.theme.MT5Surface
import com.example.ui.theme.MT5TextPrimary
import com.example.ui.theme.MT5TextSecondary
import com.example.viewmodel.TradingViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.closeAccountsPage()
    }

    val account by viewModel.account.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountInfo?>(null) }
    var actionDialogAccount by remember { mutableStateOf<AccountInfo?>(null) }
    var confirmDeleteAccount by remember { mutableStateOf<AccountInfo?>(null) }
    var showManageDeleteListDialog by remember { mutableStateOf(false) }

    // Add New Account Dialog (+ button)
    if (showAddAccountDialog) {
        AddOrEditAccountDialog(
            initialAccount = null,
            onDismiss = { showAddAccountDialog = false },
            onSave = { name, broker, id, server, isReal, balance, logoType ->
                viewModel.addAccount(
                    accountName = name,
                    brokerName = broker,
                    accountId = id,
                    serverName = server,
                    isReal = isReal,
                    initialBalance = balance,
                    logoType = logoType,
                    switchToNew = true
                )
                showAddAccountDialog = false
            }
        )
    }

    // Edit Existing Account Dialog
    editingAccount?.let { targetAcc ->
        AddOrEditAccountDialog(
            initialAccount = targetAcc,
            onDismiss = { editingAccount = null },
            onSave = { name, broker, id, server, isReal, balance, logoType ->
                viewModel.updateAccountByKey(
                    targetKey = targetAcc.key,
                    accountName = name,
                    brokerName = broker,
                    accountId = id,
                    serverName = server,
                    isReal = isReal,
                    newBalance = balance,
                    logoType = logoType
                )
                editingAccount = null
            },
            onDelete = {
                editingAccount = null
                confirmDeleteAccount = targetAcc
            }
        )
    }

    // Long-press / Info icon Account Options Dialog (Switch / Edit / Delete)
    actionDialogAccount?.let { targetAcc ->
        val isCurrentActive = targetAcc.key == account.key
        AlertDialog(
            onDismissRequest = { actionDialogAccount = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MT5AccountBrokerLogo(account = targetAcc, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = targetAcc.accountName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MT5TextPrimary
                        )
                        Text(
                            text = targetAcc.accountId,
                            fontSize = 12.sp,
                            color = MT5TextSecondary
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!isCurrentActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.switchAccount(targetAcc.key)
                                    actionDialogAccount = null
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Switch Account",
                                tint = MT5Blue,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Switch to this account",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MT5TextPrimary
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE5E7EB))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val accToEdit = targetAcc
                                actionDialogAccount = null
                                editingAccount = accToEdit
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Account",
                            tint = Color(0xFF494D52),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Edit account details & balance",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MT5TextPrimary
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE5E7EB))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val accToDelete = targetAcc
                                actionDialogAccount = null
                                confirmDeleteAccount = accToDelete
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                            .testTag("account_action_delete_${targetAcc.key}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Account",
                            tint = MT5Red,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Delete account",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MT5Red
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionDialogAccount = null }) {
                    Text("Close", color = MT5TextSecondary)
                }
            },
            containerColor = Color.White
        )
    }

    // Delete Confirmation Dialog
    confirmDeleteAccount?.let { targetAcc ->
        AlertDialog(
            onDismissRequest = { confirmDeleteAccount = null },
            title = {
                Text(
                    text = "Delete Account",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MT5TextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${targetAcc.accountName}\" (${targetAcc.accountId}) and its trade history?",
                    fontSize = 14.sp,
                    color = MT5TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(targetAcc.key)
                        confirmDeleteAccount = null
                    },
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, color = MT5Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAccount = null }) {
                    Text("Cancel", color = MT5TextSecondary)
                }
            },
            containerColor = Color.White
        )
    }

    // Manage / Delete Any Account Dialog (from 3-dots menu)
    if (showManageDeleteListDialog) {
        AlertDialog(
            onDismissRequest = { showManageDeleteListDialog = false },
            title = {
                Text(
                    text = "Select Account to Delete",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MT5TextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF6F7F9))
                                .clickable {
                                    showManageDeleteListDialog = false
                                    confirmDeleteAccount = acc
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                MT5AccountBrokerLogo(account = acc, modifier = Modifier.size(30.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = acc.accountName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MT5TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = acc.accountId,
                                        fontSize = 12.sp,
                                        color = MT5TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = MT5Red,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageDeleteListDialog = false }) {
                    Text("Close", color = MT5TextSecondary)
                }
            },
            containerColor = Color.White
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .testTag("accounts_screen")
    ) {
        // Top App Bar matching Screenshot (877674013_1585388529567270_7881693641041831359_n.jpg):
        // Left: [≡  Accounts]   Right: [Certificate   +   ⋮]
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
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Accounts",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF1C1C1E)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Certificate / Security Badge Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Certificate info */ },
                    contentAlignment = Alignment.Center
                ) {
                    MT5CertificateIcon(color = Color(0xFF494D52))
                }

                // 2. Plus (+) Icon -> Opens Add New Account Dialog
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showAddAccountDialog = true }
                        .testTag("accounts_add_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        val w = size.width
                        val h = size.height
                        val stroke = 1.7.dp.toPx()
                        drawLine(
                            color = Color(0xFF494D52),
                            start = Offset(w / 2f, 0f),
                            end = Offset(w / 2f, h),
                            strokeWidth = stroke
                        )
                        drawLine(
                            color = Color(0xFF494D52),
                            start = Offset(0f, h / 2f),
                            end = Offset(w, h / 2f),
                            strokeWidth = stroke
                        )
                    }
                }

                // 3. Three Vertical Dots (⋮) Icon
                Box {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showMenu = true }
                            .testTag("accounts_more_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val cx = size.width / 2f
                            val r = 1.6.dp.toPx()
                            drawCircle(color = Color(0xFF494D52), radius = r, center = Offset(cx, size.height * 0.2f))
                            drawCircle(color = Color(0xFF494D52), radius = r, center = Offset(cx, size.height * 0.5f))
                            drawCircle(color = Color(0xFF494D52), radius = r, center = Offset(cx, size.height * 0.8f))
                        }
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add new account (+)", color = Color(0xFF1C1C1E)) },
                            onClick = {
                                showMenu = false
                                showAddAccountDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit active account", color = Color(0xFF1C1C1E)) },
                            onClick = {
                                showMenu = false
                                editingAccount = account
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete account", color = MT5Red) },
                            onClick = {
                                showMenu = false
                                if (accounts.size <= 1) {
                                    confirmDeleteAccount = account
                                } else {
                                    showManageDeleteListDialog = true
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Deposit / Manage funds", color = Color(0xFF1C1C1E)) },
                            onClick = {
                                showMenu = false
                                viewModel.openTopUpDialog()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("User guide", color = Color(0xFF1C1C1E)) },
                            onClick = {
                                showMenu = false
                                viewModel.openSettingsPage()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About", color = Color(0xFF1C1C1E)) },
                            onClick = {
                                showMenu = false
                                viewModel.openAboutPage()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable Accounts Column: Active Account Card at top + Other Accounts List below
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Top Active Account Summary Card
            ActiveAccountHeroCard(
                account = account,
                onCardClick = { viewModel.closeAccountsPage() },
                onCardLongClick = { actionDialogAccount = account },
                onInfoClick = { actionDialogAccount = account }
            )

            // 2. Secondary Account Cards List (all other accounts)
            val otherAccounts = accounts.filter { it.key != account.key }
            otherAccounts.forEach { secondaryAcc ->
                SecondaryAccountCard(
                    account = secondaryAcc,
                    onClick = {
                        viewModel.switchAccount(secondaryAcc.key)
                    },
                    onLongClick = {
                        actionDialogAccount = secondaryAcc
                    },
                    onInfoClick = {
                        actionDialogAccount = secondaryAcc
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActiveAccountHeroCard(
    account: AccountInfo,
    onCardClick: () -> Unit,
    onCardLongClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val displayName = if (account.accountName == "Pasindu Jeewantha Pelan..." || account.accountName.isBlank()) {
        "Pasindu Jeewantha Pelanage"
    } else {
        account.accountName
    }
    val displayBroker = if (account.brokerName == "Moneta Markets" || account.brokerName.isBlank()) {
        "Moneta Markets (Pty) Ltd"
    } else {
        account.brokerName
    }
    val displayAccountId = normalizeAccountIdDash(account.accountId)
    val displayServerSub = when {
        account.serverName.isBlank() && account.isReal -> "AS08, Hedge"
        account.serverName.isBlank() && !account.isReal -> "AS2 - NY, Hedge"
        account.serverName == "MonetaMarkets-Live" -> "AS08, Hedge"
        account.serverName == "MonetaMarkets-Demo" -> "AS2 - NY, Hedge"
        else -> account.serverName
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCardClick,
                onLongClick = onCardLongClick
            )
            .testTag("account_summary_card"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF6F7F9),
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Top-Right Green "Demo" Badge when account is Demo (!account.isReal)
            if (!account.isReal) {
                MT5DemoPillBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Broker Logo (Red Ring / FundingPips / Fusion Markets)
                MT5AccountBrokerLogo(
                    account = account,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Holder Name
                Text(
                    text = displayName,
                    fontSize = 18.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1C1E),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(5.dp))

                // Broker Name in Blue
                Text(
                    text = displayBroker,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF2A67F6),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Account Number — Server + Info icon (ⓘ)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onInfoClick
                    )
                ) {
                    Text(
                        text = displayAccountId,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF70757A)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    MT5InfoCircleIcon(
                        color = Color(0xFF70757A),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Server Sub-line ("AS2 - NY, Hedge" or "AS08, Hedge")
                Text(
                    text = displayServerSub,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF70757A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Row: [QR Code Icon]      655.37 USD      [Bell Icon]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Left QR icon
                    MT5QrCodeIcon(
                        color = Color(0xFF5F6368),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(18.dp)
                    )

                    // Center Balance + USD
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = formatMT5AccountBalanceNumber(account.balance),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF1C1C1E),
                            modifier = Modifier.alignByBaseline()
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "USD",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF70757A),
                            modifier = Modifier.alignByBaseline()
                        )
                    }

                    // Right Bell icon
                    MT5BellOutlineIcon(
                        color = Color(0xFF5F6368),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Secondary Account Card matching Screenshot 877674013_1585388529567270_7881693641041831359_n.jpg:
 * - Left: 40.dp Broker Logo vertically centered
 * - Center:
 *     Line 1: Account Name (16.5.sp, Medium/SemiBold, #1C1C1E)
 *     Line 2: AccountID — Server + ⓘ icon (14.sp, #70757A)
 *     Line 3: Balance + ", Hedging" (14.sp, #70757A), e.g. "4 911.84 USD, Hedging", "0.00 USD, Hedging", or "0 , Hedging"
 * - Right:
 *     Top-right: Green "Demo" pill badge (when !account.isReal)
 *     Bottom-right: QR code icon (18.dp)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SecondaryAccountCard(
    account: AccountInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val displayName = if (account.accountName == "Pasindu Jeewantha Pelan..." || account.accountName.isBlank()) {
        "Pasindu Jeewantha Pelanage"
    } else {
        account.accountName
    }
    val displayAccountId = normalizeAccountIdDash(account.accountId)
    val isFusionZero = (account.logoType.equals("FUSION", ignoreCase = true) ||
        account.brokerName.contains("Fusion", ignoreCase = true) ||
        account.accountId.contains("Fusion", ignoreCase = true) ||
        account.accountName.contains("Fusion", ignoreCase = true)) && account.balance == 0.0

    val balanceLineText = if (isFusionZero) {
        "0 , Hedging"
    } else {
        "${formatMT5AccountBalanceNumber(account.balance)} USD, Hedging"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("secondary_account_card_${account.key}"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF6F7F9),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Broker Logo
                MT5AccountBrokerLogo(
                    account = account,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Line 1: Account Name
                    Text(
                        text = displayName,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1C1C1E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Line 2: AccountID — Server + ⓘ
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onInfoClick
                        )
                    ) {
                        Text(
                            text = displayAccountId,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF70757A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        MT5InfoCircleIcon(
                            color = Color(0xFF70757A),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Line 3: Balance + ", Hedging"
                    Text(
                        text = balanceLineText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF70757A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Top-Right Green "Demo" Badge if Demo account
            if (!account.isReal) {
                MT5DemoPillBadge(
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // Bottom-Right QR Code Icon
            MT5QrCodeIcon(
                color = Color(0xFF5F6368),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 2.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun MT5DemoPillBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE0F2EA))
            .padding(horizontal = 10.dp, vertical = 2.5.dp)
    ) {
        Text(
            text = "Demo",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF289B63)
        )
    }
}

private fun normalizeAccountIdDash(rawId: String): String {
    if (rawId.contains("\u2014")) return rawId
    if (rawId.contains(" - ")) return rawId.replace(" - ", " \u2014 ")
    return rawId
}

private fun formatMT5AccountBalanceNumber(balance: Double): String {
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ' '
        decimalSeparator = '.'
    }
    return DecimalFormat("#,##0.00", symbols).format(balance)
}

@Composable
fun AddOrEditAccountDialog(
    initialAccount: AccountInfo?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        broker: String,
        id: String,
        server: String,
        isReal: Boolean,
        balance: Double,
        logoType: String
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isNew = initialAccount == null
    var name by remember { mutableStateOf(initialAccount?.accountName ?: "Pasindu Jeewantha Pelanage") }
    var broker by remember { mutableStateOf(initialAccount?.brokerName ?: "Moneta Markets (Pty) Ltd") }
    var id by remember { mutableStateOf(initialAccount?.accountId ?: "1066312 \u2014 MonetaMarkets-Demo") }
    var server by remember { mutableStateOf(initialAccount?.serverName ?: "AS2 - NY, Hedge") }
    var isDemo by remember { mutableStateOf(initialAccount?.let { !it.isReal } ?: true) }
    var balanceText by remember {
        mutableStateOf(
            if (initialAccount != null) {
                String.format(Locale.US, "%.2f", initialAccount.balance)
            } else {
                "655.37"
            }
        )
    }
    var selectedLogoType by remember {
        mutableStateOf(
            when {
                initialAccount == null -> "MONETA"
                initialAccount.logoType.equals("FUNDINGPIPS", ignoreCase = true) ||
                    initialAccount.brokerName.contains("FundingPips", ignoreCase = true) ||
                    initialAccount.accountId.contains("FundingPips", ignoreCase = true) -> "FUNDINGPIPS"
                initialAccount.logoType.equals("FUSION", ignoreCase = true) ||
                    initialAccount.brokerName.contains("Fusion", ignoreCase = true) ||
                    initialAccount.accountId.contains("Fusion", ignoreCase = true) -> "FUSION"
                else -> "MONETA"
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "Add Trading Account" else "Edit Trading Account",
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
                // Quick Presets Row matching the user's screenshot accounts
                Text(
                    text = "Quick Broker Templates:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MT5TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip(
                        label = "Moneta Demo",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            name = "Pasindu Jeewantha Pelanage"
                            broker = "Moneta Markets (Pty) Ltd"
                            id = "1066312 \u2014 MonetaMarkets-Demo"
                            server = "AS2 - NY, Hedge"
                            isDemo = true
                            balanceText = "655.37"
                            selectedLogoType = "MONETA"
                        }
                    )
                    PresetChip(
                        label = "FundingPips",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            name = "pasindu+j"
                            broker = "FundingPips"
                            id = "10757747 \u2014 FundingPips2-SIM"
                            server = "Hedging"
                            isDemo = true
                            balanceText = "4911.84"
                            selectedLogoType = "FUNDINGPIPS"
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip(
                        label = "Moneta Live",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            name = "Pasindu Jeewantha Pelanage"
                            broker = "Moneta Markets (Pty) Ltd"
                            id = "36054607 \u2014 MonetaMarkets-Live"
                            server = "AS08, Hedge"
                            isDemo = false
                            balanceText = "0.00"
                            selectedLogoType = "MONETA"
                        }
                    )
                    PresetChip(
                        label = "Fusion Demo",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            name = "Fusion Markets Pty Ltd"
                            broker = "Fusion Markets Pty Ltd"
                            id = "420784 \u2014 FusionMarkets-Demo"
                            server = "Hedging"
                            isDemo = true
                            balanceText = "0.00"
                            selectedLogoType = "FUSION"
                        }
                    )
                }

                // Broker Logo Selector
                Text(
                    text = "Broker Icon:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MT5TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LogoOptionButton(
                        title = "Moneta",
                        isSelected = selectedLogoType == "MONETA",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedLogoType = "MONETA" }
                    ) {
                        MT5RedRingLogo(modifier = Modifier.size(24.dp))
                    }
                    LogoOptionButton(
                        title = "FundingPips",
                        isSelected = selectedLogoType == "FUNDINGPIPS",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedLogoType = "FUNDINGPIPS" }
                    ) {
                        MT5FundingPipsLogo(modifier = Modifier.size(24.dp))
                    }
                    LogoOptionButton(
                        title = "Fusion",
                        isSelected = selectedLogoType == "FUSION",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedLogoType = "FUSION" }
                    ) {
                        MT5FusionMarketsLogo(modifier = Modifier.size(24.dp))
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name") },
                    placeholder = { Text("e.g. Pasindu Jeewantha Pelanage") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_dialog_name_input")
                )
                OutlinedTextField(
                    value = broker,
                    onValueChange = { broker = it },
                    label = { Text("Broker Name") },
                    placeholder = { Text("e.g. Moneta Markets (Pty) Ltd") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_dialog_broker_input")
                )
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Account ID & Server") },
                    placeholder = { Text("e.g. 1066312 — MonetaMarkets-Demo") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_dialog_id_input")
                )
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Server Sub-line / Mode") },
                    placeholder = { Text("e.g. AS2 - NY, Hedge") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_dialog_server_input")
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Account Balance (USD)") },
                    placeholder = { Text("e.g. 655.37") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_dialog_balance_input")
                )

                // Demo badge toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Demo Account Badge",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MT5TextPrimary
                        )
                        Text(
                            text = if (isDemo) "Shows green 'Demo' pill at top-right" else "Live / Real account (no Demo badge)",
                            fontSize = 11.5.sp,
                            color = MT5TextSecondary
                        )
                    }
                    Switch(
                        checked = isDemo,
                        onCheckedChange = { isDemo = it }
                    )
                }

                // Delete Account button when editing an existing account
                if (!isNew && onDelete != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE5E7EB))
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_account_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Account",
                            tint = MT5Red,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Delete This Account",
                            color = MT5Red,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isNotEmpty()) {
                        val parsedBalance = balanceText.replace(" ", "").replace(",", "").toDoubleOrNull() ?: 0.0
                        onSave(
                            trimmedName,
                            broker.trim().ifEmpty { "Moneta Markets (Pty) Ltd" },
                            id.trim().ifEmpty { "1066312 \u2014 MonetaMarkets-Demo" },
                            server.trim().ifEmpty { "AS2 - NY, Hedge" },
                            !isDemo,
                            parsedBalance,
                            selectedLogoType
                        )
                    }
                },
                modifier = Modifier.testTag("account_dialog_save_button")
            ) {
                Text(
                    text = if (isNew) "Add Account" else "Save",
                    fontWeight = FontWeight.Bold,
                    color = MT5Blue
                )
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

@Composable
private fun PresetChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEEF2F7))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1A73E8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LogoOptionButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFFE8F0FE) else Color(0xFFF6F7F9))
            .border(
                width = if (isSelected) 1.5.dp else 0.7.dp,
                color = if (isSelected) Color(0xFF1A73E8) else Color(0xFFDADCE0),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = title,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF1A73E8) else Color(0xFF5F6368),
            maxLines = 1
        )
    }
}

@Composable
private fun MT5CertificateIcon(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height

        // Dark certificate rectangle with notched left edge
        val certPath = Path().apply {
            moveTo(w * 0.08f, h * 0.16f)
            lineTo(w * 0.92f, h * 0.16f)
            lineTo(w * 0.92f, h * 0.76f)
            lineTo(w * 0.08f, h * 0.76f)
            close()
        }
        drawPath(path = certPath, color = color)

        // Left & right decorative scallop cutouts in white
        for (i in 0..2) {
            val cy = h * (0.26f + i * 0.20f)
            drawCircle(color = Color.White, radius = w * 0.045f, center = Offset(w * 0.08f, cy))
            drawCircle(color = Color.White, radius = w * 0.045f, center = Offset(w * 0.92f, cy))
        }

        // White horizontal lines on left side of certificate
        val stroke = 1.3.dp.toPx()
        drawLine(color = Color.White, start = Offset(w * 0.20f, h * 0.32f), end = Offset(w * 0.46f, h * 0.32f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = Color.White, start = Offset(w * 0.20f, h * 0.46f), end = Offset(w * 0.42f, h * 0.46f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = Color.White, start = Offset(w * 0.20f, h * 0.60f), end = Offset(w * 0.38f, h * 0.60f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Circular seal on right side
        val sealCenter = Offset(w * 0.65f, h * 0.50f)
        drawCircle(color = Color.White, radius = w * 0.19f, center = sealCenter)
        drawCircle(color = color, radius = w * 0.14f, center = sealCenter)
        drawCircle(color = Color.White, radius = w * 0.05f, center = sealCenter)

        // Ribbon tails at bottom of seal
        val ribbonPath = Path().apply {
            moveTo(w * 0.56f, h * 0.68f)
            lineTo(w * 0.54f, h * 0.90f)
            lineTo(w * 0.65f, h * 0.82f)
            lineTo(w * 0.76f, h * 0.90f)
            lineTo(w * 0.74f, h * 0.68f)
            close()
        }
        drawPath(path = ribbonPath, color = color)
    }
}

@Composable
private fun MT5InfoCircleIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val stroke = 1.2.dp.toPx()
        drawCircle(
            color = color,
            radius = r - stroke / 2f,
            style = Stroke(width = stroke)
        )
        // Top dot of 'i'
        drawCircle(
            color = color,
            radius = 1.dp.toPx(),
            center = Offset(size.width / 2f, size.height * 0.30f)
        )
        // Stem of 'i'
        drawLine(
            color = color,
            start = Offset(size.width / 2f, size.height * 0.46f),
            end = Offset(size.width / 2f, size.height * 0.74f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MT5QrCodeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.3.dp.toPx()
        val boxSize = w * 0.38f

        // Top-left square
        drawRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.06f),
            size = Size(boxSize, boxSize),
            style = Stroke(width = stroke)
        )
        // Top-right square
        drawRect(
            color = color,
            topLeft = Offset(w * 0.56f, h * 0.06f),
            size = Size(boxSize, boxSize),
            style = Stroke(width = stroke)
        )
        // Bottom-left square
        drawRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.56f),
            size = Size(boxSize, boxSize),
            style = Stroke(width = stroke)
        )
        // Bottom-right QR dots
        val dot = w * 0.11f
        drawRect(color = color, topLeft = Offset(w * 0.56f, h * 0.56f), size = Size(dot, dot))
        drawRect(color = color, topLeft = Offset(w * 0.80f, h * 0.56f), size = Size(dot, dot))
        drawRect(color = color, topLeft = Offset(w * 0.68f, h * 0.68f), size = Size(dot, dot))
        drawRect(color = color, topLeft = Offset(w * 0.56f, h * 0.80f), size = Size(dot, dot))
        drawRect(color = color, topLeft = Offset(w * 0.80f, h * 0.80f), size = Size(dot, dot))
    }
}

@Composable
private fun MT5BellOutlineIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.3.dp.toPx()

        // Bell body
        val bellPath = Path().apply {
            moveTo(w * 0.20f, h * 0.72f)
            lineTo(w * 0.80f, h * 0.72f)
            lineTo(w * 0.72f, h * 0.44f)
            cubicTo(
                w * 0.72f, h * 0.22f,
                w * 0.28f, h * 0.22f,
                w * 0.28f, h * 0.44f
            )
            close()
        }
        drawPath(
            path = bellPath,
            color = color,
            style = Stroke(width = stroke, join = StrokeJoin.Round)
        )
        // Top knob
        drawCircle(
            color = color,
            radius = 1.1.dp.toPx(),
            center = Offset(w * 0.50f, h * 0.14f)
        )
        // Bottom clapper
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.40f, h * 0.72f),
            size = Size(w * 0.20f, h * 0.16f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}
