package com.chopcut.ui.screen

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.chopcut.ui.preview.PreviewManager
import com.chopcut.ui.timelinev5.ThumbnailProvider
import com.chopcut.ui.timelinev5.TimelineV5
import com.chopcut.ui.timelinev5.TimelineV5ViewModel
import com.chopcut.ui.timelinev5.model.Thumbnail

@Composable
fun EditorTimelineIntegration(
    previewManager: PreviewManager,
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val duration by previewManager.duration.collectAsState()
    val playerPosition by previewManager.currentPosition.collectAsState()
    
    // 1. ViewModel para gerenciar o estado da TimelineV5
    val timelineViewModel = remember { 
        TimelineV5ViewModel(initialDurationMs = duration) 
    }
    
    // Atualizar duração quando o player estiver pronto
    LaunchedEffect(duration) {
        if (duration > 0) {
            timelineViewModel.updateTotalDuration(duration)
        }
    }
    
    // 2. Provedor de thumbnails
    val thumbnailProvider = remember { ThumbnailProvider(context) }
    var thumbnails by remember { mutableStateOf<List<Thumbnail>>(emptyList()) }
    
    // 3. Buscar thumbnails quando o URI ou duração mudar
    LaunchedEffect(videoUri, duration) {
        if (duration > 0) {
            thumbnailProvider.extractThumbnails(
                uri = videoUri,
                durationMs = duration,
                count = 10, // Quantidade de thumbnails na faixa
                width = 150,
                height = 100
            ).collect { fetchedThumbnails ->
                thumbnails = fetchedThumbnails
            }
        }
    }

    // 4. Sincronizar Player -> Timeline (Playhead)
    LaunchedEffect(playerPosition) {
        timelineViewModel.updatePlayheadPosition(playerPosition)
    }

    // 5. Renderizar TimelineV5
    TimelineV5(
        viewModel = timelineViewModel,
        thumbnails = thumbnails,
        modifier = modifier,
        onScrubStart = {
            previewManager.setScrubbing(true)
        },
        onScrubEnd = {
            previewManager.setScrubbing(false)
        }
    )
    
    // 6. Sincronizar Timeline -> Player (Seek)
    val timelineState by timelineViewModel.state.collectAsState()
    LaunchedEffect(timelineState.playheadPositionMs) {
        // Só faz seek se a diferença for significativa ou se for scrubbing
        // (Isso evita loops infinitos se não for cuidadoso, mas o PreviewManager já lida com isScrubbing)
        if (Math.abs(timelineState.playheadPositionMs - playerPosition) > 50) {
            previewManager.seekTo(timelineState.playheadPositionMs)
        }
    }
}
