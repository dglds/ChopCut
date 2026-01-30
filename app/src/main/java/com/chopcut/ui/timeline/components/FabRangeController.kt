package com.chopcut.ui.timeline.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chopcut.ui.timeline.model.EstadoFab

/**
 * Botão flutuante de controle de ranges na timeline.
 *
 * Responsabilidades:
 * - Mostrar estado visual apropriado (Adicionar, Confirmar, Deletar)
 * - Animar transições entre estados
 * - Chamar callback onClick quando pressionado
 *
 * Estados visuais:
 * - ADICIONAR: Ícone ➕ cor primária (iniciar criação de range)
 * - CONFIRMAR: Ícone ✓ ou número ② cor secundária (finalizar range)
 * - DELETAR: Ícone 🗑️ cor de erro (deletar range existente)
 *
 * @param estado Estado atual do FAB
 * @param onClick Callback quando o FAB é clicado
 * @param modifier Modifier para customização
 */
@Composable
fun FabRangeController(
    estado: EstadoFab,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when (estado) {
        is EstadoFab.ADICIONAR -> MaterialTheme.colorScheme.primary
        is EstadoFab.CONFIRMAR -> MaterialTheme.colorScheme.secondary
        is EstadoFab.DELETAR -> MaterialTheme.colorScheme.error
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 12.dp
        )
    ) {
        AnimatedContent(
            targetState = estado,
            transitionSpec = {
                // Animação de scale + fade para transições suaves
                (scaleIn(animationSpec = tween(200)) + fadeIn())
                    .togetherWith(scaleOut(animationSpec = tween(200)) + fadeOut())
            },
            label = "fab_content"
        ) { targetEstado ->
            when (targetEstado) {
                is EstadoFab.ADICIONAR -> {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar range",
                        modifier = Modifier.size(24.dp)
                    )
                }
                is EstadoFab.CONFIRMAR -> {
                    // Pode usar ícone de check ou número "②"
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirmar range",
                        modifier = Modifier.size(24.dp)
                    )
                }
                is EstadoFab.DELETAR -> {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Deletar range",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Versão alternativa do FAB com texto numérico para o estado CONFIRMAR.
 * Mostra "①" após primeiro clique, "②" após segundo.
 */
@Composable
fun FabRangeControllerWithNumbers(
    estado: EstadoFab,
    numeroConfirmacao: Int = 2,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when (estado) {
        is EstadoFab.ADICIONAR -> MaterialTheme.colorScheme.primary
        is EstadoFab.CONFIRMAR -> MaterialTheme.colorScheme.tertiary
        is EstadoFab.DELETAR -> MaterialTheme.colorScheme.error
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        AnimatedContent(
            targetState = estado,
            transitionSpec = {
                (scaleIn(animationSpec = tween(200)) + fadeIn())
                    .togetherWith(scaleOut(animationSpec = tween(200)) + fadeOut())
            },
            label = "fab_content"
        ) { targetEstado ->
            when (targetEstado) {
                is EstadoFab.ADICIONAR -> {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar range",
                        modifier = Modifier.size(24.dp)
                    )
                }
                is EstadoFab.CONFIRMAR -> {
                    Text(
                        text = "${'①' + (numeroConfirmacao - 1)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                is EstadoFab.DELETAR -> {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Deletar range",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Versão estendida do FAB que mostra tooltip/label.
 * Útil para onboarding ou quando se quer mais clareza.
 */
@Composable
fun FabRangeControllerWithLabel(
    estado: EstadoFab,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val (icon, defaultLabel, color) = when (estado) {
        is EstadoFab.ADICIONAR -> Triple(
            Icons.Default.Add,
            "Novo corte",
            MaterialTheme.colorScheme.primary
        )
        is EstadoFab.CONFIRMAR -> Triple(
            Icons.Default.Check,
            "Confirmar",
            MaterialTheme.colorScheme.secondary
        )
        is EstadoFab.DELETAR -> Triple(
            Icons.Default.Delete,
            "Remover",
            MaterialTheme.colorScheme.error
        )
    }

    // Aqui você poderia usar um ExtendedFloatingActionButton
    // ou envolver em um Column com Text para o label
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = color,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label ?: defaultLabel,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Preview/Estados do FAB para debug/visualização.
 */
@Composable
fun FabRangeControllerPreview() {
    // Esta função seria usada apenas em previews do Android Studio
    // Mostra todos os estados lado a lado
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
    ) {
        FabRangeController(
            estado = EstadoFab.ADICIONAR,
            onClick = {}
        )
        FabRangeController(
            estado = EstadoFab.CONFIRMAR,
            onClick = {}
        )
        FabRangeController(
            estado = EstadoFab.DELETAR,
            onClick = {}
        )
    }
}
