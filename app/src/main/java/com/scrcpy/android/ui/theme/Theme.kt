package com.scrcpy.android.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.scrcpy.android.App
import com.scrcpy.android.data.ThemeStyle

private fun scheme(
    primary: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    accentText: Color = TextPrimary,
) = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFF07101F),
    secondary = secondary,
    onSecondary = Color(0xFF07101F),
    error = Danger,
    background = background,
    onBackground = accentText,
    surface = surface,
    onSurface = accentText,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A4569),
)

@Composable
fun ScrcpyAndroidTheme(content: @Composable () -> Unit) {
    val style = runCatching { App.singleton.settings.themeStyle }.getOrDefault(ThemeStyle.DYNAMIC)
    val context = LocalContext.current
    val dark = true // TV-first dark app

    val colors = when {
        style == ThemeStyle.DYNAMIC && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        style == ThemeStyle.BLUE -> scheme(
            primary = InfoBlue, secondary = Color(0xFF7EC8FF),
            background = Color(0xFF071426), surface = Color(0xFF0C1D33),
            surfaceVariant = Color(0xFF152C49)
        )
        style == ThemeStyle.SUNSET -> scheme(
            primary = Color(0xFFFF8A5C), secondary = Warm,
            background = Color(0xFF170F14), surface = Color(0xFF241722),
            surfaceVariant = Color(0xFF33202F)
        )
        style == ThemeStyle.PURPLE -> scheme(
            primary = Color(0xFFB388FF), secondary = Color(0xFF82B1FF),
            background = Color(0xFF0F0B1A), surface = Color(0xFF1A1429),
            surfaceVariant = Color(0xFF281F3D)
        )
        else -> scheme(
            primary = Accent, secondary = InfoBlue,
            background = Navy900, surface = Navy850,
            surfaceVariant = Navy700
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
        MaterialTheme(colorScheme = colors, content = content)
    }
}

private fun Context.isTelevision(): Boolean {
    val uim = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager ?: return false
    return uim.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
