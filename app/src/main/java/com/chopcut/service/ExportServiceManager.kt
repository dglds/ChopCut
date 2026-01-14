package com.chopcut.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.chopcut.data.model.TimeRange
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Gerenciador para controlar o ExportForegroundService
 *
 * Uso:
 * ```kotlin
 * val exportManager = ExportServiceManager(context)
 * exportManager.startExport(uri, ranges, "video.mp4")
 * exportManager.events.collect { event -> ... }
 * ```
 */
class ExportServiceManager(private val context: Context) :
    DefaultLifecycleObserver {

    sealed class ExportEvent {
        data class Progress(val progress: Int) : ExportEvent()
        data class Success(val outputUri: Uri?, val outputName: String) : ExportEvent()
        data class Error(val error: String) : ExportEvent()
    }

    private val _events = Channel<ExportEvent>(Channel.BUFFERED)
    val events: Flow<ExportEvent> = _events.receiveAsFlow()

    private val exportReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.tag("ExportServiceManager").d("Broadcast recebido: ${intent?.action}")
            when (intent?.action) {
                ExportForegroundService.ACTION_EXPORT_PROGRESS -> {
                    val progress = intent.getIntExtra(
                        ExportForegroundService.EXTRA_PROGRESS,
                        0
                    )
                    Timber.tag("ExportServiceManager").d("Progress: $progress%")
                    _events.trySend(ExportEvent.Progress(progress))
                }
                ExportForegroundService.ACTION_EXPORT_SUCCESS -> {
                    val outputUri = intent.getParcelableExtra<Uri>(
                        ExportForegroundService.EXTRA_OUTPUT_URI
                    )
                    val outputName = intent.getStringExtra(
                        "output_name"
                    ) ?: "video.mp4"
                    Timber.tag("ExportServiceManager").d("Success: $outputName -> $outputUri")
                    _events.trySend(ExportEvent.Success(outputUri, outputName))
                }
                ExportForegroundService.ACTION_EXPORT_ERROR -> {
                    val error = intent.getStringExtra(
                        ExportForegroundService.EXTRA_ERROR
                    ) ?: "Erro desconhecido"
                    Timber.tag("ExportServiceManager").d("Error: $error")
                    _events.trySend(ExportEvent.Error(error))
                }
            }
        }
    }

    init {
        // Registrar lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Registrar broadcast receiver com flags para Android 14+
        val filter = IntentFilter().apply {
            addAction(ExportForegroundService.ACTION_EXPORT_PROGRESS)
            addAction(ExportForegroundService.ACTION_EXPORT_SUCCESS)
            addAction(ExportForegroundService.ACTION_EXPORT_ERROR)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(exportReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(exportReceiver, filter)
        }

        Timber.d("ExportServiceManager inicializado")
    }

    /**
     * Inicia uma exportação usando o ForegroundService
     */
    fun startExport(
        videoUri: Uri,
        timeRanges: List<TimeRange>,
        outputName: String,
        exportType: String = ExportForegroundService.EXPORT_TYPE_TRIM,
        rotation: Int = 0,
        width: Int = 0,
        height: Int = 0
    ) {
        val intent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_START_EXPORT
            putExtra(ExportForegroundService.EXTRA_VIDEO_URI, videoUri)
            putParcelableArrayListExtra(
                ExportForegroundService.EXTRA_TIME_RANGES,
                ArrayList(timeRanges)
            )
            putExtra("output_name", outputName)
            putExtra(ExportForegroundService.EXTRA_EXPORT_TYPE, exportType)
            putExtra(ExportForegroundService.EXTRA_ROTATION, rotation)
            putExtra(ExportForegroundService.EXTRA_WIDTH, width)
            putExtra(ExportForegroundService.EXTRA_HEIGHT, height)
        }
        context.startForegroundService(intent)

        Timber.d("Exportação iniciada: $outputName (rot=$rotation)")
    }

    /**
     * Cancela a exportação em andamento
     */
    fun cancelExport() {
        val intent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_CANCEL_EXPORT
        }
        context.startService(intent)

        Timber.d("Cancelamento solicitado")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        context.unregisterReceiver(exportReceiver)
        _events.close()
        Timber.d("ExportServiceManager destruído")
    }
}

/**
 * Extension para simplificar uso em ViewModels
 */
suspend fun ExportServiceManager.collectEvents(
    scope: kotlinx.coroutines.CoroutineScope,
    onProgress: (Int) -> Unit,
    onSuccess: (Uri?, String) -> Unit,
    onError: (String) -> Unit
) {
    scope.launch {
        events.collect { event ->
            when (event) {
                is ExportServiceManager.ExportEvent.Progress -> onProgress(event.progress)
                is ExportServiceManager.ExportEvent.Success -> onSuccess(
                    event.outputUri,
                    event.outputName
                )
                is ExportServiceManager.ExportEvent.Error -> onError(event.error)
            }
        }
    }
}
