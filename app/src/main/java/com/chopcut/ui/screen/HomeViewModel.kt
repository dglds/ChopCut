package com.chopcut.ui.screen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.model.TimeRange
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.pipeline.TransformerPipeline
import com.chopcut.data.repository.VideoRepository
import com.chopcut.util.DispatcherProvider
import com.chopcut.util.error.ErrorHandler
import com.chopcut.util.error.RecoveryStrategy
import com.chopcut.util.error.safeExecuteSuspend
import com.chopcut.utils.VideoConstraints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel para HomeScreen.
 * 
 * Responsabilidades:
 * - Gerenciar seleção de vídeo
 * - Carregar metadados do vídeo
 * - Gerenciar estado da UI (Loading, VideoLoaded, Error)
 * - Testar funcionalidade de trim
 * 
 * NOTA: O pré-carregamento de thumbnails e áudio é gerenciado
 * pela PreloadViewModel (Activity-scoped), não por esta ViewModel.
 */
class HomeViewModel(
    application: Application,
    private val videoRepository: VideoRepository
) : AndroidViewModel(application) {

    private val transformerPipeline = TransformerPipeline(application, videoRepository)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()
    
    private val _errorState = MutableStateFlow<ErrorHandler.ErrorState?>(null)
    val errorState: StateFlow<ErrorHandler.ErrorState?> = _errorState.asStateFlow()

    fun selectVideo(uri: Uri) {
        Timber.tag("HomeViewModel").d("=== selectVideo CALLED ===")
        Timber.tag("HomeViewModel").d("uri: $uri")
        
        Timber.d("=== HomeViewModel.selectVideo CALLED ===")
        Timber.d("uri: $uri")
        _selectedVideoUri.value = uri
        Timber.tag("HomeViewModel").d("_selectedVideoUri atualizado para: ${_selectedVideoUri.value}")
        Timber.d("_selectedVideoUri atualizado para: ${_selectedVideoUri.value}")
        loadVideoMetadata(uri)
    }
    
    fun clearSelectedVideo() {
        _selectedVideoUri.value = null
        resetState()
    }
    
    private fun loadVideoMetadata(uri: Uri) {
        Timber.d("=== HomeViewModel.loadVideoMetadata CALLED ===")
        Timber.d("uri: $uri")
        
        viewModelScope.launch(DispatcherProvider.io) {
            Timber.d("=== loadVideoMetadata coroutine STARTED ===")
            _uiState.value = HomeUiState.Loading
            _errorState.value = null

            Timber.d("Chamando videoRepository.getMetadata...")
            val result = safeExecuteSuspend(context = getApplication()) {
                videoRepository.getMetadata(uri)
            }
            Timber.d("videoRepository.getMetadata retornou: $result")

            when (result) {
                is com.chopcut.util.error.ErrorResult.Success -> {
                    val metadata = result.data
                    Timber.d("Metadata obtido: $metadata")
                    if (metadata != null) {
                        // Validar duração
                        val validation = VideoConstraints.getValidationMessage(
                            metadata.durationMs
                        )
                        if (validation != null) {
                            Timber.w("Vídeo rejeitado: $validation")
                            _errorState.value = ErrorHandler.ErrorState(
                                title = "Vídeo muito longo",
                                message = validation,
                                recovery = com.chopcut.util.error.RecoveryStrategy.SelectAnotherVideo
                            )
                            _uiState.value = HomeUiState.Error(validation)
                        } else {
                            Timber.d("Vídeo válido, mudando estado para VideoLoaded")
                            _uiState.value = HomeUiState.VideoLoaded(metadata)
                            Timber.d("Video loaded: ${metadata.fileName}")
                            Timber.d("Duration: ${metadata.durationMs}ms")
                            Timber.d("NOTA: HomeScreen vai chamar PreloadViewModel.startPreload()")
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
                    Timber.e("Erro ao carregar metadados: ${result.errorState}")
                    _errorState.value = result.errorState
                    _uiState.value = HomeUiState.Error(result.errorState.message)
                }
            }
            Timber.d("=== loadVideoMetadata coroutine FINISHED ===")
        }
    }

    fun testTrim() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _errorState.value = ErrorHandler.ErrorState(
                title = "Nenhum vídeo selecionado",
                message = "Selecione um vídeo primeiro",
                recovery = RecoveryStrategy.SelectAnotherVideo
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
