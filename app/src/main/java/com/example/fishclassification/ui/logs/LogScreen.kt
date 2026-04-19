package com.example.fishclassification.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fishclassification.util.AppLogger
import com.example.fishclassification.util.LogEntry
import com.example.fishclassification.util.LogLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val logs by AppLogger.logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Keep the newest entry visible as logs stream in.
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("\u2190") }
                },
                actions = {
                    TextButton(onClick = { AppLogger.clear() }) { Text("Clear") }
                },
            )
        },
    ) { innerPadding ->
        if (logs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "No logs yet. Run an inference to populate this screen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Note: only app-level logs are shown here. TFLite's native JNI logs still require `adb logcat`.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(logs) { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> Color(0xFFB8860B) // amber
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurface
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${entry.formatTime()}  ${entry.level.name.padEnd(5)}  ${entry.tag}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
        entry.throwable?.let { t ->
            Text(
                text = t.stackTraceToString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
