package com.chopcut.ui.components.loading

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chopcut.ui.screen.PreloadProgress
import com.chopcut.ui.theme.ChopCutAnimation
import com.chopcut.ui.theme.ChopCutEasing

/**
 * Overlay de loading exibido durante o carregamento inicial da TrimScreen.
 *
 * Features:
 * - Animação Lottie
 * - Progresso circular visual
 * - Mensagem de status genérica
 * - Barra de progresso falsa baseada em tempo
 * - Transição suave (cross-fade) com TrimScreen
 */

@Composable
fun LoadingOverlay(
    progress: PreloadProgress,
    elapsedTimeMs: Long = 0L,
    isReadyToHide: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = ChopCutAnimation.Normal,
                easing = ChopCutEasing.Emphasized
            )
        ) + scaleIn(
            initialScale = 0.85f,
            animationSpec = tween(
                durationMillis = ChopCutAnimation.Normal,
                easing = ChopCutEasing.Emphasized
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = LoadingConstants.OVERLAY_FADE_OUT_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        ) + scaleOut(
            targetScale = LoadingConstants.OVERLAY_SCALE_OUT_TARGET,
            animationSpec = tween(
                durationMillis = LoadingConstants.OVERLAY_FADE_OUT_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)) // 80% black scrim
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Block clicks - não cancela ao clicar fora */ },
            contentAlignment = Alignment.Center
        ) {
            LoadingCard(progress = progress, elapsedTimeMs = elapsedTimeMs, isReadyToHide = isReadyToHide)
        }
    }
}

@Composable
fun LoadingCard(
    progress: PreloadProgress,
    elapsedTimeMs: Long = 0L,
    isReadyToHide: Boolean = false
) {
    Card(
        modifier = Modifier
            .width(320.dp)
            .wrapContentHeight()
            .shimmerEffect(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 16.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animação Lottie - entra primeiro
            AnimatedElement(delayMillis = 0) {
                LottieLoadingAnimation()
            }

            Spacer(Modifier.height(24.dp))

            // Progresso circular - entra em segundo
            AnimatedElement(delayMillis = 100) {
                CircularProgressWithPercentage(progress = progress)
            }

            Spacer(Modifier.height(20.dp))

            // Mensagem - entra em terceiro
            AnimatedElement(delayMillis = 200) {
                StageMessage(stage = progress.stage)
            }

            Spacer(Modifier.height(16.dp))

            // Barra de progresso falsa - entra por último
            AnimatedElement(delayMillis = 300) {
                FakeProgressBar(elapsedTimeMs = elapsedTimeMs, isReadyToHide = isReadyToHide)
            }
        }
    }
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
            initialOffsetY = { it / 4 },
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
