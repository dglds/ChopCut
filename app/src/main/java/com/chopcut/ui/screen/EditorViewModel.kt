package com.chopcut.ui.screen

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.model.TimeRange
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.pipeline.CopyPipeline
import com.chopcut.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * ViewModel for the editor screen
 */
class EditorViewModel(
    private val context: Context,
    private val videoUri: Uri
) : ViewModel() {

    private val contentResolver: ContentResolver = context.contentResolver
    private val videoRepository = VideoRepository(context)
    private val copyPipeline = CopyPipeline(context, videoRepository, DispatcherProvider)

    // Video info state
    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val videoInfo: StateFlow<VideoInfo?> = _videoInfo.asStateFlow()

    // Export result state
    private val _exportResult = MutableStateFlow<Result<Uri>?>(null)
    val exportResult: StateFlow<Result<Uri>?> = _exportResult.asStateFlow()

    // Exporting state
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    init {
        loadVideoMetadata(videoUri)
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
     * Export trimmed video
     */
    fun exportTrimmedVideo(trimRange: com.chopcut.ui.components.TrimRange) {
        viewModelScope.launch(DispatcherProvider.io) {
            _isExporting.value = true
            _exportResult.value = null

            try {
                val timeRange = TimeRange(trimRange.startMs, trimRange.endMs)

                copyPipeline.trim(videoUri, listOf(timeRange))
                    .flowOn(DispatcherProvider.io)
                    .collect { result ->
                        result.getOrNull()?.let { outputFile ->
                            // Save to gallery
                            val outputUri = videoRepository.saveToGallery(
                                file = outputFile,
                                filename = "trimmed_${System.currentTimeMillis()}.mp4"
                            )

                            outputUri?.let {
                                _exportResult.value = Result.success(it)
                                Timber.d("Export successful: $it")
                            } ?: run {
                                _exportResult.value = Result.failure(
                                    Exception("Failed to save video to gallery")
                                )
                            }
                        } ?: run {
                            result.exceptionOrNull()?.let { exception ->
                                _exportResult.value = Result.failure(exception)
                                Timber.e(exception, "Export failed")
                            }
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during export")
                _exportResult.value = Result.failure(e)
            } finally {
                _isExporting.value = false
            }
        }
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
