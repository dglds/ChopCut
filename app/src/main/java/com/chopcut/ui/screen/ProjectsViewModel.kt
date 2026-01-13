package com.chopcut.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.model.Project
import com.chopcut.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class ProjectsUiState {
    object Loading : ProjectsUiState()
    data class Success(val projects: List<Project>) : ProjectsUiState()
    data class Error(val message: String) : ProjectsUiState()
}

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProjectRepository(application)

    private val _uiState = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Loading)
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            repository.getAllProjects()
                .catch { e ->
                    Timber.e(e, "Error loading projects")
                    _uiState.value = ProjectsUiState.Error(e.message ?: "Unknown error")
                }
                .collect { projects ->
                    _uiState.value = ProjectsUiState.Success(projects)
                }
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            try {
                repository.deleteProject(project.id)
                Timber.d("Project deleted: ${project.id}")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting project")
            }
        }
    }
    
    fun renameProject(project: Project, newName: String) {
        viewModelScope.launch {
            try {
                val updatedProject = project.copy(name = newName, modifiedAt = System.currentTimeMillis())
                repository.updateProject(updatedProject)
                Timber.d("Project renamed: ${project.id} to $newName")
            } catch (e: Exception) {
                Timber.e(e, "Error renaming project")
            }
        }
    }
}
