package com.chopcut.ui.screen.debug

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.WaveformConfig
import com.chopcut.data.audio.WaveformPreset
import com.chopcut.ui.components.waveform.WaveformData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExtractionMetrics(
    val extractionTimeMs: Long,
    val barsGenerated: Int,
    val thresholdCalculated: Float,
    val samplesProcessed: Int,
    val samplesSkipped: Int
)

data class WaveformTestUiState(
    val isLoading: Boolean = false,
    val currentConfig: WaveformConfig = WaveformConfig.DEFAULT,
    val waveformData: WaveformData? = null,
    val baselineWaveformData: WaveformData? = null,
    val extractionMetrics: ExtractionMetrics? = null,
    val baselineMetrics: ExtractionMetrics? = null,
    val error: String? = null,
    val compareWithBaseline: Boolean = false
)

class WaveformTestViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(WaveformTestUiState())
    val state: StateFlow<WaveformTestUiState> = _state.asStateFlow()

    private val audioDataExtractor = AudioDataExtractor(application)
    
    private var baselineUri: Uri? = null
    private var baselineConfig: WaveformConfig? = null
    
    fun setBaseline(uri: Uri, config: WaveformConfig) {
        baselineUri = uri
        baselineConfig = config
    }
    
    fun setPreset(preset: WaveformPreset) {
        val newConfig = WaveformConfig.fromPreset(preset)
        _state.update { it.copy(currentConfig = newConfig) }
    }
    
    fun updateConfig(config: WaveformConfig) {
        _state.update { it.copy(currentConfig = config) }
    }
    
    fun setCompareWithBaseline(compare: Boolean) {
        _state.update { it.copy(compareWithBaseline = compare) }
    }
    
    suspend fun extractWithConfig(uri: Uri, config: WaveformConfig): Result<Pair<WaveformData, ExtractionMetrics>> {
        return withContext(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            
            try {
                val startTime = System.currentTimeMillis()
                
                val rawData = audioDataExtractor.extractRawPcmData(uri)
                
                val endTime = System.currentTimeMillis()
                val extractionTimeMs = endTime - startTime
                
                val waveformData = WaveformData(
                    amplitudes = rawData.pcmSamples.toList(),
                    sampleRate = rawData.sampleRate,
                    durationMs = rawData.durationMs
                )
                
                val metrics = ExtractionMetrics(
                    extractionTimeMs = extractionTimeMs,
                    barsGenerated = rawData.pcmSamples.size,
                    thresholdCalculated = 0f,
                    samplesProcessed = rawData.pcmSamples.size,
                    samplesSkipped = 0
                )
                
                Result.success(Pair(waveformData, metrics))
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
                Result.failure(e)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun generateBaseline(uri: Uri, config: WaveformConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            
            try {
                val result = extractWithConfig(uri, config)
                result.getOrNull()?.let { (waveform, metrics) ->
                    baselineUri = uri
                    baselineConfig = config
                    _state.update { 
                        it.copy(
                            baselineWaveformData = waveform,
                            baselineMetrics = metrics,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    fun regenerate(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            
            try {
                val config = _state.value.currentConfig
                val result = extractWithConfig(uri, config)
                result.getOrNull()?.let { (waveform, metrics) ->
                    _state.update { 
                        it.copy(
                            waveformData = waveform,
                            extractionMetrics = metrics,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
    
    fun reset() {
        _state.value = WaveformTestUiState()
        baselineUri = null
        baselineConfig = null
    }
}
