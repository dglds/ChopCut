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

enum class TrimActionState {
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

    TrimActionIconButton(
        actionState = actionState,
        onClick = when (actionState) {
            TrimActionState.CUT -> onAddPosition
            TrimActionState.CONFIRM -> onAddPosition
            TrimActionState.DELETE -> onDelete
        },
        modifier = modifier
    )
}

@Composable
private fun TrimActionIconButton(
    actionState: TrimActionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config = getActionConfig(actionState)

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .background(config.backgroundColor, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = Color.White
        )
    ) {
        Crossfade(
            targetState = actionState,
            animationSpec = tween(durationMillis = 200),
            label = "trim-action-icon"
        ) { state ->
            Icon(
                imageVector = config.icon,
                contentDescription = config.contentDescription
            )
        }
    }
}

private data class TrimActionConfig(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String,
    val backgroundColor: Color
)

@Composable
private fun getActionConfig(actionState: TrimActionState): TrimActionConfig {
    return when (actionState) {
        TrimActionState.CUT -> TrimActionConfig(
            icon = Icons.Default.ContentCut,
            contentDescription = "Iniciar corte",
            backgroundColor = MaterialTheme.colorScheme.primary
        )
        TrimActionState.CONFIRM -> TrimActionConfig(
            icon = Icons.Default.Check,
            contentDescription = "Confirmar corte",
            backgroundColor = Color(0xFFFF9800)
        )
        TrimActionState.DELETE -> TrimActionConfig(
            icon = Icons.Default.Delete,
            contentDescription = "Excluir range",
            backgroundColor = MaterialTheme.colorScheme.error
        )
    }
}
