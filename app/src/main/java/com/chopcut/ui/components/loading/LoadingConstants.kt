package com.chopcut.ui.components.loading

/**
 * Constantes para configuração do LoadingOverlay e suas animações
 */
object LoadingConstants {

    // Duração do loading - ajustado para renderizar thumbs existentes
    const val MIN_LOADING_DURATION_MS = 2_000L    // Mínimo 2 segundos (mostrar thumbs extraídas)
    const val MAX_LOADING_DURATION_MS = 5_000L    // Máximo 5 segundos (foco em renderização)
    const val TARGET_DURATION_MS = 3_500L         // Duração otimista para progressão

    // Progresso mínimo de thumbnails
    const val MINIMUM_THUMBNAIL_PROGRESS = 20f     // 20% das thumbnails (foco em renderizar o que existe)

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
