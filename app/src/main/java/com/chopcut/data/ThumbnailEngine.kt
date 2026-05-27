package com.chopcut

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.pow
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber


// --- Merged from FilePerformanceLogger.kt ---


/**
 * Persiste eventos de performance em um arquivo CSV para análise posterior.
 */
class FilePerformanceLogger(private val context: Context) {
    private val logFile: File by lazy {
        File(context.filesDir, "performance_telemetry.csv").apply {
            if (!exists()) {
                writeText("timestamp,stage,taskId,durationMs,queueSize,workerId\n", StandardCharsets.UTF_8)
            }
        }
    }

    /**
     * Adiciona um evento ao arquivo de log
     */
    fun logEvent(event: PerformanceEvent) {
        try {
            val line = "${event.timestamp},${event.stage},${event.taskId},${event.durationMs},${event.queueSize},${event.workerId}\n"
            FileOutputStream(logFile, true).use { out ->
                out.write(line.toByteArray(StandardCharsets.UTF_8))
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Limpa o arquivo de log
     */
    fun clearLogs() {
        if (logFile.exists()) {
            logFile.delete()
        }
    }
}

// --- Merged from OptimizedThumbnailProvider.kt ---


/**
 * Provedor de thumbnails otimizado para a timeline do editor.
 * 
 * Implementa:
 * - Quantização de timestamp (500ms)
 * - Cache LRU baseado em timestamp
 * - Fila de extração com prioridade (VISIBLE > PREFETCH > DISTANT)
 * - Pool de threads limitado (2 threads)
 * - Cancelamento de requests obsoletos
 * - Batching de atualizações (via Flow)
 */
class OptimizedThumbnailProvider(
    private val context: Context,
    private val cache: ThumbnailCache = ThumbnailCache(200), // Cache aumentado para timeline longa
    private val thumbWidth: Int = 120,
    private val thumbHeight: Int = 120
) {
    companion object {
        private const val QUANTIZATION_INTERVAL_MS = 500L
        private const val THREAD_POOL_SIZE = 2
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val extractorBatch = ThumbnailExtractorBatch(context)
    private val requestQueue = PriorityBlockingQueue<ThumbnailRequest>()
    private val executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)

    // Flow para emitir atualizações de thumbnails (UI batching)
    private val _thumbnailUpdates = MutableSharedFlow<Pair<Long, Bitmap>>(
        extraBufferCapacity = 64,
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val thumbnailUpdates: SharedFlow<Pair<Long, Bitmap>> = _thumbnailUpdates

    init {
        Log.i("ChopCut", "[PROVIDER] init START, starting ${THREAD_POOL_SIZE} workers")
        repeat(THREAD_POOL_SIZE) {
            executor.execute {
                Log.i("ChopCut", "[PROVIDER] worker LOOP starting on ${Thread.currentThread().name}")
                workerLoop()
            }
        }
        Log.i("ChopCut", "[PROVIDER] init DONE, workers submitted")
    }

    // RESTRIÇÃO ARQUITETURAL CRÍTICA: O cache deve permanecer OBRIGATORIAMENTE desativado (cacheEnabled = false).
    // Requisito oficial do projeto para evitar "falsos positivos" durante os testes e perfilamento
    // da performance crua de extração das miniaturas (fotos) do vídeo. NÃO alterar para true.
    private val cacheEnabled = false

    /**
     * Solicita um thumbnail para uma posição específica.
     * Retorna do cache se disponível, caso contrário adiciona à fila.
     */
    fun requestThumbnail(uri: Uri, positionMs: Long, priority: ThumbnailPriority) {
        val quantizedTime = quantizeTimestamp(positionMs)

        val cached = if (cacheEnabled) cache.get(uri.toString(), quantizedTime) else null
        if (cached != null) {
            scope.launch { _thumbnailUpdates.emit(quantizedTime to cached) }
            return
        }

        val request = ThumbnailRequest(
            uri = uri,
            timestamp = quantizedTime,
            priority = priority,
            width = thumbWidth,
            height = thumbHeight,
            callback = { bitmap ->
                if (cacheEnabled) {
                    cache.put(uri.toString(), quantizedTime, bitmap)
                }
                scope.launch { _thumbnailUpdates.emit(quantizedTime to bitmap) }
            }
        )
        if (!requestQueue.contains(request)) {
            requestQueue.add(request)
        }
    }

    /**
     * Realiza prefetch de uma janela de timestamps.
     */
    fun prefetch(uri: Uri, startMs: Long, endMs: Long) {
        var current = quantizeTimestamp(startMs)
        val limit = quantizeTimestamp(endMs)
        
        while (current <= limit) {
            requestThumbnail(uri, current, ThumbnailPriority.PREFETCH)
            current += QUANTIZATION_INTERVAL_MS
        }
    }

    /**
     * Limpa a fila de extração (útil em scroll rápido).
     */
    suspend fun clearQueue() {
        requestQueue.clear()
    }

    /**
     * Quantiza o timestamp para intervalos de 500ms para maximizar reuso.
     */
    private fun quantizeTimestamp(time: Long): Long {
        return (time / QUANTIZATION_INTERVAL_MS) * QUANTIZATION_INTERVAL_MS
    }

    /**
     * Loop principal de extração executado em threads do pool.
     */
    private fun workerLoop() {
        Log.i("ChopCut", "[PROVIDER] workerLoop started on ${Thread.currentThread().name}")
        while (!Thread.currentThread().isInterrupted) {
            try {
                val request = requestQueue.take()
                Log.i("ChopCut", "[PROVIDER] worker got request ts=${request.timestamp} prio=${request.priority.name}")
                
                val bitmap = runBlockingExtract(request)
                
                if (bitmap != null) {
                    val optimizedBitmap = if (bitmap.config != Bitmap.Config.RGB_565) {
                        val copy = bitmap.copy(Bitmap.Config.RGB_565, false)
                        bitmap.recycle()
                        copy
                    } else {
                        bitmap
                    }

                    request.callback(optimizedBitmap)
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    /**
     * Extração síncrona para o worker loop.
     */
    private fun runBlockingExtract(request: ThumbnailRequest): Bitmap? {
        return kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                extractorBatch.extractSingle(
                    uri = request.uri,
                    positionMs = request.timestamp,
                    width = request.width,
                    height = request.height
                )
            }
        }
    }

    /**
     * Encerra o provedor e libera recursos.
     */
    fun release() {
        executor.shutdownNow()
        scope.coroutineContext.cancelChildren()
        cache.clear()
        requestQueue.clear()
    }
}

// --- Merged from PerformanceMonitor.kt ---


/**
 * Monitor de performance global para o pipeline de processamento.
 * Coleta eventos de telemetria e calcula métricas em tempo real.
 */
object PerformanceMonitor {
    private val events = ConcurrentLinkedQueue<PerformanceEvent>()
    private var lastCalculationTime = System.currentTimeMillis()
    private var logger: FilePerformanceLogger? = null

    /**
     * Inicializa o monitor com contexto p/ persistência
     */
    fun init(context: Context) {
        logger = FilePerformanceLogger(context)
    }

    /**
     * Registra um novo evento de telemetria
     */
    fun log(event: PerformanceEvent) {
        events.add(event)
        
        // Persistência em arquivo
        logger?.logEvent(event)
        
    }

    private var previousAverageQueueSize = 0f

    /**
     * Calcula métricas da janela atual (eventos desde o último cálculo)
     */
    fun calculateMetrics(): PerformanceMetrics {
        val now = System.currentTimeMillis()
        val durationMs = now - lastCalculationTime
        val durationSec = durationMs / 1000f
        
        val currentEvents = mutableListOf<PerformanceEvent>()
        while (events.isNotEmpty()) {
            events.poll()?.let { currentEvents.add(it) }
        }
        
        lastCalculationTime = now

        if (currentEvents.isEmpty()) {
            return PerformanceMetrics(0f, emptyMap(), emptyMap())
        }

        // Throughput: frames processados (contamos o estágio PROCESS como conclusão básica para o Batch)
        val completedTasks = currentEvents.count { it.stage == ExtractionStage.PROCESS }
        val throughput = if (durationSec > 0.001f) completedTasks / durationSec else completedTasks.toFloat()

        // Durações por estágio
        val avgDurations = currentEvents.groupBy { it.stage }
            .mapValues { (_, stageEvents) -> 
                stageEvents.map { it.durationMs }.average().toFloat() 
            }

        val maxDurations = currentEvents.groupBy { it.stage }
            .mapValues { (_, stageEvents) -> 
                stageEvents.maxOfOrNull { it.durationMs } ?: 0L
            }

        // Detecção de gargalo (estágio com maior duração média)
        val bottleneck = avgDurations.maxByOrNull { it.value }?.key

        // Diagnóstico de fila
        val avgQueueSize = currentEvents.map { it.queueSize.toFloat() }.average().toFloat()
        val isQueueSaturated = avgQueueSize > 5 && avgQueueSize >= previousAverageQueueSize
        previousAverageQueueSize = avgQueueSize

        val metrics = PerformanceMetrics(throughput, avgDurations, maxDurations, bottleneck)
        
        return metrics
    }

    /**
     * Limpa o histórico de eventos
     */
    @Synchronized
    fun clear() {
        events.clear()
        lastCalculationTime = System.currentTimeMillis()
    }

    /**
     * Retorna um resumo formatado para inserção no DuckDB ou registro histórico.
     * Útil para persistência centralizada.
     */
    fun getSummaryJson(): String {
        val metrics = calculateMetrics()
        val durations = metrics.avgDurationMs.map { "\"${it.key}\": ${it.value}" }.joinToString(",")
        
        return """
            {
                "timestamp": ${System.currentTimeMillis()},
                "throughput_fps": ${"%.2f".format(metrics.throughput)},
                "bottleneck": "${metrics.bottleneckStage ?: "NONE"}",
                "avg_durations": { $durations }
            }
        """.trimIndent()
    }
}

// --- Merged from ThumbnailCache.kt ---


/**
 * Cache LRU (Least Recently Used) para thumbnails de timeline
 *
 * Armazena bitmaps em memória com limite máximo de itens.
 * Quando o limite é atingido, os itens menos usados são removidos.
 * 
 * Otimizado: Usa ReentrantReadWriteLock para leituras paralelas.
 * 
 * Implementa o padrão Cache-Aside para carregamento eficiente.
 */
class ThumbnailCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE
) {
    private val cache = LinkedHashMap<String, Bitmap>(maxSize, 0.75f, true)
    private val lock = ReentrantReadWriteLock()
    
    private var cacheHits = 0
    private var cacheMisses = 0

    companion object {
        const val DEFAULT_MAX_SIZE = 50 // Número máximo de thumbnails em cache
    }

    /**
     * Gera uma chave única para o cache baseada na URI e posição
     */
    private fun generateKey(uri: String, positionMs: Long): String {
        return "${uri}_${positionMs}"
    }

    /**
     * Obtém um thumbnail do cache
     *
     * @param uri URI do vídeo
     * @param positionMs Posição em milissegundos
     * @return Bitmap se encontrado no cache, null caso contrário
     */
    fun get(uri: String, positionMs: Long): Bitmap? {
        val key = generateKey(uri, positionMs)
        lock.readLock().lock()
        try {
            val bitmap = cache[key]
            return when {
                bitmap == null -> { cacheMisses++; null }
                bitmap.isRecycled -> {
                    // Sair do read lock e entrar no write lock para remover bitmap reciclado
                    lock.readLock().unlock()
                    lock.writeLock().lock()
                    try {
                        cache.remove(key)
                        cacheMisses++
                        null
                    } finally {
                        lock.writeLock().unlock()
                    }
                }
                else -> { cacheHits++; bitmap }
            }
        } finally {
            if (lock.readLock().tryLock()) {
                lock.readLock().unlock()
            }
        }
    }

    /**
     * Adiciona um thumbnail ao cache
     *
     * @param uri URI do vídeo
     * @param positionMs Posição em milissegundos
     * @param bitmap Bitmap a ser armazenado
     */
    fun put(uri: String, positionMs: Long, bitmap: Bitmap) {
        val key = generateKey(uri, positionMs)
        lock.writeLock().lock()
        try {
            if (cache.size >= maxSize && !cache.containsKey(key)) {
                cache.remove(cache.keys.first())
            }
            cache[key] = bitmap
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Obtém um thumbnail do cache ou executa o provider para gerar um novo.
     * Implementa o padrão Cache-Aside.
     * 
     * Resolve Problema 4: Pattern cache-aside para carregamento inteligente
     * 
     * @param uri URI do vídeo
     * @param positionMs Posição em milissegundos
     * @param provider Função para gerar o bitmap se não estiver no cache
     * @return Bitmap (do cache ou gerado pelo provider)
     */
    suspend fun getOrPut(uri: String, positionMs: Long, provider: suspend () -> Bitmap): Bitmap {
        val cached = get(uri, positionMs)
        if (cached != null) {
            return cached
        }
        
        val bitmap = provider()
        put(uri, positionMs, bitmap)
        return bitmap
    }

    /**
     * Obtém estatísticas do cache para debug e monitoramento.
     * 
     * Resolve Problema 4: Estatísticas de hit rate para monitoramento
     * 
     * @return CacheStats com estatísticas atuais
     */
    fun getStats(): CacheStats {
        lock.readLock().lock()
        try {
            val total = cacheHits + cacheMisses
            val hitRate = if (total > 0) (cacheHits.toFloat() / total * 100) else 0f
            return CacheStats(size = cache.size, maxSize = maxSize, hits = cacheHits, misses = cacheMisses, hitRate = hitRate)
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Limpa todos os itens do cache e estatísticas.
     */
    fun clear() {
        lock.writeLock().lock()
        try {
            cache.clear()
            cacheHits = 0
            cacheMisses = 0
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Retorna o número atual de itens no cache
     */
    fun size(): Int {
        lock.readLock().lock()
        try {
            return cache.size
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Verifica se o cache contém uma chave específica
     */
    fun contains(uri: String, positionMs: Long): Boolean {
        lock.readLock().lock()
        try {
            return cache.containsKey(generateKey(uri, positionMs))
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Remove um item específico do cache
     */
    fun remove(uri: String, positionMs: Long) {
        lock.writeLock().lock()
        try {
            cache.remove(generateKey(uri, positionMs))
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Verifica se existe alguma entrada em cache para o URI informado.
     * Útil para saber se um vídeo já foi processado antes de iniciar extração.
     */
    fun containsVideo(uri: String): Boolean {
        lock.readLock().lock()
        try {
            return cache.keys.any { it.startsWith("${uri}_") }
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Remove todas as entradas de cache associadas a um URI de vídeo.
     * @return Número de entradas removidas.
     */
    fun removeVideo(uri: String): Int {
        lock.writeLock().lock()
        try {
            val keys = cache.keys.filter { it.startsWith("${uri}_") }
            keys.forEach { cache.remove(it) }
            return keys.size
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Retorna o conjunto de URIs distintos que possuem entradas no cache.
     * Permite auditar quais vídeos estão ocupando cache e evitar uso de cache errado.
     * Chave interna: "${uri}_${positionMs}" — extraímos o URI removendo o sufixo numérico.
     */
    fun getTrackedUris(): Set<String> {
        lock.readLock().lock()
        try {
            return cache.keys.map { it.substringBeforeLast("_") }.toSet()
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Verifica se a capacidade do cache está igual ou acima do threshold informado.
     * @param thresholdPercent Percentual de uso para considerar "próximo do limite" (padrão: 80%)
     */
    fun isNearCapacity(thresholdPercent: Int = 80): Boolean {
        lock.readLock().lock()
        try {
            return cache.size * 100 >= maxSize * thresholdPercent
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Calcula o tamanho total em bytes de todos os bitmaps em cache.
     * Usa [Bitmap.byteCount] que reflete a alocação real (RGB_565=2b/px, ARGB_8888=4b/px).
     */
    fun totalSizeBytes(): Long {
        lock.readLock().lock()
        try {
            return cache.values.sumOf { if (it.isRecycled) 0L else it.byteCount.toLong() }
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Retorna o tamanho total formatado em B, KB, MB ou GB.
     */
    fun totalSizeFormatted(): String {
        val bytes = totalSizeBytes()
        return when {
            bytes < 1_024L -> "${bytes}B"
            bytes < 1_024L * 1_024 -> "${"%.1f".format(bytes / 1_024.0)}KB"
            bytes < 1_024L * 1_024 * 1_024 -> "${"%.1f".format(bytes / (1_024.0 * 1_024))}MB"
            else -> "${"%.1f".format(bytes / (1_024.0 * 1_024 * 1_024))}GB"
        }
    }

    /**
     * Limpa o cache de forma segura: captura estatísticas e chama [onBeforeClear] antes de apagar.
     * Garante que nenhuma informação se perde sem registro.
     * @param onBeforeClear Callback com stats do estado atual, chamado antes da limpeza.
     * @return Estatísticas capturadas antes da limpeza.
     */
    fun clearSafely(onBeforeClear: (CacheStats) -> Unit = {}): CacheStats {
        val statsBefore = getStats()
        onBeforeClear(statsBefore)
        clear()
        return statsBefore
    }

    /**
     * Estatísticas do cache LRU.
     */
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hits: Int,
        val misses: Int,
        val hitRate: Float
    ) {
        fun toLogString(): String {
            return """
                ╔═════════════════════════════════════════════════════════╗
                ║              THUMBNAIL CACHE LRU - STATS               ║
                ╚═════════════════════════════════════════════════════════╝
                
                📊 TAMANHO:
                   • Itens em cache: $size
                   • Limite máximo: $maxSize
                   • Utilização: ${size * 100 / maxSize}%
                
                🎯 PERFORMANCE:
                   • Cache hits: $hits
                   • Cache misses: $misses
                   • Hit rate: ${String.format("%.2f", hitRate)}%
                ╚═════════════════════════════════════════════════════════╝
            """.trimIndent()
        }
    }
}

// --- Merged from ThumbnailCacheManager.kt ---


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

    // RESTRIÇÃO ARQUITETURAL CRÍTICA: O cache deve permanecer OBRIGATORIAMENTE desativado (cacheEnabled = false).
    // Requisito oficial do projeto para evitar "falsos positivos" durante os testes e perfilamento
    // da performance crua de extração dos strips de vídeo. NÃO alterar para true.
    private const val cacheEnabled = false
    
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
            val result = if (cacheEnabled) memoryCache.get(uriString, positionKey) else null
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

                if (cacheEnabled) {
                    memoryCache.put(uriString, positionKey, strip)
                }
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
        if (!cacheEnabled) return false
        val uriString = uri.toString()
        val positionKey = segmentIndex.toLong()
        
        // 1. Check memory cache
        if (memoryCache.get(uriString, positionKey) != null) return true
        
        // 2. Check disk cache
        try {
            // Se o stripManager não estiver configurado, não podemos garantir a chave correta
            // mas podemos tentar com o stripManager atual se ele existir
            val manager = stripManager ?: return false
            val cacheKey = "strip_v${ThumbnailConfig.Cache.CACHE_VERSION}_${manager.getFileIdentifier(uri)}_${segmentIndex}.webp"
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

// --- Merged from ThumbnailExtractor.kt ---


/**
 * Extracts thumbnails from video for timeline preview
 */
class ThumbnailExtractor(
    private val context: Context
) {

    /**
     * Extract a single thumbnail from specific position
     * @param uri Video URI
     * @param positionMs Position in milliseconds
     * @param width Target thumbnail width
     * @param height Target thumbnail height
     * @param quality Quality of the extraction
     * @return Bitmap thumbnail or null if extraction fails
     */
    suspend fun extractAt(
        uri: Uri,
        positionMs: Long,
        width: Int,
        height: Int,
        quality: ThumbnailQuality = ThumbnailQuality.HIGH
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.setDataSource(context, uri)
            } else {
                @Suppress("DEPRECATION")
                retriever.setDataSource(uri.toString())
            }

            // Get frame at position
            // For HIGH quality, we extract slightly larger and scale down with filtering (Anti-Aliasing)
            val extractWidth = if (quality == ThumbnailQuality.HIGH) (width * ThumbnailConfig.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else width
            val extractHeight = if (quality == ThumbnailQuality.HIGH) (height * ThumbnailConfig.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else height

            val rawFrame = retriever.getScaledFrameAtTime(
                positionMs * 1000, // Convert to microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                extractWidth,
                extractHeight
            )

            val frame = if (quality == ThumbnailQuality.HIGH && rawFrame != null && (rawFrame.width != width || rawFrame.height != height)) {
                // Criar bitmap de destino no tamanho exato solicitado
                val result = Bitmap.createBitmap(width, height, rawFrame.config ?: Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(result)
                
                // Calcular área de crop centralizado
                val srcWidth = rawFrame.width
                val srcHeight = rawFrame.height
                val srcAspect = srcWidth.toFloat() / srcHeight
                val dstAspect = width.toFloat() / height

                val (cropWidth, cropHeight) = if (srcAspect > dstAspect) {
                    // Source é mais largo que o destino (e.g. 16:9 -> 1:1)
                    (srcHeight * dstAspect).toInt() to srcHeight
                } else {
                    // Source é mais alto que o destino (e.g. 9:16 -> 1:1)
                    srcWidth to (srcWidth / dstAspect).toInt()
                }

                val cropX = (srcWidth - cropWidth) / 2
                val cropY = (srcHeight - cropHeight) / 2

                val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropWidth, cropY + cropHeight)
                val dstRect = android.graphics.Rect(0, 0, width, height)

                // Desenhar com filtro bilinear para anti-aliasing
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(rawFrame, srcRect, dstRect, paint)
                
                rawFrame.recycle()
                result
            } else {
                rawFrame
            }

            frame?.also {
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Extract a thumbnail and save it to a file
     */
    suspend fun extractToFile(
        uri: Uri,
        destFile: java.io.File,
        width: Int = ThumbnailConfig.Dimensions.DEFAULT_WIDTH,
        height: Int = ThumbnailConfig.Dimensions.DEFAULT_HEIGHT,
        rotation: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        var bitmap = extractAt(uri, 0, width, height) // Extract at start (0ms)

        if (bitmap != null) {
            try {
                // Apply rotation if needed
                if (rotation % 360 != 0) {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(rotation.toFloat())
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    if (rotatedBitmap != bitmap) {
                        bitmap.recycle() // Release original if rotated is different
                        bitmap = rotatedBitmap
                    }
                }

                val startSave = System.currentTimeMillis()
                java.io.FileOutputStream(destFile).use { out ->
                    bitmap?.compress(Bitmap.CompressFormat.JPEG, ThumbnailConfig.Quality.JPEG_COMPRESSION_QUALITY, out)
                }
                val saveDuration = System.currentTimeMillis() - startSave
                PerformanceMonitor.log(PerformanceEvent(
                    stage = ExtractionStage.SAVE,
                    taskId = destFile.name,
                    durationMs = saveDuration
                ))
                true
            } catch (e: Exception) {
                false
            } finally {
                bitmap?.recycle()
            }
        } else {
            false
        }
    }

    /**
     * Extract all thumbnails from video with progress updates
     * @param uri Video URI
     * @param durationMs Video duration in milliseconds
     * @param settings Thumbnail extraction settings
     * @return Flow emitting progress updates
     */
    suspend fun extractAllWithProgress(
        uri: Uri,
        durationMs: Long,
        settings: ThumbnailSettings
    ): Flow<ThumbnailExtractionProgress> = withContext(Dispatchers.IO) {
        callbackFlow {
            val retriever = MediaMetadataRetriever()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.setDataSource(context, uri)
                } else {
                    @Suppress("DEPRECATION")
                    retriever.setDataSource(uri.toString())
                }

                // Calculate interval between thumbnails
                val intervalMs = 1000L / settings.thumbsPerSecond
                val totalThumbnails = (durationMs / intervalMs).toInt()


                // Emit initial progress
                trySend(
                    ThumbnailExtractionProgress(
                        currentIndex = 0,
                        total = totalThumbnails,
                        currentPositionMs = 0
                    )
                )

                var bitmap: Bitmap? = null

                // Extract thumbnails at regular intervals
                for (i in 0 until totalThumbnails) {
                    val positionMs = (i * intervalMs).toLong()

                    try {
                        val quality = settings.extractionQuality
                        val extractWidth = if (quality == ThumbnailQuality.HIGH) (settings.dimensionPreset.width * ThumbnailConfig.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else settings.dimensionPreset.width
                        val extractHeight = if (quality == ThumbnailQuality.HIGH) (settings.dimensionPreset.height * ThumbnailConfig.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else settings.dimensionPreset.height

                        val rawFrame = retriever.getScaledFrameAtTime(
                            positionMs * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            extractWidth,
                            extractHeight
                        )

                        bitmap = if (quality == ThumbnailQuality.HIGH && rawFrame != null && (rawFrame.width != settings.dimensionPreset.width || rawFrame.height != settings.dimensionPreset.height)) {
                            val scaled = Bitmap.createScaledBitmap(rawFrame, settings.dimensionPreset.width, settings.dimensionPreset.height, true)
                            if (scaled != rawFrame) rawFrame.recycle()
                            scaled
                        } else {
                            rawFrame
                        }

                        // Recycle previous bitmap if exists
                        bitmap?.let {
                            // Yield to avoid blocking
                            yield()

                            trySend(
                                ThumbnailExtractionProgress(
                                    currentIndex = i + 1,
                                    total = totalThumbnails,
                                    currentPositionMs = positionMs
                                )
                            )
                        }
                    } catch (e: Exception) {
                    }

                    // Recycle bitmap after use to free memory
                    bitmap?.recycle()
                    bitmap = null
                }

                // Emit completion
                trySend(
                    ThumbnailExtractionProgress(
                        currentIndex = totalThumbnails,
                        total = totalThumbnails,
                        currentPositionMs = durationMs,
                        isComplete = true
                    )
                )


            } catch (e: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                }
            }

            awaitClose {}
        }
    }

    /**
     * Extract all thumbnails from video to directory with progress updates
     * @param uri Video URI
     * @param durationMs Video duration in milliseconds
     * @param outputDir Output directory for thumbnails
     * @param settings Thumbnail extraction settings
     * @return Flow emitting progress updates
     */
    suspend fun extractAllToDirectory(
        uri: Uri,
        durationMs: Long,
        outputDir: File,
        settings: ThumbnailSettings
    ): Flow<ThumbnailExtractionProgress> = withContext(Dispatchers.IO) {
        callbackFlow {
            val retriever = MediaMetadataRetriever()
            try {
                // Create output directory if it doesn't exist
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.setDataSource(context, uri)
                } else {
                    @Suppress("DEPRECATION")
                    retriever.setDataSource(uri.toString())
                }

                // Calculate interval between thumbnails
                val intervalMs = 1000L / settings.thumbsPerSecond
                val totalThumbnails = (durationMs / intervalMs).toInt()


                // Emit initial progress
                trySend(
                    ThumbnailExtractionProgress(
                        currentIndex = 0,
                        total = totalThumbnails,
                        currentPositionMs = 0
                    )
                )

                // Determine file extension and compress format
                val (extension, compressFormat) = when (settings.format) {
                    ThumbnailFormat.JPEG -> ThumbnailConfig.FileFormats.EXT_JPG to Bitmap.CompressFormat.JPEG
                    ThumbnailFormat.PNG -> ThumbnailConfig.FileFormats.EXT_PNG to Bitmap.CompressFormat.PNG
                    ThumbnailFormat.WEBP -> ThumbnailConfig.FileFormats.EXT_WEBP to Bitmap.CompressFormat.WEBP
                }

                var bitmap: Bitmap? = null

                // Extract thumbnails at regular intervals
                for (i in 0 until totalThumbnails) {
                    val positionMs = (i * intervalMs).toLong()
                    val outputFile = File(outputDir, "thumb_${String.format("%05d", i)}$extension")

                    try {
                        val quality = settings.extractionQuality
                        val extractWidth = if (quality == ThumbnailQuality.HIGH) (settings.dimensionPreset.width * ThumbnailConfig.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else settings.dimensionPreset.width
                        val extractHeight = if (quality == ThumbnailQuality.HIGH) (settings.dimensionPreset.height * ThumbnailConfig.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else settings.dimensionPreset.height

                        val rawFrame = retriever.getScaledFrameAtTime(
                            positionMs * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            extractWidth,
                            extractHeight
                        )

                        bitmap = if (quality == ThumbnailQuality.HIGH && rawFrame != null && (rawFrame.width != settings.dimensionPreset.width || rawFrame.height != settings.dimensionPreset.height)) {
                            val scaled = Bitmap.createScaledBitmap(rawFrame, settings.dimensionPreset.width, settings.dimensionPreset.height, true)
                            if (scaled != rawFrame) rawFrame.recycle()
                            scaled
                        } else {
                            rawFrame
                        }

                        // Save bitmap to file
                        bitmap?.let {
                            val startSave = System.currentTimeMillis()
                            java.io.FileOutputStream(outputFile).use { out ->
                                it.compress(compressFormat, settings.quality, out)
                            }
                            val saveDuration = System.currentTimeMillis() - startSave
                            PerformanceMonitor.log(PerformanceEvent(
                                stage = ExtractionStage.SAVE,
                                taskId = outputFile.name,
                                durationMs = saveDuration,
                                queueSize = totalThumbnails - (i + 1)
                            ))


                            // Yield to avoid blocking
                            yield()

                            trySend(
                                ThumbnailExtractionProgress(
                                    currentIndex = i + 1,
                                    total = totalThumbnails,
                                    currentPositionMs = positionMs
                                )
                            )
                        }
                    } catch (e: Exception) {
                    }

                    // Recycle bitmap after use to free memory
                    bitmap?.recycle()
                    bitmap = null
                }

                // Emit completion
                trySend(
                    ThumbnailExtractionProgress(
                        currentIndex = totalThumbnails,
                        total = totalThumbnails,
                        currentPositionMs = durationMs,
                        isComplete = true
                    )
                )


            } catch (e: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                }
            }

            awaitClose {}
        }
    }

    /**
     * Extract a strip of thumbnails evenly distributed across video duration
     * @param uri Video URI
     * @param count Number of thumbnails to extract
     * @param width Thumbnail width
     * @param height Thumbnail height
     * @return List of thumbnails in order
     */
    suspend fun extractStrip(
        uri: Uri,
        count: Int,
        width: Int = 50,
        height: Int =50
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val thumbnails = mutableListOf<Bitmap>()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.setDataSource(context, uri)
            } else {
                @Suppress("DEPRECATION")
                retriever.setDataSource(uri.toString())
            }

            // Get video duration
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            if (durationMs <= 0) {
                return@withContext emptyList()
            }

            // Calculate interval between thumbnails
            val intervalMs = durationMs / (count + 1)


            // Extract thumbnails at regular intervals
            for (i in 1..count) {
                val positionMs = i * intervalMs

                // For strip, we use HIGH quality by default for best visual
                val extractWidth = (width * ThumbnailConfig.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt()
                val extractHeight = (height * ThumbnailConfig.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt()

                val rawFrame = retriever.getScaledFrameAtTime(
                    positionMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    extractWidth,
                    extractHeight
                )

                val frame = rawFrame?.let {
                    val scaled = Bitmap.createScaledBitmap(it, width, height, true)
                    if (scaled != it) it.recycle()
                    scaled
                }

                frame?.let { thumbnails.add(it) }

                if (frame == null) {
                }
            }


        } catch (e: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }

        thumbnails
    }

    /**
     * Extract multiple thumbnails at specific positions
     * @param uri Video URI
     * @param positionsMs List of positions in milliseconds
     * @param width Thumbnail width
     * @param height Thumbnail height
     * @return List of thumbnails matching the input positions
     */
    suspend fun extractAtPositions(
        uri: Uri,
        positionsMs: List<Long>,
        width: Int = 50,
        height: Int = 50
    ): List<Bitmap?> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.setDataSource(context, uri)
            } else {
                @Suppress("DEPRECATION")
                retriever.setDataSource(uri.toString())
            }

            val thumbnails = positionsMs.map { positionMs ->
                val quality = ThumbnailQuality.HIGH // Default to high for positions
                
                // Extract frame at larger size to allow proper center crop
                val extractFactor = if (quality == ThumbnailQuality.HIGH) 2 else 1
                val sourceBitmap = retriever.getScaledFrameAtTime(
                    positionMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    width * extractFactor,
                    height * extractFactor
                )

                val croppedBitmap = sourceBitmap?.let { src ->
                    // Create cropped thumbnail at exact dimensions (center crop)
                    val croppedThumb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(croppedThumb)

                    // Calculate center crop
                    val srcWidth = src.width
                    val srcHeight = src.height
                    val srcAspect = srcWidth.toFloat() / srcHeight
                    val dstAspect = width.toFloat() / height

                    val (cropWidth, cropHeight) = if (srcAspect > dstAspect) {
                        // Source is wider: crop sides
                        ((srcHeight * dstAspect).toInt()) to srcHeight
                    } else {
                        // Source is taller: crop top/bottom
                        srcWidth to ((srcWidth / dstAspect).toInt())
                    }

                    val cropX = (srcWidth - cropWidth) / 2
                    val cropY = (srcHeight - cropHeight) / 2

                    val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropWidth, cropY + cropHeight)
                    val dstRect = android.graphics.Rect(0, 0, width, height)

                    canvas.drawBitmap(src, srcRect, dstRect, null)

                    // Recycle source bitmap after cropping
                    src.recycle()

                    croppedThumb
                }

                if (croppedBitmap != null) {
                } else {
                }

                croppedBitmap
            }

            thumbnails

        } catch (e: Exception) {
            emptyList()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Extract a grid of thumbnails (for timeline view)
     * @param uri Video URI
     * @param columns Number of columns in the grid
     * @param rows Number of rows in the grid
     * @param width Thumbnail width
     * @param height Thumbnail height
     * @return 2D list of thumbnails [row][col]
     */
    suspend fun extractGrid(
        uri: Uri,
        columns: Int,
        rows: Int,
        width: Int = 50,
        height: Int =50
    ): List<List<Bitmap?>> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.setDataSource(context, uri)
            } else {
                @Suppress("DEPRECATION")
                retriever.setDataSource(uri.toString())
            }

            // Get video duration
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            if (durationMs <= 0) {
                return@withContext emptyList()
            }

            val intervalMs = durationMs / (columns * rows + 1)


            val grid = mutableListOf<List<Bitmap?>>()

            // Extract thumbnails row by row
            for (row in 0 until rows) {
                val rowThumbnails = mutableListOf<Bitmap?>()

                for (col in 0 until columns) {
                    val positionMs = ((row * columns + col + 1) * intervalMs).toLong()

                    // Default to normal quality for grid
                    val extractWidth = width
                    val extractHeight = height

                    val frame = retriever.getScaledFrameAtTime(
                        positionMs * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        extractWidth,
                        extractHeight
                    )

                    rowThumbnails.add(frame)
                }

                grid.add(rowThumbnails)
            }

            grid

        } catch (e: Exception) {
            emptyList()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Extract all thumbnails and stitch them into a single horizontal strip (filmstrip)
     * All thumbnails are placed side by side without any gaps
     * @param uri Video URI
     * @param durationMs Video duration in milliseconds
     * @param settings Thumbnail extraction settings
     * @param maxStripWidth Maximum width of the final strip (in pixels), 0 for unlimited
     * @return Flow emitting progress updates, completing with the final stitched Bitmap
     */
    suspend fun extractFilmstrip(
        uri: Uri,
        durationMs: Long,
        settings: ThumbnailSettings,
        maxStripWidth: Int = 0
    ): Flow<Pair<ThumbnailExtractionProgress, Bitmap?>> = withContext(Dispatchers.IO) {
        callbackFlow {
            val retriever = MediaMetadataRetriever()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.setDataSource(context, uri)
                } else {
                    @Suppress("DEPRECATION")
                    retriever.setDataSource(uri.toString())
                }

                val thumbWidth = settings.dimensionPreset.width
                val thumbHeight = settings.dimensionPreset.height

                // Calculate interval between thumbnails
                val intervalMs = ThumbnailConfig.Timing.INTERVAL_CALCULATION_DIVISOR / settings.thumbsPerSecond
                val totalThumbnails = (durationMs / intervalMs).toInt()


                // Emit initial progress
                trySend(
                    ThumbnailExtractionProgress(0, totalThumbnails, 0) to null
                )

                val bitmaps = mutableListOf<Bitmap>()

                // Extract all thumbnails with fixed size (cropped, not aspect-ratio preserved)
                for (i in 0 until totalThumbnails) {
                    val positionMs = (i * intervalMs).toLong()

                    try {
                        val quality = settings.extractionQuality
                        val extractFactor = if (quality == ThumbnailQuality.HIGH) 2 else 1
                        
                        // Extract frame at a larger size to ensure we have enough pixels
                        val sourceBitmap = retriever.getScaledFrameAtTime(
                            positionMs * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            thumbWidth * extractFactor,
                            thumbHeight * extractFactor
                        )

                        sourceBitmap?.let { src ->
                            // Create cropped thumbnail at exact dimensions
                            val croppedThumb = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(croppedThumb)

                            // Calculate center crop
                            val srcWidth = src.width
                            val srcHeight = src.height
                            val srcAspect = srcWidth.toFloat() / srcHeight
                            val dstAspect = thumbWidth.toFloat() / thumbHeight

                            val (cropWidth, cropHeight) = if (srcAspect > dstAspect) {
                                // Source is wider: crop sides
                                ((srcHeight * dstAspect).toInt()) to srcHeight
                            } else {
                                // Source is taller: crop top/bottom
                                srcWidth to ((srcWidth / dstAspect).toInt())
                            }

                            val cropX = (srcWidth - cropWidth) / 2
                            val cropY = (srcHeight - cropHeight) / 2

                            val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropWidth, cropY + cropHeight)
                            val dstRect = android.graphics.Rect(0, 0, thumbWidth, thumbHeight)

                            canvas.drawBitmap(src, srcRect, dstRect, null)

                            bitmaps.add(croppedThumb)
                            src.recycle()

                            // Emit progress
                            trySend(
                                ThumbnailExtractionProgress(i + 1, totalThumbnails, positionMs) to null
                            )

                            // Yield to avoid blocking
                            yield()
                        }
                    } catch (e: Exception) {
                    }
                }

                // Stitch all bitmaps into single horizontal strip
                if (bitmaps.isNotEmpty()) {
                    val actualWidth = thumbWidth * bitmaps.size

                    // Check if we need to limit strip width
                    val finalWidth = if (maxStripWidth > 0 && actualWidth > maxStripWidth) {
                        maxStripWidth
                    } else {
                        actualWidth
                    }

                    // Create the filmstrip bitmap
                    val filmstrip = Bitmap.createBitmap(finalWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(filmstrip)

                    // Draw each bitmap side by side (bitmaps are already exact size)
                    var xOffset = 0
                    bitmaps.forEach { thumb ->
                        canvas.drawBitmap(thumb, xOffset.toFloat(), 0f, null)
                        xOffset += thumbWidth

                        // Recycle bitmap after drawing
                        thumb.recycle()
                    }


                    // Emit final result
                    trySend(
                        ThumbnailExtractionProgress(
                            totalThumbnails,
                            totalThumbnails,
                            durationMs,
                            isComplete = true
                        ) to filmstrip
                    )
                }

            } catch (e: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                }
            }

            awaitClose {}
        }
    }

    /**
     * Extract all thumbnails and stitch them into a single horizontal strip, saving to file
     * @param uri Video URI
     * @param durationMs Video duration in milliseconds
     * @param outputFile Output file for the filmstrip image
     * @param settings Thumbnail extraction settings
     * @param maxStripWidth Maximum width of the final strip, 0 for unlimited
     * @return Flow emitting progress updates, completing with success boolean
     */
    suspend fun extractFilmstripToFile(
        uri: Uri,
        durationMs: Long,
        outputFile: File,
        settings: ThumbnailSettings,
        maxStripWidth: Int = 0
    ): Flow<ThumbnailExtractionProgress> = withContext(Dispatchers.IO) {
        callbackFlow {
            extractFilmstrip(uri, durationMs, settings, maxStripWidth).collect { (progress, filmstrip) ->

                // Send progress updates
                trySend(progress)

                // When complete, save to file
                if (progress.isComplete && filmstrip != null) {
                    try {
                        val (extension, compressFormat) = when (settings.format) {
                            ThumbnailFormat.JPEG -> ".jpg" to Bitmap.CompressFormat.JPEG
                            ThumbnailFormat.PNG -> ".png" to Bitmap.CompressFormat.PNG
                            ThumbnailFormat.WEBP -> ".webp" to Bitmap.CompressFormat.WEBP
                        }

                        // Ensure file has correct extension
                        val finalFile = if (!outputFile.name.endsWith(extension)) {
                            File(outputFile.parent, outputFile.nameWithoutExtension + extension)
                        } else {
                            outputFile
                        }

                        java.io.FileOutputStream(finalFile).use { out ->
                            filmstrip.compress(compressFormat, settings.quality, out)
                        }

                    } catch (e: Exception) {
                    } finally {
                        filmstrip.recycle()
                    }
                }
            }

            awaitClose {}
        }
    }

    companion object {
        /**
         * Default thumbnail size for timeline
         */
        const val DEFAULT_THUMB_WIDTH = ThumbnailConfig.Dimensions.NORMAL_WIDTH
        const val DEFAULT_THUMB_HEIGHT = ThumbnailConfig.Dimensions.NORMAL_HEIGHT

        /**
         * Recommended number of thumbnails for timeline
         */
        const val RECOMMENDED_THUMB_COUNT = ThumbnailConfig.Quality.DEFAULT_THUMBS_PER_STRIP
    }

    /**
     * Size presets for timeline thumbnails
     */
    enum class ThumbnailSize(val width: Int, val height: Int) {
        COMPACT(ThumbnailConfig.Dimensions.COMPACT_WIDTH, ThumbnailConfig.Dimensions.COMPACT_HEIGHT),
        NORMAL(ThumbnailConfig.Dimensions.NORMAL_WIDTH, ThumbnailConfig.Dimensions.NORMAL_HEIGHT),
        DETAILED(ThumbnailConfig.Dimensions.DETAILED_WIDTH, ThumbnailConfig.Dimensions.DETAILED_HEIGHT)
    }
}

// --- Merged from ThumbnailExtractorBatch.kt ---


/**
 * Extrator de thumbnails otimizado que reutiliza a instância do MediaMetadataRetriever
 *
 * Performance: 3-5x mais rápido que criar nova instância por thumbnail
 * Melhor para: Vídeos longos com múltiplos thumbnails
 *
 * Comparativo:
 * - MediaMetadataRetriever (atual): ~300-500ms por frame
 * - ThumbnailExtractorBatch: ~100-150ms por frame (reutilizando decoder)
 */
open class ThumbnailExtractorBatch(
    private val context: Context
) {

    /**
     * Extrai múltiplos thumbnails em batch, reutilizando a mesma instância do MediaMetadataRetriever
     *
     * @param uri URI do vídeo
     * @param positionsMs Lista de posições em milissegundos (será ordenada internamente)
     * @param width Largura alvo dos thumbnails
     * @param height Altura alvo dos thumbnails
     * @return Mapa de posição -> Bitmap
     */
    open suspend fun extractBatch(
        uri: Uri,
        positionsMs: List<Long>,
        width: Int = 320,
        height: Int = 180,
        onFrameExtracted: ((Long, Bitmap) -> Unit)? = null
    ): Map<Long, Bitmap> = withContext(Dispatchers.IO) {
        if (positionsMs.isEmpty()) {
            return@withContext emptyMap()
        }

        ActivityLogger.started(AppActivity.ThumbnailExtraction, "frames" to positionsMs.size, "uri" to uri)

        val startTime = System.currentTimeMillis()
        val results = mutableMapOf<Long, Bitmap>()

        // Ordenar posições para minimizar seeks no vídeo
        val sortedPositions = positionsMs.sorted()

        val retriever = MediaMetadataRetriever()
        try {
            // ❌ ANTES: Criava retriever a cada thumbnail (300-500ms cada)
            // ✅ AGORA: Cria UMA vez e reusa para todas as extrações

            retriever.setDataSource(context, uri)

            // Extrair cada posição
            sortedPositions.forEachIndexed { index, positionMs ->
                val currentQueueSize = sortedPositions.size - (index + 1)
                try {
                    val bitmap = extractFrameAt(retriever, positionMs, width, height, ThumbnailQuality.LOW, currentQueueSize)
                    if (bitmap != null) {
                        results[positionMs] = bitmap
                        onFrameExtracted?.invoke(positionMs, bitmap)
                    }
                } catch (e: Exception) {
                    // silenced
                }
            }

            PerformanceMonitor.calculateMetrics()
            val totalTime = System.currentTimeMillis() - startTime
            ActivityLogger.finished(AppActivity.ThumbnailExtraction, "extraídos" to results.size, "total" to positionsMs.size, "ms" to totalTime)

        } catch (e: Exception) {
            // silenced
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // silenced
            }
        }

        results
    }

    /**
     * Extrai um único thumbnail em posição específica
     *
     * @param uri URI do vídeo
     * @param positionMs Posição em milissegundos
     * @param width Largura alvo
     * @param height Altura alvo
     * @return Bitmap ou null se falhar
     */
    suspend fun extractSingle(
        uri: Uri,
        positionMs: Long,
        width: Int = 320,
        height: Int = 180
    ): Bitmap? = withContext(Dispatchers.IO) {
        Log.i("ChopCut", "[EXTRACT] extractSingle pos=$positionMs ${width}x${height}")
        val startTime = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri)
            Log.i("ChopCut", "[EXTRACT] setDataSource OK, getting frame at ${positionMs}ms")
            val bitmap = extractFrameAt(retriever, positionMs, width, height, ThumbnailQuality.HIGH)

            if (bitmap != null) {
                Log.i("ChopCut", "[EXTRACT] SUCCESS bitmap=${bitmap.width}x${bitmap.height} in ${System.currentTimeMillis() - startTime}ms")
            } else {
                Log.i("ChopCut", "[EXTRACT] NULL BITMAP from extractFrameAt")
            }

            bitmap
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // silenced
            }
        }
    }

    /**
     * Extrai um frame usando o retriever já configurado
     *
     * @param retriever Instância do MediaMetadataRetriever já inicializada
     * @param positionMs Posição em milissegundos
     * @param width Largura alvo
     * @param height Altura alvo
     * @return Bitmap ou null se falhar
     */
    private fun extractFrameAt(
        retriever: MediaMetadataRetriever,
        positionMs: Long,
        width: Int,
        height: Int,
        quality: ThumbnailQuality = ThumbnailQuality.LOW,
        queueSize: Int = 0
    ): Bitmap? {
        val taskId = "${positionMs}ms"
        return try {
            if (quality == ThumbnailQuality.LOW) {
                val startDecode = System.currentTimeMillis()
                val bitmap = retriever.getScaledFrameAtTime(
                    positionMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    width,
                    height
                )
                val decodeDuration = System.currentTimeMillis() - startDecode
                
                PerformanceMonitor.log(PerformanceEvent(
                    stage = ExtractionStage.DECODE,
                    taskId = taskId,
                    durationMs = decodeDuration,
                    queueSize = queueSize
                ))
                
                // Em LOW, o PROCESS é negligível (já feito pelo hardware no decode)
                PerformanceMonitor.log(PerformanceEvent(
                    stage = ExtractionStage.PROCESS,
                    taskId = taskId,
                    durationMs = 0,
                    queueSize = queueSize
                ))
                
                return bitmap
            }

            // A partir daqui, lógica para HIGH quality (Export/Preview Detalhado)
            val startDecode = System.currentTimeMillis()
            // Obter dimensões reais do vídeo para evitar distorção no getScaledFrameAtTime
            val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: width
            val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: height
            val videoRotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            // Trocar dimensões se estiver rotacionado
            val isPortrait = videoRotation == 90 || videoRotation == 270
            val realWidth = if (isPortrait) videoHeight else videoWidth
            val realHeight = if (isPortrait) videoWidth else videoHeight
            val videoAspectRatio = realWidth.toFloat() / realHeight.toFloat()

            // Ajustar dimensões de extração para manter aspect ratio
            val (extractWidth, extractHeight) = if (videoAspectRatio > width.toFloat() / height.toFloat()) {
                // Vídeo mais largo que o target: fixar altura, ajustar largura
                ((height * videoAspectRatio).toInt() to height)
            } else {
                // Vídeo mais alto que o target: fixar largura, ajustar altura
                (width to (width / videoAspectRatio).toInt())
            }

            // Aplicar fator de qualidade HIGH (usando 1.2x para anti-aliasing)
            val finalWidth = (extractWidth * 1.2f).toInt()
            val finalHeight = (extractHeight * 1.2f).toInt()

            val rawFrame = retriever.getScaledFrameAtTime(
                positionMs * 1000, // MediaMetadataRetriever usa microssegundos
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                finalWidth,
                finalHeight
            )
            val decodeDuration = System.currentTimeMillis() - startDecode
            PerformanceMonitor.log(PerformanceEvent(
                stage = ExtractionStage.DECODE,
                taskId = taskId,
                durationMs = decodeDuration,
                queueSize = queueSize
            ))

            val startProcess = System.currentTimeMillis()
            val frame = if (rawFrame != null && (rawFrame.width != width || rawFrame.height != height)) {

                // Criar bitmap de destino no tamanho exato solicitado (Force RGB_565 para economizar RAM)
                val result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                val canvas = android.graphics.Canvas(result)
                
                // Calcular área de crop centralizado
                val srcWidth = rawFrame.width
                val srcHeight = rawFrame.height
                val srcAspect = srcWidth.toFloat() / srcHeight
                val dstAspect = width.toFloat() / height

                val (cropWidth, cropHeight) = if (srcAspect > dstAspect) {
                    // Source é mais largo que o destino (e.g. 16:9 -> 1:1)
                    (srcHeight * dstAspect).toInt() to srcHeight
                } else {
                    // Source é mais alto que o destino (e.g. 9:16 -> 1:1)
                    srcWidth to (srcWidth / dstAspect).toInt()
                }

                val cropX = (srcWidth - cropWidth) / 2
                val cropY = (srcHeight - cropHeight) / 2

                val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropWidth, cropY + cropHeight)
                val dstRect = android.graphics.Rect(0, 0, width, height)

                // Desenhar com filtro bilinear para anti-aliasing
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(rawFrame, srcRect, dstRect, paint)
                
                rawFrame.recycle()
                result
            } else {
                rawFrame
            }
            val processDuration = System.currentTimeMillis() - startProcess
            PerformanceMonitor.log(PerformanceEvent(
                stage = ExtractionStage.PROCESS,
                taskId = taskId,
                durationMs = processDuration,
                queueSize = queueSize
            ))

            if (frame == null) {
                null
            } else {
                frame
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calcula posições ideais para thumbnails baseado na duração do vídeo
     *
     * @param durationMs Duração total do vídeo em milissegundos
     * @param intervalMs Intervalo entre thumbnails em milissegundos (padrão: 1000ms = 1 thumb por segundo)
     * @return Lista de posições em milissegundos
     */
    fun calculateThumbnailPositions(
        durationMs: Long,
        intervalMs: Long = 1000
    ): List<Long> {
        val positions = mutableListOf<Long>()
        var currentTime = 0L

        while (currentTime < durationMs) {
            positions.add(currentTime)
            currentTime += intervalMs
        }

        return positions
    }

    /**
     * Calcula posições em um range específico (para pre-fetching)
     *
     * @param durationMs Duração total do vídeo
     * @param startMs Início do range em milissegundos
     * @param endMs Fim do range em milissegundos
     * @param intervalMs Intervalo entre thumbnails
     * @return Lista de posições filtradas
     */
    fun calculateThumbnailPositionsInRange(
        durationMs: Long,
        startMs: Long,
        endMs: Long,
        intervalMs: Long = 1000
    ): List<Long> {
        return calculateThumbnailPositions(durationMs, intervalMs)
            .filter { it in startMs..endMs }
    }
}

// --- Merged from ThumbnailRequest.kt ---


/**
 * Representa uma prioridade de extração de thumbnail
 */
enum class ThumbnailPriority(val value: Int) {
    VISIBLE(1),    // Visível no viewport atual
    PREFETCH(2),   // Próximo ao viewport
    DISTANT(3);    // Longe do viewport
}

/**
 * Solicitação de extração de thumbnail
 */
data class ThumbnailRequest(
    val uri: Uri,
    val timestamp: Long,
    val priority: ThumbnailPriority,
    val width: Int,
    val height: Int,
    val callback: (Bitmap) -> Unit
) : Comparable<ThumbnailRequest> {
    override fun compareTo(other: ThumbnailRequest): Int {
        // Menor valor numérico (VISIBLE=1) tem maior prioridade
        return this.priority.value.compareTo(other.priority.value)
    }
}

// --- Merged from ThumbnailStripManager.kt ---


/**
 * Gerenciador de strips segmentadas de thumbnails para o timeline.
 *
 * Combina thumbnails em strips horizontais de [thumbsPerStrip] segundos cada.
 * Dimensões dos thumbnails são calculadas pelo chamador baseado na densidade
 * do display, garantindo rendering pixel-perfect sem upscaling.
 *
 * Otimizações de qualidade:
 * - [Bitmap.Config.RGB_565]: 50% menos memória (thumbs não precisam de alpha)
 * - [Paint.isFilterBitmap]: interpolação bilinear no CenterCrop (evita blocos)
 * - Dimensões density-aware: aspecto 3:2 matching o display (sem distorção)
 * - Strips adaptativas: começam pequenas (5) e crescem até o limite para melhor UX
 *
 * @param context Context do Android
 * @param thumbWidth Largura de cada thumbnail em pixels (deve corresponder a pxPerSecond)
 * @param thumbHeight Altura de cada thumbnail em pixels (deve corresponder a thumbnailHeightPx)
 * @param thumbsPerStrip Quantidade máxima de thumbnails por strip (padrão: 10)
 * @param adaptiveStrips Se true, usa strips adaptativas que crescem de 5 até thumbsPerStrip
 */
class ThumbnailStripManager(
    private val context: Context,
    val thumbWidth: Int,
    val thumbHeight: Int,
    val thumbsPerStrip: Int = 10,
    val adaptiveStrips: Boolean = true,
    private val batchExtractor: ThumbnailExtractorBatch = ThumbnailExtractorBatch(context)
) {
    // Removido PreferencesManager (cache agora está sempre ativado por padrão)

    /** Configuração de strips adaptativas */
    private val minThumbsPerStrip = ThumbnailConfig.Adaptive.MIN_THUMBS_PER_STRIP
    
    companion object {
        /** Limite de concorrência para extração (Aumentado para 2 para melhor throughput) */
        private val extractSemaphore = Semaphore(2)

        /** Limite de concorrência para I/O de escrita (permite escrita paralela) */
        private val ioSemaphore = Semaphore(ThumbnailConfig.Concurrency.IO_SEMAPHORE_PERMITS)

        /** Diretório de cache para strips */
        internal const val CACHE_DIR = "thumbnail_strips"

        /** Tamanho máximo do cache em bytes */
        private const val MAX_CACHE_SIZE = ThumbnailConfig.Cache.MAX_CACHE_SIZE

        /** Versão do cache para invalidação manual ao mudar formatos/lógica */
        private const val CACHE_VERSION = ThumbnailConfig.Cache.CACHE_VERSION

        /** Qualidade para compressão das strips */
        private const val COMPRESSION_QUALITY = ThumbnailConfig.Compression.STRIP_COMPRESSION_QUALITY

        
        /**
         * Calcula thumbsPerStrip para um segmento específico usando estratégia adaptativa.
         *
         * Estratégia: Começa pequena (5) para carregar rápido o início, cresce suavemente
         * até o limite máximo usando curva de potência (exponencial suave).
         *
         * Exemplo para vídeo de 15min com max=20:
         * - Segmento 0 (0%): 5 thumbs
         * - Segmento 10 (25%): ~7 thumbs
         * - Segmento 20 (50%): ~11 thumbs
         * - Segmento 30 (75%): ~14 thumbs
         * - Segmento 40+ (100%): 20 thumbs
         *
         * @param segmentIndex Índice do segmento (0-based)
         * @param totalSegments Número total de segmentos
         * @param maxThumbsPerStrip Limite máximo de thumbs por strip
         * @param minThumbsPerStrip Mínimo de thumbs por strip (padrão: 5)
         * @return Número de thumbs para este segmento
         */
        fun calculateAdaptiveThumbsPerStrip(
            segmentIndex: Int,
            totalSegments: Int,
            maxThumbsPerStrip: Int,
            minThumbsPerStrip: Int = ThumbnailConfig.Adaptive.MIN_THUMBS_PER_STRIP
        ): Int {
            if (totalSegments <= 1) return maxThumbsPerStrip

            // Progresso normalizado (0.0 a 1.0)
            val progress = segmentIndex.toFloat() / (totalSegments - 1).toFloat()

            // Curva de potência suave (ex: progress^0.5)
            // Isso faz o crescimento ser mais rápido no início, mais lento depois
            val power = ThumbnailConfig.Adaptive.ADAPTIVE_POWER_CURVE_EXPONENT
            val adjustedProgress = progress.coerceIn(0f, 1f).pow(power)

            // Calcular thumbsPerStrip ajustado
            val range = maxThumbsPerStrip - minThumbsPerStrip
            val thumbsForSegment = (minThumbsPerStrip + (range * adjustedProgress)).toInt()

            return thumbsForSegment.coerceIn(minThumbsPerStrip, maxThumbsPerStrip)
        }
        
        /**
         * Limpa todo o cache de thumbnails do disco.
         * Deve ser chamado em background (IO).
         */
        fun clearCache(context: Context) {
            try {
                val cacheDir = File(context.cacheDir, CACHE_DIR)
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                    cacheDir.mkdirs() // Recriar pasta vazia
                }
            } catch (e: Exception) {
            }
        }
    }

    /** Diretório de cache para strips */
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
    }

    /** Lock para operações de cache */
    private val cacheLock = Any()
    
    /**
     * Obtém o número de thumbs por strip para um segmento específico.
     * Se adaptiveStrips está habilitado, usa estratégia de crescimento.
     * Caso contrário, usa thumbsPerStrip fixo.
     * 
     * @param segmentIndex Índice do segmento (0-based)
     * @param totalSegments Número total de segmentos
     * @return Número de thumbs para este segmento
     */
    fun getThumbsPerStripForSegment(segmentIndex: Int, totalSegments: Int): Int {
        val result = if (adaptiveStrips) {
            calculateAdaptiveThumbsPerStrip(
                segmentIndex = segmentIndex,
                totalSegments = totalSegments,
                maxThumbsPerStrip = thumbsPerStrip,
                minThumbsPerStrip = minThumbsPerStrip
            )
        } else {
            thumbsPerStrip
        }
        
        return result
    }
    
    /**
     * Calcula a posição inicial (em segundos) de um segmento específico.
     * Para strips adaptativas, usa soma cumulativa das thumbs dos segmentos anteriores.
     * Para strips fixas, usa multiplicação simples.
     * 
     * @param segmentIndex Índice do segmento
     * @param totalSegments Número total de segmentos
     * @return Posição inicial em segundos
     */
    fun getSegmentStartSecond(segmentIndex: Int, totalSegments: Int): Int {
        if (segmentIndex == 0) return 0
        
        if (adaptiveStrips) {
            var totalSeconds = 0
            for (i in 0 until segmentIndex) {
                totalSeconds += getThumbsPerStripForSegment(i, totalSegments)
            }
            return totalSeconds
        } else {
            return segmentIndex * thumbsPerStrip
        }
    }
    
    /**
     * Loga a estratégia de strips adaptativas para debug.
     */
    fun logAdaptiveStrategy(totalSegments: Int) {
        if (!adaptiveStrips) {
            return
        }
        
        val sampleSegments = listOf(0, 1, 2, 5, 10, 15, 20, 30, 40, totalSegments - 1)
        sampleSegments.filter { it in 0 until totalSegments }.forEach { segIdx ->
            val thumbs = getThumbsPerStripForSegment(segIdx, totalSegments)
            val progress = (segIdx.toFloat() / (totalSegments - 1) * 100).toInt()
        }
    }
    
    /**
     * Gera uma chave única de cache baseada no URI do vídeo
     * Usa lastModified + tamanho do arquivo para detectar modificações
     */
    private fun getCacheKey(uri: Uri, segmentIndex: Int, onlyFirstFrame: Boolean = false): String {
        val fileInfo = getFileIdentifier(uri)
        val suffix = if (onlyFirstFrame) "_ov" else ""
        // Incluir CACHE_VERSION na chave para invalidar automaticamente versões antigas
        return "strip_v${CACHE_VERSION}_${fileInfo}_${segmentIndex}${suffix}.webp"
    }

    /**
     * Obtém um identificador único para o arquivo de vídeo
     * Combina path e tamanho para detectar mudanças
     */
    internal fun getFileIdentifier(uri: Uri): String {
        return try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.SIZE, android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val size = it.getLong(0) ?: 0L
                    val displayName = it.getString(1) ?: uri.toString()
                    "${displayName}_$size" // displayName + size detecta modificações
                } else {
                    uri.toString()
                }
            } ?: uri.toString()
        } catch (e: Exception) {
            uri.toString()
        }
    }

    /**
     * Tenta carregar uma strip do cache
     * @return Bitmap se encontrado e válido, null caso contrário
     */
    private fun loadFromCache(uri: Uri, segmentIndex: Int, onlyFirstFrame: Boolean = false): Bitmap? {
        try {
            val cacheKey = getCacheKey(uri, segmentIndex, onlyFirstFrame)
            val cacheFile = File(cacheDir, cacheKey)
            
            if (!cacheFile.exists()) {
                return null
            }

            val startTime = System.currentTimeMillis()
            
            // OTIMIZAÇÃO: Forçar RGB_565 no decode para economizar 50% de RAM (thumbnails não precisam de alpha)
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            
            // ATENÇÃO: decodeFile é thread-safe para leitura paralela
            val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath, options)

            if (bitmap != null) {
                val elapsed = System.currentTimeMillis() - startTime

                // MÉTRICAS DE PERFORMANCE: Alertar se cache I/O está lento
                if (elapsed > 50L) {
                } else {
                }
            } else {
                // Delete sem lock, filesystem cuida da atomicidade
                cacheFile.delete()
                // Record corruption in metrics
                ThumbnailCacheManager.recordCorruption()
            }
            
            return bitmap
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Salva uma strip no cache.
     * 
     * Fluxo:
     * 1. Compressão fora do lock (já é paralela)
     * 2. rename() dentro do lock synchronized (operacao atômica)
     * 
     * Nota: A compressão já acontece em paralelo pois o lock synchronized
     * é adquirido apenas após a compressão estar completa. Isso permite
     * que múltiplas threads comprimam simultaneamente, e apenas o rename
     * é serializado.
     * 
     * @return true se salvo com sucesso, false caso contrário
     */
    private fun saveToCache(uri: Uri, segmentIndex: Int, strip: Bitmap, onlyFirstFrame: Boolean = false): Boolean {
        try {
            val cacheKey = getCacheKey(uri, segmentIndex, onlyFirstFrame)
            val finalFile = File(cacheDir, cacheKey)
            val tempFile = File(cacheDir, "${cacheKey}.tmp")

            val startTime = System.currentTimeMillis()
            val compressStart = System.currentTimeMillis()

            // 1. COMPRESSÃO FORA DO LOCK (operação pesada, paralela)
            FileOutputStream(tempFile).use { out ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                strip.compress(format, COMPRESSION_QUALITY, out)
            }

            val compressTime = System.currentTimeMillis() - compressStart

            // 2. OPERAÇÃO ATÔMICA DENTRO DO LOCK
            synchronized(cacheLock) {
                if (tempFile.renameTo(finalFile)) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val sizeKB = finalFile.length() / 1024

                    trimCacheIfNeeded()
                    return true
                } else {
                    tempFile.delete()
                    return false
                }
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Remove entradas antigas do cache se exceder limite de tamanho
     * Remove as entradas menos acessadas (LRU baseado em lastModified)
     */
    private fun trimCacheIfNeeded() {
        synchronized(cacheLock) {
            try {
                val cacheFiles = cacheDir.listFiles() ?: return

                val currentSize = cacheFiles.sumOf { it.length() }

                if (currentSize <= MAX_CACHE_SIZE) {
                    return
                }

                val currentSizeMB = currentSize / 1024 / 1024
                val maxSizeMB = MAX_CACHE_SIZE / 1024 / 1024

                // Ordenar por lastModified (mais antigos primeiro)
                val sortedFiles: List<File> = cacheFiles.sortedBy { file -> file.lastModified() }
                val filesToDelete: List<File> = sortedFiles.take(cacheFiles.size / 4)

                var deletedSize = 0L
                filesToDelete.forEach { file: File ->
                    try {
                        deletedSize += file.length()
                        file.delete()
                    } catch (e: Exception) {
                    }
                }


                // Record eviction in metrics
                ThumbnailCacheManager.recordEviction()
            } catch (e: Exception) {
            }
        }
    }

    /** Paint com interpolação bilinear para CenterCrop de qualidade */
    private val cropPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    
    /**
     * Extrai uma strip para o segmento especificado usando batch extraction.
     *
     * MELHORIA: Usa ThumbnailExtractorBatch para reutilizar a instância do MediaMetadataRetriever,
     * reduzindo o tempo de extração de ~300-500ms por frame para ~100-150ms por frame (67% mais rápido).
     *
     * Se adaptiveStrips está habilitado, usa strips adaptativas que começam pequenas (5)
     * e crescem até o limite (thumbsPerStrip) para melhor UX.
     *
     * A strip final usa RGB_565 (2 bytes/pixel vs 4 do ARGB_8888).
     *
     * @param uri URI do vídeo
     * @param segmentIndex Índice do segmento (0-based)
     * @param durationMs Duração total do vídeo em milissegundos
     * @return Bitmap horizontal RGB_565 com frames stitchados, ou null se falhar
     */
    suspend fun extractSegment(
        uri: Uri,
        segmentIndex: Int,
        durationMs: Long,
        onlyFirstFrame: Boolean = false,
        onFrameExtracted: ((Long, Bitmap) -> Unit)? = null
    ): Bitmap? {
        val totalSegments = getSegmentCount(durationMs)
        return extractSegment(uri, segmentIndex, durationMs, totalSegments, onlyFirstFrame, onFrameExtracted)
    }
    
    /**
     * Extrai uma strip para o segmento especificado usando batch extraction.
     *
     * MELHORIA: Usa ThumbnailExtractorBatch para reutilizar a instância do MediaMetadataRetriever,
     * reduzindo o tempo de extração de ~300-500ms por frame para ~100-150ms por frame (67% mais rápido).
     *
     * Se adaptiveStrips está habilitado, usa strips adaptativas que começam pequenas (5)
     * e crescem até o limite (thumbsPerStrip) para melhor UX.
     *
     * A strip final usa RGB_565 (2 bytes/pixel vs 4 do ARGB_8888).
     *
     * @param uri URI do vídeo
     * @param segmentIndex Índice do segmento (0-based)
     * @param durationMs Duração total do vídeo em milissegundos
     * @param totalSegments Número total de segmentos (obrigatório para strips adaptativas)
     * @param onlyFirstFrame Se true, extrai apenas o primeiro frame do segmento (fast overview)
     * @return Bitmap horizontal RGB_565 com frames stitchados, ou null se falhar
     */
    suspend fun extractSegment(
        uri: Uri,
        segmentIndex: Int,
        durationMs: Long,
        totalSegments: Int,
        onlyFirstFrame: Boolean = false,
        onFrameExtracted: ((Long, Bitmap) -> Unit)? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        ActivityLogger.started(AppActivity.StripAssembly, "segmento" to segmentIndex, "total" to totalSegments)

        android.os.Trace.beginSection("TSM.extractSegment")

        // Fail-fast se o job já foi cancelado
        coroutineContext.ensureActive()

                // MELHORIA: Tentar carregar do cache primeiro
                if (true) {
                    android.os.Trace.beginSection("TSM.loadFromCache")
                    val cacheKey = getCacheKey(uri, segmentIndex, onlyFirstFrame)
                    val cachedStrip = loadFromCache(uri, segmentIndex, onlyFirstFrame)
                    android.os.Trace.endSection()
                    if (cachedStrip != null) {
                        val cacheHitTime = System.currentTimeMillis() - startTime
                        return@withContext cachedStrip
                    }
    
                } else {
                }
    
        extractSemaphore.withPermit {
            // Verificar novamente após adquirir permissão (pode ter demorado na fila)
            coroutineContext.ensureActive()
    
            // Calcular thumbsPerStrip adaptativo se habilitado
            val currentThumbsPerStrip = if (onlyFirstFrame) 1 else getThumbsPerStripForSegment(segmentIndex, totalSegments)
            
            val totalSeconds = ((durationMs + 999) / 1000).toInt()
            
            // Se for overview, usamos o índice para pular o intervalo correto
            val segDuration = getThumbsPerStripForSegment(segmentIndex, totalSegments)
            // TODO: quando adaptiveStrips for reativado, usar soma cumulativa em vez de multiplicação
            val startSec = segmentIndex * segDuration
            
            if (startSec >= totalSeconds) return@withPermit null
    
            val framesInSegment = if (onlyFirstFrame) 1 else minOf(currentThumbsPerStrip, totalSeconds - startSec)

              try {
                coroutineContext.ensureActive()

                // Strip RGB_565: metade da memória de ARGB_8888
                val stripWidth = thumbWidth * framesInSegment
                val strip = Bitmap.createBitmap(stripWidth, thumbHeight, Bitmap.Config.RGB_565)
                val canvas = Canvas(strip)



                // MELHORIA: Extrair frames em batch usando ThumbnailExtractorBatch
                // Isso reutiliza UMA instância do MediaMetadataRetriever para todo o segmento
                val positions = (0 until framesInSegment).map { frameIdx ->
                    val sec = startSec + frameIdx
                    sec * 1_000L // Converter para milissegundos (extractBatch espera ms)
                }

                android.os.Trace.beginSection("TSM.extractBatch")
                val extractedFrames = batchExtractor.extractBatch(
                    uri = uri,
                    positionsMs = positions,
                    width = thumbWidth,
                    height = thumbHeight,
                    onFrameExtracted = { pos, bmp ->
                        // Passar uma cópia para a UI para que o StripManager possa reciclar com segurança
                        // Isso mantém o cache/manager alheio ao ciclo de vida da UI
                        try {
                            if (!bmp.isRecycled) {
                                val config = bmp.config ?: android.graphics.Bitmap.Config.ARGB_8888
                                val copy = bmp.copy(config, false)
                                onFrameExtracted?.invoke(pos, copy)
                            }
                        } catch (e: Exception) {
                        }
                    }
                )
                android.os.Trace.endSection()


                if (extractedFrames.isEmpty()) {
                    return@withPermit null
                }


                // Stitch frames na strip
                android.os.Trace.beginSection("TSM.stitchFrames")
                for (frameIdx in 0 until framesInSegment) {
                    coroutineContext.ensureActive() // Responsividade ao cancelamento

                    val sec = startSec + frameIdx
                    val positionMs = sec * 1_000L
                    val source = extractedFrames[positionMs]

                    if (source != null) {
                        // A lógica de aspect ratio agora está no `ThumbnailExtractorBatch`.
                        // Apenas desenhamos o bitmap retornado no canvas da strip.
                        val dstX = frameIdx * thumbWidth
                        val dstRect = Rect(dstX, 0, dstX + thumbWidth, thumbHeight)

                        canvas.drawBitmap(source, null, dstRect, cropPaint)
                        source.recycle()
                    } else {
                    }
                }
                android.os.Trace.endSection()


                val totalTime = System.currentTimeMillis() - startTime
                ActivityLogger.finished(AppActivity.StripAssembly, "segmento" to segmentIndex, "ms" to totalTime)

                // MELHORIA: Salvar no cache após extração bem-sucedida
                if (true) {
                    android.os.Trace.beginSection("TSM.saveToCache")
                    saveToCache(uri, segmentIndex, strip, onlyFirstFrame)
                    android.os.Trace.endSection()
                }

                strip
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ActivityLogger.failed(AppActivity.StripAssembly, "segmento" to segmentIndex, "motivo" to e.message)
                null
            } finally {
                android.os.Trace.endSection() // TSM.extractSegment
            }
        }
    }

    /**
     * Calcula o número total de segmentos para um vídeo.
     * 
     * Para strips adaptativas, usa o valor médio de thumbsPerStrip (~2/3 do máximo)
     * para fazer uma aproximação do número de segmentos.
     */
    fun getSegmentCount(durationMs: Long): Int {
        val totalSeconds = ((durationMs + 999) / 1000).toInt()
        
        // Para strips adaptativas, usar valor médio para aproximação
        val effectiveThumbsPerStrip = if (adaptiveStrips) {
            // Valor médio da função adaptativa é aproximadamente 2/3 do máximo
            // para curva de potência com exponent 0.5
            ((thumbsPerStrip + minThumbsPerStrip * 2) / 3).coerceAtLeast(1)
        } else {
            thumbsPerStrip
        }
        
        return (totalSeconds + effectiveThumbsPerStrip - 1) / effectiveThumbsPerStrip
    }
}

// --- Merged from FastFrameExtractor.kt ---



/**
 * Fast frame extractor using MediaCodec in ByteBuffer mode.
 *
 * Reads the actual output stride/slice-height from [MediaCodec.getOutputFormat]
 * after the codec starts producing output, so the YUV→RGB conversion uses
 * hardware-accurate layout metadata instead of guessing.
 */
class FastFrameExtractor(
    private val context: Context,
    private val videoUri: Uri
) {
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var videoTrackIndex = -1
    private var outputWidth = 0
    private var outputHeight = 0

    // Actual hardware layout — populated from codec.getOutputFormat()
    private var stride = 0
    private var sliceHeight = 0
    private var colorFormat = 0
    private var codecWidth = 0   // actual decoded frame width
    private var codecHeight = 0  // actual decoded frame height
    private var outputFormatRead = false

    private val mutex = Mutex()

    suspend fun prepare(width: Int, height: Int): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            Timber.d("Preparing for %s", videoUri)
            try {
                extractor = MediaExtractor().apply {
                    setDataSource(context, videoUri, null)
                }

                videoTrackIndex = findVideoTrack(extractor!!)
                if (videoTrackIndex < 0) {
                    Timber.e("No video track found.")
                    return@withContext false
                }

                extractor!!.selectTrack(videoTrackIndex)
                val format = extractor!!.getTrackFormat(videoTrackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime == null) {
                    Timber.e("Video MIME type is null.")
                    return@withContext false
                }
                Timber.d("Input format: %s", format)

                outputWidth = width
                outputHeight = height

                // Request YUV420Flexible — the most universally supported format
                format.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                )

                codec = MediaCodec.createDecoderByType(mime)
                // null surface = ByteBuffer mode
                codec!!.configure(format, null, null, 0)
                codec!!.start()

                Timber.d("MediaCodec started in ByteBuffer mode")
                true
            } catch (e: Exception) {
                Timber.e(e, "Prepare failed for %s", videoUri)
                false
            }
        }
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return i
        }
        return -1
    }

    /**
     * Read the actual hardware output format once the codec has produced output.
     * This gives us the real stride and slice-height the hardware is using.
     */
    private fun readOutputFormat() {
        if (outputFormatRead) return
        val fmt = codec?.outputFormat ?: return

        codecWidth = fmt.getIntegerSafe(MediaFormat.KEY_WIDTH, outputWidth)
        codecHeight = fmt.getIntegerSafe(MediaFormat.KEY_HEIGHT, outputHeight)
        stride = fmt.getIntegerSafe(MediaFormat.KEY_STRIDE, codecWidth)
        sliceHeight = fmt.getIntegerSafe("slice-height", codecHeight)
        colorFormat = fmt.getIntegerSafe(MediaFormat.KEY_COLOR_FORMAT, 0)

        Timber.d(
            "Output format: %dx%d, stride=%d, sliceHeight=%d, colorFormat=0x%x, target=%dx%d",
            codecWidth, codecHeight, stride, sliceHeight, colorFormat, outputWidth, outputHeight
        )
        outputFormatRead = true
    }

    suspend fun getFrameAt(timeUs: Long): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val codec = this@FastFrameExtractor.codec ?: return@withContext null
            val extractor = this@FastFrameExtractor.extractor ?: return@withContext null

            try {
                codec.flush()
                extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                val info = MediaCodec.BufferInfo()
                val timeoutUs = 5_000L
                var frameAcquired: Bitmap? = null
                val startTime = System.currentTimeMillis()
                var eos = false

                while (frameAcquired == null && System.currentTimeMillis() - startTime < 5000) {
                    // Feed input
                    if (!eos) {
                        val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                eos = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex, 0, sampleSize,
                                    extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    // Drain output
                    val outputIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            readOutputFormat()
                        }
                        outputIndex >= 0 -> {
                            // Read format if we haven't yet (some codecs don't signal FORMAT_CHANGED)
                            readOutputFormat()

                            val isTarget = info.presentationTimeUs >= timeUs ||
                                (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)

                            if (isTarget) {
                                // Prefer getOutputImage() — gives exact plane layout info
                                val image = codec.getOutputImage(outputIndex)
                                if (image != null) {
                                    frameAcquired = convertImageToBitmap(image)
                                    image.close()
                                } else {
                                    // Fallback: raw ByteBuffer (some codecs may not support getOutputImage)
                                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                                    if (outputBuffer != null) {
                                        frameAcquired = convertYuvToBitmap(outputBuffer)
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && eos -> break
                    }
                }
                frameAcquired
            } catch (e: Exception) {
                Timber.e(e, "Extraction failed at %dms", timeUs / 1000)
                null
            }
        }
    }

    /**
     * Convert an [android.media.Image] (YUV_420_888) to an RGB_565 Bitmap.
     *
     * Uses [Image.getPlanes] to read Y, U, V with exact rowStride and pixelStride,
     * so we correctly handle NV12, NV21, I420, and YV12 layouts without guessing.
     */
    private fun convertImageToBitmap(image: android.media.Image): Bitmap? {
        val w = image.width
        val h = image.height
        val planes = image.planes  // [0]=Y, [1]=U, [2]=V

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        Timber.d(
            "Image format=%d, %dx%d | Y: rowStride=%d pixelStride=%d | U: rowStride=%d pixelStride=%d | V: rowStride=%d pixelStride=%d",
            image.format, w, h,
            yPlane.rowStride, yPlane.pixelStride,
            uPlane.rowStride, uPlane.pixelStride,
            vPlane.rowStride, vPlane.pixelStride
        )

        // Build NV21 byte array: Y (w*h) + interleaved VU (w*h/2)
        val nv21 = ByteArray(w * h * 3 / 2)

        // Copy Y plane, respecting rowStride
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        for (row in 0 until h) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv21, row * w, w)
        }

        // Copy UV into NV21 order (V, U interleaved)
        val uvDst = w * h
        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        if (uvPixelStride == 2) {
            // Interleaved (NV12 or NV21) — read V and U from their respective buffers
            for (row in 0 until h / 2) {
                for (col in 0 until w / 2) {
                    val srcIdx = row * uvRowStride + col * uvPixelStride
                    val dstIdx = uvDst + row * w + col * 2
                    nv21[dstIdx] = vBuffer.get(srcIdx)       // V
                    nv21[dstIdx + 1] = uBuffer.get(srcIdx)   // U
                }
            }
        } else {
            // Planar (I420/YV12): pixelStride == 1
            for (row in 0 until h / 2) {
                for (col in 0 until w / 2) {
                    val srcIdx = row * uvRowStride + col
                    val dstIdx = uvDst + row * w + col * 2
                    nv21[dstIdx] = vBuffer.get(srcIdx)       // V
                    nv21[dstIdx + 1] = uBuffer.get(srcIdx)   // U
                }
            }
        }

        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, w, h, null)
            val out = ByteArrayOutputStream(w * h)
            yuvImage.compressToJpeg(Rect(0, 0, w, h), 85, out)

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                if (outputWidth > 0 && outputHeight > 0) {
                    inSampleSize = calculateSampleSize(w, h, outputWidth, outputHeight)
                }
            }
            val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size(), opts)
                ?: return null

            if (outputWidth > 0 && outputHeight > 0) {
                val scaled = Bitmap.createScaledBitmap(bitmap, outputWidth, outputHeight, true)
                if (scaled !== bitmap) bitmap.recycle()
                scaled
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Timber.e(e, "Image conversion failed: %dx%d", w, h)
            null
        }
    }

    /**
     * Fallback: convert a raw YUV output buffer to an RGB_565 Bitmap.
     *
     * Used only when [MediaCodec.getOutputImage] returns null. Copies UV data
     * directly without swap (the codec format is unknown, but direct copy is
     * more likely correct than assuming NV12).
     */
    private fun convertYuvToBitmap(buffer: java.nio.ByteBuffer): Bitmap? {
        val w = codecWidth
        val h = codecHeight
        val s = stride.coerceAtLeast(w)
        val sh = sliceHeight.coerceAtLeast(h)

        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        Timber.d("Buffer: %d bytes, expected s*sh*1.5=%d, s*h*1.5=%d", data.size, s * sh * 3 / 2, s * h * 3 / 2)

        // Detect UV plane offset: some codecs use stride*sliceHeight, others stride*height
        val uvOffsetPadded = s * sh
        val uvOffsetCompact = s * h
        val uvNeeded = s * (h / 2)  // bytes needed for UV plane

        val uvSrcStart = when {
            uvOffsetPadded + uvNeeded <= data.size -> uvOffsetPadded
            uvOffsetCompact + uvNeeded <= data.size -> uvOffsetCompact
            else -> {
                // Fallback: UV immediately after Y data
                Timber.w("Buffer too small for expected UV offset, using compact layout")
                uvOffsetCompact.coerceAtMost(data.size - 1)
            }
        }
        Timber.d("UV offset: %d (padded=%d, compact=%d)", uvSrcStart, uvOffsetPadded, uvOffsetCompact)

        // Pack into NV21: contiguous Y (w×h) + interleaved VU (w×h/2)
        val nv21Size = w * h * 3 / 2
        val nv21 = ByteArray(nv21Size)

        // Copy Y plane, removing stride padding
        for (row in 0 until h) {
            val srcOffset = row * s
            val dstOffset = row * w
            if (srcOffset + w <= data.size) {
                System.arraycopy(data, srcOffset, nv21, dstOffset, w)
            }
        }

        // Copy UV plane directly — no swap, since we don't know the exact format
        // in this fallback path. Direct copy works if codec emits NV21 natively.
        val uvDstStart = w * h
        for (row in 0 until h / 2) {
            val srcRowOffset = uvSrcStart + row * s
            val dstRowOffset = uvDstStart + row * w
            val copyLen = w.coerceAtMost(data.size - srcRowOffset).coerceAtMost(nv21.size - dstRowOffset)
            if (copyLen > 0) {
                System.arraycopy(data, srcRowOffset, nv21, dstRowOffset, copyLen)
            }
        }

        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, w, h, null)
            val out = ByteArrayOutputStream(w * h)
            yuvImage.compressToJpeg(Rect(0, 0, w, h), 85, out)

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                if (outputWidth > 0 && outputHeight > 0) {
                    inSampleSize = calculateSampleSize(w, h, outputWidth, outputHeight)
                }
            }
            val fullBitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size(), opts)
                ?: return null

            // Scale to target, preserving aspect ratio
            if (outputWidth > 0 && outputHeight > 0) {
                val scaled = Bitmap.createScaledBitmap(fullBitmap, outputWidth, outputHeight, true)
                if (scaled !== fullBitmap) fullBitmap.recycle()
                scaled
            } else {
                fullBitmap
            }
        } catch (e: Exception) {
            Timber.e(e, "YUV conversion failed: %dx%d stride=%d sh=%d buf=%d", w, h, s, sh, data.size)
            null
        }
    }

    private fun calculateSampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        var sample = 1
        var w = srcW
        var h = srcH
        while (w / 2 >= dstW && h / 2 >= dstH) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    fun release() {
        try {
            codec?.stop()
            codec?.release()
            extractor?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing resources")
        }
    }

    private fun MediaFormat.getIntegerSafe(key: String, default: Int): Int {
        return try {
            if (containsKey(key)) getInteger(key) else default
        } catch (_: Exception) {
            default
        }
    }
}
