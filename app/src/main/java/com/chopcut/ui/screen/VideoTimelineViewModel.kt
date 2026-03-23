package com.chopcut.ui.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.collection.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.thumbnail.v3.FastFrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.lifecycle.ViewModelProvider
import kotlin.math.max
import kotlin.math.min

/**
 * ViewModel especializada para gerenciar sprites de thumbnails da timeline.
 *
 * Responsabilidades:
 * - Gerenciar cache de sprites em memória (StateFlow reativo)
 * - Cache LRU persistente entre navegações
 * - Extrair sprites sequencialmente sem delay (a todo vapor)
 * - Render-first: emitir sprite para state IMEDIATAMENTE, depois cachear
 *
 * Escopo: Activity (compartilhada entre navegações)
 */
class VideoTimelineViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    // ========== CONSTANTES ==========
    
    private companion object {
        const val SPRITE_COLS = 3
        const val SPRITE_ROWS = 1
        const val THUMBS_PER_SPRITE = SPRITE_COLS * SPRITE_ROWS  // 3
        const val FRAME_INTERVAL_US = 1_000_000L  // 1 FPS (1 frame a cada 1000ms)
        const val CACHE_PERCENTAGE = 0.10f  // 10% do total
        const val MAX_CACHE_SPRITES = 900
        const val BATCH_SIZE = 3
        
        // Multi-threading: 2 threads fixas
        const val MAX_CONCURRENT_EXTRACTIONS = 2
    }
    
    // ========== ESTADO REATIVO (sprites prontos para renderizar) ==========
    
    private val _sprites = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val sprites: StateFlow<Map<Int, Bitmap>> = _sprites.asStateFlow()
    
    // ========== CACHE LRU PERSISTENTE (fallback) ==========
    
    private var spriteCache: LruCache<Int, Bitmap>? = null
    
    // ========== ESTADO DE EXTRAÇÃO ==========
    
    private val _extractionProgress = MutableStateFlow<Float>(0f)
    val extractionProgress: StateFlow<Float> = _extractionProgress.asStateFlow()
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    // ========== DEPENDÊNCIAS ==========
    
    private var extractor: FastFrameExtractor? = null
    private var activeUri: Uri? = null
    private var activeDurationMs = 0L
    private var loadingJob: Job? = null
    
    // ========== DIMENSÕES ==========
    
    private var thumbWidth = 0
    private var thumbHeight = 0
    
    // ========== MÉTODOS PÚBLICOS ==========
    
    /**
     * Carrega sprites para um vídeo com render-first.
     *
     * Processo:
     * 1. Verifica se já carregou (cache hit)
     * 2. Prepara FastFrameExtractor
     * 3. Extrai sprites sequencialmente sem delay
     * 4. Para cada sprite: emitir para state (render-first) + cachear no LRU
     *
     * @param uri URI do vídeo
     * @param durationMs Duração do vídeo em ms
     * @param width Largura da thumbnail
     * @param height Altura da thumbnail
     */
    fun loadSprites(uri: Uri, durationMs: Long, width: Int, height: Int) {
        if (activeUri != null && activeUri != uri) {
            loadingJob?.cancel()
            clear()
        }
        
        if (activeUri == uri && _isReady.value) {
            Timber.d("Sprites já carregados para $uri, cache hit!")
            return
        }
        
        activeUri = uri
        activeDurationMs = durationMs
        thumbWidth = width
        thumbHeight = height
        
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.d("Carregando sprites: uri=$uri, duration=${durationMs}ms, dims=${width}x${height}")
                
                _isReady.value = false
                _extractionProgress.value = 0f
                
                // Inicializar cache
                val totalFrames = kotlin.math.ceil(durationMs / 1000f).toInt()
                val totalSprites = (totalFrames + THUMBS_PER_SPRITE - 1) / THUMBS_PER_SPRITE
                // Cache size: se poucos sprites (<10), mantém todos; senão, 10% com max 900
                val cacheSize = if (totalSprites < 10) {
                    totalSprites  // Mantém todos os sprites quando são poucos
                } else {
                    max(min((totalSprites * CACHE_PERCENTAGE).toInt(), MAX_CACHE_SPRITES), 1)
                }
                spriteCache = LruCache(cacheSize)
                
                Timber.d("Total frames: $totalFrames, total sprites: $totalSprites, cache size: $cacheSize")
                
                // Recrear extractor sempre (para evitar conflito com vídeos diferentes)
                extractor?.release()
                extractor = FastFrameExtractor(getApplication(), uri)
                val prepared = extractor?.prepare(width, height) ?: false
                
                if (!prepared) {
                    Timber.e("Falha ao preparar FastFrameExtractor")
                    _isReady.value = false
                    return@launch
                }
                
                Timber.d("FastFrameExtractor preparado, iniciando extração de sprites")
                
                val spritesExtracted = mutableMapOf<Int, Bitmap>()
                val concurrency = 2
                val batchSize = BATCH_SIZE
                var nextSpriteIndex = 0
                
                while (nextSpriteIndex < totalSprites) {
                    val currentBatchSize = min(batchSize, totalSprites - nextSpriteIndex)
                    val endBatchIndex = nextSpriteIndex + currentBatchSize
                    
                    Timber.d("Extraindo lote: sprite $nextSpriteIndex até ${endBatchIndex - 1} (tamanho: $currentBatchSize)")
                    
                    (nextSpriteIndex until endBatchIndex).chunked(concurrency).forEach { chunk ->
                        val deferreds: List<kotlinx.coroutines.Deferred<Pair<Int, Bitmap?>>> = chunk.map { spriteIndex ->
                            async(Dispatchers.IO) {
                                ensureActive()
                                val cached = spriteCache?.get(spriteIndex)
                                if (cached != null && !cached.isRecycled) {
                                    Pair(spriteIndex, cached)
                                } else {
                                    val sprite = extractSprite(spriteIndex, totalFrames)
                                    Pair(spriteIndex, sprite)
                                }
                            }
                        }
                        val results = deferreds.map { it.await() }
                        
                        results.forEach { (spriteIndex, sprite) ->
                            if (sprite != null) {
                                spritesExtracted[spriteIndex] = sprite
                                _sprites.value = spritesExtracted.toMap()
                                spriteCache?.put(spriteIndex, sprite)
                                _extractionProgress.value = (spriteIndex + 1).toFloat() / totalSprites
                                Timber.d("Sprite $spriteIndex extraido, progress: ${_extractionProgress.value}")
                            }
                        }
                    }
                    
                    nextSpriteIndex = endBatchIndex
                }
                
                _isReady.value = true
                Timber.d("Extração de sprites concluída: ${spritesExtracted.size} sprites")
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.d("Extração de sprites cancelada")
                _isReady.value = false
            } catch (e: Exception) {
                Timber.e(e, "Erro ao carregar sprites")
                _isReady.value = false
            }
        }
    }
    
    /**
     * Obtém um sprite baseado no índice do frame.
     *
     * Prioridade: 1. State reativo (pronto para renderizar), 2. Cache LRU (persistente)
     *
     * @param frameIndex Índice do frame
     * @return Sprite Bitmap ou null se não disponível
     */
    fun getSprite(frameIndex: Int): Bitmap? {
        val spriteIndex = frameIndex / THUMBS_PER_SPRITE
        
        // Tentar do state (pronto para renderizar)
        val fromState = _sprites.value[spriteIndex]
        if (fromState != null && !fromState.isRecycled) {
            return fromState
        }
        
        // Tentar do cache LRU (persistente)
        val fromCache = spriteCache?.get(spriteIndex)
        if (fromCache != null && !fromCache.isRecycled) {
            return fromCache
        }
        
        return null
    }
    
    /**
     * Verifica se um frame específico está pronto.
     *
     * @param frameIndex Índice do frame
     * @return true se frame está pronto (sprite disponível), false caso contrário
     */
    fun isFrameReady(frameIndex: Int): Boolean {
        return getSprite(frameIndex) != null
    }

    /**
     * Verifica se um frame específico veio do cache.
     *
     * @param frameIndex Índice do frame
     * @return true se frame veio do cache (não extraído agora), false caso contrário
     */
    fun isFrameFromCache(frameIndex: Int): Boolean {
        val spriteIndex = frameIndex / THUMBS_PER_SPRITE
        return spriteCache?.get(spriteIndex) != null
    }
    
    /**
     * Obtém o número total de frames para a duração atual.
     *
     * @return Total de frames
     */
    fun getTotalFrames(): Int {
        return (activeDurationMs / 1000).toInt().coerceAtLeast(1)
    }
    
    /**
     * Limpa todo o cache e estado.
     *
     * IMPORTANTE: Recicla os bitmaps para liberar memória nativa.
     */
    fun clear() {
        Timber.d("Limpando cache de sprites")
        
        // Reciclar sprites no state
        _sprites.value.values.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        
        // Evict cache LRU
        spriteCache?.evictAll()
        
        // Liberar extractor
        extractor?.release()
        extractor = null
        
        // Limpar estado
        _sprites.value = emptyMap()
        _extractionProgress.value = 0f
        _isReady.value = false
        activeUri = null
        activeDurationMs = 0L
        
        Timber.d("Cache de sprites limpo")
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    /**
     * Extrai um sprite contendo 100 frames.
     *
     * Processo:
     * 1. Criar sprite bitmap (10×10 grid)
     * 2. Extrair 100 frames consecutivos usando FastFrameExtractor
     * 3. Desenhar cada frame na posição correta do grid
     * 4. Reciclar frames temporários (importante para evitar OOM)
     *
     * @param spriteIndex Índice do sprite (0, 1, 2, ...)
     * @param totalFrames Total de frames no vídeo
     * @return Sprite Bitmap ou null se falhar
     */
    private suspend fun extractSprite(spriteIndex: Int, totalFrames: Int): Bitmap? {
        val extractor = extractor ?: return null
        
        val startFrame = spriteIndex * THUMBS_PER_SPRITE
        val endFrame = min(startFrame + THUMBS_PER_SPRITE, totalFrames)
        
        // Criar sprite bitmap
        val spriteW = SPRITE_COLS * thumbWidth
        val spriteH = SPRITE_ROWS * thumbHeight
        val sprite = Bitmap.createBitmap(spriteW, spriteH, Bitmap.Config.RGB_565)
        val canvas = Canvas(sprite)
        val paint = Paint()
        
        // Extrair e desenhar frames no sprite
        for (i in 0 until (endFrame - startFrame)) {
            val frameIndex = startFrame + i
            val timeUs = frameIndex * FRAME_INTERVAL_US
            
            val frame = extractor.getFrameAt(timeUs)
            if (frame != null) {
                val col = i % SPRITE_COLS
                val row = i / SPRITE_COLS
                canvas.drawBitmap(
                    frame,
                    col * thumbWidth.toFloat(),
                    row * thumbHeight.toFloat(),
                    paint
                )
                frame.recycle()  // IMPORTANTE: liberar frame temporário
            }
        }
        
        return sprite
    }
    
    // ========== CLEANUP ==========
    
    override fun onCleared() {
        super.onCleared()
        clear()
    }
    
    // ========== FACTORY ==========
    
    class VideoTimelineViewModelFactory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VideoTimelineViewModel::class.java)) {
                return VideoTimelineViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
        }
    }
}
