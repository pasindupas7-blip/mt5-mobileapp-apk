package com.example.ui.accounts

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MT5HamburgerButton
import com.example.ui.components.MT5RedRingLogo
import com.example.ui.settings.EditAccountDialog
import com.example.viewmodel.TradingViewModel
import java.util.Locale

@Composable
fun AccountsScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.closeAccountsPage()
    }

    val account by viewModel.account.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditAccountDialog(
            currentAccount = account,
            onDismiss = { showEditDialog = false },
            onSave = { name, broker, id, server ->
                viewModel.updateAccountDetails(name, broker, id, server)
                showEditDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .testTag("accounts_screen")
    ) {
        // Top App Bar matching Screenshot 1 (860515491_2543138086197892_289954658250838977_n.jpg):
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

                // 2. Plus (+) Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showEditDialog = true }
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
                            text = { Text("Edit account details", color = Color(0xFF1C1C1E)) },
                            onClick = {
                                showMenu = false
                                showEditDialog = true
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

        // Main Account Card matching Screenshot 1
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
        val displayAccountId = if (account.accountId.startsWith("36054607")) {
            "36054607 \u2014 MonetaMarkets-Live"
        } else {
            account.accountId
        }
        val displayServerSub = if (account.serverName == "MonetaMarkets-Live" || account.serverName.isBlank()) {
            "AS08, Hedge"
        } else {
            account.serverName
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.closeAccountsPage() }
                .testTag("account_summary_card"),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF6F7F9),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Red 3-segment ring logo
                MT5RedRingLogo(
                    modifier = Modifier.size(40.dp)
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
                    horizontalArrangement = Arrangement.Center
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

                // Server Sub-line ("AS08, Hedge")
                Text(
                    text = displayServerSub,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF70757A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Row: [QR Code Icon]      0.00 USD      [Bell Icon]
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
                            text = String.format(Locale.US, "%.2f", account.balance),
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
