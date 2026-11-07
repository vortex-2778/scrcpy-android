package com.scrcpy.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.scrcpy.android.R

/**
 * Roboto (bundled): Regular 400, Medium 500, Bold 700.
 * Light/Thin/SemiBold/Black requests fall back to the closest available weight.
 */
val Roboto = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_regular, FontWeight.Light),
    Font(R.font.roboto_regular, FontWeight.Thin),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_medium, FontWeight.SemiBold),
    Font(R.font.roboto_bold, FontWeight.Bold),
    Font(R.font.roboto_bold, FontWeight.Black),
)

/**
 * Material 3 expressive type scale for the 10-foot TV UI:
 * generous sizes, tight tracking on display text, comfortable body sizes.
 */
val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp, lineHeight = 21.sp, letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Roboto, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp
    ),
)
