package com.scrcpy.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrcpy.android.util.AppLog
import com.scrcpy.android.ui.components.SectionCard
import com.scrcpy.android.ui.theme.TextSecondary

@Composable
fun LogScreen(onBack: () -> Unit) {
    val lines by AppLog.linesFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("Log", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "${lines.size} lines (newest last)",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
        SectionCard(title = "Session trace") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (lines.isEmpty()) {
                    item {
                        Text(
                            "Nothing logged yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                items(lines) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if ("E/" in line) FontWeight.SemiBold
                            else FontWeight.Normal
                        ),
                        color = when {
                            "E/" in line -> MaterialTheme.colorScheme.error
                            "W/" in line -> MaterialTheme.colorScheme.tertiary
                            else -> TextSecondary
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = { AppLog.clear() }) { Text("Clear") }
        }
    }
}

