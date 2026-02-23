package com.chopcut.ui.components.trim

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private enum class TrimActionState {
    CUT,
    CONFIRM,
    DELETE
}

@Composable
fun TrimActionButton(
    isDraftMode: Boolean,
    isInsideRange: Boolean,
    onAddPosition: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionState = when {
        isDraftMode -> TrimActionState.CONFIRM
        isInsideRange -> TrimActionState.DELETE
        else -> TrimActionState.CUT
    }

    val backgroundColor = when (actionState) {
        TrimActionState.CUT -> MaterialTheme.colorScheme.primary
        TrimActionState.CONFIRM -> Color(0xFFFF9800)
        TrimActionState.DELETE -> MaterialTheme.colorScheme.error
    }

    val onClick = when (actionState) {
        TrimActionState.CUT -> onAddPosition
        TrimActionState.CONFIRM -> onAddPosition
        TrimActionState.DELETE -> onDelete
    }

    val contentDescription = when (actionState) {
        TrimActionState.CUT -> "Iniciar corte"
        TrimActionState.CONFIRM -> "Confirmar corte"
        TrimActionState.DELETE -> "Excluir range"
    }

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .background(backgroundColor, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
    ) {
        Crossfade(
            targetState = actionState,
            animationSpec = tween(durationMillis = 200),
            label = "trim-action-icon"
        ) { state ->
            Icon(
                imageVector = when (state) {
                    TrimActionState.CUT -> Icons.Default.ContentCut
                    TrimActionState.CONFIRM -> Icons.Default.Check
                    TrimActionState.DELETE -> Icons.Default.Delete
                },
                contentDescription = contentDescription
            )
        }
    }
}
