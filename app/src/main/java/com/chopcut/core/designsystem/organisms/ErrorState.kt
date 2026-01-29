package com.chopcut.core.designsystem.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.BodyText
import com.chopcut.core.designsystem.atoms.PrimaryButton
import com.chopcut.core.designsystem.atoms.SecondaryButton
import com.chopcut.core.designsystem.atoms.SecondaryCard
import com.chopcut.core.designsystem.atoms.SmallText
import com.chopcut.core.designsystem.atoms.SubtitleText
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SizeTokens
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Estado de erro com mensagem e ações de recuperação.
 * Use quando ocorre um erro carregando dados ou executando ações.
 *
 * @param message Mensagem de erro
 * @param modifier Modifier para customização
 * @param title Título do erro (opcional)
 * @param icon Ícone representativo
 * @param retryLabel Label do botão de tentar novamente
 * @param onRetry Ação de tentar novamente (null = sem botão)
 * @param onDismiss Ação de dispensar erro (null = sem botão)
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Erro",
    icon: ImageVector = Icons.Default.ErrorOutline,
    retryLabel: String = "Tentar novamente",
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    SecondaryCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SpacingTokens.lg),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(SizeTokens.iconLg),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(SpacingTokens.md))
                SubtitleText(
                    text = title,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(SpacingTokens.md))

            BodyText(
                text = message,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            if (onRetry != null || onDismiss != null) {
                Spacer(modifier = Modifier.height(SpacingTokens.lg))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
                ) {
                    if (onRetry != null) {
                        PrimaryButton(
                            onClick = onRetry,
                            text = retryLabel
                        )
                    }
                    if (onDismiss != null) {
                        SecondaryButton(
                            onClick = onDismiss,
                            text = "Ignorar"
                        )
                    }
                }
            }
        }
    }
}

/**
 * Estado de erro em tela cheia.
 * Use para erros críticos que impedem o uso da tela.
 *
 * @param message Mensagem de erro
 * @param modifier Modifier para customização
 * @param title Título do erro
 * @param onRetry Ação de tentar novamente
 */
@Composable
fun FullScreenError(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Algo deu errado",
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingTokens.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(SizeTokens.iconHero),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(SpacingTokens.xl))

        SubtitleText(
            text = title,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(SpacingTokens.md))

        BodyText(
            text = message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(SpacingTokens.xxl))
            PrimaryButton(
                onClick = onRetry,
                text = "Tentar novamente"
            )
        }
    }
}

/**
 * Banner de aviso.
 * Use para mensagens de aviso não críticas.
 *
 * @param message Mensagem de aviso
 * @param modifier Modifier para customização
 */
@Composable
fun WarningBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(SpacingTokens.md)
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(SpacingTokens.radiusSm)
            )
            .padding(SpacingTokens.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(SizeTokens.iconMd)
        )
        SmallText(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun ErrorStatePreview() {
    ChopCutTheme {
        ErrorState(
            message = "Não foi possível carregar os projetos. Verifique sua conexão.",
            onRetry = {},
            onDismiss = {},
            modifier = Modifier.padding(SpacingTokens.lg)
        )
    }
}

@Preview(showBackground = true, heightDp = 300)
@Composable
private fun FullScreenErrorPreview() {
    ChopCutTheme {
        FullScreenError(
            message = "Ocorreu um erro ao carregar o editor. Tente novamente mais tarde.",
            onRetry = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WarningBannerPreview() {
    ChopCutTheme {
        WarningBanner(
            message = "Atenção: Esta ação não pode ser desfeita."
        )
    }
}
