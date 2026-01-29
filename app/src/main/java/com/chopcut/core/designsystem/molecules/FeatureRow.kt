package com.chopcut.core.designsystem.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.BodyText
import com.chopcut.core.designsystem.atoms.SmallText
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SizeTokens
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Molécula para exibir um recurso/feature com ícone emoji, título e descrição.
 * Use em listas de recursos ou funcionalidades.
 *
 * @param icon Emoji ou caractere representando o recurso
 * @param title Título do recurso
 * @param description Descrição curta do recurso
 * @param modifier Modifier para customização
 */
@Composable
fun FeatureRow(
    icon: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpacingTokens.sm),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        androidx.compose.material3.Text(
            text = icon,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.size(SizeTokens.iconLg)
        )

        // Content
        Column(modifier = Modifier.weight(1f)) {
            BodyText(
                text = title,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            SmallText(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun FeatureRowPreview() {
    ChopCutTheme {
        FeatureRow(
            icon = "✂️",
            title = "Trim",
            description = "Cortar vídeos em partes",
            modifier = Modifier.padding(SpacingTokens.lg)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FeatureRowListPreview() {
    ChopCutTheme {
        Column(modifier = Modifier.padding(SpacingTokens.lg)) {
            FeatureRow(
                icon = "✂️",
                title = "Trim",
                description = "Cortar vídeos em partes"
            )
            FeatureRow(
                icon = "🔗",
                title = "Join",
                description = "Concatenar vídeos"
            )
            FeatureRow(
                icon = "🗜️",
                title = "Compress",
                description = "Reduzir tamanho/bitrate"
            )
            FeatureRow(
                icon = "📐",
                title = "Resize",
                description = "Alterar resolução"
            )
        }
    }
}
