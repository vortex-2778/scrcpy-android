package com.scrcpy.android.ui.theme

import androidx.compose.ui.graphics.Color

// ---- neutral (dark, TV-friendly) ------------------------------- ------------
val Navy950 = Color(0xFF05070E)   // app background base
val Navy900 = Color(0xFF0A0F1C)   // background / surfaceBase
val Navy850 = Color(0xFF0E1424)   // surface
val Navy800 = Color(0xFF131A2C)   // surfaceContainer
val Navy700 = Color(0xFF19213A)   // surfaceContainerHigh
val Navy600 = Color(0xFF232D4E)   // outline-ish

// ---- text ------------------------------------------------------------------
val TextPrimary = Color(0xFFEAF0FA)
val TextSecondary = Color(0xFFA7B1CC)
val TextHint = Color(0xFF6E7794)

// ---- accents (deep tones so light text stays readable on top) --------------
val Accent = Color(0xFF2E9E67)          // scrcpy green (primary/deep)
val AccentBright = Color(0xFF4FD08F)    // indicators only, never behind text
val AccentDim = Color(0xFF14532E)
val AccentContainer = Color(0xFF0F3D27)
val OnAccentContainer = Color(0xFFB9F2D5)
val Warm = Color(0xFFF0B429)            // connecting / battery heat
val Danger = Color(0xFFCF4A3F)
val DangerContainer = Color(0xFF3E120D)
val OnDangerContainer = Color(0xFFFFD9D3)
val InfoBlue = Color(0xFF3B8ED0)
val InfoBlueContainer = Color(0xFF0E2C46)
val OnInfoBlueContainer = Color(0xFFC4E4FF)

/** Swatch color used for the theme selector dots. */
val PaletteSwatches = mapOf<String, Color>(
    "DYNAMIC" to Color(0xFF7C8BF2),
    "GREEN" to Color(0xFF2E9E67),
    "BLUE" to Color(0xFF3B8ED0),
    "SUNSET" to Color(0xFFE07043),
    "PURPLE" to Color(0xFF8A6BE8)
)
