package com.jbase.demo

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    private val host = AdsDemoHost()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by host.state.collectAsState()
                JBaseDemoScreen(
                    state = state,
                    onInitialize = { host.initialize(this) },
                    onSetMode = host::setMode,
                    onShow = { host.show(this, it) },
                    onHideAll = host::hideAll
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.unity3d.player.UnityPlayer.currentActivity = this
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JBaseDemoScreen(
    state: AdsUiState,
    onInitialize: () -> Unit,
    onSetMode: (AdNetworkMode) -> Unit,
    onShow: (AdFormat) -> Unit,
    onHideAll: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(state, onInitialize)
            NetworkModePicker(state.mode, onSetMode)
            AdActionGrid(onShow, onHideAll)
            AdContainerPlaceholder()
            LogPanel(state)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(state: AdsUiState, onInitialize: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("JBase Ads Kotlin Demo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Mode: ${state.mode.label}", color = Color(0xFF4B5563))
            }
            Button(
                onClick = onInitialize,
                enabled = !state.initializing
            ) {
                Text(if (state.initializing) "Initializing" else "Init")
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("AdMob AAR", state.admobReady)
            StatusChip("MAX AAR", state.maxReady)
            if (state.lastError != null) {
                AssistChip(
                    onClick = {},
                    label = { Text("Error: ${state.lastError}") }
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text("$label: ${if (active) "loaded" else "missing"}") }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetworkModePicker(
    current: AdNetworkMode,
    onSetMode: (AdNetworkMode) -> Unit
) {
    Section("Network") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AdNetworkMode.entries.forEach { mode ->
                val selected = mode == current
                if (selected) {
                    Button(onClick = { onSetMode(mode) }) { Text(mode.label) }
                } else {
                    OutlinedButton(onClick = { onSetMode(mode) }) { Text(mode.label) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdActionGrid(onShow: (AdFormat) -> Unit, onHideAll: () -> Unit) {
    Section("Ad formats") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AdFormat.entries.forEach { format ->
                Button(
                    onClick = { onShow(format) },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor(format))
                ) {
                    Text(format.label)
                }
            }
            OutlinedButton(onClick = onHideAll) {
                Text("Hide all")
            }
        }
    }
}

@Composable
private fun AdContainerPlaceholder() {
    Section("View host") {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(Color(0xFFE5E7EB), RoundedCornerShape(8.dp)),
            factory = { context ->
                FrameLayout(context).apply {
                    id = android.R.id.content
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            }
        )
        Text(
            "Android View host is available for AARs that attach banner/MREC views to the activity decor.",
            color = Color(0xFF64748B),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LogPanel(state: AdsUiState) {
    Section("Log") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111827), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (state.logs.isEmpty()) {
                Text("No events yet", color = Color(0xFFCBD5E1))
            } else {
                state.logs.forEach { line ->
                    Text(line, color = Color(0xFFE5E7EB), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
        content()
    }
}

private fun buttonColor(format: AdFormat): Color {
    return when (format) {
        AdFormat.INTER, AdFormat.REWARD, AdFormat.AOA -> Color(0xFF2563EB)
        AdFormat.BANNER, AdFormat.MREC -> Color(0xFF059669)
        AdFormat.NATIVE_FULLSCREEN,
        AdFormat.NATIVE_BANNER,
        AdFormat.NATIVE_MREC,
        AdFormat.NATIVE_COLLAPSIBLE -> Color(0xFF7C3AED)
    }
}
