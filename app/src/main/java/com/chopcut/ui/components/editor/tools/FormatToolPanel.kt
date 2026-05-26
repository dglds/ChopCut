package com.chopcut.ui.components.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme

@Composable
fun FormatToolPanel(
    currentRatio: Float?,
    onRatioSelected: (Float?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RectangleShape)
            .border(2.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RatioButton(label = "Original", isSelected = currentRatio == null, onClick = { onRatioSelected(null) })
            RatioButton(label = "16:9", isSelected = currentRatio == 16f/9f, onClick = { onRatioSelected(16f/9f) })
            RatioButton(label = "9:16", isSelected = currentRatio == 9f/16f, onClick = { onRatioSelected(9f/16f) })
            RatioButton(label = "1:1", isSelected = currentRatio == 1f, onClick = { onRatioSelected(1f) })
        }
        
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Check, contentDescription = "OK", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RatioButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RectangleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
        )
    }
}
