package com.chopcut.core.designsystem.atoms

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SizeTokens

/**
 * Indicador de carregamento circular.
 * Use para mostrar operações em andamento.
 *
 * @param modifier Modifier para customização
 * @param color Cor do spinner
 * @param strokeWidth Espessura do traço
 * @param size Tamanho do spinner
 */
@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    size: Dp = SizeTokens.iconXl
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth
    )
}

/**
 * Indicador de carregamento pequeno.
 * Use inline com textos ou em botões.
 *
 * @param modifier Modifier para customização
 * @param color Cor do spinner
 */
@Composable
fun SmallLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    CircularProgressIndicator(
        modifier = modifier.size(SizeTokens.iconMd),
        color = color,
        strokeWidth = androidx.compose.ui.unit.dp
    )
}

/**
 * Indicador de carregamento grande.
 * Use para estados de loading em tela cheia.
 *
 * @param modifier Modifier para customização
 * @param color Cor do spinner
 */
@Composable
fun LargeLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    CircularProgressIndicator(
        modifier = modifier.size(SizeTokens.iconHero),
        color = color,
        strokeWidth = androidx.compose.ui.unit.dp * 4
    )
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun LoadingSpinnerPreview() {
    ChopCutTheme {
        LoadingSpinner()
    }
}

@Preview(showBackground = true)
@Composable
private fun SmallLoadingSpinnerPreview() {
    ChopCutTheme {
        SmallLoadingSpinner()
    }
}

@Preview(showBackground = true)
@Composable
private fun LargeLoadingSpinnerPreview() {
    ChopCutTheme {
        LargeLoadingSpinner()
    }
}
