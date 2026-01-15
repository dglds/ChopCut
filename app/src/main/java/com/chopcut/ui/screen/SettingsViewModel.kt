package com.chopcut.ui.screen

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.model.VideoCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * ViewModel for managing app settings
 */
class SettingsViewModel(
    private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // Settings state
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ExportSettings> = _settings.asStateFlow()

    // Storage cleanup state
    private val _cleanupResult = MutableStateFlow<CleanupResult?>(null)
    val cleanupResult: StateFlow<CleanupResult?> = _cleanupResult.asStateFlow()

    /**
     * Update video codec
     */
    fun updateCodec(codec: VideoCodec) {
        _settings.value = _settings.value.copy(codec = codec)
        prefs.edit().putString(KEY_CODEC, codec.name).apply()
    }

    /**
     * Update video bitrate
     */
    fun updateBitrate(kbps: Int) {
        _settings.value = _settings.value.copy(bitrateKbps = kbps)
        prefs.edit().putInt(KEY_BITRATE, kbps).apply()
    }

    /**
     * Update audio bitrate
     */
    fun updateAudioBitrate(kbps: Int) {
        _settings.value = _settings.value.copy(audioBitrateKbps = kbps)
        prefs.edit().putInt(KEY_AUDIO_BITRATE, kbps).apply()
    }

    /**
     * Update frame rate
     */
    fun updateFrameRate(fps: Int) {
        _settings.value = _settings.value.copy(frameRate = fps)
        prefs.edit().putInt(KEY_FRAME_RATE, fps).apply()
    }

    /**
     * Update resolution preset
     */
    fun updateResolutionPreset(preset: ResolutionPreset) {
        _settings.value = _settings.value.copy(resolutionPreset = preset)
        prefs.edit().putString(KEY_RESOLUTION, preset.name).apply()
    }

    /**
     * Update key frame interval
     */
    fun updateKeyFrameInterval(seconds: Int) {
        _settings.value = _settings.value.copy(keyFrameInterval = seconds)
        prefs.edit().putInt(KEY_KEYFRAME_INTERVAL, seconds).apply()
    }

    /**
     * Update fast path preference
     */
    fun updateUseFastPath(use: Boolean) {
        _settings.value = _settings.value.copy(useFastPath = use)
        prefs.edit().putBoolean(KEY_FAST_PATH, use).apply()
    }

    /**
     * Reset all settings to defaults
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            val defaults = ExportSettings()
            _settings.value = defaults

            prefs.edit().apply {
                putString(KEY_CODEC, defaults.codec.name)
                putInt(KEY_BITRATE, defaults.bitrateKbps)
                putInt(KEY_AUDIO_BITRATE, defaults.audioBitrateKbps)
                putInt(KEY_FRAME_RATE, defaults.frameRate)
                putString(KEY_RESOLUTION, defaults.resolutionPreset.name)
                putInt(KEY_KEYFRAME_INTERVAL, defaults.keyFrameInterval)
                putBoolean(KEY_FAST_PATH, defaults.useFastPath)
            }.apply()
        }
    }

    /**
     * Clear all exported videos in the Movies/ChopCut directory
     * Uses MediaStore on Android 10+ for proper scoped storage handling
     */
    fun clearChopCutDirectory() {
        viewModelScope.launch {
            try {
                // Check storage permission on older Android versions
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    val permissionCheck = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                        _cleanupResult.value = CleanupResult(
                            0, 0L,
                            "Permissão de armazenamento necessária"
                        )
                        return@launch
                    }
                }

                var filesDeleted = 0
                var totalSize = 0L

                // 1. Try to clear root /ChopCut folder (legacy path)
                try {
                    val rootDir = File(Environment.getExternalStorageDirectory(), "ChopCut")
                    if (rootDir.exists()) {
                        val (deleted, size) = deleteRecursively(rootDir)
                        filesDeleted += deleted
                        totalSize += size
                    }
                } catch (e: Exception) {
                    Timber.w("Failed to clear root ChopCut: ${e.message}")
                }

                // 2. Clear Movies/ChopCut via MediaStore (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val collection = android.provider.MediaStore.Video.Media.getContentUri(
                            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                        )

                        // Query all videos in Movies/ChopCut
                        val projection = arrayOf(
                            android.provider.MediaStore.Video.Media._ID,
                            android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                            android.provider.MediaStore.Video.Media.SIZE,
                            android.provider.MediaStore.Video.Media.DATA
                        )

                        val selection = "${android.provider.MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
                        val selectionArgs = arrayOf("%${Environment.DIRECTORY_MOVIES}/ChopCut%")

                        context.contentResolver.query(
                            collection,
                            projection,
                            selection,
                            selectionArgs,
                            null
                        )?.use { cursor ->
                            while (cursor.moveToNext()) {
                                val idColumn = cursor.getColumnIndexOrThrow(
                                    android.provider.MediaStore.Video.Media._ID
                                )
                                val sizeColumn = cursor.getColumnIndexOrThrow(
                                    android.provider.MediaStore.Video.Media.SIZE
                                )
                                val dataColumn = cursor.getColumnIndexOrThrow(
                                    android.provider.MediaStore.Video.Media.DATA
                                )

                                val id = cursor.getLong(idColumn)
                                val size = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L
                                val dataPath = if (dataColumn >= 0) cursor.getString(dataColumn) else null

                                // Delete via MediaStore
                                val uri = android.content.ContentUris.withAppendedId(collection, id)
                                val rowsDeleted = context.contentResolver.delete(uri, null, null)

                                if (rowsDeleted > 0) {
                                    filesDeleted++
                                    totalSize += size
                                }

                                // Also try to delete the actual file if path exists
                                dataPath?.let {
                                    try {
                                        File(it).delete()
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to clear Movies/ChopCut via MediaStore")
                    }
                } else {
                    // Android 9 and below: direct file access
                    try {
                        val moviesDir = File(
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_MOVIES
                            ),
                            "ChopCut"
                        )
                        if (moviesDir.exists()) {
                            val (deleted, size) = deleteRecursively(moviesDir)
                            filesDeleted += deleted
                            totalSize += size
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to clear Movies/ChopCut directory")
                    }
                }

                if (filesDeleted == 0 && totalSize == 0L) {
                    _cleanupResult.value = CleanupResult(
                        0, 0L,
                        "Nenhum arquivo encontrado em Movies/ChopCut"
                    )
                } else {
                    val sizeText = formatFileSize(totalSize)
                    _cleanupResult.value = CleanupResult(
                        filesDeleted,
                        totalSize,
                        "$filesDeleted arquivos excluídos ($sizeText liberados)"
                    )
                    Timber.d("ChopCut directory cleared: $filesDeleted files, $sizeText freed")
                }

            } catch (e: Exception) {
                Timber.e(e, "Error clearing ChopCut directory")
                _cleanupResult.value = CleanupResult(0, 0L, "Erro: ${e.message}")
            }
        }
    }

    /**
     * Reset cleanup result after showing it
     */
    fun clearCleanupResult() {
        _cleanupResult.value = null
    }

    private fun deleteRecursively(dir: File): Pair<Int, Long> {
        var filesDeleted = 0
        var totalSize = 0L

        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val deleted = deleteRecursively(file)
                filesDeleted += deleted.first
                totalSize += deleted.second
            } else {
                totalSize += file.length()
                if (file.delete()) {
                    filesDeleted++
                }
            }
        }

        // Delete the directory itself after clearing contents
        dir.delete()
        return Pair(filesDeleted, totalSize)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Load settings from SharedPreferences
     */
    private fun loadSettings(): ExportSettings {
        return ExportSettings(
            codec = try {
                VideoCodec.valueOf(
                    prefs.getString(KEY_CODEC, VideoCodec.H264.name) ?: VideoCodec.H264.name
                )
            } catch (e: IllegalArgumentException) {
                VideoCodec.H264
            },
            bitrateKbps = prefs.getInt(KEY_BITRATE, 5000),
            audioBitrateKbps = prefs.getInt(KEY_AUDIO_BITRATE, 128),
            frameRate = prefs.getInt(KEY_FRAME_RATE, 30),
            resolutionPreset = try {
                ResolutionPreset.valueOf(
                    prefs.getString(KEY_RESOLUTION, ResolutionPreset.ORIGINAL.name)
                        ?: ResolutionPreset.ORIGINAL.name
                )
            } catch (e: IllegalArgumentException) {
                ResolutionPreset.ORIGINAL
            },
            keyFrameInterval = prefs.getInt(KEY_KEYFRAME_INTERVAL, 2),
            useFastPath = prefs.getBoolean(KEY_FAST_PATH, true)
        )
    }

    companion object {
        private const val PREFS_NAME = "chopcut_settings"
        private const val KEY_CODEC = "codec"
        private const val KEY_BITRATE = "bitrate"
        private const val KEY_AUDIO_BITRATE = "audio_bitrate"
        private const val KEY_FRAME_RATE = "frame_rate"
        private const val KEY_RESOLUTION = "resolution_preset"
        private const val KEY_KEYFRAME_INTERVAL = "keyframe_interval"
        private const val KEY_FAST_PATH = "use_fast_path"
    }
}

/**
 * Result of directory cleanup operation
 */
data class CleanupResult(
    val filesDeleted: Int,
    val bytesFreed: Long,
    val message: String
)
