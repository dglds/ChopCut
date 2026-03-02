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
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
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

    private val _totalSegments = MutableStateFlow<Int>(0)
    val totalSegments: StateFlow<Int> = _totalSegments.asStateFlow()
    
    private val _uiState = MutableStateFlow<ThumbnailUiState>(ThumbnailUiState.Idle)
    val uiState: StateFlow<ThumbnailUiState> = _uiState.asStateFlow()
    
    // ========== DEPENDÊNCIAS ==========
    
    private var stripManager: com.chopcut.data.thumbnail.ThumbnailStripManager? = null
    
    // Controle para persistência de carregamento
    private var activeLoadingUri: Uri? = null
    private var loadingJob: kotlinx.coroutines.Job? = null
    
    // ========== MÉTODOS PÚBLICOS ==========
    
    /**
     * Carrega todos os segmentos de um vídeo sequencialmente em background.
     * Esta função sobrevive à navegação pois está no viewModelScope.
     * 
     * @param uri URI do vídeo
     * @param durationMs Duração em ms
     */
    fun loadAllStripsSequentially(uri: Uri, durationMs: Long) {
        if (activeLoadingUri == uri && loadingJob?.isActive == true) {
            Timber.d("Sequencial loading já em andamento para $uri")
            return
        }

        loadingJob?.cancel()
        activeLoadingUri = uri
        
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.i("Iniciando CARREGAMENTO SEQUENCIAL PERSISTENTE para $uri")
                
                // Garantir stripManager configurado
                if (stripManager == null) {
                    val density = getApplication<Application>().resources.displayMetrics.density
                    val thumbWidth = (60 * density).toInt().coerceAtLeast(1)
                    val thumbsPerStrip = PreferencesManager(getApplication()).thumbsPerStrip
                    
                    // Obter metadados se necessário para o aspect ratio
                    val videoInfo = videoRepository.getMetadata(uri)
                    val aspectRatio = videoInfo?.aspectRatio ?: 1.77f
                    val thumbHeight = (thumbWidth / aspectRatio).toInt().coerceAtLeast(1)

                    stripManager = com.chopcut.data.thumbnail.ThumbnailStripManager(
                        getApplication(), thumbWidth, thumbHeight, thumbsPerStrip,
                        adaptiveStrips = false
                    )
                }

                val totalSegments = stripManager?.getSegmentCount(durationMs) ?: 0
                
                for (segIdx in 0 until totalSegments) {
                    // Verificação de cancelamento da coroutine
                    ensureActive()

                    if (!_strips.value.containsKey(segIdx)) {
                        loadStrip(uri, segIdx, durationMs)
                        // Delay para não travar o decoder e permitir outras tarefas
                        kotlinx.coroutines.delay(150)
                    }
                }
                
                Timber.i("Finalizado CARREGAMENTO SEQUENCIAL para $uri")
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Falha no carregamento sequencial")
                }
            }
        }
    }
    
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
                _totalSegments.value = totalSegments

                // FASE 2.4: Extração Inteligente e Adaptativa
                // Calcular segmentos baseado em percentual do vídeo ao invés de número fixo
                val preloadPercent = 0.05  // 5% do vídeo
                val preloadSeconds = ((videoInfo.durationMs / 1000) * preloadPercent).toLong()
                val minPreloadSeconds = 30L  // Mínimo de 30 segundos
                val effectivePreloadSeconds = maxOf(preloadSeconds, minPreloadSeconds)

                // Calcular segmentos baseado em thumbsPerStrip
                val calculatedStripsToPreload = (effectivePreloadSeconds / thumbsPerStrip).toInt()
                    .coerceAtLeast(3)  // Mínimo de 3 segmentos
                    .coerceAtMost(totalSegments)

                // Usar o máximo entre o calculado e o fornecido pelo chamador
                val effectiveStripsToPreload = maxOf(calculatedStripsToPreload, stripsToPreload)
                    .coerceAtMost(totalSegments)

                Timber.d("""
                    Extração Inteligente Adaptativa:
                    • Duração do vídeo: ${videoInfo.durationMs / 1000}s
                    • 5% do vídeo: ${preloadSeconds}s
                    • Preload efetivo: ${effectivePreloadSeconds}s
                    • Segmentos calculados: $calculatedStripsToPreload
                    • Segmentos fornecidos: $stripsToPreload
                    • Segmentos finais: $effectiveStripsToPreload/$totalSegments
                """.trimIndent())
                
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
        _totalSegments.value = 0
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

        Timber.d("Extraindo $targetSegments segmentos com cache (paralelo)")

        // CARREGAMENTO PARALELO usando ThumbnailCacheManager
        val jobs = (0 until targetSegments).map { segIdx ->
            async {
                com.chopcut.data.thumbnail.ThumbnailCacheManager.getStrip(
                    uri = uri,
                    segmentIndex = segIdx,
                    durationMs = durationMs,
                    thumbWidth = stripManager?.thumbWidth ?: 60,
                    thumbHeight = stripManager?.thumbHeight ?: 40,
                    thumbsPerStrip = stripManager?.thumbsPerStrip ?: 10
                )?.let { bitmap ->
                    segIdx to bitmap
                }
            }
        }

        // Aguardar todas as strips carregarem e reportar progresso
        val results = jobs.mapIndexed { index, deferred ->
            val result = deferred.await()
            if (result != null) {
                // OTIMIZAÇÃO: Atualizar _strips incrementalmente para feedback visual e liberação rápida
                _strips.update { current ->
                    current.toMutableMap().apply {
                        put(result.first, result.second)
                    }
                }
            }

            // Calcular progresso atual
            val progress = (index + 1).toFloat() / targetSegments

            // Verificar se atingiu um novo estágio
            val currentStage = progressStages.indexOfFirst { progress >= it }
            if (currentStage != lastReportedStage && currentStage >= 0) {
                val percent = (progressStages[currentStage] * 100).toInt()
                _thumbnailProgress.value = progressStages[currentStage]
                Timber.d("Thumbnail extraction: ${percent}% (${strips.size}/$targetSegments)")
                lastReportedStage = currentStage
            }

            result
        }

        // Garantir que 100% seja reportado
        if (lastReportedStage < progressStages.size - 1) {
            _thumbnailProgress.value = 1f
            Timber.d("Thumbnail extraction: 100% (${strips.size}/$targetSegments)")
        }

        Timber.d("Strips carregadas COM CACHE: ${targetSegments}")
        _strips.value
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
