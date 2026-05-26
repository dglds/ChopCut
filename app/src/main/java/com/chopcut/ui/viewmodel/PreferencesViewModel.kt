package com.chopcut.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.local.PreferencesManager
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.thumbnail.ThumbnailStripManager
import com.chopcut.data.thumbnail.ThumbnailCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    
    /** Estado do modo do tema (0=System, 1=Light, 2=Dark) */
    private val _themeMode = MutableStateFlow(prefsManager.themeMode)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setCacheEnabled(enabled: Boolean) {
        prefsManager.thumbnailCacheEnabled = enabled
        _isCacheEnabled.value = enabled
    }

    fun setDebugEnabled(enabled: Boolean) {
        prefsManager.debugEnabled = enabled
        _isDebugEnabled.value = enabled
    }

    fun setThemeMode(mode: Int) {
        prefsManager.themeMode = mode
        _themeMode.value = mode
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
            totalMB
        } catch (e: Exception) {
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

            videoIds.size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Limpa todo o cache de thumbnails (memória e disco)
     */
    fun clearThumbnailCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = PreferencesUiState.Loading
            try {
                val context: android.content.Context = getApplication()

                // Limpar cache de memória (LRU)
                ThumbnailCacheManager.clearMemoryCache()

                // Limpar cache de disco
                ThumbnailStripManager.clearCache(context)

                _uiState.value = PreferencesUiState.Success(
                    message = "Cache de thumbnails limpo com sucesso (memória e disco)."
                )
            } catch (e: Exception) {
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
