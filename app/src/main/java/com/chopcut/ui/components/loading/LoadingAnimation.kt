package com.chopcut.ui.components.loading

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import com.chopcut.ui.screen.ExtractionStage
import com.chopcut.ui.screen.PreloadProgress
import com.chopcut.ui.theme.ChopCutAnimation

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
fun StageMessage(stage: ExtractionStage, progress: PreloadProgress) {
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
 * Progride de 0% a 95% durante o processamento,
 * depois completa para 100% quando isReadyToHide = true.
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
            durationMillis = if (isReadyToHide) {
                LoadingConstants.PROGRESS_BAR_ANIMATION_FINAL_MS
            } else {
                LoadingConstants.PROGRESS_BAR_ANIMATION_NORMAL_MS
            },
            easing = if (isReadyToHide) LinearEasing else FastOutSlowInEasing
        ),
        label = "animated_progress"
    )

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
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
private fun getStageMessage(stage: ExtractionStage, progress: PreloadProgress): String {
    return when (stage) {
        ExtractionStage.Starting,
        ExtractionStage.Validating -> "Preparando vídeo..."
        ExtractionStage.ExtractingAudio -> {
            if (progress.audioAmplitudesTotal > 0) {
                "Extraíndo áudio: ${progress.audioAmplitudesCount}/${progress.audioAmplitudesTotal}"
            } else {
                "Extraíndo áudio..."
            }
        }
        ExtractionStage.ExtractingThumbnails -> {
            if (progress.thumbnailsTotal > 0) {
                "Extraíndo ${progress.thumbnailsExtracted}/${progress.thumbnailsTotal} thumbnails"
            } else {
                "Extraíndo thumbnails..."
            }
        }
        ExtractionStage.Ready -> "Pronto!"
    }
}
