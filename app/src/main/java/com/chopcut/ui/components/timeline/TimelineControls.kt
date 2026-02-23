package com.chopcut.ui.components.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chopcut.ui.theme.ChopCutMonoFont
import com.chopcut.utils.TimeUtils

@Composable
fun SeekbarProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color(0xFF424242))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Color(0xFF64B5F6))
        )
    }
}

@Composable
fun CurrentTimeDisplay(
    currentTimeMs: Long,
    isInsideRange: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = TimeUtils.formatTimeWithMillis(currentTimeMs),
        color = if (isInsideRange) Color.Red else Color.White,
        fontSize = 24.sp,
        fontFamily = ChopCutMonoFont,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        modifier = modifier.padding(vertical = 12.dp)
    )
}

@Composable
fun VideoFileInfo(
    fileInfo: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = fileInfo,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start
    )
}
