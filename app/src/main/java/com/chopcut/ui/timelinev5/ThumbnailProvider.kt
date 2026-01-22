package com.chopcut.ui.timelinev5

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.ui.timelinev5.model.Thumbnail
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
    private val context: Context
) {
    private val extractor = ThumbnailExtractor(context)

    /**
     * Extrai thumbnails em intervalos regulares.
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
            // Calcula os tempos de extração
            val interval = durationMs / count.coerceAtLeast(1)
            val times = (0 until count).map { it * interval }

            val bitmaps = extractor.extractAtPositions(
                uri = uri,
                positionsMs = times,
                width = width,
                height = height
            )

            val thumbnails = times.zip(bitmaps).map { (time, bitmap) ->
                Thumbnail(timeMs = time, bitmap = bitmap)
            }

            emit(thumbnails)
        } catch (e: Exception) {
            Timber.e(e, "Erro ao extrair thumbnails para TimelineV5")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
}