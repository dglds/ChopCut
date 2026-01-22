package com.chopcut.ui.timelinev4

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TimelineContainer(
    state: TimelineState,
    onEvent: (TimelineEvent) -> Unit,
    thumbnails: Map<Int, Bitmap?> = emptyMap(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color.DarkGray)
    ) {
        // TODO: Add Scrollable Container for Filmstrip
        FilmstripView(
            thumbnails = thumbnails,
            startIndex = 0, // Placeholder
            visibleCount = 10, // Placeholder
            thumbnailWidth = 60.dp,
            modifier = Modifier.fillMaxSize()
        )

        // Playhead (Center Line)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.Red)
        )

        // Timecode (Top Center)
        TimecodeView(
            currentTimeMs = state.currentTimeMs,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun TimelineContainerPreview() {
    TimelineContainer(
        state = TimelineState(currentTimeMs = 1500),
        onEvent = {}
    )
}
