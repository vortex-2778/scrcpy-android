package com.scrcpy.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Navy900,
    secondary = InfoBlue,
    onSecondary = Navy900,
    error = Danger,
    background = Navy900,
    onBackground = TextPrimary,
    surface = Navy850,
    onSurface = TextPrimary,
    surfaceVariant = Navy700,
    onSurfaceVariant = TextSecondary,
    outline = Navy600,
)

@Composable
fun ScrcpyAndroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
