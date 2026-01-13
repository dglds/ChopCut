package com.chopcut.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chopcut.data.model.EditOperationEntity

@Dao
interface EditOperationDao {
    @Query("SELECT * FROM edit_operations WHERE projectId = :projectId ORDER BY orderIndex ASC")
    suspend fun getOperationsByProject(projectId: String): List<EditOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperations(operations: List<EditOperationEntity>)

    @Query("DELETE FROM edit_operations WHERE projectId = :projectId")
    suspend fun deleteOperationsByProject(projectId: String)
}
