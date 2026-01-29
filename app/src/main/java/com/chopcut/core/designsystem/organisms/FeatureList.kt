package com.chopcut.core.designsystem.organisms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.SecondaryCard
import com.chopcut.core.designsystem.molecules.FeatureRow
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Modelo de dado para features.
 */
data class Feature(
    val icon: String,
    val title: String,
    val description: String
)

/**
 * Lista de features em um card.
 * Use para mostrar os recursos do app.
 *
 * @param features Lista de features a exibir
 * @param modifier Modifier para customização
 * @param title Título opcional do card
 */
@Composable
fun FeatureList(
    features: List<Feature>,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    SecondaryCard(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(SpacingTokens.lg)
        ) {
            if (title != null) {
                com.chopcut.core.designsystem.atoms.SubtitleText(
                    text = title,
                    modifier = Modifier.padding(bottom = SpacingTokens.md)
                )
            }

            features.forEach { feature ->
                FeatureRow(
                    icon = feature.icon,
                    title = feature.title,
                    description = feature.description
                )
            }
        }
    }
}

/**
 * Lista de features com LazyColumn para listas grandes.
 *
 * @param features Lista de features a exibir
 * @param modifier Modifier para customização
 * @param contentPadding Padding interno da lista
 */
@Composable
fun LazyFeatureList(
    features: List<Feature>,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(
        SpacingTokens.lg
    )
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
        items(features) { feature ->
            FeatureRow(
                icon = feature.icon,
                title = feature.title,
                description = feature.description
            )
        }
    }
}

/**
 * Features padrão do ChopCut.
 */
object DefaultFeatures {
    val all = listOf(
        Feature("✂️", "Trim", "Cortar vídeos em partes"),
        Feature("🔗", "Join", "Concatenar vídeos"),
        Feature("🗜️", "Compress", "Reduzir tamanho/bitrate"),
        Feature("📐", "Resize", "Alterar resolução"),
        Feature("⬛", "Crop", "Recortar área do vídeo"),
        Feature("🎵", "Áudio", "Extrair trilha de áudio"),
        Feature("⚡", "Speed", "Alterar velocidade"),
        Feature("🎨", "Filters", "Aplicar filtros")
    )
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun FeatureListPreview() {
    ChopCutTheme {
        FeatureList(
            features = DefaultFeatures.all.take(4),
            title = "Recursos",
            modifier = Modifier.padding(SpacingTokens.lg)
        )
    }
}

@Preview(showBackground = true, heightDp = 300)
@Composable
private fun LazyFeatureListPreview() {
    ChopCutTheme {
        LazyFeatureList(
            features = DefaultFeatures.all,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
