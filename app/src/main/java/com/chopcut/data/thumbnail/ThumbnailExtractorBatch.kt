package com.chopcut.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.chopcut.data.model.ThumbnailQuality
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
class ThumbnailExtractorBatch(
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
    suspend fun extractBatch(
        uri: Uri,
        positionsMs: List<Long>,
        width: Int = 320,
        height: Int = 180,
        quality: ThumbnailQuality = ThumbnailQuality.HIGH
    ): Map<Long, Bitmap> = withContext(Dispatchers.IO) {
        if (positionsMs.isEmpty()) {
            Timber.w("extractBatch: positionsMs is empty")
            return@withContext emptyMap()
        }

        val startTime = System.currentTimeMillis()
        val results = mutableMapOf<Long, Bitmap>()

        // Ordenar posições para minimizar seeks no vídeo
        val sortedPositions = positionsMs.sorted()
        Timber.d("extractBatch: Starting extraction of ${positionsMs.size} thumbnails from $uri")

        val retriever = MediaMetadataRetriever()
        try {
            // ❌ ANTES: Criava retriever a cada thumbnail (300-500ms cada)
            // ✅ AGORA: Cria UMA vez e reusa para todas as extrações

            retriever.setDataSource(context, uri)

            // Extrair cada posição
            sortedPositions.forEach { positionMs ->
                try {
                    val bitmap = extractFrameAt(retriever, positionMs, width, height, quality)
                    if (bitmap != null) {
                        results[positionMs] = bitmap
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to extract frame at ${positionMs}ms")
                }
            }

            val totalTime = System.currentTimeMillis() - startTime
            val avgTime = if (results.isNotEmpty()) totalTime / results.size else 0

            Timber.d("extractBatch: Completed in ${totalTime}ms (${results.size}/${positionsMs.size} successful, avg ${avgTime}ms per frame)")

        } catch (e: Exception) {
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
        height: Int = 180,
        quality: ThumbnailQuality = ThumbnailQuality.HIGH
    ): Bitmap? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri)
            val bitmap = extractFrameAt(retriever, positionMs, width, height)

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
        quality: ThumbnailQuality = ThumbnailQuality.HIGH
    ): Bitmap? {
        return try {
            // Get frame at position
            // For HIGH quality, we extract slightly larger and scale down with filtering (Anti-Aliasing)
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

            // Aplicar fator de qualidade HIGH se necessário (usando 1.2x para anti-aliasing)
            val finalWidth = if (quality == ThumbnailQuality.HIGH) (extractWidth * 1.2f).toInt() else extractWidth
            val finalHeight = if (quality == ThumbnailQuality.HIGH) (extractHeight * 1.2f).toInt() else extractHeight

            // 🔍 ASPECT MONITOR: Log detalhado de metadados do vídeo e dimensões de extração
            android.util.Log.i("ThumbnailAspectMonitor", """
                ═══════════════════════════════════════════════════════════
                EXTRAÇÃO DE FRAME - Posição: ${positionMs}ms | Qualidade: $quality
                ═══════════════════════════════════════════════════════════
                📹 VÍDEO ORIGINAL:
                   • Dimensões brutas: ${videoWidth}x${videoHeight}
                   • Rotação: ${videoRotation}°
                   • Portrait: $isPortrait
                   • Dimensões reais (pós-rotação): ${realWidth}x${realHeight}
                   • Aspect Ratio Real: ${String.format("%.3f", videoAspectRatio)} (${realWidth}:${realHeight})

                🎯 TARGET SOLICITADO:
                   • Dimensões: ${width}x${height}
                   • Aspect Ratio Target: ${String.format("%.3f", width.toFloat() / height.toFloat())}

                ⚙️ EXTRAÇÃO AJUSTADA:
                   • Dimensões base: ${extractWidth}x${extractHeight}
                   • Aspect Ratio: ${String.format("%.3f", extractWidth.toFloat() / extractHeight.toFloat())}
                   • Dimensões finais (com quality): ${finalWidth}x${finalHeight}
                   • Fator de escala: ${if (quality == ThumbnailQuality.HIGH) "1.2x (HIGH)" else "1.0x (LOW)"}
                ═══════════════════════════════════════════════════════════
            """.trimIndent())

            val rawFrame = retriever.getScaledFrameAtTime(
                positionMs * 1000, // MediaMetadataRetriever usa microssegundos
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                finalWidth,
                finalHeight
            )

            val frame = if (quality == ThumbnailQuality.HIGH && rawFrame != null && (rawFrame.width != width || rawFrame.height != height)) {
                // 🔍 ASPECT MONITOR: Log de rescale para qualidade HIGH
                android.util.Log.i("ThumbnailAspectMonitor", """
                    🔄 RESCALE HIGH QUALITY:
                       • Frame extraído: ${rawFrame.width}x${rawFrame.height} (ratio: ${String.format("%.3f", rawFrame.width.toFloat() / rawFrame.height.toFloat())})
                       • Rescaling para: ${width}x${height} (ratio: ${String.format("%.3f", width.toFloat() / height.toFloat())})
                       • Delta: ${rawFrame.width - width}px largura, ${rawFrame.height - height}px altura
                """.trimIndent())

                val scaled = Bitmap.createScaledBitmap(rawFrame, width, height, true)
                if (scaled != rawFrame) rawFrame.recycle()
                scaled
            } else {
                rawFrame
            }

            if (frame == null) {
                android.util.Log.w("ThumbnailAspectMonitor", "❌ ERRO: Frame NULL retornado na posição ${positionMs}ms")
                Timber.w("extractFrameAt: Got null frame at ${positionMs}ms")
                null
            } else {
                // 🔍 ASPECT MONITOR: Log do frame final
                android.util.Log.i("ThumbnailAspectMonitor", """
                    ✅ FRAME FINAL EXTRAÍDO:
                       • Dimensões: ${frame.width}x${frame.height}
                       • Aspect Ratio: ${String.format("%.3f", frame.width.toFloat() / frame.height.toFloat())}
                       • Config: ${frame.config}
                       • Bytes/pixel: ${when(frame.config) {
                           Bitmap.Config.RGB_565 -> "2"
                           Bitmap.Config.ARGB_8888 -> "4"
                           else -> "unknown"
                       }}
                       • Tamanho estimado: ${(frame.width * frame.height * when(frame.config) {
                           Bitmap.Config.RGB_565 -> 2
                           Bitmap.Config.ARGB_8888 -> 4
                           else -> 4
                       }) / 1024}KB
                    ═══════════════════════════════════════════════════════════
                """.trimIndent())

                frame
            }
        } catch (e: Exception) {
            android.util.Log.e("ThumbnailAspectMonitor", "❌ EXCEÇÃO durante extração na posição ${positionMs}ms: ${e.message}", e)
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
