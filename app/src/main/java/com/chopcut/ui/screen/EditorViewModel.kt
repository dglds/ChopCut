package com.chopcut.ui.screen

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.AudioRawData
import com.chopcut.data.audio.WaveFormGenerator
import com.chopcut.data.model.EditOperation
import com.chopcut.data.model.ExportPreset
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.data.undo.UndoManager
import com.chopcut.ui.components.WaveformData
import com.chopcut.data.model.TimeRange
import com.chopcut.data.model.Transform
import com.chopcut.data.model.ExportConfig
import com.chopcut.data.pipeline.TranscodePipeline
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.repository.ProjectRepository
import com.chopcut.data.repository.PresetRepository
import com.chopcut.data.repository.VideoRepository
import com.chopcut.service.ExportForegroundService
import com.chopcut.service.ExportServiceManager
import com.chopcut.service.ExportWorkScheduler
import com.chopcut.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

enum class EditorTool {
    NONE,
    TRIM,
    CROP,
    ROTATE,
    FILTER,
    SPEED,
    VOLUME
}

/**
 * ViewModel for the editor screen
 */
class EditorViewModel(
    private val context: Context,
    private val videoUri: Uri,
    private val projectId: String? = null
) : ViewModel() {

    private val contentResolver: ContentResolver = context.contentResolver
    private val videoRepository = VideoRepository(context)
    private val projectRepository = ProjectRepository(context)
    private val presetRepository = PresetRepository(context)
    private val audioDataExtractor = AudioDataExtractor(context)
    private val transcodePipeline = TranscodePipeline(context, videoRepository)
    private val thumbnailExtractor = ThumbnailExtractor(context)
    
    // Presets
    val availablePresets = presetRepository.getAllPresets()
    
    // Undo Manager
    private val undoManager = UndoManager()

    // Service manager para export em background
    private val exportServiceManager = ExportServiceManager(context)

    // Project state
    private val _project = MutableStateFlow<com.chopcut.data.model.Project?>(null)
    val project: StateFlow<com.chopcut.data.model.Project?> = _project.asStateFlow()

    // Delegate edits to UndoManager
    val edits: StateFlow<List<EditOperation>> = undoManager.currentEdits
    val canUndo: StateFlow<Boolean> = undoManager.canUndo
    val canRedo: StateFlow<Boolean> = undoManager.canRedo

    // Save state
    enum class SaveStatus { SAVED, SAVING, UNSAVED }
    private val _saveStatus = MutableStateFlow(SaveStatus.SAVED)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()
    
    // UI Messages (Toasts, Snackbars)
    private val _uiMessage = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val messageFlow: kotlinx.coroutines.flow.Flow<String> = _uiMessage

    private var autoSaveJob: Job? = null
    private val AUTO_SAVE_DELAY = 3000L // 3 seconds debounce

    // Video info state
    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val videoInfo: StateFlow<VideoInfo?> = _videoInfo.asStateFlow()

    // Current video URI (may be external or internal)
    private val _currentVideoUri = MutableStateFlow<Uri?>(null)
    val currentVideoUri: StateFlow<Uri?> = _currentVideoUri.asStateFlow()

    // Waveform state
    private val _waveformData = MutableStateFlow<WaveformData>(WaveformData.empty())
    val waveformData: StateFlow<WaveformData> = _waveformData.asStateFlow()

    // Raw PCM data (extracted once, reused for waveform generation)
    private val _audioRawData = MutableStateFlow<AudioRawData?>(null)
    
    // Configurable waveform bar count (default for editor)
    private val _waveformBars = MutableStateFlow(100)

    // Export result state
    private val _exportResult = MutableStateFlow<Result<Uri>?>(null)
    val exportResult: StateFlow<Result<Uri>?> = _exportResult.asStateFlow()

    // Exporting state
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    // Export progress
    private val _exportProgress = MutableStateFlow(0)
    val exportProgress: StateFlow<Int> = _exportProgress.asStateFlow()

    // Active Tool State
    private val _activeTool = MutableStateFlow(EditorTool.NONE)
    val activeTool: StateFlow<EditorTool> = _activeTool.asStateFlow()

    init {
        // Initialize with provided URI (will be updated to internal URI after import)
        _currentVideoUri.value = videoUri

        if (projectId != null) {
            loadProject(projectId)
        } else {
            // New project: initialize with provided URI, but start import process
            loadVideoMetadata(videoUri)
            initializeNewProject()
        }
        setupExportEventListeners()
    }

    private fun initializeNewProject() {
        viewModelScope.launch(DispatcherProvider.io) {
            // 1. Create project object immediately with original URI
            val metadata = videoRepository.getMetadata(videoUri) ?: _videoInfo.value
            val newProjectId = java.util.UUID.randomUUID().toString()
            
            if (metadata != null) {
                val initialProject = com.chopcut.data.model.Project(
                    id = newProjectId,
                    name = metadata.fileName,
                    sourceVideoUri = videoUri.toString(), // Temporary: original URI
                    duration = metadata.durationMs
                )
                _project.value = initialProject
                _videoInfo.value = metadata
                
                // Initial save to register in DB
                saveProject()
                Timber.d("Project initialized: $newProjectId")

                // 2. Start background import (copy to internal storage)
                val internalFile = videoRepository.copyToInternalStorage(videoUri, newProjectId)
                
                // 2.1 Generate thumbnail
                val thumbFile = java.io.File(context.filesDir, "projects/$newProjectId/thumbnail.jpg")
                val thumbSuccess = thumbnailExtractor.extractToFile(videoUri, thumbFile)
                val thumbPath = if (thumbSuccess) thumbFile.absolutePath else null
                
                if (internalFile != null) {
                    val internalUri = Uri.fromFile(internalFile)

                    // Update video URI to internal URI
                    _currentVideoUri.value = internalUri

                    // 3. Update project with internal URI and Thumbnail
                    val updatedProject = initialProject.copy(
                        sourceVideoUri = internalUri.toString(),
                        thumbnail = thumbPath
                    )
                    _project.value = updatedProject

                    // Save update silently
                    projectRepository.saveProject(updatedProject, edits.value)
                    Timber.d("Project video imported successfully: $internalUri, Thumb: $thumbPath")
                } else {
                    Timber.e("Failed to import video for project")
                }
            }
        }
    }

    private fun loadProject(id: String) {
        viewModelScope.launch(DispatcherProvider.io) {
            projectRepository.getProjectWithEdits(id)?.let { pair ->
                val project = pair.first
                val loadedEdits = pair.second
                _project.value = project
                undoManager.loadInitialState(loadedEdits)

                // Update video URI to internal URI from project
                val projectUri = Uri.parse(project.sourceVideoUri)
                _currentVideoUri.value = projectUri
                loadVideoMetadata(projectUri)
            }
        }
    }

    fun addOperation(operation: EditOperation) {
        undoManager.addOperation(operation)
        triggerAutoSave()
    }

    fun undo() {
        undoManager.undo()
        triggerAutoSave()
    }

    fun redo() {
        undoManager.redo()
        triggerAutoSave()
    }

    private fun triggerAutoSave() {
        _saveStatus.value = SaveStatus.UNSAVED
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY)
            saveProject()
        }
    }

    /**
     * Saves the current project state
     */
    fun saveProject(name: String? = null, manual: Boolean = false) {
        viewModelScope.launch(DispatcherProvider.io) {
            _saveStatus.value = SaveStatus.SAVING
            val currentProject = _project.value
            val metadata = _videoInfo.value ?: return@launch

            val projectToSave = if (currentProject != null) {
                currentProject.copy(
                    name = name ?: currentProject.name,
                    modifiedAt = System.currentTimeMillis()
                )
            } else {
                // Should not happen if initializeNewProject runs, but fallback
                com.chopcut.data.model.Project(
                    name = name ?: metadata.fileName,
                    sourceVideoUri = videoUri.toString(),
                    duration = metadata.durationMs
                )
            }

            try {
                // Regenerate thumbnail with current rotation
                val totalRotation = edits.value.filterIsInstance<EditOperation.Rotation>()
                    .sumOf { it.degrees }
                
                val thumbFile = java.io.File(context.filesDir, "projects/${projectToSave.id}/thumbnail.jpg")
                // Ensure directory exists
                thumbFile.parentFile?.mkdirs()
                
                // Use sourceVideoUri from project (it's internal URI now)
                val sourceUri = Uri.parse(projectToSave.sourceVideoUri)
                
                val thumbSuccess = thumbnailExtractor.extractToFile(
                    sourceUri, 
                    thumbFile, 
                    rotation = totalRotation
                )
                
                val finalProject = if (thumbSuccess) {
                    projectToSave.copy(thumbnail = thumbFile.absolutePath)
                } else {
                    projectToSave
                }

                projectRepository.saveProject(finalProject, edits.value)
                _project.value = finalProject
                _saveStatus.value = SaveStatus.SAVED
                Timber.d("Project saved: ${finalProject.name}")
                if (manual) {
                    _uiMessage.emit("Projeto salvo com sucesso!")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error saving project")
                _saveStatus.value = SaveStatus.UNSAVED
                if (manual) {
                    _uiMessage.emit("Erro ao salvar projeto: ${e.message}")
                }
            }
        }
    }

    /**
     * Configura listeners para eventos de exportação do service
     */
    private fun setupExportEventListeners() {
        viewModelScope.launch {
            exportServiceManager.events.collect { event ->
                when (event) {
                    is ExportServiceManager.ExportEvent.Progress -> {
                        _exportProgress.value = event.progress
                        Timber.d("Export progress: ${event.progress}%")
                    }
                    is ExportServiceManager.ExportEvent.Success -> {
                        _isExporting.value = false
                        _exportProgress.value = 100

                        event.outputUri?.let {
                            _exportResult.value = Result.success(it)
                            Timber.d("Export successful: ${event.outputName} -> $it")
                        } ?: run {
                            _exportResult.value = Result.failure(
                                Exception("Falha ao salvar vídeo na galeria")
                            )
                        }
                    }
                    is ExportServiceManager.ExportEvent.Error -> {
                        _isExporting.value = false
                        _exportResult.value = Result.failure(Exception(event.error))
                        Timber.e("Export failed: ${event.error}")
                        _uiMessage.emit("Erro na exportação: ${event.error}")
                    }
                }
            }
        }
    }

    /**
     * Load video metadata and start audio extraction
     */
    fun loadVideoMetadata(uri: Uri) {
        viewModelScope.launch(DispatcherProvider.io) {
            try {
                val metadata = videoRepository.getMetadata(uri)
                _videoInfo.value = metadata
                Timber.d("Loaded video metadata: ${metadata?.width}x${metadata?.height}")
                
                if (metadata?.hasAudio == true) {
                    extractAudioData(uri)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load video metadata")
            }
        }
    }

    /**
     * Extracts raw audio data and generates waveform
     */
    private fun extractAudioData(uri: Uri) {
        viewModelScope.launch(DispatcherProvider.io) {
            try {
                Timber.d("Starting audio extraction for waveform...")
                val rawData = audioDataExtractor.extractRawPcmData(uri)
                _audioRawData.value = rawData
                
                // Generate waveform with default bars
                val amplitudes = WaveFormGenerator.generateWaveform(
                    pcmSamples = rawData.pcmSamples,
                    barCount = _waveformBars.value
                )
                
                _waveformData.value = WaveformData(
                    amplitudes = amplitudes,
                    sampleRate = rawData.sampleRate,
                    durationMs = rawData.durationMs
                )
                Timber.d("Waveform generated with ${amplitudes.size} bars")
            } catch (e: Exception) {
                Timber.e(e, "Failed to extract audio data")
            }
        }
    }

    /**
     * Executes a test operation (Lightweight for UI testing)
     * Adds to Undo stack but simulates processing to be fast.
     */
    fun testOperation(type: String) {
        viewModelScope.launch(DispatcherProvider.io) {
            val metadata = _videoInfo.value ?: return@launch
            
            // Simulating UI operation only
            when (type) {
                "rotate" -> {
                    addOperation(EditOperation.Rotation(90))
                }
                "resize" -> {
                    addOperation(EditOperation.Resize(metadata.width / 2, metadata.height / 2))
                }
                "crop" -> {
                    addOperation(EditOperation.Crop(0, 0, 100, 100))
                }
            }
            
            // Notify UI (Fast feedback)
            Timber.d("Test operation $type applied (Logic only)")
        }
    }

    /**
     * Applies a Trim operation to the project state (adds to Undo stack)
     */
    fun applyTrim(trimRange: com.chopcut.ui.components.TrimRange) {
        val op = EditOperation.Trim(trimRange.startMs, trimRange.endMs)
        addOperation(op)
        Timber.d("Applied Trim operation: ${trimRange.startMs}-${trimRange.endMs}ms")
    }

    /**
     * Export processed video using ForegroundService
     * Applies Trim + Transforms (Rotate) + Preset (Resize)
     */
    fun exportVideo(trimRange: com.chopcut.ui.components.TrimRange?, preset: ExportPreset? = null) {
        viewModelScope.launch(DispatcherProvider.io) {
            _isExporting.value = true
            _exportResult.value = null
            _exportProgress.value = 0

            try {
                // 1. Calculate Trim from history (last trim wins) or parameter
                val metadata = _videoInfo.value ?: throw IllegalStateException("Metadata not loaded")
                
                val lastTrim = edits.value.filterIsInstance<EditOperation.Trim>().lastOrNull()
                
                val finalTrimRange = when {
                    lastTrim != null -> TimeRange(lastTrim.startTime, lastTrim.endTime)
                    trimRange != null -> TimeRange(trimRange.startMs, trimRange.endMs)
                    else -> TimeRange(0, metadata.durationMs)
                }

                // 2. Calculate Transforms from history
                val totalRotation = edits.value.filterIsInstance<EditOperation.Rotation>()
                    .sumOf { it.degrees }

                val volumeOp = edits.value.filterIsInstance<EditOperation.Volume>().lastOrNull()
                val volume = volumeOp?.volume ?: 1.0f

                val fadeOp = edits.value.filterIsInstance<EditOperation.Fade>().lastOrNull()
                val fadeInMs = fadeOp?.fadeInMs ?: 0L
                val fadeOutMs = fadeOp?.fadeOutMs ?: 0L

                val filterOp = edits.value.filterIsInstance<EditOperation.Filter>().lastOrNull()
                val filter = filterOp?.filterType ?: com.chopcut.data.model.FilterType.NONE
                val filterIntensity = filterOp?.intensity ?: 1.0f

                // 3. Determine config (Preset vs Original)
                val config = preset?.toExportConfig(
                    originalWidth = metadata.width,
                    originalHeight = metadata.height,
                    originalBitrate = metadata.bitrate.toInt()
                ) ?: ExportConfig.fromVideoInfo(metadata)
                
                // 4. Determine Pipeline Type
                // If we have rotation OR filters OR a preset OR VOLUME change OR FADE, we MUST transcode.
                val hasTransform = totalRotation % 360 != 0
                val hasFilters = filter != com.chopcut.data.model.FilterType.NONE
                val hasFormatChange = preset != null
                val hasVolumeChange = volume != 1.0f
                val hasFade = fadeInMs > 0 || fadeOutMs > 0

                val exportType = if (hasTransform || hasFilters || hasFormatChange || hasVolumeChange || hasFade) {
                    ExportForegroundService.EXPORT_TYPE_TRANSCODE
                } else {
                    ExportForegroundService.EXPORT_TYPE_TRIM
                }

                val outputName = "ChopCut_${System.currentTimeMillis()}.mp4"

                // Use project URI if available (stable internal file), otherwise fallback to initial URI
                val targetUri = _project.value?.sourceVideoUri?.let { Uri.parse(it) } ?: videoUri

                exportServiceManager.startExport(
                    videoUri = targetUri,
                    timeRanges = listOf(finalTrimRange),
                    outputName = outputName,
                    exportType = exportType,
                    rotation = totalRotation,
                    width = config.width,
                    height = config.height,
                    volume = volume,
                    filter = filter,
                    filterIntensity = filterIntensity,
                    fadeInMs = fadeInMs,
                    fadeOutMs = fadeOutMs
                )

                Timber.d("Export started: $exportType, rot=$totalRotation, vol=$volume, fade_in=$fadeInMs, fade_out=$fadeOutMs, filter=$filter, int=$filterIntensity")
            } catch (e: Exception) {
                Timber.e(e, "Error during export preparation")
                _exportResult.value = Result.failure(e)
                _isExporting.value = false
                _uiMessage.emit("Falha ao iniciar exportação: ${e.message}")
            }
        }
    }

    /**
     * Cancela a exportação em andamento
     */
    fun cancelExport() {
        exportServiceManager.cancelExport()
        _isExporting.value = false
        _exportProgress.value = 0
        Timber.d("Export cancelled")
    }

    fun setActiveTool(tool: EditorTool) {
        _activeTool.value = tool
    }

    override fun onCleared() {
        super.onCleared()
        // ExportServiceManager é um lifecycle observer, então se limpará automaticamente
    }
}

/**
 * Factory for creating EditorViewModel
 */
class EditorViewModelFactory(
    private val context: Context,
    private val videoUri: Uri,
    private val projectId: String? = null
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            return EditorViewModel(context, videoUri, projectId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
