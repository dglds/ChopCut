package com.chopcut.ui.timelinev5

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chopcut.ui.timelinev5.model.Thumbnail

/**
 * Renderiza uma faixa contínua de thumbnails do vídeo.
 */
@Composable
fun ThumbnailStrip(
    thumbnails: List<Thumbnail>,
    modifier: Modifier = Modifier,
    thumbnailWidth: Dp = 60.dp
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        thumbnails.forEach { thumb ->
            Box(
                modifier = Modifier
                    .width(thumbnailWidth)
                    .fillMaxHeight()
                    .background(Color.DarkGray)
            ) {
                thumb.bitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}