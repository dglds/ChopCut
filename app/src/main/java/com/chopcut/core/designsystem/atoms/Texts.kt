package com.chopcut.core.designsystem.atoms

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.theme.ChopCutTheme

/**
 * Texto de headline grande.
 * Use para títulos principais de telas.
 *
 * @param text Texto a ser exibido
 * @param modifier Modifier para customização
 * @param color Cor do texto (usa onSurface do tema por padrão)
 * @param textAlign Alinhamento do texto
 * @param maxLines Número máximo de linhas
 */
@Composable
fun HeadlineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineLarge,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * Texto de headline médio.
 * Use para subtítulos ou títulos de seções.
 *
 * @param text Texto a ser exibido
 * @param modifier Modifier para customização
 * @param color Cor do texto (usa onSurface do tema por padrão)
 * @param textAlign Alinhamento do texto
 * @param maxLines Número máximo de linhas
 */
@Composable
fun TitleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * Texto de headline pequeno.
 * Use para títulos de cards ou items.
 *
 * @param text Texto a ser exibido
 * @param modifier Modifier para customização
 * @param color Cor do texto (usa onSurface do tema por padrão)
 * @param textAlign Alinhamento do texto
 * @param maxLines Número máximo de linhas
 */
@Composable
fun SubtitleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * Texto de corpo grande.
 * Use para textos principais e descrições.
 *
 * @param text Texto a ser exibido
 * @param modifier Modifier para customização
 * @param color Cor do texto (usa onSurface do tema por padrão)
 * @param textAlign Alinhamento do texto
 * @param maxLines Número máximo de linhas
 * @param fontWeight Peso da fonte
 */
@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        fontWeight = fontWeight
    )
}

/**
 * Texto de corpo médio.
 * Use para textos secundários.
 *
 * @param text Texto a ser exibido
 * @param modifier Modifier para customização
 * @param color Cor do texto (usa onSurface do tema por padrão)
 * @param textAlign Alinhamento do texto
 * @param maxLines Número máximo de linhas
 * @param fontWeight Peso da fonte
 */
@Composable
fun MediumText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        fontWeight = fontWeight
    )
}

/**
 * Texto de corpo pequeno.
 * Use para legendas, metadados e textos auxiliares.
 *
 * @param text Texto a ser exibido
 * @param modifier Modifier para customização
 * @param color Cor do texto (usa onSurfaceVariant do tema por padrão)
 * @param textAlign Alinhamento do texto
 * @param maxLines Número máximo de linhas
 * @param fontWeight Peso da fonte
 */
@Composable
fun SmallText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        fontWeight = fontWeight
    )
}

/**
 * Texto de label.
 * Use para labels de campos e navegação.
 *
 * @param text Texto a ser exibido
 * @param modifier Modifier para customização
 * @param color Cor do texto (usa onSurface do tema por padrão)
 * @param textAlign Alinhamento do texto
 * @param maxLines Número máximo de linhas
 * @param fontWeight Peso da fonte
 */
@Composable
fun LabelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        fontWeight = fontWeight
    )
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun TextsPreview() {
    ChopCutTheme {
        androidx.compose.foundation.layout.Column {
            HeadlineText(text = "Headline Large")
            TitleText(text = "Title Large")
            SubtitleText(text = "Title Medium")
            BodyText(text = "Body Large")
            MediumText(text = "Body Medium")
            SmallText(text = "Body Small")
            LabelText(text = "Label Large")
        }
    }
}
