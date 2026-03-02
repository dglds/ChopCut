package com.chopcut.data.thumbnail

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    private var appContext: Context? = null
    
    // Cache em memória LRU (100 strips ~43MB)
    private val memoryCache = ThumbnailCache(maxSize = 100)
    
    // Gerenciador de strips (já existe, vamos reutilizar)
    private var stripManager: ThumbnailStripManager? = null
    
    // Tracking de jobs
    private val jobsLock = Mutex()
    private val videoJobs = mutableMapOf<String, Job>()
    private val segmentJobs = mutableMapOf<String, Job>()
    
    /**
     * Inicializa o ThumbnailCacheManager com o contexto da aplicação.
     * Deve ser chamado no Application.onCreate()
     */
    fun init(context: Context) {
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
    
    /**
     * Configura o ThumbnailStripManager com as dimensões corretas.
     * Deve ser chamado antes de extrair strips para um novo vídeo.
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
        ensureInitialized()
        
        // Configurar stripManager se necessário
        if (stripManager == null || 
            stripManager!!.thumbWidth != thumbWidth || 
            stripManager!!.thumbHeight != thumbHeight || 
            stripManager!!.thumbsPerStrip != thumbsPerStrip) {
            configureStripManager(thumbWidth, thumbHeight, thumbsPerStrip)
        }
        
        val uriString = uri.toString()
        val positionKey = segmentIndex.toLong()
        
        // Tentar cache-aside: memória → disco → extração
        return memoryCache.getOrPut(uriString, positionKey) {
            // Cache miss: tentar disco ou extrair
            stripManager!!.extractSegment(uri, segmentIndex, durationMs) 
                ?: throw NoSuchElementException("Failed to extract segment $segmentIndex")
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
        
        // Carregar segmentos iniciais prioritários de forma síncrona
        Timber.d("Starting preload for $uri: $initialSegments initial segments out of $segmentCount total")
        for (segIdx in 0 until minOf(initialSegments, segmentCount)) {
            val strip = getStrip(uri, segIdx, durationMs, thumbWidth, thumbHeight, thumbsPerStrip)
            if (strip != null) {
                strips[segIdx] = strip
                Timber.d("Preloaded segment $segIdx/$segmentCount")
            } else {
                Timber.w("Failed to preload segment $segIdx/$segmentCount")
            }
        }
        
        Timber.d("Preload completed: ${strips.size} segments loaded")
        
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
     * Verifica se foi inicializado corretamente.
     */
    private fun ensureInitialized() {
        if (appContext == null) {
            throw IllegalStateException("ThumbnailCacheManager not initialized. Call init(context) first.")
        }
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
