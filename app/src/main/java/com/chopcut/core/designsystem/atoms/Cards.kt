package com.chopcut.core.designsystem.atoms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Card de superfície padrão.
 * Use para agrupar conteúdo relacionado com a cor de superfície do tema.
 *
 * @param modifier Modifier para customização
 * @param shape Forma do card
 * @param containerColor Cor de fundo do card
 * @param content Conteúdo do card
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        content = { Column(content = content) }
    )
}

/**
 * Card com cor do container primário.
 * Use para destacar informações importantes.
 *
 * @param modifier Modifier para customização
 * @param shape Forma do card
 * @param content Conteúdo do card
 */
@Composable
fun PrimaryCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        content = { Column(content = content) }
    )
}

/**
 * Card com cor do container secundário.
 * Use para agrupar conteúdo secundário.
 *
 * @param modifier Modifier para customização
 * @param shape Forma do card
 * @param content Conteúdo do card
 */
@Composable
fun SecondaryCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        content = { Column(content = content) }
    )
}

/**
 * Card elevado.
 * Use para criar hierarquia visual com sombra.
 *
 * @param modifier Modifier para customização
 * @param shape Forma do card
 * @param content Conteúdo do card
 */
@Composable
fun ElevatedCardAtom(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier,
        shape = shape,
        content = { Column(content = content) }
    )
}

/**
 * Card com borda (outlined).
 * Use para agrupar conteúdo com menor ênfase.
 *
 * @param modifier Modifier para customização
 * @param shape Forma do card
 * @param content Conteúdo do card
 */
@Composable
fun OutlinedCardAtom(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = modifier,
        shape = shape,
        content = { Column(content = content) }
    )
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun CardsPreview() {
    ChopCutTheme {
        Column(modifier = Modifier.padding(SpacingTokens.lg)) {
            SurfaceCard {
                BodyText(
                    text = "Surface Card",
                    modifier = Modifier.padding(SpacingTokens.lg)
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(SpacingTokens.md))
            PrimaryCard {
                BodyText(
                    text = "Primary Card",
                    modifier = Modifier.padding(SpacingTokens.lg)
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(SpacingTokens.md))
            SecondaryCard {
                BodyText(
                    text = "Secondary Card",
                    modifier = Modifier.padding(SpacingTokens.lg)
                )
            }
        }
    }
}
