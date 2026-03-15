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
import timber.log.Timber
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

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
     * Carrega strips visíveis + buffer de on-demand.
     * 
     * @param uri URI do vídeo
     * @param durationMs Duração em ms
     * @param currentSecond Segundo atual da timeline
     * @param viewportWidthSeconds Largura da viewport em segundos
     * @param bufferSize Buffer de strips antes e depois da viewport (padrão: 6)
     */
    fun loadVisibleStripsWithBuffer(
        uri: Uri,
        durationMs: Long,
        currentSecond: Int,
        viewportWidthSeconds: Int,
        bufferSize: Int = 6
    ) {
        loadingJob?.cancel()
        activeLoadingUri = uri
        
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                
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
                        adaptiveStrips = true
                    )
                }

                
                val totalSegments = stripManager?.getSegmentCount(durationMs) ?: 0
                if (totalSegments == 0) return@launch
                _totalSegments.value = totalSegments

                // 1. Calcular segmentos visíveis
                val thumbsPerStrip = stripManager?.thumbsPerStrip ?: 10
                val startSegment = (currentSecond / thumbsPerStrip).coerceAtLeast(0)
                val endSegment = ((currentSecond + viewportWidthSeconds) / thumbsPerStrip)
                    .coerceAtMost(totalSegments - 1)

                // 2. Adicionar buffer
                val bufferedStart = maxOf(0, startSegment - bufferSize)
                val bufferedEnd = minOf(totalSegments - 1, endSegment + bufferSize)

                Timber.tag("OnDemandLoading").d("Viewport: second=$currentSecond, segments=$startSegment..$endSegment, buffer=$bufferSize, range=$bufferedStart..$bufferedEnd")

                // 3. Carregar apenas segmentos no buffer (sem delay fixo)
                val segmentsToLoad = (bufferedStart..bufferedEnd).filter { segIdx ->
                    !_strips.value.containsKey(segIdx)
                }

                if (segmentsToLoad.isNotEmpty()) {
                    Timber.tag("OnDemandLoading").i("Carregando ${segmentsToLoad.size} strips: ${segmentsToLoad.first()}..${segmentsToLoad.last()}")
                    
                    // Carregar em batch de 5 para performance
                    segmentsToLoad.chunked(5).forEach { chunk ->
                        ensureActive()
                        chunk.forEach { segIdx ->
                            launch {
                                loadStrip(uri, segIdx, durationMs)
                            }
                        }
                    }
                }
                
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                }
            }
        }
    }
    
    /**
     * Pré-carrega um número fixo de strips para um vídeo.
     * 
     * DESATIVADO PARA TESTE: Usando apenas on-demand loading
     * 
     * @param uri URI do vídeo
     * @param stripsToPreload Número de strips a carregar (padrão: 6)
     */
    fun preload(uri: Uri, stripsToPreload: Int = 6) {
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = ThumbnailUiState.Loading
                _thumbnailProgress.value = 0f
                _strips.value = emptyMap()
                _totalSegments.value = 0
                
                
                // 1. Obter metadados do vídeo
                val videoInfo = videoRepository.getMetadata(uri)
                if (videoInfo == null) {
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
                    adaptiveStrips = true
                )

                val totalSegments = stripManager!!.getSegmentCount(videoInfo.durationMs)
                _totalSegments.value = totalSegments
                
                Timber.tag("ThumbnailPreload").i("Metadados carregados: duração=${videoInfo.durationMs}ms, totalSegments=$totalSegments, adaptiveStrips=true")
                
                // Notificar que a visualização está pronta (sem strips pré-carregadas)
                _uiState.value = ThumbnailUiState.Ready(0, totalSegments)
                
                Timber.tag("ThumbnailPreload").i("Preload DESATIVADO - On-demand loading vai carregar strips conforme usuário rola")
                 
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
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
                        current.toMutableMap().also { it[segmentIndex] = strip }
                    }
                    trimMemory() // Evitar OOM durante scroll
                }
            } catch (e: Exception) {
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
     * Otimizado: Remove diretamente sem criar cópia do map.
     */
    private fun trimMemory() {
        _strips.update { current ->
            if (current.size > 500) {
                val keysToRemove = current.keys.take(current.size - 500)
                current.toMutableMap().also { map ->
                    keysToRemove.forEach { map.remove(it) }
                }
            } else {
                current
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
                current.toMutableMap().also { map ->
                    results.forEach { (segmentIndex, strip) ->
                        map[segmentIndex] = strip
                    }
                }
            }
            
            trimMemory()
            
            // Reduzido drasticamente para scroll mais fluido
            val baseDelay = if (onlyFirstFrame) 5L else {
                if (durationMs > 3_600_000L) 20L else 10L
            }
            kotlinx.coroutines.delay(baseDelay)
        }

        _strips.value.toMap()
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
