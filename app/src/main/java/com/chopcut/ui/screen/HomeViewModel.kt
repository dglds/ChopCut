package com.chopcut.ui.screen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.WaveFormGenerator
import com.chopcut.ui.components.WaveformData
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.model.TimeRange
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.pipeline.TransformerPipeline
import com.chopcut.util.DispatcherProvider
import com.chopcut.util.error.ErrorHandler
import com.chopcut.util.error.RecoveryStrategy
import com.chopcut.util.error.safeExecuteSuspend
import com.chopcut.utils.VideoConstraints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

class HomeViewModel(
    application: Application,
    private val videoRepository: VideoRepository
) : AndroidViewModel(application) {

    private val transformerPipeline = TransformerPipeline(application, videoRepository)
    private val audioDataExtractor = AudioDataExtractor(application)
    
    // PreloadViewModel para extração em background
    private val preloadViewModel = PreloadViewModel(application, videoRepository)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()
    
    // Estado do preload
    val preloadState = preloadViewModel.uiState
    val preloadedData = preloadViewModel.preloadedData

    // Structured error state
    private val _errorState = MutableStateFlow<ErrorHandler.ErrorState?>(null)
    val errorState: StateFlow<ErrorHandler.ErrorState?> = _errorState.asStateFlow()

    fun selectVideo(uri: Uri) {
        _selectedVideoUri.value = uri
        loadVideoMetadata(uri)
    }
    
    fun clearSelectedVideo() {
        _selectedVideoUri.value = null
        resetState()
    }
    
    // Iniciar preload em background após vídeo ser validado
    private fun startPreloadInBackground(uri: Uri) {
        viewModelScope.launch {
            // Aguardar um pouco para UI atualizar primeiro
            kotlinx.coroutines.delay(100)
            preloadViewModel.startPreload(uri, 180f)
            
            // Observar o preloadState para salvar os dados no PreloadDataStore quando prontos
            preloadViewModel.uiState.collectLatest { state ->
                if (state is PreloadUiState.Ready) {
                    // Salvar dados no PreloadDataStore para TrimScreen
                    PreloadDataStore.setData(state.data)
                    Timber.d("Preloaded data saved to PreloadDataStore: ${state.data.preloadedStrips.size} strips")
                }
            }
        }
    }

    private fun loadVideoMetadata(uri: Uri) {
        viewModelScope.launch(DispatcherProvider.io) {
            _uiState.value = HomeUiState.Loading
            _errorState.value = null

            val result = safeExecuteSuspend(context = getApplication()) {
                videoRepository.getMetadata(uri)
            }

            when (result) {
                is com.chopcut.util.error.ErrorResult.Success -> {
                    val metadata = result.data
                    if (metadata != null) {
                        // Validar duração
                        val validation = VideoConstraints.getValidationMessage(
                            metadata.durationMs
                        )
                        if (validation != null) {
                            _errorState.value = ErrorHandler.ErrorState(
                                title = "Vídeo muito longo",
                                message = validation,
                                recovery = com.chopcut.util.error.RecoveryStrategy.SelectAnotherVideo
                            )
                            _uiState.value = HomeUiState.Error(validation)
                        } else {
                            _uiState.value = HomeUiState.VideoLoaded(metadata)
                            Timber.d("Video loaded: ${metadata.fileName}")
                            // INICIAR PRELOAD EM BACKGROUND IMEDIATAMENTE
                            startPreloadInBackground(uri)
                        }
                    } else {
                        _errorState.value = ErrorHandler.ErrorState(
                            title = "Erro de vídeo",
                            message = "Falha ao ler metadados do vídeo",
                            recovery = com.chopcut.util.error.RecoveryStrategy.SelectAnotherVideo
                        )
                    }
                }
                is com.chopcut.util.error.ErrorResult.Error -> {
                    _errorState.value = result.errorState
                    _uiState.value = HomeUiState.Error(result.errorState.message)
                }
            }
        }
    }

    fun testTrim() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _errorState.value = ErrorHandler.ErrorState(
                title = "Nenhum vídeo selecionado",
                message = "Selecione um vídeo primeiro",
                recovery = com.chopcut.util.error.RecoveryStrategy.SelectAnotherVideo
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Trimming video...")
            _errorState.value = null

            try {
                val range = TimeRange(startMs = 0, endMs = 5000)

                transformerPipeline.trim(uri, listOf(range))
                    .collect { progress ->
                        when (progress) {
                            is com.chopcut.data.pipeline.TrimProgress.InProgress -> {
                                // Progresso intermediário - pode atualizar UI se necessário
                            }
                            is com.chopcut.data.pipeline.TrimProgress.Completed -> {
                                val file = progress.file
                                Timber.d("Trim completed: ${file.absolutePath}")
                                _uiState.value = HomeUiState.Success(
                                    "Trim completed!\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB"
                                )
                            }
                            is com.chopcut.data.pipeline.TrimProgress.Failed -> {
                                val error = progress.error
                                Timber.e(error, "Trim failed")
                                val errorState = ErrorHandler.handle(error, getApplication())
                                _errorState.value = errorState
                                _uiState.value = HomeUiState.Error(errorState.message)
                            }
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during trim")
                val errorState = ErrorHandler.handle(e, getApplication())
                _errorState.value = errorState
                _uiState.value = HomeUiState.Error(errorState.message)
            }
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Initial
        // Cancelar preload ao resetar
        preloadViewModel.cancelPreload()
        PreloadDataStore.clearData()
    }
    
    fun cancelPreload() {
        preloadViewModel.cancelPreload()
    }
    
    fun isPreloadReady(): Boolean {
        return preloadState.value is PreloadUiState.Ready
    }
}

sealed class HomeUiState {
    object Initial : HomeUiState()
    object Loading : HomeUiState()
    data class VideoLoaded(val videoInfo: VideoInfo) : HomeUiState()
    data class Processing(val message: String) : HomeUiState()
    data class Success(val message: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
