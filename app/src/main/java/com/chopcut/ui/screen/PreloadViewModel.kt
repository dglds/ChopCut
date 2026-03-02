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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    
    // ========== JOBS ==========
    
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
        preloadJob?.cancel()
        thumbnailJob?.cancel()
        
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
                
                // 1. ThumbnailViewModel carrega strips
                Timber.d("Step 1: Starting thumbnail preload...")
                thumbnailVM.preload(uri, stripsToPreload)
                
                // Observar progresso de thumbnails
                thumbnailJob = launch {
                    thumbnailVM.thumbnailProgress.collect { progress ->
                        val percent = (progress * 100).toInt()
                        _uiState.value = PreloadUiState.Loading(progress = PreloadProgress(
                            stage = ExtractionStage.ExtractingThumbnails,
                            audioPercent = 0,
                            thumbnailPercent = percent,
                            currentSegment = 0,
                            totalSegments = stripsToPreload,
                            thumbnailsExtracted = thumbnailVM.strips.value.size,
                            thumbnailsTotal = stripsToPreload
                        ))
                    }
                }
                
                // Aguardar thumbnails carregarem
                while (thumbnailVM.uiState.value !is ThumbnailViewModel.ThumbnailUiState.Ready) {
                    kotlinx.coroutines.delay(100)
                }
                
                Timber.d("Step 2: Thumbnails loaded")
                
                // 2. AudioViewModel carrega waveform
                Timber.d("Step 3: Starting audio waveform loading...")
                audioVM.loadWaveform(uri, targetBarCount = calculateTargetBarCount(durationMs = 0))
                
                // Aguardar áudio carregar
                while (audioVM.uiState.value !is AudioViewModel.AudioUiState.Ready) {
                    kotlinx.coroutines.delay(100)
                }
                
                Timber.d("Step 4: Audio waveform loaded")
                
                // 3. Marcar como Ready (criar PreloadedData com dados das ViewModels)
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
                        hasAudio = audioVM.amplitudes.value.size > 0,
                        sizeBytes = 0
                    ),
                    audioAmplitudes = audioVM.amplitudes.value,
                    preloadedStrips = thumbnailVM.strips.value,
                    totalSegments = thumbnailVM.strips.value.size,
                    preloadPercentage = 1f
                )
                _uiState.value = PreloadUiState.Ready(preloadedData)
                
                Timber.d("=== PreloadViewModel.startPreload COMPLETED ===")
                
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
