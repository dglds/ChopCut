package com.chopcut.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Componente de thumbnail individual para a VideoTimeline V2
 *
 * Renderiza um único thumbnail de vídeo na timeline com suporte a:
 * - Bitmap já carregado
 * - Placeholder durante carregamento
 * - Tratamento de erro
 * - Tamanho configurável
 *
 * @param bitmap Bitmap do thumbnail (null se ainda não carregado)
 * @param positionMs Posição do thumbnail no vídeo em milissegundos
 * @param width Largura do thumbnail em pixels
 * @param height Altura do thumbnail em pixels
 * @param modifier Modifier para o componente
 */
@Composable
fun TimelineThumbnailItem(
    bitmap: Bitmap?,
    positionMs: Long,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier
) {
    val widthDp = with(LocalDensity.current) { width.toDp() }
    val heightDp = with(LocalDensity.current) { height.toDp() }

    Box(
        modifier = modifier
            .size(width = widthDp, height = heightDp)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> {
                // Thumbnail carregado
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Thumbnail at ${positionMs}ms",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(widthDp, heightDp)
                )
            }
            else -> {
                // Placeholder ou erro
                Text(
                    text = "${(positionMs / 1000)}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
