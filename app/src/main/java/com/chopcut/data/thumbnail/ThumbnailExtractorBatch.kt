package com.chopcut.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.chopcut.data.model.ExtractionStage
import com.chopcut.data.model.PerformanceEvent
import com.chopcut.data.model.ThumbnailQuality
import com.chopcut.util.logging.ActivityLogger
import com.chopcut.util.logging.AppActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.system.measureTimeMillis

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
            Timber.w("No positions to extract, returning empty map")
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
                    } else {
                        Timber.w("Frame at ${positionMs}ms returned null")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to extract frame at ${positionMs}ms")
                }
            }

            PerformanceMonitor.calculateMetrics() // Reportar métricas ao final do batch
            val totalTime = System.currentTimeMillis() - startTime
            val avgTime = if (results.isNotEmpty()) totalTime / results.size else 0
            Timber.i("extractBatch: ${results.size}/${positionsMs.size} frames in ${totalTime}ms (avg ${avgTime}ms/frame)")
            ActivityLogger.finished(AppActivity.ThumbnailExtraction, "extraídos" to results.size, "total" to positionsMs.size, "ms" to totalTime)

        } catch (e: Exception) {
            ActivityLogger.failed(AppActivity.ThumbnailExtraction, "motivo" to e.message)
            Timber.e(e, "extractBatch: Fatal error during batch extraction")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Timber.e(e, "extractBatch: Error releasing MediaMetadataRetriever")
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
        val startTime = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri)
            val bitmap = extractFrameAt(retriever, positionMs, width, height, ThumbnailQuality.HIGH)

            val elapsedTime = System.currentTimeMillis() - startTime
            Timber.d("extractSingle: Completed in ${elapsedTime}ms for frame at ${positionMs}ms")

            bitmap
        } catch (e: Exception) {
            Timber.e(e, "extractSingle: Failed to extract frame at ${positionMs}ms")
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Timber.e(e, "extractSingle: Error releasing MediaMetadataRetriever")
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
            Timber.tag("ThumbnailAspectMonitor").e(e, "❌ EXCEÇÃO durante extração na posição ${positionMs}ms: ${e.message}")
            Timber.w(e, "extractFrameAt: Error extracting frame at ${positionMs}ms")
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
