package com.chopcut.ui.components.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chopcut.ui.state.CompressionLevel

@Composable
fun CompressToolPanel(
    currentLevel: CompressionLevel,
    onLevelSelected: (CompressionLevel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompressionLevel.values().forEach { level ->
                    CompressionButton(
                        level = level,
                        isSelected = currentLevel == level,
                        onClick = { onLevelSelected(level) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentLevel.description,
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        
        IconButton(onClick = onClose, modifier = Modifier.padding(start = 16.dp)) {
            Icon(Icons.Default.Check, contentDescription = "OK", tint = Color(0xFF00E5FF))
        }
    }
}

@Composable
private fun CompressionButton(
    level: CompressionLevel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = level.label,
            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
