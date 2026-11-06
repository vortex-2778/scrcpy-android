package com.scrcpy.android.ui.screens

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.scrcpy.android.mirror.MirrorController
import com.scrcpy.android.ui.theme.Navy800
import com.scrcpy.android.ui.theme.TextSecondary
import com.scrcpy.android.ui.theme.Warm

/**
 * Full-screen video view with chips (resolution/battery/temp) and phone-side
 * display actions. Input is intercepted at MainActivity level (D-pad + mouse).
 */
@Composable
fun PlayerScreen(onExit: () -> Unit) {
    val state by MirrorController.state.collectAsState()
    val phone by MirrorController.phoneStatus.collectAsState()
    val session = (state as? MirrorController.State.Streaming)?.session
    val failed = state as? MirrorController.State.Failed

    DisposableEffect(Unit) {
        onDispose { session?.setSurface(null) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).apply {
                    holder.setFormat(android.graphics.PixelFormat.OPAQUE)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            val s = currentStreamingSession()
                            if (holder.surface.isValid) {
                                s?.setSurface(holder.surface)
                            }
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            val s = currentStreamingSession()
                            if (holder.surface.isValid) {
                                s?.setSurface(holder.surface)
                            }
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            currentStreamingSession()?.setSurface(null)
                        }
                    })
                }
            }
        )

        // status chips
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("LIVE ${session?.videoDecoder?.frameCount ?: 0}f")
            session?.videoDecoder?.deviceName?.takeIf { it.isNotEmpty() }?.let { Chip(it) }
            val m = session?.videoDecoder?.meta
            if (m != null && m.width > 0) Chip("${m.width}x${m.height}")
            phone.batteryPercent?.let { Chip("🔋 $it%") }
            phone.temperatureCelsius?.let { Chip("$it°C") }
        }

        // controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { MirrorController.setPhoneDisplay(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Warm)
                ) { Text("Phone screen on") }
                Button(
                    onClick = { MirrorController.setPhoneDisplay(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy800)
                ) { Text("off") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { session?.controlWriter?.rotateDevice() },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy800)
                ) { Text("Rotate") }
                Button(onClick = onExit) { Text("Exit") }
            }
        }

        if (session == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Not streaming", style = MaterialTheme.typography.headlineSmall)
                Text(
                    failed?.message ?: "Connect a phone and start mirroring from Home.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

fun currentStreamingSession(): com.scrcpy.android.scrcpy.ScrcpySession? =
    (MirrorController.state.value as? MirrorController.State.Streaming)?.session

@Composable
fun Chip(text: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .background(Navy800.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
