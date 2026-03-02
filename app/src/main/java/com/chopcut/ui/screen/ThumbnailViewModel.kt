package com.chopcut.ui.screen

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel especializada para gerenciar thumbnail strips.
 * 
 * Responsabilidades:
 * - Gerenciar cache de strips em memória
 * - Pré-carregar strips (preload)
 * - Carregar strips on-demand
 * - Reportar progresso de extração
 * - Gerenciar memória (eviction)
 * 
 * Escopo: Activity (compartilhada entre HomeScreen e TrimScreen)
 */
class ThumbnailViewModel(
    application: Application,
    private val videoRepository: VideoRepository
) : AndroidViewModel(application) {
    
    // ========== ESTADO ==========
    
    private val _strips = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val strips: StateFlow<Map<Int, Bitmap>> = _strips.asStateFlow()
    
    private val _thumbnailProgress = MutableStateFlow<Float>(0f)
    val thumbnailProgress: StateFlow<Float> = _thumbnailProgress.asStateFlow()
    
    private val _uiState = MutableStateFlow<ThumbnailUiState>(ThumbnailUiState.Idle)
    val uiState: StateFlow<ThumbnailUiState> = _uiState.asStateFlow()
    
    // ========== DEPENDÊNCIAS ==========
    
    private var stripManager: com.chopcut.data.thumbnail.ThumbnailStripManager? = null
    
    // ========== MÉTODOS PÚBLICOS ==========
    
    /**
     * Pré-carrega um número fixo de strips para um vídeo.
     * 
     * @param uri URI do vídeo
     * @param stripsToPreload Número de strips a carregar (padrão: 6)
     */
    fun preload(uri: Uri, stripsToPreload: Int = 6) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = ThumbnailUiState.Loading
                _thumbnailProgress.value = 0f
                
                Timber.d("=== ThumbnailViewModel.preload STARTED ===")
                Timber.d("URI: $uri, strips to preload: $stripsToPreload")
                
                // 1. Obter metadados do vídeo
                val videoInfo = videoRepository.getMetadata(uri)
                if (videoInfo == null) {
                    Timber.e("VideoInfo is null")
                    _uiState.value = ThumbnailUiState.Error("Metadados não disponíveis")
                    return@launch
                }
                
                // 2. Configurar stripManager
                val density = getApplication<Application>().resources.displayMetrics.density
                val thumbWidth = (60 * density).toInt().coerceAtLeast(1)
                val thumbHeight = (thumbWidth / videoInfo.aspectRatio).toInt().coerceAtLeast(1)
                val thumbsPerStrip = PreferencesManager(getApplication()).thumbsPerStrip
                
                stripManager = com.chopcut.data.thumbnail.ThumbnailStripManager(
                    getApplication(), thumbWidth, thumbHeight, thumbsPerStrip,
                    adaptiveStrips = false
                )
                
                val totalSegments = stripManager!!.getSegmentCount(videoInfo.durationMs)
                val effectiveStripsToPreload = minOf(stripsToPreload, totalSegments)
                
                Timber.d("Configuração: totalSegments=$totalSegments, toPreload=$effectiveStripsToPreload")
                
                // 3. Carregar strips com progresso em estágios (0%, 25%, 50%, 75%, 100%)
                val strips = extractThumbnailsWithProgress(
                    uri = uri,
                    targetSegments = effectiveStripsToPreload,
                    totalSegments = totalSegments,
                    durationMs = videoInfo.durationMs
                )
                
                // 4. Atualizar estado
                _strips.value = strips
                _thumbnailProgress.value = 1f
                _uiState.value = ThumbnailUiState.Ready(strips.size, totalSegments)
                
                Timber.d("=== ThumbnailViewModel.preload COMPLETED ===")
                Timber.d("Loaded: ${strips.size}/$totalSegments strips")
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Preload failed")
                    _uiState.value = ThumbnailUiState.Error(e.message ?: "Erro desconhecido")
                }
            }
        }
    }
    
    /**
     * Carrega uma strip específica on-demand.
     * 
     * @param uri URI do vídeo
     * @param segmentIndex Índice do segmento
     * @param durationMs Duração do vídeo em ms
     */
    fun loadStrip(uri: Uri, segmentIndex: Int, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_strips.value.containsKey(segmentIndex)) {
                Timber.d("Strip $segmentIndex já está em cache")
                return@launch
            }
            
            try {
                val strip = com.chopcut.data.thumbnail.ThumbnailCacheManager.getStrip(
                    uri = uri,
                    segmentIndex = segmentIndex,
                    durationMs = durationMs,
                    thumbWidth = stripManager?.thumbWidth ?: 60,
                    thumbHeight = stripManager?.thumbHeight ?: 40,
                    thumbsPerStrip = stripManager?.thumbsPerStrip ?: 10
                )
                
                if (strip != null) {
                    _strips.value = _strips.value.toMutableMap().apply {
                        put(segmentIndex, strip)
                    }
                    Timber.d("Strip $segmentIndex carregada")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load strip $segmentIndex")
            }
        }
    }
    
    /**
     * Carrega múltiplas strips em background.
     * 
     * @param uri URI do vídeo
     * @param segmentIndices Lista de índices de segmentos
     * @param durationMs Duração do vídeo em ms
     */
    fun loadStrips(uri: Uri, segmentIndices: List<Int>, durationMs: Long) {
        segmentIndices.forEach { segIdx ->
            loadStrip(uri, segIdx, durationMs)
        }
    }
    
    /**
     * Verifica se há strips suficientes carregadas.
     * 
     * @param requiredStrips Número mínimo de strips necessário
     * @return true se houver strips suficientes, false caso contrário
     */
    fun hasEnoughStrips(requiredStrips: Int): Boolean {
        val count = _strips.value.size
        val result = count >= requiredStrips
        
        Timber.d("hasEnoughStrips check: required=$requiredStrips, " +
                "available=$count, result=$result")
        
        return result
    }
    
    /**
     * Limpa todas as strips e estado.
     */
    fun clear() {
        Timber.d("Clearing ThumbnailViewModel")
        
        _strips.value.values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        
        _strips.value = emptyMap()
        _thumbnailProgress.value = 0f
        _uiState.value = ThumbnailUiState.Idle
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    /**
     * Extrai thumbnails com progresso em estágios (0%, 25%, 50%, 75%, 100%).
     * 
     * @param uri URI do vídeo
     * @param targetSegments Número de segmentos a carregar
     * @param totalSegments Número total de segmentos do vídeo
     * @param durationMs Duração do vídeo em ms
     * @return Map de segmentIndex para Bitmap
     */
    private suspend fun extractThumbnailsWithProgress(
        uri: Uri,
        targetSegments: Int,
        totalSegments: Int,
        durationMs: Long
    ): Map<Int, Bitmap> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val strips = mutableMapOf<Int, Bitmap>()
        val progressStages = listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)
        var lastReportedStage = -1
        
        Timber.d("Extraindo $targetSegments segmentos com progresso em estágios")
        
        for (segIdx in 0 until targetSegments) {
            val strip = stripManager!!.extractSegment(uri, segIdx, durationMs, totalSegments)
            if (strip != null) {
                strips[segIdx] = strip
                
                // Calcular progresso atual
                val progress = (segIdx + 1).toFloat() / targetSegments
                
                // Verificar se atingiu um novo estágio
                val currentStage = progressStages.indexOfFirst { progress >= it }
                if (currentStage != lastReportedStage && currentStage >= 0) {
                    val percent = (progressStages[currentStage] * 100).toInt()
                    
                    _thumbnailProgress.value = progressStages[currentStage]
                    
                    Timber.d("Thumbnail extraction: ${percent}% (${strips.size}/$targetSegments)")
                    lastReportedStage = currentStage
                }
            }
        }
         
        // Garantir que 100% seja reportado
        if (lastReportedStage < progressStages.size - 1) {
            _thumbnailProgress.value = 1f
            Timber.d("Thumbnail extraction: 100% (${strips.size}/$targetSegments)")
        }
        
        Timber.d("Strips carregadas: ${strips.size}/$targetSegments")
        strips  // withContext retorna isso automaticamente
    }
    
    override fun onCleared() {
        super.onCleared()
        clear()
    }
    
    // ========== CLASSES DE ESTADO ==========
    
    sealed class ThumbnailUiState {
        object Idle : ThumbnailUiState()
        object Loading : ThumbnailUiState()
        data class Ready(val loadedCount: Int, val totalCount: Int) : ThumbnailUiState()
        data class Error(val message: String) : ThumbnailUiState()
    }
    
    // ========== FACTORY ==========
    
    class ThumbnailViewModelFactory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ThumbnailViewModel::class.java)) {
                val videoRepository = VideoRepository(application)
                return ThumbnailViewModel(application, videoRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
        }
    }
}
