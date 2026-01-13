package com.chopcut.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chopcut.data.model.EditOperationEntity
import com.chopcut.data.model.Project

@Database(entities = [Project::class, EditOperationEntity::class], version = 1, exportSchema = false)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun editOperationDao(): EditOperationDao

    companion object {
        const val DATABASE_NAME = "chopcut_projects.db"

        @Volatile
        private var INSTANCE: ProjectDatabase? = null

        fun getDatabase(context: android.content.Context): ProjectDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    ProjectDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
