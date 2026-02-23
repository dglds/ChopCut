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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class PreloadViewModel(application: Application) : AndroidViewModel(application) {
    
    private val audioDataExtractor = AudioDataExtractor(application)
    private var preloadJob: Job? = null
    private var stripManager: ThumbnailStripManager? = null
    
    private val _uiState = MutableStateFlow<PreloadUiState>(PreloadUiState.Idle)
    val uiState: StateFlow<PreloadUiState> = _uiState.asStateFlow()
    
    private val _preloadedData = MutableStateFlow<PreloadedData?>(null)
    val preloadedData: StateFlow<PreloadedData?> = _preloadedData.asStateFlow()
    
    fun startPreload(uri: Uri, screenWidthDp: Float) {
        preloadJob?.cancel()
        
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
                val thumbHeight = (thumbWidth * 0.67f).toInt() // Aspect 3:2
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
                
                // FASE 2: Extração de Áudio
                updateProgress(
                    stage = ExtractionStage.ExtractingAudio,
                    logs = listOf("Extraindo áudio...")
                )
                
                val targetBarCount = calculateTargetBarCount(durationMs)
                val audioData = extractAudioWithProgress(uri, targetBarCount)
                
                updateProgress(
                    stage = ExtractionStage.ExtractingAudio,
                    audioPercent = 100,
                    logs = listOf("Áudio: 100% ✓ (${audioData.pcmSamples.size} amostras)")
                )
                
                // FASE 3: Extração de Thumbnails
                updateProgress(
                    stage = ExtractionStage.ExtractingThumbnails,
                    logs = listOf("Extraindo thumbnails...")
                )
                
                val strips = extractThumbnailsWithProgress(
                    uri = uri,
                    stripManager = stripManager!!,
                    targetSegments = preloadSegments,
                    totalSegments = totalSegments,
                    durationMs = durationMs
                )
                
                updateProgress(
                    stage = ExtractionStage.Ready,
                    thumbnailPercent = 100,
                    logs = listOf("Thumbnails: 100% ✓ ($preloadSegments strips)")
                )
                
                // Dados prontos
                val data = PreloadedData(
                    videoUri = uri,
                    audioAmplitudes = audioData.pcmSamples.toList(),
                    preloadedStrips = strips,
                    totalSegments = totalSegments,
                    preloadPercentage = 0.5f
                )
                
                _preloadedData.value = data
                _uiState.value = PreloadUiState.Ready(data)
                
                Timber.d("Preload completed: ${strips.size} strips, ${audioData.pcmSamples.size} audio samples")
                
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
        _uiState.value = PreloadUiState.Cancelled
        Timber.d("Preload cancelled")
    }
    
    private suspend fun extractAudioWithProgress(
        uri: Uri,
        targetBarCount: Int
    ): AudioRawData {
        var lastPercent = -1
        
        // Como AudioDataExtractor não tem callback de progresso nativo,
        // vamos extrair tudo de uma vez e simular progresso para debug
        val audioData = audioDataExtractor.extractRawPcmData(uri, targetBarCount)
        
        // Emitir logs de progresso simulados para debug
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
    
    private suspend fun extractThumbnailsWithProgress(
        uri: Uri,
        stripManager: ThumbnailStripManager,
        targetSegments: Int,
        totalSegments: Int,
        durationMs: Long
    ): Map<Int, Bitmap> {
        val strips = mutableMapOf<Int, Bitmap>()
        var lastPercent = -1
        
        for (i in 0 until targetSegments) {
            val strip = stripManager.extractSegment(uri, i, durationMs)
            if (strip != null) {
                strips[i] = strip
            }
            
            // Calcular percentual
            val percent = ((i + 1) * 100 / targetSegments)
            if (percent != lastPercent) {
                updateProgress(
                    stage = ExtractionStage.ExtractingThumbnails,
                    thumbnailPercent = percent,
                    currentSegment = i + 1,
                    totalSegments = targetSegments,
                    logs = listOf("Strip ${i + 1}/$targetSegments extraída (${percent}%)")
                )
                lastPercent = percent
            }
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
            durationMs < 60000 -> 100 // < 1 min
            durationMs < 300000 -> 300 // < 5 min
            else -> 600 // 5-15 min
        }
    }
    
    private suspend fun getVideoDuration(uri: Uri): Long {
        // Usar AudioDataExtractor para obter duração (já tem MediaExtractor interno)
        try {
            val audioData = audioDataExtractor.extractRawPcmData(uri, 100)
            return audioData.durationMs
        } catch (e: Exception) {
            Timber.e(e, "Failed to get video duration")
            return VideoConstraints.MAX_DURATION_MS + 1 // Forçar erro
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        preloadJob?.cancel()
        preloadedData.value?.preloadedStrips?.values?.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
