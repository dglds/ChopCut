package com.chopcut.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.WaveformExtractor
import com.chopcut.data.audio.WaveFormGenerator
import com.chopcut.data.audio.WaveformCache
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
    
    private val _amplitudes = MutableStateFlow<FloatArray>(floatArrayOf())
    val amplitudes: StateFlow<FloatArray> = _amplitudes.asStateFlow()
    
    private val _uiState = MutableStateFlow<AudioUiState>(AudioUiState.Idle)
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()
    
    // ========== DEPENDÊNCIAS ==========
    
    private val waveformExtractor = WaveformExtractor(application)
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
        timber.log.Timber.d("AudioViewModel: loadWaveform desativada temporariamente para teste de stress")
        _uiState.value = AudioUiState.Ready(0)
        _amplitudes.value = floatArrayOf()
        _waveform.value = WaveformData(floatArrayOf(), 0, 0)
        return
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
        _amplitudes.value = floatArrayOf()
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
