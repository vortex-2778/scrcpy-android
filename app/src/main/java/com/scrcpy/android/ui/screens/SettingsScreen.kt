package com.scrcpy.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scrcpy.android.App
import com.scrcpy.android.data.AudioCodec
import com.scrcpy.android.data.DisplayMode
import com.scrcpy.android.data.ThemeStyle
import com.scrcpy.android.data.VideoCodec
import com.scrcpy.android.ui.components.SectionCard
import com.scrcpy.android.ui.theme.PaletteSwatches

/**
 * Dedicated settings page, ordered like a TV settings menu:
 *  1) Connection  2) Video  3) Audio  4) Phone behavior  5) Appearance.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = App.singleton.settings

    var displayMode by remember { mutableStateOf(settings.displayMode) }
    var desktopRes by remember { mutableStateOf(settings.desktopRes) }
    var videoCodec by remember { mutableStateOf(settings.videoCodec) }
    var maxSize by remember { mutableIntStateOf(settings.maxSize) }
    var maxFps by remember { mutableIntStateOf(settings.maxFps) }
    var bitrateMbps by remember { mutableIntStateOf(settings.videoBitRate / 1_000_000) }
    var audioEnabled by remember { mutableStateOf(settings.audioEnabled) }
    var audioCodec by remember { mutableStateOf(settings.audioCodec) }
    var turnScreenOff by remember { mutableStateOf(settings.turnScreenOff) }
    var stayAwake by remember { mutableStateOf(settings.stayAwake) }
    var powerOffOnClose by remember { mutableStateOf(settings.powerOffOnClose) }
    var themeStyle by remember { mutableStateOf(settings.themeStyle) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Changes apply to the next mirroring session.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ------------------------------------------------- 1) Connection ----
        SectionCard(
            title = "Connection",
            subtitle = "How the phone screen reaches this TV"
        ) {
            segmented(
                label = "Display mode",
                options = DisplayMode.entries.map { it.label },
                selectedIndex = DisplayMode.entries.indexOf(displayMode)
            ) { idx ->
                displayMode = DisplayMode.entries[idx]
                settings.displayMode = displayMode
            }
            if (displayMode != DisplayMode.MIRROR) {
                segmented(
                    label = "Virtual display size",
                    options = listOf("720p · 160dpi", "1080p · 160dpi", "1440p · 200dpi"),
                    selectedIndex = when (desktopRes) {
                        "1920x1080/160" -> 1
                        "2560x1440/200" -> 2
                        else -> 0
                    }
                ) { idx ->
                    desktopRes = when (idx) {
                        1 -> "1920x1080/160"
                        2 -> "2560x1440/200"
                        else -> "1280x720/160"
                    }
                    settings.desktopRes = desktopRes
                }
            }
        }

        // ----------------------------------------------------- 2) Video -----
        SectionCard(
            title = "Video",
            subtitle = "Encoding negotiated with the scrcpy server"
        ) {
            segmented(
                label = "Codec",
                options = VideoCodec.entries.map { it.id.uppercase() },
                selectedIndex = VideoCodec.entries.indexOf(videoCodec)
            ) { idx ->
                videoCodec = VideoCodec.entries[idx]
                settings.videoCodec = videoCodec
            }
            segmented(
                label = "Max size (0 = native)",
                options = listOf("native", "1920", "1280", "960"),
                selectedIndex = when (maxSize) {
                    1920 -> 1; 1280 -> 2; 960 -> 3; else -> 0
                }
            ) { idx ->
                maxSize = when (idx) {
                    1 -> 1920; 2 -> 1280; 3 -> 960; else -> 0
                }
                settings.maxSize = maxSize
            }
            segmented(
                label = "Max FPS (0 = uncapped)",
                options = listOf("0", "30", "60", "90", "120"),
                selectedIndex = when (maxFps) {
                    30 -> 1; 60 -> 2; 90 -> 3; 120 -> 4; else -> 0
                }
            ) { idx ->
                maxFps = when (idx) {
                    1 -> 30; 2 -> 60; 3 -> 90; 4 -> 120; else -> 0
                }
                settings.maxFps = maxFps
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bitrate", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$bitrateMbps Mbps",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = bitrateMbps.toFloat(),
                    onValueChange = {
                        bitrateMbps = it.toInt()
                        settings.videoBitRate = bitrateMbps * 1_000_000
                    },
                    valueRange = 1f..24f,
                    steps = 46
                )
            }
        }

        // ----------------------------------------------------- 3) Audio -----
        SectionCard(
            title = "Audio",
            subtitle = "Phone sound played on the TV (Android 11+)"
        ) {
            switch(
                title = "Capture & play audio",
                subtitle = if (audioEnabled) "On — audio sockets opened" else "Off — video only",
                checked = audioEnabled
            ) { value ->
                audioEnabled = value
                settings.audioEnabled = value
            }
            if (audioEnabled) {
                segmented(
                    label = "Codec",
                    options = AudioCodec.entries.map { it.id },
                    selectedIndex = AudioCodec.entries.indexOf(audioCodec)
                ) { idx ->
                    audioCodec = AudioCodec.entries[idx]
                    settings.audioCodec = audioCodec
                }
            }
        }

        // -------------------------------------------- 4) Phone behavior ----
        SectionCard(
            title = "Phone",
            subtitle = "What happens on the phone while it streams"
        ) {
            switch(
                title = "Turn screen off",
                subtitle = "Streaming continues with the phone display dark",
                checked = turnScreenOff
            ) { value ->
                turnScreenOff = value
                settings.turnScreenOff = value
            }
            switch(
                title = "Stay awake",
                subtitle = "Keep awake while plugged into USB",
                checked = stayAwake
            ) { value ->
                stayAwake = value
                settings.stayAwake = value
            }
            switch(
                title = "Power off on close",
                subtitle = "Switch the display off when you stop mirroring",
                checked = powerOffOnClose
            ) { value ->
                powerOffOnClose = value
                settings.powerOffOnClose = value
            }
        }

        // ------------------------------------------------ 5) Appearance -----
        SectionCard(
            title = "Appearance",
            subtitle = "Material 3 color scheme"
        ) {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeStyle.entries.forEach { style ->
                    val selected = style == themeStyle
                    val swatch = PaletteSwatches[style.name] ?: MaterialTheme.colorScheme.primary
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            themeStyle = style
                            settings.themeStyle = style
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .then(
                                    if (selected) Modifier.border(
                                        3.dp,
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    ) else Modifier
                                )
                                .padding(4.dp)
                                .background(swatch, CircleShape)
                        )
                        Text(
                            style.name.lowercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                "Monet / dynamic uses Material You colors on Android 12+.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---------------------------------------------------------- exit ----
        Button(onClick = onBack) {
            Text("Back")
        }
        Text(
            "While mirroring: remote keys go to the phone · MENU shows the overlay · " +
                "BACK exits the player · Ctrl+Esc (keyboard) exits the app",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ------------------------------------------------------------ helpers -------

/**
 * Segmented-button selector. Long option lists wrap into chunks of 3 rows so
 * they never overflow the 10-foot UI.
 */
@Composable
private fun segmented(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        options.chunked(3).forEachIndexed { chunkIndex, chunk ->
            val offset = chunkIndex * 3
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                chunk.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = selectedIndex == offset + i,
                        onClick = { onPick(offset + i) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = i,
                            count = chunk.size
                        ),
                        colors = segmentedColors(),
                        label = { Text(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun segmentedColors(): SegmentedButtonColors =
    SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        activeBorderColor = MaterialTheme.colorScheme.primary,
        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
    )

@Composable
private fun switch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        )
    }
}
