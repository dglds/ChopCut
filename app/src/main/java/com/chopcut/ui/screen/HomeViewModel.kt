package com.chopcut.ui.screen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.codec.CodecCapabilities
import com.chopcut.data.model.TimeRange
import com.chopcut.data.model.VideoCodec
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.pipeline.CopyPipeline
import com.chopcut.data.pipeline.TranscodeOperations
import com.chopcut.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val videoRepository = VideoRepository(application)
    private val codecCapabilities = CodecCapabilities()
    private val copyPipeline = CopyPipeline(application, videoRepository)
    private val transcodeOperations = TranscodeOperations(application, videoRepository)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    init {
        checkCodecs()
    }

    fun selectVideo(uri: Uri) {
        _selectedVideoUri.value = uri
        loadVideoMetadata(uri)
    }

    private fun loadVideoMetadata(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            try {
                val metadata = videoRepository.getMetadata(uri)
                if (metadata != null) {
                    _uiState.value = HomeUiState.VideoLoaded(metadata)
                    Timber.d("Video loaded: ${metadata.fileName}")
                } else {
                    _uiState.value = HomeUiState.Error("Failed to load video metadata")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading video")
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun testTrim() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _uiState.value = HomeUiState.Error("Please select a video first")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Trimming video...")

            try {
                // Trim first 5 seconds
                val range = TimeRange(startMs = 0, endMs = 5000)

                copyPipeline.trim(uri, listOf(range))
                    .collect { result ->
                        result.onSuccess { file ->
                            Timber.d("Trim completed: ${file.absolutePath}")
                            _uiState.value = HomeUiState.Success(
                                "Trim completed!\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB"
                            )
                        }.onFailure { error ->
                            Timber.e(error, "Trim failed")
                            _uiState.value = HomeUiState.Error(error.message ?: "Trim failed")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during trim")
                _uiState.value = HomeUiState.Error(e.message ?: "Trim failed")
            }
        }
    }

    fun testCompress() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _uiState.value = HomeUiState.Error("Please select a video first")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Compressing video (~2 sec test)...")

            try {
                // Compress to 2 Mbps (limited to 60 frames for testing)
                val targetBitrate = 2_000_000

                transcodeOperations.compress(uri, targetBitrate)
                    .collect { result ->
                        result.onSuccess { file ->
                            Timber.d("Compress completed: ${file.absolutePath}")
                            _uiState.value = HomeUiState.Success(
                                "Compression completed!\nTarget bitrate: ${targetBitrate / 1_000_000} Mbps\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB\n(Limited to 60 frames for testing)"
                            )
                        }.onFailure { error ->
                            Timber.e(error, "Compress failed")
                            _uiState.value = HomeUiState.Error(error.message ?: "Compress failed")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during compress")
                _uiState.value = HomeUiState.Error(e.message ?: "Compress failed")
            }
        }
    }

    fun testResize() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _uiState.value = HomeUiState.Error("Please select a video first")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Resizing video to 50% (~2 sec test)...")

            try {
                // Get original video info to calculate target size
                val metadata = videoRepository.getMetadata(uri)
                if (metadata == null) {
                    _uiState.value = HomeUiState.Error("Failed to read video metadata")
                    return@launch
                }

                // Resize to 50% of original resolution
                val targetWidth = metadata.width / 2
                val targetHeight = metadata.height / 2

                transcodeOperations.resize(uri, targetWidth, targetHeight)
                    .collect { result ->
                        result.onSuccess { file ->
                            Timber.d("Resize completed: ${file.absolutePath}")
                            _uiState.value = HomeUiState.Success(
                                "Resize completed!\n${metadata.width}x${metadata.height} → ${targetWidth}x${targetHeight}\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB\n(Limited to 60 frames for testing)"
                            )
                        }.onFailure { error ->
                            Timber.e(error, "Resize failed")
                            _uiState.value = HomeUiState.Error(error.message ?: "Resize failed")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during resize")
                _uiState.value = HomeUiState.Error(e.message ?: "Resize failed")
            }
        }
    }

    fun testCrop() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _uiState.value = HomeUiState.Error("Please select a video first")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Cropping video center 50% (~2 sec test)...")

            try {
                // Crop center 50% of the video
                val cropRect = android.graphics.RectF(
                    0.25f,  // left 25%
                    0.25f,  // top 25%
                    0.75f,  // right 75%
                    0.75f   // bottom 75%
                )

                transcodeOperations.crop(uri, cropRect)
                    .collect { result ->
                        result.onSuccess { file ->
                            Timber.d("Crop completed: ${file.absolutePath}")
                            _uiState.value = HomeUiState.Success(
                                "Crop completed!\nCenter 50% cropped\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB\n(Limited to 60 frames for testing)"
                            )
                        }.onFailure { error ->
                            Timber.e(error, "Crop failed")
                            _uiState.value = HomeUiState.Error(error.message ?: "Crop failed")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during crop")
                _uiState.value = HomeUiState.Error(e.message ?: "Crop failed")
            }
        }
    }

    fun checkCodecs() {
        viewModelScope.launch {
            try {
                codecCapabilities.logAvailableCodecs()

                val bestCodec = codecCapabilities.selectBestEncoder()
                val codecs = VideoCodec.entries

                _uiState.value = HomeUiState.CodecsLoaded(
                    codecs = codecs.map { codec ->
                        CodecInfo(
                            name = codec.displayName,
                            hasEncoder = codecCapabilities.hasEncoder(codec),
                            hasDecoder = codecCapabilities.hasDecoder(codec),
                            isBest = bestCodec == codec
                        )
                    }
                )

                Timber.d("Best codec: ${bestCodec?.displayName}")
            } catch (e: Exception) {
                Timber.e(e, "Error checking codecs")
                _uiState.value = HomeUiState.Error("Failed to check codecs: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Initial
    }
}

sealed class HomeUiState {
    object Initial : HomeUiState()
    object Loading : HomeUiState()
    data class VideoLoaded(val videoInfo: VideoInfo) : HomeUiState()
    data class CodecsLoaded(val codecs: List<CodecInfo>) : HomeUiState()
    data class Processing(val message: String) : HomeUiState()
    data class Success(val message: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

data class CodecInfo(
    val name: String,
    val hasEncoder: Boolean,
    val hasDecoder: Boolean,
    val isBest: Boolean
)
