package com.chopcut.core.designsystem.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.MediumText
import com.chopcut.core.designsystem.atoms.SmallText
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Molécula para exibir informação em formato label: valor.
 * Use para metadados e informações em pares.
 *
 * @param label Texto do rótulo (ex: "Resolução")
 * @param value Texto do valor (ex: "1920x1080")
 * @param modifier Modifier para customização
 * @param labelColor Cor do label
 * @param valueColor Cor do valor
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpacingTokens.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SmallText(
            text = label,
            color = labelColor.copy(alpha = 0.7f)
        )
        SmallText(
            text = value,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * Variação de InfoRow com destaque no valor.
 * Use quando o valor precisa de mais atenção.
 *
 * @param label Texto do rótulo
 * @param value Texto do valor
 * @param modifier Modifier para customização
 */
@Composable
fun InfoRowHighlighted(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpacingTokens.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MediumText(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MediumText(
            text = value,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun InfoRowPreview() {
    ChopCutTheme {
        InfoRow(
            label = "Resolução",
            value = "1920x1080",
            modifier = Modifier.padding(SpacingTokens.lg)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InfoRowHighlightedPreview() {
    ChopCutTheme {
        InfoRowHighlighted(
            label = "Duração",
            value = "3:45",
            modifier = Modifier.padding(SpacingTokens.lg)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InfoRowListPreview() {
    ChopCutTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(SpacingTokens.lg)
        ) {
            InfoRow(label = "Resolução", value = "1920x1080")
            InfoRow(label = "Duração", value = "3:45")
            InfoRow(label = "Codec", value = "H.264")
            InfoRow(label = "Bitrate", value = "8 Mbps")
        }
    }
}
