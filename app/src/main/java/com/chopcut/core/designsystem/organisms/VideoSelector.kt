package com.chopcut.core.designsystem.organisms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.BodyText
import com.chopcut.core.designsystem.atoms.PrimaryButton
import com.chopcut.core.designsystem.atoms.SmallText
import com.chopcut.core.designsystem.atoms.SurfaceCard
import com.chopcut.core.designsystem.molecules.VideoInfoCard
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Seletor de vídeo com estado vazio.
 * Use quando nenhum vídeo foi selecionado ainda.
 *
 * @param onSelectVideo Ação ao clicar em selecionar vídeo
 * @param modifier Modifier para customização
 * @param title Título do card
 * @param subtitle Subtítulo descritivo
 */
@Composable
fun VideoSelector(
    onSelectVideo: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Começar a Editar",
    subtitle: String = "Selecione um vídeo do seu dispositivo"
) {
    SurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(SpacingTokens.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
        ) {
            BodyText(
                text = title,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            SmallText(
                text = subtitle,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )

            PrimaryButton(
                onClick = onSelectVideo,
                text = "Selecionar Vídeo",
                icon = Icons.Default.VideoFile
            )
        }
    }
}

/**
 * Seletor de vídeo com vídeo já selecionado.
 * Mostra as informações do vídeo e permite trocar.
 *
 * @param videoInfo Informações do vídeo selecionado
 * @param onSelectVideo Ação para selecionar outro vídeo
 * @param onOpenEditor Ação para abrir o editor
 * @param modifier Modifier para customização
 */
@Composable
fun VideoSelectorWithVideo(
    videoInfo: com.chopcut.data.model.VideoInfo,
    onSelectVideo: () -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
    ) {
        VideoInfoCard(videoInfo = videoInfo)

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
            com.chopcut.core.designsystem.atoms.OutlinedButtonAtom(
                onClick = onSelectVideo,
                text = "Trocar",
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                onClick = onOpenEditor,
                text = "Abrir Editor",
                icon = Icons.Default.PlayArrow,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Estado de loading durante a seleção/carregamento do vídeo.
 *
 * @param modifier Modifier para customização
 * @param message Mensagem de loading
 */
@Composable
fun VideoSelectorLoading(
    modifier: Modifier = Modifier,
    message: String = "Carregando informações do vídeo..."
) {
    SurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            com.chopcut.core.designsystem.atoms.LoadingSpinner()
            Spacer(modifier = Modifier.height(SpacingTokens.lg))
            SmallText(
                text = message,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun VideoSelectorPreview() {
    ChopCutTheme {
        VideoSelector(
            onSelectVideo = {},
            modifier = Modifier.padding(SpacingTokens.lg)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoSelectorWithVideoPreview() {
    ChopCutTheme {
        VideoSelectorWithVideo(
            videoInfo = com.chopcut.data.model.VideoInfo(
                uri = android.net.Uri.EMPTY,
                fileName = "video_teste.mp4",
                width = 1920,
                height = 1080,
                durationMs = 225000,
                videoCodec = "H.264"
            ),
            onSelectVideo = {},
            onOpenEditor = {},
            modifier = Modifier.padding(SpacingTokens.lg)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoSelectorLoadingPreview() {
    ChopCutTheme {
        VideoSelectorLoading(
            modifier = Modifier.padding(SpacingTokens.lg)
        )
    }
}
