package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TickDirection
import com.example.ui.theme.MT5Blue
import com.example.ui.theme.MT5BlueLight
import com.example.ui.theme.MT5GreenLight
import com.example.ui.theme.MT5Red
import com.example.ui.theme.MT5RedLight
import java.util.Locale
import kotlinx.coroutines.delay

data class PipParts(
    val prefix: String,
    val pip: String,
    val superscript: String
)

fun formatPipParts(price: Double, digits: Int): PipParts {
    val formatted = String.format(Locale.US, "%.${digits}f", price)
    return when (digits) {
        5, 7 -> {
            // e.g. "1.12198" -> prefix "1.12", pip "19", superscript "8"
            // e.g. "0.0105271" -> prefix "0.0105", pip "27", superscript "1"
            if (formatted.length >= 7) {
                val prefix = formatted.substring(0, formatted.length - 3)
                val pip = formatted.substring(formatted.length - 3, formatted.length - 1)
                val superScr = formatted.substring(formatted.length - 1)
                PipParts(prefix, pip, superScr)
            } else {
                PipParts(formatted, "", "")
            }
        }
        3 -> {
            // e.g. "156.813" -> prefix "156.", pip "81", superscript "3"
            if (formatted.length >= 5) {
                val prefix = formatted.substring(0, formatted.length - 3)
                val pip = formatted.substring(formatted.length - 3, formatted.length - 1)
                val superScr = formatted.substring(formatted.length - 1)
                PipParts(prefix, pip, superScr)
            } else {
                PipParts(formatted, "", "")
            }
        }
        2 -> {
            // e.g. "4366.14" -> prefix "4366.", pip "14", superscript ""
            val dotIdx = formatted.indexOf('.')
            if (dotIdx != -1 && formatted.length > dotIdx + 1) {
                val prefix = formatted.substring(0, dotIdx + 1)
                val pip = formatted.substring(dotIdx + 1)
                PipParts(prefix, pip, "")
            } else {
                PipParts(formatted, "", "")
            }
        }
        1 -> {
            PipParts("", formatted, "")
        }
        else -> {
            val dotIdx = formatted.indexOf('.')
            if (dotIdx != -1 && formatted.length >= dotIdx + 3) {
                val prefix = formatted.substring(0, formatted.length - 2)
                val pip = formatted.substring(formatted.length - 2)
                PipParts(prefix, pip, "")
            } else {
                PipParts(formatted, "", "")
            }
        }
    }
}

private val MT5QuoteUpBlue = Color(0xFF0A60C2)
private val MT5QuoteDownRed = Color(0xFFC62828)
private val MT5QuoteNeutralGray = Color(0xFF5F6368)

@Composable
fun PipDisplay(
    price: Double,
    digits: Int,
    tickDirection: TickDirection,
    lastTickTime: Long,
    modifier: Modifier = Modifier,
    baseFontSize: TextUnit = 13.sp,
    pipFontSize: TextUnit = 23.sp,
    superFontSize: TextUnit = 12.sp,
    forceColor: Color? = null,
    enableFlashBackground: Boolean = true
) {
    val pipParts = remember(price, digits) { formatPipParts(price, digits) }

    // Flash background state
    var isFlashing by remember { mutableStateOf(false) }
    var flashColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(price, lastTickTime) {
        if (enableFlashBackground && tickDirection != TickDirection.NEUTRAL) {
            flashColor = if (tickDirection == TickDirection.UP) MT5GreenLight else MT5RedLight
            isFlashing = true
            delay(400)
            isFlashing = false
        }
    }

    val animatedBg by animateColorAsState(
        targetValue = if (isFlashing) flashColor else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "pipFlash"
    )

    val textColor = forceColor ?: when (tickDirection) {
        TickDirection.UP -> MT5QuoteUpBlue
        TickDirection.DOWN -> MT5QuoteDownRed
        TickDirection.NEUTRAL -> MT5QuoteNeutralGray
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(animatedBg)
            .padding(horizontal = 1.dp, vertical = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            // Normal prefix digits (e.g. "1.12" or "156.")
            Text(
                text = pipParts.prefix,
                fontSize = baseFontSize,
                fontWeight = FontWeight.Bold,
                color = textColor,
                lineHeight = baseFontSize
            )
            // Enlarged 2-digit pip (e.g. "19" or "81")
            Text(
                text = pipParts.pip,
                fontSize = pipFontSize,
                fontWeight = FontWeight.Bold,
                color = textColor,
                lineHeight = pipFontSize
            )
            // Superscript last fractional pip (e.g. "8" or "3")
            if (pipParts.superscript.isNotEmpty()) {
                Text(
                    text = pipParts.superscript,
                    fontSize = superFontSize,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.offset(y = (-8).dp),
                    lineHeight = superFontSize
                )
            }
        }
    }
}
