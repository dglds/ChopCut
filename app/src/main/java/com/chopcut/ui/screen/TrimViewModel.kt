package com.chopcut.ui.screen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.WaveFormGenerator
import com.chopcut.data.audio.WaveformQuality
import com.chopcut.ui.components.TrimPosition
import com.chopcut.ui.components.WaveformData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class TrimViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TrimEditorState())
    val state: StateFlow<TrimEditorState> = _state.asStateFlow()

    private var waveformQuality: WaveformQuality = WaveformQuality.Medium

    private val audioDataExtractor = AudioDataExtractor(application)
    
    override fun onCleared() {
        super.onCleared()
    }

    fun setWaveformQuality(quality: WaveformQuality) {
        waveformQuality = quality
        Timber.d("Waveform quality set to: ${quality.displayName}")
    }

    fun loadWaveform(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("TrimViewModel: loadWaveform START - uri=$uri")
            _state.update { it.copy(isWaveformLoading = true, waveformError = null) }
            try {
                // Usar targetBarCount baseado na qualidade atual
                val barCount = waveformQuality.calculateBarCount(
                    durationMs = 0,  // Será obtido do rawData
                    screenWidthDp = 400f
                )
                Timber.d("TrimViewModel: calculated barCount=$barCount for quality=${waveformQuality.displayName}")

                val rawData = audioDataExtractor.extractRawPcmData(uri, targetBarCount = barCount)
                Timber.d("TrimViewModel: rawData received - samples=${rawData.pcmSamples.size}, duration=${rawData.durationMs}ms, sampleRate=${rawData.sampleRate}")

                val threshold = 0.05f
                val silenceHeight: Float? = null  // Dinâmico - calculado a partir das samples mais baixas

                val amplitudes = WaveFormGenerator.generateWaveform(
                    pcmSamples = rawData.pcmSamples,
                    durationMs = rawData.durationMs,
                    quality = waveformQuality,
                    screenWidthDp = 400f,
                    threshold = threshold,
                    silenceHeight = silenceHeight
                )
                val waveformData = WaveformData(
                    amplitudes = amplitudes,
                    sampleRate = rawData.sampleRate,
                    durationMs = rawData.durationMs
                )
                _state.update { it.copy(waveformData = waveformData, isWaveformLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to extract waveform")
                _state.update { it.copy(waveformData = WaveformData.empty(), isWaveformLoading = false, waveformError = e.message) }
            }
        }
    }

    fun loadAudioWaveforms(uri: Uri, targetBarCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("TrimViewModel: loadAudioWaveforms START - uri=$uri, targetBarCount=$targetBarCount")
            _state.update { it.copy(isAudioWaveformsLoading = true, audioWaveformsAmplitudes = emptyList()) }
            try {
                val rawData = audioDataExtractor.extractRawPcmData(uri, targetBarCount = targetBarCount)
                Timber.d("TrimViewModel: AudioWaveforms rawData received - samples=${rawData.pcmSamples.size}, duration=${rawData.durationMs}ms")

                // Armazenar as amplitudes diretamente (já processadas pelo AudioDataExtractor)
                val amplitudesList = rawData.pcmSamples.toList()
                Timber.d("TrimViewModel: Converted to List - ${amplitudesList.size} amplitudes")
                _state.update { it.copy(
                    audioWaveformsAmplitudes = amplitudesList,
                    isAudioWaveformsLoading = false
                ) }
                Timber.d("TrimViewModel: AudioWaveforms loaded - state updated with ${amplitudesList.size} bars")
            } catch (e: Exception) {
                Timber.e(e, "Failed to extract audio waveforms")
                _state.update { it.copy(audioWaveformsAmplitudes = emptyList(), isAudioWaveformsLoading = false) }
            }
        }
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
    }

    fun setVideoDuration(duration: Long) {
        _state.update { it.copy(videoDurationMs = duration) }
    }

    fun setWaveformData(data: WaveformData) {
        _state.update { it.copy(waveformData = data) }
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
}
