package com.chopcut.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.chopcut.ui.preview.PreviewManager
import com.chopcut.ui.timelinev4.TimelineContainer
import com.chopcut.ui.timelinev4.TimelineEvent
import com.chopcut.ui.timelinev4.TimelineViewModel

@Composable
fun EditorTimelineIntegration(
    previewManager: PreviewManager,
    modifier: Modifier = Modifier
) {
    val timelineViewModel = remember { TimelineViewModel() }
    val timelineState by timelineViewModel.state.collectAsState()
    val playerPosition by previewManager.currentPosition.collectAsState()

    // 1. Sync Player -> Timeline
    LaunchedEffect(playerPosition) {
        timelineViewModel.updateExternalPosition(playerPosition)
    }

    // 2. Render Timeline
    TimelineContainer(
        state = timelineState,
        onEvent = { event ->
            timelineViewModel.onEvent(event)
            
            // 3. Sync Timeline -> Player
            when (event) {
                is TimelineEvent.Seek -> {
                    previewManager.seekTo(event.positionMs)
                }
                is TimelineEvent.ScrubStart -> {
                    previewManager.setScrubbing(true)
                }
                is TimelineEvent.ScrubEnd -> {
                    previewManager.setScrubbing(false)
                }
                else -> {}
            }
        },
        modifier = modifier
    )
}
