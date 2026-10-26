package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AccountInfo
import kotlin.math.cos
import kotlin.math.sin

/**
 * Standard Android MT5 Top-Left Hamburger (3 horizontal lines) button
 * that opens the slide-out navigation drawer when touched.
 */
@Composable
fun MT5HamburgerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF1C1C1E)
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("drawer_hamburger_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 20.dp, height = 14.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.dp.toPx()
            drawLine(
                color = tint,
                start = Offset(0f, stroke / 2f),
                end = Offset(w, stroke / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(0f, h - stroke / 2f),
                end = Offset(w, h - stroke / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun MT5NavigationDrawerOverlay(
    isOpen: Boolean,
    account: AccountInfo,
    onDismiss: () -> Unit,
    onManageAccountsClick: () -> Unit,
    onTradeClick: () -> Unit,
    onNewsClick: () -> Unit,
    onMailboxClick: () -> Unit,
    onJournalClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCommunityClick: () -> Unit,
    onAlgoTradingClick: () -> Unit,
    onAboutClick: () -> Unit = onSettingsClick
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Dark scrim over right side of screen
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Slide-out white drawer panel (matches Screenshot 1: 865080757_932275263096793_5580651177513903920_n.jpg)
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(240)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(200)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(304.dp)
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume clicks inside drawer */ }
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .testTag("mt5_navigation_drawer")
            ) {
                // Top Account Header (clicking anywhere on the account header or "Manage accounts" opens AccountsScreen)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onManageAccountsClick() }
                        .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Red segmented ring logo icon matching Screenshot 1
                    MT5RedRingLogo(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(36.dp)
                    )

                    Spacer(modifier = Modifier.width(18.dp))

                    val drawerDisplayName = if (account.accountName.startsWith("Pasindu Jeewantha Pelan")) {
                        "Pasindu Jeewantha Pelan..."
                    } else {
                        account.accountName
                    }
                    val drawerDisplayId = if (account.accountId.startsWith("36054607")) {
                        "36054607 - MonetaMarkets..."
                    } else {
                        account.accountId
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = drawerDisplayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF1C1C1E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = drawerDisplayId,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF757575),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Manage accounts",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF1A73E8),
                            modifier = Modifier
                                .clickable { onManageAccountsClick() }
                                .testTag("drawer_manage_accounts")
                        )
                    }
                }

                HorizontalDivider(thickness = 0.7.dp, color = Color(0xFFE5E7EB))

                Spacer(modifier = Modifier.height(6.dp))

                // 1. Trade (custom trending line with 3 nodes icon)
                DrawerCustomRow(
                    title = "Trade",
                    onClick = onTradeClick,
                    iconContent = {
                        MT5TrendingNodesIcon(color = Color(0xFF424242))
                    }
                )

                // 2. News
                DrawerVectorRow(
                    icon = Icons.Outlined.Newspaper,
                    title = "News",
                    onClick = onNewsClick
                )

                // 3. Mailbox (with red circle badge "8")
                DrawerVectorRow(
                    icon = Icons.Outlined.Email,
                    title = "Mailbox",
                    rightBadgeNumber = "8",
                    onClick = onMailboxClick
                )

                // 4. Journal
                DrawerVectorRow(
                    icon = Icons.Outlined.MenuBook,
                    title = "Journal",
                    onClick = onJournalClick
                )

                // 5. Settings
                DrawerVectorRow(
                    icon = Icons.Outlined.Settings,
                    title = "Settings",
                    onClick = onSettingsClick
                )

                // 6. Economic calendar + blue outlined "Ads" pill
                DrawerVectorRow(
                    icon = Icons.Outlined.CalendarToday,
                    title = "Economic calendar",
                    showAdsPill = true,
                    onClick = onNewsClick
                )

                // 7. Traders Community
                DrawerVectorRow(
                    icon = Icons.Outlined.PeopleOutline,
                    title = "Traders Community",
                    onClick = onCommunityClick
                )

                // 8. MQL5 Algo Trading
                DrawerVectorRow(
                    icon = Icons.Outlined.Send,
                    title = "MQL5 Algo Trading",
                    onClick = onAlgoTradingClick
                )

                // 9. User guide
                DrawerVectorRow(
                    icon = Icons.Outlined.HelpOutline,
                    title = "User guide",
                    onClick = onSettingsClick
                )

                // 10. About
                DrawerVectorRow(
                    icon = Icons.Outlined.Info,
                    title = "About",
                    onClick = onAboutClick
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun MT5RedRingLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // Red outer circle
        drawCircle(
            color = Color(0xFFD93025),
            radius = r,
            center = center
        )
        // White center hole
        drawCircle(
            color = Color.White,
            radius = r * 0.42f,
            center = center
        )
        // 3 white radial segment dividers
        val angles = floatArrayOf(35f, 155f, 275f)
        for (deg in angles) {
            val rad = Math.toRadians(deg.toDouble())
            val start = Offset(
                x = center.x + (r * 0.40f * cos(rad)).toFloat(),
                y = center.y + (r * 0.40f * sin(rad)).toFloat()
            )
            val end = Offset(
                x = center.x + (r * 1.02f * cos(rad)).toFloat(),
                y = center.y + (r * 1.02f * sin(rad)).toFloat()
            )
            drawLine(
                color = Color.White,
                start = start,
                end = end,
                strokeWidth = 1.6.dp.toPx()
            )
        }
    }
}

@Composable
fun MT5TrendingNodesIcon(
    color: Color,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val p1 = Offset(w * 0.14f, h * 0.76f)
        val p2 = Offset(w * 0.38f, h * 0.44f)
        val p3 = Offset(w * 0.60f, h * 0.62f)
        val p4 = Offset(w * 0.86f, h * 0.26f)

        val stroke = 2.dp.toPx()
        drawLine(color = color, start = p1, end = p2, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = p2, end = p3, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = p3, end = p4, strokeWidth = stroke, cap = StrokeCap.Round)

        val nodeRadius = 2.7.dp.toPx()
        drawCircle(color = color, radius = nodeRadius, center = p1)
        drawCircle(color = color, radius = nodeRadius, center = p2)
        drawCircle(color = color, radius = nodeRadius, center = p3)
        drawCircle(color = color, radius = nodeRadius, center = p4)
    }
}

@Composable
private fun DrawerCustomRow(
    title: String,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1C1E)
        )
    }
}

@Composable
private fun DrawerVectorRow(
    icon: ImageVector,
    title: String,
    rightBadgeNumber: String? = null,
    showAdsPill: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF424242),
                modifier = Modifier.size(23.dp)
            )
            Spacer(modifier = Modifier.width(24.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1C1E)
            )
            if (showAdsPill) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF1A73E8), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Ads",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1A73E8)
                    )
                }
            }
        }

        if (rightBadgeNumber != null) {
            Box(
                modifier = Modifier
                    .size(21.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE54D42)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rightBadgeNumber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
