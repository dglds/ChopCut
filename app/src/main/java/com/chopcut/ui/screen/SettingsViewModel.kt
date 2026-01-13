package com.chopcut.ui.screen

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.model.VideoCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing app settings
 */
class SettingsViewModel(
    context: Context
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // Settings state
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ExportSettings> = _settings.asStateFlow()

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
