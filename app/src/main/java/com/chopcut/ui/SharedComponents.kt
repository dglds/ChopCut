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

// --- Merged from LoadingAnimation.kt ---


/**
 * Componentes de animação para o LoadingOverlay.
 *
 * Inclui:
 * - LottieLoadingAnimation: Animação principal em Lottie
 * - CircularProgressWithPercentage: Indicador circular sem texto
 * - StageMessage: Mensagem genérica de status
 * - FakeProgressBar: Barra de progresso baseada em tempo
 */

/**
 * Animação nativa de loading usando Canvas.
 * Círculos concêntricos com rotação e pulsação.
 */
@Composable
fun LottieLoadingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_animation")

    // Rotação dos círculos
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulsação
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Opacidade alternada
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = size.minDimension
            val centerX = size.width / 2
            val centerY = size.height / 2

            // Círculo externo
            drawCircle(
                color = primaryColor,
                radius = canvasSize * 0.35f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                alpha = alpha * 0.2f,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // Círculo médio (rotacionando)
            val middleRadius = canvasSize * 0.25f
            val angleRad = Math.toRadians(rotation.toDouble())
            val offsetX = (middleRadius * 0.3 * kotlin.math.cos(angleRad)).toFloat()
            val offsetY = (middleRadius * 0.3 * kotlin.math.sin(angleRad)).toFloat()

            drawCircle(
                color = secondaryColor,
                radius = middleRadius,
                center = androidx.compose.ui.geometry.Offset(centerX + offsetX, centerY + offsetY),
                alpha = alpha * 0.4f,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Círculo interno
            drawCircle(
                color = primaryColor,
                radius = canvasSize * 0.15f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                alpha = alpha
            )
        }
    }
}

/**
 * Indicador circular de progresso sem porcentagem.
 * Apenas visual, não representa progresso real.
 */
@Composable
fun CircularProgressWithPercentage(
    progress: PreloadProgress
) {
    val infiniteTransition = rememberInfiniteTransition(label = "progress_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(80.dp)
                .rotate(rotation),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/**
 * Mensagem genérica de status baseada no estágio atual.
 * Usa mensagens amigáveis ao invés de termos técnicos.
 */
@Composable
fun StageMessage(stage: PreloadStage, progress: PreloadProgress) {
    val infiniteTransition = rememberInfiniteTransition(label = "message_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    AnimatedContent(
        targetState = getStageMessage(stage, progress),
        transitionSpec = {
            (fadeIn(tween(ChopCutAnimation.Normal)) +
                slideInVertically { height -> height / 3 }) togetherWith
            (fadeOut(tween(ChopCutAnimation.Fast)) +
                slideOutVertically { height -> -height / 3 })
        },
        label = "stage_message"
    ) { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        )
    }
}

/**
 * Barra de progresso baseada em dados reais (thumbnails + audio).
 * Progride de 0% a 95% durante o processamento.
 *
 * NOTA: Esta função não é mais usada na tela de loading simplificada.
 * Mantida para compatibilidade caso seja necessária futuramente.
 */
@Composable
fun FakeProgressBar(progress: PreloadProgress, elapsedTimeMs: Long, isReadyToHide: Boolean = false) {
    val realProgress = calculateRealProgress(progress)
    val timeBasedProgress = remember(elapsedTimeMs, isReadyToHide) {
        if (isReadyToHide) 1f else calculateFakeProgress(elapsedTimeMs)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "progress_bar_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = realProgress.coerceAtLeast(timeBasedProgress * 0.3f),
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "animated_progress"
    )

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RectangleShape)
            .graphicsLayer {
                alpha = if (animatedProgress < 1f) glowAlpha else 1f
            },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

// Funções auxiliares

/**
 * Calcula progresso real baseado em dados extraídos.
 *
 * Estratégia:
 * - Thumbnails: 60% do progresso total
 * - Audio: 40% do progresso total
 * - Usando pesos definidos em LoadingConstants
 */
private fun calculateRealProgress(progress: PreloadProgress): Float {
    val thumbnailProgress = if (progress.thumbnailsTotal > 0) {
        progress.thumbnailsExtracted.toFloat() / progress.thumbnailsTotal.toFloat()
    } else {
        progress.thumbnailPercent / 100f
    }

    val audioProgress = if (progress.audioAmplitudesTotal > 0) {
        progress.audioAmplitudesCount.toFloat() / progress.audioAmplitudesTotal.toFloat()
    } else {
        progress.audioPercent / 100f
    }

    return (thumbnailProgress * LoadingConstants.THUMBNAIL_PROGRESS_WEIGHT +
            audioProgress * LoadingConstants.AUDIO_PROGRESS_WEIGHT)
        .coerceIn(0f, LoadingConstants.PROGRESS_BAR_MAX_VALUE)
}

/**
 * Calcula progresso falso baseado em tempo decorrido.
 *
 * Estratégia:
 * - 0-2.1s: Progresso rápido (0% → 50%) - mostra atividade
 * - 2.1-4.9s: Progresso médio (50% → 80%) - estável
 * - 4.9-7s: Progresso lento (80% → 90%) - aproximando do fim
 * - Máximo 95% até confirmação real
 */
private fun calculateFakeProgress(elapsedTimeMs: Long): Float {
    if (elapsedTimeMs >= LoadingConstants.MAX_LOADING_DURATION_MS) {
        return LoadingConstants.PROGRESS_BAR_MAX_VALUE
    }

    val normalizedTime = (elapsedTimeMs.toFloat() / LoadingConstants.TARGET_DURATION_MS)
        .coerceIn(0f, 1f)

    return when {
        normalizedTime < 0.3f -> (normalizedTime / 0.3f) * 0.5f
        normalizedTime < 0.7f -> 0.5f + ((normalizedTime - 0.3f) / 0.4f) * 0.3f
        else -> 0.8f + ((normalizedTime - 0.7f) / 0.3f) * 0.1f
    }.coerceIn(0f, LoadingConstants.PROGRESS_BAR_MAX_VALUE)
}

/**
 * Retorna mensagem genérica baseada no estágio de extração.
 * Usa linguagem amigável ao invés de termos técnicos.
 */
private fun getStageMessage(stage: PreloadStage, progress: PreloadProgress): String {
    return when (stage) {
        PreloadStage.Starting,
        PreloadStage.Validating -> "Preparando vídeo..."
        PreloadStage.ExtractingAudio -> {
            if (progress.audioAmplitudesTotal > 0) {
                "Extraíndo áudio: ${progress.audioAmplitudesCount}/${progress.audioAmplitudesTotal}"
            } else {
                "Extraíndo áudio..."
            }
        }
        PreloadStage.ExtractingThumbnails -> {
            if (progress.thumbnailsTotal > 0) {
                "Extraíndo ${progress.thumbnailsExtracted}/${progress.thumbnailsTotal} thumbnails"
            } else {
                "Extraíndo thumbnails..."
            }
        }
        PreloadStage.Ready -> "Pronto!"
    }
}

// --- Merged from LoadingConstants.kt ---

/**
 * Constantes para configuração do LoadingOverlay e suas animações
 */
object LoadingConstants {

    // Limite de duração para considerar vídeo "curto" (pular loading se cache hit)
    const val SHORT_VIDEO_THRESHOLD_MS = 60_000L   // 60 segundos

    // Duração do loading - calculado dinamicamente
    // MIN_LOADING_DURATION_MS agora é calculado como 5% da duração do vídeo
    // const val MIN_LOADING_DURATION_MS = 2_000L    // REMOVIDO: Agora é dinâmico

    const val MAX_LOADING_DURATION_MS = 5_000L    // Máximo 5 segundos (foco em renderização)
    const val TARGET_DURATION_MS = 3_500L         // Duração otimista para progressão

    // Porcentagem da duração do vídeo para tempo mínimo de loading
    const val MIN_LOADING_PERCENTAGE = 0.01f      // 1% da duração do vídeo (era 5%)

    // Progresso mínimo de thumbnails
    const val MINIMUM_THUMBNAIL_PROGRESS = 20f     // 20% das thumbnails (foco em renderizar o que existe)

    // Número fixo de strips a carregar antes de permitir navegação
    const val MINIMUM_STRIPS_REQUIRED = 6          // REQUERIDO: 6 strips fixo

    // Timings de transição
    const val CROSS_FADE_START_DELAY_MS = 100L      // Delay reduzido para iniciar cross-fade

    // Animações - Overlay
    const val OVERLAY_FADE_OUT_DURATION_MS = 500     // Fade out do overlay (reduzido de 700ms)
    const val OVERLAY_SCALE_OUT_TARGET = 0.95f        // Scale final do overlay

    // Animações - EditorScreen (sincronizado com overlay)
    const val TRIM_FADE_IN_DURATION_MS = 500          // Fade in da EditorScreen (reduzido de 700ms)
    const val TRIM_SCALE_IN_START = 0.98f             // Scale inicial suave
    const val TRIM_SCALE_IN_END = 1.0f               // Scale final

    // Animações - Navegação
    const val NAV_FADE_IN_DURATION_MS = 400           // Fade in de navegação
    const val NAV_FADE_OUT_DURATION_MS = 400          // Fade out de navegação
    const val NAV_SCALE_START = 0.95f                 // Scale inicial suave
    const val NAV_SCALE_END = 1.0f                   // Scale final

    // Animações - Barra de progresso
    const val PROGRESS_BAR_MAX_VALUE = 0.95f          // Máximo 95% até confirmar

    // Intervalo de verificação
    const val LOADING_CHECK_INTERVAL_MS = 100L          // Verificar a cada 100ms

    // Pesos para cálculo de progresso real
    const val THUMBNAIL_PROGRESS_WEIGHT = 0.6f       // Thumbnails contribuem 60%
    const val AUDIO_PROGRESS_WEIGHT = 0.4f            // Audio contribui 40%
}

// --- Merged from LoadingOverlay.kt ---


/**
 * Overlay de loading simplificado exibido durante o carregamento inicial da EditorScreen.
 *
 * Features:
 * - Animação Lottie (apenas spinner)
 * - Mensagem de status
 * - Transição suave com EditorScreen
 */

@Composable
fun LoadingOverlay(
    progress: PreloadProgress,
    elapsedTimeMs: Long = 0L,
    isReadyToHide: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000)) // 80% black scrim
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* Block clicks - não cancela ao clicar fora */ },
        contentAlignment = Alignment.Center
    ) {
            LoadingCard(progress = progress, elapsedTimeMs = elapsedTimeMs)
        }
}

@Composable
fun LoadingCard(
    progress: PreloadProgress,
    elapsedTimeMs: Long = 0L
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Spinner - animação Lottie
        AnimatedElement(delayMillis = 0) {
            LottieLoadingAnimation()
        }

        // Mensagem de status
        AnimatedElement(delayMillis = 150) {
            StageMessage(stage = progress.stage, progress = progress)
        }

        // Contador de tempo
        AnimatedElement(delayMillis = 200) {
            TimeCounter(elapsedTimeMs = elapsedTimeMs)
        }
    }
}

@Composable
private fun TimeCounter(elapsedTimeMs: Long) {
    val seconds = (elapsedTimeMs / 1000).toInt()
    val displayTime = if (seconds < 60) {
        "${seconds}s"
    } else {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        String.format("%d:%02d", minutes, remainingSeconds)
    }

    Text(
        text = displayTime,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    )
}

@Composable
private fun AnimatedElement(
    delayMillis: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 400,
                easing = FastOutSlowInEasing
            )
        ) + slideInVertically(
            initialOffsetY = { it / 6 },
            animationSpec = tween(
                durationMillis = 400,
                easing = ChopCutEasing.Emphasized
            )
        ),
        label = "element_animation"
    ) {
        content()
    }
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

// --- Merged from EditorSplitLayout.kt ---


@Composable
fun EditorSplitLayout(
    topContent: @Composable BoxScope.() -> Unit,
    bottomContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    topWeight: Float = 0.6f,
    bottomWeight: Float = 0.4f
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Área 1: Topo - Fixa
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(topWeight)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            topContent()
        }

        // Área 2: Base - Dinâmica
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(bottomWeight)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            bottomContent()
        }
    }
}
// --- Merged from ToolPanelContainer.kt ---


/**
 * Container for dynamic tool panels in the editor.
 * Handles transitions between different tool states.
 */
@Composable
fun <T> ToolPanelContainer(
    currentState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        AnimatedContent(
            targetState = currentState,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300)) using
                        SizeTransform(clip = false)
            },
            label = "ToolPanelTransition"
        ) { targetState ->
            content(targetState)
        }
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
