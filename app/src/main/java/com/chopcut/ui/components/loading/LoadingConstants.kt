package com.chopcut.ui.components.loading

/**
 * Constantes para configuração do LoadingOverlay e suas animações
 */
object LoadingConstants {

    // Duração do loading - ajustado para melhor UX
    const val MIN_LOADING_DURATION_MS = 3_000L    // Mínimo 3 segundos (reduzido de 5s)
    const val MAX_LOADING_DURATION_MS = 8_000L    // Máximo 8 segundos (reduzido de 10s)
    const val TARGET_DURATION_MS = 5_500L         // Duração otimista para progressão

    // Progresso mínimo de thumbnails
    const val MINIMUM_THUMBNAIL_PROGRESS = 30f     // 30% das thumbnails (reduzido de 50%)

    // Timings de transição
    const val CROSS_FADE_START_DELAY_MS = 100L      // Delay reduzido para iniciar cross-fade

    // Animações - Overlay
    const val OVERLAY_FADE_OUT_DURATION_MS = 500     // Fade out do overlay (reduzido de 700ms)
    const val OVERLAY_SCALE_OUT_TARGET = 0.95f        // Scale final do overlay

    // Animações - TrimScreen (sincronizado com overlay)
    const val TRIM_FADE_IN_DURATION_MS = 500          // Fade in da TrimScreen (reduzido de 700ms)
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
