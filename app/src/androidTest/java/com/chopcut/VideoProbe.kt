package com.chopcut

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileInputStream

/**
 * Resultado da inspeção objetiva de um vídeo de saída.
 *
 * É o equivalente, dentro do teste, ao que faríamos com `ffprobe` num `adb pull`:
 * lê o arquivo de verdade e expõe codec, dimensões, duração e tamanho — sem
 * confiar em nenhum estado interno do app.
 */
data class ProbeResult(
    val videoMime: String?,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long
) {
    /** Bitrate efetivo (bytes por segundo), métrica justa de compactação independente do corte. */
    val bytesPerSecond: Double get() = if (durationMs > 0) sizeBytes * 1000.0 / durationMs else 0.0

    /** Invariante de encoder do projeto: H.264 exige dimensões pares. */
    val dimensionsAreEven: Boolean get() = width % 2 == 0 && height % 2 == 0
}

/** Inspeção objetiva de vídeos via `MediaExtractor` + `MediaMetadataRetriever`. */
object VideoProbe {

    fun probe(file: File): ProbeResult {
        // O serviço `mediaextractor` é um processo isolado e NÃO lê arquivos no diretório
        // privado do app via path. Abrimos o FD aqui (temos acesso) e o passamos adiante.
        val mime = FileInputStream(file).use { fis -> videoMime { setDataSource(fis.fd) } }
        return FileInputStream(file).use { fis ->
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(fis.fd)
                retriever.toProbeResult(mime, file.length())
            }
        }
    }

    fun probe(context: Context, uri: Uri): ProbeResult {
        val mime = videoMime { setDataSource(context, uri, null) }
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.toProbeResult(mime, sizeOf(context, uri))
        }
    }

    private fun MediaMetadataRetriever.toProbeResult(mime: String?, sizeBytes: Long): ProbeResult {
        val w = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        // Normaliza pela rotação, igual a VideoRepository.getMetadata faz.
        val (width, height) = if (rotation == 90 || rotation == 270) h to w else w to h
        return ProbeResult(mime, width, height, durationMs, sizeBytes)
    }

    /** Lê o mime da primeira track de vídeo. [config] aponta o extractor para a fonte. */
    private fun videoMime(config: MediaExtractor.() -> Unit): String? {
        val extractor = MediaExtractor()
        try {
            extractor.config()
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) return mime
            }
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun sizeOf(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull() ?: 0L

    private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T {
        try {
            return block(this)
        } finally {
            runCatching { release() }
        }
    }
}
