package com.chopcut.ui.components.editor.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chopcut.ui.state.EditorTool

@Composable
fun MainToolBar(
    onToolSelected: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolButton(
            icon = Icons.Default.ContentCut,
            label = "Cortar",
            onClick = { onToolSelected(EditorTool.TRIM) }
        )
        ToolButton(
            icon = Icons.AutoMirrored.Filled.CallMerge,
            label = "Juntar",
            onClick = { onToolSelected(EditorTool.ADD_MEDIA) }
        )
        ToolButton(
            icon = Icons.Default.AspectRatio,
            label = "Tamanho",
            onClick = { onToolSelected(EditorTool.FORMAT) }
        )
        ToolButton(
            icon = Icons.Default.Crop,
            label = "Recortar",
            onClick = { onToolSelected(EditorTool.CROP) }
        )
        ToolButton(
            icon = Icons.Default.Compress,
            label = "Comprimir",
            onClick = { onToolSelected(EditorTool.COMPRESS) }
        )
        ToolButton(
            icon = Icons.Default.MusicNote,
            label = "Áudio",
            onClick = { onToolSelected(EditorTool.AUDIO) }
        )
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(
            text = label, 
            color = Color.White, 
            style = MaterialTheme.typography.bodySmall
        )
    }
}
