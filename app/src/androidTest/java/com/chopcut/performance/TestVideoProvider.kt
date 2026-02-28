package com.chopcut.performance

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

/**
 * Provider de vídeos de teste para testes de performance
 */
class TestVideoProvider(private val context: Context) {

    companion object {
        // Usa diretório público do Movies (acessível sem permissões especiais a partir do Android 10)
        const val TEST_VIDEOS_DIR = "/sdcard/Movies/ChopCut/tests"
        // Fallback directory in case Movies is blocked by Scoped Storage in test environment
        const val FALLBACK_VIDEOS_DIR = "/sdcard/Download/ChopCutTests"
    }

    /**
     * Obtém todos os vídeos da pasta de testes
     * Usa MediaStore para melhor compatibilidade com Android 10+
     */
    fun getAllTestVideos(): List<File> {
        val videos = mutableListOf<File>()
        
        // Testa os dois diretórios
        listOf(TEST_VIDEOS_DIR, FALLBACK_VIDEOS_DIR).forEach { dirPath ->
            val testDir = File(dirPath)

            // 1. Tenta acesso direto via File API (funciona em debug/test com permissões)
            if (testDir.exists()) {
                testDir.listFiles { file ->
                    file.isFile && (file.extension.lowercase() in listOf("mp4", "3gp", "mov", "mkv"))
                }?.forEach { videos.add(it) }
                
                if (videos.isNotEmpty()) {
                    Timber.d("Found ${videos.size} videos via direct file access in $dirPath")
                }
            }

            // 2. Fallback: MediaStore (mais robusto para Scoped Storage)
            if (videos.isEmpty()) {
                Timber.d("Trying MediaStore to find videos in $dirPath")
                
                val projection = arrayOf(
                    android.provider.MediaStore.Video.Media.DATA,
                    android.provider.MediaStore.Video.Media.DISPLAY_NAME
                )

                val selection = "${android.provider.MediaStore.Video.Media.DATA} LIKE ?"
                val selectionArgs = arrayOf("$dirPath/%")

                try {
                    context.contentResolver.query(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${android.provider.MediaStore.Video.Media.DATE_ADDED} DESC"
                    )?.use { cursor ->
                        val dataColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)

                        while (cursor.moveToNext()) {
                            val path = cursor.getString(dataColumn)
                            val file = File(path)
                            if (file.exists()) {
                                videos.add(file)
                                Timber.d("Found video via MediaStore: ${file.name}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error querying MediaStore for $dirPath")
                }
            }
        }

        val result = videos.distinctBy { it.absolutePath }
        Timber.d("Total videos found: ${result.size}")
        return result
    }

    /**
     * Cria um vídeo de teste sintético com configurações específicas
     */
    fun createTestVideo(
        durationSeconds: Int = 10,
        width: Int = 1920,
        height: Int = 1080,
        frameRate: Int = 30
    ): Uri {
        // Salva na pasta de testes em vez do cache
        val testDir = File(TEST_VIDEOS_DIR)
        if (!testDir.exists()) {
            testDir.mkdirs()
        }
        val outputFile = File(testDir, "synthetic_${width}x${height}_${durationSeconds}s.mp4")

        // Se já existe, retorna
        if (outputFile.exists()) {
            Timber.d("Using existing test video: ${outputFile.absolutePath}")
            return Uri.fromFile(outputFile)
        }

        Timber.d("Creating test video: ${outputFile.absolutePath}")

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val surface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bufferInfo = MediaCodec.BufferInfo()
        var trackIndex = -1

        try {
            val totalFrames = durationSeconds * frameRate
            var frameIndex = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                // Processar output
                if (!outputDone) {
                    val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)

                    when (outputBufferIndex) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // Sem output disponível
                        }
                        else -> {
                            if (outputBufferIndex >= 0) {
                                val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)

                                if (outputBuffer != null && bufferInfo.size > 0) {
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                    if (trackIndex >= 0) {
                                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                    }
                                }

                                encoder.releaseOutputBuffer(outputBufferIndex, false)

                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                }
                            }
                        }
                    }
                }

                // Gerar frames de input
                if (!inputDone && frameIndex < totalFrames) {
                    // Renderizar frame colorido na surface
                    renderFrameToSurface(surface, frameIndex, width, height)
                    frameIndex++
                } else if (!inputDone) {
                    encoder.signalEndOfInputStream()
                    inputDone = true
                }
            }
        } finally {
            encoder.stop()
            encoder.release()
            surface.release()
            muxer.stop()
            muxer.release()
        }

        Timber.d("Test video created: ${outputFile.absolutePath} (${outputFile.length() / 1024}KB)")
        return Uri.fromFile(outputFile)
    }

    private fun renderFrameToSurface(surface: android.view.Surface, frameIndex: Int, width: Int, height: Int) {
        // Simplificado: apenas sinaliza frame sem renderização real
        // Em implementação real, usaria Canvas ou OpenGL para desenhar
    }

    /**
     * Obtém vídeo de teste (da pasta ou cria sintético)
     */
    fun getTestVideoUri(durationSeconds: Int = 30): Uri {
        val videos = getAllTestVideos()

        return if (videos.isNotEmpty()) {
            Timber.d("Found ${videos.size} test videos in $TEST_VIDEOS_DIR")
            Uri.fromFile(videos.first())
        } else {
            Timber.d("No test videos found, creating synthetic video")
            createTestVideo(durationSeconds = durationSeconds)
        }
    }

    /**
     * Obtém informações sobre um vídeo
     */
    fun getVideoInfo(file: File): VideoTestInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            VideoTestInfo(
                file = file,
                durationMs = duration,
                width = width,
                height = height,
                rotation = rotation,
                sizeBytes = file.length()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting video info for ${file.name}")
            VideoTestInfo(file = file)
        } finally {
            retriever.release()
        }
    }

    /**
     * Obtém vídeo de exemplo do sistema (se disponível)
     */
    fun getSampleVideoUri(): Uri? {
        // Tenta obter vídeo de amostra do MediaStore
        val projection = arrayOf(
            android.provider.MediaStore.Video.Media._ID,
            android.provider.MediaStore.Video.Media.DURATION
        )

        context.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${android.provider.MediaStore.Video.Media.DURATION} > ?",
            arrayOf("5000"), // Pelo menos 5 segundos
            "${android.provider.MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID))
                return android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(id.toString())
                    .build()
            }
        }

        return null
    }

    /**
     * Limpa vídeos sintéticos criados
     */
    fun cleanupSyntheticVideos() {
        val testDir = File(TEST_VIDEOS_DIR)
        testDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("synthetic_") && file.extension == "mp4") {
                file.delete()
                Timber.d("Deleted synthetic video: ${file.name}")
            }
        }
    }
}

/**
 * Informações sobre um vídeo de teste
 */
data class VideoTestInfo(
    val file: File,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val sizeBytes: Long = 0
) {
    val durationSeconds: Double
        get() = durationMs / 1000.0

    val resolution: String
        get() = "${width}x${height}"

    val sizeMB: Double
        get() = sizeBytes / (1024.0 * 1024.0)

    override fun toString(): String {
        return "${file.name} - ${resolution} ${String.format("%.1f", durationSeconds)}s ${String.format("%.2f", sizeMB)}MB"
    }
}
