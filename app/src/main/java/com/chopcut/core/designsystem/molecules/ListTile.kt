package com.chopcut.core.designsystem.molecules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.chopcut.core.designsystem.atoms.BodyText
import com.chopcut.core.designsystem.atoms.SmallText
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Item de lista genérico com título, subtítulo e conteúdo trailing opcional.
 * Use para listas de dados simples.
 *
 * @param title Título principal
 * @param modifier Modifier para customização
 * @param subtitle Subtítulo opcional
 * @param leading Conteúdo à esquerda (ícone, avatar, etc)
 * @param trailing Conteúdo à direita (botão, checkbox, etc)
 * @param onClick Ação ao clicar
 */
@Composable
fun ListTile(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) {
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
        if (leading != null) {
            leading()
            Spacer(Modifier.width(SpacingTokens.sm))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
        ) {
            BodyText(
                text = title,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                SmallText(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(SpacingTokens.sm))
            trailing()
        }
    }
}

/**
 * Item de lista com ícone emoji.
 * Use quando precisar de um ícone simples sem importar vetores.
 *
 * @param icon Emoji ou caractere representativo
 * @param title Título principal
 * @param modifier Modifier para customização
 * @param subtitle Subtítulo opcional
 * @param trailing Conteúdo à direita
 * @param onClick Ação ao clicar
 */
@Composable
fun EmojiListTile(
    icon: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    ListTile(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        leading = {
            androidx.compose.material3.Text(
                text = icon,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.size(SpacingTokens.xxl)
            )
        },
        trailing = trailing,
        onClick = onClick
    )
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun ListTilePreview() {
    ChopCutTheme {
        ListTile(
            title = "Título do Item",
            subtitle = "Subtítulo descritivo",
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ListTileWithLeadingPreview() {
    ChopCutTheme {
        ListTile(
            title = "Projeto 1",
            subtitle = "2 minutos",
            leading = {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(SpacingTokens.xxl)
                        .androidx.compose.foundation.background(
                            MaterialTheme.colorScheme.primaryContainer,
                            androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    SmallText(
                        text = "V",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmojiListTilePreview() {
    ChopCutTheme {
        EmojiListTile(
            icon = "🎬",
            title = "Meu Vídeo",
            subtitle = "3:45 • 1080p",
            onClick = {}
        )
    }
}
