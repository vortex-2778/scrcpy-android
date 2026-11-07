package com.scrcpy.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.scrcpy.android.mirror.MirrorController
import com.scrcpy.android.scrcpy.ServerArgs
import com.scrcpy.android.ui.components.InsightCard
import com.scrcpy.android.ui.components.ScreenBackdrop
import com.scrcpy.android.ui.components.SectionCard
import com.scrcpy.android.ui.components.StatusDot
import com.scrcpy.android.usb.UsbMonitor

/**
 * TV dashboard: status hero, primary action first, live phone insight tiles.
 * All colors come from the theme scheme so dark mode always shows light text.
 */
@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLog: () -> Unit,
) {
    val state by MirrorController.state.collectAsState()
    val usbState by UsbMonitor.state.collectAsState()
    val phone by MirrorController.phoneStatus.collectAsState()
    val streaming = state as? MirrorController.State.Streaming

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Scrcpy Android Port",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "Wired low-latency mirroring: phone → TV over USB, with remote " +
                    "control passed back to the phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // --------------------------------------------- status hero card ----
            val (dotColor, statusHeadline, statusSubline) = when (val s = state) {
                is MirrorController.State.Streaming -> tripleStatus(
                    MaterialTheme.colorScheme.primary,
                    "Streaming",
                    streaming?.session?.videoDecoder?.deviceName?.takeIf { it.isNotEmpty() }
                        ?: "Live video + input"
                )
                is MirrorController.State.Connecting -> tripleStatus(
                    androidx.compose.ui.graphics.Color(0xFFF0B429),
                    s.step.ifBlank { "Connecting" },
                    "ADB handshake · pushing scrcpy ${ServerArgs.SERVER_VERSION}"
                )
                is MirrorController.State.Failed -> tripleStatus(
                    MaterialTheme.colorScheme.error,
                    "Stopped",
                    s.message
                )
                MirrorController.State.Idle -> when (val u = usbState) {
                    is UsbMonitor.UsbState.Granted -> tripleStatus(
                        MaterialTheme.colorScheme.tertiary,
                        "Phone ready",
                        "USB debugging authorized — press Connect"
                    )
                    is UsbMonitor.UsbState.DeviceAttached -> tripleStatus(
                        androidx.compose.ui.graphics.Color(0xFFF0B429),
                        "Phone attached",
                        "Press Connect to grant USB access"
                    )
                    UsbMonitor.UsbState.NoDevice -> tripleStatus(
                        MaterialTheme.colorScheme.error,
                        "No phone on USB",
                        "Plug a USB data cable from the phone into the TV"
                    )
                }
            }

            SectionCard(title = "Status") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusDot(dotColor)
                    Text(statusHeadline, style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    statusSubline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state is MirrorController.State.Failed) {
                    OutlinedButton(onClick = { MirrorController.clearError() }) {
                        Text("Dismiss")
                    }
                }
            }

            // -------------------------------------------------- action row ----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val s = state) {
                    is MirrorController.State.Streaming -> {
                        Button(onClick = onOpenPlayer) { Text("Open player") }
                        OutlinedButton(onClick = { MirrorController.disconnect() }) {
                            Text("Stop")
                        }
                    }
                    is MirrorController.State.Connecting -> {
                        Button(onClick = { MirrorController.disconnect() }) { Text("Cancel") }
                    }
                    else -> {
                        Button(onClick = { MirrorController.startConnection() }) {
                            Text("Connect")
                        }
                    }
                }
                OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
                OutlinedButton(onClick = onOpenLog) { Text("Log") }
            }

            // --------------------------------------------- insight tiles ------
            val decoder = streaming?.session?.videoDecoder
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InsightCard(
                    "Battery",
                    phone.batteryPercent?.let { "$it%" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                InsightCard(
                    "Temp",
                    phone.temperatureCelsius?.let { "$it°C" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                InsightCard(
                    "Source",
                    decoder?.meta?.takeIf { it.width > 0 }
                        ?.let { "${it.width}×${it.height}" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                InsightCard(
                    "Frames",
                    streaming?.session?.videoDecoder?.frameCount?.toString() ?: "0",
                    modifier = Modifier.weight(1f)
                )
            }

            SectionCard(title = "How it works") {
                Column {
                    HowLine("1", "The app speaks ADB over USB and authorizes with a persisted RSA key (accept the dialog on the phone once).")
                    HowLine("2", "Official scrcpy ${ServerArgs.SERVER_VERSION} server is pushed to /data/local/tmp and launched via app_process.")
                    HowLine("3", "Video (H.264/H.265) + audio are streamed back and decoded to this screen.")
                    HowLine("4", "D-pad, clicks and scrolling you make here are injected as input on the phone.")
                }
            }
        }
    }
}

private fun tripleStatus(color: androidx.compose.ui.graphics.Color, headline: String, subline: String) =
    Triple(color, headline, subline)

@Composable
private fun HowLine(index: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(
            index,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .width(28.dp)
                .padding(top = 2.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
