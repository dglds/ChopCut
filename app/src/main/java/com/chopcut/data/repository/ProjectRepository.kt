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

    suspend fun cleanupUnusedPermissions(contentResolver: android.content.ContentResolver) {
        try {
            val projects = projectDao.getAllProjectsList()
            // Normalize URIs due to potential encoding differences
            val usedUris = projects.mapNotNull { project ->
                project.sourceVideoUri?.let { uriString ->
                    try {
                        setOf(uriString, android.net.Uri.decode(uriString))
                    } catch (e: Exception) {
                        setOf(uriString)
                    }
                }
            }.flatten().toSet()
            
            val persistedPermissions = contentResolver.persistedUriPermissions
            var releasedCount = 0
            
            Timber.i("DEBUG: Active Projects URIs: $usedUris")
            Timber.i("DEBUG: Persisted Permissions: ${persistedPermissions.map { it.uri.toString() }}")
            
            for (permission in persistedPermissions) {
                val uriString = permission.uri.toString()
                val decodedUriString = try {
                    android.net.Uri.decode(uriString)
                } catch (e: Exception) {
                    uriString
                }

                // Check if either raw or decoded URI is in use
                val isUsed = uriString in usedUris || decodedUriString in usedUris
                
                // Add grace period: Don't delete permissions granted in the last 60 seconds
                // This prevents race conditions where a user picks a file (New Project) 
                // and the cleanup runs concurrently before the project is saved to DB.
                val isRecent = (System.currentTimeMillis() - permission.persistedTime) < 60_000
                
                if (!isUsed) {
                    if (isRecent) {
                        Timber.i("Skipping cleanup for recent permission (<1m): $uriString")
                        continue
                    }

                    try {
                        contentResolver.releasePersistableUriPermission(
                            permission.uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        releasedCount++
                        Timber.d("Released unused permission for: $uriString")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to release permission for: $uriString")
                    }
                } else {
                    Timber.v("Keeping permission (Match): $uriString")
                }
            }
            Timber.i("Permission cleanup finished. Released $releasedCount permissions.")
        } catch (e: Exception) {
            Timber.e(e, "Error during permission cleanup")
        }
    }

    }