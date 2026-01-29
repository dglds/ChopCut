package com.chopcut.core.designsystem.molecules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.HeadlineText
import com.chopcut.core.designsystem.atoms.LabelText
import com.chopcut.core.designsystem.atoms.SmallText
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Item de estatística com valor grande e label.
 * Use em dashboards ou cards de resumo.
 *
 * @param value Valor da estatística (ex: "42")
 * @param label Descrição da estatística (ex: "Projetos")
 * @param modifier Modifier para customização
 * @param unit Unidade opcional (ex: "min", "%")
 */
@Composable
fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null
) {
    Column(
        modifier = modifier.padding(SpacingTokens.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeadlineText(
            text = if (unit != null) "$value$unit" else value,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        LabelText(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Item de estatística compacto com valor médio.
 * Use em linhas ou quando espaço é limitado.
 *
 * @param value Valor da estatística
 * @param label Descrição da estatística
 * @param modifier Modifier para customização
 */
@Composable
fun CompactStatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(SpacingTokens.sm),
        horizontalAlignment = Alignment.Start
    ) {
        androidx.compose.material3.Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        SmallText(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun StatItemPreview() {
    ChopCutTheme {
        StatItem(
            value = "12",
            label = "Projetos"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatItemWithUnitPreview() {
    ChopCutTheme {
        StatItem(
            value = "85",
            label = "Compressão",
            unit = "%"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompactStatItemPreview() {
    ChopCutTheme {
        CompactStatItem(
            value = "24",
            label = "Vídeos editados"
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun StatsRowPreview() {
    ChopCutTheme {
        androidx.compose.foundation.layout.Row {
            StatItem(value = "12", label = "Projetos", modifier = Modifier.weight(1f))
            StatItem(value = "48", label = "Vídeos", modifier = Modifier.weight(1f))
            StatItem(value = "5h", label = "Editado", modifier = Modifier.weight(1f))
        }
    }
}
