package com.chopcut.ui.state

/**
 * Modo de scroll da timeline.
 *
 * A timeline opera em exatamente um destes 3 modos por vez,
 * derivado exclusivamente de [isPlaying] e [isScrubbing] do EditorState.
 *
 * NUNCA adicione um 4to modo sem atualizar TODOS os [when] exaustivos.
 */
enum class TimelineScrollMode {
    /** Player parado, sem gesto do usuário. Posição = ExoPlayer.currentPosition. */
    IDLE,
    /** Player rodando. Scroll automático interpolado via VSYNC. */
    AUTO,
    /** Usuário arrastando. Player é ignorado, posição é do gesto. */
    MANUAL
}
