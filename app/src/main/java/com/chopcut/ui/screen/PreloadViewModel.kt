package com.chopcut.ui.screen

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel coordenadora para pré-carregamento de vídeo.
 * 
 * Responsabilidades:
 * - Orquestrar pré-carregamento de thumbnails e áudio
 * - Coordenar ThumbnailViewModel e AudioViewModel
 * - Gerenciar estado geral (Loading/Ready/Error)
 * - Fornecer métodos para verificar se o preload está pronto
 * 
 * Escopo: Activity (compartilhada entre HomeScreen e TrimScreen)
 * 
 * Esta ViewModel não realiza extração diretamente - ela delega para:
 * - ThumbnailViewModel: Carregamento de strips
 * - AudioViewModel: Carregamento de waveform
 */
class PreloadViewModel(
    application: Application,
    private val thumbnailVM: ThumbnailViewModel,
    private val audioVM: AudioViewModel
) : AndroidViewModel(application) {
    
    // ========== ESTADO ==========

    private val _uiState = MutableStateFlow<PreloadUiState>(PreloadUiState.Idle)
    val uiState: StateFlow<PreloadUiState> = _uiState.asStateFlow()

    // StateFlow reativo para isReady (Apenas thumbnails são críticas para entrar no editor)
    // Regra: Liberar com 50% das strips OU se estiver em cache.
    val isReadyFlow: StateFlow<Boolean> = combine(
        thumbnailVM.strips,
        thumbnailVM.totalSegments,
        thumbnailVM.isCached
    ) { strips, total, isCached ->
        if (total == 0) return@combine false
        
        // Se já está em cache, libera automático!
        if (isCached) {
            return@combine true
        }

        // Critério: 50% das strips prontas
        // Para vídeos muito curtos, garantimos um mínimo de 1 ou o total se o total for pequeno
        val threshold = if (total <= 6) total else (total * 0.5).toInt().coerceAtLeast(6)
        val ready = strips.size >= threshold
        
        ready
    }.stateIn(
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
     * Inicia pré-carregamento de vídeo.
     * 
     * Orquestra ThumbnailViewModel e AudioViewModel para carregar
     * thumbnails e waveform em paralelo.
     * 
     * @param uri URI do vídeo
     * @param stripsToPreload Número de strips a carregar (padrão: 6)
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
                
                // Job para thumbnails
                val thumbPreloadJob = launch {
                    thumbnailVM.preload(uri)
                    thumbnailVM.uiState.first { it is ThumbnailViewModel.ThumbnailUiState.Ready }
                }

                /* 
                // Job para áudio (Desabilitado temporariamente por performance)
                val audioPreloadJob = launch {
                    audioVM.loadWaveform(uri, targetBarCount = calculateTargetBarCount(durationMs = 0))
                }
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

                // AGUARDAR: Liberamos o acesso assim que o thumbPreloadJob terminar (mínimo de 6 strips)
                // O áudio continuará em background se ainda estiver processando
                thumbPreloadJob.join()
                
                // 3. Marcar como Ready
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
                    audioAmplitudes = audioVM.amplitudes.value,
                    preloadedStrips = thumbnailVM.strips.value,
                    totalSegments = thumbnailVM.strips.value.size,
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
     * Cancela o pré-carregamento em andamento.
     */
    fun cancelPreload() {
        
        preloadJob?.cancel()
        preloadJob = null
        thumbnailJob?.cancel()
        thumbnailJob = null
    }
    
    /**
     * Limpa todo o estado de pré-carregamento.
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
