package com.chopcut.ui.timeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.ui.timeline.model.Thumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

/**
 * Provider responsável por extrair thumbnails para a TimelineV5.
 * Utiliza o ThumbnailExtractor existente do projeto.
 */
class ThumbnailProvider(
    private val context: Context,
    private val cache: com.chopcut.data.thumbnail.ThumbnailCache = com.chopcut.data.thumbnail.ThumbnailCache(100)
) {
    private val extractor = ThumbnailExtractor(context)

    /**
     * Extrai thumbnails em intervalos regulares, utilizando cache se disponível.
     *
     * @param uri URI do vídeo.
     * @param durationMs Duração total do vídeo.
     * @param count Quantidade aproximada de thumbnails desejada.
     * @param width Largura desejada de cada thumbnail.
     * @param height Altura desejada de cada thumbnail.
     */
    fun extractThumbnails(
        uri: Uri,
        durationMs: Long,
        count: Int,
        width: Int,
        height: Int
    ): Flow<List<Thumbnail>> = flow {
        try {
            val uriString = uri.toString()
            val interval = durationMs / count.coerceAtLeast(1)
            val times = (0 until count).map { it * interval }

            val thumbnails = times.map { timeMs ->
                val cachedBitmap = cache.get(uriString, timeMs)
                if (cachedBitmap != null) {
                    Thumbnail(timeMs, cachedBitmap)
                } else {
                    val bitmap = extractor.extractAt(uri, timeMs, width, height)
                    if (bitmap != null) {
                        cache.put(uriString, timeMs, bitmap)
                    }
                    Thumbnail(timeMs, bitmap)
                }
            }

            emit(thumbnails)
        } catch (e: Exception) {
            Timber.e(e, "Erro ao extrair thumbnails para TimelineV5")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
}