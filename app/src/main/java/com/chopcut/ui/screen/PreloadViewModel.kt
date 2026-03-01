package com.chopcut.ui.screen

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.AudioRawData
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.model.ThumbnailQuality
import com.chopcut.data.repository.VideoRepository
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

class PreloadViewModel(
    application: Application,
    private val videoRepository: VideoRepository
) : AndroidViewModel(application) {

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

                // FASE 1: Extração de Metadados e Validação
                updateProgress(stage = ExtractionStage.Validating, logs = listOf("Extraindo metadados..."))
                val videoInfo = videoRepository.getMetadata(uri)
                if (videoInfo == null) {
                    _uiState.value = PreloadUiState.Error("Não foi possível ler os metadados do vídeo.")
                    return@launch
                }

                updateProgress(stage = ExtractionStage.Validating, logs = listOf("Validando vídeo..."))
                if (!VideoConstraints.isDurationValid(videoInfo.durationMs)) {
                    val message = VideoConstraints.getValidationMessage(videoInfo.durationMs)!!
                    _uiState.value = PreloadUiState.Error(
                        message = message,
                        isDurationExceeded = true
                    )
                    return@launch
                }

                updateProgress(
                    stage = ExtractionStage.Validating,
                    logs = listOf(
                        "Duração: ${videoInfo.durationMs / 60000}min",
                        "Validado: ≤ 15min ✓"
                    )
                )

                // Inicializar StripManager com dimensões baseadas no aspect ratio do vídeo
                // IMPORTANTE: Usar as mesmas dimensões do TimelineEditor (60dp de largura)
                val thumbWidth = screenWidthDp.toInt()
                val thumbHeight = (thumbWidth / videoInfo.aspectRatio).toInt().coerceAtLeast(1)
                stripManager = ThumbnailStripManager(getApplication(), thumbWidth, thumbHeight)

                Timber.d("ThumbnailStripManager iniciado: thumbWidth=$thumbWidth, thumbHeight=$thumbHeight, aspectRatio=${videoInfo.aspectRatio}")

                val totalSegments = stripManager!!.getSegmentCount(videoInfo.durationMs)
                // OTIMIZADO: Pré-carregar apenas os primeiros 3 segmentos (suficiente para o início)
                // O carregamento radial no TimelineEditor cuidará do resto conforme o usuário navega.
                // Foco em renderizar thumbs do cache em vez de extrair novas.
                val preloadSegments = 3.coerceAtMost(totalSegments)

                updateProgress(
                    stage = ExtractionStage.Validating,
                    totalSegments = totalSegments,
                    logs = listOf(
                        "Total strips: $totalSegments",
                        "Pré-carregar: $preloadSegments"
                    )
                )

                // Iniciar extração de thumbnails (foco em cache)
                thumbnailJob = viewModelScope.launch(Dispatchers.IO) {
                    updateProgress(
                        stage = ExtractionStage.ExtractingThumbnails,
                        logs = listOf("Carregando thumbnails do cache...")
                    )

                    // Extração de alta qualidade (HIGH quality) - agora em um único passo
                    val strips = extractThumbnailsWithPriority(
                        uri = uri,
                        stripManager = stripManager!!,
                        targetSegments = preloadSegments,
                        totalSegments = totalSegments,
                        durationMs = videoInfo.durationMs
                    )
                    
                    // Atualizar dados com as strips extraídas
                    updateDataWithStrips(videoInfo, strips, totalSegments)
                    
                    updateProgress(
                        stage = ExtractionStage.Ready,
                        thumbnailPercent = 100,
                        logs = listOf("Thumbnails processadas: 100% ✓")
                    )
                }

                // Extração de áudio em paralelo
                val targetBarCount = calculateTargetBarCount(videoInfo.durationMs)
                val audioData = extractAudioWithProgress(uri, targetBarCount)

                val currentStrips = _preloadedData.value?.preloadedStrips
                _preloadedData.value = PreloadedData(
                    videoInfo = videoInfo,
                    audioAmplitudes = audioData.pcmSamples.toList(),
                    preloadedStrips = currentStrips ?: emptyMap(),
                    totalSegments = totalSegments,
                    preloadPercentage = 0.5f
                )

                if (currentStrips != null && currentStrips.isNotEmpty()) {
                    updateProgress(
                        stage = ExtractionStage.Ready,
                        audioPercent = 100,
                        thumbnailPercent = 100,
                        logs = listOf("Áudio: 100% ✓", "Thumbnails: 100% ✓")
                    )

                    val data = PreloadedData(
                        videoInfo = videoInfo,
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

    private fun updateProgress(
        stage: ExtractionStage? = null,
        audioPercent: Int? = null,
        thumbnailPercent: Int? = null,
        currentSegment: Int? = null,
        totalSegments: Int? = null,
        logs: List<String>? = null,
        preloadedStrips: Map<Int, Bitmap>? = null,
        thumbnailsExtracted: Int? = null,
        thumbnailsTotal: Int? = null,
        audioAmplitudesCount: Int? = null,
        audioAmplitudesTotal: Int? = null
    ) {
        val currentState = _uiState.value
        val currentProgress = if (currentState is PreloadUiState.Loading) {
            currentState.progress
        } else {
            PreloadProgress(stage = ExtractionStage.Starting)
        }

        val newLogs = if (logs == null || logs.isEmpty()) currentProgress.logs else {
            (currentProgress.logs + logs).takeLast(20)
        }

        _uiState.update {
            PreloadUiState.Loading(
                currentProgress.copy(
                    stage = stage ?: currentProgress.stage,
                    audioPercent = audioPercent ?: currentProgress.audioPercent,
                    thumbnailPercent = thumbnailPercent ?: currentProgress.thumbnailPercent,
                    currentSegment = currentSegment ?: currentProgress.currentSegment,
                    totalSegments = totalSegments ?: currentProgress.totalSegments,
                    logs = newLogs,
                    preloadedStrips = preloadedStrips ?: currentProgress.preloadedStrips,
                    thumbnailsExtracted = thumbnailsExtracted ?: currentProgress.thumbnailsExtracted,
                    thumbnailsTotal = thumbnailsTotal ?: currentProgress.thumbnailsTotal,
                    audioAmplitudesCount = audioAmplitudesCount ?: currentProgress.audioAmplitudesCount,
                    audioAmplitudesTotal = audioAmplitudesTotal ?: currentProgress.audioAmplitudesTotal
                )
            )
        }
    }

    private fun calculateTargetBarCount(durationMs: Long): Int {
        return when {
            durationMs < 60000 -> 100
            durationMs < 300000 -> 300
            else -> 600
        }
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
                    audioAmplitudesCount = (audioData.pcmSamples.size * percent / 100),
                    audioAmplitudesTotal = audioData.pcmSamples.size,
                    logs = listOf("Extraindo áudio: $percent%")
                )
                lastPercent = percent
            }
        }

        return audioData
    }

    private fun updateDataWithStrips(
        videoInfo: VideoInfo,
        newStrips: Map<Int, Bitmap>,
        totalSegments: Int
    ) {
        val currentData = _preloadedData.value
        val audioAmplitudes = currentData?.audioAmplitudes ?: emptyList()
        val existingStrips = currentData?.preloadedStrips ?: emptyMap()
        
        // Merge strips (new ones override old ones if same index)
        val mergedStrips = existingStrips.toMutableMap()
        mergedStrips.putAll(newStrips)
        
        val data = PreloadedData(
            videoInfo = videoInfo,
            audioAmplitudes = audioAmplitudes,
            preloadedStrips = mergedStrips,
            totalSegments = totalSegments,
            preloadPercentage = 0.5f
        )
        
        _preloadedData.value = data
        
        // Se o áudio estiver pronto, atualizamos o Ready state também
        if (audioAmplitudes.isNotEmpty()) {
            _uiState.value = PreloadUiState.Ready(data)
        }
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

        Timber.d("Extraindo até $targetSegments segmentos (foco em cache)")

        for (segIdx in 0 until targetSegments) {
            val strip = stripManager.extractSegment(uri, segIdx, durationMs)
            if (strip != null) {
                strips[segIdx] = strip

                val percent = ((segIdx + 1) * 100 / targetSegments)
                if (percent != lastPercent) {
                    updateProgress(
                        stage = ExtractionStage.ExtractingThumbnails,
                        thumbnailPercent = percent,
                        currentSegment = segIdx + 1,
                        totalSegments = targetSegments,
                        thumbnailsExtracted = strips.size,
                        thumbnailsTotal = totalSegments,
                        logs = listOf("Strip ${segIdx + 1}/$targetSegments carregada (${percent}%)"),
                        preloadedStrips = strips.toMap()
                    )
                    lastPercent = percent
                }
            } else {
                Timber.w("Strip $segIdx não carregada (continuando)")
            }
        }

        Timber.d("Strips carregadas: ${strips.size}/$targetSegments")
        return strips
    }
    
    override fun onCleared() {
        super.onCleared()
        preloadJob?.cancel()
        thumbnailJob?.cancel()
        // REMOVIDO: Não reciclar bitmaps aqui, pois eles podem estar em uso no TrimScreen/PreloadDataStore
    }
}
