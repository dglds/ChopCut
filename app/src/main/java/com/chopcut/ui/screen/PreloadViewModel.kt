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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

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
    // Regra: Liberar com 6 strips, ou com todas se o vídeo tiver menos de 6.
    val isReadyFlow: StateFlow<Boolean> = combine(
        thumbnailVM.strips,
        thumbnailVM.totalSegments
    ) { strips, total ->
        if (total == 0) return@combine false
        val threshold = minOf(total, 6)
        val ready = strips.size >= threshold
        Timber.v("isReadyFlow check: strips=${strips.size}, total=$total, threshold=$threshold, ready=$ready")
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
    fun startPreload(uri: Uri, stripsToPreload: Int = 6) {
        Timber.tag("PreloadViewModel").d("=== startPreload CALLED ===")
        Timber.tag("PreloadViewModel").d("uri: $uri")
        Timber.tag("PreloadViewModel").d("stripsToPreload: $stripsToPreload")
        Timber.tag("PreloadViewModel").d("activeUri: $activeUri")
        Timber.tag("PreloadViewModel").d("currentState: ${_uiState.value}")
        Timber.tag("PreloadViewModel").d("strips loaded: ${thumbnailVM.strips.value.size}")
        Timber.tag("PreloadViewModel").d("totalSegments: ${thumbnailVM.totalSegments.value}")
        
        Timber.d("=== PreloadViewModel.startPreload CALLED ===")
        Timber.d("uri: $uri")
        Timber.d("stripsToPreload: $stripsToPreload")
        Timber.d("activeUri: $activeUri")
        Timber.d("currentState: ${_uiState.value}")
        Timber.d("strips loaded: ${thumbnailVM.strips.value.size}")
        Timber.d("totalSegments: ${thumbnailVM.totalSegments.value}")
        
        if (activeUri == uri && _uiState.value is PreloadUiState.Ready) {
            Timber.d("Preload já está pronto para $uri, pulando restart")
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
                
                Timber.d("=== PreloadViewModel.startPreload STARTED ===")
                Timber.d("URI: $uri, strips to preload: $stripsToPreload")
                
                // 1. Iniciar ambos em paralelo
                Timber.d("Step 1: Starting parallel preload (thumbnails + audio)...")
                
                // Job para thumbnails
                val thumbPreloadJob = launch {
                    thumbnailVM.preload(uri, stripsToPreload)
                    // Aguardar o estado Ready da ThumbnailViewModel (que agora sabe o total real)
                    while (thumbnailVM.uiState.value !is ThumbnailViewModel.ThumbnailUiState.Ready) {
                        kotlinx.coroutines.delay(100)
                    }
                    Timber.d("Parallel: ThumbnailViewModel is Ready")
                }

                /* 
                // Job para áudio (Desabilitado temporariamente por performance)
                val audioPreloadJob = launch {
                    audioVM.loadWaveform(uri, targetBarCount = calculateTargetBarCount(durationMs = 0))
                    Timber.d("Parallel: Audio waveform ready")
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
                                     thumbnailsTotal = stripsToPreload
                                 ))
                             } else currentState
                        }
                    }
                }

                // AGUARDAR: Liberamos o acesso assim que o thumbPreloadJob terminar (mínimo de 6 strips)
                // O áudio continuará em background se ainda estiver processando
                thumbPreloadJob.join()
                
                Timber.d("Step 2: Fast-track ready (Thumbnails OK). Audio status: ${audioVM.uiState.value}")
                
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
                
                Timber.d("=== PreloadViewModel.startPreload FAST-READY ===")
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Preload failed")
                    _uiState.value = PreloadUiState.Error(e.message ?: "Erro desconhecido")
                }
            }
        }
    }
    
    /**
     * Verifica se há dados suficientes carregados para permitir navegação.
     * 
     * @param requiredStrips Número mínimo de strips necessário (padrão: 6)
     * @return true se estiver pronto, false caso contrário
     */
    fun isPreloadReady(requiredStrips: Int = 6): Boolean {
        val thumbnailsReady = thumbnailVM.hasEnoughStrips(requiredStrips)
        val audioReady = audioVM.isReady()
        val result = thumbnailsReady && audioReady
        
        Timber.d("isPreloadReady check: requiredStrips=$requiredStrips, " +
                "thumbnailsReady=$thumbnailsReady, audioReady=$audioReady, result=$result")
        
        return result
    }
    
    /**
     * Cancela o pré-carregamento em andamento.
     */
    fun cancelPreload() {
        Timber.d("Cancelling preload")
        
        preloadJob?.cancel()
        preloadJob = null
        thumbnailJob?.cancel()
        thumbnailJob = null
    }
    
    /**
     * Limpa todo o estado de pré-carregamento.
     */
    fun clear() {
        Timber.d("Clearing PreloadViewModel")
        
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
