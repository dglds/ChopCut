package com.chopcut.ui.screen.debug

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.WaveformQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel para o componente AudioWaveForms
 *
 * Gerencia a extração de dados de áudio e o estado da UI
 *
 * @param application Context da aplicação
 */
class AudioWaveFormsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<AudioWaveFormsUiState>(AudioWaveFormsUiState.Idle)
    val uiState: StateFlow<AudioWaveFormsUiState> = _uiState.asStateFlow()

    private val audioDataExtractor = AudioDataExtractor(application)

    /**
     * Carrega o waveform do vídeo
     *
     * @param uri URI do vídeo
     * @param targetBarCount Número desejado de barras (padrão: 200)
     */
    fun loadWaveform(
        uri: Uri,
        targetBarCount: Int = 200
    ) {
        viewModelScope.launch {
            _uiState.value = AudioWaveFormsUiState.Loading

            try {
                Timber.d("Loading waveform with targetBarCount=$targetBarCount")

                val rawData = audioDataExtractor.extractRawPcmData(
                    uri = uri,
                    targetBarCount = targetBarCount
                )

                if (rawData.pcmSamples.isEmpty()) {
                    Timber.w("No audio data found")
                    _uiState.value = AudioWaveFormsUiState.Error("Nenhum dado de áudio encontrado")
                } else {
                    Timber.d("Waveform loaded: ${rawData.pcmSamples.size} bars, duration=${rawData.durationMs}ms")
                    _uiState.value = AudioWaveFormsUiState.Ready(
                        amplitudes = rawData.pcmSamples.toList(),
                        durationMs = rawData.durationMs,
                        sampleRate = rawData.sampleRate,
                        barCount = rawData.pcmSamples.size
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading waveform")
                _uiState.value = AudioWaveFormsUiState.Error(e.message ?: "Erro ao carregar áudio")
            }
        }
    }

    /**
     * Carrega o waveform usando uma qualidade predefinida
     *
     * @param uri URI do vídeo
     * @param quality Qualidade predefinida
     * @param screenWidthDp Largura da tela em dp (para cálculo)
     */
    fun loadWaveform(
        uri: Uri,
        quality: WaveformQuality,
        screenWidthDp: Float = 400f
    ) {
        // Para usar qualidade, precisamos primeiro obter a duração
        // Por simplicidade, usamos um cálculo direto
        val barCount = quality.calculateBarCount(
            durationMs = 60000, // Estimativa inicial de 1 minuto
            screenWidthDp = screenWidthDp
        )
        loadWaveform(uri, targetBarCount = barCount)
    }

    /**
     * Reinicia o estado para Idle
     */
    fun reset() {
        _uiState.value = AudioWaveFormsUiState.Idle
    }
}

/**
 * Estados da UI do AudioWaveForms
 */
sealed class AudioWaveFormsUiState {
    /** Estado inicial */
    data object Idle : AudioWaveFormsUiState()

    /** Carregando dados de áudio */
    data object Loading : AudioWaveFormsUiState()

    /** Dados prontos para renderização */
    data class Ready(
        val amplitudes: List<Float>,
        val durationMs: Long,
        val sampleRate: Int,
        val barCount: Int
    ) : AudioWaveFormsUiState()

    /** Erro ao carregar */
    data class Error(val message: String) : AudioWaveFormsUiState()
}
