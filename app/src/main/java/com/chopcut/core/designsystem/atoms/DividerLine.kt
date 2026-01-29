package com.chopcut.core.designsystem.atoms

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Divisor horizontal.
 * Use para separar seções ou itens de lista.
 *
 * @param modifier Modifier para customização
 * @param color Cor do divisor
 * @param thickness Espessura da linha
 */
@Composable
fun DividerLine(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    thickness: Dp = DividerDefaults.Thickness
) {
    HorizontalDivider(
        modifier = modifier,
        color = color,
        thickness = thickness
    )
}

/**
 * Divisor vertical.
 * Use para separar colunas ou elementos lado a lado.
 *
 * @param modifier Modifier para customização
 * @param color Cor do divisor
 * @param thickness Espessura da linha
 */
@Composable
fun VerticalDividerLine(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    thickness: Dp = DividerDefaults.Thickness
) {
    VerticalDivider(
        modifier = modifier,
        color = color,
        thickness = thickness
    )
}

/**
 * Divisor com espaçamento interno.
 * Use quando precisar de padding ao redor do divisor.
 *
 * @param modifier Modifier para customização
 * @param color Cor do divisor
 * @param thickness Espessura da linha
 * @param padding Padding vertical aplicado ao divisor
 */
@Composable
fun PaddedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    thickness: Dp = DividerDefaults.Thickness,
    padding: Dp = SpacingTokens.md
) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = padding),
        color = color,
        thickness = thickness
    )
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun DividerLinePreview() {
    ChopCutTheme {
        DividerLine(modifier = Modifier.padding(SpacingTokens.lg))
    }
}

@Preview(showBackground = true, widthDp = 100, heightDp = 100)
@Composable
private fun VerticalDividerLinePreview() {
    ChopCutTheme {
        VerticalDividerLine(modifier = Modifier.padding(SpacingTokens.lg))
    }
}

@Preview(showBackground = true)
@Composable
private fun PaddedDividerPreview() {
    ChopCutTheme {
        PaddedDivider(modifier = Modifier.padding(horizontal = SpacingTokens.lg))
    }
}
