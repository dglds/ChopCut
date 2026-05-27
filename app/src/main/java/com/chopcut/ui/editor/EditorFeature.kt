package com.chopcut

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


// --- Merged from EditorScreen.kt ---



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    videoUri: Uri,
    preloadViewModel: PreloadViewModel,
    thumbnailViewModel: ThumbnailViewModel,
    audioViewModel: AudioViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
 
    // Observar PreloadViewModel com lifecycle awareness
    val preloadUiState by preloadViewModel.uiState.collectAsStateWithLifecycle()

    // Observar ThumbnailViewModel com lifecycle awareness
    val thumbnailStrips by thumbnailViewModel.strips.collectAsStateWithLifecycle()
    val thumbnailProgress by thumbnailViewModel.thumbnailProgress.collectAsStateWithLifecycle()

    // Observar AudioViewModel com lifecycle awareness
    val audioAmplitudes by audioViewModel.amplitudes.collectAsStateWithLifecycle()
    // Criar EditorViewModel
    val viewModel: EditorViewModel = viewModel(
        factory = EditorViewModel.EditorViewModelFactory(videoUri)
    )
    val state by viewModel.state.collectAsState()

    val smoothPositionMs = remember { mutableStateOf(state.currentPosition.toFloat()) }
    LaunchedEffect(state.currentPosition) {
        if (state.currentPosition.toFloat() != smoothPositionMs.value) {
            smoothPositionMs.value = state.currentPosition.toFloat()
        }
    }

    var saveDialogState by remember { mutableStateOf(SaveDialogState()) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Sincronizar dados das ViewModels para EditorViewModel
    LaunchedEffect(audioAmplitudes) {
        timber.log.Timber.d("EditorScreen: LaunchedEffect audioAmplitudes - size=${audioAmplitudes.size}")
        if (audioAmplitudes.isNotEmpty()) {
            viewModel.updateAudioAmplitudes(audioAmplitudes)
            timber.log.Timber.d("EditorScreen: updated EditorViewModel with ${audioAmplitudes.size} amplitudes")
        }
    }

    // Estados de controle do overlay
    var showLoadingOverlay by remember { mutableStateOf(false) }
    var elapsedTimeMs by remember { mutableStateOf(0L) }
    var isReadyToHide by remember { mutableStateOf(false) }

    // Verificar se dados já estão completos (cache hit) - simplificado
    val isDataAlreadyCached = remember(thumbnailStrips) {
        val requiredStrips = 6
        val hasThumbnails = thumbnailStrips.size >= requiredStrips
        hasThumbnails
    }

    // Calcular tempo mínimo de loading dinamicamente (5% da duração do vídeo)
    val minLoadingDurationMs = remember(state.videoDurationMs) {
        if (state.videoDurationMs > 0) {
            val calculatedMin = (state.videoDurationMs * LoadingConstants.MIN_LOADING_PERCENTAGE).toLong()
            val clampedMin = calculatedMin.coerceIn(500L, LoadingConstants.MAX_LOADING_DURATION_MS)
            clampedMin
        } else {
            2_000L
        }
    }

    // Se já estiver no cache, esconder o overlay imediatamente
    LaunchedEffect(isDataAlreadyCached) {
        if (isDataAlreadyCached) {
            showLoadingOverlay = false
        }
    }

    // Monitoramento REATIVO para esconder overlay (substitui while loop)
    // OTIMIZAÇÃO: Usa snapshotFlow para reagir apenas a mudanças reais, eliminando polling
    LaunchedEffect(showLoadingOverlay, isDataAlreadyCached) {
        if (!showLoadingOverlay || isDataAlreadyCached) {
            return@LaunchedEffect
        }

        val startTime = System.currentTimeMillis()

        // Timer para atualizar elapsedTimeMs periodicamente
        scope.launch {
            while (showLoadingOverlay) {
                elapsedTimeMs = System.currentTimeMillis() - startTime
                delay(100) // Apenas para atualizar o timer visual
            }
        }

        // Observação REATIVA de mudanças nos dados (substitui while loop)
        snapshotFlow {
            val currentElapsed = System.currentTimeMillis() - startTime
            val minTimeReached = currentElapsed >= minLoadingDurationMs
            val maxTimeReached = currentElapsed >= LoadingConstants.MAX_LOADING_DURATION_MS
            val hasSufficientThumbnails = thumbnailStrips.size >= 6
            val hasAudio = audioAmplitudes.isNotEmpty()

            val shouldHide = shouldHideLoadingOverlay(
                state = preloadUiState,
                minTimeReached = minTimeReached,
                maxTimeReached = maxTimeReached,
                hasSufficientThumbnails = hasSufficientThumbnails,
                hasAudio = true // Ignorar áudio temporariamente conforme solicitado
            )

            Triple(shouldHide, currentElapsed, hasSufficientThumbnails)
        }
            .distinctUntilChanged()
            .collect { (shouldHide, currentElapsed, hasSufficientThumbnails) ->
                if (shouldHide) {
                    val minTimeReached = currentElapsed >= minLoadingDurationMs
                    val maxTimeReached = currentElapsed >= LoadingConstants.MAX_LOADING_DURATION_MS
                    val hasAudio = audioAmplitudes.isNotEmpty()

                    val reason = getHideReason(
                        state = preloadUiState,
                        maxTimeReached = maxTimeReached,
                        minTimeReached = minTimeReached,
                        hasSufficientThumbnails = hasSufficientThumbnails,
                        elapsedTimeMs = currentElapsed,
                        thumbnailProgress = thumbnailProgress
                    )

                    isReadyToHide = true
                    delay(LoadingConstants.CROSS_FADE_START_DELAY_MS)
                    showLoadingOverlay = false
                }
            }
    }

    // Cancelar preload ao pressionar voltar durante loading
    BackHandler(enabled = showLoadingOverlay) {
        preloadViewModel.cancelPreload()
        showLoadingOverlay = false
        onNavigateBack()
    }

    // Se estiver usando uma ferramenta, o botão voltar cancela a ferramenta em vez de sair do editor
    BackHandler(enabled = !showLoadingOverlay) {
        if (state.activeTool != EditorTool.NONE) {
            viewModel.setActiveTool(EditorTool.NONE)
        } else {
            onNavigateBack()
        }
    }

    val videoRepository = remember { VideoRepository(context) }
    val transformerPipeline = remember { TransformerPipeline(context, videoRepository) }
    
    // Carregar waveform de áudio via AudioViewModel (Activity-scoped)
    LaunchedEffect(videoUri) {
        if (videoUri != Uri.EMPTY) {
            audioViewModel.loadWaveform(videoUri)
        }
    }

    when {
        videoUri == Uri.EMPTY -> {
            ErrorState(
                title = "Nenhum vídeo selecionado",
                message = "Selecione um vídeo para começar a editar",
                modifier = Modifier.fillMaxSize(),
                actionLabel = "Voltar",
                onAction = onNavigateBack
            )
        }
        else -> {
            // Box wrapper para permitir overlay
            Box(modifier = Modifier.fillMaxSize()) {
                // Animação de entrada suave para EditorScreen
                AnimatedVisibility(
                    visible = !showLoadingOverlay,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = LoadingConstants.TRIM_FADE_IN_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + scaleIn(
                        initialScale = LoadingConstants.TRIM_SCALE_IN_START,
                        animationSpec = tween(
                            durationMillis = LoadingConstants.TRIM_FADE_IN_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ),
                    exit = fadeOut(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = LinearEasing
                        )
                    )
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Editor", fontWeight = FontWeight.Bold) },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Black,
                                    titleContentColor = Color.White,
                                    navigationIconContentColor = Color.White
                                ),
                                navigationIcon = {
                                    IconButton(onClick = onNavigateBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            val ranges = state.trimPosition.completeRanges
                                            if (ranges.isEmpty()) {
                                                Toast.makeText(context, "Adicione pelo menos um corte", Toast.LENGTH_SHORT).show()
                                                return@IconButton
                                            }
                                            showSaveDialog = true
                                        },
                                        enabled = !saveDialogState.isSaving
                                    ) {
                                        Icon(
                                            if (saveDialogState.isSaving) Icons.Default.Check else Icons.Default.Save,
                                            contentDescription = "Salvar",
                                            tint = if (saveDialogState.isSaving) Color(0xFF00E5FF) else Color.White
                                        )
                                    }
                                }
                            )
                        }
                    ) { paddingValues ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0A0A0A)) // Fundo premium
                                .padding(paddingValues)
                        ) {
                            // Area do Video Player
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.3f), // Dá mais peso ao player
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                VideoFileInfo(
                                    fileInfo = FormatUtils.getFileInfo(context, videoUri, state.videoDurationMs)
                                )

                                VideoPreview(
                                    exoPlayer = state.exoPlayer ?: return@Column,
                                    isPlaying = state.isPlaying,
                                    isInsideRange = state.isInsideRange,
                                    playerError = state.playerError,
                                    isSecurityError = state.isSecurityError,
                                    currentTimeMs = state.currentPosition,
                                    onRequestNewMedia = { },
                                    onRetry = { viewModel.retryPlayer() },
                                    onTogglePlayPause = {
                                        if (state.isPlaying) viewModel.pause() else viewModel.play()
                                    },
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                )

                                // Passive Seekbar
                                val progress = if (state.videoDurationMs > 0) state.currentPosition.toFloat() / state.videoDurationMs.toFloat() else 0f
                                SeekbarProgress(progress = progress, modifier = Modifier.padding(vertical = 8.dp))
                            }

                            Spacer(Modifier.height(12.dp))

                            // Timeline Area
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.3f))
                                    .padding(vertical = 12.dp)
                            ) {
                                if (state.videoDurationMs > 0) {
                                    VideoTimeline(
                                        videoUri = videoUri,
                                        durationMs = state.videoDurationMs,
                                        currentPositionMs = state.currentPosition,
                                        isPlaying = state.isPlaying,
                                        onSeek = { viewModel.setCurrentPosition(it) },
                                        onScrubStart = { viewModel.startScrubbing() },
                                        onScrubStop = { finalPos -> viewModel.stopScrubbing(finalPos) },
                                        trimRanges = state.trimPosition.completeRanges,
                                        audioAmplitudes = state.audioWaveformsAmplitudes,
                                        showWaveform = true,
                                        videoWidth = state.videoWidth,
                                        videoHeight = state.videoHeight,
                                        modifier = Modifier.fillMaxWidth().height(120.dp)
                                    )
                                }
                            }

                            AnimatedContent(
                                targetState = state.activeTool,
                                label = "ToolSwapAnimation",
                                modifier = Modifier.padding(bottom = 24.dp)
                            ) { tool ->
                                when (tool) {
                                    EditorTool.NONE -> {
                                        MainToolBar(
                                            onToolSelected = { viewModel.setActiveTool(it) }
                                        )
                                    }
                                    EditorTool.TRIM -> {
                                        val isInsideRange = state.trimPosition.isPositionInRange(state.currentPosition)
                                        TrimToolPanel(
                                            isDraftMode = state.trimPosition.isDraftMode,
                                            isInsideRange = isInsideRange,
                                            onAddPosition = { viewModel.addPosition(state.currentPosition) },
                                            onDelete = { viewModel.removeRangeAt(state.currentPosition) },
                                            onClose = { viewModel.applyToolChangesAndClose() }
                                        )
                                    }
                                    EditorTool.FORMAT -> {
                                        FormatToolPanel(
                                            currentRatio = state.aspectRatio,
                                            onRatioSelected = { viewModel.setAspectRatio(it) },
                                            onClose = { viewModel.applyToolChangesAndClose() }
                                        )
                                    }
                                    EditorTool.ADD_MEDIA -> {
                                        ToolPlaceholder("Galeria abrirá aqui...", onClose = { viewModel.applyToolChangesAndClose() })
                                    }
                                    EditorTool.CROP -> {
                                        ToolPlaceholder("Ferramenta de Recorte de Área", onClose = { viewModel.applyToolChangesAndClose() })
                                    }
                                    EditorTool.COMPRESS -> {
                                        CompressToolPanel(
                                            currentLevel = state.compressionLevel,
                                            onLevelSelected = { viewModel.setCompressionLevel(it) },
                                            onClose = { viewModel.applyToolChangesAndClose() }
                                        )
                                    }
                                    EditorTool.AUDIO -> {
                                        ToolPlaceholder("Ajustes de Trilha Sonora", onClose = { viewModel.applyToolChangesAndClose() })
                                    }
                                }
                            }
                        }
                    }
                } // Fecha AnimatedVisibility

                // LoadingOverlay renderizado quando visível
                if (showLoadingOverlay) {
                    LoadingOverlay(
                        progress = when (val state = preloadUiState) {
                            is PreloadUiState.Loading -> state.progress
                            else -> PreloadProgress(stage = PreloadStage.Starting)
                        },
                        elapsedTimeMs = elapsedTimeMs,
                        isReadyToHide = isReadyToHide
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        TrimSaveDialog(
            state = saveDialogState,
            onDismiss = {
                if (saveDialogState.canDismiss) {
                    showSaveDialog = false
                    saveDialogState = SaveDialogState()
                }
            },
            onSave = {
                scope.launch(Dispatchers.IO) {
                    saveDialogState = saveDialogState.copy(isSaving = true, progress = 0)
                    try {
                        val trimRanges = state.trimPosition.completeRanges.sortedBy { it.first }


                        val rangesToSave = RangeUtils.calculateKeepRanges(trimRanges, state.videoDurationMs)


                        if (rangesToSave.isEmpty()) {
                            throw Exception("No ranges to save - video is empty")
                        }

                        transformerPipeline.trim(
                            uri = videoUri, 
                            ranges = rangesToSave, 
                            aspectRatio = state.aspectRatio,
                            compressionLevel = state.compressionLevel
                        )
                            .collect { progress ->
                                when (progress) {
                                    is TrimProgress.InProgress -> {
                                        saveDialogState = saveDialogState.copy(progress = progress.percent)
                                    }
                                    is TrimProgress.Completed -> {
                                        val trimmedFile = progress.file

                                        val originalFileName = FileNameUtils.extractBaseNameFromUri(videoUri)
                                        val fileName = FileNameUtils.generateTimestampedFileName(originalFileName)

                                        videoRepository.saveToGallery(trimmedFile, fileName)
                                        trimmedFile.delete()

                                        saveDialogState = saveDialogState.copy(isCompleted = true, isSaving = false)
                                    }
                                    is TrimProgress.Failed -> {
                                        saveDialogState = saveDialogState.copy(
                                            error = progress.error.message ?: "Erro desconhecido",
                                            isSaving = false
                                        )
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        saveDialogState = saveDialogState.copy(
                            error = e.message ?: "Erro desconhecido",
                            isSaving = false
                        )
                    }
                }
            },
            onNavigateBack = onNavigateBack
        )
    }
}

@Composable
private fun ToolPlaceholder(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Check, contentDescription = "OK", tint = Color(0xFF00E5FF))
        }
    }
}

// Funções auxiliares para lógica de loading

private fun shouldHideLoadingOverlay(
    state: PreloadUiState,
    minTimeReached: Boolean,
    maxTimeReached: Boolean,
    hasSufficientThumbnails: Boolean,
    hasAudio: Boolean
): Boolean {
    return when (state) {
        is PreloadUiState.Ready -> {
            true
        }
        is PreloadUiState.Loading -> {
            // Sempre esconder no timeout máximo
            maxTimeReached || (minTimeReached && hasSufficientThumbnails && hasAudio)
        }
        is PreloadUiState.Error, is PreloadUiState.Cancelled -> {
            true
        }
        is PreloadUiState.Idle -> {
            // Fallback: se está em Idle por mais de 2 segundos, esconder para não ficar preso
            true
        }
    }
}

private fun getHideReason(
    state: PreloadUiState,
    maxTimeReached: Boolean,
    minTimeReached: Boolean,
    hasSufficientThumbnails: Boolean,
    elapsedTimeMs: Long,
    thumbnailProgress: Float
): String {
    return when {
        state is PreloadUiState.Error -> "Error state"
        state is PreloadUiState.Cancelled -> "Cancelled by user"
        state is PreloadUiState.Idle -> "Idle state (fallback)"
        maxTimeReached -> "Max timeout (${LoadingConstants.MAX_LOADING_DURATION_MS / 1000}s)"
        state is PreloadUiState.Ready -> "Ready (${elapsedTimeMs / 1000}s, ${thumbnailProgress.toInt()}% thumbnails)"
        minTimeReached && hasSufficientThumbnails -> "Min time reached + sufficient data"
        else -> "Unknown reason"
    }
}

// --- Merged from EditorViewModel.kt ---


/**
 * ViewModel para EditorScreen.
 *
 * Responsabilidades:
 * - Gerenciar estado do editor de trim (posições, duração, trim ranges)
 * - Carregar waveform de áudio
 * - Gerenciar posição atual do playhead
 * - Gerenciar Player de vídeo (ExoPlayer)
 *
 * NOTA: O pré-carregamento de thumbnails e áudio é gerenciado
 * pela PreloadViewModel (Activity-scoped), não por esta ViewModel.
 */
class EditorViewModel(
    application: Application,
    private val videoUri: Uri?,
    private val initialAudioAmplitudes: FloatArray? = null,
    private val initialPreloadedStrips: Map<Int, Bitmap>? = null
) : AndroidViewModel(application) {

    class EditorViewModelFactory(
        private val videoUri: Uri?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
                @Suppress("DEPRECATION")
                val app = modelClass.classLoader?.let {
                    try {
                        java.lang.Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication")
                            .invoke(null) as? Application
                    } catch (e: Exception) {
                        null
                    }
                }

                if (app != null) {
                    return EditorViewModel(
                        application = app,
                        videoUri = videoUri,
                        initialAudioAmplitudes = null,
                        initialPreloadedStrips = null
                    ) as T
                }
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var waveformQuality: WaveformQuality = WaveformQuality.Medium
    private val videoRepository = VideoRepository(application)

    private var playerManager: PlayerManager? = null

    init {
        // Instantiate PlayerManager
        if (videoUri != null) {
            playerManager = PlayerManager(
                context = application,
                videoUri = videoUri,
                onDurationReady = { duration ->
                    _state.update { it.copy(videoDurationMs = duration) }
                }
            )

            _state.update { it.copy(exoPlayer = playerManager?.exoPlayer) }

            // Obter dimensões do vídeo para o Timeline
            viewModelScope.launch {
                videoRepository.getMetadata(videoUri)?.let { info ->
                    _state.update { it.copy(videoWidth = info.width, videoHeight = info.height) }
                }
            }

            // Observe player states
            viewModelScope.launch {
                playerManager?.isPlaying?.collectLatest { isPlaying ->
                    _state.update { it.copy(isPlaying = isPlaying) }
                }
            }
            viewModelScope.launch {
                playerManager?.playerError?.collectLatest { error ->
                    _state.update { it.copy(playerError = error) }
                }
            }
            viewModelScope.launch {
                playerManager?.isSecurityError?.collectLatest { isSecurityError ->
                    _state.update { it.copy(isSecurityError = isSecurityError) }
                }
            }
            viewModelScope.launch {
                playerManager?.currentPositionFlow?.collectLatest { position: Long ->
                    if (!_state.value.isScrubbing) {
                        _state.update { it.copy(currentPosition = position) }
                    }
                }
            }
        }
    }

    fun setWaveformQuality(quality: WaveformQuality) {
        waveformQuality = quality
    }

    fun setActiveTool(tool: EditorTool) {
        _state.update { it.copy(activeTool = tool) }
    }

    fun applyToolChangesAndClose() {
        _state.update { it.copy(activeTool = EditorTool.NONE) }
    }

    fun setAspectRatio(ratio: Float?) {
        _state.update { it.copy(aspectRatio = ratio) }
    }

    fun setCompressionLevel(level: CompressionLevel) {
        _state.update { it.copy(compressionLevel = level) }
    }

    /**
     * Atualiza as amplitudes de áudio.
     * Usado para sincronizar dados do AudioViewModel.
     */
    fun updateAudioAmplitudes(amplitudes: FloatArray) {
        Timber.d("EditorViewModel: updateAudioAmplitudes called with ${amplitudes.size} amplitudes")
        _state.update { it.copy(
            audioWaveformsAmplitudes = amplitudes
        ) }
        Timber.d("EditorViewModel: state updated, audioWaveformsAmplitudes size = ${_state.value.audioWaveformsAmplitudes.size}")
    }

    fun addPosition(pos: Long) {
        val current = _state.value.trimPosition
        if (pos in current.positions) {
            return
        }
        _state.update { it.copy(trimPosition = current.withPosition(pos)) }
    }

    fun setCurrentPosition(pos: Long) {
        _state.update { it.copy(currentPosition = pos) }
        if (!_state.value.isScrubbing) {
            playerManager?.seekTo(pos)
        }
    }

    fun startScrubbing() {
        _state.update { it.copy(isScrubbing = true) }
    }

    fun stopScrubbing(finalPos: Long) {
        playerManager?.seekTo(finalPos)
        _state.update { it.copy(isScrubbing = false, currentPosition = finalPos) }
    }

    fun setVideoDuration(duration: Long) {
        _state.update { it.copy(videoDurationMs = duration) }
    }

    fun setWaveformData(data: WaveformData) {
        _state.update { it.copy(waveformData = data) }
    }

    fun updateRange(rangeIndex: Int, newStartMs: Long, newEndMs: Long) {
        val current = _state.value.trimPosition
        _state.update { it.copy(trimPosition = current.updateRangeAt(rangeIndex, newStartMs, newEndMs)) }
    }

    fun removeRangeAt(pos: Long) {
        val current = _state.value.trimPosition
        if (current.isDraftMode) {
            val newPositions = current.positions.dropLast(1)
            _state.update { it.copy(trimPosition = current.copy(positions = newPositions)) }
        } else {
            val newTrim = current.removeRangeAt(pos)
            _state.update { it.copy(trimPosition = newTrim) }
        }
    }

    fun clear() {
        _state.value = EditorState()
    }

    fun getCompleteRanges(): List<Pair<Long, Long>> {
        return _state.value.trimPosition.completeRanges
    }

    fun play() {
        playerManager?.play()
    }

    fun pause() {
        playerManager?.pause()
    }
    
    fun retryPlayer() {
        playerManager?.retry()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager?.release()
    }
}


// --- Merged from EditorState.kt ---


/**
 * Estado global do Editor Unificado
 */
data class EditorState(
    val activeTool: EditorTool = EditorTool.NONE,
    val aspectRatio: Float? = null,
    val compressionLevel: CompressionLevel = CompressionLevel.ORIGINAL,
    val trimPosition: TrimPosition = TrimPosition.Empty,
    val currentPosition: Long = 0L,
    val videoDurationMs: Long = 0L,
    val waveformData: WaveformData = WaveformData.empty(),
    val isWaveformLoading: Boolean = false,
    val waveformError: String? = null,
    // Novos campos para AudioWaveForms
    val audioWaveformsAmplitudes: FloatArray = floatArrayOf(),
    val isAudioWaveformsLoading: Boolean = false,
    
    // Dimensions
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,

    // Player related states
    val exoPlayer: ExoPlayer? = null,
    val isPlaying: Boolean = false,
    val playerError: String? = null,
    val isSecurityError: Boolean = false,

    // Scrubbing: true enquanto o usuário está arrastando a timeline
    val isScrubbing: Boolean = false
) {
    val totalTrimmedMs: Long
        get() = trimPosition.completeRanges.sumOf { it.second - it.first }

    val finalDurationMs: Long
        get() = (videoDurationMs - totalTrimmedMs).coerceAtLeast(0L)

    val isDraftMode: Boolean get() = trimPosition.isDraftMode

    val isInsideRange: Boolean get() = trimPosition.isPositionInRange(currentPosition)

    val isDraftInsideRange: Boolean
        get() = isDraftMode && trimPosition.draftPosition?.let { draft ->
            trimPosition.completeRanges.any { (s, e) -> draft in s..e }
        } ?: false

    val hasTrims: Boolean
        get() = trimPosition.completeRanges.isNotEmpty()
}


// --- Merged from AudioViewModel.kt ---


/**
 * ViewModel especializada para gerenciar áudio e waveform.
 * 
 * Responsabilidades:
 * - Carregar waveform de áudio
 * - Gerenciar amplitudes de áudio
 * - Reportar estado de carregamento
 * - Calcular número de barras de waveform baseado na qualidade
 * 
 * Escopo: Activity (compartilhada entre HomeScreen e EditorScreen)
 */
class AudioViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    // ========== ESTADO ==========
    
    private val _waveform = MutableStateFlow<WaveformData?>(null)
    val waveform: StateFlow<WaveformData?> = _waveform.asStateFlow()
    
    private val _amplitudes = MutableStateFlow<FloatArray>(floatArrayOf())
    val amplitudes: StateFlow<FloatArray> = _amplitudes.asStateFlow()
    
    private val _uiState = MutableStateFlow<AudioUiState>(AudioUiState.Idle)
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()
    
    // ========== DEPENDÊNCIAS ==========
    
    private val waveformExtractor = WaveformExtractor(application)
    private var waveformQuality: WaveformQuality = WaveformQuality.Medium
    private var activeUri: Uri? = null

    /**
     * Carrega a waveform de áudio para um vídeo.
     * 
     * @param uri URI do vídeo
     * @param targetBarCount Número de barras de waveform (opcional)
     * @param force Force reload ignoring cache
     */
    fun loadWaveform(uri: Uri, targetBarCount: Int? = null, force: Boolean = false) {
        timber.log.Timber.d("AudioViewModel: loadWaveform desativada temporariamente para teste de stress")
        _uiState.value = AudioUiState.Ready(0)
        _amplitudes.value = floatArrayOf()
        _waveform.value = WaveformData(floatArrayOf(), 0)
        return
    }
    
    /**
     * Define a qualidade da waveform.
     * 
     * @param quality Qualidade da waveform
     */
    fun setWaveformQuality(quality: WaveformQuality) {
        waveformQuality = quality
    }
    
    /**
     * Verifica se o áudio está pronto.
     * 
     * @return true se áudio estiver carregado, false caso contrário
     */
    fun isReady(): Boolean {
        val ready = _uiState.value is AudioUiState.Ready
        return ready
    }
    
    /**
     * Limpa o estado da waveform.
     */
    fun clear() {
        _waveform.value = null
        _amplitudes.value = floatArrayOf()
        _uiState.value = AudioUiState.Idle
        activeUri = null
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    /**
     * Calcula o número de barras de waveform baseado na qualidade.
     * 
     * @param durationMs Duração do vídeo em ms
     * @param screenWidthDp Largura da tela em dp
     * @return Número de barras
     */
    private fun calculateBarCount(durationMs: Long, screenWidthDp: Float): Int {
        return waveformQuality.calculateBarCount(durationMs, screenWidthDp)
    }
    
    // ========== CLASSES DE ESTADO ==========
    
    sealed class AudioUiState {
        object Idle : AudioUiState()
        object Loading : AudioUiState()
        data class Ready(val barCount: Int) : AudioUiState()
        data class Error(val message: String) : AudioUiState()
    }
    
    // ========== FACTORY ==========
    
    class AudioViewModelFactory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AudioViewModel::class.java)) {
                return AudioViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
        }
    }
}

// --- Merged from ThumbnailViewModel.kt ---


/**
 * ViewModel especializada para gerenciar thumbnail strips.
 * 
 * Responsabilidades:
 * - Gerenciar cache de strips em memória
 * - Pré-carregar strips (preload)
 * - Carregar strips on-demand
 * - Reportar progresso de extração
 * - Gerenciar memória (eviction)
 * 
 * Escopo: Activity (compartilhada entre HomeScreen e EditorScreen)
 */
class ThumbnailViewModel(
    application: Application,
    private val videoRepository: VideoRepository
) : AndroidViewModel(application) {
    
    // ========== ESTADO ==========
    
    private val _strips = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val strips: StateFlow<Map<Int, Bitmap>> = _strips.asStateFlow()
    
    private val _thumbnailProgress = MutableStateFlow<Float>(0f)
    val thumbnailProgress: StateFlow<Float> = _thumbnailProgress.asStateFlow()

    private val _totalSegments = MutableStateFlow<Int>(0)
    val totalSegments: StateFlow<Int> = _totalSegments.asStateFlow()
    
    private val _isCached = MutableStateFlow<Boolean>(false)
    val isCached: StateFlow<Boolean> = _isCached.asStateFlow()
    
    private val _uiState = MutableStateFlow<ThumbnailUiState>(ThumbnailUiState.Idle)
    val uiState: StateFlow<ThumbnailUiState> = _uiState.asStateFlow()
    
    // ========== DEPENDÊNCIAS ==========
    
    private var stripManager: ThumbnailStripManager? = null
    
    // Controle para persistência de carregamento
    private var activeLoadingUri: Uri? = null
    private var loadingJob: kotlinx.coroutines.Job? = null
    
    // ========== MÉTODOS PÚBLICOS ==========
    
    /**
     * Carrega strips visíveis + buffer de on-demand.
     * 
     * @param uri URI do vídeo
     * @param durationMs Duração em ms
     * @param currentSecond Segundo atual da timeline
     * @param viewportWidthSeconds Largura da viewport em segundos
     * @param bufferSize Buffer de strips antes e depois da viewport (padrão: 6)
     */
    fun loadVisibleStripsWithBuffer(
        uri: Uri,
        durationMs: Long,
        currentSecond: Int,
        viewportWidthSeconds: Int,
        bufferSize: Int = 6
    ) {
        loadingJob?.cancel()
        activeLoadingUri = uri
        
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                
                // Garantir stripManager configurado
                if (stripManager == null) {
                    val density = getApplication<Application>().resources.displayMetrics.density
                    val thumbWidth = (60 * density).toInt().coerceAtLeast(1)
                    val thumbsPerStrip = 10
                    
                    // Obter metadados se necessário para o aspect ratio
                    val videoInfo = videoRepository.getMetadata(uri)
                    val aspectRatio = videoInfo?.aspectRatio ?: 1.77f
                    val thumbHeight = (thumbWidth / aspectRatio).toInt().coerceAtLeast(1)

                    stripManager = ThumbnailStripManager(
                        getApplication(), thumbWidth, thumbHeight, thumbsPerStrip,
                        adaptiveStrips = true
                    )
                }

                
                val totalSegments = stripManager?.getSegmentCount(durationMs) ?: 0
                if (totalSegments == 0) return@launch
                _totalSegments.value = totalSegments

                // 1. Calcular segmentos visíveis
                val thumbsPerStrip = stripManager?.thumbsPerStrip ?: 10
                val startSegment = (currentSecond / thumbsPerStrip).coerceAtLeast(0)
                val endSegment = ((currentSecond + viewportWidthSeconds) / thumbsPerStrip)
                    .coerceAtMost(totalSegments - 1)

                // 2. Adicionar buffer
                val bufferedStart = maxOf(0, startSegment - bufferSize)
                val bufferedEnd = minOf(totalSegments - 1, endSegment + bufferSize)

                Timber.tag("OnDemandLoading").d("Viewport: second=$currentSecond, segments=$startSegment..$endSegment, buffer=$bufferSize, range=$bufferedStart..$bufferedEnd")

                // 3. Carregar apenas segmentos no buffer (sem delay fixo)
                val segmentsToLoad = (bufferedStart..bufferedEnd).filter { segIdx ->
                    !_strips.value.containsKey(segIdx)
                }

                if (segmentsToLoad.isNotEmpty()) {
                    Timber.tag("OnDemandLoading").i("Carregando ${segmentsToLoad.size} strips: ${segmentsToLoad.first()}..${segmentsToLoad.last()}")
                    
                    // Carregar em batch de 5 para performance
                    segmentsToLoad.chunked(5).forEach { chunk ->
                        ensureActive()
                        chunk.forEach { segIdx ->
                            launch {
                                loadStrip(uri, segIdx, durationMs)
                            }
                        }
                    }
                }
                
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                }
            }
        }
    }
    
    /**
     * Pré-carrega um número fixo de strips para um vídeo.
     * 
     * DESATIVADO PARA TESTE: Usando apenas on-demand loading
     * 
     * @param uri URI do vídeo
     * @param stripsToPreload Número de strips a carregar (padrão: 6)
     */
    fun preload(uri: Uri, stripsToPreload: Int = 6) {
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = ThumbnailUiState.Loading
                _thumbnailProgress.value = 0f
                _strips.value = emptyMap()
                _totalSegments.value = 0
                
                
                // 1. Obter metadados do vídeo
                val videoInfo = videoRepository.getMetadata(uri)
                if (videoInfo == null) {
                    _uiState.value = ThumbnailUiState.Error("Metadados não disponíveis")
                    return@launch
                }
                
                // 2. Configurar stripManager
                val density = getApplication<Application>().resources.displayMetrics.density
                val thumbWidth = (60 * density).toInt().coerceAtLeast(1)
                val thumbHeight = (thumbWidth / videoInfo.aspectRatio).toInt().coerceAtLeast(1)
                val thumbsPerStrip = 10
                
                stripManager = ThumbnailStripManager(
                    getApplication(), thumbWidth, thumbHeight, thumbsPerStrip,
                    adaptiveStrips = true
                )

                val totalSegments = stripManager!!.getSegmentCount(videoInfo.durationMs)
                _totalSegments.value = totalSegments
                
                Timber.tag("ThumbnailPreload").i("Metadados carregados: duração=${videoInfo.durationMs}ms, totalSegments=$totalSegments, adaptiveStrips=true")
                
                // Notificar que a visualização está pronta (sem strips pré-carregadas)
                _uiState.value = ThumbnailUiState.Ready(0, totalSegments)
                
                Timber.tag("ThumbnailPreload").i("Preload DESATIVADO - On-demand loading vai carregar strips conforme usuário rola")
                 
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _uiState.value = ThumbnailUiState.Error(e.message ?: "Erro desconhecido")
                }
            }
        }
    }
    
    /**
     * Carrega uma strip específica on-demand.
     * 
     * @param uri URI do vídeo
     * @param segmentIndex Índice do segmento
     * @param durationMs Duração do vídeo em ms
     */
    fun loadStrip(uri: Uri, segmentIndex: Int, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_strips.value.containsKey(segmentIndex)) {
                return@launch
            }
            
            try {
                val strip = ThumbnailCacheManager.getStrip(
                    uri = uri,
                    segmentIndex = segmentIndex,
                    durationMs = durationMs,
                    thumbWidth = stripManager?.thumbWidth ?: 60,
                    thumbHeight = stripManager?.thumbHeight ?: 40,
                    thumbsPerStrip = stripManager?.thumbsPerStrip ?: 10
                )
                
                if (strip != null) {
                    _strips.update { current ->
                        current.toMutableMap().also { it[segmentIndex] = strip }
                    }
                    trimMemory() // Evitar OOM durante scroll
                }
            } catch (e: Exception) {
            }
        }
    }
    
    /**
     * Carrega múltiplas strips em background.
     * 
     * @param uri URI do vídeo
     * @param segmentIndices Lista de índices de segmentos
     * @param durationMs Duração do vídeo em ms
     */
    fun loadStrips(uri: Uri, segmentIndices: List<Int>, durationMs: Long) {
        segmentIndices.forEach { segIdx ->
            loadStrip(uri, segIdx, durationMs)
        }
    }
    
    /**
     * Verifica se há strips suficientes carregadas.
     * 
     * @param requiredStrips Número mínimo de strips necessário
     * @return true se houver strips suficientes, false caso contrário
     */
    fun hasEnoughStrips(requiredStrips: Int): Boolean {
        val count = _strips.value.size
        val result = count >= requiredStrips
        
        
        return result
    }
    
    /**
     * Limpa todas as strips e estado.
     *
     * IMPORTANTE: NÃO recicla os bitmaps pois eles ainda podem estar
     * sendo usados pelo ThumbnailCacheManager (cache compartilhado).
     *
     * O cache LRU do ThumbnailCacheManager gerencia automaticamente a
     * liberação de memória quando necessário.
     */
    fun clear() {

        // NÃO recicla bitmaps - eles podem estar sendo usados pelo cache
        // _strips.value.values.forEach { bitmap ->
        //     if (!bitmap.isRecycled) bitmap.recycle()
        // }

        _strips.value = emptyMap()
        _thumbnailProgress.value = 0f
        _totalSegments.value = 0
        _isCached.value = false
        _uiState.value = ThumbnailUiState.Idle
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    /**
     * Limita o número de bitmaps em memória para evitar OOM.
     * Mantém os 500 segmentos mais recentes para suportar vídeos longos (> 1h).
     * Otimizado: Remove diretamente sem criar cópia do map.
     */
    private fun trimMemory() {
        _strips.update { current ->
            if (current.size > 500) {
                val keysToRemove = current.keys.take(current.size - 500)
                current.toMutableMap().also { map ->
                    keysToRemove.forEach { map.remove(it) }
                }
            } else {
                current
            }
        }
    }

    /**
     * Extrai thumbnails em estágios (LOD) com processamento em lotes.
     */
    private suspend fun extractThumbnailsLOD(
        uri: Uri,
        totalSegments: Int,
        durationMs: Long,
        onlyFirstFrame: Boolean
    ): Map<Int, Bitmap> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val density = getApplication<Application>().resources.displayMetrics.density
        val defaultThumbWidth = (60 * density).toInt().coerceAtLeast(1)
        val defaultThumbHeight = (defaultThumbWidth / 1.77f).toInt().coerceAtLeast(1)
        
        
        val batchSize = 5 
        
        for (i in 0 until totalSegments step batchSize) {
            ensureActive()
            
            val end = (i + batchSize).coerceAtMost(totalSegments)
            val batchIndices = i until end
            
            val jobs = batchIndices.map { segIdx ->
                async {
                    ensureActive()
                    
                    val strip = ThumbnailCacheManager.getStrip(
                        uri = uri,
                        segmentIndex = segIdx,
                        durationMs = durationMs,
                        thumbWidth = stripManager?.thumbWidth ?: defaultThumbWidth,
                        thumbHeight = stripManager?.thumbHeight ?: defaultThumbHeight,
                        thumbsPerStrip = stripManager?.thumbsPerStrip ?: 10,
                        onlyFirstFrame = onlyFirstFrame
                    )
                    strip?.let { segIdx to it }
                }
            }

            val results: List<Pair<Int, Bitmap>> = jobs.awaitAll().filterNotNull()
            
            _strips.update { current ->
                current.toMutableMap().also { map ->
                    results.forEach { (segmentIndex, strip) ->
                        map[segmentIndex] = strip
                    }
                }
            }
            
            trimMemory()
            
            // Reduzido drasticamente para scroll mais fluido
            val baseDelay = if (onlyFirstFrame) 5L else {
                if (durationMs > 3_600_000L) 20L else 10L
            }
            kotlinx.coroutines.delay(baseDelay)
        }

        _strips.value.toMap()
    }
    
    /**
     * Extrai thumbnails com progresso em estágios (DEPRECATED: Usar LOD).
     */
    
    override fun onCleared() {
        super.onCleared()
        clear()
    }
    
    // ========== CLASSES DE ESTADO ==========
    
    sealed class ThumbnailUiState {
        object Idle : ThumbnailUiState()
        object Loading : ThumbnailUiState()
        data class Ready(val loadedCount: Int, val totalCount: Int) : ThumbnailUiState()
        data class Error(val message: String) : ThumbnailUiState()
    }
    
    // ========== FACTORY ==========
    
    class ThumbnailViewModelFactory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ThumbnailViewModel::class.java)) {
                val videoRepository = VideoRepository(application)
                return ThumbnailViewModel(application, videoRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
        }
    }
}

// --- Merged from AudioConfig.kt ---

object AudioConfig {
    object Extraction {
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_SAMPLES_PER_BAR = 1000
        const val NOISE_SAMPLE_SIZE = 50000
        const val DECODER_TIMEOUT_US = 100000L
        const val MAX_TRY_AGAIN = 200
    }
    
    object Quality {
        const val SILENCE_THRESHOLD = 0.015f
        const val VOICE_BOOST_FACTOR = 1.5f
        const val DYNAMIC_THRESHOLD_MULTIPLIER = 2.5f
    }
}

// --- Merged from ThumbnailConfig.kt ---

object ThumbnailConfig {
    object Dimensions {
        const val COMPACT_HEIGHT = 40
        const val COMPACT_WIDTH = 40
        const val DETAILED_HEIGHT = 80
        const val DETAILED_WIDTH = 80
        const val DEFAULT_HEIGHT = 180
        const val DEFAULT_WIDTH = 320
        const val NORMAL_HEIGHT = 50
        const val NORMAL_WIDTH = 50
    }
    
    object Quality {
        const val DEFAULT_THUMBS_PER_STRIP = 10
        const val HIGH_QUALITY_EXTRACT_FACTOR = 1.2f
        const val JPEG_COMPRESSION_QUALITY = 80
    }
    
    object Timing {
        const val INTERVAL_CALCULATION_DIVISOR = 1000L
    }
    
    object FileFormats {
        const val EXT_JPG = ".jpg"
        const val EXT_PNG = ".png"
        const val EXT_WEBP = ".webp"
    }
    
    object Concurrency {
        const val IO_SEMAPHORE_PERMITS = 3
    }
    
    object Cache {
        const val CACHE_VERSION = 3
        const val MAX_CACHE_SIZE = 200L * 1024 * 1024
    }
    
    object Compression {
        const val STRIP_COMPRESSION_QUALITY = 70
    }
    
    object Adaptive {
        const val MIN_THUMBS_PER_STRIP = 5
        const val ADAPTIVE_POWER_CURVE_EXPONENT = 0.5f
    }
}
