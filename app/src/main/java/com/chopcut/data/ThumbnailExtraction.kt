package com.chopcut

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ExtractionProgressState(
    val isRunning: Boolean = false,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val logs: List<String> = emptyList(),
    val isComplete: Boolean = false,
    val error: String? = null,
    val outputDirPath: String? = null,
    val statsSummary: String? = null
)

data class ExtractionResult(
    val successCount: Int,
    val totalFrames: Int,
    val totalDurationMs: Long,
    val outputDirPath: String?,
    val statsSummary: String
)

class ThumbnailExtraction(private val context: Context) {

    suspend fun extract(
        uri: Uri,
        videoInfo: VideoInfo,
        settings: ThumbnailSettings,
        onProgress: (ExtractionProgressState) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var state = ExtractionProgressState(isRunning = true)
        onProgress(state)

        val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun adbLog(message: String, isError: Boolean = false) {
            if (isError) Log.e("ChopCutExtraction", message)
            else Log.i("ChopCutExtraction", message)
        }

        fun addLog(message: String, isError: Boolean = false) {
            val timestamp = logTimeFormat.format(Date())
            val formatted = "[$timestamp] $message"
            adbLog(message, isError)
            state = state.copy(logs = state.logs + formatted)
            onProgress(state)
        }

        val startTime = System.currentTimeMillis()
        addLog("Iniciando extração de frames...")
        addLog("Vídeo: ${videoInfo.fileName} (${videoInfo.width}x${videoInfo.height}, ${videoInfo.frameRate}fps, ${TimeUtils.formatDuration(videoInfo.durationMs)})")
        addLog("Configurações:")
        val videoAr = videoInfo.aspectRatio
        val (cfgW, cfgH) = settings.computeDimensions(videoAr)
        addLog("   • Tamanho: ${settings.sizePreset.displayName} (${cfgW}x${cfgH}, 16:9)")
        addLog("   • Escala: ${settings.scaleMode.displayName}")
        addLog("   • Formato: ${settings.format.displayName}")
        addLog("   • Qualidade: ${settings.quality}%")

        val thumbsPerSecond = settings.thumbsPerSecond
        val intervalMs = if (thumbsPerSecond <= 0) {
            addLog("   • Taxa: Todos os frames (${videoInfo.frameRate} fps)")
            1000f / videoInfo.frameRate.coerceAtLeast(1)
        } else {
            addLog("   • Taxa: $thumbsPerSecond frame(s) por segundo")
            1000f / thumbsPerSecond
        }

        val totalFrames = if (thumbsPerSecond <= 0) {
            (videoInfo.durationMs * videoInfo.frameRate / 1000f).toInt().coerceAtLeast(1)
        } else {
            (videoInfo.durationMs / intervalMs).toInt().coerceAtLeast(1)
        }

        addLog("Total estimado de frames a extrair: $totalFrames")

        val sanitizedName = videoInfo.fileName
            .substringBeforeLast(".")
            .replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
            .trim('_')
        val outputDirName = sanitizedName

        val baseDir = context.getExternalFilesDir("extracted_frames") ?: context.filesDir
        val outputDir = File(baseDir, outputDirName)

        if (!outputDir.exists()) {
            if (outputDir.mkdirs()) {
                addLog("Pasta criada: ${outputDir.absolutePath}")
            } else {
                addLog("Falha ao criar a pasta de destino.", isError = true)
                state = state.copy(isRunning = false, error = "Falha ao criar diretório")
                onProgress(state)
                return@withContext ExtractionResult(0, totalFrames, System.currentTimeMillis() - startTime, null, "")
            }
        } else {
            addLog("Pasta de destino: ${outputDir.absolutePath}")
        }

        state = state.copy(total = totalFrames, outputDirPath = outputDir.absolutePath)
        onProgress(state)

        val retriever = MediaMetadataRetriever()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.setDataSource(context, uri)
            } else {
                @Suppress("DEPRECATION")
                retriever.setDataSource(uri.toString())
            }

            val extension = when (settings.format) {
                ThumbnailFormat.JPEG -> ThumbnailConfig.FileFormats.EXT_JPG
                ThumbnailFormat.PNG -> ThumbnailConfig.FileFormats.EXT_PNG
                ThumbnailFormat.WEBP -> ThumbnailConfig.FileFormats.EXT_WEBP
            }

            val compressFormat = when (settings.format) {
                ThumbnailFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ThumbnailFormat.PNG -> Bitmap.CompressFormat.PNG
                ThumbnailFormat.WEBP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                }
            }

            var successCount = 0
            val extractionStart = System.currentTimeMillis()

            for (i in 0 until totalFrames) {
                ensureActive()

                val positionMs = (i * intervalMs).toLong()
                val frameFile = File(outputDir, "frame_${String.format("%05d", i + 1)}$extension")
                val frameStart = System.currentTimeMillis()

                try {
                    val quality = settings.extractionQuality
                    val (targetW, targetH) = settings.computeDimensions(videoAr)
                    val (extractBaseW, extractBaseH) = settings.computeExtractDimensions(videoAr)

                    val (extractW, extractH) = if (quality == ThumbnailQuality.HIGH) {
                        val factor = ThumbnailConfig.Quality.HIGH_QUALITY_EXTRACT_FACTOR
                        (extractBaseW * factor).toInt() to (extractBaseH * factor).toInt()
                    } else {
                        extractBaseW to extractBaseH
                    }

                    val hasRotation = videoInfo.rotation == 90 || videoInfo.rotation == 270
                    val (reqW, reqH) = if (hasRotation) extractH to extractW else extractW to extractH
                    val rawFrame = retriever.getScaledFrameAtTime(
                        positionMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        reqW,
                        reqH
                    )

                    val retrieverAlreadyRotated = hasRotation && rawFrame != null &&
                        rawFrame.width == extractW && rawFrame.height == extractH
                    val orientedFrame = if (rawFrame != null && hasRotation && !retrieverAlreadyRotated) {
                        val matrix = android.graphics.Matrix()
                        matrix.postRotate(videoInfo.rotation.toFloat())
                        val rotated = Bitmap.createBitmap(rawFrame, 0, 0, rawFrame.width, rawFrame.height, matrix, true)
                        if (rotated != rawFrame) rawFrame.recycle()
                        rotated
                    } else {
                        rawFrame
                    }

                    val bitmap = when {
                        orientedFrame == null -> null
                        settings.scaleMode == ThumbnailScaleMode.FILL && videoAr >= 1f && targetW != targetH -> {
                            val srcW = orientedFrame.width
                            val srcH = orientedFrame.height
                            val srcAr = srcW.toFloat() / srcH.toFloat()
                            val dstAr = targetW.toFloat() / targetH.toFloat()
                            val (cropW, cropH) = if (srcAr > dstAr) {
                                (srcH * dstAr).toInt() to srcH
                            } else {
                                srcW to (srcW / dstAr).toInt()
                            }
                            val cropX = (srcW - cropW) / 2
                            val cropY = (srcH - cropH) / 2
                            val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
                            val canvas = android.graphics.Canvas(result)
                            val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropW, cropY + cropH)
                            val dstRect = android.graphics.Rect(0, 0, targetW, targetH)
                            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                            canvas.drawBitmap(orientedFrame, srcRect, dstRect, paint)
                            orientedFrame.recycle()
                            result
                        }
                        else -> {
                            if (quality == ThumbnailQuality.HIGH && (orientedFrame.width != targetW || orientedFrame.height != targetH)) {
                                val scaled = Bitmap.createScaledBitmap(orientedFrame, targetW, targetH, true)
                                if (scaled != orientedFrame) orientedFrame.recycle()
                                scaled
                            } else {
                                orientedFrame
                            }
                        }
                    }

                    if (bitmap != null) {
                        java.io.FileOutputStream(frameFile).use { out ->
                            bitmap.compress(compressFormat, settings.quality, out)
                        }
                        bitmap.recycle()
                        successCount++

                        val frameDuration = System.currentTimeMillis() - frameStart
                        val percent = ((i + 1) * 100f / totalFrames).toInt()
                        val logMsg = "Frame ${i + 1}/$totalFrames ($percent%) ${targetW}x${targetH} em ${frameDuration}ms -> ${frameFile.name}"
                        adbLog(logMsg)
                        val timestamp = logTimeFormat.format(Date())
                        state = state.copy(
                            currentIndex = i + 1,
                            logs = state.logs + "[$timestamp] $logMsg"
                        )
                        onProgress(state)
                    } else {
                        val warnMsg = "Falha ao obter frame ${i + 1} na posição ${positionMs}ms"
                        adbLog(warnMsg, isError = true)
                        val timestamp = logTimeFormat.format(Date())
                        state = state.copy(
                            currentIndex = i + 1,
                            logs = state.logs + "[$timestamp] $warnMsg"
                        )
                        onProgress(state)
                    }
                } catch (e: Exception) {
                    val errMsg = "Erro no frame ${i + 1}: ${e.message}"
                    adbLog(errMsg, isError = true)
                    val timestamp = logTimeFormat.format(Date())
                    state = state.copy(
                        currentIndex = i + 1,
                        logs = state.logs + "[$timestamp] $errMsg"
                    )
                    onProgress(state)
                }
            }

            val totalDuration = System.currentTimeMillis() - startTime
            val avgTimePerFrame = if (successCount > 0) (totalDuration - (extractionStart - startTime)) / successCount else 0L
            val fpsThroughput = if (totalDuration > 0) (successCount * 1000f / totalDuration) else 0f

            val summary = buildString {
                appendLine("ESTATÍSTICAS DE EXTRAÇÃO:")
                appendLine("   • Frames com sucesso: $successCount/$totalFrames")
                appendLine("   • Tempo total: ${String.format("%.2f", totalDuration / 1000f)}s")
                appendLine("   • Média por frame: ${avgTimePerFrame}ms (${String.format("%.1f", fpsThroughput)} fps)")
                appendLine("   • Destino: Android/data/com.chopcut/files/extracted_frames/$outputDirName/")
                appendLine()
                append("PARA COPIAR VIA ADB NO COMPUTADOR: ")
                append("adb pull \"/sdcard/Android/data/com.chopcut/files/extracted_frames/$outputDirName\" ./extracted_frames")
            }

            Log.i("ChopCutExtraction", summary)

            state = state.copy(
                isRunning = false,
                isComplete = true,
                statsSummary = summary,
                logs = state.logs + "Extração finalizada com sucesso!"
            )
            onProgress(state)

            ExtractionResult(
                successCount = successCount,
                totalFrames = totalFrames,
                totalDurationMs = totalDuration,
                outputDirPath = outputDir.absolutePath,
                statsSummary = summary
            )
        } catch (e: Exception) {
            if (e !is CancellationException) {
                val timestamp = logTimeFormat.format(Date())
                val errMsg = "Falha crítica: ${e.message}"
                adbLog(errMsg, isError = true)
                state = state.copy(isRunning = false, error = e.message ?: "Erro desconhecido")
                onProgress(state)
            }
            throw e
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}
