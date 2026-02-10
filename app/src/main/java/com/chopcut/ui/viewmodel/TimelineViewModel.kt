package com.chopcut.ui.viewmodel



import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.WaveFormGenerator
import com.chopcut.data.model.EditOperation
import com.chopcut.ui.components.TrimPosition
import com.chopcut.ui.components.TimelineEditorState
import com.chopcut.ui.components.WaveformData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TimelineEditorState())
    val state: StateFlow<TimelineEditorState> = _state.asStateFlow()
    
    // Extractor
    private val audioDataExtractor = AudioDataExtractor(application)

    fun loadWaveform(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isWaveformLoading = true, waveformError = null) }
            try {
                Timber.d("Timeline: Extracting waveform for $uri")
                val rawData = audioDataExtractor.extractRawPcmData(uri)
                if (rawData != null && rawData.pcmSamples.isNotEmpty()) {
                    val bars = 200 // Number of bars to render
                    val amplitudes = WaveFormGenerator.generateWaveform(rawData.pcmSamples, bars)
                    
                    val waveform = WaveformData(
                        amplitudes = amplitudes,
                        sampleRate = rawData.sampleRate,
                        durationMs = rawData.durationMs
                    )
                    
                    withContext(Dispatchers.Main) { 
                        _state.update { 
                            it.copy(
                                waveformData = waveform,
                                isWaveformLoading = false
                            ) 
                        }
                    }
                    Timber.d("Timeline: Waveform extracted (${amplitudes.size} points)")
                } else {
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(
                                isWaveformLoading = false,
                                waveformError = "Sem dados de áudio"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Timeline: Failed to extract waveform")
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isWaveformLoading = false,
                            waveformError = "Erro ao carregar áudio: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun addPosition(pos: Long) {
        val current = _state.value.trimPosition
        if (pos in current.positions) {
            Timber.d("Timeline: posição $pos já existe na lista")
            return
        }
        val newPosition = current.withPosition(pos)
        Timber.d("Timeline: addPosition $pos -> ${newPosition.positions}")
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

    fun setWaveformData(data: com.chopcut.ui.components.WaveformData) {
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
        _state.value = TimelineEditorState()
        Timber.d("Timeline: estado limpo")
    }

    fun getCompleteRanges(): List<Pair<Long, Long>> {
        return _state.value.trimPosition.completeRanges
    }

    fun loadEdits(edits: List<EditOperation>) {
        val trimEdits = edits.filterIsInstance<EditOperation.Trim>()
        val positions = trimEdits.flatMap { listOf(it.startTime, it.endTime) }
        val newTrimPosition = TrimPosition(positions = positions)
        _state.update {
            it.copy(trimPosition = newTrimPosition)
        }
        Timber.d("Timeline: Loaded ${trimEdits.size} edits -> $positions")
    }
}
