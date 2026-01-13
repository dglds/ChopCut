package com.chopcut.ui.screen

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.AudioRawData
import com.chopcut.data.audio.WaveFormGenerator
import com.chopcut.ui.components.WaveformData
import com.chopcut.data.model.TimeRange
import com.chopcut.data.model.Transform
import com.chopcut.data.model.ExportConfig
import com.chopcut.data.pipeline.TranscodePipeline
import kotlinx.coroutines.flow.collect
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.repository.ProjectRepository
import com.chopcut.data.repository.VideoRepository
import com.chopcut.service.ExportServiceManager
import com.chopcut.service.ExportWorkScheduler
import com.chopcut.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

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
    private val audioDataExtractor = AudioDataExtractor(context)
    private val transcodePipeline = TranscodePipeline(context, videoRepository)

    // Service manager para export em background
    private val exportServiceManager = ExportServiceManager(context)

    // Project state
    private val _project = MutableStateFlow<com.chopcut.data.model.Project?>(null)
    val project: StateFlow<com.chopcut.data.model.Project?> = _project.asStateFlow()

    private val _edits = MutableStateFlow<List<com.chopcut.data.model.EditOperation>>(emptyList())
    val edits: StateFlow<List<com.chopcut.data.model.EditOperation>> = _edits.asStateFlow()

    // Video info state
    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val videoInfo: StateFlow<VideoInfo?> = _videoInfo.asStateFlow()

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

    init {
        if (projectId != null) {
            loadProject(projectId)
        } else {
            loadVideoMetadata(videoUri)
        }
        setupExportEventListeners()
    }

    private fun loadProject(id: String) {
        viewModelScope.launch(DispatcherProvider.io) {
            projectRepository.getProjectWithEdits(id)?.let { pair ->
                val project = pair.first
                val edits = pair.second
                _project.value = project
                _edits.value = edits
                loadVideoMetadata(Uri.parse(project.sourceVideoUri))
            }
        }
    }

    /**
     * Saves the current project state
     */
    fun saveProject(name: String? = null) {
        viewModelScope.launch(DispatcherProvider.io) {
            val currentProject = _project.value
            val metadata = _videoInfo.value ?: return@launch

            val projectToSave = if (currentProject != null) {
                currentProject.copy(
                    name = name ?: currentProject.name,
                    modifiedAt = System.currentTimeMillis()
                )
            } else {
                com.chopcut.data.model.Project(
                    name = name ?: metadata.fileName,
                    sourceVideoUri = videoUri.toString(),
                    duration = metadata.durationMs
                )
            }

            projectRepository.saveProject(projectToSave, _edits.value)
            _project.value = projectToSave
            Timber.d("Project saved: ${projectToSave.name}")
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
     * Executes a test operation with fixed values using TranscodePipeline
     */
    fun testOperation(type: String) {
        viewModelScope.launch(DispatcherProvider.io) {
            val metadata = _videoInfo.value ?: return@launch
            _isExporting.value = true
            _exportResult.value = null
            _exportProgress.value = 0

            try {
                val outputName = "test_${type}_${System.currentTimeMillis()}.mp4"
                
                val transform = when (type) {
                    "rotate" -> Transform(rotation = 90f)
                    "resize" -> Transform(scaleX = 0.5f, scaleY = 0.5f)
                    "crop" -> Transform(cropRect = android.graphics.RectF(0.25f, 0.25f, 0.75f, 0.75f))
                    else -> Transform.IDENTITY
                }

                // Use default export config based on metadata but with 60 frames limit for speed
                val config = ExportConfig.fromVideoInfo(metadata).copy(
                    bitrate = 2_000_000 // lower bitrate for faster test
                )

                Timber.d("Starting transcode test: $type")
                
                transcodePipeline.process(videoUri, transform, config)
                    .collect { result ->
                        result.onSuccess { file ->
                            val outputUri = videoRepository.saveToGallery(file, outputName)
                            _exportProgress.value = 100
                            _isExporting.value = false
                            _exportResult.value = Result.success(outputUri ?: Uri.EMPTY)
                            Timber.d("Test operation $type completed: $outputUri")
                        }.onFailure { error ->
                            Timber.e(error, "Test operation $type failed")
                            _exportResult.value = Result.failure(error)
                            _isExporting.value = false
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during test operation")
                _exportResult.value = Result.failure(e)
                _isExporting.value = false
            }
        }
    }

    /**
     * Export trimmed video usando ForegroundService
     */
    fun exportTrimmedVideo(trimRange: com.chopcut.ui.components.TrimRange) {
        viewModelScope.launch(DispatcherProvider.io) {
            _isExporting.value = true
            _exportResult.value = null
            _exportProgress.value = 0

            try {
                val timeRange = TimeRange(trimRange.startMs, trimRange.endMs)
                val outputName = "trimmed_${System.currentTimeMillis()}.mp4"

                exportServiceManager.startExport(
                    videoUri = videoUri,
                    timeRanges = listOf(timeRange),
                    outputName = outputName,
                    exportType = "trim"
                )

                Timber.d("Export started via ForegroundService")
            } catch (e: Exception) {
                Timber.e(e, "Error during export")
                _exportResult.value = Result.failure(e)
                _isExporting.value = false
            }
        }
    }

    /**
     * Exporta vídeo usando WorkManager (para operações muito longas)
     */
    fun exportTrimmedVideoLong(trimRange: com.chopcut.ui.components.TrimRange) {
        viewModelScope.launch(DispatcherProvider.io) {
            _isExporting.value = true
            _exportResult.value = null
            _exportProgress.value = 0

            try {
                val timeRange = TimeRange(trimRange.startMs, trimRange.endMs)
                val outputName = "trimmed_${System.currentTimeMillis()}.mp4"

                val scheduler = ExportWorkScheduler(context)
                scheduler.scheduleExport(
                    videoUri = videoUri,
                    timeRanges = listOf(timeRange),
                    outputName = outputName
                )

                Timber.d("Export started via WorkManager")
                // Nota: WorkManager não envia progresso em tempo real
                // O resultado será entregue quando o worker completar
            } catch (e: Exception) {
                Timber.e(e, "Error during export")
                _exportResult.value = Result.failure(e)
                _isExporting.value = false
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
