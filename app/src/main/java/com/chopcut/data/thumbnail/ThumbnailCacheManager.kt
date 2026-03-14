package com.chopcut.data.thumbnail

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * Singleton para gerenciar cache compartilhado de thumbnail strips.
 * 
 * Resolve Problema 1: Centraliza cache entre todos os ViewModels
 * Resolve Problema 2: Persiste jobs mesmo quando ViewModels são destruídos
 * Resolve Problema 4: Usa ThumbnailCache LRU internamente
 * Resolve Problema 6: Cache persiste ao navegar
 */
object ThumbnailCacheManager {
    @Volatile
    private var appContext: Context? = null
    
    // Cache em memória LRU (100 strips ~43MB)
    private val memoryCache = ThumbnailCache(maxSize = 100)
    
    // Gerenciador de strips (já existe, vamos reutilizar)
    private var stripManager: ThumbnailStripManager? = null
    
    // Lock para inicialização thread-safe
    private val initLock = Mutex()

    // Scope persistente para jobs internos (evita CoroutineScope órfão)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tracking de jobs
    private val jobsLock = Mutex()
    private val videoJobs = mutableMapOf<String, Job>()
    private val segmentJobs = mutableMapOf<String, Job>()

    /**
     * Métricas de cache health para monitoramento e debugging
     */
    private val metrics = CacheMetrics()

    /**
     * Classe interna para tracking de métricas do cache
     */
    private class CacheMetrics {
        @Volatile
        var hits = 0L

        @Volatile
        var misses = 0L

        @Volatile
        var extractionCount = 0L

        @Volatile
        var extractionTotalTimeMs = 0L

        @Volatile
        var lastExtractionTime = 0L

        @Volatile
        var cacheCorruptionsDetected = 0L

        @Volatile
        var evictions = 0L

        fun getHitRate(): Double {
            val total = hits + misses
            return if (total > 0) (hits.toDouble() / total) * 100.0 else 0.0
        }

        fun getAverageExtractionTime(): Long {
            return if (extractionCount > 0) extractionTotalTimeMs / extractionCount else 0L
        }

        fun recordHit() { synchronized(this) { hits++ } }
        fun recordMiss() { synchronized(this) { misses++ } }
        fun recordExtraction(timeMs: Long) {
            synchronized(this) {
                extractionCount++
                extractionTotalTimeMs += timeMs
                lastExtractionTime = timeMs
            }
        }
        fun recordCorruption() { synchronized(this) { cacheCorruptionsDetected++ } }
        fun recordEviction() { synchronized(this) { evictions++ } }

        fun reset() {
            synchronized(this) {
                hits = 0L
                misses = 0L
                extractionCount = 0L
                extractionTotalTimeMs = 0L
                lastExtractionTime = 0L
                cacheCorruptionsDetected = 0L
                evictions = 0L
            }
        }

        fun getSummary(): String {
            return """
                ╔═════════════════════════════════════════════════════════╗
                ║          CACHE HEALTH METRICS                            ║
                ╠═════════════════════════════════════════════════════════╣
                ║  Cache Hits          : $hits                              ║
                ║  Cache Misses        : $misses                            ║
                ║  Hit Rate            : ${String.format("%.2f", getHitRate())}%                        ║
                ╠═════════════════════════════════════════════════════════╣
                ║  Extrações           : $extractionCount                   ║
                ║  Tempo médio         : ${getAverageExtractionTime()}ms                         ║
                ║  Última extração     : $lastExtractionTime ms                       ║
                ╠═════════════════════════════════════════════════════════╣
                ║  Corrupções          : $cacheCorruptionsDetected                  ║
                ║  Evictions           : $evictions                           ║
                ╚═════════════════════════════════════════════════════════╝
            """.trimIndent()
        }
    }

    /**
     * Inicializa o ThumbnailCacheManager com o contexto da aplicação.
     * Deve ser chamado no Application.onCreate()
     * Thread-safe usando @Volatile + Mutex
     */
    suspend fun init(context: Context) {
        initLock.withLock {
            if (appContext != null) {
                return@withLock
            }
            
            appContext = context.applicationContext
            
        }
    }
    
    /**
     * Versão síncrona para compatibilidade (chamada no Application.onCreate)
     * Usa runBlocking para garantir inicialização síncrona
     */
    fun initSync(context: Context) {
        kotlinx.coroutines.runBlocking {
            init(context)
        }
    }
    
    /**
     * Configura o ThumbnailStripManager com as dimensões corretas.
     * Deve ser chamado antes de extrair strips para um novo vídeo.
     *
     * ✅ MELHORIA: Usa as dimensões exatas fornecidas para evitar
     * problemas de extração e cache miss desnecessário.
     */
    fun configureStripManager(
        thumbWidth: Int,
        thumbHeight: Int,
        thumbsPerStrip: Int
    ) {
        val context = appContext ?: throw IllegalStateException("ThumbnailCacheManager not initialized")

        stripManager = ThumbnailStripManager(
            context = context,
            thumbWidth = thumbWidth,
            thumbHeight = thumbHeight,
            thumbsPerStrip = thumbsPerStrip,
            adaptiveStrips = false
        )

    }

    /**
     * Garante que o cache está inicializado
     */
    private fun ensureInitialized() {
        if (appContext == null) {
            throw IllegalStateException("ThumbnailCacheManager not initialized. Call init() first.")
        }
    }

    /**
     * Obtém ou extrai uma strip de forma inteligente usando cache-aside.
     * 
     * Fluxo: 1. Memória LRU → 2. Disco → 3. Extração
     * 
     * Resolve Problema 6: Cache persiste ao navegar
     * Resolve Problema 9: LRU implementado no cache de memória
     * 
     * @param uri URI do vídeo
     * @param segmentIndex Índice do segmento
     * @param durationMs Duração do vídeo em ms
     * @param thumbWidth Largura da thumbnail
     * @param thumbHeight Altura da thumbnail
     * @param thumbsPerStrip Thumbs por strip
     * @return Bitmap da strip ou null se falhar
     */
    suspend fun getStrip(
        uri: Uri,
        segmentIndex: Int,
        durationMs: Long,
        thumbWidth: Int,
        thumbHeight: Int,
        thumbsPerStrip: Int,
        onlyFirstFrame: Boolean = false
    ): Bitmap? {
        ensureInitialized()

        // ✅ OTIMIZAÇÃO: Configurar stripManager APENAS se realmente necessário
        val needsNewStripManager = stripManager == null ||
            stripManager!!.thumbWidth != thumbWidth ||
            stripManager!!.thumbHeight != thumbHeight ||
            (!onlyFirstFrame && stripManager!!.thumbsPerStrip != thumbsPerStrip)

        if (needsNewStripManager) {
            // Se for overview, não precisamos mudar o thumbsPerStrip do manager fixo
            val targetThumbsPerStrip = if (onlyFirstFrame) 1 else thumbsPerStrip
            configureStripManager(thumbWidth, thumbHeight, targetThumbsPerStrip)
        }

        val uriString = uri.toString()
        val positionKey = if (onlyFirstFrame) (segmentIndex.toLong() or (1L shl 32)) else segmentIndex.toLong()

        val startTime = System.currentTimeMillis()

        // Single lookup via getOrPut (isRecycled check is inside ThumbnailCache.get)
        return try {
            val result = memoryCache.get(uriString, positionKey)
            if (result != null) {
                metrics.recordHit()
                result
            } else {
                metrics.recordMiss()

                val strip = stripManager!!.extractSegment(uri, segmentIndex, durationMs, onlyFirstFrame)
                    ?: run {
                        throw NoSuchElementException("Failed to extract segment $segmentIndex")
                    }

                val extractionTime = System.currentTimeMillis() - startTime
                metrics.recordExtraction(extractionTime)

                memoryCache.put(uriString, positionKey, strip)
                strip
            }
        } catch (e: NoSuchElementException) {
            null
        }
    }

    /**
     * Carrega uma strip específica com tracking para cancelamento inteligente.
     *
     * Resolve Problema 3: Cancelamento inteligente ao pular scroll
     */
    suspend fun loadStripWithTracking(
        uri: Uri,
        segmentIndex: Int,
        durationMs: Long,
        thumbWidth: Int,
        thumbHeight: Int,
        thumbsPerStrip: Int,
        onResult: (Bitmap?) -> Unit
    ) {
        ensureInitialized()
        
        val jobKey = "${uri}_${segmentIndex}"
        
        // Cancelar job anterior para o mesmo segmento
        jobsLock.withLock {
            segmentJobs[jobKey]?.cancel()
            segmentJobs.remove(jobKey)
        }
        
        val job = scope.launch {
            try {
                val strip = getStrip(uri, segmentIndex, durationMs, thumbWidth, thumbHeight, thumbsPerStrip, false)
                
                withContext(Dispatchers.Main) {
                    onResult(strip)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
            } catch (e: Exception) {
            } finally {
                withContext(Dispatchers.Main + NonCancellable) {
                    jobsLock.withLock {
                        segmentJobs.remove(jobKey)
                    }
                }
            }
        }
        
        jobsLock.withLock {
            segmentJobs[jobKey] = job
        }
    }
    
    /**
     * Cancela todos os jobs ativos para um vídeo específico.
     * 
     * Resolve Problema 2: Jobs persistem ao navegar (não cancelam)
     */
    fun cancelJobsForUri(uri: Uri) {
        
        scope.launch {
            jobsLock.withLock {
                val uriKey = uri.toString()
                videoJobs[uriKey]?.cancel()
                videoJobs.remove(uriKey)
                val segmentsToCancel = segmentJobs.keys.filter { it.startsWith("${uri}_") }
                segmentsToCancel.forEach { key ->
                    segmentJobs[key]?.cancel()
                    segmentJobs.remove(key)
                }
            }
        }
    }
    
    /**
     * Cancela jobs que estão muito distantes do segmento atual.
     * 
     * Resolve Problema 3: Cancelamento inteligente ao pular scroll
     * Resolve Problema 12: Estratégia radial não cancela jobs
     * 
     * @param uri URI do vídeo
     * @param currentSegment Segmento atualmente visível
     * @param threshold Distância máxima de segmentos para não cancelar
     */
    fun cancelFarJobs(uri: Uri, currentSegment: Int, threshold: Int = 5) {
        
        scope.launch {
            jobsLock.withLock {
                val uriPrefix = "${uri}_"
                val jobsToCancel = segmentJobs.keys
                    .filter { it.startsWith(uriPrefix) }
                    .mapNotNull { key ->
                        key.removePrefix(uriPrefix).toIntOrNull()?.let { idx -> key to idx }
                    }
                    .filter { (_, segIdx) -> abs(segIdx - currentSegment) > threshold }
                if (jobsToCancel.isNotEmpty()) {
                }
                jobsToCancel.forEach { (key, _) ->
                    segmentJobs[key]?.cancel()
                    segmentJobs.remove(key)
                }
            }
        }
    }
    
    /**
     * Inicia pré-carregamento de strips em background.
     * 
     * Resolve Problema 2: Jobs persistem mesmo se o ViewModel for destruído
     */
    suspend fun startPreload(
        uri: Uri,
        durationMs: Long,
        thumbWidth: Int,
        thumbHeight: Int,
        thumbsPerStrip: Int,
        segmentCount: Int,
        initialSegments: Int = 5
    ): Map<Int, Bitmap> {
        ensureInitialized()
        
        // Configurar stripManager
        configureStripManager(thumbWidth, thumbHeight, thumbsPerStrip)
        
        // Cancelar jobs anteriores para o mesmo vídeo de forma síncrona
        jobsLock.withLock {
            val uriKey = uri.toString()
            videoJobs[uriKey]?.cancel()
            videoJobs.remove(uriKey)
            
            val segmentsToCancel = segmentJobs.keys.filter { it.startsWith("${uri}_") }
            segmentsToCancel.forEach { key ->
                segmentJobs[key]?.cancel()
                segmentJobs.remove(key)
            }
        }
        
        val strips = mutableMapOf<Int, Bitmap>()

        // Carregar segmentos iniciais prioritários de forma PARALELA
        val preloadStartTime = System.currentTimeMillis()

        // Usar coroutineScope para criar escopo estruturado para async
        coroutineScope {
            // Lançar todas as extrações em paralelo usando async
            val jobs = (0 until minOf(initialSegments, segmentCount)).map { segIdx ->
                async(Dispatchers.IO) {
                    try {
                        val strip = getStrip(uri, segIdx, durationMs, thumbWidth, thumbHeight, thumbsPerStrip, false)
                        if (strip != null) {
                            segIdx to strip
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            // Aguardar conclusão de todas as extrações e adicionar ao mapa
            jobs.awaitAll().filterNotNull().forEach { (idx, bitmap) ->
                strips[idx] = bitmap
            }
        }

        val preloadElapsedMs = System.currentTimeMillis() - preloadStartTime
        val avgTimePerStrip = if (strips.isNotEmpty()) preloadElapsedMs / strips.size else 0
        
        return strips
    }
    
    /**
     * Obtém estatísticas combinadas do cache.
     * 
     * Resolve Problema 4: Estatísticas detalhadas para monitoramento
     */
    fun getStats(): CacheStats {
        val memStats = memoryCache.getStats()
        
        return CacheStats(
            memoryCacheSize = memStats.size,
            memoryCacheMaxSize = memStats.maxSize,
            memoryCacheHitRate = memStats.hitRate,
            memoryCacheHits = memStats.hits,
            memoryCacheMisses = memStats.misses,
            activeVideoJobsCount = videoJobs.size,
            activeSegmentJobsCount = segmentJobs.size
        )
    }
    
    /**
     * Loga estatísticas detalhadas do cache para debug.
     */
    fun logStats() {
        val stats = getStats()
        val memStats = memoryCache.getStats()
        
    }
    
    /**
     * Limpa cache de memória LRU.
     */
    fun clearMemoryCache() {
        memoryCache.clear()
    }
    
    /**
     * Limpa todo o cache (disco e memória).
     */
    fun clearAll() {
        appContext?.let { ThumbnailStripManager.clearCache(it) }
        clearMemoryCache()
    }

    /**
     * Limpa todo o cache (disco e memória) com um context específico.
     * Útil para testes onde um context diferente pode ser necessário.
     */
    fun clearAll(context: Context) {
        ThumbnailStripManager.clearCache(context)
        clearMemoryCache()
    }

    /**
     * Estatísticas do cache manager.
     */
    data class CacheStats(
        val memoryCacheSize: Int,
        val memoryCacheMaxSize: Int,
        val memoryCacheHitRate: Float,
        val memoryCacheHits: Int,
        val memoryCacheMisses: Int,
        val activeVideoJobsCount: Int,
        val activeSegmentJobsCount: Int
    )

    // ========== CACHE HEALTH METRICS API ==========

    /**
     * Retorna o resumo das métricas de cache health.
     * Útil para debugging e monitoramento.
     */
    fun getCacheHealthSummary(): String = metrics.getSummary()

    /**
     * Imprime as métricas de cache health no log.
     * Usa logcat tag "CacheMetrics" para fácil filtragem.
     */
    fun logCacheHealth() {
    }

    /**
     * Retorna o hit rate atual do cache em porcentagem (0.0 - 100.0).
     */
    fun getHitRate(): Double = metrics.getHitRate()

    /**
     * Retorna o tempo médio de extração em ms.
     */
    fun getAverageExtractionTime(): Long = metrics.getAverageExtractionTime()

    /**
     * Retorna o número total de cache hits.
     */
    fun getTotalHits(): Long = metrics.hits

    /**
     * Retorna o número total de cache misses.
     */
    fun getTotalMisses(): Long = metrics.misses

    /**
     * Retorna o número de corrupções de cache detectadas.
     */
    fun getCorruptionCount(): Long = metrics.cacheCorruptionsDetected

    /**
     * Retorna o número de evictions que ocorreram.
     */
    fun getEvictionCount(): Long = metrics.evictions

    /**
     * Retorna o tamanho atual do cache de memória (número de itens).
     */
    fun getCacheSize(): Int = memoryCache.size()

    /**
     * Retorna o uso de memória do cache em bytes.
     */
    fun getMemoryUsage(): Long = memoryCache.totalSizeBytes()

    /**
     * Reseta todas as métricas.
     * Útil para começar uma nova sessão de medição.
     */
    fun resetMetrics() {
        metrics.reset()
    }

    /**
     * Verifica rapidamente se um segmento específico está no cache (memória ou disco).
     */
    fun isSegmentCached(uri: Uri, segmentIndex: Int): Boolean {
        val uriString = uri.toString()
        val positionKey = segmentIndex.toLong()
        
        // 1. Check memory cache
        if (memoryCache.get(uriString, positionKey) != null) return true
        
        // 2. Check disk cache
        try {
            // Se o stripManager não estiver configurado, não podemos garantir a chave correta
            // mas podemos tentar com o stripManager atual se ele existir
            val manager = stripManager ?: return false
            val cacheKey = "strip_v${com.chopcut.config.constants.ThumbnailConfig.Cache.CACHE_VERSION}_${manager.getFileIdentifier(uri)}_${segmentIndex}.webp"
            val dir = File(appContext?.cacheDir, "thumbnail_strips")
            return File(dir, cacheKey).exists()
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Verifica se o vídeo tem pelo menos o número inicial de strips em cache.
     */
    fun hasInitialStripsCached(uri: Uri, segmentCount: Int, initialCount: Int = 6): Boolean {
        val countToCheck = minOf(segmentCount, initialCount)
        if (countToCheck <= 0) return false
        
        // Se pelo menos metade dos iniciais estiverem em cache, consideramos "cached" para UX
        var cachedCount = 0
        for (i in 0 until countToCheck) {
            if (isSegmentCached(uri, i)) cachedCount++
        }
        return cachedCount >= (countToCheck / 2).coerceAtLeast(1)
    }

    /**
     * Registra uma corrupção de cache detectada.
     * Chamado internamente pelo ThumbnailStripManager ao encontrar arquivos corrompidos.
     */
    fun recordCorruption() {
        metrics.recordCorruption()
    }

    /**
     * Registra um eviction do cache LRU.
     * Chamado internamente pelo ThumbnailCache ao remover itens.
     */
    fun recordEviction() {
        metrics.recordEviction()
    }
}
