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
    
    private val _individualFrames = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val individualFrames: StateFlow<Map<Long, Bitmap>> = _individualFrames.asStateFlow()
    
    // Controle para persistência de carregamento
    private var activeLoadingUri: Uri? = null
    private var loadingJob: kotlinx.coroutines.Job? = null
    
    private val _visibleSegmentIndex = MutableStateFlow<Int>(0)
    
    // ========== MÉTODOS PÚBLICOS ==========
    
    /**
     * Reporta o índice do segmento atualmente visível para priorizar carregamento.
     * OTIMIZAÇÃO: Histerese de 2 segmentos para evitar re-sorts constantes.
     */
    fun updateVisibleRange(index: Int) {
        val current = _visibleSegmentIndex.value
        val diff = kotlin.math.abs(current - index)
        
        // Só atualizar se a mudança for significativa OU se for o primeiro carregamento (0)
        // Isso evita que pequenos movimentos de scroll causem re-sort radial
        if (diff >= 2 || (current == 0 && index != 0)) {
            _visibleSegmentIndex.value = index
            Timber.v("Visible segment updated: $index (diff: $diff)")
        }
    }

    private var activeLoadingDuration: Long = 0L

    /**
     * Carrega todos os segmentos de um vídeo sequencialmente em background.
     */
    fun loadAllStripsSequentially(uri: Uri, durationMs: Long) {
        val durationDiff = kotlin.math.abs(activeLoadingDuration - durationMs)
        
        if (activeLoadingUri == uri && loadingJob?.isActive == true && durationDiff < 5000) {
            Timber.d("Sequencial loading já em andamento e estável para $uri")
            return
        }

        loadingJob?.cancel()
        activeLoadingUri = uri
        activeLoadingDuration = durationMs
        
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
                
                // Limpar frames individuais da sessão anterior
                _individualFrames.update { current ->
                    current.values.forEach { if (!it.isRecycled) it.recycle() }
                    emptyMap()
                }
                
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

                // Carregamento uniforme: um único passo, sem overview (Stage 1)
                Timber.i("ThumbnailViewModel: Iniciando CARREGAMENTO UNIFORME (onlyFirstFrame=false)")
                extractThumbnailsLOD(
                    uri = uri,
                    totalSegments = totalSegments,
                    durationMs = videoInfo.durationMs,
                    onlyFirstFrame = false
                )

                _thumbnailProgress.value = 1f
                _uiState.value = ThumbnailUiState.Ready(_strips.value.size, totalSegments)
                Timber.i("ThumbnailViewModel: CARREGAMENTO COMPLETO. ${_strips.value.size}/$totalSegments strips.")
                
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
                    thumbsPerStrip = stripManager?.thumbsPerStrip ?: 10,
                    onFrameExtracted = { posMs, bmp ->
                        _individualFrames.update { current ->
                            current.toMutableMap().apply { put(posMs, bmp) }
                        }
                    }
                )
                
                if (strip != null) {
                    _strips.update { current ->
                        current.toMutableMap().apply {
                            put(segmentIndex, strip)
                        }
                    }
                    
                    // Limpar frames individuais que agora fazem parte da strip
                    val thumbsPerStrip = stripManager?.thumbsPerStrip ?: 10
                    val startTimeMs = segmentIndex * thumbsPerStrip * 1000L
                    val endTimeMs = (segmentIndex + 1) * thumbsPerStrip * 1000L
                    
                    _individualFrames.update { current ->
                        val toRemove = current.filterKeys { it in startTimeMs until endTimeMs }
                        toRemove.values.forEach { if (!it.isRecycled) it.recycle() }
                        current.filterKeys { it < startTimeMs || it >= endTimeMs }
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
        _individualFrames.value = emptyMap()
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
        
        Timber.d("extractThumbnailsLOD: total=$totalSegments, onlyFirstFrame=$onlyFirstFrame (Uniform=true)")
        
        val batchSize = 5 
        val remainingIndices = (0 until totalSegments).toMutableList()
        
        // Se já temos algumas strips (devido ao Estágio 1 ou cache), não precisamos re-extrair
        // Mas para o Estágio 2, precisamos extrair a versão completa (thumbsPerStrip > 1) 
        // mesmo que já tenhamos o overview.

        while (remainingIndices.isNotEmpty()) {
            ensureActive()
            
            // Carregamento Uniforme: Processar na ordem original (0..N)
            
            val currentBatch = remainingIndices.take(batchSize)
            remainingIndices.removeAll(currentBatch)
            
            val jobs = currentBatch.map { segIdx ->
                async {
                    ensureActive()
                    val strip = try {
                        com.chopcut.data.thumbnail.ThumbnailCacheManager.getStrip(
                            uri = uri,
                            segmentIndex = segIdx,
                            durationMs = durationMs,
                            thumbWidth = stripManager?.thumbWidth ?: defaultThumbWidth,
                            thumbHeight = stripManager?.thumbHeight ?: defaultThumbHeight,
                            thumbsPerStrip = stripManager?.thumbsPerStrip ?: 10,
                            onlyFirstFrame = onlyFirstFrame,
                            onFrameExtracted = { posMs, bmp ->
                                // Streaming: Atualizar frames individuais enquanto a strip não está pronta
                                _individualFrames.update { current ->
                                    current.toMutableMap().apply { put(posMs, bmp) }
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Error extracting segment $segIdx")
                        null
                    }
                    
                    if (strip != null) {
                        // 🚀 OTIMIZAÇÃO: Commit imediato do segmento para a UI
                        _strips.update { current ->
                            current.toMutableMap().apply { put(segIdx, strip) }
                        }
                        
                        // Limpar frames individuais que agora fazem parte da strip carregada
                        val tps = stripManager?.thumbsPerStrip ?: 10
                        val startMs = segIdx * tps * 1000L
                        val endMs = (segIdx + 1) * tps * 1000L
                        
                        _individualFrames.update { current ->
                            val toRemove = current.filterKeys { it in startMs until endMs }
                            toRemove.values.forEach { if (!it.isRecycled) it.recycle() }
                            current.filterKeys { it < startMs || it >= endMs }
                        }
                        
                        trimMemory()
                    }
                    
                    strip?.let { segIdx to it }
                }
            }

            jobs.awaitAll()
            
            // Atualizar progresso real
            val loadedCount = totalSegments - remainingIndices.size
            _thumbnailProgress.value = loadedCount.toFloat() / totalSegments
            
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
