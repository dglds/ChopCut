package com.chopcut.ui.screen

import android.content.Context
import com.chopcut.data.model.DimensionPreset
import com.chopcut.data.model.ThumbnailFormat
import com.chopcut.data.model.ThumbnailSettings
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.ui.components.buttons.ChopCutPrimaryButton
import com.chopcut.ui.components.cards.ChopCutCard
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.components.feedback.LoadingState
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.ui.theme.ErrorDark
import com.chopcut.ui.theme.OnSurface
import com.chopcut.ui.theme.Primary
import kotlinx.coroutines.launch

/**
 * Settings screen for configuring export options and app preferences
 *
 * @param onNavigateBack Callback when user wants to go back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToDevelop: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(context)
    }

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val thumbnailSettings by (settingsViewModel as? SettingsViewModel)?.thumbnailSettings?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(ThumbnailSettings()) }
    val cleanupResult by (settingsViewModel as? SettingsViewModel)?.cleanupResult?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(ChopCutSpacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ChopCutSpacing.md)
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

            // Thumbnail Settings
            ThumbnailSettingsCard(
                thumbnailSettings = thumbnailSettings,
                onThumbsPerSecondChange = { (settingsViewModel as? SettingsViewModel)?.updateThumbsPerSecond(it) },
                onQualityChange = { (settingsViewModel as? SettingsViewModel)?.updateThumbnailQuality(it) },
                onFormatChange = { (settingsViewModel as? SettingsViewModel)?.updateThumbnailFormat(it) },
                onDimensionChange = { (settingsViewModel as? SettingsViewModel)?.updateThumbnailDimension(it) }
            )

            // Info Card
            InfoCard()

            // Storage Card
            StorageSettingsCard(
                onClearCache = { settingsViewModel.clearChopCutDirectory() },
                cleanupResult = cleanupResult,
                onDismissResult = { settingsViewModel.clearCleanupResult() }
            )

            // Developer Card
            DeveloperSettingsCard(
                onNavigateToDevelop = onNavigateToDevelop
            )

            // Reset Button
            ChopCutPrimaryButton(
                text = "Restaurar Padrões",
                onClick = { settingsViewModel.resetToDefaults() },
                modifier = Modifier.fillMaxWidth()
            )
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

    ChopCutCard(
        modifier = Modifier.fillMaxWidth(),
        showShadow = true
    ) {
        Column {
            Text(
                text = "Configurações de Exportação",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(ChopCutSpacing.md))

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

            Spacer(Modifier.height(ChopCutSpacing.sm))

            // Bitrate Slider
            Text(
                text = "Bitrate: ${settings.bitrateKbps} kbps",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
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
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
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
    ChopCutCard(
        modifier = Modifier.fillMaxWidth(),
        showShadow = true
    ) {
        Column {
            Text(
                text = "Configurações de Áudio",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(ChopCutSpacing.md))

            // Audio Bitrate Slider
            Text(
                text = "Bitrate de Áudio: ${settings.audioBitrateKbps} kbps",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
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
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(ChopCutSpacing.xs))

            Text(
                text = "Formato: AAC (Advanced Audio Coding)",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
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
    ChopCutCard(
        modifier = Modifier.fillMaxWidth(),
        showShadow = true
    ) {
        Column {
            Text(
                text = "Configurações Avançadas",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(ChopCutSpacing.md))

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

            Spacer(Modifier.height(ChopCutSpacing.sm))

            // Fast Path Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Caminho Rápido (Fast Path)",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Usa cópia direta quando possível",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
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
    ChopCutCard(
        modifier = Modifier.fillMaxWidth(),
        showShadow = false
    ) {
        Row(
            modifier = Modifier.padding(ChopCutSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(ChopCutSpacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sobre as Configurações",
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Bitrate mais alto = melhor qualidade, arquivo maior. " +
                           "H.265 oferece melhor compressão que H.264.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Storage settings card - clear cache/directories
 */
@Composable
fun StorageSettingsCard(
    onClearCache: () -> Unit,
    cleanupResult: CleanupResult?,
    onDismissResult: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(ChopCutSpacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorDark,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(ChopCutSpacing.xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Armazenamento",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Limpar pasta de exportações (Movies/ChopCut)",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(ChopCutSpacing.sm))

            androidx.compose.material3.Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = ErrorDark
                )
            ) {
                Text("Limpar Vídeos Exportados")
            }
        }
    }

    // Confirmation dialog
    if (showConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Limpar Vídeos Exportados?") },
            text = {
                Text(
                    "Isso irá excluir todos os vídeos da pasta Movies/ChopCut. " +
                    "Esta ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onClearCache()
                    }
                ) {
                    Text("Limpar", color = ErrorDark)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Result dialog
    if (cleanupResult != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissResult,
            title = { Text("Resultado") },
            text = {
                Text(cleanupResult.message)
            },
            confirmButton = {
                TextButton(onClick = onDismissResult) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Thumbnail settings card - extraction configuration
 */
@Composable
fun ThumbnailSettingsCard(
    thumbnailSettings: ThumbnailSettings,
    onThumbsPerSecondChange: (Int) -> Unit,
    onQualityChange: (Int) -> Unit,
    onFormatChange: (ThumbnailFormat) -> Unit,
    onDimensionChange: (DimensionPreset) -> Unit
) {
    var showFormatDialog by remember { mutableStateOf(false) }
    var showDimensionDialog by remember { mutableStateOf(false) }

    ChopCutCard(
        modifier = Modifier.fillMaxWidth(),
        showShadow = true
    ) {
        Column {
            Text(
                text = "Configurações de Thumbnails",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(ChopCutSpacing.md))

            // Thumbs Per Second Slider
            Text(
                text = "Thumbnails por Segundo: ${thumbnailSettings.thumbsPerSecond}",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = thumbnailSettings.thumbsPerSecond.toFloat(),
                onValueChange = { onThumbsPerSecondChange(it.toInt()) },
                valueRange = 1f..10f,
                steps = 9,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = thumbsPerSecondDescription(thumbnailSettings.thumbsPerSecond),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(ChopCutSpacing.sm))

            // Quality Slider
            Text(
                text = "Qualidade: ${thumbnailSettings.quality}%",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = thumbnailSettings.quality.toFloat(),
                onValueChange = { onQualityChange(it.toInt()) },
                valueRange = 50f..100f,
                steps = 50,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = qualityDescription(thumbnailSettings.quality),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(ChopCutSpacing.sm))

            // Format Selection
            SettingRow(
                label = "Formato",
                value = thumbnailSettings.format.displayName,
                onClick = { showFormatDialog = true }
            )

            // Dimension Selection
            SettingRow(
                label = "Dimensão",
                value = thumbnailSettings.dimensionPreset.displayName,
                onClick = { showDimensionDialog = true }
            )
        }
    }

    // Format Selection Dialog
    if (showFormatDialog) {
        ThumbnailFormatSelectionDialog(
            currentFormat = thumbnailSettings.format,
            onFormatSelected = {
                onFormatChange(it)
                showFormatDialog = false
            },
            onDismiss = { showFormatDialog = false }
        )
    }

    // Dimension Selection Dialog
    if (showDimensionDialog) {
        ThumbnailDimensionSelectionDialog(
            currentPreset = thumbnailSettings.dimensionPreset,
            onPresetSelected = {
                onDimensionChange(it)
                showDimensionDialog = false
            },
            onDismiss = { showDimensionDialog = false }
        )
    }
}

/**
 * Thumbnail format selection dialog
 */
@Composable
fun ThumbnailFormatSelectionDialog(
    currentFormat: ThumbnailFormat,
    onFormatSelected: (ThumbnailFormat) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Formato da Thumbnail") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThumbnailFormat.entries.forEach { format ->
                    val isSelected = format == currentFormat
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = { onFormatSelected(format) },
                        label = {
                            Column {
                                Text(
                                    text = format.displayName,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = format.description,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
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
 * Developer settings card - access to development/testing screen
 */
@Composable
fun DeveloperSettingsCard(
    onNavigateToDevelop: () -> Unit
) {
    ChopCutCard(
        modifier = Modifier.fillMaxWidth(),
        showShadow = false
    ) {
        Column(modifier = Modifier.padding(ChopCutSpacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(ChopCutSpacing.xs))
                Text(
                    text = "Desenvolvimento",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(ChopCutSpacing.xs))

            Text(
                text = "Acesse a área de testes para visualizar e testar componentes isoladamente.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(ChopCutSpacing.sm))

            ChopCutPrimaryButton(
                text = "Abrir Área de Testes",
                onClick = onNavigateToDevelop,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Thumbnail dimension preset selection dialog
 */
@Composable
fun ThumbnailDimensionSelectionDialog(
    currentPreset: DimensionPreset,
    onPresetSelected: (DimensionPreset) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dimensão da Thumbnail") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DimensionPreset.entries.forEach { preset ->
                    val isSelected = preset == currentPreset
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = { onPresetSelected(preset) },
                        label = {
                            Text(
                                text = preset.displayName,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
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
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.Button(
            onClick = onClick,
            modifier = Modifier.width(120.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = value,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
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
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = codecDescription(codec),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
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
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = preset.description,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
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
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
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
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
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
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
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

private fun thumbsPerSecondDescription(thumbsPerSecond: Int): String {
    val perMinute = thumbsPerSecond * 60
    return when {
        thumbsPerSecond <= 2 -> "Baixo (~$perMinute thumbs/min)"
        thumbsPerSecond <= 5 -> "Médio (~$perMinute thumbs/min)"
        else -> "Alto (~$perMinute thumbs/min)"
    }
}

private fun qualityDescription(quality: Int): String {
    return when {
        quality < 70 -> "Baixo (arquivos menores)"
        quality < 85 -> "Bom (balanceamento)"
        quality < 95 -> "Alta (boa qualidade)"
        else -> "Máxima (arquivos maiores)"
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
