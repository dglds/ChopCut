package com.chopcut.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chopcut.data.model.EditOperationEntity
import com.chopcut.data.model.ExportPreset
import com.chopcut.data.model.Project

@Database(
    entities = [
        Project::class, 
        EditOperationEntity::class,
        ExportPreset::class
    ], 
    version = 3, 
    exportSchema = false
)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun editOperationDao(): EditOperationDao
    abstract fun presetDao(): PresetDao

    companion object {
        const val DATABASE_NAME = "chopcut_projects.db"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add Fade columns to EditOperationEntity
                db.execSQL("ALTER TABLE edit_operations ADD COLUMN fadeInMs INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE edit_operations ADD COLUMN fadeOutMs INTEGER DEFAULT NULL")
            }
        }

        @Volatile
        private var INSTANCE: ProjectDatabase? = null

        fun getDatabase(context: android.content.Context): ProjectDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    ProjectDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration() // Reset DB on schema change if migration fails
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
