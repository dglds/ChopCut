package com.chopcut.service

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chopcut.data.model.TimeRange
import com.chopcut.data.pipeline.CopyPipeline
import com.chopcut.data.repository.VideoRepository
import com.chopcut.util.DispatcherProvider
import com.chopcut.util.TimeTracker
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File

/**
 * Worker para exportação de vídeos usando WorkManager.
 *
 * Use para operações muito longas que precisam persistir mesmo
 * se o app for fechado ou o device reiniciado.
 */
class ExportWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    private val videoRepository = VideoRepository(applicationContext)
    private val dispatcherProvider = DispatcherProvider

    override fun doWork(): Result {
        val videoUriString = inputData.getString(KEY_VIDEO_URI)
        val timeRanges = inputData.getStringArray(KEY_TIME_RANGES)
        val outputName = inputData.getString(KEY_OUTPUT_NAME) ?: "video.mp4"

        if (videoUriString == null || timeRanges == null) {
            Timber.e("Dados de entrada inválidos")
            return Result.failure(workDataOf(KEY_ERROR to "Dados inválidos"))
        }

        val videoUri = Uri.parse(videoUriString)
        val ranges = timeRanges.map { range ->
            val parts = range.split(",")
            TimeRange(parts[0].toLong(), parts[1].toLong())
        }

        val tracker = TimeTracker.start("export_workmanager")
        return try {
            Timber.tag("TIME").d("export_workmanager_start: $outputName")

            // Executar exportação (blocking no Worker)
            val kotlinResult: kotlin.Result<File> = runBlocking(dispatcherProvider.io) {
                val copyPipeline = CopyPipeline(applicationContext, videoRepository, dispatcherProvider)
                copyPipeline.trim(videoUri, ranges).firstOrNull() ?: kotlin.Result.failure(
                    Exception("Nenhum resultado retornado do pipeline")
                )
            }

            kotlinResult.getOrNull()?.let { outputFile ->
                Timber.tag("TIME").d("export_workmanager_save_start: ${outputFile.name}")
                val outputUri = runBlocking(dispatcherProvider.io) {
                    videoRepository.saveToGallery(outputFile, outputName)
                }

                if (outputUri != null) {
                    Timber.tag("TIME").d("export_workmanager_output: $outputUri")
                    tracker.end()
                    Result.success(
                        workDataOf(
                            KEY_OUTPUT_URI to outputUri.toString(),
                            KEY_OUTPUT_NAME to outputName
                        )
                    )
                } else {
                    Timber.e("TIME: export_workmanager_save_failed")
                    Result.failure(workDataOf(KEY_ERROR to "Falha ao salvar na galeria"))
                }
            } ?: run {
                val error = kotlinResult.exceptionOrNull()?.message ?: "Erro desconhecido"
                Timber.e("TIME: export_workmanager_failed: $error")
                Result.failure(workDataOf(KEY_ERROR to error))
            }
        } catch (e: Exception) {
            Timber.e(e, "TIME: export_workmanager_exception")
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Erro desconhecido")))
        }
    }

    companion object {
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_TIME_RANGES = "time_ranges"
        const val KEY_OUTPUT_NAME = "output_name"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_ERROR = "error"

        const val WORK_NAME_PREFIX = "chopcut_export_"
    }
}

/**
 * Gerenciador para agendar exportações via WorkManager
 */
class ExportWorkScheduler(private val context: Context) {

    private val workManager = androidx.work.WorkManager.getInstance(context)

    /**
     * Agenda uma exportação via WorkManager
     */
    fun scheduleExport(
        videoUri: Uri,
        timeRanges: List<TimeRange>,
        outputName: String,
        tag: String = "export"
    ): String {
        val rangesArray = timeRanges.map { "${it.startMs},${it.endMs}" }.toTypedArray()

        val inputData = workDataOf(
            ExportWorker.KEY_VIDEO_URI to videoUri.toString(),
            ExportWorker.KEY_TIME_RANGES to rangesArray,
            ExportWorker.KEY_OUTPUT_NAME to outputName
        )

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(inputData)
            .addTag(tag)
            .build()

        workManager.enqueue(workRequest)

        val workName = "${ExportWorker.WORK_NAME_PREFIX}${System.currentTimeMillis()}"
        Timber.d("Exportação agendada via WorkManager: $workName")

        return workName
    }

    /**
     * Cancela trabalho com tag específica
     */
    fun cancelExport(tag: String) {
        workManager.cancelAllWorkByTag(tag)
        Timber.d("Exportação cancelada: $tag")
    }

    /**
     * Cancela todos os trabalhos de exportação
     */
    fun cancelAll() {
        workManager.cancelAllWorkByTag("export")
        Timber.d("Todas as exportações canceladas")
    }

    /**
     * Obtém status do trabalho
     */
    fun getWorkInfo(tag: String) = workManager.getWorkInfosByTag(tag)

    /**
     * Obtém status do trabalho por ID
     */
    fun getWorkInfoById(workId: java.util.UUID) = workManager.getWorkInfoById(workId)
}
