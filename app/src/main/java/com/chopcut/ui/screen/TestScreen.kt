package com.chopcut.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.data.model.VideoInfo
import com.chopcut.ui.components.WaveForm
import com.chopcut.ui.components.WaveformData

/**
 * Test screen - Contains all test operations from original HomeScreen
 *
 * @param viewModel HomeViewModel (reused for test operations)
 * @param onNavigateBack Callback to navigate back
 * @param onNavigateToEditor Callback to navigate to editor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToEditor: (android.net.Uri) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedUri by viewModel.selectedVideoUri.collectAsState()
    val waveformData by viewModel.waveformData.collectAsState()
    val waveformBars by viewModel.waveformBars.collectAsState()
    val waveformMirrored by viewModel.waveformMirrored.collectAsState()
    val audioRawData by viewModel.audioRawData.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()

    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectVideo(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ChopCut - Testes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Select Video Button
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Step 1: Selecionar Vídeo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Selecionar Vídeo da Galeria")
                        }
                        val uri = selectedUri
                        if (uri != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Selecionado: ${uri.lastPathSegment}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Open Editor Button
            val uri = selectedUri
            if (uri != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Abrir Editor de Vídeo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Experimente o novo editor com preview e timeline!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { onNavigateToEditor(uri) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Abrir no Editor")
                            }
                        }
                    }
                }
            }

            // Video Info
            if (uiState is HomeUiState.VideoLoaded) {
                item {
                    VideoInfoCard((uiState as HomeUiState.VideoLoaded).videoInfo)
                }
            }

            // Codec Info
            if (uiState is HomeUiState.CodecsLoaded) {
                item {
                    CodecInfoCard((uiState as HomeUiState.CodecsLoaded).codecs)
                }
            }

            // Test Operations - Phase 1 (CopyPipeline)
            if (selectedUri != null) {
                item {
                    TestOperationsCard(
                        onTestTrim = { viewModel.testTrim() },
                        enabled = uiState !is HomeUiState.Processing
                    )
                }
            }

            // Transcode Operations - Phase 2
            if (selectedUri != null) {
                item {
                    TranscodeOperationsCard(
                        onTestCompress = { viewModel.testCompress() },
                        onTestResize = { viewModel.testResize() },
                        onTestCrop = { viewModel.testCrop() },
                        enabled = uiState !is HomeUiState.Processing
                    )
                }
            }

            // Background Export Test - Phase 4.4
            if (selectedUri != null) {
                item {
                    BackgroundExportCard(
                        progress = exportProgress,
                        status = exportStatus,
                        onTestForegroundService = { viewModel.testExportForegroundService() },
                        onTestWorkManager = { viewModel.testExportWorkManager() },
                        onTestReencode = { viewModel.testExportReencode() },
                        onCancelExport = { viewModel.cancelExport() },
                        enabled = uiState !is HomeUiState.Processing
                    )
                }
            }

            // Audio Operations - Phase 4.1
            if (selectedUri != null) {
                item {
                    AudioOperationsCard(
                        onExtractPcmData = { viewModel.extractPcmData() },
                        onGenerateWaveform = { viewModel.generateWaveform() },
                        hasRawData = audioRawData != null,
                        enabled = uiState !is HomeUiState.Processing,
                        waveformBars = waveformBars,
                        onBarsChange = { viewModel.setWaveformBars(it) }
                    )
                }
            }

            // WaveForm - Raw Audio Visualization
            if (waveformData.amplitudes.isNotEmpty()) {
                item {
                    WaveFormCard(
                        waveformData,
                        mirrored = waveformMirrored,
                        onToggleMirrored = { viewModel.toggleWaveformMirrored() }
                    )
                }
            }

            // Status Messages
            when (uiState) {
                is HomeUiState.Loading -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Carregando...")
                            }
                        }
                    }
                }
                is HomeUiState.Processing -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text((uiState as HomeUiState.Processing).message)
                            }
                        }
                    }
                }
                is HomeUiState.Success -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "✓ Sucesso",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = (uiState as HomeUiState.Success).message,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.resetState() }) {
                                    Text("OK")
                                }
                            }
                        }
                    }
                }
                is HomeUiState.Error -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "✗ Erro",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = (uiState as HomeUiState.Error).message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.resetState() }) {
                                    Text("Dispensar")
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun VideoInfoCard(videoInfo: VideoInfo) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Informações do Vídeo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            InfoRow("Nome do Arquivo", videoInfo.fileName)
            InfoRow("Resolução", "${videoInfo.width} x ${videoInfo.height}")
            InfoRow("Duração", formatDuration(videoInfo.durationMs))
            InfoRow("Frame Rate", "${videoInfo.frameRate} fps")
            InfoRow("Bitrate", "${videoInfo.bitrate / 1000} kbps")
            InfoRow("Codec de Vídeo", videoInfo.videoCodec ?: "Desconhecido")
            InfoRow("Codec de Áudio", videoInfo.audioCodec ?: if (videoInfo.hasAudio) "Sim" else "Não")
            InfoRow("Tamanho", "${videoInfo.sizeBytes / (1024 * 1024)} MB")
            InfoRow("Rotação", "${videoInfo.rotation}°")
        }
    }
}

@Composable
fun CodecInfoCard(codecs: List<CodecInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Codecs Disponíveis no Dispositivo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            codecs.forEach { codec ->
                CodecItem(codec)
                if (codec != codecs.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CodecItem(codec: CodecInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = codec.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (codec.isBest) FontWeight.Bold else FontWeight.Normal
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CodecBadge("Encoder", codec.hasEncoder)
                CodecBadge("Decoder", codec.hasDecoder)
            }
        }
        if (codec.isBest) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "MELHOR",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun CodecBadge(label: String, supported: Boolean) {
    Surface(
        color = if (supported)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "$label: ${if (supported) "✓" else "✗"}",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (supported)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun TestOperationsCard(
    onTestTrim: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Teste de Operações - Fase 1",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Teste recursos da Fase 1 (CopyPipeline):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onTestTrim,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Testar Trim (0-5 segundos)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Nota: A saída será salva no cache do app",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TranscodeOperationsCard(
    onTestCompress: () -> Unit,
    onTestResize: () -> Unit,
    onTestCrop: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Teste de Transcodificação",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Teste recursos da Fase 2 (requer transcodificação):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Compress Button
            Button(
                onClick = onTestCompress,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Testar Compress (2 Mbps)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Resize Button
            Button(
                onClick = onTestResize,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Testar Resize (50% resolução)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Crop Button
            Button(
                onClick = onTestCrop,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Testar Crop (centro 50%)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "⚠️ Estas operações requerem transcodificação completa (mais lento)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun AudioOperationsCard(
    onExtractPcmData: () -> Unit,
    onGenerateWaveform: () -> Unit,
    hasRawData: Boolean,
    enabled: Boolean,
    waveformBars: Int,
    onBarsChange: (Int) -> Unit
) {
    var barsInput by remember { mutableStateOf(waveformBars.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Teste de Waveform de Áudio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasRawData)
                    "✓ Dados de áudio extraídos - gere o waveform!"
                else
                    "1. Extraia dados de áudio, 2. Gere waveform",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Waveform bars input
            OutlinedTextField(
                value = barsInput,
                onValueChange = {
                    barsInput = it
                    it.toIntOrNull()?.let { onBarsChange(it) }
                },
                label = { Text("Barras do Waveform (10-500)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Step 1: Extract Audio Data
            Button(
                onClick = onExtractPcmData,
                enabled = enabled && !hasRawData,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasRawData)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (hasRawData) "✓ Dados de Áudio Extraídos" else "1. Extrair Dados de Áudio")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Step 2: Generate Waveform
            Button(
                onClick = onGenerateWaveform,
                enabled = enabled && hasRawData,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("2. Gerar Waveform ($waveformBars barras)")
            }
        }
    }
}

@Composable
fun WaveFormCard(
    data: WaveformData,
    mirrored: Boolean = false,
    onToggleMirrored: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Waveform - Áudio Raw",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Calculate stats
            val maxAmp = data.amplitudes.maxOrNull() ?: 0f
            val avgAmp = if (data.amplitudes.isNotEmpty()) data.amplitudes.sum() / data.amplitudes.size else 0f

            Text(
                text = "Samples: ${data.amplitudes.size} • Duração: ${data.durationMs}ms • SR: ${data.sampleRate}Hz",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Max: ${"%.4f".format(maxAmp)} • Avg: ${"%.4f".format(avgAmp)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            WaveForm(
                amplitudes = data.amplitudes,
                maxAmp = maxAmp,
                avgAmp = avgAmp,
                mirrored = mirrored,
                modifier = Modifier.fillMaxWidth()
            )

            // Toggle mirrored button
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onToggleMirrored,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mirrored)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (mirrored) "Waveform: Espelhado (Centro)" else "Waveform: Normal (Base)")
            }

            // Show first 20 values
            if (data.amplitudes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Primeiros 20 valores:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = data.amplitudes.take(20).joinToString(", ") { "%.3f".format(it) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BackgroundExportCard(
    progress: Int,
    status: String?,
    onTestForegroundService: () -> Unit,
    onTestWorkManager: () -> Unit,
    onTestReencode: () -> Unit,
    onCancelExport: () -> Unit,
    enabled: Boolean
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (progress > 0)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        ),
        border = if (progress > 0)
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Teste de Export em Background",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (progress > 0)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (progress > 0) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Teste exportação em background com notificação:",
                style = MaterialTheme.typography.bodySmall,
                color = if (progress > 0)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer
            )

            // Progress Bar (visível durante export)
            if (progress > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = status ?: "Exportando...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botão de cancelar durante export
                Button(
                    onClick = { showCancelDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancelar Export")
                }

                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                // Botão único para testar FG Service
                Button(
                    onClick = onTestForegroundService,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testar Export com Notificação")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botão secundário para WorkManager
                OutlinedButton(
                    onClick = onTestWorkManager,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testar via WorkManager")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botão terciário para Re-encode (garante qualidade)
                Button(
                    onClick = onTestReencode,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testar Re-encode (Qualidade Garantida)")
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Info box
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (progress > 0)
                        MaterialTheme.colorScheme.surface
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "ℹ️ Diferenças:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (progress > 0)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• FG Service: notificação + continua app fechado\n" +
                              "• WorkManager: persiste após reboot do device",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (progress > 0)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Dialog de confirmação de cancelamento
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancelar exportação?") },
            text = { Text("A exportação será interrompida e o arquivo parcial será descartado.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancelExport()
                        showCancelDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sim, cancelar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Não")
                }
            }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
}
