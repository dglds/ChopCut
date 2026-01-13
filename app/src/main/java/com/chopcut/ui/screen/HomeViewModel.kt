package com.chopcut.ui.screen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.AudioRawData
import com.chopcut.data.audio.AudioExtractor
import com.chopcut.data.audio.WaveFormGenerator
import com.chopcut.data.audio.model.AudioFormat
import com.chopcut.ui.components.WaveformData
import com.chopcut.data.codec.CodecCapabilities
import com.chopcut.data.model.ExportConfig
import com.chopcut.data.model.TimeRange
import com.chopcut.data.model.Transform
import com.chopcut.data.model.VideoCodec
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.pipeline.CopyPipeline
import com.chopcut.data.pipeline.TranscodeOperations
import com.chopcut.data.pipeline.TranscodePipeline
import com.chopcut.data.repository.VideoRepository
import com.chopcut.service.ExportServiceManager
import com.chopcut.util.DispatcherProvider
import com.chopcut.util.error.ChopCutException
import com.chopcut.util.error.ErrorHandler
import com.chopcut.util.error.safeExecute
import com.chopcut.util.error.safeExecuteSuspend
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val videoRepository = VideoRepository(application)
    private val codecCapabilities = CodecCapabilities()
    private val copyPipeline = CopyPipeline(application, videoRepository)
    private val transcodePipeline = TranscodePipeline(application, videoRepository)
    private val transcodeOperations = TranscodeOperations(application, videoRepository)
    private val audioExtractor = AudioExtractor(application, videoRepository)
    private val audioDataExtractor = AudioDataExtractor(application)

    // Export service manager para testes
    private val exportServiceManager = ExportServiceManager(application)

    // Raw PCM data (extracted once, reused for waveform generation)
    private val _audioRawData = MutableStateFlow<AudioRawData?>(null)
    val audioRawData: StateFlow<AudioRawData?> = _audioRawData.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    // State for extracted audio file
    private val _extractedAudioFile = MutableStateFlow<java.io.File?>(null)
    val extractedAudioFile: StateFlow<java.io.File?> = _extractedAudioFile.asStateFlow()

    // State for raw waveform data
    private val _waveformData = MutableStateFlow<WaveformData>(WaveformData.empty())
    val waveformData: StateFlow<WaveformData> = _waveformData.asStateFlow()

    // Configurable waveform bar count
    private val _waveformBars = MutableStateFlow(50)
    val waveformBars: StateFlow<Int> = _waveformBars.asStateFlow()

    // Waveform mirrored mode
    private val _waveformMirrored = MutableStateFlow(false)
    val waveformMirrored: StateFlow<Boolean> = _waveformMirrored.asStateFlow()

    // Structured error state
    private val _errorState = MutableStateFlow<ErrorHandler.ErrorState?>(null)
    val errorState: StateFlow<ErrorHandler.ErrorState?> = _errorState.asStateFlow()

    // Export test state
    private val _exportProgress = MutableStateFlow(0)
    val exportProgress: StateFlow<Int> = _exportProgress.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    init {
        checkCodecs()
        setupExportListeners()
    }

    private fun setupExportListeners() {
        viewModelScope.launch {
            exportServiceManager.events.collect { event ->
                when (event) {
                    is ExportServiceManager.ExportEvent.Progress -> {
                        _exportProgress.value = event.progress
                        _exportStatus.value = "Exportando: ${event.progress}%"
                        Timber.d("Export progress: ${event.progress}%")
                    }
                    is ExportServiceManager.ExportEvent.Success -> {
                        _exportProgress.value = 100
                        _exportStatus.value = "Concluído: ${event.outputName}"
                        _uiState.value = HomeUiState.Success(
                            "Export via Service concluído!\nOutput: ${event.outputName}\nURI: ${event.outputUri}"
                        )
                        Timber.d("Export successful: ${event.outputName}")
                        // Resetar progresso após um delay
                        kotlinx.coroutines.delay(3000)
                        _exportProgress.value = 0
                        _exportStatus.value = null
                    }
                    is ExportServiceManager.ExportEvent.Error -> {
                        _exportStatus.value = "Erro: ${event.error}"
                        _uiState.value = HomeUiState.Error("Export falhou: ${event.error}")
                        Timber.e("Export failed: ${event.error}")
                        // Resetar progresso após um delay
                        kotlinx.coroutines.delay(3000)
                        _exportProgress.value = 0
                        _exportStatus.value = null
                    }
                }
            }
        }
    }

    /**
     * Clear the current error state
     */
    fun clearError() {
        _errorState.value = null
    }

    fun selectVideo(uri: Uri) {
        _selectedVideoUri.value = uri
        loadVideoMetadata(uri)
    }

    private fun loadVideoMetadata(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            _errorState.value = null

            val result = safeExecuteSuspend(context = getApplication()) {
                videoRepository.getMetadata(uri)
            }

            when (result) {
                is com.chopcut.util.error.ErrorResult.Success -> {
                    val metadata = result.data
                    if (metadata != null) {
                        _uiState.value = HomeUiState.VideoLoaded(metadata)
                        Timber.d("Video loaded: ${metadata.fileName}")
                    } else {
                        _errorState.value = ErrorHandler.ErrorState(
                            title = "Erro de vídeo",
                            message = "Falha ao ler metadados do vídeo",
                            recovery = com.chopcut.util.error.RecoveryStrategy.SelectAnotherVideo
                        )
                    }
                }
                is com.chopcut.util.error.ErrorResult.Error -> {
                    _errorState.value = result.errorState
                    _uiState.value = HomeUiState.Error(result.errorState.message)
                }
            }
        }
    }

    fun testTrim() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _errorState.value = ErrorHandler.ErrorState(
                title = "Nenhum vídeo selecionado",
                message = "Selecione um vídeo primeiro",
                recovery = com.chopcut.util.error.RecoveryStrategy.SelectAnotherVideo
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Trimming video...")
            _errorState.value = null

            try {
                // Trim first 5 seconds
                val range = TimeRange(startMs = 0, endMs = 5000)

                copyPipeline.trim(uri, listOf(range))
                    .collect { result ->
                        result.onSuccess { file ->
                            Timber.d("Trim completed: ${file.absolutePath}")
                            _uiState.value = HomeUiState.Success(
                                "Trim completed!\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB"
                            )
                        }.onFailure { error ->
                            Timber.e(error, "Trim failed")
                            val errorState = ErrorHandler.handle(error, getApplication())
                            _errorState.value = errorState
                            _uiState.value = HomeUiState.Error(errorState.message)
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during trim")
                val errorState = ErrorHandler.handle(e, getApplication())
                _errorState.value = errorState
                _uiState.value = HomeUiState.Error(errorState.message)
            }
        }
    }

    fun testCompress() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _errorState.value = ErrorHandler.ErrorState(
                title = "Nenhum vídeo selecionado",
                message = "Selecione um vídeo primeiro",
                recovery = com.chopcut.util.error.RecoveryStrategy.SelectAnotherVideo
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Compressing video (~2 sec test)...")
            _errorState.value = null

            try {
                // Compress to 2 Mbps (limited to 60 frames for testing)
                val targetBitrate = 2_000_000

                transcodeOperations.compress(uri, targetBitrate)
                    .collect { result ->
                        result.onSuccess { file ->
                            Timber.d("Compress completed: ${file.absolutePath}")
                            _uiState.value = HomeUiState.Success(
                                "Compression completed!\nTarget bitrate: ${targetBitrate / 1_000_000} Mbps\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB\n(Limited to 60 frames for testing)"
                            )
                        }.onFailure { error ->
                            Timber.e(error, "Compress failed")
                            val errorState = ErrorHandler.handle(error, getApplication())
                            _errorState.value = errorState
                            _uiState.value = HomeUiState.Error(errorState.message)
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during compress")
                val errorState = ErrorHandler.handle(e, getApplication())
                _errorState.value = errorState
                _uiState.value = HomeUiState.Error(errorState.message)
            }
        }
    }

    fun testResize() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _uiState.value = HomeUiState.Error("Please select a video first")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Resizing video to 50% (~2 sec test)...")

            try {
                // Get original video info to calculate target size
                val metadata = videoRepository.getMetadata(uri)
                if (metadata == null) {
                    _uiState.value = HomeUiState.Error("Failed to read video metadata")
                    return@launch
                }

                // Resize to 50% of original resolution
                val targetWidth = metadata.width / 2
                val targetHeight = metadata.height / 2

                transcodeOperations.resize(uri, targetWidth, targetHeight)
                    .collect { result ->
                        result.onSuccess { file ->
                            Timber.d("Resize completed: ${file.absolutePath}")
                            _uiState.value = HomeUiState.Success(
                                "Resize completed!\n${metadata.width}x${metadata.height} → ${targetWidth}x${targetHeight}\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB\n(Limited to 60 frames for testing)"
                            )
                        }.onFailure { error ->
                            Timber.e(error, "Resize failed")
                            _uiState.value = HomeUiState.Error(error.message ?: "Resize failed")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during resize")
                _uiState.value = HomeUiState.Error(e.message ?: "Resize failed")
            }
        }
    }

    fun testCrop() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _uiState.value = HomeUiState.Error("Please select a video first")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Cropping video center 50% (~2 sec test)...")

            try {
                // Crop center 50% of the video
                val cropRect = android.graphics.RectF(
                    0.25f,  // left 25%
                    0.25f,  // top 25%
                    0.75f,  // right 75%
                    0.75f   // bottom 75%
                )

                transcodeOperations.crop(uri, cropRect)
                    .collect { result ->
                        result.onSuccess { file ->
                            Timber.d("Crop completed: ${file.absolutePath}")
                            _uiState.value = HomeUiState.Success(
                                "Crop completed!\nCenter 50% cropped\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB\n(Limited to 60 frames for testing)"
                            )
                        }.onFailure { error ->
                            Timber.e(error, "Crop failed")
                            _uiState.value = HomeUiState.Error(error.message ?: "Crop failed")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during crop")
                _uiState.value = HomeUiState.Error(e.message ?: "Crop failed")
            }
        }
    }

    fun testExtractAudio() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _uiState.value = HomeUiState.Error("Please select a video first")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Extracting audio...")

            try {
                audioExtractor.extract(uri, AudioFormat.AAC)
                    .collect { result ->
                        result.onSuccess { file ->
                            Timber.d("TIME: audio_extract_success: ${file.absolutePath}")
                            _extractedAudioFile.value = file
                            _uiState.value = HomeUiState.Success(
                                "Audio extracted!\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB\nFormat: AAC (.m4a)"
                            )
                        }.onFailure { error ->
                            Timber.e(error, "Audio extract failed")
                            _uiState.value = HomeUiState.Error(error.message ?: "Extract audio failed")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during audio extraction")
                _uiState.value = HomeUiState.Error(e.message ?: "Extract audio failed")
            }
        }
    }

    // Extract raw PCM data (once, reused for waveform)
    fun extractPcmData() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _uiState.value = HomeUiState.Error("Please select a video first")
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Extracting audio data...")
            try {
                val rawData = audioDataExtractor.extractRawPcmData(uri)
                _audioRawData.value = rawData
                _waveformData.value = WaveformData.empty()  // Clear old waveform

                _uiState.value = HomeUiState.Success(
                    "Audio data extracted!\n" +
                    "Samples: ${rawData.pcmSamples.size}\n" +
                    "Duration: ${rawData.durationMs}ms\n" +
                    "Sample rate: ${rawData.sampleRate}Hz"
                )

            } catch (e: Exception) {
                Timber.e(e, "Error during PCM extraction")
                _uiState.value = HomeUiState.Error(e.message ?: "PCM extraction failed")
            }
        }
    }

    // NEW: Generate waveform from already extracted data
    fun generateWaveform() {
        val rawData = _audioRawData.value
        if (rawData == null || rawData.pcmSamples.isEmpty()) {
            _uiState.value = HomeUiState.Error("Extract audio data first!")
            return
        }

        viewModelScope.launch {
            try {
                val amplitudes = WaveFormGenerator.generateWaveform(
                    pcmSamples = rawData.pcmSamples,
                    barCount = _waveformBars.value
                )

                _waveformData.value = WaveformData(
                    amplitudes = amplitudes,
                    sampleRate = rawData.sampleRate,
                    durationMs = rawData.durationMs
                )

                Timber.d("TIME: waveform generated: ${amplitudes.size} bars")

                _uiState.value = HomeUiState.Success(
                    "Waveform generated!\n" +
                    "Bars: ${amplitudes.size}\n" +
                    "Change bar count and regenerate!"
                )

            } catch (e: Exception) {
                Timber.e(e, "Error during waveform generation")
                _uiState.value = HomeUiState.Error(e.message ?: "Waveform generation failed")
            }
        }
    }

    fun checkCodecs() {
        viewModelScope.launch {
            try {
                codecCapabilities.logAvailableCodecs()

                val bestCodec = codecCapabilities.selectBestEncoder()
                val codecs = VideoCodec.entries

                _uiState.value = HomeUiState.CodecsLoaded(
                    codecs = codecs.map { codec ->
                        CodecInfo(
                            name = codec.displayName,
                            hasEncoder = codecCapabilities.hasEncoder(codec),
                            hasDecoder = codecCapabilities.hasDecoder(codec),
                            isBest = bestCodec == codec
                        )
                    }
                )

                Timber.d("Best codec: ${bestCodec?.displayName}")
            } catch (e: Exception) {
                Timber.e(e, "Error checking codecs")
                _uiState.value = HomeUiState.Error("Failed to check codecs: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Initial
    }

    fun setWaveformBars(bars: Int) {
        _waveformBars.value = bars.coerceIn(10, 500)
    }

    fun toggleWaveformMirrored() {
        _waveformMirrored.value = !_waveformMirrored.value
    }

    /**
     * Teste de export usando ForegroundService
     * Mostra notificação de progresso e continua em background
     */
    fun testExportForegroundService() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _errorState.value = ErrorHandler.ErrorState(
                title = "Nenhum vídeo selecionado",
                message = "Selecione um vídeo primeiro",
                recovery = com.chopcut.util.error.RecoveryStrategy.SelectAnotherVideo
            )
            return
        }

        viewModelScope.launch(DispatcherProvider.io) {
            _exportProgress.value = 0
            _exportStatus.value = "Iniciando export..."

            try {
                // Obter duração do vídeo para exportar até 30s ou o completo se for menor
                val metadata = videoRepository.getMetadata(uri)
                val durationMs = metadata?.durationUs?.div(1000) ?: 30000
                val exportDurationMs = minOf(durationMs, 30000) // Máximo 30s

                val timeRange = TimeRange(startMs = 0, endMs = exportDurationMs)
                val outputName = "test_service_${System.currentTimeMillis()}.mp4"

                exportServiceManager.startExport(
                    videoUri = uri,
                    timeRanges = listOf(timeRange),
                    outputName = outputName,
                    exportType = "trim"
                )

                _uiState.value = HomeUiState.Processing("Export iniciado via ForegroundService...")
                Timber.d("ForegroundService export started: ${exportDurationMs}ms")
            } catch (e: Exception) {
                Timber.e(e, "Error starting foreground service export")
                _uiState.value = HomeUiState.Error("Erro ao iniciar export: ${e.message}")
            }
        }
    }

    /**
     * Teste de export com RE-ENCODING (garante qualidade, mas é mais lento)
     * Usa TranscodePipeline com as mesmas configurações do vídeo original
     */
    fun testExportReencode() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _errorState.value = ErrorHandler.ErrorState(
                title = "Nenhum vídeo selecionado",
                message = "Selecione um vídeo primeiro",
                recovery = com.chopcut.util.error.RecoveryStrategy.SelectAnotherVideo
            )
            return
        }

        viewModelScope.launch(DispatcherProvider.io) {
            _exportProgress.value = 0
            _exportStatus.value = "Iniciando export (re-encode)..."

            try {
                val metadata = videoRepository.getMetadata(uri)
                    ?: throw IllegalArgumentException("Não foi possível ler metadados do vídeo")

                // Usar configuração baseada no vídeo original
                val config = com.chopcut.data.model.ExportConfig.fromVideoInfo(metadata)

                Timber.d("Export re-encode: ${metadata.width}x${metadata.height}, codec=${metadata.videoCodec}, bitrate=${config.bitrate}")

                _uiState.value = HomeUiState.Processing("Re-encodando vídeo...\nIsso pode levar alguns segundos")

                transcodePipeline.process(uri, com.chopcut.data.model.Transform.IDENTITY, config)
                    .collect { result ->
                        result.getOrNull()?.let { file ->
                            _exportProgress.value = 100
                            _exportStatus.value = "Salvando na galeria..."

                            viewModelScope.launch(DispatcherProvider.io) {
                                val outputName = "reencode_${System.currentTimeMillis()}.mp4"
                                val outputUri = videoRepository.saveToGallery(file, outputName)

                                outputUri?.let {
                                    _exportProgress.value = 0
                                    _uiState.value = HomeUiState.Success(
                                        "Export (Re-encode) concluído!\n" +
                                        "Output: $outputName\n" +
                                        "Size: ${file.length() / 1024} KB\n" +
                                        "URI: $it"
                                    )
                                } ?: run {
                                    _uiState.value = HomeUiState.Error("Falha ao salvar na galeria")
                                }
                            }
                        } ?: run {
                            val error = result.exceptionOrNull()
                            Timber.e(error, "Re-encode failed")
                            _exportProgress.value = 0
                            _uiState.value = HomeUiState.Error("Re-encode falhou: ${error?.message}")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during re-encode")
                _exportProgress.value = 0
                _uiState.value = HomeUiState.Error("Erro ao iniciar re-encode: ${e.message}")
            }
        }
    }

    /**
     * Teste de export usando WorkManager
     * Persiste mesmo se o app for fechado
     */
    fun testExportWorkManager() {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            _errorState.value = ErrorHandler.ErrorState(
                title = "Nenhum vídeo selecionado",
                message = "Selecione um vídeo primeiro",
                recovery = com.chopcut.util.error.RecoveryStrategy.SelectAnotherVideo
            )
            return
        }

        viewModelScope.launch(DispatcherProvider.io) {
            try {
                val timeRange = TimeRange(startMs = 0, endMs = 5000)
                val outputName = "test_workmanager_${System.currentTimeMillis()}.mp4"

                val scheduler = com.chopcut.service.ExportWorkScheduler(getApplication())
                scheduler.scheduleExport(
                    videoUri = uri,
                    timeRanges = listOf(timeRange),
                    outputName = outputName
                )

                _uiState.value = HomeUiState.Processing("Export agendado via WorkManager")
                Timber.d("WorkManager export scheduled")
            } catch (e: Exception) {
                Timber.e(e, "Error scheduling work manager export")
                _uiState.value = HomeUiState.Error("Erro ao agendar export: ${e.message}")
            }
        }
    }

    /**
     * Cancela export em andamento
     */
    fun cancelExport() {
        exportServiceManager.cancelExport()
        _exportProgress.value = 0
        _exportStatus.value = "Export cancelado"
        _uiState.value = HomeUiState.Success("Export cancelado")
        Timber.d("Export cancelled")
    }
}

sealed class HomeUiState {
    object Initial : HomeUiState()
    object Loading : HomeUiState()
    data class VideoLoaded(val videoInfo: VideoInfo) : HomeUiState()
    data class CodecsLoaded(val codecs: List<CodecInfo>) : HomeUiState()
    data class Processing(val message: String) : HomeUiState()
    data class Success(val message: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

data class CodecInfo(
    val name: String,
    val hasEncoder: Boolean,
    val hasDecoder: Boolean,
    val isBest: Boolean
)
