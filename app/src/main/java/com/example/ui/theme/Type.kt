package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val MT5RobotoCondensedFontFamily = FontFamily(
    Font(R.font.roboto_condensed, FontWeight.Normal),
    Font(R.font.roboto_condensed_500, FontWeight.Medium),
    Font(R.font.roboto_condensed_700, FontWeight.SemiBold),
    Font(R.font.roboto_condensed_700, FontWeight.Bold)
)

val MT5RobotoFontFamily = FontFamily(
    Font(R.font.roboto, FontWeight.Normal),
    Font(R.font.roboto_500, FontWeight.Medium),
    Font(R.font.roboto_700, FontWeight.SemiBold),
    Font(R.font.roboto_700, FontWeight.Bold)
)

// Set of Material typography styles
val Typography =
    Typography(
        bodyLarge =
            TextStyle(
                fontFamily = MT5RobotoFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            )
    )
