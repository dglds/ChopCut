package com.chopcut.core.designsystem.molecules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.BodyText
import com.chopcut.core.designsystem.atoms.IconBox
import com.chopcut.core.designsystem.atoms.SmallText
import com.chopcut.core.designsystem.atoms.SurfaceIconBox
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SizeTokens
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Item de ação clicável com ícone, título, descrição opcional e indicador de navegação.
 * Use em menus de configurações ou listas de ações.
 *
 * @param icon Ícone representativo
 * @param title Título da ação
 * @param modifier Modifier para customização
 * @param description Descrição opcional
 * @param onClick Ação ao clicar
 * @param showArrow Se deve mostrar seta de navegação
 * @param enabled Se o item está habilitado
 */
@Composable
fun ActionItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    showArrow: Boolean = true,
    enabled: Boolean = true
) {
    val clickModifier = if (onClick != null && enabled) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = SpacingTokens.md, horizontal = SpacingTokens.lg),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SurfaceIconBox(
            icon = icon,
            contentDescription = null,
            size = SizeTokens.touchTargetMin
        )

        Spacer(Modifier.width(SpacingTokens.sm))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
        ) {
            BodyText(
                text = title,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (description != null) {
                SmallText(
                    text = description,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }

        if (showArrow && enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SizeTokens.iconMd)
            )
        }
    }
}

/**
 * Variação compacta do ActionItem sem descrição.
 */
@Composable
fun CompactActionItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    ActionItem(
        icon = icon,
        title = title,
        modifier = modifier,
        description = null,
        onClick = onClick,
        showArrow = true,
        enabled = enabled
    )
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun ActionItemPreview() {
    ChopCutTheme {
        ActionItem(
            icon = Icons.Default.Settings,
            title = "Configurações",
            description = "Personalize o app",
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompactActionItemPreview() {
    ChopCutTheme {
        CompactActionItem(
            icon = Icons.Default.Settings,
            title = "Configurações",
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionItemDisabledPreview() {
    ChopCutTheme {
        ActionItem(
            icon = Icons.Default.Settings,
            title = "Configurações",
            description = "Opção indisponível",
            enabled = false
        )
    }
}

// Fix missing import for preview
private val Default = Icons.Default
