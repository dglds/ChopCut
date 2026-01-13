package com.chopcut.ui.screen

import android.content.Context
import com.chopcut.data.model.VideoCodec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * Settings screen for configuring export options and app preferences
 *
 * @param onNavigateBack Callback when user wants to go back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(context)
    }

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Export Settings
            ExportSettingsCard(
                settings = settings,
                onCodecChange = { settingsViewModel.updateCodec(it) },
                onBitrateChange = { settingsViewModel.updateBitrate(it) },
                onResolutionPresetChange = { settingsViewModel.updateResolutionPreset(it) },
                onFrameRateChange = { settingsViewModel.updateFrameRate(it) }
            )

            // Audio Settings
            AudioSettingsCard(
                settings = settings,
                onAudioBitrateChange = { settingsViewModel.updateAudioBitrate(it) }
            )

            // Advanced Settings
            AdvancedSettingsCard(
                settings = settings,
                onKeyFrameIntervalChange = { settingsViewModel.updateKeyFrameInterval(it) },
                onUseFastPathChange = { settingsViewModel.updateUseFastPath(it) }
            )

            // Info Card
            InfoCard()

            // Reset Button
            Button(
                onClick = { settingsViewModel.resetToDefaults() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restaurar Padrões")
            }
        }
    }
}

/**
 * Export settings card - codec, bitrate, resolution
 */
@Composable
fun ExportSettingsCard(
    settings: ExportSettings,
    onCodecChange: (VideoCodec) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onResolutionPresetChange: (ResolutionPreset) -> Unit,
    onFrameRateChange: (Int) -> Unit
) {
    var showCodecDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showFrameRateDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Configurações de Exportação",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(16.dp))

            // Codec Selection
            SettingRow(
                label = "Codec de Vídeo",
                value = settings.codec.displayName,
                onClick = { showCodecDialog = true }
            )

            // Resolution Preset
            SettingRow(
                label = "Resolução",
                value = settings.resolutionPreset.displayName,
                onClick = { showResolutionDialog = true }
            )

            // Frame Rate
            SettingRow(
                label = "Frame Rate",
                value = "${settings.frameRate} fps",
                onClick = { showFrameRateDialog = true }
            )

            Spacer(Modifier.height(12.dp))

            // Bitrate Slider
            Text(
                text = "Bitrate: ${settings.bitrateKbps} kbps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = settings.bitrateKbps.toFloat(),
                onValueChange = { onBitrateChange(it.toInt()) },
                valueRange = 500f..20000f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = bitrateDescription(settings.bitrateKbps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }

    // Codec Selection Dialog
    if (showCodecDialog) {
        CodecSelectionDialog(
            currentCodec = settings.codec,
            onCodecSelected = {
                onCodecChange(it)
                showCodecDialog = false
            },
            onDismiss = { showCodecDialog = false }
        )
    }

    // Resolution Selection Dialog
    if (showResolutionDialog) {
        ResolutionSelectionDialog(
            currentPreset = settings.resolutionPreset,
            onPresetSelected = {
                onResolutionPresetChange(it)
                showResolutionDialog = false
            },
            onDismiss = { showResolutionDialog = false }
        )
    }

    // Frame Rate Selection Dialog
    if (showFrameRateDialog) {
        FrameRateSelectionDialog(
            currentFrameRate = settings.frameRate,
            onFrameRateSelected = {
                onFrameRateChange(it)
                showFrameRateDialog = false
            },
            onDismiss = { showFrameRateDialog = false }
        )
    }
}

/**
 * Audio settings card
 */
@Composable
fun AudioSettingsCard(
    settings: ExportSettings,
    onAudioBitrateChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Configurações de Áudio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(Modifier.height(16.dp))

            // Audio Bitrate Slider
            Text(
                text = "Bitrate de Áudio: ${settings.audioBitrateKbps} kbps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = settings.audioBitrateKbps.toFloat(),
                onValueChange = { onAudioBitrateChange(it.toInt()) },
                valueRange = 64f..320f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = audioBitrateDescription(settings.audioBitrateKbps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Formato: AAC (Advanced Audio Coding)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Advanced settings card
 */
@Composable
fun AdvancedSettingsCard(
    settings: ExportSettings,
    onKeyFrameIntervalChange: (Int) -> Unit,
    onUseFastPathChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Configurações Avançadas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            // Key Frame Interval
            var showKeyFrameDialog by remember { mutableStateOf(false) }

            SettingRow(
                label = "Intervalo de Keyframe",
                value = "${settings.keyFrameInterval}s",
                onClick = { showKeyFrameDialog = true }
            )

            if (showKeyFrameDialog) {
                KeyFrameIntervalDialog(
                    currentInterval = settings.keyFrameInterval,
                    onIntervalSelected = {
                        onKeyFrameIntervalChange(it)
                        showKeyFrameDialog = false
                    },
                    onDismiss = { showKeyFrameDialog = false }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Fast Path Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Caminho Rápido (Fast Path)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Usa cópia direta quando possível",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = settings.useFastPath,
                    onCheckedChange = onUseFastPathChange
                )
            }
        }
    }
}

/**
 * Info card with app information
 */
@Composable
fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sobre as Configurações",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Bitrate mais alto = melhor qualidade, arquivo maior. " +
                           "H.265 oferece melhor compressão que H.264.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Reusable setting row component
 */
@Composable
fun SettingRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onClick,
            modifier = Modifier.width(120.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Codec selection dialog
 */
@Composable
fun CodecSelectionDialog(
    currentCodec: VideoCodec,
    onCodecSelected: (VideoCodec) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecione o Codec") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoCodec.entries.forEach { codec ->
                    val isSelected = codec == currentCodec
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = { onCodecSelected(codec) },
                        label = {
                            Column {
                                Text(
                                    text = codec.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = codecDescription(codec),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

/**
 * Resolution preset selection dialog
 */
@Composable
fun ResolutionSelectionDialog(
    currentPreset: ResolutionPreset,
    onPresetSelected: (ResolutionPreset) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolução de Exportação") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResolutionPreset.entries.forEach { preset ->
                    val isSelected = preset == currentPreset
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = { onPresetSelected(preset) },
                        label = {
                            Column {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

/**
 * Frame rate selection dialog
 */
@Composable
fun FrameRateSelectionDialog(
    currentFrameRate: Int,
    onFrameRateSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val frameRates = listOf(24, 25, 30, 50, 60)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Frame Rate") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                frameRates.forEach { fps ->
                    val isSelected = fps == currentFrameRate
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = { onFrameRateSelected(fps) },
                        label = {
                            Text(
                                text = "$fps fps${if (fps >= 50) " (Suave)" else if (fps <= 25) " (Cinemático)" else " (Padrão)"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

/**
 * Key frame interval selection dialog
 */
@Composable
fun KeyFrameIntervalDialog(
    currentInterval: Int,
    onIntervalSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val intervals = listOf(1, 2, 3, 5, 10)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Intervalo de Keyframe") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                intervals.forEach { interval ->
                    val isSelected = interval == currentInterval
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = { onIntervalSelected(interval) },
                        label = {
                            Text(
                                text = "$interval segundo${if (interval > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Intervalos menores = melhor qualidade, arquivo maior",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

// Helper functions

private fun bitrateDescription(kbps: Int): String {
    return when {
        kbps < 1000 -> "Baixa (720p ou menos)"
        kbps < 3000 -> "Média (1080p)"
        kbps < 8000 -> "Alta (1080p ou 4K)"
        else -> "Muito Alta (4K)"
    }
}

private fun audioBitrateDescription(kbps: Int): String {
    return when {
        kbps < 128 -> "Padrão (voz)"
        kbps < 192 -> "Boa (música)"
        else -> "Alta (estudio)"
    }
}

private fun codecDescription(codec: VideoCodec): String {
    return when (codec) {
        VideoCodec.H264 -> "Mais compatível, arquivos maiores"
        VideoCodec.H265 -> "Melhor compressão, requer dispositivo mais recente"
        VideoCodec.VP8 -> "Codec aberto da Google"
        VideoCodec.VP9 -> "Sucessor do VP8, melhor compressão"
        VideoCodec.AV1 -> "Nova geração, melhor compressão"
        VideoCodec.MPEG4 -> "Legado, compatível com dispositivos antigos"
    }
}

// Data classes

/**
 * Resolution presets for export
 */
enum class ResolutionPreset(val displayName: String, val description: String) {
    ORIGINAL("Original", "Mantém resolução do vídeo de origem"),
    QVGA("320x240", "Muito baixo, para vídeos curtos"),
    VGA("640x480", "Baixo, compatível com dispositivos antigos"),
    HD("1280x720", "720p HD - Padrão"),
    FULL_HD("1920x1080", "1080p Full HD - Alta qualidade"),
    QHD("2560x1440", "1440p 2K - Muito alta qualidade"),
    UHD("3840x2160", "2160p 4K - Ultra HD")
}

/**
 * Export settings data class
 */
data class ExportSettings(
    val codec: VideoCodec = VideoCodec.H264,
    val bitrateKbps: Int = 5000,
    val audioBitrateKbps: Int = 128,
    val frameRate: Int = 30,
    val resolutionPreset: ResolutionPreset = ResolutionPreset.ORIGINAL,
    val keyFrameInterval: Int = 2,
    val useFastPath: Boolean = true
)
