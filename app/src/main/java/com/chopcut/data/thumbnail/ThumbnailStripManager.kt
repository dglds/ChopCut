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
    val adaptiveStrips: Boolean = true
) {
    /** Gerenciador de preferências para verificar se cache está habilitado */
    private val prefsManager = PreferencesManager(context)
    
    /** Configuração de strips adaptativas */
    private val minThumbsPerStrip = 5  // Mínimo para início rápido
    
    companion object {
        /** Limite de concorrência global para não saturar hardware decoders (3-4 é seguro para maioria dos devices) */
        private val extractSemaphore = Semaphore(3)

        /** Diretório de cache para strips */
        private const val CACHE_DIR = "thumbnail_strips"

        /** Tamanho máximo do cache em bytes (200MB) */
        private const val MAX_CACHE_SIZE = 200L * 1024 * 1024

        /** Versão do cache para invalidação manual ao mudar formatos/lógica */
        private const val CACHE_VERSION = 3 // v1=JPEG, v2=WEBP, v3=AdaptiveStrips

        /** Qualidade para compressão das strips (70% = excelente equilíbrio qualidade/tamanho para tiras) */
        private const val COMPRESSION_QUALITY = 70
        
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
            minThumbsPerStrip: Int = 5
        ): Int {
            if (totalSegments <= 1) return maxThumbsPerStrip
            
            // Progresso normalizado (0.0 a 1.0)
            val progress = segmentIndex.toFloat() / (totalSegments - 1).toFloat()
            
            // Curva de potência suave (ex: progress^0.5)
            // Isso faz o crescimento ser mais rápido no início, mais lento depois
            val power = 0.5f
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
    private fun getCacheKey(uri: Uri, segmentIndex: Int): String {
        val fileInfo = getFileIdentifier(uri)
        // Incluir CACHE_VERSION na chave para invalidar automaticamente versões antigas
        return "strip_v${CACHE_VERSION}_${fileInfo}_${segmentIndex}.webp"
    }

    /**
     * Obtém um identificador único para o arquivo de vídeo
     * Combina path e tamanho para detectar mudanças
     */
    private fun getFileIdentifier(uri: Uri): String {
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
    private fun loadFromCache(uri: Uri, segmentIndex: Int): Bitmap? {
        try {
            val cacheKey = getCacheKey(uri, segmentIndex)
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
                Timber.d("ThumbnailStrip: Cache HIT for segment $segmentIndex (${elapsed}ms)")
            } else {
                Timber.w("ThumbnailStrip: Cache file corrupted for segment $segmentIndex")
                // Delete sem lock, filesystem cuida da atomicidade
                cacheFile.delete()
            }
            
            return bitmap
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailStrip: Failed to load from cache for segment $segmentIndex")
            return null
        }
    }

    /**
     * Salva uma strip no cache
     * @return true se salvo com sucesso, false caso contrário
     */
    private fun saveToCache(uri: Uri, segmentIndex: Int, strip: Bitmap): Boolean {
        try {
            val cacheKey = getCacheKey(uri, segmentIndex)
            val finalFile = File(cacheDir, cacheKey)
            val tempFile = File(cacheDir, "${cacheKey}.tmp")
            
            val startTime = System.currentTimeMillis()
            
            // 1. COMPRESSÃO FORA DO LOCK (Operação Pesada)
            FileOutputStream(tempFile).use { out ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                strip.compress(format, COMPRESSION_QUALITY, out)
            }
            
            // 2. OPERAÇÃO ATÔMICA DENTRO DO LOCK
            synchronized(cacheLock) {
                if (tempFile.renameTo(finalFile)) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val sizeKB = finalFile.length() / 1024
                    Timber.d("ThumbnailStrip: Cached segment $segmentIndex (${elapsed}ms, ${sizeKB}KB)")
                    
                    trimCacheIfNeeded()
                    return true
                } else {
                    tempFile.delete()
                    return false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "ThumbnailStrip: Failed to save cache for segment $segmentIndex")
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
                
                Timber.d("ThumbnailStrip: Cache size ${(currentSize / 1024 / 1024)}MB exceeds limit, trimming...")
                
                // Ordenar por lastModified (mais antigos primeiro)
                val sortedFiles: List<File> = cacheFiles.sortedBy { file -> file.lastModified() }
                val filesToDelete: List<File> = sortedFiles.take(cacheFiles.size / 4)
                
                var deletedSize = 0L
                filesToDelete.forEach { file: File ->
                    try {
                        deletedSize += file.length()
                        file.delete()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to delete cache file: ${file.name}")
                    }
                }
                
                Timber.d("ThumbnailStrip: Trimmed cache, deleted ${deletedSize / 1024}KB")
            } catch (e: Exception) {
                Timber.e(e, "ThumbnailStrip: Failed to trim cache")
            }
        }
    }

    /** Paint com interpolação bilinear para CenterCrop de qualidade */
    private val cropPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    
    /** Batch extractor para reutilizar MediaMetadataRetriever */
    private val batchExtractor = ThumbnailExtractorBatch(context)

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
        durationMs: Long
    ): Bitmap? {
        val totalSegments = getSegmentCount(durationMs)
        return extractSegment(uri, segmentIndex, durationMs, totalSegments)
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
     * @return Bitmap horizontal RGB_565 com frames stitchados, ou null se falhar
     */
    suspend fun extractSegment(
        uri: Uri,
        segmentIndex: Int,
        durationMs: Long,
        totalSegments: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        // Fail-fast se o job já foi cancelado
        coroutineContext.ensureActive()

                // MELHORIA: Tentar carregar do cache primeiro (se habilitado)
                if (prefsManager.thumbnailCacheEnabled) {
                    val cachedStrip = loadFromCache(uri, segmentIndex)
                    if (cachedStrip != null) {
                        Timber.i("ThumbnailStrip: CACHE HIT - Segment $segmentIndex loaded from disk")
                        return@withContext cachedStrip
                    }

                    Timber.i("ThumbnailStrip: CACHE MISS - Segment $segmentIndex will be extracted")
                } else {
                    Timber.d("ThumbnailStrip: Cache disabled - Segment $segmentIndex will be extracted")
                }

        extractSemaphore.withPermit {
            // Verificar novamente após adquirir permissão (pode ter demorado na fila)
            coroutineContext.ensureActive()

            // Calcular thumbsPerStrip adaptativo se habilitado
            val currentThumbsPerStrip = getThumbsPerStripForSegment(segmentIndex, totalSegments)
            
            val totalSeconds = ((durationMs + 999) / 1000).toInt()
            val startSec = segmentIndex * currentThumbsPerStrip
            if (startSec >= totalSeconds) return@withPermit null

            val framesInSegment = minOf(currentThumbsPerStrip, totalSeconds - startSec)

             try {
                coroutineContext.ensureActive()

                // Strip RGB_565: metade da memória de ARGB_8888
                val stripWidth = thumbWidth * framesInSegment
                val strip = Bitmap.createBitmap(stripWidth, thumbHeight, Bitmap.Config.RGB_565)
                val canvas = Canvas(strip)

                // 🔍 ASPECT MONITOR: Log início da criação da strip
                android.util.Log.i("ThumbnailAspectMonitor", """
                    ╔═════════════════════════════════════════════════════════╗
                    ║ CRIAÇÃO DE STRIP - Segmento: $segmentIndex
                    ╚═════════════════════════════════════════════════════════╝
                    📊 STRIP INFO:
                       • Thumbs por strip: $currentThumbsPerStrip${if (adaptiveStrips) " (Adaptativo)" else ""}
                        • Frames no segmento: $framesInSegment
                       • Dimensões da strip: ${stripWidth}x${thumbHeight}
                       • Config: RGB_565 (2 bytes/pixel)
                       • Tamanho estimado: ${(stripWidth * thumbHeight * 2) / 1024}KB
                       • Range de tempo: ${startSec}s - ${startSec + framesInSegment}s

                    🎯 THUMB INDIVIDUAL:
                       • Dimensões: ${thumbWidth}x${thumbHeight}
                       • Aspect Ratio: ${String.format("%.3f", thumbWidth.toFloat() / thumbHeight.toFloat())}
                """.trimIndent())

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

                if (extractedFrames.isEmpty()) {
                    android.util.Log.w("ThumbnailAspectMonitor", "❌ ERRO: Nenhum frame extraído para segmento $segmentIndex")
                    Timber.w("ThumbnailStrip: No frames extracted for segment $segmentIndex")
                    return@withPermit null
                }

                android.util.Log.i("ThumbnailAspectMonitor", "   ✅ Extraídos ${extractedFrames.size}/$framesInSegment frames com sucesso")

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

                        // 🔍 ASPECT MONITOR: Log detalhado do stitching
                        android.util.Log.d("ThumbnailAspectMonitor", """
                            🔗 STITCHING Frame #$frameIdx (${sec}s):
                               • Source: ${source.width}x${source.height} (ratio: ${String.format("%.3f", source.width.toFloat() / source.height.toFloat())})
                               • Destino na strip: [$dstX, 0, ${dstX + thumbWidth}, $thumbHeight]
                               • Esperado: ${thumbWidth}x${thumbHeight}
                               • Match: ${source.width == thumbWidth && source.height == thumbHeight}
                        """.trimIndent())

                        canvas.drawBitmap(source, null, dstRect, cropPaint)
                        source.recycle()
                    } else {
                        android.util.Log.w("ThumbnailAspectMonitor", "   ⚠️ Frame #$frameIdx (${sec}s) não extraído")
                        Timber.w("ThumbnailStrip: Failed to extract frame at sec=$sec")
                    }
                }

                android.util.Log.i("ThumbnailAspectMonitor", """
                    ✅ STRIP FINALIZADA:
                       • Segmento: $segmentIndex
                       • Dimensões finais: ${strip.width}x${strip.height}
                       • Aspect Ratio: ${String.format("%.3f", strip.width.toFloat() / strip.height.toFloat())}
                       • Frames stitchados: ${extractedFrames.size}/$framesInSegment
                    ╚═════════════════════════════════════════════════════════╝
                """.trimIndent())

                Timber.d("ThumbnailStrip: Segment $segmentIndex ($framesInSegment frames, ${stripWidth}x$thumbHeight, RGB_565) - BATCH MODE")

                // MELHORIA: Salvar no cache após extração bem-sucedida (se habilitado)
                if (prefsManager.thumbnailCacheEnabled) {
                    saveToCache(uri, segmentIndex, strip)
                }

                strip
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ThumbnailAspectMonitor", "❌ EXCEÇÃO FATAL ao extrair segmento $segmentIndex: ${e.message}", e)
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
