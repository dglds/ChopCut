package com.chopcut

import androidx.compose.animation.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest


// --- Merged from DurationLabel.kt ---


/**
 * Label de duração com fonte monoespaçada
 *
 * Usado para exibir timestamps de vídeo de forma alinhada
 *
 * @param duration Duração formatada (ex: "01:23:45")
 * @param modifier Modificador
 * @param backgroundColor Cor de fundo (opcional)
 * @param textColor Cor do texto (opcional)
 */
@Composable
fun DurationLabel(
    duration: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black.copy(alpha = 0.6f),
    textColor: Color = Color.White
) {
    Text(
        text = duration,
        style = DurationTextStyle.copy(
            fontFamily = ChopCutMonoFont,
            color = textColor
        ),
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RectangleShape
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * Pair de duração (atual / total)
 *
 * Ex: "01:23 / 05:00"
 *
 * @param current Duração atual
 * @param total Duração total
 * @param modifier Modificador
 */
@Composable
fun DurationPairLabel(
    current: String,
    total: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RectangleShape
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = current,
            style = DurationTextStyle.copy(
                fontFamily = ChopCutMonoFont,
                color = Color.White
            )
        )
        Text(
            text = " / ",
            style = DurationTextStyle.copy(
                fontFamily = ChopCutMonoFont,
                color = Color.White.copy(alpha = 0.7f)
            )
        )
        Text(
            text = total,
            style = DurationTextStyle.copy(
                fontFamily = ChopCutMonoFont,
                color = Color.White.copy(alpha = 0.7f)
            )
        )
    }
}

/**
 * Formata milissegundos para string de duração
 *
 * @param millis Duração em milissegundos
 * @param showHours Se deve incluir horas (true para vídeos > 1 hora)
 * @return String formatada (ex: "01:23:45" ou "01:23")
 */
fun formatDuration(millis: Long, showHours: Boolean = millis >= 3600000): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (showHours) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

// --- Merged from ChopCutButton.kt ---


/**
 * Botão primário do Design System ChopCut
 *
 * Usado para ações principais: Exportar, Salvar, Avançar
 *
 * @param text Texto do botão
 * @param onClick Ação ao clicar
 * @param modifier Modificador
 * @param enabled Se habilitado
 * @param icon Ícone opcional (ficará à esquerda do texto)
 */
@Composable
fun ChopCutPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Feedback visual ao pressionar (opacidade)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                // Aqui poderia adicionar feedback háptico
                // hapticConfirm(LocalContext.current)
            }
        }
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryColor(),
            contentColor = OnPrimary,
            disabledContainerColor = primaryColor().copy(alpha = PressedAlpha),
            disabledContentColor = OnPrimary.copy(alpha = PressedAlpha)
        ),
        contentPadding = PaddingValues(
            horizontal = 24.dp,
            vertical = 12.dp
        ),
        shape = RectangleShape
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = ChopCutTypography.labelLarge
        )
    }
}

/**
 * Botão secundário do Design System ChopCut
 *
 * Usado para ações secundárias: Cancelar, Voltar
 *
 * @param text Texto do botão
 * @param onClick Ação ao clicar
 * @param modifier Modificador
 * @param enabled Se habilitado
 */
@Composable
fun ChopCutSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = OnSurface
        ),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 10.dp
        ),
        shape = RectangleShape,
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = ChopCutTypography.labelMedium
        )
    }
}

// --- Merged from ChopCutFab.kt ---


/**
 * FAB (Floating Action Button) do Design System ChopCut
 *
 * Usado para ação principal na tela: Adicionar projeto, Novo vídeo
 *
 * @param onClick Ação ao clicar
 * @param modifier Modificador
 * @param icon Ícone a ser exibido
 * @param contentDescription Descrição para acessibilidade
 */
@Composable
fun ChopCutFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String?
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = primaryColor(),
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp,
            hoveredElevation = 10.dp,
            focusedElevation = 8.dp
        ),
        shape = RectangleShape
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Small FAB para ações secundárias flutuantes
 *
 * @param onClick Ação ao clicar
 * @param modifier Modificador
 * @param icon Ícone a ser exibido
 * @param contentDescription Descrição para acessibilidade
 */
@Composable
fun ChopCutSmallFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String?
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = primaryColor(),
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp,
            hoveredElevation = 6.dp,
            focusedElevation = 6.dp
        ),
        shape = RectangleShape
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

// --- Merged from VideoCard.kt ---


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
            .shadow(4.dp, RectangleShape)
            .clip(RectangleShape)
            .background(Surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, RectangleShape)
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
            .shadow(4.dp, RectangleShape)
            .clip(RectangleShape)
            .background(Surface)
            .border(2.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .padding(16.dp),
        content = content
    )
}



// --- Merged from ShimmerEffect.kt ---


fun Modifier.shimmerEffect(): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val shimmerColors = listOf(
        Color.Transparent,
        Color.White.copy(alpha = 0.15f),
        Color.Transparent
    )

    this.drawBehind {
        val brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 500f, 0f),
            end = Offset(translateAnim, size.height)
        )
        drawRect(brush = brush)
    }
}



/**
 * Estado de erro
 *
 * Usado para exibir mensagens de erro com opção de retry
 */
@Composable
fun ErrorState(
    icon: ImageVector = Icons.Outlined.ErrorOutline,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String = "Tentar novamente",
    onAction: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ErrorDark,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                style = ChopCutTypography.titleMedium,
                color = OnSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = message,
                style = ChopCutTypography.bodyMedium,
                color = OnSurface.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(24.dp))

            ChopCutPrimaryButton(
                text = actionLabel,
                onClick = onAction
            )
        }
    }
}

@Composable
fun VideoPickerEmpty(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Surface)
            .border(1.dp, Primary.copy(alpha = 0.3f), RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = ChopCutSpacing.md, vertical = ChopCutSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Primary.copy(alpha = 0.1f), RectangleShape)
                .border(1.dp, Primary.copy(alpha = 0.5f), RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Primary
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Selecionar Vídeo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Toque para importar da galeria",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Primary, RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = OnPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun VideoPickerLoading() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Surface)
            .border(1.dp, Divider, RectangleShape)
            .padding(horizontal = ChopCutSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.md)
    ) {
        CircularProgressIndicator(
            color = Primary,
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = "Carregando metadados do vídeo...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
fun VideoPickerLoaded(
    videoInfo: VideoInfo,
    videoUri: Uri,
    isPreloading: Boolean = false,
    onChangeVideo: () -> Unit,
    onOpenEditor: () -> Unit,
    onRemoveVideo: () -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(Surface)
            .border(1.dp, Divider, RectangleShape)
            .padding(ChopCutSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.sm)
    ) {
        // Thumbnail & Badges Preview (Left side)
        Box(
            modifier = Modifier
                .width(140.dp)
                .fillMaxHeight()
                .background(Color.Black)
                .border(1.dp, Border, RectangleShape)
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(videoUri)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader
                ),
                contentDescription = "Thumbnail do vídeo",
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            )

            // Duration Badge (Overlay bottom-right of thumbnail)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(ChopCutSpacing.xxs)
                    .background(Color.Black.copy(alpha = 0.7f), RectangleShape)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(videoInfo.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Aspect Ratio Badge (Overlay top-left of thumbnail)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(ChopCutSpacing.xxs)
                    .background(Primary.copy(alpha = 0.85f), RectangleShape)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = FormatUtils.getAspectRatio(videoInfo.width, videoInfo.height),
                    color = OnPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Details & Actions (Right side)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = videoInfo.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = buildString {
                        append("${videoInfo.width}×${videoInfo.height}")
                        append(" · ")
                        append(videoInfo.videoCodec ?: "N/A")
                        append(" · ")
                        append("${videoInfo.frameRate}fps")
                        if (videoInfo.hasAudio) append(" · 🔊")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Compact Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary action: Recortar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(Primary, RectangleShape)
                        .clickable(onClick = onOpenEditor),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPreloading) {
                            CircularProgressIndicator(
                                color = OnPrimary,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = null,
                                tint = OnPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (isPreloading) "..." else "Recortar",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimary
                        )
                    }
                }

                // Change Video Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SurfaceVariant, RectangleShape)
                        .border(1.dp, Border, RectangleShape)
                        .clickable(onClick = onChangeVideo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Trocar vídeo",
                        tint = OnSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Remove Video Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Error.copy(alpha = 0.15f), RectangleShape)
                        .border(1.dp, Error.copy(alpha = 0.5f), RectangleShape)
                        .clickable(onClick = onRemoveVideo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remover vídeo",
                        tint = Error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
