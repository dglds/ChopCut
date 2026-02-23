package com.chopcut.ui.screen

import android.net.Uri
import com.chopcut.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.ui.components.feedback.DebugToast
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.theme.OnPrimary
import com.chopcut.ui.theme.Primary
import com.chopcut.ui.theme.Surface
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreloadScreen(
    videoUri: Uri,
    viewModel: PreloadViewModel = viewModel(),
    onReady: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preparando vídeo...") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is PreloadUiState.Idle -> {
                    viewModel.startPreload(videoUri, 180f)
                }
                is PreloadUiState.Loading -> {
                    LoadingContent(
                        progress = (uiState as PreloadUiState.Loading).progress,
                        onCancel = { viewModel.cancelPreload() }
                    )
                }
                is PreloadUiState.Ready -> {
                    Timber.d("Preload ready, navigating to editor")
                    PreloadDataStore.setData((uiState as PreloadUiState.Ready).data)
                    onReady()
                }
                is PreloadUiState.Error -> {
                    val error = uiState as PreloadUiState.Error
                    ErrorState(
                        title = if (error.isDurationExceeded) "Vídeo muito longo" else "Erro",
                        message = error.message,
                        actionLabel = "Cancelar",
                        onAction = onCancel
                    )
                }
                PreloadUiState.Cancelled -> {
                    Timber.d("Preload cancelled, navigating back")
                    onCancel()
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(
    progress: PreloadProgress,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = Primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = getStageMessage(progress.stage),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = Primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        ProgressIndicators(progress)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (BuildConfig.DEBUG) {
            DebugProgressLogs(progress.logs)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        CancelButton(onCancel = onCancel)
    }
}

private fun getStageMessage(stage: ExtractionStage): String {
    return when (stage) {
        ExtractionStage.Starting -> "Iniciando..."
        ExtractionStage.Validating -> "Validando vídeo..."
        ExtractionStage.ExtractingAudio -> "Extraindo áudio..."
        ExtractionStage.ExtractingThumbnails -> "Extraindo thumbnails..."
        ExtractionStage.Ready -> "Pronto!"
    }
}

@Composable
private fun ProgressIndicators(
    progress: PreloadProgress
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AudioProgress(progress.audioPercent)
        ThumbnailProgress(
            progress.thumbnailPercent,
            progress.currentSegment,
            progress.totalSegments
        )
        
        val overallProgress = (progress.audioPercent + progress.thumbnailPercent) / 2
        OverallProgressBar(overallProgress)
    }
}

@Composable
private fun OverallProgressBar(progress: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Progresso Geral",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.bodyMedium,
                color = Primary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress / 100f)
                    .height(8.dp)
                    .background(Primary, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun AudioProgress(percent: Int) {
    val status = if (percent == 100) "✓" else "$percent%"
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Áudio:",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (percent == 100) Primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ThumbnailProgress(
    percent: Int,
    current: Int,
    total: Int
) {
    val status = if (percent == 100) "✓" else "$percent%"
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Thumbnails:",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (percent == 100) Primary else MaterialTheme.colorScheme.onSurface
        )
        if (total > 0) {
            Text(
                text = "($current/$total)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DebugProgressLogs(logs: List<String>) {
    if (logs.isEmpty()) return
    
    Spacer(modifier = Modifier.height(8.dp))
    
    val logText = buildString {
        logs.forEach { log ->
            append(log)
            append("\n")
        }
    }
    
    DebugToast(text = logText)
}

@Composable
private fun CancelButton(onCancel: () -> Unit) {
    Button(
        onClick = onCancel,
        colors = ButtonDefaults.buttonColors(
            containerColor = Surface
        ),
        modifier = Modifier
            .height(40.dp)
            .width(120.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Cancel,
            contentDescription = "Cancelar",
            tint = OnPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Cancelar")
    }
}
