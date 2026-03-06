package com.chopcut.data.thumbnail

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import com.chopcut.config.constants.ThumbnailConstants
import com.chopcut.config.constants.CacheConstants
import com.chopcut.config.constants.PerformanceConstants
import com.chopcut.data.local.PreferencesManager
import com.chopcut.data.model.ThumbnailQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.math.pow

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
    /** Gerenciador de preferências para verificar se cache está habilitado */
    private val prefsManager = PreferencesManager(context)

    /** Configuração de strips adaptativas */
    private val minThumbsPerStrip = ThumbnailConstants.Quality.MIN_THUMBS_PER_STRIP
    
    companion object {
        /** Limite de concorrência para extração (baseado no hardware) */
        private val extractSemaphore = Semaphore(PerformanceConstants.ThreadCounts.calculateOptimalThreads(Runtime.getRuntime().availableProcessors()))

        /** Limite de concorrência para I/O de escrita (permite escrita paralela) */
        private val ioSemaphore = Semaphore(ThumbnailConstants.Concurrency.IO_SEMAPHORE_PERMITS)

        /** Diretório de cache para strips */
        private const val CACHE_DIR = "thumbnail_strips"

        /** Tamanho máximo do cache em bytes */
        private const val MAX_CACHE_SIZE = ThumbnailConstants.Cache.MAX_CACHE_SIZE

        /** Versão do cache para invalidação manual ao mudar formatos/lógica */
        private const val CACHE_VERSION = ThumbnailConstants.Cache.CACHE_VERSION

        /** Qualidade para compressão das strips */
        private const val COMPRESSION_QUALITY = ThumbnailConstants.Quality.STRIP_COMPRESSION_QUALITY

        init {
            val optimalThreads = PerformanceConstants.ThreadCounts.calculateOptimalThreads(Runtime.getRuntime().availableProcessors())
            Timber.tag("ThumbnailStrip").i("""
                ╔═════════════════════════════════════════════════════════╗
                ║     THUMBNAIL STRIP MANAGER - CONFIGURAÇÃO             ║
                ╚═════════════════════════════════════════════════════════╝
                
                📊 HARDWARE DETECTADO:
                   • CPU Cores: ${Runtime.getRuntime().availableProcessors()}
                   • Threads de extração: $optimalThreads (máximo)
                   • Threads de I/O: ${ThumbnailConstants.Concurrency.IO_SEMAPHORE_PERMITS} (escrita paralela)
                 
                 💡 ESTRATÉGIA ADOTADA:
                    ${when {
                        Runtime.getRuntime().availableProcessors() <= 2 -> "→ Baixo custo: ${PerformanceConstants.ThreadCounts.LOW_END} threads (mínimo para overhead)"
                        Runtime.getRuntime().availableProcessors() <= 4 -> "→ Médio: ${PerformanceConstants.ThreadCounts.MID_RANGE} threads (equilíbrio)"
                        Runtime.getRuntime().availableProcessors() <= 6 -> "→ High-end: ${PerformanceConstants.ThreadCounts.HIGH_END} threads (alto throughput)"
                        else -> "→ Muito potente: ${PerformanceConstants.ThreadCounts.MAX} threads (máximo throughput)"
                    }}
                 
                 🚀 OTIMIZAÇÕES HABILITADAS:
                    ✓ Threads dinâmicas baseadas no hardware
                    ✓ Escrita paralela (até ${ThumbnailConstants.Concurrency.IO_SEMAPHORE_PERMITS} strips simultâneas)
                    ✓ Cache LRU em disco (${ThumbnailConstants.Cache.MAX_CACHE_SIZE / 1024 / 1024}MB)
                    ✓ Compressão WEBP (${ThumbnailConstants.Quality.STRIP_COMPRESSION_QUALITY}% qualidade)
                 ╚═════════════════════════════════════════════════════════╝
            """.trimIndent())
        }
        
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
            minThumbsPerStrip: Int = ThumbnailConstants.Quality.MIN_THUMBS_PER_STRIP
        ): Int {
            if (totalSegments <= 1) return maxThumbsPerStrip

            // Progresso normalizado (0.0 a 1.0)
            val progress = segmentIndex.toFloat() / (totalSegments - 1).toFloat()

            // Curva de potência suave (ex: progress^0.5)
            // Isso faz o crescimento ser mais rápido no início, mais lento depois
            val power = ThumbnailConstants.Quality.ADAPTIVE_POWER_CURVE_EXPONENT
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
                    Timber.d("ThumbnailStrip: Cache cleared successfully")
                }
            } catch (e: Exception) {
                Timber.e(e, "ThumbnailStrip: Failed to clear cache")
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
        
        if (adaptiveStrips && (segmentIndex == 0 || segmentIndex == totalSegments / 2 || segmentIndex == totalSegments - 1)) {
            Timber.d("AdaptiveStrip: Segment $segmentIndex/$totalSegments -> $result thumbs")
        }
        
        return result
    }
    
    /**
     * Loga a estratégia de strips adaptativas para debug.
     */
    fun logAdaptiveStrategy(totalSegments: Int) {
        if (!adaptiveStrips) {
            Timber.i("AdaptiveStrip: DISABLED - using fixed $thumbsPerStrip thumbs per strip")
            return
        }
        
        Timber.i("╔═════════════════════════════════════════════════════════╗")
        Timber.i("║      ADAPTIVE STRIP STRATEGY - $totalSegments segments    ║")
        Timber.i("╚═════════════════════════════════════════════════════════╝")
        
        val sampleSegments = listOf(0, 1, 2, 5, 10, 15, 20, 30, 40, totalSegments - 1)
        sampleSegments.filter { it in 0 until totalSegments }.forEach { segIdx ->
            val thumbs = getThumbsPerStripForSegment(segIdx, totalSegments)
            val progress = (segIdx.toFloat() / (totalSegments - 1) * 100).toInt()
            Timber.i("   Segment $segIdx (progress: $progress%) → $thumbs thumbs/strip")
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
            Timber.w(e, "Failed to get file identifier, using URI as fallback")
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
                if (elapsed > ThumbnailConstants.Timing.CACHE_READ_THRESHOLD_MS) {
                    Timber.w("⚠️ Slow disk cache read: ${elapsed}ms for segment $segmentIndex (threshold: ${ThumbnailConstants.Timing.CACHE_READ_THRESHOLD_MS}ms)")
                } else {
                    Timber.d("✓ Cache HIT for segment $segmentIndex (${elapsed}ms)")
                }
            } else {
                Timber.tag("StripPerf").w("🔴 Cache file corrupted for segment $segmentIndex")
                // Delete sem lock, filesystem cuida da atomicidade
                cacheFile.delete()
                // Record corruption in metrics
                ThumbnailCacheManager.recordCorruption()
            }
            
            return bitmap
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailStrip: Failed to load from cache for segment $segmentIndex")
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
                    Timber.tag("StripPerf").d("💾 Cached segment $segmentIndex (compress=${compressTime}ms, total=${elapsed}ms, ${sizeKB}KB)")

                    trimCacheIfNeeded()
                    return true
                } else {
                    tempFile.delete()
                    Timber.tag("StripPerf").w("⚠️ Failed to rename temp file for segment $segmentIndex")
                    return false
                }
            }
        } catch (e: Exception) {
            Timber.tag("StripPerf").e(e, "🔴 Failed to save cache for segment $segmentIndex")
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
                Timber.tag("StripPerf").w("🟠 Cache size ${currentSizeMB}MB exceeds limit ${maxSizeMB}MB, trimming...")

                // Ordenar por lastModified (mais antigos primeiro)
                val sortedFiles: List<File> = cacheFiles.sortedBy { file -> file.lastModified() }
                val filesToDelete: List<File> = sortedFiles.take(cacheFiles.size / CacheConstants.Thumbnail.TRIM_RATIO_DIVISOR)

                var deletedSize = 0L
                filesToDelete.forEach { file: File ->
                    try {
                        deletedSize += file.length()
                        file.delete()
                    } catch (e: Exception) {
                        Timber.tag("StripPerf").w(e, "Failed to delete cache file: ${file.name}")
                    }
                }

                Timber.tag("StripPerf").i("🟢 Trimmed cache: deleted ${filesToDelete.size} files, ${deletedSize / 1024}KB")

                // Record eviction in metrics
                ThumbnailCacheManager.recordEviction()
            } catch (e: Exception) {
                Timber.tag("StripPerf").e(e, "🔴 Failed to trim cache")
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
        onlyFirstFrame: Boolean = false
    ): Bitmap? {
        val totalSegments = getSegmentCount(durationMs)
        return extractSegment(uri, segmentIndex, durationMs, totalSegments, onlyFirstFrame)
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
        onlyFirstFrame: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        Timber.tag("StripPerf").d("=== extractSegment STARTED ===")
        Timber.tag("StripPerf").d("segmentIndex: $segmentIndex, totalSegments: $totalSegments, durationMs: $durationMs")

        // Fail-fast se o job já foi cancelado
        coroutineContext.ensureActive()

                // MELHORIA: Tentar carregar do cache primeiro (se habilitado)
                Timber.tag("StripPerf").d("Cache enabled: ${prefsManager.thumbnailCacheEnabled}")
                if (prefsManager.thumbnailCacheEnabled) {
                    val cacheKey = getCacheKey(uri, segmentIndex, onlyFirstFrame)
                    val cachedStrip = loadFromCache(uri, segmentIndex, onlyFirstFrame)
                    if (cachedStrip != null) {
                        val cacheHitTime = System.currentTimeMillis() - startTime
                        Timber.tag("StripPerf").i("🔵 CACHE HIT - Segment $segmentIndex ${if (onlyFirstFrame) "(overview)" else ""} loaded from disk, time=${cacheHitTime}ms, size=${cachedStrip.width}x${cachedStrip.height}")
                        return@withContext cachedStrip
                    }
    
                    Timber.tag("StripPerf").i("🟠 CACHE MISS - Segment $segmentIndex ${if (onlyFirstFrame) "(overview)" else ""} will be extracted")
                } else {
                    Timber.tag("StripPerf").d("Cache disabled - Segment $segmentIndex will be extracted")
                }
    
        extractSemaphore.withPermit {
            // Verificar novamente após adquirir permissão (pode ter demorado na fila)
            coroutineContext.ensureActive()
    
            // Calcular thumbsPerStrip adaptativo se habilitado
            val currentThumbsPerStrip = if (onlyFirstFrame) 1 else getThumbsPerStripForSegment(segmentIndex, totalSegments)
            
            val totalSeconds = ((durationMs + 999) / 1000).toInt()
            
            // Se for overview, usamos o índice para pular o intervalo correto
            val segDuration = getThumbsPerStripForSegment(segmentIndex, totalSegments)
            val startSec = segmentIndex * segDuration
            
            if (startSec >= totalSeconds) return@withPermit null
    
            val framesInSegment = if (onlyFirstFrame) 1 else minOf(currentThumbsPerStrip, totalSeconds - startSec)

              try {
                Timber.d("Extracting segment $segmentIndex: $framesInSegment frames, strip size=${thumbWidth * framesInSegment}x$thumbHeight")
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

                val extractedFrames = batchExtractor.extractBatch(
                    uri = uri,
                    positionsMs = positions,
                    width = thumbWidth,
                    height = thumbHeight
                )

                Timber.d("Batch extraction returned ${extractedFrames.size} frames for segment $segmentIndex")

                if (extractedFrames.isEmpty()) {
                    Timber.tag("ThumbnailAspectMonitor").w("❌ ERRO: Nenhum frame extraído para segmento $segmentIndex")
                    Timber.e("ThumbnailStrip: No frames extracted for segment $segmentIndex")
                    return@withPermit null
                }

                Timber.tag("ThumbnailAspectMonitor").i("   ✅ Extraídos ${extractedFrames.size}/$framesInSegment frames com sucesso")

                // Stitch frames na strip
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
                        Timber.w("ThumbnailStrip: Failed to extract frame at sec=$sec")
                    }
                }

                Timber.d("ThumbnailStrip: Segment $segmentIndex ($framesInSegment frames, ${stripWidth}x$thumbHeight, RGB_565) - BATCH MODE")

                val totalTime = System.currentTimeMillis() - startTime
                Timber.tag("StripPerf").i("⚪ extractSegment COMPLETED for segment $segmentIndex, time=${totalTime}ms")
                Timber.tag("StripPerf").d("=== ThumbnailStripManager.extractSegment COMPLETED for segment $segmentIndex ===")

                // MELHORIA: Salvar no cache após extração bem-sucedida (se habilitado)
                if (prefsManager.thumbnailCacheEnabled) {
                    Timber.tag("StripPerf").d("Saving segment $segmentIndex ${if (onlyFirstFrame) "(overview)" else ""} to cache...")
                    saveToCache(uri, segmentIndex, strip, onlyFirstFrame)
                }

                strip
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag("ThumbnailAspectMonitor").e(e, "❌ EXCEÇÃO FATAL ao extrair segmento $segmentIndex: ${e.message}")
                Timber.e(e, "ThumbnailStrip: Fatal error extracting segment $segmentIndex")
                null
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
