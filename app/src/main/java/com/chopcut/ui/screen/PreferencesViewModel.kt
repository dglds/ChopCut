package com.chopcut.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class PreferencesUiState {
    object Idle : PreferencesUiState()
    object Loading : PreferencesUiState()
    data class Success(val message: String) : PreferencesUiState()
    data class Error(val message: String) : PreferencesUiState()
}

class PreferencesViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val videoRepository = VideoRepository(application)

    private val _uiState = MutableStateFlow<PreferencesUiState>(PreferencesUiState.Idle)
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    fun deleteSavedVideos() {
        viewModelScope.launch {
            _uiState.value = PreferencesUiState.Loading
            try {
                val deletedCount = videoRepository.deleteSavedVideos()
                if (deletedCount > 0) {
                    _uiState.value = PreferencesUiState.Success(
                        message = "$deletedCount vídeo(s) removido(s) com sucesso."
                    )
                } else {
                    _uiState.value = PreferencesUiState.Success(
                        message = "Nenhum vídeo encontrado para remover."
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting saved videos")
                _uiState.value = PreferencesUiState.Error(
                    message = "Erro ao remover vídeos: ${e.message}"
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = PreferencesUiState.Idle
    }
}
