package com.chopcut.ui.screen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.WaveFormGenerator
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

    private val waveformCache = com.chopcut.data.audio.WaveformCache()
    private val audioDataExtractor = AudioDataExtractor(application, waveformCache)

    override fun onCleared() {
        super.onCleared()
        waveformCache.clear()
        Timber.d("TrimViewModel CLEARED!")
    }

    fun loadWaveform(uri: Uri) {
        Timber.d("TrimViewModel loadWaveform called")
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isWaveformLoading = true, waveformError = null) }
            try {
                val rawData = audioDataExtractor.extractRawPcmData(uri)
                val amplitudes = WaveFormGenerator.generateWaveform(
                    pcmSamples = rawData.pcmSamples,
                    barCount = 100
                )
                val waveformData = WaveformData(
                    amplitudes = amplitudes,
                    sampleRate = rawData.sampleRate,
                    durationMs = rawData.durationMs
                )
                _state.update { it.copy(waveformData = waveformData, isWaveformLoading = false) }
                Timber.d("Waveform loaded: ${amplitudes.size} bars")
            } catch (e: Exception) {
                Timber.e(e, "TrimViewModel: Failed to extract waveform")
                _state.update { it.copy(waveformData = WaveformData.empty(), isWaveformLoading = false, waveformError = e.message) }
            }
        }
    }

    fun addPosition(pos: Long) {
        val current = _state.value.trimPosition
        if (pos in current.positions) {
            Timber.d("TrimViewModel: posição $pos já existe na lista")
            return
        }
        val newPosition = current.withPosition(pos)
        Timber.d("TrimViewModel: addPosition $pos -> ${newPosition.positions}")
        _state.update {
            it.copy(trimPosition = newPosition)
        }
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
        Timber.d("TrimViewModel: estado limpo")
    }

    fun getCompleteRanges(): List<Pair<Long, Long>> {
        return _state.value.trimPosition.completeRanges
    }
}
