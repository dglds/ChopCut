package com.chopcut

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import java.io.File
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class MarkerInterval(
    val id: Int,
    val startMs: Long,
    val endMs: Long
)

sealed class ExportUiState {
    data object Idle : ExportUiState()
    data class Exporting(val progress: Int = -1) : ExportUiState()
    data class Success(val shareUri: Uri) : ExportUiState()
    data class Error(val message: String) : ExportUiState()
}

class TimelineViewModel(
    application: Application,
    private val videoUri: Uri?
) : AndroidViewModel(application) {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _durationMs = MutableStateFlow(59_000L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _markerIntervals = MutableStateFlow<List<MarkerInterval>>(emptyList())
    val markerIntervals: StateFlow<List<MarkerInterval>> = _markerIntervals.asStateFlow()

    private val _activeMarkerStartMs = MutableStateFlow<Long?>(null)
    val activeMarkerStartMs: StateFlow<Long?> = _activeMarkerStartMs.asStateFlow()

    private val _thumbBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val thumbBitmaps: StateFlow<Map<Int, Bitmap>> = _thumbBitmaps.asStateFlow()

    private val _isPreviewMode = MutableStateFlow(false)
    val isPreviewMode: StateFlow<Boolean> = _isPreviewMode.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val videoRepository = VideoRepository(application)

    private val _videoAr = MutableStateFlow(16f / 9f)
    val aspectRatio: StateFlow<Float> = _videoAr.asStateFlow()

    private val _videoWidth  = MutableStateFlow(1920)
    private val _videoHeight = MutableStateFlow(1080)
    val videoWidth:  StateFlow<Int> = _videoWidth.asStateFlow()
    val videoHeight: StateFlow<Int> = _videoHeight.asStateFlow()

    private val _videoBitrate = MutableStateFlow(0L)
    val videoBitrate: StateFlow<Long> = _videoBitrate.asStateFlow()

    private val _videoSizeBytes = MutableStateFlow(0L)
    val videoSizeBytes: StateFlow<Long> = _videoSizeBytes.asStateFlow()

    data class VideoDetails(
        val title: String,
        val sizeString: String,
        val aspectRatioString: String,
        val durationString: String
    )

    private val _videoDetails = MutableStateFlow<VideoDetails?>(null)
    val videoDetails: StateFlow<VideoDetails?> = _videoDetails.asStateFlow()

    var exoPlayer: ExoPlayer? = null
        private set

    init {
        if (videoUri != null) {
            exoPlayer = ExoPlayer.Builder(application).build().apply {
                setMediaItem(MediaItem.fromUri(videoUri))
                prepare()
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false
            }

            viewModelScope.launch {
                val repository = videoRepository
                repository.getMetadata(videoUri)?.let { info ->
                    _durationMs.value = info.durationMs
                    _videoAr.value = info.aspectRatio
                    _videoWidth.value  = info.width
                    _videoHeight.value = info.height
                    _videoBitrate.value = info.bitrate
                    _videoSizeBytes.value = info.sizeBytes
                    _videoDetails.value = VideoDetails(
                        title = info.fileName,
                        sizeString = FormatUtils.formatFileSize(info.sizeBytes),
                        aspectRatioString = getAspectRatioString(info.width, info.height),
                        durationString = TimeUtils.formatDuration(info.durationMs)
                    )
                    // Auto-detect extracted frames directory
                    val extractedDir = FileNameUtils.resolveThumbnailDirectory(application, info.fileName)
                    if (extractedDir.exists() && extractedDir.isDirectory) {
                        loadThumbnails(extractedDir.absolutePath)
                    }
                }
            }

            // Sync ExoPlayer states
            exoPlayer?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        exoPlayer?.duration?.let { dur ->
                            if (dur > 0L) _durationMs.value = dur
                        }
                    } else if (state == Player.STATE_ENDED) {
                        pause()
                    }
                }
            })

            viewModelScope.launch {
                while (true) {
                    val player = exoPlayer
                    if (player != null && player.isPlaying) {
                        val currentPos = player.currentPosition
                        if (_isPreviewMode.value) {
                            val nextPos = checkAndSkipIntervals(currentPos)
                            if (nextPos != currentPos) {
                                player.seekTo(nextPos)
                                _currentPositionMs.value = nextPos
                            } else {
                                _currentPositionMs.value = currentPos
                            }
                        } else {
                            _currentPositionMs.value = currentPos
                        }
                    }
                    delay(16)
                }
            }
        }
    }

    private fun loadThumbnails(dirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = java.io.File(dirPath)
            if (!dir.exists() || !dir.isDirectory) return@launch

            val files = dir.listFiles { f ->
                f.name.startsWith("frame_") && (f.name.endsWith(".jpg") || f.name.endsWith(".png") || f.name.endsWith(".webp"))
            }?.sortedBy { f ->
                f.name.removePrefix("frame_").substringBefore(".").toIntOrNull() ?: 0
            } ?: return@launch

            val density = getApplication<Application>().resources.displayMetrics.density
            val targetHeightPx = (60 * density).toInt()
            val videoAr = _videoAr.value
            val targetWidthPx = (targetHeightPx * videoAr).toInt().coerceAtLeast(1)

            files.forEachIndexed { index, file ->
                val bitmap = try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    val srcWidth = options.outWidth
                    val srcHeight = options.outHeight

                    var sampleSize = 1
                    if (srcHeight > targetHeightPx) {
                        val halfHeight = srcHeight / 2
                        while (halfHeight / sampleSize >= targetHeightPx) {
                            sampleSize *= 2
                        }
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                    decoded?.let {
                        if (it.width != targetWidthPx || it.height != targetHeightPx) {
                            val scaled = Bitmap.createScaledBitmap(it, targetWidthPx, targetHeightPx, true)
                            if (scaled != it) it.recycle()
                            scaled
                        } else {
                            it
                        }
                    }
                } catch (e: Exception) { null }
                if (bitmap != null) {
                    _thumbBitmaps.update { it + (index to bitmap) }
                }
            }
        }
    }

    fun getThumbBitmap(secondIndex: Int): Bitmap? = _thumbBitmaps.value[secondIndex]

    fun play() {
        if (exoPlayer != null) {
            if (exoPlayer!!.currentPosition >= _durationMs.value) {
                exoPlayer!!.seekTo(0L)
            }
            exoPlayer!!.play()
        } else {
            if (_currentPositionMs.value >= _durationMs.value) {
                _currentPositionMs.value = 0L
            }
            _isPlaying.value = true
        }
    }

    fun pause() {
        if (exoPlayer != null) {
            exoPlayer!!.pause()
        } else {
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun updatePosition(ms: Long) {
        var targetPos = ms.coerceIn(0L, _durationMs.value)
        if (_isPreviewMode.value) {
            targetPos = checkAndSkipIntervals(targetPos)
        }
        _currentPositionMs.value = targetPos
        exoPlayer?.seekTo(targetPos)
    }

    fun setPreviewMode(enabled: Boolean) {
        _isPreviewMode.value = enabled
        if (enabled) {
            val nextPos = checkAndSkipIntervals(_currentPositionMs.value)
            if (nextPos != _currentPositionMs.value) {
                updatePosition(nextPos)
            }
        }
    }

    fun exportCuts(level: CompressionLevel = CompressionLevel.ORIGINAL) {
        val uri = videoUri ?: return
        val intervals = _markerIntervals.value
        if (intervals.isEmpty()) return

        val trimPairs = intervals.map { it.startMs to it.endMs }
        val keepRanges = RangeUtils.calculateKeepRanges(trimPairs, _durationMs.value)
        if (keepRanges.isEmpty()) {
            _exportState.value = ExportUiState.Error("Os cortes cobrem o vídeo inteiro — não sobrou nada para exportar.")
            return
        }

        _exportState.value = ExportUiState.Exporting(-1)
        viewModelScope.launch {
            if (level == CompressionLevel.ORIGINAL) {
                val pipeline = CopyPipeline(getApplication<Application>(), videoRepository)
                pipeline.trim(uri, keepRanges).collect { result ->
                    result.fold(
                        onSuccess = { file ->
                            saveAndFinishExport(file, uri)
                        },
                        onFailure = { e ->
                            _exportState.value = ExportUiState.Error(
                                (e as? ChopCutException)?.userMessage ?: e.message ?: "Falha ao recortar o vídeo"
                            )
                        }
                    )
                }
            } else {
                val pipeline = TransformerPipeline(getApplication<Application>(), videoRepository)
                pipeline.trim(uri, keepRanges, _videoAr.value, level).collect { progress ->
                    when (progress) {
                        is TrimProgress.InProgress -> {
                            _exportState.value = ExportUiState.Exporting(progress.percent)
                        }
                        is TrimProgress.Completed -> {
                            saveAndFinishExport(progress.file, uri)
                        }
                        is TrimProgress.Failed -> {
                            _exportState.value = ExportUiState.Error(
                                (progress.error as? ChopCutException)?.userMessage ?: progress.error.message ?: "Falha ao compactar o vídeo"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveAndFinishExport(file: File, uri: Uri) {
        viewModelScope.launch {
            try {
                val baseName = FileNameUtils.extractBaseNameFromUri(uri)
                val stamp = java.text.SimpleDateFormat("mmss", java.util.Locale.US)
                    .format(java.util.Date())
                videoRepository.saveToGallery(file, "${baseName}_chopcut_$stamp.mp4")
                val shareUri = FileProvider.getUriForFile(
                    getApplication<Application>(),
                    "com.chopcut.fileprovider",
                    file
                )
                _exportState.value = ExportUiState.Success(shareUri)
            } catch (e: Exception) {
                _exportState.value = ExportUiState.Error(e.message ?: "Falha ao salvar o vídeo")
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportUiState.Idle
    }

    fun checkAndSkipIntervals(positionMs: Long): Long {
        var current = positionMs
        var skipped = true
        while (skipped) {
            skipped = false
            for (interval in _markerIntervals.value) {
                if (current >= interval.startMs && current < interval.endMs) {
                    current = interval.endMs
                    skipped = true
                    break
                }
            }
        }
        return current
    }

    fun toggleMarker(currentPositionMs: Long) {
        val start = _activeMarkerStartMs.value
        if (start == null) {
            // Iniciar novo intervalo no playhead
            _activeMarkerStartMs.value = currentPositionMs
        } else {
            // Finalizar o intervalo ativo
            val end = currentPositionMs
            val minTime = Math.min(start, end)
            val maxTime = Math.max(start, end)

            val newInterval = MarkerInterval(
                id = 0,
                startMs = minTime,
                endMs = maxTime
            )

            _markerIntervals.value = mergeIntervals(_markerIntervals.value + newInterval)
            _activeMarkerStartMs.value = null
        }
    }

    fun deleteInterval(intervalId: Int) {
        _markerIntervals.value = _markerIntervals.value.filter { it.id != intervalId }
        _markerIntervals.value = reindexIntervals(_markerIntervals.value)
        if (_markerIntervals.value.isEmpty()) {
            setPreviewMode(false)
        }
    }

    private fun reindexIntervals(list: List<MarkerInterval>): List<MarkerInterval> {
        return list.mapIndexed { index, interval ->
            interval.copy(id = index + 1)
        }
    }

    private fun mergeIntervals(list: List<MarkerInterval>): List<MarkerInterval> {
        if (list.isEmpty()) return emptyList()

        val sorted = list.sortedWith(compareBy({ it.startMs }, { it.endMs }))
        val merged = mutableListOf<MarkerInterval>()
        var current = sorted[0]

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startMs <= current.endMs) {
                current = current.copy(endMs = Math.max(current.endMs, next.endMs))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        return reindexIntervals(merged)
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
    }

    class TimelineViewModelFactory(
        private val application: Application,
        private val videoUri: Uri?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TimelineViewModel::class.java)) {
                return TimelineViewModel(application, videoUri) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

@Composable
fun VideoPlayerV2(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    videoUri: Uri? = null,
    onNavigateBack: () -> Unit,
    viewModel: TimelineViewModel = viewModel(
        factory = TimelineViewModel.TimelineViewModelFactory(
            application = LocalContext.current.applicationContext as Application,
            videoUri = videoUri
        )
    )
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()

    val markerIntervals by viewModel.markerIntervals.collectAsStateWithLifecycle()
    val activeMarkerStartMs by viewModel.activeMarkerStartMs.collectAsStateWithLifecycle()

    val videoDetails by viewModel.videoDetails.collectAsStateWithLifecycle()

    val aspectRatio  by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val videoWidth   by viewModel.videoWidth.collectAsStateWithLifecycle()
    val videoHeight  by viewModel.videoHeight.collectAsStateWithLifecycle()
    val thumbBitmaps by viewModel.thumbBitmaps.collectAsStateWithLifecycle()

    val isPreviewMode by viewModel.isPreviewMode.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val videoBitrate by viewModel.videoBitrate.collectAsStateWithLifecycle()
    val videoSizeBytes by viewModel.videoSizeBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showExportBottomSheet by remember { mutableStateOf(false) }
    var selectedCompressionLevel by remember { mutableStateOf(CompressionLevel.ORIGINAL) }

    // Target position para suavização manual de scrubbing
    var targetPositionMs by remember { mutableStateOf(currentPositionMs.toFloat()) }

    // Sincronizar targetPositionMs durante auto-play
    LaunchedEffect(currentPositionMs, isPlaying) {
        if (isPlaying) {
            targetPositionMs = currentPositionMs.toFloat()
        }
    }

    // Se temos ExoPlayer e não está reproduzindo, atualizar a posição no player ao arrastar
    LaunchedEffect(targetPositionMs) {
        if (viewModel.exoPlayer != null && !isPlaying) {
            viewModel.updatePosition(targetPositionMs.toLong())
        }
    }

    // Loop de animação precisa de 60 FPS usando withFrameNanos (apenas quando não usamos ExoPlayer real)
    LaunchedEffect(isPlaying, isPreviewMode) {
        if (isPlaying && viewModel.exoPlayer == null) {
            var lastNanos = System.nanoTime()
            var accumulatedMs = currentPositionMs.toFloat()
            while (isPlaying) {
                withFrameNanos { nanos ->
                    val deltaNanos = nanos - lastNanos
                    val deltaMs = deltaNanos / 1_000_000f
                    lastNanos = nanos

                    accumulatedMs = (accumulatedMs + deltaMs).coerceAtMost(durationMs.toFloat())
                    if (isPreviewMode) {
                        val nextMs = viewModel.checkAndSkipIntervals(accumulatedMs.toLong())
                        if (nextMs != accumulatedMs.toLong()) {
                            accumulatedMs = nextMs.toFloat()
                        }
                    }
                    viewModel.updatePosition(accumulatedMs.toLong())

                    if (accumulatedMs >= durationMs) {
                        viewModel.pause()
                    }
                }
            }
        }
    }

    // Loop de amortecimento premium por decaimento exponencial ao pausar (apenas quando não usamos ExoPlayer real)
    LaunchedEffect(isPlaying) {
        if (!isPlaying && viewModel.exoPlayer == null) {
            var lastNanos = System.nanoTime()
            while (!isPlaying) {
                withFrameNanos { nanos ->
                    val deltaNanos = nanos - lastNanos
                    val deltaMs = deltaNanos / 1_000_000f
                    lastNanos = nanos

                    val current = viewModel.currentPositionMs.value.toFloat()
                    val target = targetPositionMs
                    val diff = target - current

                    if (Math.abs(diff) > 0.5f) {
                        val tau = 80f
                        val factor = (1f - Math.exp(-deltaMs.toDouble() / tau.toDouble())).toFloat()
                        val next = current + diff * factor
                        viewModel.updatePosition(next.toLong())
                    } else if (current != target) {
                        viewModel.updatePosition(target.toLong())
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Timeline Demo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (markerIntervals.isNotEmpty()) {
                        TextButton(
                            onClick = { showExportBottomSheet = true }
                        ) {
                            Text(
                                text = "CONFIRMAR",
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Player Container (holds real VideoPlayerV2/ExoPlayer or visual mock)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                val player = viewModel.exoPlayer
                if (player != null) {
                    VideoPlayerV2(
                        exoPlayer = player,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F3460), Color(0xFF16213E), Color(0xFF1A1A2E))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Simulação",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            // 1. Video Progress Bar (full width of player 0.95f, height 2dp, Cyan neon color)
            val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(Color(0xFF00E5FF))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Control Row: circular play/pause button (40dp) and metadata (white, left-aligned)
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Play/Pause button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Metadata elements distributed across the remaining width
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val details = videoDetails
                    val title = details?.title ?: "Vídeo Demo"
                    val size = details?.sizeString ?: "12.4 MB"
                    val aspect = details?.aspectRatioString ?: "16:9"
                    val duration = details?.durationString ?: "00:59"

                    Text(
                        text = title,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.5f, fill = false)
                    )
                    Text(
                        text = size,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = aspect,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = duration,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            // Contador de Milissegundos posicionado acima do Playhead
            Text(
                text = formatMs(currentPositionMs),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Altura dinâmica baseada nas dimensões reais do vídeo
            val (thumbWDp, thumbHDp) = ThumbnailConfig.TimelineThumbs.computeDp(videoWidth, videoHeight)
            val containerHeight = (24.dp + thumbHDp.dp + 10.dp).coerceAtLeast(80.dp)
            Timeline(
                targetPositionMs = targetPositionMs,
                onTargetPositionChanged = { targetPositionMs = it },
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                sensitivity = 1.60f,
                markerIntervals = markerIntervals,
                activeMarkerStartMs = activeMarkerStartMs,
                onDeleteInterval = { viewModel.deleteInterval(it) },
                thumbBitmaps = thumbBitmaps,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                isPreviewMode = isPreviewMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(containerHeight)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Modo Preview Toggle Switch Row
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isPreviewMode) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "MODO PREVIEW (PULAR CORTES)",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPreviewMode) Color.White else Color.White.copy(alpha = 0.5f)
                        )
                    )
                }

                Switch(
                    checked = isPreviewMode,
                    onCheckedChange = { viewModel.setPreviewMode(it) },
                    enabled = markerIntervals.isNotEmpty(),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFF00E5FF),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                        disabledCheckedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.3f),
                        disabledUncheckedTrackColor = Color.White.copy(alpha = 0.05f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Controles de Marcação na parte inferior (centralizado e limpo)
            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão de Marcação
                val isRecording = activeMarkerStartMs != null
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by if (isRecording) {
                    infiniteTransition.animateFloat(
                        initialValue = 0.08f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutLinearInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                } else {
                    remember { mutableStateOf(0.08f) }
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = if (isRecording) Color.Red.copy(alpha = pulseAlpha) else Color.White.copy(alpha = 0.08f),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (isRecording) Color.Red else Color.White.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .clickable { viewModel.toggleMarker(currentPositionMs) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isRecording) Color.Red else Color.White,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isRecording) "CORTE" else "MARCAR",
                            color = if (isRecording) Color.Red else Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    if (showExportBottomSheet) {
        val trimPairs = markerIntervals.map { it.startMs to it.endMs }
        val keepRanges = RangeUtils.calculateKeepRanges(trimPairs, durationMs)

        ModalBottomSheet(
            onDismissRequest = { showExportBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Opções de Exportação",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selecione o nível de compressão do vídeo recortado",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompressionLevel.values().forEach { level ->
                        val isViable = level.isViable(videoWidth, videoHeight, videoBitrate)
                        val estimatedSize = FormatUtils.estimateExportSize(
                            level = level,
                            keepRanges = keepRanges,
                            originalDurationUs = durationMs * 1000L,
                            originalSizeBytes = videoSizeBytes,
                            originalWidth = videoWidth,
                            originalHeight = videoHeight,
                            originalBitrateBps = videoBitrate
                        )

                        val sizeString = FormatUtils.formatFileSize(estimatedSize)
                        val targetResString = if (level != CompressionLevel.ORIGINAL) {
                            val targetH = Math.min(level.targetHeight, videoHeight)
                            val targetW = (videoWidth * targetH) / videoHeight
                            val evenW = (targetW / 2) * 2
                            val evenH = (targetH / 2) * 2
                            "Resolução: ${evenW}x${evenH} (~${level.targetBitrateBps / 1_000_000} Mbps)"
                        } else ""

                        CompressionOptionCard(
                            level = level,
                            isSelected = selectedCompressionLevel == level,
                            isViable = isViable,
                            estimatedSize = sizeString,
                            targetResString = targetResString,
                            onClick = { selectedCompressionLevel = level }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            showExportBottomSheet = false
                            viewModel.setPreviewMode(false)
                            viewModel.exportCuts(selectedCompressionLevel)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EXPORTAR VÍDEO",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = { showExportBottomSheet = false }) {
                    Text(
                        "Cancelar",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    when (val st = exportState) {
        is ExportUiState.Exporting -> {
            Dialog(onDismissRequest = { }) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = if (st.progress >= 0) Modifier.size(48.dp) else Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        val labelText = if (st.progress >= 0) {
                            "Processando (${st.progress}%)…"
                        } else {
                            "Recortando…"
                        }
                        Text(
                            text = labelText,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        is ExportUiState.Success -> {
            AlertDialog(
                onDismissRequest = { },
                title = {
                    Text(
                        text = "Vídeo recortado!",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = "Salvo na pasta ChopCut.",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "video/*"
                                putExtra(Intent.EXTRA_STREAM, st.shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Compartilhar vídeo"))
                        }
                    ) {
                        Text(
                            text = "Compartilhar",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.resetExportState()
                            if (videoUri != null) {
                                AppliedCutsRegistry.setHasCuts(videoUri, true)
                            }
                            onNavigateBack()
                        }
                    ) {
                        Text(
                            text = "Concluir",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                textContentColor = Color.White,
                titleContentColor = Color.White
            )
        }

        is ExportUiState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetExportState() },
                title = {
                    Text(
                        text = "Falha ao recortar",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = st.message,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.exportCuts() }) {
                        Text(
                            text = "Tentar novamente",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.resetExportState() }) {
                        Text(
                            text = "Fechar",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                textContentColor = Color.White,
                titleContentColor = Color.White
            )
        }

        ExportUiState.Idle -> { }
    }
}


@Composable
fun Timeline(
    targetPositionMs: Float,
    onTargetPositionChanged: (Float) -> Unit,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    sensitivity: Float,
    markerIntervals: List<MarkerInterval>,
    activeMarkerStartMs: Long?,
    onDeleteInterval: (Int) -> Unit,
    thumbBitmaps: Map<Int, Bitmap> = emptyMap(),
    videoWidth: Int = 1920,
    videoHeight: Int = 1080,
    isPreviewMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val thumbnailPaint = remember {
        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
        }
    }
    // Pré-alocações reutilizadas no draw scope (evita alocação por frame a 60Hz)
    val dstRect = remember { android.graphics.Rect() }
    val labelStyle = remember { TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
    val gridLine = remember { Color.White.copy(alpha = 0.15f) }
    val tickStrong = remember { Color.White.copy(alpha = 0.4f) }
    val tickWeak = remember { Color.White.copy(alpha = 0.2f) }
    val markerFill = remember { Color.Yellow.copy(alpha = 0.22f) }
    val activeFill = remember { Color.Yellow.copy(alpha = 0.15f) }
    val activeBorder = remember { Color.Yellow.copy(alpha = 0.7f) }

    val targetPositionState = rememberUpdatedState(targetPositionMs)
    val onTargetPositionChangedState = rememberUpdatedState(onTargetPositionChanged)
    val sensitivityState = rememberUpdatedState(sensitivity)

    val isRecording = activeMarkerStartMs != null
    val infiniteTransition = rememberInfiniteTransition(label = "playheadTransition")
    val playheadColor by if (isRecording) {
        infiniteTransition.animateColor(
            initialValue = Color(0xFF00E5FF),
            targetValue = Color(0xFFFFC107),
            animationSpec = infiniteRepeatable(
                animation = tween(250, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "playheadColor"
        )
    } else {
        remember { mutableStateOf(Color(0xFF00E5FF)) }
    }

    // Dimensões com fator de resolução + caps preservando AR exato
    val (thumbWDpF, thumbHDpF) = ThumbnailConfig.TimelineThumbs.computeDp(videoWidth, videoHeight)
    val thumbWidthPx  = with(density) { thumbWDpF.dp.toPx() }
    val thumbHeightPx = with(density) { thumbHDpF.dp.toPx() }
    val timelineTopPx = with(density) { 24.dp.toPx() }
    val tickHeightPx = with(density) { 6.dp.toPx() }
    val tickGapPx = with(density) { 4.dp.toPx() }

    val totalSeconds = (durationMs / 1000f).toInt().coerceAtLeast(1)
    // Labels "${i}s" são estáveis — medir 1x por totalSeconds, não por frame
    val tickLabels = remember(totalSeconds, labelStyle) {
        (0..totalSeconds).map { textMeasurer.measure(text = "${it}s", style = labelStyle) }
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val centerX = widthPx / 2f
        
        // Calculate vertical offset to center the 69dp visual content inside the 114dp touch container
        val contentHeightPx = timelineTopPx + thumbHeightPx
        val verticalOffsetPx = (heightPx - contentHeightPx) / 2f
        
        // Total timeline length in pixels based on video duration
        val totalTimelineWidthPx = totalSeconds * thumbWidthPx
        val scrollOffsetPx = (currentPositionMs.toFloat() / durationMs.toFloat()) * totalTimelineWidthPx

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isPlaying, durationMs) {
                    if (!isPlaying) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val msPerPx = durationMs.toFloat() / totalTimelineWidthPx
                                val deltaMs = -dragAmount * msPerPx
                                val newTarget = (targetPositionState.value + deltaMs * sensitivityState.value)
                                    .coerceIn(0f, durationMs.toFloat())
                                onTargetPositionChangedState.value(newTarget)
                            }
                        )
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val canvasCenterX = width / 2f
            
            // Calculate vertical offset to center the 69dp visual content inside the 114dp touch container
            val canvasContentHeightPx = timelineTopPx + thumbHeightPx
            val canvasVerticalOffsetPx = (height - canvasContentHeightPx) / 2f
            
            // Total timeline length in pixels based on video duration
            val canvasTotalTimelineWidthPx = totalSeconds * thumbWidthPx
            val canvasScrollOffsetPx = (currentPositionMs.toFloat() / durationMs.toFloat()) * canvasTotalTimelineWidthPx
            
            // 1. Draw top line for ticks reference (with gap offset)
            drawLine(
                color = gridLine,
                start = Offset(0f, canvasVerticalOffsetPx + timelineTopPx - tickGapPx),
                end = Offset(width, canvasVerticalOffsetPx + timelineTopPx - tickGapPx),
                strokeWidth = 1f
            )
            
            // 2. Draw Ticks & Labels & Thumbnails
            for (i in 0..totalSeconds) {
                val thumbLeft = canvasCenterX + (i * thumbWidthPx) - canvasScrollOffsetPx
                val thumbRight = thumbLeft + thumbWidthPx
                
                // Draw thumbnail only if within duration bounds
                if (i < totalSeconds && thumbRight >= -50f && thumbLeft <= width + 50f) {
                    val bitmap = thumbBitmaps[i]
                    if (bitmap != null && !bitmap.isRecycled) {
                        // Draw real bitmap thumbnail
                        dstRect.set(
                            thumbLeft.toInt(),
                            (canvasVerticalOffsetPx + timelineTopPx).toInt(),
                            thumbRight.toInt(),
                            (canvasVerticalOffsetPx + timelineTopPx + thumbHeightPx).toInt()
                        )
                        // Paint e Rect prealocados fora do escopo do Canvas para evitar jank por GC
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawBitmap(bitmap, null, dstRect, thumbnailPaint)
                        }
                    } else {
                        // Fallback gradient placeholder
                        val colorStart = when {
                            i % 5 == 0 -> Color(0xFFE94560)
                            i % 2 == 0 -> Color(0xFF1A1A2E)
                            else -> Color(0xFF0F3460)
                        }
                        val colorEnd = when {
                            i % 5 == 0 -> Color(0xFF0F3460)
                            i % 2 == 0 -> Color(0xFF16213E)
                            else -> Color(0xFF1A1A2E)
                        }
                        val brush = Brush.linearGradient(
                            colors = listOf(colorStart, colorEnd),
                            start = Offset(thumbLeft, canvasVerticalOffsetPx + timelineTopPx),
                            end = Offset(thumbRight, canvasVerticalOffsetPx + timelineTopPx + thumbHeightPx)
                        )
                        drawRect(
                            brush = brush,
                            topLeft = Offset(thumbLeft, canvasVerticalOffsetPx + timelineTopPx),
                            size = Size(thumbWidthPx, thumbHeightPx)
                        )
                    }
                    
                    // Draw thin thumbnail border
                    drawRect(
                        color = gridLine,
                        topLeft = Offset(thumbLeft, canvasVerticalOffsetPx + timelineTopPx),
                        size = Size(thumbWidthPx, thumbHeightPx),
                        style = Stroke(width = with(density) { 0.5.dp.toPx() })
                    )
                }
                
                // Draw tick mark and label at each second boundary
                val tickX = canvasCenterX + (i * thumbWidthPx) - canvasScrollOffsetPx
                if (tickX >= -10f && tickX <= width + 10f) {
                    // Standard second tick
                    drawLine(
                        color = tickStrong,
                        start = Offset(tickX, canvasVerticalOffsetPx + timelineTopPx - tickGapPx - tickHeightPx),
                        end = Offset(tickX, canvasVerticalOffsetPx + timelineTopPx - tickGapPx),
                        strokeWidth = with(density) { 1.dp.toPx() }
                    )
                    
                    // Sub-ticks (half seconds)
                    val halfTickX = tickX + (thumbWidthPx / 2f)
                    if (halfTickX >= -10f && halfTickX <= width + 10f && i < totalSeconds) {
                        drawLine(
                            color = tickWeak,
                            start = Offset(halfTickX, canvasVerticalOffsetPx + timelineTopPx - tickGapPx - (tickHeightPx / 2f)),
                            end = Offset(halfTickX, canvasVerticalOffsetPx + timelineTopPx - tickGapPx),
                            strokeWidth = with(density) { 0.5.dp.toPx() }
                        )
                    }
                    
                    // Label pré-medido (tickLabels) — sem measure()/TextStyle/Color.copy por frame
                    val tickLabel = tickLabels[i]
                    drawText(
                        textLayoutResult = tickLabel,
                        topLeft = Offset(tickX - tickLabel.size.width / 2f, canvasVerticalOffsetPx + 2.dp.toPx())
                    )
                }
            }
            
            // 3. Draw Yellow Marker Intervals (Transparent overlay + solid borders)
            markerIntervals.forEach { interval ->
                val startX = canvasCenterX + (interval.startMs.toFloat() / durationMs.toFloat()) * canvasTotalTimelineWidthPx - canvasScrollOffsetPx
                val endX = canvasCenterX + (interval.endMs.toFloat() / durationMs.toFloat()) * canvasTotalTimelineWidthPx - canvasScrollOffsetPx
                val drawWidth = endX - startX
                
                if (endX >= 0f && startX <= width) {
                    // Fill semi-transparent yellow
                    drawRect(
                        color = markerFill,
                        topLeft = Offset(startX, canvasVerticalOffsetPx + timelineTopPx),
                        size = Size(drawWidth, thumbHeightPx)
                    )
                    
                    // Left & Right solid border
                    drawLine(
                        color = Color.Yellow,
                        start = Offset(startX, canvasVerticalOffsetPx + timelineTopPx),
                        end = Offset(startX, canvasVerticalOffsetPx + timelineTopPx + thumbHeightPx),
                        strokeWidth = with(density) { 1.5.dp.toPx() }
                    )
                    drawLine(
                        color = Color.Yellow,
                        start = Offset(endX, canvasVerticalOffsetPx + timelineTopPx),
                        end = Offset(endX, canvasVerticalOffsetPx + timelineTopPx + thumbHeightPx),
                        strokeWidth = with(density) { 1.5.dp.toPx() }
                    )
                }
            }
     
            // 4. Draw In-Progress Active Marker Selection (Real-time expanding/contracting visual feedback)
            if (activeMarkerStartMs != null) {
                val minMs = Math.min(activeMarkerStartMs, currentPositionMs)
                val maxMs = Math.max(activeMarkerStartMs, currentPositionMs)
                
                val startX = canvasCenterX + (minMs.toFloat() / durationMs.toFloat()) * canvasTotalTimelineWidthPx - canvasScrollOffsetPx
                val endX = canvasCenterX + (maxMs.toFloat() / durationMs.toFloat()) * canvasTotalTimelineWidthPx - canvasScrollOffsetPx
                val drawWidth = endX - startX
                
                if (endX >= 0f && startX <= width) {
                    // Fill semi-transparent active yellow
                    drawRect(
                        color = activeFill,
                        topLeft = Offset(startX, canvasVerticalOffsetPx + timelineTopPx),
                        size = Size(drawWidth, thumbHeightPx)
                    )
                    
                    // Active solid borders
                    drawLine(
                        color = activeBorder,
                        start = Offset(startX, canvasVerticalOffsetPx + timelineTopPx),
                        end = Offset(startX, canvasVerticalOffsetPx + timelineTopPx + thumbHeightPx),
                        strokeWidth = with(density) { 1.5.dp.toPx() }
                    )
                    drawLine(
                        color = activeBorder,
                        start = Offset(endX, canvasVerticalOffsetPx + timelineTopPx),
                        end = Offset(endX, canvasVerticalOffsetPx + timelineTopPx + thumbHeightPx),
                        strokeWidth = with(density) { 1.5.dp.toPx() }
                    )
                }
            }
     
        }
 
        // 2. Playhead Canvas (Overlay) - Redesenhado independentemente do Canvas principal para performance premium
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height
            val canvasCenterX = width / 2f
            
            val canvasContentHeightPx = timelineTopPx + thumbHeightPx
            val canvasVerticalOffsetPx = (height - canvasContentHeightPx) / 2f
 
            val playheadWidth = if (isRecording) 3.5.dp else 2.5.dp
            val capRadius = if (isRecording) 7.dp else 5.dp
     
            if (isRecording) {
                drawCircle(
                    color = playheadColor.copy(alpha = 0.25f),
                    radius = with(density) { (capRadius + 4.dp).toPx() },
                    center = Offset(canvasCenterX, canvasVerticalOffsetPx + timelineTopPx - tickGapPx - tickHeightPx)
                )
            }
     
            drawLine(
                color = playheadColor,
                start = Offset(canvasCenterX, canvasVerticalOffsetPx + timelineTopPx - tickGapPx - tickHeightPx),
                end = Offset(canvasCenterX, canvasVerticalOffsetPx + timelineTopPx + thumbHeightPx + 4.dp.toPx()),
                strokeWidth = with(density) { playheadWidth.toPx() }
            )
            
            drawCircle(
                color = playheadColor,
                radius = with(density) { capRadius.toPx() },
                center = Offset(canvasCenterX, canvasVerticalOffsetPx + timelineTopPx - tickGapPx - tickHeightPx)
            )
        }

        // Overlay Interactive Delete Buttons over the marked intervals
        val buttonSizeDp = 24.dp
        val buttonSizePx = with(density) { buttonSizeDp.toPx() }
        val centerYPx = verticalOffsetPx + timelineTopPx + thumbHeightPx / 2f

        if (!isPreviewMode) {
            markerIntervals.forEach { interval ->
                val startX = centerX + (interval.startMs.toFloat() / durationMs.toFloat()) * totalTimelineWidthPx - scrollOffsetPx
                val endX = centerX + (interval.endMs.toFloat() / durationMs.toFloat()) * totalTimelineWidthPx - scrollOffsetPx
                val drawWidth = endX - startX
                val centerXPx = startX + drawWidth / 2f

                // Only display delete button if:
                // 1. Center of the segment is on screen
                // 2. Segment width is at least the button size plus a tiny margin
                val isButtonVisible = centerXPx >= buttonSizePx / 2f && centerXPx <= widthPx - buttonSizePx / 2f
                val isSegmentWideEnough = drawWidth >= buttonSizePx

                if (isButtonVisible && isSegmentWideEnough) {
                    val xOffsetDp = with(density) { (centerXPx - buttonSizePx / 2f).toDp() }
                    val yOffsetDp = with(density) { (centerYPx - buttonSizePx / 2f).toDp() }

                    Box(
                        modifier = Modifier
                            .offset(x = xOffsetDp, y = yOffsetDp)
                            .size(buttonSizeDp)
                            .clip(CircleShape)
                            .background(Color.Red)
                            .clickable { onDeleteInterval(interval.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Excluir marcação",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompressionOptionCard(
    level: CompressionLevel,
    isSelected: Boolean,
    isViable: Boolean,
    estimatedSize: String,
    targetResString: String,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isViable -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .clickable(enabled = isViable) { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = level.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = level.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                if (!isViable) {
                    Text(
                        text = "Indisponível",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else if (isSelected) {
                    Text(
                        text = "✓",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isViable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tamanho: $estimatedSize",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    if (targetResString.isNotEmpty()) {
                        Text(
                            text = targetResString,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val milliseconds = ms % 1000
    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, milliseconds)
}

private fun getAspectRatioString(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "16:9"
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val g = gcd(width, height)
    val sw = width / g
    val sh = height / g
    return "$sw:$sh"
}
