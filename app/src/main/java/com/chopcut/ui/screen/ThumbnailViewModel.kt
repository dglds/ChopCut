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
import kotlinx.coroutines.awaitAll
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
    
    private val _isCached = MutableStateFlow<Boolean>(false)
    val isCached: StateFlow<Boolean> = _isCached.asStateFlow()
    
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

                Timber.i("ThumbnailViewModel: Iniciando CARREGAMENTO SEQUENCIAL (LOD Estágio 2) para $uri")
                
                val totalSegments = stripManager?.getSegmentCount(durationMs) ?: 0
                _totalSegments.value = totalSegments
                
                // Usar a lógica de batching já implementada no extractThumbnailsLOD
                extractThumbnailsLOD(
                    uri = uri,
                    totalSegments = totalSegments,
                    durationMs = durationMs,
                    onlyFirstFrame = false
                )
                
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
        Timber.d("=== ThumbnailViewModel.preload CALLED ===")
        Timber.d("uri: $uri")
        Timber.d("stripsToPreload: $stripsToPreload")
        Timber.d("activeLoadingUri: $activeLoadingUri")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = ThumbnailUiState.Loading
                _thumbnailProgress.value = 0f
                _strips.value = emptyMap()
                _totalSegments.value = 0
                
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
                
                // 3. Verificar se já está em cache para liberação automática
                val hasCache = com.chopcut.data.thumbnail.ThumbnailCacheManager.hasInitialStripsCached(uri, totalSegments)
                _isCached.value = hasCache
                if (hasCache) {
                    Timber.i("ThumbnailViewModel: Cache Hit inicial detectado para $uri")
                }

                Timber.d("Total segments: $totalSegments")

                // Carregar TODOS os segmentos do vídeo
                val effectiveStripsToPreload = totalSegments

                Timber.d("Carregando $effectiveStripsToPreload segmentos (total)")
                
                // 3. Carregar em Dois Estágios (LOD)
                // Estágio 1: Apenas o primeiro frame de cada strip (Rápido!)
                Timber.i("ThumbnailViewModel: Iniciando ESTÁGIO 1 (Overview)")
                val overviewStrips = extractThumbnailsLOD(
                    uri = uri,
                    totalSegments = totalSegments,
                    durationMs = videoInfo.durationMs,
                    onlyFirstFrame = true
                )
                
                _strips.value = overviewStrips
                _thumbnailProgress.value = 0.5f // 50% do progresso total (Overview pronta)
                
                // Notificar que a visualização básica está pronta
                _uiState.value = ThumbnailUiState.Ready(overviewStrips.size, totalSegments)
                Timber.i("ThumbnailViewModel: ESTÁGIO 1 COMPLETO. Editor liberado.")

                // Estágio 2: Preencher o resto em background (Sem bloquear a UI)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        Timber.i("ThumbnailViewModel: Iniciando ESTÁGIO 2 (Detailing)")
                        extractThumbnailsLOD(
                            uri = uri,
                            totalSegments = totalSegments,
                            durationMs = videoInfo.durationMs,
                            onlyFirstFrame = false // Carrega o resto
                        )
                        _thumbnailProgress.value = 1f
                        Timber.i("ThumbnailViewModel: ESTÁGIO 2 COMPLETO. Todos os frames carregados.")
                    } catch (e: Exception) {
                        Timber.e(e, "ThumbnailViewModel: Erro no Estágio 2")
                    }
                }
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Preload FAILED")
                    Timber.e("Error message: ${e.message}")
                    Timber.e("Stack trace: ${e.stackTraceToString()}")
                    _uiState.value = ThumbnailUiState.Error(e.message ?: "Erro desconhecido")
                } else {
                    Timber.w("Preload CANCELLED")
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
                    _strips.update { current ->
                        current.toMutableMap().apply {
                            put(segmentIndex, strip)
                        }
                    }
                    trimMemory() // Evitar OOM durante scroll
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
     *
     * IMPORTANTE: NÃO recicla os bitmaps pois eles ainda podem estar
     * sendo usados pelo ThumbnailCacheManager (cache compartilhado).
     *
     * O cache LRU do ThumbnailCacheManager gerencia automaticamente a
     * liberação de memória quando necessário.
     */
    fun clear() {
        Timber.d("Clearing ThumbnailViewModel")
        Timber.d("NOTA: Não recicla bitmaps para não quebrar cache do ThumbnailCacheManager")

        // NÃO recicla bitmaps - eles podem estar sendo usados pelo cache
        // _strips.value.values.forEach { bitmap ->
        //     if (!bitmap.isRecycled) bitmap.recycle()
        // }

        _strips.value = emptyMap()
        _thumbnailProgress.value = 0f
        _totalSegments.value = 0
        _isCached.value = false
        _uiState.value = ThumbnailUiState.Idle
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    /**
     * Limita o número de bitmaps em memória para evitar OOM.
     * Mantém os 500 segmentos mais recentes para suportar vídeos longos (> 1h).
     */
    private fun trimMemory() {
        val currentStrips = _strips.value
        if (currentStrips.size > 500) {
            Timber.d("ThumbnailViewModel: Trimming memory (size=${currentStrips.size})")
            // Manter os últimos 500 adicionados (FIFO)
            val keysToRemove = currentStrips.keys.toList().take(currentStrips.size - 500)
            _strips.update { current ->
                current.toMutableMap().apply {
                    keysToRemove.forEach { remove(it) }
                }
            }
        }
    }

    /**
     * Extrai thumbnails em estágios (LOD) com processamento em lotes.
     */
    private suspend fun extractThumbnailsLOD(
        uri: Uri,
        totalSegments: Int,
        durationMs: Long,
        onlyFirstFrame: Boolean
    ): Map<Int, Bitmap> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val density = getApplication<Application>().resources.displayMetrics.density
        val defaultThumbWidth = (60 * density).toInt().coerceAtLeast(1)
        val defaultThumbHeight = (defaultThumbWidth / 1.77f).toInt().coerceAtLeast(1)
        
        Timber.d("extractThumbnailsLOD: total=$totalSegments, onlyFirstFrame=$onlyFirstFrame, density=$density")
        
        val batchSize = 5 
        
        for (i in 0 until totalSegments step batchSize) {
            ensureActive()
            
            val end = (i + batchSize).coerceAtMost(totalSegments)
            val batchIndices = i until end
            
            val jobs = batchIndices.map { segIdx ->
                async {
                    ensureActive()
                    
                    val strip = com.chopcut.data.thumbnail.ThumbnailCacheManager.getStrip(
                        uri = uri,
                        segmentIndex = segIdx,
                        durationMs = durationMs,
                        thumbWidth = stripManager?.thumbWidth ?: defaultThumbWidth,
                        thumbHeight = stripManager?.thumbHeight ?: defaultThumbHeight,
                        thumbsPerStrip = stripManager?.thumbsPerStrip ?: 10,
                        onlyFirstFrame = onlyFirstFrame
                    )
                    strip?.let { segIdx to it }
                }
            }

            val results: List<Pair<Int, Bitmap>> = jobs.awaitAll().filterNotNull()
            
            _strips.update { current ->
                current.toMutableMap().apply {
                    results.forEach { (segmentIndex, strip) -> put(segmentIndex, strip) }
                }
            }
            
            trimMemory()
            
            val baseDelay = if (onlyFirstFrame) 10L else {
                if (durationMs > 3_600_000L) 150L else 50L
            }
            kotlinx.coroutines.delay(baseDelay)
        }

        _strips.value
    }
    
    /**
     * Extrai thumbnails com progresso em estágios (DEPRECATED: Usar LOD).
     */
    
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
