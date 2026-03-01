package com.chopcut.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.local.PreferencesManager
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.thumbnail.ThumbnailStripManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

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
    private val prefsManager = PreferencesManager(application)

    private val _uiState = MutableStateFlow<PreferencesUiState>(PreferencesUiState.Idle)
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    /** Estado do cache de thumbnails (reactivo) */
    private val _isCacheEnabled = MutableStateFlow(prefsManager.thumbnailCacheEnabled)
    val isCacheEnabled: StateFlow<Boolean> = _isCacheEnabled.asStateFlow()

    /** Estado do debugger (reactivo) */
    private val _isDebugEnabled = MutableStateFlow(prefsManager.debugEnabled)
    val isDebugEnabled: StateFlow<Boolean> = _isDebugEnabled.asStateFlow()

    fun setCacheEnabled(enabled: Boolean) {
        prefsManager.thumbnailCacheEnabled = enabled
        _isCacheEnabled.value = enabled
        Timber.i("Thumbnail cache ${if (enabled) "enabled" else "disabled"}")
    }

    fun setDebugEnabled(enabled: Boolean) {
        prefsManager.debugEnabled = enabled
        _isDebugEnabled.value = enabled
        Timber.i("Debug toast ${if (enabled) "enabled" else "disabled"}")
    }

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

    /**
     * Calcula o tamanho atual do cache de thumbnails em MB
     */
    suspend fun getThumbnailCacheSize(): Double = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(getApplication<Application>().cacheDir, "thumbnail_strips")
            if (!cacheDir.exists()) {
                return@withContext 0.0
            }

            var totalBytes = 0L
            cacheDir.walkTopDown()
                .filter { it.isFile }
                .forEach { totalBytes += it.length() }

            val totalMB = totalBytes / (1024.0 * 1024.0)
            Timber.d("Thumbnail cache size: ${String.format("%.2f", totalMB)}MB ($totalBytes bytes)")
            totalMB
        } catch (e: Exception) {
            Timber.e(e, "Error calculating thumbnail cache size")
            0.0
        }
    }

    /**
     * Conta quantos vídeos têm cache
     * Baseia-se no fileInfo único nos nomes dos arquivos de cache
     */
    suspend fun getCachedVideoCount(): Int = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(getApplication<Application>().cacheDir, "thumbnail_strips")
            if (!cacheDir.exists()) {
                return@withContext 0
            }

            val videoIds = mutableSetOf<String>()
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".webp")) {
                    val parts = file.name.removeSuffix(".webp").split("_")
                    if (parts.size >= 4 && parts[0] == "strip" && parts[1].startsWith("v")) {
                        val fileInfo = parts.drop(3).joinToString("_")
                        if (fileInfo.isNotEmpty()) {
                            videoIds.add(fileInfo)
                        }
                    }
                }
            }

            Timber.d("Found ${videoIds.size} videos with cache")
            videoIds.size
        } catch (e: Exception) {
            Timber.e(e, "Error counting cached videos")
            0
        }
    }

    /**
     * Limpa todo o cache de thumbnails do disco
     */
     fun clearThumbnailCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = PreferencesUiState.Loading
            try {
                val context: android.content.Context = getApplication()
                ThumbnailStripManager.clearCache(context)
                _uiState.value = PreferencesUiState.Success(
                    message = "Cache de thumbnails limpo com sucesso."
                )
                Timber.i("Thumbnail cache cleared by user")
            } catch (e: Exception) {
                Timber.e(e, "Error clearing thumbnail cache")
                _uiState.value = PreferencesUiState.Error(
                    message = "Erro ao limpar cache: ${e.message}"
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = PreferencesUiState.Idle
    }
}
