package com.chopcut.data.repository

import android.content.Context
import com.chopcut.data.local.ProjectDatabase
import com.chopcut.data.model.ExportPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class PresetRepository(context: Context) {
    private val database = ProjectDatabase.getDatabase(context)
    private val presetDao = database.presetDao()

    /**
     * Gets all available presets (native + custom)
     */
    fun getAllPresets(): Flow<List<ExportPreset>> {
        val nativePresets = flowOf(ExportPreset.DEFAULT_PRESETS)
        val customPresets = presetDao.getCustomPresets()

        return combine(nativePresets, customPresets) { native, custom ->
            native + custom
        }
    }

    suspend fun saveCustomPreset(preset: ExportPreset) {
        presetDao.insertPreset(preset.copy(isCustom = true))
    }

    suspend fun deletePreset(preset: ExportPreset) {
        if (preset.isCustom) {
            presetDao.deletePreset(preset)
        }
    }
}
