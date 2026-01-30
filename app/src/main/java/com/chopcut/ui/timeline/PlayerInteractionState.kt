package com.chopcut.ui.timeline

/**
 * Estados de interação do player e timeline (CropSnap-style)
 *
 * Estados principais que controlam o comportamento da timeline
 * e sua interação com o player de vídeo.
 *
 * Nota: Este arquivo foi renomeado de TimelineState.kt para evitar
 * conflito com o modelo de dados TimelineState em model/TimelineModels.kt
 */
sealed class PlayerInteractionState {
    /** Vídeo tocando, timeline auto-scrolling */
    object Playing : PlayerInteractionState()

    /** Usuário arrastando a timeline */
    object Scrubbing : PlayerInteractionState()

    /** Seek settling após scroll */
    object SeekSettling : PlayerInteractionState()

    /** Timeline idle (vídeo pausado) */
    object Idle : PlayerInteractionState()

    override fun toString(): String = when (this) {
        is Playing -> "Playing"
        is Scrubbing -> "Scrubbing"
        is SeekSettling -> "SeekSettling"
        is Idle -> "Idle"
    }
}

/**
 * Subestados durante scrubbing para controle fino
 *
 * Permite um controle mais granular durante a interação do usuário
 * com a timeline, especialmente para throttle de seek.
 */
sealed class ScrubbingState {
    /** Nenhuma interação em andamento */
    object Idle : ScrubbingState()

    /** Usuário está arrastando ativamente */
    object Dragging : ScrubbingState()

    /** Seek está pendente (aguardando throttle) */
    object ThrottledSeekPending : ScrubbingState()

    /** Renderizando frame após seek */
    object RenderingFrame : ScrubbingState()

    override fun toString(): String = when (this) {
        is Idle -> "Idle"
        is Dragging -> "Dragging"
        is ThrottledSeekPending -> "ThrottledSeekPending"
        is RenderingFrame -> "RenderingFrame"
    }
}
