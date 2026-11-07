package com.scrcpy.android.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.scrcpy.android.App
import com.scrcpy.android.data.ThemeStyle

/**
 * Every content color is explicitly light: this is a TV-first dark app, so text
 * can never fall back to a dark/black tone on any of the content slots
 * (fixes "black text in dark mode", incl. buttons using bright primaries).
 */
private fun darkScheme(
    primary: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
) = darkColorScheme(
    primary = primary,
    onPrimary = TextPrimary,                       // light text on vivid buttons
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = AccentBright,
    secondary = secondary,
    onSecondary = TextPrimary,
    secondaryContainer = Navy700,
    onSecondaryContainer = TextPrimary,
    tertiary = InfoBlue,
    onTertiary = TextPrimary,
    error = Danger,
    onError = TextPrimary,
    errorContainer = DangerContainer,
    onErrorContainer = OnDangerContainer,
    background = background,
    onBackground = TextPrimary,
    surface = surface,
    onSurface = TextPrimary,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Navy950,
    surfaceContainerLow = Navy850,
    surfaceContainer = Navy800,
    surfaceContainerHigh = Navy700,
    surfaceContainerHighest = Navy600,
    surfaceDim = Navy950,
    surfaceBright = Navy700,
    inverseSurface = Color(0xFFE4E9F5),
    inverseOnSurface = Navy900,
    outline = Navy600,
    outlineVariant = Color(0xFF2B3557),
    scrim = Color(0xE6000000),
)

@Composable
fun ScrcpyAndroidTheme(content: @Composable () -> Unit) {
    val style = runCatching { App.singleton.settings.themeStyle }
        .getOrDefault(ThemeStyle.DYNAMIC)
    val context = LocalContext.current

    val colors = when (style) {
        ThemeStyle.DYNAMIC -> dynamicColors(context)
        ThemeStyle.BLUE -> darkScheme(
            primary = Color(0xFF2E7CB8), secondary = Color(0xFF4C9BD9),
            background = Color(0xFF060B16), surface = Color(0xFF0C1526),
            surfaceVariant = Color(0xFF182740), primaryContainer = InfoBlueContainer,
            onPrimaryContainer = OnInfoBlueContainer
        )
        ThemeStyle.SUNSET -> darkScheme(
            primary = Color(0xFFBB6A2F), secondary = Color(0xFFE0793F),
            background = Color(0xFF150C08), surface = Color(0xFF231511),
            surfaceVariant = Color(0xFF39241B), primaryContainer = Color(0xFF3F1B0C),
            onPrimaryContainer = Color(0xFFFFD8C2)
        )
        ThemeStyle.PURPLE -> darkScheme(
            primary = Color(0xFF7158C9), secondary = Color(0xFF987EE8),
            background = Color(0xFF0B0A16), surface = Color(0xFF151325),
            surfaceVariant = Color(0xFF241F40), primaryContainer = Color(0xFF2A2050),
            onPrimaryContainer = Color(0xFFD9CFFF)
        )
        else -> darkScheme(   // GREEN
            primary = Accent, secondary = AccentBright,
            background = Navy900, surface = Navy850,
            surfaceVariant = Navy700, primaryContainer = AccentContainer,
            onPrimaryContainer = OnAccentContainer
        )
    }

    // TVs are viewed from the couch: enlarge text slightly (mobile keeps 1.0).
    val baseDensity = LocalDensity.current
    val isTv = context.isTelevision()
    val density = if (isTv) {
        Density(baseDensity.density, baseDensity.fontScale * 1.12f)
    } else {
        baseDensity
    }

    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content
        )
    }
}

/**
 * Material You / Monet, forced into the dark app look. The stock dynamic dark
 * scheme puts *dark* text on tonal (pastel) primary buttons which reads as
 * "black text" on a TV, so primary tools are deepened and their content color
 * forced light while everything else keeps the system palette.
 */
private fun dynamicColors(context: Context) =
    if (Build.VERSION.SDK_INT >= 31) {
        val m = dynamicDarkColorScheme(context)
        val deep = lerp(m.primary, Color(0xFF0B0F19), 0.45f)
        m.copy(
            primary = deep,
            onPrimary = TextPrimary,
            secondary = lerp(m.secondary, Color(0xFF0B0F19), 0.35f),
            onSecondary = TextPrimary,
            tertiary = lerp(m.tertiary, Color(0xFF0B0F19), 0.35f),
            onTertiary = TextPrimary,
            primaryContainer = lerp(m.primaryContainer, Color(0xFF111623), 0.25f),
            onPrimaryContainer = TextPrimary,
        )
    } else {
        darkScheme(
            primary = Accent, secondary = AccentBright,
            background = Navy900, surface = Navy850,
            surfaceVariant = Navy700, primaryContainer = AccentContainer,
            onPrimaryContainer = OnAccentContainer
        )
    }

private fun Context.isTelevision(): Boolean {
    val uim = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        ?: return false
    return uim.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
