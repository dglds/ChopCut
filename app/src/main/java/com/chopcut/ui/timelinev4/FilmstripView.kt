package com.chopcut.ui.timelinev4

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FilmstripView(
    thumbnails: Map<Int, Bitmap?>, // Index to Bitmap mapping
    startIndex: Int,
    visibleCount: Int,
    thumbnailWidth: Dp,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        for (i in startIndex until (startIndex + visibleCount)) {
            val bitmap = thumbnails[i]
            
            Box(
                modifier = Modifier
                    .width(thumbnailWidth)
                    .fillMaxHeight()
                    .background(Color.Gray) // Placeholder color
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
    }
}
