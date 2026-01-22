package com.chopcut.ui.timelinev5

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Controles simples para adicionar e remover ranges.
 */
@Composable
fun RangeControls(
    onAddRange: () -> Unit,
    onRemoveRange: () -> Unit,
    hasSelectedRange: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onAddRange,
            modifier = Modifier.width(100.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(" + Range")
        }

        Button(
            onClick = onRemoveRange,
            enabled = hasSelectedRange,
            modifier = Modifier.width(120.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Text(" Remover")
        }
    }
}
