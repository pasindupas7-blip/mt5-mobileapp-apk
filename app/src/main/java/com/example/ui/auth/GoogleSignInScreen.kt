package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.MT5Blue
import com.example.ui.theme.MT5Red
import com.example.ui.theme.MT5TextPrimary
import com.example.ui.theme.MT5TextSecondary

/**
 * Clean, centered one-tap Google Sign-In screen (no manual email/password fields).
 * Supports Android & iOS cross-platform Firestore sync.
 */
@Composable
fun GoogleSignInScreen(
    isSigningIn: Boolean,
    errorMessage: String?,
    onContinueWithGoogle: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF3F7FE),
                        Color(0xFFFFFFFF),
                        Color(0xFFF5F7FA)
                    )
                )
            )
            .drawBehind {
                // Subtle atmospheric trading accent glow behind the centered sign-in card
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A73E8).copy(alpha = 0.09f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.18f),
                        radius = size.minDimension * 0.65f
                    ),
                    radius = size.minDimension * 0.65f,
                    center = Offset(size.width * 0.2f, size.height * 0.18f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF34A853).copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.82f),
                        radius = size.minDimension * 0.55f
                    ),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.85f, size.height * 0.82f)
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .testTag("google_sign_in_screen"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("google_sign_in_card"),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 10.dp,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, Color(0xFFE8ECF2))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Subtle top brand sync emblem
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MT5Blue.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = null,
                        tint = MT5Blue,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Heading
                Text(
                    text = stringResource(R.string.auth_sign_in_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MT5TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("sign_in_heading")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Subtitle
                Text(
                    text = stringResource(R.string.auth_sign_in_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MT5TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                    modifier = Modifier.testTag("sign_in_subtitle")
                )

                // Clean error banner if sign-in is cancelled or fails
                AnimatedVisibility(
                    visible = !errorMessage.isNullOrBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    errorMessage?.let { msg ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp)
                                .testTag("sign_in_error_banner"),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MT5Red,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = msg,
                                    color = Color(0xFF991B1B),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onDismissError,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("dismiss_auth_error_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.auth_dismiss_error),
                                        tint = Color(0xFF991B1B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Full-width rounded white Google Sign-In button with official multicolor "G" logo
                ContinueWithGoogleButton(
                    isLoading = isSigningIn,
                    onClick = onContinueWithGoogle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("continue_with_google_button")
                )
            }
        }
    }
}

/**
 * Reusable full-width rounded white button with subtle border, official multicolor Google "G"
 * vector logo on the left, bold centered label, and loading spinner state.
 */
@Composable
fun ContinueWithGoogleButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFDADCE0)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color(0xFFF8F9FA),
            disabledContentColor = Color(0xFF5F6368)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 1.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("google_sign_in_spinner"),
                    strokeWidth = 2.4.dp,
                    color = MT5Blue
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.auth_signing_in),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3C4043)
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GoogleGVectorLogo(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(22.dp)
                )
                Text(
                    text = stringResource(R.string.auth_continue_with_google),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F1F1F),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Official 4-color Google "G" logo rendered crisply in vector Canvas paths.
 */
@Composable
fun GoogleGVectorLogo(modifier: Modifier = Modifier) {
    val desc = stringResource(R.string.auth_google_logo_desc)
    Canvas(
        modifier = modifier.semantics {
            contentDescription = desc
        }
    ) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.21f
        val inset = strokeWidth / 2f + w * 0.04f
        val arcSize = Size(w - inset * 2f, h - inset * 2f)
        val topLeft = Offset(inset, inset)

        // Top-right to top-left Red arc (#EA4335)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 212f,
            sweepAngle = 104f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )

        // Left Yellow arc (#FBBC05)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 76f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )

        // Bottom Green arc (#34A853)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 42f,
            sweepAngle = 102f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )

        // Right Blue arc (#4285F4)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -6f,
            sweepAngle = 52f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )

        // Horizontal Blue bar of the "G" (#4285F4)
        val barTop = h * 0.415f
        val barBottom = h * 0.595f
        val barPath = Path().apply {
            moveTo(w * 0.51f, barTop)
            lineTo(w * 0.955f, barTop)
            lineTo(w * 0.955f, barBottom)
            lineTo(w * 0.51f, barBottom)
            close()
        }
        drawPath(path = barPath, color = Color(0xFF4285F4))
    }
}
