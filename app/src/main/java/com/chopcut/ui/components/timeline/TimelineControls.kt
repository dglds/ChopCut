package com.chopcut.ui.components.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chopcut.ui.theme.ChopCutMonoFont
import com.chopcut.utils.TimeUtils

@Composable
fun SeekbarProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    // SEEKBAR NEON LUXO
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.6f),
                            Color(0xFF00E5FF)
                        )
                    )
                )
                .then(
                    Modifier.border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                )
        )
    }
}

@Composable
fun CurrentTimeDisplay(
    currentTimeMs: Long,
    isInsideRange: Boolean,
    modifier: Modifier = Modifier
) {
    val neonColor = if (isInsideRange) Color(0xFFFF5252) else Color(0xFF00E5FF)
    
    Box(
        modifier = modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = TimeUtils.formatTimeWithMillis(currentTimeMs),
            color = neonColor,
            fontSize = 22.sp,
            fontFamily = ChopCutMonoFont,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = Shadow(
                    color = neonColor.copy(alpha = 0.5f),
                    offset = Offset(0f, 0f),
                    blurRadius = 8f
                )
            )
        )
    }
}

@Composable
fun VideoFileInfo(
    fileInfo: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                width = 0.5.dp, 
                color = Color.White.copy(alpha = 0.08f), 
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.4f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = fileInfo,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
