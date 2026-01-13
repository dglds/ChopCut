package com.chopcut.data.repository

import android.content.Context
import com.chopcut.data.local.ProjectDatabase
import com.chopcut.data.model.EditOperation
import com.chopcut.data.model.EditOperationEntity
import com.chopcut.data.model.Project
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Repository to manage Projects and their EditOperations.
 */
class ProjectRepository(context: Context) {
    private val database = ProjectDatabase.getDatabase(context)
    private val projectDao = database.projectDao()
    private val editOperationDao = database.editOperationDao()

    fun getAllProjects(): Flow<List<Project>> = projectDao.getAllProjects()

    suspend fun getProject(id: String): Project? = projectDao.getProjectById(id)

    suspend fun getProjectWithEdits(id: String): Pair<Project, List<EditOperation>>? {
        val project = projectDao.getProjectById(id) ?: return null
        val edits = editOperationDao.getOperationsByProject(id)
            .map { it.toEditOperation() }
        return Pair(project, edits)
    }

    suspend fun saveProject(project: Project, edits: List<EditOperation>) {
        try {
            projectDao.insertProject(project)
            // Replace all operations to ensure sync
            editOperationDao.deleteOperationsByProject(project.id)
            val entities = edits.mapIndexed { index, op ->
                EditOperationEntity.fromEditOperation(project.id, op, index)
            }
            editOperationDao.insertOperations(entities)
            Timber.d("Project saved successfully: ${project.name} (${project.id})")
        } catch (e: Exception) {
            Timber.e(e, "Error saving project: ${project.id}")
            throw e
        }
    }

        suspend fun deleteProject(projectId: String) {

            projectDao.deleteProjectById(projectId)

            // Operations will be deleted by CASCADE

        }

    

        suspend fun updateProject(project: Project) {

            projectDao.updateProject(project)

        }

    }

    