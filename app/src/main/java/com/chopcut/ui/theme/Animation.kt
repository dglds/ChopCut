package com.chopcut.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.runtime.Composable

/**
 * Durações de animação do Design System ChopCut
 *
 * Baseado em Material Motion com ajustes para editor de vídeo
 */
object ChopCutAnimation {
    const val Fast = 50       // Micro-interações instantâneas
    const val Normal = 100    // Transições padrão
    const val Slow = 150      // Transições de tela
}

/**
 * Easing curves do Design System ChopCut
 */
object ChopCutEasing {
    /**
     * Padrão agora é linear para sensação brutalista e seca
     */
    val Standard = LinearEasing

    /**
     * Transições diretas
     */
    val Emphasized = LinearEasing

    /**
     * Linear puro
     */
    val Linear = LinearEasing

    /**
     * Para saídas rápidas
     */
    val FastOutLinearIn = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    /**
     * Para entradas suaves
     */
    val LinearOutSlowIn = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
}

/**
 * Animation specs pré-configurados
 */
object ChopCutAnimationSpec {
    /**
     * Spec para animações rápidas (feedback visual)
     */
    fun fast() = TweenSpec<Float>(
        durationMillis = ChopCutAnimation.Fast,
        easing = ChopCutEasing.Standard
    )

    /**
     * Spec para animações normais
     */
    fun normal() = TweenSpec<Float>(
        durationMillis = ChopCutAnimation.Normal,
        easing = ChopCutEasing.Standard
    )

    /**
     * Spec para animações lentas
     */
    fun slow() = TweenSpec<Float>(
        durationMillis = ChopCutAnimation.Slow,
        easing = ChopCutEasing.Emphasized
    )

    /**
     * Spec para animações com spring
     * Útil para movimentos naturais
     */
    fun spring() = SpringSpec<Float>(
        dampingRatio = 0.85f,
        stiffness = 300f
    )
}

/**
 * Respeita a configuração de movimento reduzido do sistema
 * @param defaultSpec Spec padrão quando movimento reduzido não está ativo
 */
@Composable
fun respectReducedMotion(defaultSpec: TweenSpec<Float>): TweenSpec<Float> {
    // TODO: Implementar verificação de reduced motion quando disponível
    // Por enquanto, retorna sempre o spec padrão
    return defaultSpec
}

/**
 * Tipos de transição suportados
 */
enum class ChopCutTransition {
    FADE,
    SLIDE_HORIZONTAL,
    SLIDE_VERTICAL,
    SCALE,
    NONE
}
