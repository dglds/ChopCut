package com.chopcut.ui.components.loading

/**
 * Constantes para configuração do LoadingOverlay e suas animações
 */
object LoadingConstants {

    // Duração do loading
    const val MIN_LOADING_DURATION_MS = 5_000L    // Mínimo 5 segundos
    const val MAX_LOADING_DURATION_MS = 10_000L   // Máximo 10 segundos
    const val TARGET_DURATION_MS = 7_000L         // Duração otimista para progressão da barra

    // Progresso mínimo de thumbnails
    const val MINIMUM_THUMBNAIL_PROGRESS = 50f    // 50% das thumbnails

    // Timings de transição
    const val PROGRESS_BAR_COMPLETE_DELAY_MS = 400L    // Aguardar barra chegar a 100%
    const val CROSS_FADE_START_DELAY_MS = 200L         // Delay para iniciar cross-fade

    // Animações - Overlay
    const val OVERLAY_FADE_OUT_DURATION_MS = 700       // Fade out do overlay
    const val OVERLAY_SCALE_OUT_TARGET = 0.97f         // Scale final do overlay

    // Animações - TrimScreen
    const val TRIM_FADE_IN_DURATION_MS = 600           // Fade in da TrimScreen
    const val TRIM_SLIDE_IN_FRACTION = 20              // Slide de 5% (1/20)

    // Animações - Barra de progresso
    const val PROGRESS_BAR_ANIMATION_NORMAL_MS = 500   // Animação normal
    const val PROGRESS_BAR_ANIMATION_FINAL_MS = 300    // Animação quando completando
    const val PROGRESS_BAR_MAX_VALUE = 0.95f           // Máximo 95% até confirmar

    // Intervalo de verificação
    const val LOADING_CHECK_INTERVAL_MS = 100L         // Verificar a cada 100ms
}
