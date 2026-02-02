package com.chopcut.ui.timeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.util.debug.DebugConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Gerenciador de thumbnails para a timeline.
 * Focado em ESTABILIDADE sobre velocidade:
 * - Carrega thumbnails um por vez (evita ANR)
 * - Timeout de 5s por thumbnail
 * - Fallback automático para placeholder em caso de erro
 * - Logs detalhados para debug via Logcat
 * - Cache em memória com limite de 50MB
 */
class ThumbnailLoader(
    private val context: Context
) {
    companion object {
        private const val TAG = "[ThumbnailLoader]"
        private const val TIMEOUT_MS = 5000L // 5s timeout por thumb
        private const val MAX_CACHE_SIZE_MB = 50
        private const val MAX_CACHE_SIZE_BYTES = MAX_CACHE_SIZE_MB * 1024 * 1024
        private const val THUMB_WIDTH = 80
        private const val THUMB_HEIGHT = 80
        private const val RETRY_DELAY_MS = 100L // Delay entre tentativas
    }

    private val extractor = ThumbnailExtractor(context)
    
    // Cache thread-safe
    private val cache: LruCache<Long, Bitmap> = object : LruCache<Long, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: Long, value: Bitmap): Int {
            return value.byteCount
        }

        override fun entryRemoved(evicted: Boolean, key: Long, oldValue: Bitmap?, newValue: Bitmap?) {
            super.entryRemoved(evicted, key, oldValue, newValue)
            if (evicted) {
                oldValue?.recycle()
                Timber.d("$TAG Cache EVICT - position=${key}ms, novo tamanho=${size()}")
            }
        }
    }

    // Estado de carregamento exposto via StateFlow
    private val _loadingState = MutableStateFlow(LoadingState.IDLE)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    // Estatísticas para debug
    private val _stats = MutableStateFlow(LoadingStats())
    val stats: StateFlow<LoadingStats> = _stats.asStateFlow()

    // Mutex para serializar carregamento (um por vez)
    private val loadMutex = Mutex()

    // Track de posições que falharam (para não tentar repetidamente)
    private val failedPositions = mutableSetOf<Long>()

    // Callback para captura de screenshots em momentos chave
    private var screenshotCallback: ((String, Int) -> Unit)? = null

    // Último log de estatísticas (para evitar spam)
    private var lastStatsLogTime = 0L

    enum class LoadingState {
        IDLE,
        LOADING,
        ERROR,
        COMPLETE
    }

    data class LoadingStats(
        val totalFrames: Int = 0,
        val loadedCount: Int = 0,
        val failedCount: Int = 0,
        val cacheHits: Int = 0,
        val cacheMisses: Int = 0,
        val currentPosition: Long = 0L
    ) {
        val hitRate: Float
            get() = if (cacheHits + cacheMisses > 0) {
                (cacheHits.toFloat() / (cacheHits + cacheMisses)) * 100
            } else 0f

        val progress: Float
            get() = if (totalFrames > 0) {
                (loadedCount.toFloat() / totalFrames) * 100
            } else 0f
    }

    /**
     * Inicializa o cache e loga configuração
     */
    init {
        Timber.d("$TAG Inicializado - Cache: ${MAX_CACHE_SIZE_MB}MB, Timeout: ${TIMEOUT_MS}ms")
        Timber.d("$TAG Dimensões: ${THUMB_WIDTH}x${THUMB_HEIGHT}px")
    }

    /**
     * Carrega thumbnails de forma segura e lazy.
     * Carrega um por vez para evitar ANR.
     * 
     * @param uri URI do vídeo
     * @param durationMs Duração total do vídeo
     * @param frameCount Número de frames a extrair
     * @param onProgress Callback opcional para progresso
     */
    suspend fun loadThumbnails(
        uri: Uri,
        durationMs: Long,
        frameCount: Int,
        onProgress: ((LoadingStats) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (durationMs <= 0 || frameCount <= 0) {
            Timber.w("$TAG Parâmetros inválidos: duration=${durationMs}ms, frames=${frameCount}")
            return@withContext
        }

        _loadingState.value = LoadingState.LOADING
        _stats.value = LoadingStats(totalFrames = frameCount)

        Timber.d("$TAG Iniciando carregamento: ${frameCount} frames, vídeo=${durationMs}ms")
        val startTime = System.currentTimeMillis()

        // Notifica início para screenshot
        notifyLoadingStarted()

        val intervalMs = durationMs / frameCount.toFloat()
        var successCount = 0
        var failCount = 0
        var cacheHitCount = 0
        var cacheMissCount = 0

        // Limpa falhas anteriores para este vídeo
        failedPositions.clear()

        // Carrega um por vez (serializado)
        for (index in 0 until frameCount) {
            if (!isActive) {
                Timber.w("$TAG Cancelado pelo usuário no frame ${index}/${frameCount}")
                break
            }

            val positionMs = (index * intervalMs).toLong()

            // Atualiza estado atual
            _stats.value = _stats.value.copy(currentPosition = positionMs)

            // Verifica cache primeiro
            val cached = cache.get(positionMs)
            if (cached != null && !cached.isRecycled) {
                cacheHitCount++
                successCount++
                if (DebugConfig.VERBOSE_THUMBNAIL_LOGS) {
                    Timber.v("$TAG HIT - position=${positionMs}ms, index=${index}")
                }
                continue
            }

            cacheMissCount++

            // Tenta carregar com timeout
            val result = loadSingleThumbnail(uri, positionMs, index, frameCount)

            when (result) {
                is LoadResult.Success -> {
                    successCount++
                    cache.put(positionMs, result.bitmap)
                }
                is LoadResult.Error -> {
                    failCount++
                    failedPositions.add(positionMs)
                    Timber.w("$TAG FAIL - position=${positionMs}ms: ${result.message}")
                }
            }

            // Atualiza estatísticas
            _stats.value = LoadingStats(
                totalFrames = frameCount,
                loadedCount = successCount,
                failedCount = failCount,
                cacheHits = cacheHitCount,
                cacheMisses = cacheMissCount,
                currentPosition = positionMs
            )

            onProgress?.invoke(_stats.value)

            // Auto-log de estatísticas (evita spam)
            if (DebugConfig.AUTO_LOG_STATS) {
                val now = System.currentTimeMillis()
                if (now - lastStatsLogTime >= DebugConfig.AUTO_LOG_INTERVAL_MS) {
                    val progress = (index + 1) * 100 / frameCount
                    Timber.d("$TAG Progresso: ${progress}% (${index + 1}/${frameCount})")
                    lastStatsLogTime = now
                }
            }

            // Captura screenshot em milestones (0%, 25%, 50%, 75%, 100%)
            if (DebugConfig.SCREENSHOT_ON_THUMBNAIL_EVENTS) {
                val progress = (index + 1) * 100 / frameCount
                val milestones = listOf(0, 25, 50, 75, 100)
                val lastProgress = index * 100 / frameCount
                
                milestones.forEach { milestone ->
                    if (lastProgress < milestone && progress >= milestone) {
                        screenshotCallback?.invoke("progress", milestone)
                        Timber.d("$TAG Screenshot milestone: ${milestone}%")
                    }
                }
            }

            // Delay entre carregamentos para não sobrecarregar
            delay(RETRY_DELAY_MS)
        }

        val elapsed = System.currentTimeMillis() - startTime
        _loadingState.value = if (failCount == frameCount) {
            notifyError()
            LoadingState.ERROR
        } else if (failCount > 0) {
            notifyComplete()
            Timber.w("$TAG Parcial: ${successCount}/${frameCount} thumbs em ${elapsed}ms")
            LoadingState.COMPLETE
        } else {
            notifyComplete()
            LoadingState.COMPLETE
        }

        val finalStats = _stats.value
        Timber.d("$TAG Completo em ${elapsed}ms")
        Timber.d("$TAG Sucesso: ${finalStats.loadedCount}/${frameCount} (${finalStats.progress.toInt()}%)")
        Timber.d("$TAG Cache: ${finalStats.cacheHits} hits, ${finalStats.cacheMisses} misses, " +
                "hitRate=${finalStats.hitRate.toInt()}%")
        Timber.d("$TAG Memória: ${cache.size()}/${MAX_CACHE_SIZE_MB}MB")
        Timber.d("$TAG Falhas: ${failedPositions.size} posições")
    }

    /**
     * Carrega um único thumbnail com timeout e tratamento de erro.
     * Thread-safe via mutex.
     */
    private suspend fun loadSingleThumbnail(
        uri: Uri,
        positionMs: Long,
        index: Int,
        total: Int
    ): LoadResult = loadMutex.withLock {
        try {
            if (DebugConfig.VERBOSE_THUMBNAIL_LOGS) {
                Timber.v("$TAG Carregando thumb ${index + 1}/${total} - position=${positionMs}ms")
            }

            val bitmap = withTimeout(TIMEOUT_MS) {
                extractor.extractAt(uri, positionMs, THUMB_WIDTH, THUMB_HEIGHT)
            }

            if (bitmap != null && !bitmap.isRecycled) {
                if (DebugConfig.VERBOSE_THUMBNAIL_LOGS) {
                    Timber.v("$TAG OK - position=${positionMs}ms, size=${bitmap.width}x${bitmap.height}")
                }
                LoadResult.Success(bitmap)
            } else {
                LoadResult.Error("Bitmap nulo ou reciclado")
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w("$TAG TIMEOUT - position=${positionMs}ms após ${TIMEOUT_MS}ms")
            LoadResult.Error("Timeout após ${TIMEOUT_MS}ms")
        } catch (e: CancellationException) {
            Timber.w("$TAG CANCELADO - position=${positionMs}ms")
            LoadResult.Error("Operação cancelada")
        } catch (e: Exception) {
            Timber.e(e, "$TAG ERRO - position=${positionMs}ms: ${e.message}")
            LoadResult.Error("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Obtém um thumbnail do cache (ou null se não existir/falhou).
     * Thread-safe.
     */
    fun getThumbnail(positionMs: Long): Bitmap? {
        val bitmap = cache.get(positionMs)
        return if (bitmap != null && !bitmap.isRecycled) {
            bitmap
        } else {
            null
        }
    }

    /**
     * Verifica se uma posição falhou anteriormente (para não tentar repetidamente).
     */
    fun hasFailed(positionMs: Long): Boolean {
        return failedPositions.contains(positionMs)
    }

    /**
     * Define callback para captura de screenshots.
     * Parâmetros: (eventType, progressPercent)
     */
    fun setScreenshotCallback(callback: (String, Int) -> Unit) {
        screenshotCallback = callback
        Timber.d("$TAG Screenshot callback registrado")
    }

    /**
     * Notifica início do carregamento (para screenshot).
     */
    fun notifyLoadingStarted() {
        if (DebugConfig.SCREENSHOT_ON_THUMBNAIL_EVENTS) {
            screenshotCallback?.invoke("start", 0)
            Timber.d("$TAG Screenshot event: start")
        }
    }

    /**
     * Notifica erro no carregamento (para screenshot).
     */
    fun notifyError() {
        if (DebugConfig.SCREENSHOT_ON_THUMBNAIL_EVENTS) {
            screenshotCallback?.invoke("error", 0)
            Timber.d("$TAG Screenshot event: error")
        }
    }

    /**
     * Notifica completude do carregamento (para screenshot).
     */
    fun notifyComplete() {
        if (DebugConfig.SCREENSHOT_ON_THUMBNAIL_EVENTS) {
            screenshotCallback?.invoke("complete", 100)
            Timber.d("$TAG Screenshot event: complete")
        }
    }

    /**
     * Limpa o cache e libera memória.
     * Chamar ao descartar vídeo.
     */
    fun clear() {
        Timber.d("$TAG Limpando cache... (${cache.size()} itens)")
        cache.evictAll()
        failedPositions.clear()
        _stats.value = LoadingStats()
        _loadingState.value = LoadingState.IDLE
        screenshotCallback = null
        lastStatsLogTime = 0L
        Timber.d("$TAG Cache limpo")
    }

    /**
     * Loga estatísticas atuais do cache (para debug manual).
     */
    fun logStats() {
        val stats = _stats.value
        Timber.d("$TAG ===== ESTATÍSTICAS =====")
        Timber.d("$TAG Estado: ${_loadingState.value}")
        Timber.d("$TAG Progresso: ${stats.progress.toInt()}% (${stats.loadedCount}/${stats.totalFrames})")
        Timber.d("$TAG Cache: ${stats.cacheHits} hits, ${stats.cacheMisses} misses (${stats.hitRate.toInt()}%)")
        Timber.d("$TAG Memória: ${cache.size()}/${MAX_CACHE_SIZE_MB}MB")
        Timber.d("$TAG Falhas: ${failedPositions.size} posições")
        Timber.d("$TAG ========================")
    }

    private sealed class LoadResult {
        data class Success(val bitmap: Bitmap) : LoadResult()
        data class Error(val message: String) : LoadResult()
    }
}
