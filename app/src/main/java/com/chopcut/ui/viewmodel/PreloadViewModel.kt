package com.chopcut.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.util.DispatcherProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel coordenadora para preparação de vídeo.
 * 
 * Responsabilidades:
 * - Configurar ThumbnailViewModel com metadados do vídeo
 * - Liberação de acesso ao editor (apenas após obter metadados)
 * - Gerenciar estado geral (Loading/Ready/Error)
 * - Fornecer métodos para verificar se o vídeo está pronto
 * 
 * Escopo: Activity (compartilhada entre HomeScreen e TrimScreen)
 * 
 * Estratégia On-Demand:
 * - Thumbnails são carregadas apenas quando o usuário rola a timeline
 * - Preload está DESATIVADO para maximizar performance de abertura
 * - ThumbnailViewModel.handleOnDemand() carrega strips conforme necessário
 * - AudioViewModel não é usado atualmente (áudio carregado sob demanda no TrimScreen)
 */
class PreloadViewModel(
    application: Application,
    private val thumbnailVM: ThumbnailViewModel,
    private val audioVM: AudioViewModel
) : AndroidViewModel(application) {
    
    // ========== ESTADO ==========

    private val _uiState = MutableStateFlow<PreloadUiState>(PreloadUiState.Idle)
    val uiState: StateFlow<PreloadUiState> = _uiState.asStateFlow()

    // StateFlow reativo para isReady — libera assim que o número de segmentos é conhecido
    // (logo após obter os metadados do vídeo, antes de qualquer extração)
    val isReadyFlow: StateFlow<Boolean> = thumbnailVM.totalSegments
        .map { total -> total > 0 }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    // ========== JOBS ==========
    
    private var activeUri: Uri? = null
    private var preloadJob: Job? = null
    private var thumbnailJob: Job? = null
    
    // ========== DEPENDÊNCIAS ==========
    
    private val audioDataExtractor = AudioDataExtractor(application)
    
    // ========== MÉTODOS PÚBLICOS ==========
    
    /**
     * Inicia preparação de vídeo para o editor.
     * 
     * Estratégia On-Demand:
     * - Apenas obtém metadados do vídeo (duração, dimensões, segmentos)
     * - Configura ThumbnailViewModel para carregamento on-demand
     * - Thumbnails são carregadas apenas quando o usuário rola a timeline
     * - Áudio não é pré-carregado (carregado sob demanda no TrimScreen)
     * 
     * Benefícios:
     * - Abertura do editor é quase instantânea
     * - Menor uso de memória inicial
     * - Thumbnails carregadas apenas conforme necessário
     * 
     * @param uri URI do vídeo
     */
    fun startPreload(uri: Uri) {
        
        if (activeUri != null && activeUri != uri) {
            clear()
        }
        
        if (activeUri == uri && _uiState.value is PreloadUiState.Ready) {
            return
        }

        preloadJob?.cancel()
        thumbnailJob?.cancel()
        activeUri = uri
        
        preloadJob = viewModelScope.launch(DispatcherProvider.io) {
            try {
                _uiState.value = PreloadUiState.Loading(progress = PreloadProgress(
                    stage = ExtractionStage.Starting,
                    audioPercent = 0,
                    thumbnailPercent = 0,
                    currentSegment = 0,
                    totalSegments = 0,
                    thumbnailsExtracted = 0,
                    thumbnailsTotal = 0
                ))
                
                // Configurar ThumbnailViewModel (obter metadados, carregar strips on-demand)
                val thumbnailSetupJob = launch {
                    thumbnailVM.preload(uri)
                    thumbnailVM.uiState.first { it is ThumbnailViewModel.ThumbnailUiState.Ready }
                }

                /* 
                // Áudio não é pré-carregado (on-demand no TrimScreen)
                // Isso maximiza performance de abertura do editor
                */

                // Observar progresso de thumbnails para UI
                thumbnailJob = launch {
                    thumbnailVM.thumbnailProgress.collect { progress ->
                        val percent = (progress * 100).toInt()
                        _uiState.update { currentState ->
                             if (currentState is PreloadUiState.Loading) {
                                 currentState.copy(progress = currentState.progress.copy(
                                     stage = ExtractionStage.ExtractingThumbnails,
                                     thumbnailPercent = percent,
                                     thumbnailsExtracted = thumbnailVM.strips.value.size,
                                     thumbnailsTotal = thumbnailVM.totalSegments.value
                                 ))
                             } else currentState
                        }
                    }
                }

                // AGUARDAR: Liberamos o acesso assim que o thumbnailSetupJob terminar (apenas metadados)
                // Thumbnails são carregadas on-demand quando o usuário rola a timeline
                thumbnailSetupJob.join()
                
                // Marcar como Ready (apenas metadados, strips vazias para on-demand)
                val preloadedData = PreloadedData(
                    videoInfo = com.chopcut.data.model.VideoInfo(
                        uri = uri,
                        fileName = "video.mp4",
                        mimeType = "video/mp4",
                        durationUs = 0,
                        width = 0,
                        height = 0,
                        rotation = 0,
                        bitrate = 0,
                        frameRate = 30,
                        videoCodec = null,
                        audioCodec = null,
                        hasAudio = true, // Assumimos que tem áudio para não bloquear
                        sizeBytes = 0
                    ),
                    audioAmplitudes = emptyList(),
                    preloadedStrips = emptyMap(),
                    totalSegments = thumbnailVM.totalSegments.value,
                    preloadPercentage = 1f
                )
                _uiState.value = PreloadUiState.Ready(preloadedData)
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _uiState.value = PreloadUiState.Error(e.message ?: "Erro desconhecido")
                }
            }
        }
    }
    
    /**
     * Cancela a preparação do vídeo em andamento.
     */
    fun cancelPreload() {
        
        preloadJob?.cancel()
        preloadJob = null
        thumbnailJob?.cancel()
        thumbnailJob = null
    }
    
    /**
     * Limpa todo o estado de preparação do vídeo.
     */
    fun clear() {
        
        cancelPreload()
        activeUri = null
        _uiState.value = PreloadUiState.Idle
        thumbnailVM.clear()
        audioVM.clear()
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    /**
     * Calcula o número de barras de waveform.
     */
    private fun calculateTargetBarCount(durationMs: Long): Int {
        return when {
            durationMs < 60000 -> 100
            durationMs < 300000 -> 300
            else -> 600
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        clear()
    }
    
    // ========== CLASSES DE ESTADO ==========
    
    // PreloadUiState já existe em PreloadUiState.kt
    // PreloadProgress já existe em PreloadUiState.kt
    // ExtractionStage já existe em PreloadUiState.kt
    // PreloadedData pode ser mantida para compatibilidade ou removida
    
    // ========== FACTORY ==========
    
    class PreloadViewModelFactory(
        private val application: Application,
        private val thumbnailVM: ThumbnailViewModel,
        private val audioVM: AudioViewModel
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PreloadViewModel::class.java)) {
                return PreloadViewModel(application, thumbnailVM, audioVM) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
        }
    }
}
