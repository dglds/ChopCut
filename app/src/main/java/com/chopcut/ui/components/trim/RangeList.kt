package com.chopcut.ui.components.trim

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chopcut.util.formatTime

@Composable
fun RangeList(
    ranges: List<Pair<Long, Long>>,
    currentPosition: Long,
    totalDurationMs: Long,
    finalDurationMs: Long,
    isDraftMode: Boolean,
    draftPosition: Long?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Duração Info
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total: ${formatTime(totalDurationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Final: ${formatTime(finalDurationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF64B5F6),
                fontWeight = FontWeight.Bold
            )
        }

        if (ranges.isEmpty() && !isDraftMode) {
            Text(
                text = "Nenhum range definido",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        } else {
            ranges.forEachIndexed { index, (start, end) ->
                Text(
                    text = "#${index + 1} ${formatTime(start)} → ${formatTime(end)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            if (isDraftMode && draftPosition != null) {
                Text(
                    text = "Draft: ${formatTime(draftPosition)} → ${formatTime(currentPosition)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
