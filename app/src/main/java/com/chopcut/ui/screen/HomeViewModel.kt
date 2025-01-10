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
