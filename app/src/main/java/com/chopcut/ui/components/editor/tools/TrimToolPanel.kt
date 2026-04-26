package com.chopcut.ui.components.editor.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chopcut.ui.components.trim.TrimButtons

@Composable
fun TrimToolPanel(
    isDraftMode: Boolean,
    isInsideRange: Boolean,
    onAddPosition: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Aproveitamos o botão TrimButtons já existente
        TrimButtons(
            isDraftMode = isDraftMode,
            isInsideRange = isInsideRange,
            onAddPosition = onAddPosition,
            onDelete = onDelete,
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        )
        
        IconButton(onClick = onClose, modifier = Modifier.padding(end = 16.dp)) {
            Icon(Icons.Default.Check, contentDescription = "OK", tint = Color(0xFF00E5FF))
        }
    }
}
