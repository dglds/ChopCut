package com.chopcut.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sealed class representing all possible edit operations in the editor.
 */
sealed class EditOperation {
    data class Trim(val startTime: Long, val endTime: Long) : EditOperation()
    data class Crop(val left: Int, val top: Int, val right: Int, val bottom: Int) : EditOperation()
    data class Rotation(val degrees: Int) : EditOperation()
    data class Resize(val width: Int, val height: Int) : EditOperation()
    data class Filter(val filterType: FilterType, val intensity: Float) : EditOperation()
    data class Speed(val speed: Float) : EditOperation()
    data class Volume(val volume: Float) : EditOperation()
}

/**
 * Room entity to persist edit operations.
 */
@Entity(
    tableName = "edit_operations",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class EditOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: String,
    val type: String,
    val orderIndex: Int,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Parameters (null if not used)
    val startTime: Long? = null,
    val endTime: Long? = null,
    val rectLeft: Int? = null,
    val rectTop: Int? = null,
    val rectRight: Int? = null,
    val rectBottom: Int? = null,
    val rotationDegrees: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val filterType: String? = null,
    val filterIntensity: Float? = null,
    val speed: Float? = null,
    val volume: Float? = null
) {
    fun toEditOperation(): EditOperation {
        return when (type) {
            "TRIM" -> EditOperation.Trim(startTime!!, endTime!!)
            "CROP" -> EditOperation.Crop(rectLeft!!, rectTop!!, rectRight!!, rectBottom!!)
            "ROTATION" -> EditOperation.Rotation(rotationDegrees!!)
            "RESIZE" -> EditOperation.Resize(width!!, height!!)
            "FILTER" -> EditOperation.Filter(FilterType.valueOf(filterType!!), filterIntensity!!)
            "SPEED" -> EditOperation.Speed(speed!!)
            "VOLUME" -> EditOperation.Volume(volume!!)
            else -> throw IllegalArgumentException("Unknown edit operation type: $type")
        }
    }

    companion object {
        fun fromEditOperation(projectId: String, operation: EditOperation, orderIndex: Int): EditOperationEntity {
            return when (operation) {
                is EditOperation.Trim -> EditOperationEntity(
                    projectId = projectId, type = "TRIM", orderIndex = orderIndex,
                    startTime = operation.startTime, endTime = operation.endTime
                )
                is EditOperation.Crop -> EditOperationEntity(
                    projectId = projectId, type = "CROP", orderIndex = orderIndex,
                    rectLeft = operation.left, rectTop = operation.top,
                    rectRight = operation.right, rectBottom = operation.bottom
                )
                is EditOperation.Rotation -> EditOperationEntity(
                    projectId = projectId, type = "ROTATION", orderIndex = orderIndex,
                    rotationDegrees = operation.degrees
                )
                is EditOperation.Resize -> EditOperationEntity(
                    projectId = projectId, type = "RESIZE", orderIndex = orderIndex,
                    width = operation.width, height = operation.height
                )
                is EditOperation.Filter -> EditOperationEntity(
                    projectId = projectId, type = "FILTER", orderIndex = orderIndex,
                    filterType = operation.filterType.name, filterIntensity = operation.intensity
                )
                is EditOperation.Speed -> EditOperationEntity(
                    projectId = projectId, type = "SPEED", orderIndex = orderIndex,
                    speed = operation.speed
                )
                is EditOperation.Volume -> EditOperationEntity(
                    projectId = projectId, type = "VOLUME", orderIndex = orderIndex,
                    volume = operation.volume
                )
            }
        }
    }
}
