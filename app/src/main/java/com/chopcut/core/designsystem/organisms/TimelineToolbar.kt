package com.chopcut.core.designsystem.organisms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.IconBox
import com.chopcut.core.designsystem.atoms.SecondaryIconBox
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Toolbar de ações para a timeline.
 *
 * @param onCut Callback para cortar
 * @param onUndo Callback para desfazer
 * @param onRedo Callback para refazer
 * @param onDelete Callback para deletar
 * @param onZoomIn Callback para zoom in
 * @param onZoomOut Callback para zoom out
 * @param onAdd Callback para adicionar
 * @param modifier Modifier para customização
 * @param canUndo Se pode desfazer
 * @param canRedo Se pode refazer
 * @param hasSelection Se tem seleção ativa
 */
@Composable
fun TimelineToolbar(
    onCut: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    hasSelection: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(SpacingTokens.md),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecondaryIconBox(
            icon = Icons.Default.Undo,
            contentDescription = "Desfazer",
            modifier = Modifier.clickable(enabled = canUndo, onClick = onUndo)
        )
        SecondaryIconBox(
            icon = Icons.Default.Redo,
            contentDescription = "Refazer",
            modifier = Modifier.clickable(enabled = canRedo, onClick = onRedo)
        )
        IconBox(
            icon = Icons.Default.Add,
            contentDescription = "Adicionar",
            modifier = Modifier.clickable(onClick = onAdd)
        )
        SecondaryIconBox(
            icon = Icons.Default.ContentCut,
            contentDescription = "Cortar",
            modifier = Modifier.clickable(enabled = hasSelection, onClick = onCut)
        )
        SecondaryIconBox(
            icon = Icons.Default.Delete,
            contentDescription = "Deletar",
            modifier = Modifier.clickable(enabled = hasSelection, onClick = onDelete)
        )
        SecondaryIconBox(
            icon = Icons.Default.ZoomOut,
            contentDescription = "Zoom out",
            modifier = Modifier.clickable(onClick = onZoomOut)
        )
        SecondaryIconBox(
            icon = Icons.Default.ZoomIn,
            contentDescription = "Zoom in",
            modifier = Modifier.clickable(onClick = onZoomIn)
        )
    }
}

// Helper para clickable no IconBox
private fun Modifier.clickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    return if (enabled) {
        this.then(androidx.compose.foundation.clickable(onClick = onClick))
    } else {
        this.then(androidx.compose.ui.draw.alpha(0.3f))
    }
}

// ============================================================================
// PREVIEW
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun TimelineToolbarPreview() {
    ChopCutTheme {
        TimelineToolbar(
            onCut = {},
            onUndo = {},
            onRedo = {},
            onDelete = {},
            onZoomIn = {},
            onZoomOut = {},
            onAdd = {},
            canUndo = true,
            hasSelection = true
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TimelineToolbarDisabledPreview() {
    ChopCutTheme {
        TimelineToolbar(
            onCut = {},
            onUndo = {},
            onRedo = {},
            onDelete = {},
            onZoomIn = {},
            onZoomOut = {},
            onAdd = {}
        )
    }
}
