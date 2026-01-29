package com.chopcut.core.designsystem.molecules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.PrimaryCard
import com.chopcut.core.designsystem.atoms.SubtitleText
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Molécula para exibir informações de um vídeo em um card.
 * Use quando um vídeo é selecionado para mostrar seus metadados.
 *
 * @param fileName Nome do arquivo
 * @param resolution Resolução (ex: "1920x1080")
 * @param duration Duração formatada (ex: "3:45")
 * @param codec Codec de vídeo (ex: "H.264")
 * @param modifier Modifier para customização
 */
@Composable
fun VideoInfoCard(
    fileName: String,
    resolution: String,
    duration: String,
    codec: String,
    modifier: Modifier = Modifier
) {
    PrimaryCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SpacingTokens.lg)) {
            SubtitleText(
                text = fileName,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(SpacingTokens.md))
            InfoRow(
                label = "Resolução",
                value = resolution,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                valueColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            InfoRow(
                label = "Duração",
                value = duration,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                valueColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            InfoRow(
                label = "Codec",
                value = codec,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                valueColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Versão stateless que aceita um objeto VideoInfo.
 * Extensão conveniente para uso com o modelo de dados do app.
 */
@Composable
fun VideoInfoCard(
    videoInfo: com.chopcut.data.model.VideoInfo,
    modifier: Modifier = Modifier
) {
    VideoInfoCard(
        fileName = videoInfo.fileName,
        resolution = "${videoInfo.width}x${videoInfo.height}",
        duration = formatDuration(videoInfo.durationMs),
        codec = videoInfo.videoCodec ?: "Unknown",
        modifier = modifier
    )
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun VideoInfoCardPreview() {
    ChopCutTheme {
        VideoInfoCard(
            fileName = "video_teste.mp4",
            resolution = "1920x1080",
            duration = "3:45",
            codec = "H.264",
            modifier = Modifier.padding(SpacingTokens.lg)
        )
    }
}
