package com.chopcut.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape
import com.chopcut.ui.theme.ChopCutTypography
import com.chopcut.ui.theme.DurationTextStyle
import com.chopcut.ui.theme.OnSurface
import com.chopcut.ui.theme.OverlayDark
import com.chopcut.ui.theme.Surface

/**
 * Card de vídeo do Design System ChopCut
 *
 * Usado para exibir thumbnails de vídeos na galeria e projetos salvos
 *
 * @param thumbnail Painter ou ImageBitmap do thumbnail
 * @param title Título do vídeo/projeto
 * @param duration Duração formatada (ex: "01:23")
 * @param modifier Modificador
 * @param onClick Ação ao clicar
 * @param icon Ícone opcional sobreposto (play, edit, etc)
 * @param subtitle Subtítulo opcional (data, tamanho, etc)
 */
@Composable
fun VideoCard(
    thumbnail: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    duration: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null
) {
    Column(
        modifier = modifier
            .clip(RectangleShape)
            .background(Surface)
            .clickable(onClick = onClick)
    ) {
        // Thumbnail com overlay gradiente
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            // Thumbnail
            androidx.compose.foundation.Image(
                painter = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Overlay gradiente (base-to-top)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                OverlayDark
                            )
                        )
                    )
            )

            // Duração (canto superior direito)
            Text(
                text = duration,
                style = DurationTextStyle.copy(color = Color.White),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RectangleShape
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            // Ícone central (se fornecido)
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                        .size(48.dp)
                )
            }
        }

        // Título e subtítulo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                style = ChopCutTypography.titleMedium,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = ChopCutTypography.bodySmall,
                    color = OnSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Card genérico do Design System ChopCut
 *
 * Usado para agrupar conteúdo relacionado
 *
 * @param modifier Modificador
 * @param showShadow Se deve mostrar sombra
 * @param content Conteúdo do card
 */
@Composable
fun ChopCutCard(
    modifier: Modifier = Modifier,
    showShadow: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RectangleShape)
            .background(Surface)
            .then(
                if (showShadow) {
                    Modifier.shadow(
                        elevation = 2.dp,
                        shape = RectangleShape,
                        spotColor = Color.Black.copy(alpha = 0.1f)
                    )
                } else {
                    Modifier
                }
            )
            .padding(16.dp),
        content = content
    )
}
