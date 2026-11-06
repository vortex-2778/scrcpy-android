package com.scrcpy.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrcpy.android.App
import com.scrcpy.android.data.AudioCodec
import com.scrcpy.android.data.VideoCodec
import com.scrcpy.android.ui.theme.TextSecondary

/** Mirror parameters (applied on the next connect). */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = App.singleton.settings
    var videoCodecState by remember { mutableStateOf(settings.videoCodec) }
    var audioEnabled by remember { mutableStateOf(settings.audioEnabled) }
    var audioCodecState by remember { mutableStateOf(settings.audioCodec) }
    var bitrateMbps by remember { mutableIntStateOf(settings.videoBitRate / 1_000_000) }
    var maxSize by remember { mutableIntStateOf(settings.maxSize) }
    var turnScreenOff by remember { mutableStateOf(settings.turnScreenOff) }
    var stayAwake by remember { mutableStateOf(settings.stayAwake) }
    var powerOffOnClose by remember { mutableStateOf(settings.powerOffOnClose) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Changes apply to the next mirroring session.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Group("Video")
        ChoiceRow(
            label = "Codec",
            options = VideoCodec.entries.map { it.id },
            selected = videoCodecState.id
        ) { pick ->
            val codec = VideoCodec.valueOf(pick.uppercase())
            settings.videoCodec = codec
            videoCodecState = codec
        }
        ChoiceRow(
            label = "Max size",
            options = listOf("native", "1920", "1280", "960"),
            selected = if (maxSize == 0) "native" else maxSize.toString()
        ) { pick ->
            val v = if (pick == "native") 0 else pick.toInt()
            settings.maxSize = v
            maxSize = v
        }
        Text("Bitrate: ${bitrateMbps} Mbps", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = bitrateMbps.toFloat(),
            onValueChange = {
                bitrateMbps = it.toInt()
                settings.videoBitRate = it.toInt() * 1_000_000
            },
            valueRange = 1f..24f,
            steps = 46
        )

        Group("Audio")
        ChoiceRow(
            label = "Capture",
            options = listOf("on", "off"),
            selected = if (audioEnabled) "on" else "off"
        ) { pick ->
            audioEnabled = pick == "on"
            settings.audioEnabled = audioEnabled
        }
        if (audioEnabled) {
            ChoiceRow(
                label = "Codec",
                options = AudioCodec.entries.map { it.id },
                selected = audioCodecState.id
            ) { pick ->
                val codec = AudioCodec.fromId(pick)
                settings.audioCodec = codec
                audioCodecState = codec
            }
        }

        Group("Phone power")
        ChoiceRow(
            label = "Screen off during mirror",
            options = listOf("no", "yes"),
            selected = if (turnScreenOff) "yes" else "no"
        ) { pick ->
            turnScreenOff = pick == "yes"
            settings.turnScreenOff = turnScreenOff
        }
        ChoiceRow(
            label = "Stay awake while plugged",
            options = listOf("no", "yes"),
            selected = if (stayAwake) "yes" else "no"
        ) { pick ->
            stayAwake = pick == "yes"
            settings.stayAwake = stayAwake
        }
        ChoiceRow(
            label = "Display power off on close",
            options = listOf("no", "yes"),
            selected = if (powerOffOnClose) "yes" else "no"
        ) { pick ->
            powerOffOnClose = pick == "yes"
            settings.powerOffOnClose = powerOffOnClose
        }

        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                OutlinedButton(
                    onClick = { onPick(option) },
                    colors = if (option == selected) {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        )
                    } else {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(option)
                }
            }
        }
    }
}
