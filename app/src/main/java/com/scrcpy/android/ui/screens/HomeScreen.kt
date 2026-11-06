package com.scrcpy.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrcpy.android.mirror.MirrorController
import com.scrcpy.android.scrcpy.ServerArgs
import com.scrcpy.android.usb.UsbMonitor
import com.scrcpy.android.ui.components.StatusDot
import com.scrcpy.android.ui.theme.Accent
import com.scrcpy.android.ui.theme.Danger
import com.scrcpy.android.ui.theme.InfoBlue
import com.scrcpy.android.ui.theme.TextSecondary
import com.scrcpy.android.ui.theme.Warm

@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLog: () -> Unit,
) {
    val state by MirrorController.state.collectAsState()
    val usbState by UsbMonitor.state.collectAsState()
    val phone by MirrorController.phoneStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Scrcpy Android Port",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "USB mirror of a phone screen to this TV (wired, with remote control back to the phone).",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        // --- connection row ---
        val dotColor = when {
            state is MirrorController.State.Streaming -> Accent
            state is MirrorController.State.Connecting -> Warm
            state is MirrorController.State.Failed -> Danger
            usbState is UsbMonitor.UsbState.Granted -> InfoBlue
            usbState is UsbMonitor.UsbState.DeviceAttached -> Warm
            else -> Danger
        }
        val statusText = when (val s = state) {
            is MirrorController.State.Streaming -> "Streaming"
            is MirrorController.State.Connecting -> s.step
            is MirrorController.State.Failed -> s.message
            MirrorController.State.Idle -> when (val u = usbState) {
                is UsbMonitor.UsbState.Granted -> "Phone ready over USB"
                is UsbMonitor.UsbState.DeviceAttached -> "Tap connect to grant USB access"
                UsbMonitor.UsbState.NoDevice -> "No phone detected on USB"
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusDot(dotColor)
            Text(statusText, style = MaterialTheme.typography.titleMedium)
        }

        if (state is MirrorController.State.Failed) {
            OutlinedButton(onClick = { MirrorController.clearError() }) {
                Text("Dismiss error")
            }
        }

        // --- actions ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                is MirrorController.State.Streaming -> {
                    Button(
                        onClick = onOpenPlayer,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text("Open player")
                    }
                    Button(
                        onClick = { MirrorController.disconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = Danger)
                    ) {
                        Text("Stop")
                    }
                }
                is MirrorController.State.Connecting -> {
                    Button(onClick = { MirrorController.disconnect() }) {
                        Text("Cancel")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            MirrorController.startConnection(false, "")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text("Connect")
                    }
                }
            }
            OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
            OutlinedButton(onClick = onOpenLog) { Text("Log") }
        }

        // --- phone info ---
        InfoGrid(
            phoneName = (state as? MirrorController.State.Streaming)?.session?.videoDecoder?.deviceName,
            resolution = (state as? MirrorController.State.Streaming)?.session?.videoDecoder?.meta,
            battery = phone.batteryPercent,
            tempC = phone.temperatureCelsius
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "How it works",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "The TV app speaks the Android Debug Bridge protocol directly over USB, pushes the " +
                "official scrcpy ${ServerArgs.SERVER_VERSION} server jar to the phone's /data/local/tmp " +
                "and launches it with app_process. Video is streamed to this screen and remote-control " +
                "events (D-pad, clicks, scroll) are injected on the phone.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun InfoGrid(
    phoneName: String?,
    resolution: com.scrcpy.android.scrcpy.VideoDecoder.Meta?,
    battery: Int?,
    tempC: Int?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoLine("Phone", phoneName ?: "—")
        InfoLine(
            "Resolution",
            if (resolution != null && resolution.width > 0) "${resolution.width}x${resolution.height}" else "—"
        )
        InfoLine("Battery", battery?.let { "$it%" } ?: "—")
        InfoLine("Temperature", tempC?.let { "$it°C" } ?: "—")
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(120.dp), color = TextSecondary)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
