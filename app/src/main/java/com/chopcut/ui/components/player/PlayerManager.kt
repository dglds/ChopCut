package com.chopcut.ui.components.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encapsula a criação e gerenciamento do ExoPlayer.
 *
 * Expõe o estado do player como StateFlows reativos e métodos
 * de controle (play/pause/seek/retry/release).
 *
 * @param onDurationReady chamado quando o player resolve a duração do vídeo
 */
@OptIn(UnstableApi::class)
class PlayerManager(
    context: Context,
    videoUri: Uri,
    private val onDurationReady: (Long) -> Unit
) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(videoUri))
        prepare()
        repeatMode = Player.REPEAT_MODE_OFF
        playWhenReady = false
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _isSecurityError = MutableStateFlow(false)
    val isSecurityError: StateFlow<Boolean> = _isSecurityError.asStateFlow()

    val currentPosition: Long get() = exoPlayer.currentPosition

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                onDurationReady(duration)
                _playerError.value = null
                _isSecurityError.value = false
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            val isPermError = cause?.toString()?.contains("SecurityException") == true ||
                    cause?.cause?.toString()?.contains("SecurityException") == true

            _isSecurityError.value = isPermError
            _playerError.value = if (isPermError) {
                "Permissão do arquivo expirou. Toque em 'Re-Localizar' para corrigir."
            } else {
                "Erro ao reproduzir: ${error.message ?: "Desconhecido"}"
            }
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun retry() {
        _playerError.value = null
        _isSecurityError.value = false
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun release() {
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }
}
