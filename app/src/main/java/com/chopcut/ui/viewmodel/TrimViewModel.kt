package com.chopcut.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.WaveformQuality
import com.chopcut.data.repository.VideoRepository
import com.chopcut.ui.components.trim.TrimPosition
import com.chopcut.ui.components.waveform.WaveformData
import com.chopcut.ui.components.player.PlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.collectLatest

/**
 * ViewModel para TrimScreen.
 *
 * Responsabilidades:
 * - Gerenciar estado do editor de trim (posições, duração, trim ranges)
 * - Carregar waveform de áudio
 * - Gerenciar posição atual do playhead
 * - Gerenciar Player de vídeo (ExoPlayer)
 *
 * NOTA: O pré-carregamento de thumbnails e áudio é gerenciado
 * pela PreloadViewModel (Activity-scoped), não por esta ViewModel.
 */
class TrimViewModel(
    application: Application,
    private val videoUri: Uri?,
    private val initialAudioAmplitudes: List<Float>? = null,
    private val initialPreloadedStrips: Map<Int, Bitmap>? = null
) : AndroidViewModel(application) {

    class TrimViewModelFactory(
        private val videoUri: Uri?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrimViewModel::class.java)) {
                @Suppress("DEPRECATION")
                val app = modelClass.classLoader?.let {
                    try {
                        java.lang.Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication")
                            .invoke(null) as? Application
                    } catch (e: Exception) {
                        null
                    }
                }

                if (app != null) {
                    return TrimViewModel(
                        application = app,
                        videoUri = videoUri,
                        initialAudioAmplitudes = null,
                        initialPreloadedStrips = null
                    ) as T
                }
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val _state = MutableStateFlow(TrimEditorState())
    val state: StateFlow<TrimEditorState> = _state.asStateFlow()

    private var waveformQuality: WaveformQuality = WaveformQuality.Medium
    private val videoRepository = VideoRepository(application)

    private var playerManager: PlayerManager? = null

    init {
        // Instantiate PlayerManager
        if (videoUri != null) {
            playerManager = PlayerManager(
                context = application,
                videoUri = videoUri,
                onDurationReady = { duration ->
                    _state.update { it.copy(videoDurationMs = duration) }
                }
            )

            _state.update { it.copy(exoPlayer = playerManager?.exoPlayer) }

            // Obter dimensões do vídeo para o Timeline
            viewModelScope.launch {
                videoRepository.getMetadata(videoUri)?.let { info ->
                    _state.update { it.copy(videoWidth = info.width, videoHeight = info.height) }
                }
            }

            // Observe player states
            viewModelScope.launch {
                playerManager?.isPlaying?.collectLatest { isPlaying ->
                    _state.update { it.copy(isPlaying = isPlaying) }
                }
            }
            viewModelScope.launch {
                playerManager?.playerError?.collectLatest { error ->
                    _state.update { it.copy(playerError = error) }
                }
            }
            viewModelScope.launch {
                playerManager?.isSecurityError?.collectLatest { isSecurityError ->
                    _state.update { it.copy(isSecurityError = isSecurityError) }
                }
            }
            viewModelScope.launch {
                playerManager?.currentPositionFlow?.collectLatest { position: Long ->
                    _state.update { it.copy(currentPosition = position) }
                }
            }
        }
    }

    fun setWaveformQuality(quality: WaveformQuality) {
        waveformQuality = quality
    }

    /**
     * Atualiza as amplitudes de áudio.
     * Usado para sincronizar dados do AudioViewModel.
     */
    fun updateAudioAmplitudes(amplitudes: List<Float>) {

        _state.update { it.copy(
            audioWaveformsAmplitudes = amplitudes
        ) }
    }

    fun addPosition(pos: Long) {
        val current = _state.value.trimPosition
        if (pos in current.positions) {
            return
        }
        _state.update { it.copy(trimPosition = current.withPosition(pos)) }
    }

    fun setCurrentPosition(pos: Long) {
        _state.update { it.copy(currentPosition = pos) }
        playerManager?.seekTo(pos)
    }

    fun setVideoDuration(duration: Long) {
        _state.update { it.copy(videoDurationMs = duration) }
    }

    fun setWaveformData(data: WaveformData) {
        _state.update { it.copy(waveformData = data) }
    }

    fun updateRange(rangeIndex: Int, newStartMs: Long, newEndMs: Long) {
        val current = _state.value.trimPosition
        _state.update { it.copy(trimPosition = current.updateRangeAt(rangeIndex, newStartMs, newEndMs)) }
    }

    fun removeRangeAt(pos: Long) {
        val current = _state.value.trimPosition
        if (current.isDraftMode) {
            val newPositions = current.positions.dropLast(1)
            _state.update { it.copy(trimPosition = current.copy(positions = newPositions)) }
        } else {
            val newTrim = current.removeRangeAt(pos)
            _state.update { it.copy(trimPosition = newTrim) }
        }
    }

    fun clear() {
        _state.value = TrimEditorState()
    }

    fun getCompleteRanges(): List<Pair<Long, Long>> {
        return _state.value.trimPosition.completeRanges
    }

    fun play() {
        playerManager?.play()
    }

    fun pause() {
        playerManager?.pause()
    }
    
    fun retryPlayer() {
        playerManager?.retry()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager?.release()
    }
}

