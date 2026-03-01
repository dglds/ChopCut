package com.chopcut.ui.components.trim

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
    
    // Animação de cor de fundo suave entre os estados
    val animatedBgColor by animateColorAsState(
        targetValue = config.backgroundColor,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "fab-color-transition"
    )

    // Animação de pulsação apenas no estado CONFIRM (durante o trim)
    val infiniteTransition = rememberInfiniteTransition(label = "fab-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (actionState == TrimActionState.CONFIRM) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab-scale"
    )
    
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (actionState == TrimActionState.CONFIRM) 0.8f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab-border-alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Efeito de brilho externo pulsante (apenas para CONFIRM)
        if (actionState == TrimActionState.CONFIRM) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulseScale)
                    .background(config.backgroundColor.copy(alpha = 0.4f), CircleShape)
            )
        }

        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(64.dp)
                .background(animatedBgColor, CircleShape)
                .then(
                    if (actionState == TrimActionState.CONFIRM) {
                        Modifier.border(2.dp, Color.White.copy(alpha = borderAlpha), CircleShape)
                    } else Modifier
                ),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Color.White
            )
        ) {
            Crossfade(
                targetState = actionState,
                animationSpec = tween(durationMillis = 300),
                label = "trim-action-icon"
            ) { state ->
                val iconConfig = getActionConfig(state)
                Icon(
                    imageVector = iconConfig.icon,
                    contentDescription = iconConfig.contentDescription,
                    modifier = Modifier.size(32.dp)
                )
            }
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
            backgroundColor = Color(0xFFFF9800) // Amarelo/Laranja vibrante
        )
        TrimActionState.DELETE -> TrimActionConfig(
            icon = Icons.Default.Delete,
            contentDescription = "Excluir range",
            backgroundColor = MaterialTheme.colorScheme.error
        )
    }
}
