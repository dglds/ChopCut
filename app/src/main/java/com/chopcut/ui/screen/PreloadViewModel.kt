package com.chopcut.ui.screen

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.AudioRawData
import com.chopcut.data.thumbnail.ThumbnailStripManager
import com.chopcut.util.DispatcherProvider
import com.chopcut.utils.VideoConstraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class PreloadViewModel(application: Application) : AndroidViewModel(application) {
    
    private val audioDataExtractor = AudioDataExtractor(application)
    private var preloadJob: Job? = null
    private var thumbnailJob: Job? = null
    private var stripManager: ThumbnailStripManager? = null
    
    private val _uiState = MutableStateFlow<PreloadUiState>(PreloadUiState.Idle)
    val uiState: StateFlow<PreloadUiState> = _uiState.asStateFlow()
    
    private val _preloadedData = MutableStateFlow<PreloadedData?>(null)
    val preloadedData: StateFlow<PreloadedData?> = _preloadedData.asStateFlow()
    
    fun startPreload(uri: Uri, screenWidthDp: Float) {
        preloadJob?.cancel()
        thumbnailJob?.cancel()
        
        preloadJob = viewModelScope.launch(DispatcherProvider.io) {
            try {
                _uiState.value = PreloadUiState.Loading(
                    PreloadProgress(stage = ExtractionStage.Starting)
                )
                
                // FASE 1: Validação
                updateProgress(ExtractionStage.Validating, logs = listOf("Validando vídeo..."))
                
                val durationMs = getVideoDuration(uri)
                if (!VideoConstraints.isDurationValid(durationMs)) {
                    val message = VideoConstraints.getValidationMessage(durationMs)!!
                    _uiState.value = PreloadUiState.Error(
                        message = message,
                        isDurationExceeded = true
                    )
                    return@launch
                }
                
                updateProgress(
                    stage = ExtractionStage.Validating,
                    logs = listOf(
                        "Duração: ${durationMs / 60000}min",
                        "Validado: ≤ 15min ✓"
                    )
                )
                
                // Inicializar StripManager com dimensões baseadas na largura da tela
                val thumbWidth = screenWidthDp.toInt()
                val thumbHeight = (thumbWidth * 0.67f).toInt()
                stripManager = ThumbnailStripManager(getApplication(), thumbWidth, thumbHeight)
                
                val totalSegments = stripManager!!.getSegmentCount(durationMs)
                val preloadSegments = (totalSegments * 0.5f).toInt().coerceAtLeast(1)
                
                updateProgress(
                    stage = ExtractionStage.Validating,
                    totalSegments = totalSegments,
                    logs = listOf(
                        "Total strips: $totalSegments",
                        "Pré-carregar: $preloadSegments (50%)"
                    )
                )
                
                // PRIORIDADE: Extração de Thumbnails com delay
                updateProgress(
                    stage = ExtractionStage.ExtractingAudio,
                    logs = listOf("Aguardando delay para extração de thumbnails...")
                )
                
                // Iniciar extração de thumbnails com delay (PRIORIDADE)
                thumbnailJob = viewModelScope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(PreloadConfig.THUMBNAIL_EXTRACTION_DELAY_MS)
                    
                    updateProgress(
                        stage = ExtractionStage.WaitingForThumbnails,
                        logs = listOf("Iniciando extração de thumbnails (prioridade)...")
                    )
                    
                    val strips = extractThumbnailsWithPriority(
                        uri = uri,
                        stripManager = stripManager!!,
                        targetSegments = preloadSegments,
                        totalSegments = totalSegments,
                        durationMs = durationMs
                    )
                    
                    // Quando thumbnails terminam, marcar como Ready se já tiver áudio
                    val currentAudio = _preloadedData.value?.audioAmplitudes
                    if (currentAudio != null) {
                        updateProgress(
                            stage = ExtractionStage.Ready,
                            thumbnailPercent = 100,
                            logs = listOf("Thumbnails: 100% ✓ ($preloadSegments strips)")
                        )
                        
                        val data = PreloadedData(
                            videoUri = uri,
                            audioAmplitudes = currentAudio,
                            preloadedStrips = strips,
                            totalSegments = totalSegments,
                            preloadPercentage = 0.5f
                        )
                        
                        _preloadedData.value = data
                        _uiState.value = PreloadUiState.Ready(data)
                        
                        Timber.d("Preload completed: ${strips.size} strips, ${currentAudio.size} audio samples")
                    } else {
                        // Se não tem áudio, aguardar (áudio será extraído em paralelo)
                        Timber.d("Thumbnails completed, waiting for audio...")
                    }
                }
                
                // Extração de áudio em paralelo (sem prioridade)
                val targetBarCount = calculateTargetBarCount(durationMs)
                val audioData = extractAudioWithProgress(uri, targetBarCount)
                
                // Salvar dados parciais quando áudio termina
                val currentStrips = _preloadedData.value?.preloadedStrips
                _preloadedData.value = PreloadedData(
                    videoUri = uri,
                    audioAmplitudes = audioData.pcmSamples.toList(),
                    preloadedStrips = currentStrips ?: emptyMap(),
                    totalSegments = totalSegments,
                    preloadPercentage = 0.5f
                )
                
                // Se thumbnails já terminaram, marcar como Ready
                if (currentStrips != null && currentStrips.isNotEmpty()) {
                    updateProgress(
                        stage = ExtractionStage.Ready,
                        audioPercent = 100,
                        thumbnailPercent = 100,
                        logs = listOf(
                            "Áudio: 100% ✓",
                            "Thumbnails: 100% ✓"
                        )
                    )
                    
                    val data = PreloadedData(
                        videoUri = uri,
                        audioAmplitudes = audioData.pcmSamples.toList(),
                        preloadedStrips = currentStrips,
                        totalSegments = totalSegments,
                        preloadPercentage = 0.5f
                    )
                    
                    _preloadedData.value = data
                    _uiState.value = PreloadUiState.Ready(data)
                    
                    Timber.d("Preload completed: ${currentStrips.size} strips, ${audioData.pcmSamples.size} audio samples")
                }
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Preload failed")
                    _uiState.value = PreloadUiState.Error(
                        message = e.message ?: "Erro ao preparar vídeo"
                    )
                }
            }
        }
    }
    
    fun cancelPreload() {
        preloadJob?.cancel()
        thumbnailJob?.cancel()
        _uiState.value = PreloadUiState.Cancelled
        Timber.d("Preload cancelled")
    }
    
    private suspend fun extractAudioWithProgress(
        uri: Uri,
        targetBarCount: Int
    ): AudioRawData {
        var lastPercent = -1
        
        val audioData = audioDataExtractor.extractRawPcmData(uri, targetBarCount)
        
        val totalSteps = 5
        for (i in 1..totalSteps) {
            val percent = (i * 100 / totalSteps)
            if (percent != lastPercent) {
                updateProgress(
                    stage = ExtractionStage.ExtractingAudio,
                    audioPercent = percent,
                    logs = listOf("Extraindo áudio: $percent%")
                )
                lastPercent = percent
            }
        }
        
        return audioData
    }
    
    private suspend fun extractThumbnailsWithPriority(
        uri: Uri,
        stripManager: ThumbnailStripManager,
        targetSegments: Int,
        totalSegments: Int,
        durationMs: Long
    ): Map<Int, Bitmap> {
        val strips = mutableMapOf<Int, Bitmap>()
        var lastPercent = -1
        
        // PRIORIDADE: Primeira strip com Dispatchers.Default (alta prioridade)
        updateProgress(
            stage = ExtractionStage.ExtractingThumbnails,
            currentSegment = 1,
            logs = listOf("Strip 1 extraída instantaneamente (prioridade)")
        )
        
        val strip0 = withContext(Dispatchers.Default) {
            stripManager.extractSegment(uri, 0, durationMs)
        }
        if (strip0 != null) {
            strips[0] = strip0
        }
        
        Timber.d("First strip extracted immediately: ${strip0 != null}")
        
        // Continuar com restante em paralelo (Dispatchers.IO, limitado por Semaphore)
        if (targetSegments > 1) {
            val remainingSegments = (1 until targetSegments).toList()
            
            val results = remainingSegments.map { segIdx ->
                viewModelScope.async(Dispatchers.IO) {
                    val strip = stripManager.extractSegment(uri, segIdx, durationMs)
                    if (strip != null) {
                        strips[segIdx] = strip
                    }
                    
                    val percent = ((segIdx + 1) * 100 / targetSegments)
                    if (percent != lastPercent) {
                        updateProgress(
                            stage = ExtractionStage.ExtractingThumbnails,
                            thumbnailPercent = percent,
                            currentSegment = segIdx + 1,
                            totalSegments = targetSegments,
                            logs = listOf("Strip ${segIdx + 1}/$targetSegments extraída (${percent}%)")
                        )
                        lastPercent = percent
                    }
                    
                    strip
                }
            }.awaitAll()
        }
        
        return strips
    }
    
    private fun updateProgress(
        stage: ExtractionStage,
        audioPercent: Int = 0,
        thumbnailPercent: Int = 0,
        currentSegment: Int = 0,
        totalSegments: Int = 0,
        logs: List<String> = emptyList()
    ) {
        val currentState = _uiState.value
        
        if (currentState is PreloadUiState.Loading) {
            val currentLogs = currentState.progress.logs
            val newLogs = if (logs.isEmpty()) currentLogs else {
                val maxLogs = 20
                (currentLogs + logs).takeLast(maxLogs)
            }
            
            _uiState.update {
                PreloadUiState.Loading(
                    currentState.progress.copy(
                        stage = stage,
                        audioPercent = audioPercent,
                        thumbnailPercent = thumbnailPercent,
                        currentSegment = currentSegment,
                        totalSegments = totalSegments,
                        logs = newLogs
                    )
                )
            }
        }
    }
    
    private fun calculateTargetBarCount(durationMs: Long): Int {
        return when {
            durationMs < 60000 -> 100
            durationMs < 300000 -> 300
            else -> 600
        }
    }
    
    private suspend fun getVideoDuration(uri: Uri): Long {
        try {
            val audioData = audioDataExtractor.extractRawPcmData(uri, 100)
            return audioData.durationMs
        } catch (e: Exception) {
            Timber.e(e, "Failed to get video duration")
            return VideoConstraints.MAX_DURATION_MS + 1
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        preloadJob?.cancel()
        thumbnailJob?.cancel()
        preloadedData.value?.preloadedStrips?.values?.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
