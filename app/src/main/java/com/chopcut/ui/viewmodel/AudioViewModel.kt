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
        timber.log.Timber.d("AudioViewModel: loadWaveform called for $uri")
        if (activeUri != null && activeUri != uri) {
            clear()
        }

        if (!force && activeUri == uri && _uiState.value is AudioUiState.Ready) {
            timber.log.Timber.d("AudioViewModel: already loaded, skipping")
            return
        }

        activeUri = uri
        viewModelScope.launch(DispatcherProvider.io) {
            try {
                _uiState.value = AudioUiState.Loading
                timber.log.Timber.d("AudioViewModel: starting extraction")
                
                // OTIMIZAÇÃO: Densidade dinâmica (20 barras/seg) via extractor
                val barCount = targetBarCount ?: -1

                // RESTRIÇÃO ARQUITETURAL CRÍTICA: O cache deve permanecer OBRIGATORIAMENTE desativado (cacheEnabled = false).
                // Requisito oficial do projeto para evitar "falsos positivos" durante os testes e perfilamento
                // da performance crua de extração da mídia. NÃO alterar para true.
                val cacheEnabled = false
                val cacheFile = if (cacheEnabled) WaveformCache.fileFor(getApplication(), uri) else null
                val cached = cacheFile?.let { WaveformCache.read(it) }
                val rawData = if (cached != null) {
                    timber.log.Timber.d("AudioViewModel: cache HIT (${cached.pcmSamples.size} pontos) para $uri")
                    cached
                } else {
                    val t0 = System.currentTimeMillis()
                    timber.log.Timber.d("AudioViewModel: calling waveformExtractor.extractRawPcmData")
                    val extracted = waveformExtractor.extractRawPcmData(uri, targetBarCount = barCount)
                    val elapsed = System.currentTimeMillis() - t0
                    timber.log.Timber.d("AudioViewModel: extraction returned ${extracted.pcmSamples.size} samples")
                    timber.log.Timber.d(
                        "AudioViewModel: extração concluída em %dms — %d pontos, %dms de áudio (%.1fx realtime)",
                        elapsed,
                        extracted.pcmSamples.size,
                        extracted.durationMs,
                        if (elapsed > 0) extracted.durationMs.toFloat() / elapsed else 0f
                    )
                    if (cacheEnabled && extracted.pcmSamples.isNotEmpty() && cacheFile != null) {
                        WaveformCache.write(cacheFile, extracted)
                    }
                    extracted
                }
                
                // Gerar waveform
                val amplitudesList = WaveFormGenerator.generateWaveform(
                    pcmSamples = rawData.pcmSamples,
                    durationMs = rawData.durationMs,
                    quality = waveformQuality,
                    screenWidthDp = 400f,
                    threshold = 0.05f,
                    silenceHeight = null
                )
                timber.log.Timber.d("AudioViewModel: WaveformGenerator returned ${amplitudesList.size} amplitudes")

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
                timber.log.Timber.d("AudioViewModel: state updated with ${amplitudesList.size} amplitudes, _amplitudes.value.size=${_amplitudes.value.size}")
                
                
            } catch (e: Exception) {
                timber.log.Timber.e("AudioViewModel: error loading waveform - ${e.message}")
                e.printStackTrace()
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
