package com.chopcut.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Gerenciador de strips segmentadas de thumbnails para o timeline.
 *
 * Combina thumbnails em strips horizontais de [SEGMENT_SECONDS] segundos cada.
 * Dimensões dos thumbnails são calculadas pelo chamador baseado na densidade
 * do display, garantindo rendering pixel-perfect sem upscaling.
 *
 * Otimizações de qualidade:
 * - [Bitmap.Config.RGB_565]: 50% menos memória (thumbs não precisam de alpha)
 * - [Paint.isFilterBitmap]: interpolação bilinear no CenterCrop (evita blocos)
 * - Dimensões density-aware: aspecto 3:2 matching o display (sem distorção)
 *
 * @param context Context do Android
 * @param thumbWidth Largura de cada thumbnail em pixels (deve corresponder a pxPerSecond)
 * @param thumbHeight Altura de cada thumbnail em pixels (deve corresponder a thumbnailHeightPx)
 */
class ThumbnailStripManager(
    private val context: Context,
    val thumbWidth: Int,
    val thumbHeight: Int
) {
    companion object {
        /** Número de thumbnails (segundos) por strip */
        const val SEGMENT_SECONDS = 10
        
        /** Limite de concorrência global para não saturar hardware decoders (3-4 é seguro para maioria dos devices) */
        private val extractSemaphore = Semaphore(3)

        /**
         * Limpa todo o cache de thumbnails do disco.
         * Deve ser chamado em background (IO).
         */
        fun clearCache(context: Context) {
            try {
                val cacheDir = File(context.cacheDir, "thumbs_v1")
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

    /** Paint com interpolação bilinear para CenterCrop de qualidade */
    private val cropPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Extrai uma strip para o segmento especificado.
     *
     * A strip final usa RGB_565 (2 bytes/pixel vs 4 do ARGB_8888).
     * Para um thumbWidth=180, thumbHeight=120, segment=10:
     *   1800 × 120 × 2 bytes = 432KB por strip
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
    ): Bitmap? = withContext(Dispatchers.IO) {
        // Fail-fast se o job já foi cancelado
        coroutineContext.ensureActive()

        extractSemaphore.withPermit {
            // Verificar novamente após adquirir permissão (pode ter demorado na fila)
            coroutineContext.ensureActive()

            val startSec = segmentIndex * SEGMENT_SECONDS
            val totalSeconds = ((durationMs + 999) / 1000).toInt()
            if (startSec >= totalSeconds) return@withPermit null

            val framesInSegment = minOf(SEGMENT_SECONDS, totalSeconds - startSec)

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                
                coroutineContext.ensureActive()

                // Strip RGB_565: metade da memória de ARGB_8888
                val stripWidth = thumbWidth * framesInSegment
                val strip = Bitmap.createBitmap(stripWidth, thumbHeight, Bitmap.Config.RGB_565)
                val canvas = Canvas(strip)

                // Extrair 1.0x (nativo) para melhor qualidade (equilíbrio)
                val extractWidth = thumbWidth
                val extractHeight = thumbHeight
                val dstAspect = thumbWidth.toFloat() / thumbHeight

                for (frameIdx in 0 until framesInSegment) {
                    coroutineContext.ensureActive() // Responsividade ao cancelamento

                    val sec = startSec + frameIdx
                    val positionUs = sec * 1_000_000L

                    try {
                        val source = retriever.getScaledFrameAtTime(
                            positionUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            extractWidth,
                            extractHeight
                        )

                        if (source != null) {
                            // CenterCrop
                            val srcW = source.width
                            val srcH = source.height
                            val srcAspect = srcW.toFloat() / srcH

                            val cropW: Int
                            val cropH: Int
                            if (srcAspect > dstAspect) {
                                cropH = srcH
                                cropW = (srcH * dstAspect).toInt()
                            } else {
                                cropW = srcW
                                cropH = (srcW / dstAspect).toInt()
                            }

                            val cropX = (srcW - cropW) / 2
                            val cropY = (srcH - cropH) / 2

                            val srcRect = Rect(cropX, cropY, cropX + cropW, cropY + cropH)
                            val dstX = frameIdx * thumbWidth
                            val dstRect = Rect(dstX, 0, dstX + thumbWidth, thumbHeight)

                            canvas.drawBitmap(source, srcRect, dstRect, cropPaint)
                            source.recycle()
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Timber.w(e, "ThumbnailStrip: Failed to extract frame at sec=$sec")
                    }
                }

                Timber.d("ThumbnailStrip: Segment $segmentIndex ($framesInSegment frames, ${stripWidth}x$thumbHeight, RGB_565)")
                strip
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "ThumbnailStrip: Fatal error extracting segment $segmentIndex")
                null
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    Timber.e(e, "ThumbnailStrip: Error releasing retriever")
                }
            }
        }
    }

    /**
     * Calcula o número total de segmentos para um vídeo.
     */
    fun getSegmentCount(durationMs: Long): Int {
        val totalSeconds = ((durationMs + 999) / 1000).toInt()
        return (totalSeconds + SEGMENT_SECONDS - 1) / SEGMENT_SECONDS
    }
}
