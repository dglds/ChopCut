package com.chopcut.core.designsystem.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SizeTokens

/**
 * Container de ícone com fundo.
 * Use para destacar ícones em listas ou cards.
 *
 * @param icon Vetor do ícone
 * @param contentDescription Descrição de acessibilidade
 * @param modifier Modifier para customização
 * @param containerColor Cor do fundo
 * @param iconColor Cor do ícone
 * @param shape Forma do container
 * @param size Tamanho do container
 */
@Composable
fun IconBox(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    shape: Shape = CircleShape,
    size: androidx.compose.ui.unit.Dp = SizeTokens.touchTargetMin
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(SizeTokens.iconMd)
        )
    }
}

/**
 * Variação de IconBox com fundo de superfície.
 */
@Composable
fun SurfaceIconBox(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = RoundedCornerShape(SizeTokens.radiusMd),
    size: androidx.compose.ui.unit.Dp = SizeTokens.touchTargetMin
) {
    IconBox(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        iconColor = iconColor,
        shape = shape,
        size = size
    )
}

/**
 * Variação de IconBox com fundo secundário.
 */
@Composable
fun SecondaryIconBox(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    size: androidx.compose.ui.unit.Dp = SizeTokens.touchTargetMin
) {
    IconBox(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = shape,
        size = size
    )
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun IconBoxPreview() {
    ChopCutTheme {
        IconBox(
            icon = androidx.compose.material.icons.Icons.Default.PlayArrow,
            contentDescription = "Play"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SurfaceIconBoxPreview() {
    ChopCutTheme {
        SurfaceIconBox(
            icon = androidx.compose.material.icons.Icons.Default.Settings,
            contentDescription = "Settings"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SecondaryIconBoxPreview() {
    ChopCutTheme {
        SecondaryIconBox(
            icon = androidx.compose.material.icons.Icons.Default.Info,
            contentDescription = "Info"
        )
    }
}
