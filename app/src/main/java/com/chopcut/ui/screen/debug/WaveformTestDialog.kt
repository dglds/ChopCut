package com.chopcut.ui.screen.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chopcut.data.audio.WaveformConfig
import com.chopcut.data.audio.WaveformPreset
import com.chopcut.ui.components.WaveformData
import com.chopcut.ui.theme.ChopCutSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveformTestDialog(
    videoUri: android.net.Uri,
    viewModel: WaveformTestViewModel,
    onDismiss: () -> Unit = {},
    onReload: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🧪 Testar WaveForm")
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp).height(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(0.95f),
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(ChopCutSpacing.sm)
            ) {
                PresetSection(
                    selectedPreset = state.currentConfig.preset,
                    onPresetSelected = { 
                        viewModel.setPreset(it)
                        viewModel.regenerate(videoUri)
                    }
                )
                
                Spacer(Modifier.height(ChopCutSpacing.md))
                
                ConfigSection(
                    config = state.currentConfig
                )
                
                Spacer(Modifier.height(ChopCutSpacing.md))
                
                PreviewSection(
                    currentWaveform = state.waveformData,
                    metrics = state.extractionMetrics
                )
                
                state.error?.let { error ->
                    Spacer(Modifier.height(ChopCutSpacing.md))
                    Text(
                        text = "Erro: $error",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.xs)
            ) {
                OutlinedButton(
                    onClick = { viewModel.regenerate(videoUri) },
                    enabled = !state.isLoading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.width(16.dp))
                    Text("Regenerar")
                }
                
                OutlinedButton(
                    onClick = onReload,
                    enabled = !state.isLoading
                ) {
                    Text("Recarregar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isLoading) {
                Text("Fechar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSection(
    selectedPreset: WaveformPreset,
    onPresetSelected: (WaveformPreset) -> Unit
) {
    Column {
        Text("Presets", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(ChopCutSpacing.xs))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.xs),
            modifier = Modifier.fillMaxWidth()
        ) {
            WaveformPreset.values().forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onPresetSelected(preset) },
                    label = { Text(preset.displayName) }
                )
            }
        }
    }
}

@Composable
private fun ConfigSection(
    config: WaveformConfig
) {
    Column {
        Text("Configurações", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(ChopCutSpacing.xs))
        
        ConfigRow("Sampling Rate", "1:${config.samplingRate}")
        ConfigRow("Threshold", "${(config.minThreshold * 100).toInt()}% - ${(config.maxThreshold * 100).toInt()}%")
        ConfigRow("Sensibilidade", "${config.sensitivityMultiplier}x")
        ConfigRow("Usar Mediana", if (config.useMedian) "Sim" else "Não")
        ConfigRow("Altura Silêncio", "${(config.silenceHeight * 100).toInt()}%")
        ConfigRow("Barras Alvo", config.targetBarCount.toString())
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PreviewSection(
    currentWaveform: WaveformData?,
    metrics: com.chopcut.ui.screen.debug.ExtractionMetrics?
) {
    Column {
        Text("Preview", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        
        Spacer(Modifier.height(ChopCutSpacing.xs))
        
        if (currentWaveform != null) {
            WaveformPreview(
                waveformData = currentWaveform,
                metrics = metrics
            )
        } else {
            Text(
                text = "Gere o waveform para ver o preview",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun WaveformPreview(
    waveformData: WaveformData,
    metrics: com.chopcut.ui.screen.debug.ExtractionMetrics?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ChopCutSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WaveformPlaceholder(waveformData.amplitudes.size)
        
        Spacer(Modifier.height(ChopCutSpacing.xs))
        
        metrics?.let { m ->
            Text(
                text = "⏱ ${m.extractionTimeMs}ms | 📊 ${m.barsGenerated} barras",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun WaveformPlaceholder(barCount: Int) {
    val bars = minOf(barCount, 50)
    val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            val barWidth = width / bars
            
            for (i in 0 until bars) {
                val barHeight = (height * 0.1f + height * 0.7f * (i % 5) / 4f).coerceIn(2.dp.toPx(), height * 0.9f)
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = i * barWidth + 1.dp.toPx(),
                        y = centerY - barHeight / 2
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        width = (barWidth - 2.dp.toPx()).coerceAtLeast(1.dp.toPx()),
                        height = barHeight
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}
