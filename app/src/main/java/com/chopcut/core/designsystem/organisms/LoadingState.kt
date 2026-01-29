package com.chopcut.core.designsystem.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.BodyText
import com.chopcut.core.designsystem.atoms.LargeLoadingSpinner
import com.chopcut.core.designsystem.atoms.LoadingSpinner
import com.chopcut.core.designsystem.atoms.SmallText
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Estado de carregamento em tela cheia.
 * Use durante operações de carregamento iniciais.
 *
 * @param modifier Modifier para customização
 * @param message Mensagem opcional abaixo do spinner
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LoadingSpinner()

        if (message != null) {
            Spacer(modifier = Modifier.height(SpacingTokens.xl))
            BodyText(
                text = message,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Estado de carregamento em tela cheia com spinner grande.
 * Use para operações pesadas que levam tempo.
 *
 * @param modifier Modifier para customização
 * @param title Título do loading
 * @param subtitle Subtítulo descritivo
 */
@Composable
fun FullScreenLoading(
    modifier: Modifier = Modifier,
    title: String = "Carregando...",
    subtitle: String? = null
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LargeLoadingSpinner()

        Spacer(modifier = Modifier.height(SpacingTokens.xl))

        BodyText(
            text = title,
            textAlign = TextAlign.Center
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(SpacingTokens.md))
            SmallText(
                text = subtitle,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Loading com progresso linear.
 * Use quando é possível mostrar progresso determinado.
 *
 * @param progress Progresso de 0.0 a 1.0
 * @param modifier Modifier para customização
 * @param label Label opcional acima do progresso
 */
@Composable
fun LinearProgressLoading(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SpacingTokens.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (label != null) {
            SmallText(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(SpacingTokens.sm))
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Loading indeterminado linear.
 * Use quando não é possível determinar o progresso.
 *
 * @param modifier Modifier para customização
 * @param label Label opcional
 */
@Composable
fun IndeterminateLinearLoading(
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SpacingTokens.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (label != null) {
            SmallText(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(SpacingTokens.sm))
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Overlay de loading.
 * Use para bloquear interações durante operações.
 *
 * @param modifier Modifier para customização
 * @param message Mensagem do loading
 */
@Composable
fun LoadingOverlay(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background escuro semi-transparente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
        )

        // Loading no centro
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LargeLoadingSpinner()

            if (message != null) {
                Spacer(modifier = Modifier.height(SpacingTokens.xl))
                BodyText(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true, heightDp = 200)
@Composable
private fun LoadingStatePreview() {
    ChopCutTheme {
        LoadingState(message = "Carregando projetos...")
    }
}

@Preview(showBackground = true, heightDp = 300)
@Composable
private fun FullScreenLoadingPreview() {
    ChopCutTheme {
        FullScreenLoading(
            title = "Exportando vídeo...",
            subtitle = "Isso pode levar alguns minutos"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LinearProgressLoadingPreview() {
    ChopCutTheme {
        LinearProgressLoading(
            progress = 0.6f,
            label = "Exportando... 60%"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IndeterminateLinearLoadingPreview() {
    ChopCutTheme {
        IndeterminateLinearLoading(label = "Processando...")
    }
}
