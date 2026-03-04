package com.chopcut.data.thumbnail

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
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
    
    // Tracking de jobs
    private val jobsLock = Mutex()
    private val videoJobs = mutableMapOf<String, Job>()
    private val segmentJobs = mutableMapOf<String, Job>()
    
    /**
     * Inicializa o ThumbnailCacheManager com o contexto da aplicação.
     * Deve ser chamado no Application.onCreate()
     * Thread-safe usando @Volatile + Mutex
     */
    suspend fun init(context: Context) {
        initLock.withLock {
            if (appContext != null) {
                Timber.d("ThumbnailCacheManager: Já inicializado, ignorando")
                return@withLock
            }
            
            appContext = context.applicationContext
            
            Timber.i("""
                ╔═════════════════════════════════════════════════════════╗
                ║          THUMBNAIL CACHE MANAGER - INICIALIZADO        ║
                ╚═════════════════════════════════════════════════════════╝
                
                🗄️  CACHE EM MEMÓRIA (LRU):
                   • Capacidade: 100 strips (~43MB)
                   • Auto-eviction: Least Recently Used
                 
                💾  CACHE EM DISCO:
                   • Formato: WEBP (70% qualidade)
                   • Capacidade: 200MB
                   • LRU baseado em lastModified
                 
                🔗 ARQUITETURA:
                   • Memória: ThumbnailCache (LRU)
                   • Disco: ThumbnailStripManager
                   • Jobs: Tracking inteligente por vídeo/segmento
                 ╚═════════════════════════════════════════════════════════╝
            """.trimIndent())
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

        Timber.d("ThumbnailCacheManager: StripManager configured with thumbWidth=$thumbWidth, thumbHeight=$thumbHeight, thumbsPerStrip=$thumbsPerStrip")
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
        thumbsPerStrip: Int
    ): Bitmap? {
        Timber.d("ThumbnailCacheManager.getStrip called: segment=$segmentIndex, duration=$durationMs, size=${thumbWidth}x$thumbHeight")
        ensureInitialized()

        // ✅ OTIMIZAÇÃO: Configurar stripManager APENAS se realmente necessário
        // Se as dimensões forem as mesmas, REUSAR o stripManager existente
        // Isso evita recriar instâncias e melhora hit rate do cache de disco
        val needsNewStripManager = stripManager == null ||
            stripManager!!.thumbWidth != thumbWidth ||
            stripManager!!.thumbHeight != thumbHeight ||
            stripManager!!.thumbsPerStrip != thumbsPerStrip

        if (needsNewStripManager) {
            configureStripManager(thumbWidth, thumbHeight, thumbsPerStrip)
            Timber.d("ThumbnailCacheManager: Configured new stripManager for dimensions ${thumbWidth}x${thumbHeight}")
        } else {
            Timber.d("ThumbnailCacheManager: Reusing existing stripManager")
        }

        val uriString = uri.toString()
        val positionKey = segmentIndex.toLong()

        // ✅ MELHORIA: Verificar se bitmap do cache ainda é válido antes de retornar
        val cached = memoryCache.get(uriString, positionKey)
        if (cached != null && !cached.isRecycled) {
            Timber.d("ThumbnailCacheManager: Cache HIT (bitmap válido) for segment $segmentIndex")
            return cached
        } else if (cached != null && cached.isRecycled) {
            Timber.w("ThumbnailCacheManager: Cache HIT mas bitmap está reciclado, removendo do cache for segment $segmentIndex")
            memoryCache.remove(uriString, positionKey)
            // Continua para cache miss abaixo
        } else {
            Timber.d("ThumbnailCacheManager: Cache MISS for segment $segmentIndex, will extract")
        }

        // Tentar cache-aside: memória → disco → extração
        return memoryCache.getOrPut(uriString, positionKey) {
            // Cache miss: tentar disco ou extrair
            Timber.d("ThumbnailCacheManager: Extracting segment $segmentIndex (cache miss)")
            val strip = stripManager!!.extractSegment(uri, segmentIndex, durationMs)
            if (strip != null) {
                Timber.d("ThumbnailCacheManager: Segment $segmentIndex extracted successfully, size=${strip.width}x${strip.height}")
            } else {
                Timber.e("ThumbnailCacheManager: Failed to extract segment $segmentIndex, returning null")
            }
            strip ?: throw NoSuchElementException("Failed to extract segment $segmentIndex")
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
        
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            try {
                val strip = getStrip(uri, segmentIndex, durationMs, thumbWidth, thumbHeight, thumbsPerStrip)
                
                withContext(Dispatchers.Main) {
                    onResult(strip)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.d("Segment job cancelled: $jobKey")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load strip: $jobKey")
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
        Timber.d("Cancelling all jobs for URI: $uri")
        
        CoroutineScope(Dispatchers.IO).launch {
            jobsLock.withLock {
                val uriKey = uri.toString()
                
                // Cancelar job do vídeo
                videoJobs[uriKey]?.cancel()
                videoJobs.remove(uriKey)
                
                // Cancelar todos os jobs de segmentos para este URI
                val segmentsToCancel = segmentJobs.keys.filter { it.startsWith("${uri}_") }
                segmentsToCancel.forEach { key ->
                    segmentJobs[key]?.cancel()
                    segmentJobs.remove(key)
                }
                
                Timber.d("Cancelled ${segmentsToCancel.size + 1} jobs for $uri")
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
        Timber.d("Checking for far jobs from segment $currentSegment (threshold: $threshold)")
        
        CoroutineScope(Dispatchers.IO).launch {
            jobsLock.withLock {
                val uriPrefix = "${uri}_"
                
                val jobsToCancel = segmentJobs.keys
                    .filter { it.startsWith(uriPrefix) }
                    .mapNotNull { key ->
                        key.removePrefix(uriPrefix).toIntOrNull()?.let { idx -> key to idx }
                    }
                    .filter { (_, segIdx) -> abs(segIdx - currentSegment) > threshold }
                
                if (jobsToCancel.isNotEmpty()) {
                    Timber.d("Cancelling ${jobsToCancel.size} far jobs (distance > $threshold)")
                }
                
                jobsToCancel.forEach { (key, _) ->
                    segmentJobs[key]?.cancel()
                    segmentJobs.remove(key)
                    Timber.v("Cancelled far job: $key (distance > $threshold)")
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
            Timber.d("Cancelled ${segmentsToCancel.size} segment jobs for $uri")
        }
        
        val strips = mutableMapOf<Int, Bitmap>()

        // Carregar segmentos iniciais prioritários de forma PARALELA
        Timber.d("Starting PARALLEL preload for $uri: $initialSegments initial segments out of $segmentCount total")
        val preloadStartTime = System.currentTimeMillis()

        // Usar coroutineScope para criar escopo estruturado para async
        coroutineScope {
            // Lançar todas as extrações em paralelo usando async
            val jobs = (0 until minOf(initialSegments, segmentCount)).map { segIdx ->
                async(Dispatchers.IO) {
                    try {
                        val strip = getStrip(uri, segIdx, durationMs, thumbWidth, thumbHeight, thumbsPerStrip)
                        if (strip != null) {
                            segIdx to strip
                        } else {
                            Timber.w("Failed to preload segment $segIdx/$segmentCount")
                            null
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error preloading segment $segIdx")
                        null
                    }
                }
            }

            // Aguardar conclusão de todas as extrações e adicionar ao mapa
            jobs.awaitAll().filterNotNull().forEach { (idx, bitmap) ->
                strips[idx] = bitmap
                Timber.d("Preloaded segment $idx/$segmentCount")
            }
        }

        val preloadElapsedMs = System.currentTimeMillis() - preloadStartTime
        val avgTimePerStrip = if (strips.isNotEmpty()) preloadElapsedMs / strips.size else 0
        Timber.i("⚡ PARALLEL PRELOAD completed: ${strips.size}/${initialSegments} segments in ${preloadElapsedMs}ms (avg: ${avgTimePerStrip}ms/strip)")
        
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
        
        Timber.i(memStats.toLogString())
        
        Timber.i("""
            ╔═════════════════════════════════════════════════════════╗
            ║          THUMBNAIL CACHE MANAGER - JOBS ATIVOS          ║
            ╚═════════════════════════════════════════════════════════╝
            
            🔨 JOBS DE VÍDEO:
               • Ativos: ${stats.activeVideoJobsCount}
               • Uso por vídeo: 0 ou 1 (singleton)
            
            🎞️  JOBS DE SEGMENTO:
               • Ativos: ${stats.activeSegmentJobsCount}
               • Cancelamento inteligente: habilitado
               • Distância máxima: 5 segmentos
            ╚═════════════════════════════════════════════════════════╝
        """.trimIndent())
    }
    
    /**
     * Limpa cache de memória LRU.
     */
    fun clearMemoryCache() {
        memoryCache.clear()
        Timber.d("ThumbnailCacheManager: Memory cache cleared")
    }
    
    /**
     * Limpa todo o cache (disco e memória).
     */
    fun clearAll() {
        appContext?.let { ThumbnailStripManager.clearCache(it) }
        clearMemoryCache()
        Timber.i("ThumbnailCacheManager: All caches cleared")
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
}
