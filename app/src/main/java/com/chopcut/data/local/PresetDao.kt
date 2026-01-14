package com.chopcut.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chopcut.data.model.ExportPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM export_presets WHERE isCustom = 1")
    fun getCustomPresets(): Flow<List<ExportPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: ExportPreset)

    @Delete
    suspend fun deletePreset(preset: ExportPreset)
}
