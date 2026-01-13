package com.chopcut.ui.screen

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.model.TimeRange
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.repository.VideoRepository
import com.chopcut.service.ExportServiceManager
import com.chopcut.service.ExportWorkScheduler
import com.chopcut.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for the editor screen
 */
class EditorViewModel(
    private val context: Context,
    private val videoUri: Uri
) : ViewModel() {

    private val contentResolver: ContentResolver = context.contentResolver
    private val videoRepository = VideoRepository(context)

    // Service manager para export em background
    private val exportServiceManager = ExportServiceManager(context)

    // Video info state
    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val videoInfo: StateFlow<VideoInfo?> = _videoInfo.asStateFlow()

    // Export result state
    private val _exportResult = MutableStateFlow<Result<Uri>?>(null)
    val exportResult: StateFlow<Result<Uri>?> = _exportResult.asStateFlow()

    // Exporting state
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    // Export progress
    private val _exportProgress = MutableStateFlow(0)
    val exportProgress: StateFlow<Int> = _exportProgress.asStateFlow()

    init {
        loadVideoMetadata(videoUri)
        setupExportEventListeners()
    }

    /**
     * Configura listeners para eventos de exportação do service
     */
    private fun setupExportEventListeners() {
        viewModelScope.launch {
            exportServiceManager.events.collect { event ->
                when (event) {
                    is ExportServiceManager.ExportEvent.Progress -> {
                        _exportProgress.value = event.progress
                        Timber.d("Export progress: ${event.progress}%")
                    }
                    is ExportServiceManager.ExportEvent.Success -> {
                        _isExporting.value = false
                        _exportProgress.value = 100

                        event.outputUri?.let {
                            _exportResult.value = Result.success(it)
                            Timber.d("Export successful: ${event.outputName} -> $it")
                        } ?: run {
                            _exportResult.value = Result.failure(
                                Exception("Falha ao salvar vídeo na galeria")
                            )
                        }
                    }
                    is ExportServiceManager.ExportEvent.Error -> {
                        _isExporting.value = false
                        _exportResult.value = Result.failure(Exception(event.error))
                        Timber.e("Export failed: ${event.error}")
                    }
                }
            }
        }
    }

    /**
     * Load video metadata
     */
    fun loadVideoMetadata(uri: Uri) {
        viewModelScope.launch(DispatcherProvider.io) {
            try {
                val metadata = videoRepository.getMetadata(uri)
                _videoInfo.value = metadata
                Timber.d("Loaded video metadata: ${metadata?.width}x${metadata?.height}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load video metadata")
            }
        }
    }

    /**
     * Export trimmed video usando ForegroundService
     */
    fun exportTrimmedVideo(trimRange: com.chopcut.ui.components.TrimRange) {
        viewModelScope.launch(DispatcherProvider.io) {
            _isExporting.value = true
            _exportResult.value = null
            _exportProgress.value = 0

            try {
                val timeRange = TimeRange(trimRange.startMs, trimRange.endMs)
                val outputName = "trimmed_${System.currentTimeMillis()}.mp4"

                exportServiceManager.startExport(
                    videoUri = videoUri,
                    timeRanges = listOf(timeRange),
                    outputName = outputName,
                    exportType = "trim"
                )

                Timber.d("Export started via ForegroundService")
            } catch (e: Exception) {
                Timber.e(e, "Error during export")
                _exportResult.value = Result.failure(e)
                _isExporting.value = false
            }
        }
    }

    /**
     * Exporta vídeo usando WorkManager (para operações muito longas)
     */
    fun exportTrimmedVideoLong(trimRange: com.chopcut.ui.components.TrimRange) {
        viewModelScope.launch(DispatcherProvider.io) {
            _isExporting.value = true
            _exportResult.value = null
            _exportProgress.value = 0

            try {
                val timeRange = TimeRange(trimRange.startMs, trimRange.endMs)
                val outputName = "trimmed_${System.currentTimeMillis()}.mp4"

                val scheduler = ExportWorkScheduler(context)
                scheduler.scheduleExport(
                    videoUri = videoUri,
                    timeRanges = listOf(timeRange),
                    outputName = outputName
                )

                Timber.d("Export started via WorkManager")
                // Nota: WorkManager não envia progresso em tempo real
                // O resultado será entregue quando o worker completar
            } catch (e: Exception) {
                Timber.e(e, "Error during export")
                _exportResult.value = Result.failure(e)
                _isExporting.value = false
            }
        }
    }

    /**
     * Cancela a exportação em andamento
     */
    fun cancelExport() {
        exportServiceManager.cancelExport()
        _isExporting.value = false
        _exportProgress.value = 0
        Timber.d("Export cancelled")
    }

    override fun onCleared() {
        super.onCleared()
        // ExportServiceManager é um lifecycle observer, então se limpará automaticamente
    }
}

/**
 * Factory for creating EditorViewModel
 */
class EditorViewModelFactory(
    private val context: Context,
    private val videoUri: Uri
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            return EditorViewModel(context, videoUri) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
