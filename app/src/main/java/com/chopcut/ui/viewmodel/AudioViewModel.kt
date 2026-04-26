package com.chopcut.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.WaveFormGenerator
import com.chopcut.data.audio.WaveformQuality
import com.chopcut.ui.components.waveform.WaveformData
import com.chopcut.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel especializada para gerenciar áudio e waveform.
 * 
 * Responsabilidades:
 * - Carregar waveform de áudio
 * - Gerenciar amplitudes de áudio
 * - Reportar estado de carregamento
 * - Calcular número de barras de waveform baseado na qualidade
 * 
 * Escopo: Activity (compartilhada entre HomeScreen e EditorScreen)
 */
class AudioViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    // ========== ESTADO ==========
    
    private val _waveform = MutableStateFlow<WaveformData?>(null)
    val waveform: StateFlow<WaveformData?> = _waveform.asStateFlow()
    
    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()
    
    private val _uiState = MutableStateFlow<AudioUiState>(AudioUiState.Idle)
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()
    
    // ========== DEPENDÊNCIAS ==========
    
    private val audioDataExtractor = AudioDataExtractor(application)
    private var waveformQuality: WaveformQuality = WaveformQuality.Medium
    private var activeUri: Uri? = null

    /**
     * Carrega a waveform de áudio para um vídeo.
     * 
     * @param uri URI do vídeo
     * @param targetBarCount Número de barras de waveform (opcional)
     * @param force Force reload ignoring cache
     */
    fun loadWaveform(uri: Uri, targetBarCount: Int? = null, force: Boolean = false) {
        if (activeUri != null && activeUri != uri) {
            clear()
        }
        
        if (!force && activeUri == uri && _uiState.value is AudioUiState.Ready) {
            return
        }
        
        activeUri = uri
        viewModelScope.launch(DispatcherProvider.io) {
            try {
                _uiState.value = AudioUiState.Loading
                
                // OTIMIZAÇÃO: Densidade dinâmica (20 barras/seg) via extractor
                val barCount = targetBarCount ?: -1
                
                // Extrair dados brutos de áudio
                val rawData = audioDataExtractor.extractRawPcmData(uri, targetBarCount = barCount)
                
                timber.log.Timber.d("AudioViewModel: PCM extraído com ${rawData.pcmSamples.size} pontos para $uri")
                
                // Gerar waveform
                val amplitudesList = WaveFormGenerator.generateWaveform(
                    pcmSamples = rawData.pcmSamples,
                    durationMs = rawData.durationMs,
                    quality = waveformQuality,
                    screenWidthDp = 400f,
                    threshold = 0.05f,
                    silenceHeight = null
                )
                
                timber.log.Timber.d("AudioViewModel: Waveform gerada com ${amplitudesList.size} amplitudes")
                
                // Criar WaveformData
                val waveformData = WaveformData(
                    amplitudes = amplitudesList,
                    sampleRate = rawData.sampleRate,
                    durationMs = rawData.durationMs
                )
                
                // Atualizar estado
                _waveform.value = waveformData
                _amplitudes.value = amplitudesList
                _uiState.value = AudioUiState.Ready(amplitudesList.size)
                
                
            } catch (e: Exception) {
                _uiState.value = AudioUiState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }
    
    /**
     * Define a qualidade da waveform.
     * 
     * @param quality Qualidade da waveform
     */
    fun setWaveformQuality(quality: WaveformQuality) {
        waveformQuality = quality
    }
    
    /**
     * Verifica se o áudio está pronto.
     * 
     * @return true se áudio estiver carregado, false caso contrário
     */
    fun isReady(): Boolean {
        val ready = _uiState.value is AudioUiState.Ready
        return ready
    }
    
    /**
     * Limpa o estado da waveform.
     */
    fun clear() {
        _waveform.value = null
        _amplitudes.value = emptyList()
        _uiState.value = AudioUiState.Idle
        activeUri = null
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    /**
     * Calcula o número de barras de waveform baseado na qualidade.
     * 
     * @param durationMs Duração do vídeo em ms
     * @param screenWidthDp Largura da tela em dp
     * @return Número de barras
     */
    private fun calculateBarCount(durationMs: Long, screenWidthDp: Float): Int {
        return waveformQuality.calculateBarCount(durationMs, screenWidthDp)
    }
    
    // ========== CLASSES DE ESTADO ==========
    
    sealed class AudioUiState {
        object Idle : AudioUiState()
        object Loading : AudioUiState()
        data class Ready(val barCount: Int) : AudioUiState()
        data class Error(val message: String) : AudioUiState()
    }
    
    // ========== FACTORY ==========
    
    class AudioViewModelFactory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AudioViewModel::class.java)) {
                return AudioViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
        }
    }
}
