package com.chopcut.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.chopcut.data.model.TimeRange
import com.chopcut.data.pipeline.CopyPipeline
import com.chopcut.data.repository.VideoRepository
import com.chopcut.util.DispatcherProvider
import com.chopcut.util.TimeTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Serviço em foreground para exportação de vídeos em background.
 *
 * Mantém o processo vivo mesmo com a tela apagada usando WakeLock parcial
 * e mostra notificação de progresso da exportação.
 */
class ExportForegroundService : Service() {

    companion object {
        const val ACTION_START_EXPORT = "com.chopcut.ACTION_START_EXPORT"
        const val ACTION_CANCEL_EXPORT = "com.chopcut.ACTION_CANCEL_EXPORT"

        // Extras para iniciar exportação
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_TIME_RANGES = "time_ranges"
        const val EXTRA_EXPORT_TYPE = "export_type"

        // Tipos de exportação
        const val EXPORT_TYPE_TRIM = "trim"
        const val EXPORT_TYPE_COMPRESS = "compress"
        const val EXPORT_TYPE_RESIZE = "resize"
        const val EXPORT_TYPE_CROP = "crop"

        // Ações de broadcast para resultados
        const val ACTION_EXPORT_PROGRESS = "com.chopcut.EXPORT_PROGRESS"
        const val ACTION_EXPORT_SUCCESS = "com.chopcut.EXPORT_SUCCESS"
        const val ACTION_EXPORT_ERROR = "com.chopcut.EXPORT_ERROR"

        // Extras para broadcast de resultado
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_OUTPUT_URI = "output_uri"
        const val EXTRA_OUTPUT_NAME = "output_name"
        const val EXTRA_ERROR = "error"
    }

    private val serviceScope = CoroutineScope(DispatcherProvider.io + Job())
    private val binder = LocalBinder()

    private lateinit var notificationManager: ExportNotificationManager
    private var exportJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false

    // Repositório e pipeline
    private lateinit var videoRepository: VideoRepository
    private lateinit var copyPipeline: CopyPipeline

    inner class LocalBinder : Binder() {
        fun getService(): ExportForegroundService = this@ExportForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag("ExportForegroundService").d("Service criado")

        notificationManager = ExportNotificationManager(this)
        videoRepository = VideoRepository(this)
        copyPipeline = CopyPipeline(this, videoRepository, DispatcherProvider)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EXPORT -> {
                val videoUri = intent.getParcelableExtra<Uri>(EXTRA_VIDEO_URI)
                val timeRanges = intent.getParcelableArrayListExtra<TimeRange>(EXTRA_TIME_RANGES)
                val outputName = intent.getStringExtra("output_name") ?: "video.mp4"
                val exportType = intent.getStringExtra(EXTRA_EXPORT_TYPE) ?: EXPORT_TYPE_TRIM

                if (videoUri != null && timeRanges != null) {
                    startExport(videoUri, timeRanges, outputName, exportType)
                } else {
                    Timber.tag("ExportForegroundService").e("URI ou timeRanges inválidos")
                    stopSelf()
                }
            }
            ACTION_CANCEL_EXPORT -> {
                cancelExport()
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Inicia a exportação de vídeo
     */
    private fun startExport(
        videoUri: Uri,
        timeRanges: List<TimeRange>,
        outputName: String,
        exportType: String
    ) {
        if (isRunning) {
            Timber.tag("ExportForegroundService").w("Exportação já em andamento")
            return
        }

        isRunning = true
        acquireWakeLock()

        // Iniciar como foreground
        val notification = notificationManager.createExportNotification(outputName)
        startForeground(notificationManager.getNotificationId(), notification)

        exportJob = serviceScope.launch {
            val tracker = TimeTracker.start("export_service")
            try {
                Timber.tag("TIME").d("export_service_start: $outputName")

                // Coletar o resultado do pipeline
                var finalResult: kotlinx.coroutines.flow.Flow<kotlin.Result<java.io.File>> =
                    copyPipeline.trim(videoUri, timeRanges)

                // Coletar o primeiro (e único) resultado
                var outputUri: Uri? = null

                finalResult
                    .flowOn(DispatcherProvider.io)
                    .onEach { result ->
                        // Progresso intermediário
                        broadcastProgress(50)
                    }
                    .catch { e ->
                        Timber.tag("ExportForegroundService").e(e, "Erro durante exportação")
                        Timber.tag("TIME").d("export_service_error: ${e.message}")
                        tracker.end()
                        broadcastError(e.message ?: "Erro desconhecido")
                        notificationManager.showError(e.message ?: "Erro ao exportar")
                    }
                    .onCompletion {
                        cleanup()
                    }
                    .collect { result ->
                        result.getOrNull()?.let { outputFile ->
                            Timber.tag("TIME").d("export_save_to_gallery_start: ${outputFile.name}")
                            outputUri = videoRepository.saveToGallery(outputFile, outputName)
                            Timber.tag("TIME").d("export_save_to_gallery_success: ${outputFile.name} -> $outputUri")
                            tracker.end()
                            broadcastSuccess(outputUri, outputName)
                            notificationManager.showSuccess(outputName)
                        } ?: run {
                            val error = result.exceptionOrNull()?.message ?: "Erro desconhecido"
                            Timber.tag("ExportForegroundService").e("Export falhou: $error")
                            tracker.end()
                            broadcastError(error)
                            notificationManager.showError(error)
                        }
                    }
            } catch (e: Exception) {
                Timber.tag("ExportForegroundService").e(e, "Erro ao iniciar exportação")
                Timber.tag("TIME").d("export_service_exception: ${e.message}")
                tracker.end()
                broadcastError(e.message ?: "Erro ao iniciar exportação")
                notificationManager.showError(e.message ?: "Erro ao exportar")
                cleanup()
            }
        }
    }

    /**
     * Cancela a exportação em andamento
     */
    private fun cancelExport() {
        Timber.tag("ExportForegroundService").d("Cancelando exportação")
        exportJob?.cancel()
        cleanup()
    }

    /**
     * Adquire WakeLock para evitar que o device durma durante exportação
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ChopCut:ExportWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutos max
        }
        Timber.tag("ExportForegroundService").d("WakeLock adquirido")
    }

    /**
     * Libera recursos e para o serviço
     */
    private fun cleanup() {
        isRunning = false
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.tag("ExportForegroundService").d("WakeLock liberado")
            }
        }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Envia broadcast de progresso
     */
    private fun broadcastProgress(progress: Int) {
        val intent = Intent(ACTION_EXPORT_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS, progress)
        }
        sendBroadcast(intent)
        Timber.tag("ExportForegroundService").d("Broadcast progress: $progress%")
    }

    /**
     * Envia broadcast de sucesso
     */
    private fun broadcastSuccess(outputUri: Uri?, outputName: String) {
        val intent = Intent(ACTION_EXPORT_SUCCESS).apply {
            putExtra(EXTRA_OUTPUT_URI, outputUri)
            putExtra("output_name", outputName)  // Usar string literal para evitar ambiguidade
        }
        sendBroadcast(intent)
        Timber.tag("ExportForegroundService").d("Broadcast success: $outputName")
    }

    /**
     * Envia broadcast de erro
     */
    private fun broadcastError(error: String) {
        val intent = Intent(ACTION_EXPORT_ERROR).apply {
            putExtra(EXTRA_ERROR, error)
        }
        sendBroadcast(intent)
        Timber.tag("ExportForegroundService").d("Broadcast error: $error")
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        serviceScope.cancel()
        Timber.tag("ExportForegroundService").d("Service destruído")
    }

    /**
     * Verifica se há exportação em andamento
     */
    fun isExporting(): Boolean = isRunning
}
