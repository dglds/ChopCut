package com.chopcut.ui.components.loading

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chopcut.ui.viewmodel.PreloadProgress
import com.chopcut.ui.theme.ChopCutAnimation
import com.chopcut.ui.theme.ChopCutEasing

/**
 * Overlay de loading simplificado exibido durante o carregamento inicial da TrimScreen.
 *
 * Features:
 * - Animação Lottie (apenas spinner)
 * - Mensagem de status
 * - Transição suave com TrimScreen
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
