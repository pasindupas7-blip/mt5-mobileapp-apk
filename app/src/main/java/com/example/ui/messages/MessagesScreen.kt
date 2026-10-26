package com.example.ui.messages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MT5HamburgerButton
import com.example.viewmodel.TradingViewModel

private data class ChannelItem(
    val title: String,
    val subtitle: String,
    val subscribers: String,
    val isVerified: Boolean = false,
    val avatarBg: Color,
    val avatarLabel: String,
    val avatarSubLabel: String = "",
    val avatarAccent: Color = Color.White,
    val hasDashedRing: Boolean = false
)

private val popularChannels = listOf(
    ChannelItem(
        title = "Algo Trading",
        subtitle = "The best publications of the largest communi...",
        subscribers = "623.8 k",
        isVerified = true,
        avatarBg = Color(0xFFFFFFFF),
        avatarLabel = "MQL5",
        avatarSubLabel = "EN",
        avatarAccent = Color(0xFF1E70B8),
        hasDashedRing = true
    ),
    ChannelItem(
        title = "Scalping",
        subtitle = "Ride the breakout, lock the trend, and let mo...",
        subscribers = "8.4 k",
        avatarBg = Color(0xFF112A12),
        avatarLabel = "SECRET",
        avatarSubLabel = "SCALPER",
        avatarAccent = Color(0xFF4CAF50)
    ),
    ChannelItem(
        title = "Advanced Scalper",
        subtitle = "The best breakout scalper on the market",
        subscribers = "12.7 k",
        avatarBg = Color(0xFF000000),
        avatarLabel = "AS",
        avatarAccent = Color(0xFFE53935)
    ),
    ChannelItem(
        title = "Apollo Forex Systems",
        subtitle = "Trading strategies, indicators, systems, signal...",
        subscribers = "178.3 k",
        avatarBg = Color(0xFF7B0909),
        avatarLabel = "A",
        avatarSubLabel = "APOLLO",
        avatarAccent = Color.White
    ),
    ChannelItem(
        title = "New Gadgets and Game Tech N...",
        subtitle = "New Gadgets and Game Technical News",
        subscribers = "111.6 k",
        avatarBg = Color(0xFF1E88E5),
        avatarLabel = "TECH",
        avatarAccent = Color.White
    ),
    ChannelItem(
        title = "Something Interesting",
        subtitle = "Something interesting and usefull about Met...",
        subscribers = "106.4 k",
        avatarBg = Color(0xFFF3E5E5),
        avatarLabel = "SI",
        avatarAccent = Color(0xFF4E342E)
    ),
    ChannelItem(
        title = "Savia Signals - Boom & Crash Trend",
        subtitle = "High Quality Boom & Crash Trend Signals",
        subscribers = "4.9 k",
        avatarBg = Color(0xFF141414),
        avatarLabel = "S",
        avatarAccent = Color(0xFFD4AF37)
    ),
    ChannelItem(
        title = "Bearish An Bullish EA",
        subtitle = "Bearish & Bullish EA | Opened today! Welcom...",
        subscribers = "7.1 k",
        avatarBg = Color(0xFF0D1B12),
        avatarLabel = "EA",
        avatarAccent = Color(0xFF4CAF50)
    )
)

@Composable
fun MessagesScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // Android MT5 Top Bar matching Screenshot 6:
        // Left: [≡  Pasindu___0077]   Right: [MQID   Search   +   ⋮]
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
                    text = "Pasindu___0077",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF1C1C1E)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // MQID badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF424242))
                        .clickable { viewModel.openSettingsPage() }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MQID",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search messages",
                    tint = Color(0xFF3C4043),
                    modifier = Modifier.size(23.dp)
                )

                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New message",
                    tint = Color(0xFF3C4043),
                    modifier = Modifier.size(25.dp)
                )

                Box {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color(0xFF3C4043),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { showMoreMenu = true }
                    )

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("User guide", color = Color(0xFF1C1C1E)) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.openSettingsPage()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About", color = Color(0xFF1C1C1E)) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.openAboutPage()
                            }
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // Top "System" message row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Blue MQL5 circle avatar
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E70B8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) {
                                    append("MQL")
                                }
                                withStyle(SpanStyle(color = Color(0xFFF5A623), fontWeight = FontWeight.ExtraBold)) {
                                    append("5")
                                }
                            },
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "System",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1C1E)
                            )
                            Text(
                                text = "01.08.2023",
                                fontSize = 13.sp,
                                color = Color(0xFF3C4043)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trading signals:   ...",
                                fontSize = 15.sp,
                                color = Color(0xFF3C4043),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A73E8))
                            )
                        }
                    }
                }
            }

            // "POPULAR CHANNELS" Section Header
            item {
                Text(
                    text = "POPULAR CHANNELS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF3C4043),
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 6.dp)
                )
            }

            // Popular Channels list matching Screenshot 6
            items(popularChannels, key = { it.title }) { channel ->
                ChannelRow(channel = channel)
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: ChannelItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel Avatar
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            if (channel.hasDashedRing) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF1E70B8),
                        radius = size.minDimension / 2f - 1.dp.toPx(),
                        style = Stroke(
                            width = 1.6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF1E70B8), fontWeight = FontWeight.ExtraBold)) {
                                append("MQL")
                            }
                            withStyle(SpanStyle(color = Color(0xFFF5A623), fontWeight = FontWeight.ExtraBold)) {
                                append("5")
                            }
                        },
                        fontSize = 11.sp,
                        lineHeight = 11.sp
                    )
                    Text(
                        text = channel.avatarSubLabel,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E70B8),
                        lineHeight = 8.sp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(channel.avatarBg)
                        .border(0.5.dp, Color(0xFFE0E0E0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = channel.avatarLabel,
                            fontSize = if (channel.avatarLabel.length <= 2) 15.sp else 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = channel.avatarAccent,
                            lineHeight = 15.sp
                        )
                        if (channel.avatarSubLabel.isNotEmpty()) {
                            Text(
                                text = channel.avatarSubLabel,
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 7.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = channel.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.isVerified) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF3B6998),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = channel.subscribers,
                        fontSize = 13.sp,
                        color = Color(0xFF3C4043)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = channel.subtitle,
                fontSize = 15.sp,
                color = Color(0xFF1C1C1E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
