package com.chopcut

import android.app.Application
import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import android.util.Log
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.ensureActive
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState


// --- Merged from HomeScreen.kt ---


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    preloadViewModel: PreloadViewModel,
    onNavigateToEditor: (Uri) -> Unit = {},
    onNavigateToTimelineV2: (String) -> Unit = {}
) {
    val application = LocalContext.current.applicationContext as Application
    val videoRepository = remember { VideoRepository(application) }
    val factory = remember { HomeViewModelFactory(application, videoRepository) }
    val viewModel: HomeViewModel = viewModel(factory = factory)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()
    val preloadUiState by preloadViewModel.uiState.collectAsStateWithLifecycle()
    val isPreloadReady by preloadViewModel.isReadyFlow.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val clearCacheState by viewModel.clearCacheState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showExtractionProgress by remember { mutableStateOf(false) }
    val extractionProgress by viewModel.extractionProgress.collectAsStateWithLifecycle()

    // Iniciar preload ao selecionar vídeo
    LaunchedEffect(selectedUri, uiState) {
        val uri = selectedUri
        if (uri != null && uiState is HomeUiState.VideoLoaded) {
            preloadViewModel.startPreload(uri)
        } else {
        }
    }

    var showGallery by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            showGallery = true
        } else {
        }
    }

    val requestGallery: () -> Unit = {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ChopCut") }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = ChopCutSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ChopCutSpacing.lg)
            ) {
                item {
                    Spacer(Modifier.height(ChopCutSpacing.xs))
        
                     val uri = selectedUri
                    when {
                        uri != null && uiState is HomeUiState.VideoLoaded -> {
                            val isLoading = !isPreloadReady

                            Column(verticalArrangement = Arrangement.spacedBy(ChopCutSpacing.sm)) {
                                VideoPickerLoaded(
                                    videoInfo = (uiState as HomeUiState.VideoLoaded).videoInfo,
                                    videoUri = uri,
                                    isPreloading = isLoading,
                                    onChangeVideo = requestGallery,
                                    onOpenEditor = {
                                        onNavigateToEditor(uri)
                                    },
                                    onRemoveVideo = {
                                        preloadViewModel.clear()
                                        viewModel.clearSelectedVideo()
                                    }
                                )
                                ChopCutSecondaryButton(
                                    text = "Extrair Frames",
                                    onClick = {
                                        showExtractionProgress = true
                                        viewModel.startExtraction(
                                            uri,
                                            ThumbnailSettings(
                                                thumbsPerSecond = 1,
                                                quality = 85,
                                                format = ThumbnailFormat.JPEG,
                                                scaleMode = ThumbnailScaleMode.FILL,
                                                extractionQuality = ThumbnailQuality.HIGH
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        uri != null && uiState is HomeUiState.Loading -> {
                            VideoPickerLoading()
                        }
                        else -> {
                            VideoPickerEmpty(onClick = requestGallery)
                        }
                    }
                }
        
                item {
                    Text(
                        text = "Sistema",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    Spacer(Modifier.height(ChopCutSpacing.sm))
                    CacheFeatureCard(
                        diskCacheSize = cacheSizeBytes,
                        clearCacheState = clearCacheState,
                        onClearCache = { viewModel.clearCache() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(ChopCutSpacing.sm))
                    ChopCutSecondaryButton(
                        onClick = {
                            val uri = selectedUri
                            if (uri != null) {
                                val encodedUri = java.net.URLEncoder.encode(uri.toString(), "UTF-8")
                                onNavigateToTimelineV2("timelineV2?videoUri=$encodedUri")
                            } else {
                                onNavigateToTimelineV2("timelineV2")
                            }
                        },
                        text = "TimelineV2 Demo",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (uiState is HomeUiState.Error) {
                    item {
                        ErrorState(
                            title = "Erro",
                            message = (uiState as HomeUiState.Error).message,
                            actionLabel = "Dispensar",
                            onAction = { viewModel.resetState() }
                        )
                    }
                }
        
                item { Spacer(Modifier.height(ChopCutSpacing.md)) }
            }

        }
    }

    if (showGallery) {
        BottomSheetGallery(
            onDismiss = { showGallery = false },
            onVideoSelected = { uri ->
                showGallery = false
                viewModel.selectVideo(uri)
            }
        )
    }

    ExtractionProgressBottomSheet(
        visible = showExtractionProgress,
        progressState = extractionProgress,
        onDismiss = {
            showExtractionProgress = false
            if (extractionProgress.isRunning) {
                viewModel.cancelExtraction()
            }
        },
        onCancel = {
            viewModel.cancelExtraction()
        }
    )
}

@Composable
private fun VideoPickerEmpty(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RectangleShape)
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(ChopCutSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    Primary.copy(alpha = 0.12f),
                    RectangleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = Primary
            )
        }
        Spacer(Modifier.height(ChopCutSpacing.sm))
        Text(
            text = "Selecionar Vídeo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )
        Spacer(Modifier.height(ChopCutSpacing.xxs))
        Text(
            text = "Toque para escolher da galeria",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(ChopCutSpacing.sm))
        Box(
            modifier = Modifier
                .padding(horizontal = ChopCutSpacing.lg)
                .fillMaxWidth()
                .height(40.dp)
                .background(Primary, RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = OnPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Escolher Vídeo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = OnPrimary
                )
            }
        }
    }
}

@Composable
private fun VideoPickerLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RectangleShape)
            .background(SurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChopCutSpacing.sm)
        ) {
            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp
            )
            Text(
                text = "Carregando vídeo...",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun VideoPickerLoaded(
    videoInfo: VideoInfo,
    videoUri: Uri,
    isPreloading: Boolean = false,
    onChangeVideo: () -> Unit,
    onOpenEditor: () -> Unit,
    onRemoveVideo: () -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RectangleShape)
            .background(SurfaceVariant)
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(videoUri)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader
            ),
            contentDescription = "Thumbnail do vídeo",
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            OverlayDark.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ChopCutSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.sm)
        ) {
            BadgeText(text = "${videoInfo.width}×${videoInfo.height}")
            BadgeText(text = FormatUtils.getAspectRatio(videoInfo.width, videoInfo.height))
            Spacer(modifier = Modifier.weight(1f))
            BadgeText(text = formatDuration(videoInfo.durationMs))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(ChopCutSpacing.sm)
        ) {
            Text(
                text = videoInfo.fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(ChopCutSpacing.xxs))
            Text(
                text = buildString {
                    append(videoInfo.videoCodec ?: "N/A")
                    append(" · ")
                    append("${videoInfo.frameRate}fps")
                    if (videoInfo.hasAudio) append(" · 🔊")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(ChopCutSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(Primary, RectangleShape)
                        .clickable(onClick = onOpenEditor),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPreloading) {
                            CircularProgressIndicator(
                                color = OnPrimary,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = OnPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (isPreloading) "Preparando..." else "Editar",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = OnPrimary
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(80.dp)
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            RectangleShape
                        )
                        .clickable(onClick = onChangeVideo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Trocar vídeo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(50.dp)
                        .background(
                            Color.Red.copy(alpha = 0.8f),
                            RectangleShape
                        )
                        .clickable(onClick = onRemoveVideo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remover vídeo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Card de Cache de Thumbnails nos recursos
 * Mostra os bytes de cache e botão para limpar
 */
@Composable
private fun CacheFeatureCard(
    diskCacheSize: Long,
    clearCacheState: ClearCacheState,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RectangleShape)
            .background(Surface)
            .padding(ChopCutSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Cache",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = OnSurface
            )
            Text(
                text = when (clearCacheState) {
                    ClearCacheState.Idle -> formatBytes(diskCacheSize)
                    ClearCacheState.Clearing -> "Limpando..."
                    ClearCacheState.Done -> "Cache limpo!"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (clearCacheState == ClearCacheState.Done) Success else TextSecondary
            )
        }

        ChopCutSecondaryButton(
            onClick = onClearCache,
            text = when (clearCacheState) {
                ClearCacheState.Idle -> "Limpar"
                ClearCacheState.Clearing -> "Limpando..."
                ClearCacheState.Done -> "Limpo!"
            },
            enabled = clearCacheState == ClearCacheState.Idle
        )
    }
}

// formatAspectRatio removido (centralizado em FormatUtils)

/**
 * Formata bytes em formato legível (KB, MB, GB)
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

@Composable
private fun BadgeText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RectangleShape
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
// --- Merged from HomeViewModel.kt ---


/**
 * ViewModel para HomeScreen.
 * 
 * Responsabilidades:
 * - Gerenciar seleção de vídeo
 * - Carregar metadados do vídeo
 * - Gerenciar estado da UI (Loading, VideoLoaded, Error)
 * - Testar funcionalidade de trim
 * 
 * NOTA: O pré-carregamento de thumbnails e áudio é gerenciado
 * pela PreloadViewModel (Activity-scoped), não por esta ViewModel.
 */
data class ExtractionProgressState(
    val isRunning: Boolean = false,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val logs: List<String> = emptyList(),
    val isComplete: Boolean = false,
    val error: String? = null,
    val outputDirPath: String? = null,
    val statsSummary: String? = null
)

class HomeViewModel(
    application: Application,
    private val videoRepository: VideoRepository
) : AndroidViewModel(application) {

    private val transformerPipeline = TransformerPipeline(application, videoRepository)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()
    
    private val _errorState = MutableStateFlow<ErrorHandler.ErrorState?>(null)
    val errorState: StateFlow<ErrorHandler.ErrorState?> = _errorState.asStateFlow()

    // --- Extração de Frames ---
    private val _extractionProgress = MutableStateFlow(ExtractionProgressState())
    val extractionProgress: StateFlow<ExtractionProgressState> = _extractionProgress.asStateFlow()

    private var extractionJob: Job? = null

    fun cancelExtraction() {
        extractionJob?.cancel()
        _extractionProgress.update { current ->
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val logLine = "[$timestamp] ⚠️ Extração cancelada pelo usuário."
            Log.w("ChopCutExtraction", "Extração cancelada pelo usuário.")
            current.copy(
                isRunning = false,
                logs = current.logs + logLine,
                error = "Cancelada pelo usuário"
            )
        }
    }

    fun startExtraction(uri: Uri, settings: ThumbnailSettings) {
        val videoInfo = (uiState.value as? HomeUiState.VideoLoaded)?.videoInfo ?: return
        
        extractionJob?.cancel()
        
        extractionJob = viewModelScope.launch(Dispatchers.IO) {
            _extractionProgress.value = ExtractionProgressState(isRunning = true)
            
            val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            // Apenas escreve no ADB - nao dispara recomposicao
            fun adbLog(message: String, isError: Boolean = false) {
                if (isError) Log.e("ChopCutExtraction", message)
                else Log.i("ChopCutExtraction", message)
            }
            // Escreve no ADB + acumula log para UI (usar somente fora do loop de frames)
            fun addLog(message: String, isError: Boolean = false) {
                val timestamp = logTimeFormat.format(Date())
                val formatted = "[$timestamp] $message"
                adbLog(message, isError)
                _extractionProgress.update { current ->
                    current.copy(logs = current.logs + formatted)
                }
            }

            val startTime = System.currentTimeMillis()
            addLog("🚀 Iniciando extração de frames...")
            addLog("🎬 Vídeo: ${videoInfo.fileName} (${videoInfo.width}x${videoInfo.height}, ${videoInfo.frameRate}fps, ${formatDuration(videoInfo.durationMs)})")
            addLog("⚙️ Configurações:")
            val videoAr = videoInfo.aspectRatio
            val (cfgW, cfgH) = settings.computeDimensions(videoAr)
            addLog("   • Tamanho: ${settings.sizePreset.displayName} (${cfgW}x${cfgH}, 16:9)")
            addLog("   • Escala: ${settings.scaleMode.displayName}")
            addLog("   • Formato: ${settings.format.displayName}")
            addLog("   • Qualidade: ${settings.quality}%")
            
            // Taxa de quadros
            val thumbsPerSecond = settings.thumbsPerSecond
            val intervalMs = if (thumbsPerSecond <= 0) {
                // "Todos os frames"
                addLog("   • Taxa: Todos os frames (${videoInfo.frameRate} fps)")
                1000f / videoInfo.frameRate.coerceAtLeast(1)
            } else {
                addLog("   • Taxa: $thumbsPerSecond frame(s) por segundo")
                1000f / thumbsPerSecond
            }

            val totalFrames = if (thumbsPerSecond <= 0) {
                (videoInfo.durationMs * videoInfo.frameRate / 1000f).toInt().coerceAtLeast(1)
            } else {
                (videoInfo.durationMs / intervalMs).toInt().coerceAtLeast(1)
            }

            addLog("📊 Total estimado de frames a extrair: $totalFrames")

            // Criar diretório de saída
            val sanitizedName = videoInfo.fileName.substringBeforeLast(".").replace("[^a-zA-Z0-9_\\-]".toRegex(), "_").trim('_')
            val outputDirName = sanitizedName
            
            val baseDir = getApplication<Application>().getExternalFilesDir("extracted_frames")
                ?: getApplication<Application>().filesDir
            val outputDir = File(baseDir, outputDirName)
            
            if (!outputDir.exists()) {
                val created = outputDir.mkdirs()
                if (created) {
                    addLog("📁 Pasta criada: ${outputDir.absolutePath}")
                } else {
                    addLog("❌ Falha ao criar a pasta de destino.", isError = true)
                    _extractionProgress.update { it.copy(isRunning = false, error = "Falha ao criar diretório") }
                    return@launch
                }
            } else {
                addLog("📁 Pasta de destino: ${outputDir.absolutePath}")
            }

            _extractionProgress.update { it.copy(total = totalFrames, outputDirPath = outputDir.absolutePath) }

            val retriever = MediaMetadataRetriever()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.setDataSource(getApplication(), uri)
                } else {
                    @Suppress("DEPRECATION")
                    retriever.setDataSource(uri.toString())
                }

                val extension = when (settings.format) {
                    ThumbnailFormat.JPEG -> ThumbnailConfig.FileFormats.EXT_JPG
                    ThumbnailFormat.PNG -> ThumbnailConfig.FileFormats.EXT_PNG
                    ThumbnailFormat.WEBP -> ThumbnailConfig.FileFormats.EXT_WEBP
                }
                
                val compressFormat = when (settings.format) {
                    ThumbnailFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    ThumbnailFormat.PNG -> Bitmap.CompressFormat.PNG
                    ThumbnailFormat.WEBP -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                    }
                }

                var successCount = 0
                val extractionStart = System.currentTimeMillis()

                for (i in 0 until totalFrames) {
                    // Verificar se foi cancelado
                    ensureActive()

                    val positionMs = (i * intervalMs).toLong()
                    val frameFile = File(outputDir, "frame_${String.format("%05d", i + 1)}$extension")

                    val frameStart = System.currentTimeMillis()
                    
                    try {
                        val quality = settings.extractionQuality
                        val (targetW, targetH) = settings.computeDimensions(videoAr)
                        val (extractBaseW, extractBaseH) = settings.computeExtractDimensions(videoAr)

                        // Supersampling para qualidade alta: extrair maior e reduzir
                        val (extractW, extractH) = if (quality == ThumbnailQuality.HIGH) {
                            val factor = ThumbnailConfig.Quality.HIGH_QUALITY_EXTRACT_FACTOR
                            (extractBaseW * factor).toInt() to (extractBaseH * factor).toInt()
                        } else {
                            extractBaseW to extractBaseH
                        }

                        // Extrair frame escalado (getScaledFrameAtTime retorna frame bruto sem aplicar rotação)
                        val hasRotation = videoInfo.rotation == 90 || videoInfo.rotation == 270
                        val (reqW, reqH) = if (hasRotation) {
                            extractH to extractW
                        } else {
                            extractW to extractH
                        }
                        val rawFrame = retriever.getScaledFrameAtTime(
                            positionMs * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            reqW,
                            reqH
                        )

                        // Aplicar rotação se necessário (ex: vídeos 9:16 armazenados como 16:9 landscape)
                        val orientedFrame = if (rawFrame != null && hasRotation) {
                            val matrix = android.graphics.Matrix()
                            matrix.postRotate(videoInfo.rotation.toFloat())
                            val rotated = Bitmap.createBitmap(rawFrame, 0, 0, rawFrame.width, rawFrame.height, matrix, true)
                            if (rotated != rawFrame) rawFrame.recycle()
                            rotated
                        } else {
                            rawFrame
                        }

                        // Mapear para dimensões alvo conforme scaleMode
                        val bitmap = when {
                            orientedFrame == null -> null
                            settings.scaleMode == ThumbnailScaleMode.FILL && videoAr >= 1f && targetW != targetH -> {
                                // Center-crop para landscape/square (preserva conteúdo nas bordas)
                                val srcW = orientedFrame.width
                                val srcH = orientedFrame.height
                                val srcAr = srcW.toFloat() / srcH.toFloat()
                                val dstAr = targetW.toFloat() / targetH.toFloat()
                                val (cropW, cropH) = if (srcAr > dstAr) {
                                    (srcH * dstAr).toInt() to srcH
                                } else {
                                    srcW to (srcW / dstAr).toInt()
                                }
                                val cropX = (srcW - cropW) / 2
                                val cropY = (srcH - cropH) / 2
                                val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
                                val canvas = android.graphics.Canvas(result)
                                val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropW, cropY + cropH)
                                val dstRect = android.graphics.Rect(0, 0, targetW, targetH)
                                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                                canvas.drawBitmap(orientedFrame, srcRect, dstRect, paint)
                                orientedFrame.recycle()
                                result
                            }
                            else -> {
                                // FIT (ou portrait FILL): redimensiona mantendo AR dentro do box
                                if (quality == ThumbnailQuality.HIGH && (orientedFrame.width != targetW || orientedFrame.height != targetH)) {
                                    val scaled = Bitmap.createScaledBitmap(orientedFrame, targetW, targetH, true)
                                    if (scaled != orientedFrame) orientedFrame.recycle()
                                    scaled
                                } else {
                                    orientedFrame
                                }
                            }
                        }

                        if (bitmap != null) {
                            // Salvar no arquivo
                            java.io.FileOutputStream(frameFile).use { out ->
                                bitmap.compress(compressFormat, settings.quality, out)
                            }
                            bitmap.recycle()
                            successCount++

                            val frameDuration = System.currentTimeMillis() - frameStart
                            val percent = ((i + 1) * 100f / totalFrames).toInt()
                            val logMsg = "📸 Frame ${i + 1}/$totalFrames ($percent%) ${targetW}x${targetH} em ${frameDuration}ms -> ${frameFile.name}"
                            adbLog(logMsg)
                            val timestamp = logTimeFormat.format(Date())
                            _extractionProgress.update { current ->
                                current.copy(
                                    currentIndex = i + 1,
                                    logs = current.logs + "[$timestamp] $logMsg"
                                )
                            }
                        } else {
                            val warnMsg = "⚠️ Falha ao obter frame ${i + 1} na posição ${positionMs}ms"
                            adbLog(warnMsg, isError = true)
                            val timestamp = logTimeFormat.format(Date())
                            _extractionProgress.update { current ->
                                current.copy(
                                    currentIndex = i + 1,
                                    logs = current.logs + "[$timestamp] $warnMsg"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        val errMsg = "⚠️ Erro no frame ${i + 1}: ${e.message}"
                        adbLog(errMsg, isError = true)
                        val timestamp = logTimeFormat.format(Date())
                        _extractionProgress.update { current ->
                            current.copy(
                                currentIndex = i + 1,
                                logs = current.logs + "[$timestamp] $errMsg"
                            )
                        }
                    }
                }

                val totalDuration = System.currentTimeMillis() - startTime
                val avgTimePerFrame = if (successCount > 0) (totalDuration - (extractionStart - startTime)) / successCount else 0L
                val fpsThroughput = if (totalDuration > 0) (successCount * 1000f / totalDuration) else 0f
                
                val summary = """
                    📊 ESTATÍSTICAS DE EXTRAÇÃO:
                       • Frames com sucesso: $successCount/$totalFrames
                       • Tempo total: ${String.format("%.2f", totalDuration / 1000f)}s
                       • Média por frame: ${avgTimePerFrame}ms (${String.format("%.1f", fpsThroughput)} fps)
                       • Destino: Android/data/com.chopcut/files/extracted_frames/$outputDirName/
                       
                    💻 PARA COPIAR VIA ADB NO COMPUTADOR:
                       adb pull "/sdcard/Android/data/com.chopcut/files/extracted_frames/$outputDirName" ./extracted_frames
                """.trimIndent()

                Log.i("ChopCutExtraction", summary)
                
                _extractionProgress.update { current ->
                    current.copy(
                        isRunning = false,
                        isComplete = true,
                        statsSummary = summary,
                        logs = current.logs + "🎉 Extração finalizada com sucesso!"
                    )
                }

            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    addLog("❌ Falha crítica: ${e.message}", isError = true)
                    _extractionProgress.update { current ->
                        current.copy(isRunning = false, error = e.message ?: "Erro desconhecido")
                    }
                }
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun selectVideo(uri: Uri) {
        _selectedVideoUri.value = uri
        loadVideoMetadata(uri)
    }
    
    fun clearSelectedVideo() {
        _selectedVideoUri.value = null
        resetState()
    }
    
    private fun loadVideoMetadata(uri: Uri) {
        
        viewModelScope.launch(DispatcherProvider.io) {
            _uiState.value = HomeUiState.Loading
            _errorState.value = null

            val result = safeExecuteSuspend(context = getApplication()) {
                videoRepository.getMetadata(uri)
            }

            when (result) {
                is ErrorResult.Success -> {
                    val metadata = result.data
                    if (metadata != null) {
                        // Validar duração
                        val validation = VideoConstraints.getValidationMessage(
                            metadata.durationMs
                        )
                        if (validation != null) {
                            _errorState.value = ErrorHandler.ErrorState(
                                title = "Vídeo muito longo",
                                message = validation,
                                recovery = RecoveryStrategy.SelectAnotherVideo
                            )
                            _uiState.value = HomeUiState.Error(validation)
                        } else {
                            _uiState.value = HomeUiState.VideoLoaded(metadata)
                        }
                    } else {
                        _errorState.value = ErrorHandler.ErrorState(
                            title = "Erro de vídeo",
                            message = "Falha ao ler metadados do vídeo",
                            recovery = RecoveryStrategy.SelectAnotherVideo
                        )
                    }
                }
                is ErrorResult.Error -> {
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
                recovery = RecoveryStrategy.SelectAnotherVideo
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = HomeUiState.Processing("Trimming video...")
            _errorState.value = null

            try {
                val range = TimeRange(startMs = 0, endMs = 5000)

                transformerPipeline.trim(uri, listOf(range))
                    .collect { progress ->
                        when (progress) {
                            is TrimProgress.InProgress -> {
                                // Progresso intermediário - pode atualizar UI se necessário
                            }
                            is TrimProgress.Completed -> {
                                val file = progress.file
                                _uiState.value = HomeUiState.Success(
                                    "Trim completed!\nOutput: ${file.name}\nSize: ${file.length() / 1024} KB"
                                )
                            }
                            is TrimProgress.Failed -> {
                                val error = progress.error
                                val errorState = ErrorHandler.handle(error, getApplication())
                                _errorState.value = errorState
                                _uiState.value = HomeUiState.Error(errorState.message)
                            }
                        }
                    }
            } catch (e: Exception) {
                val errorState = ErrorHandler.handle(e, getApplication())
                _errorState.value = errorState
                _uiState.value = HomeUiState.Error(errorState.message)
            }
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Initial
    }

    // --- Cache management ---

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _clearCacheState = MutableStateFlow(ClearCacheState.Idle)
    val clearCacheState: StateFlow<ClearCacheState> = _clearCacheState.asStateFlow()

    init {
        loadCacheInfo()
    }

    fun loadCacheInfo() {
        viewModelScope.launch(DispatcherProvider.io) {
            val cacheDir = File(getApplication<Application>().cacheDir, ThumbnailStripManager.CACHE_DIR)
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles() ?: emptyArray()
                _cacheSizeBytes.value = files.sumOf { it.length() }
            } else {
                _cacheSizeBytes.value = 0L
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _clearCacheState.value = ClearCacheState.Clearing
            withContext(DispatcherProvider.io) {
                ThumbnailStripManager.clearCache(getApplication())
                ThumbnailCacheManager.clearAll()
            }
            _cacheSizeBytes.value = 0L
            _clearCacheState.value = ClearCacheState.Done
            delay(2000)
            _clearCacheState.value = ClearCacheState.Idle
        }
    }
}

enum class ClearCacheState { Idle, Clearing, Done }

sealed class HomeUiState {
    object Initial : HomeUiState()
    object Loading : HomeUiState()
    data class VideoLoaded(val videoInfo: VideoInfo) : HomeUiState()
    data class Processing(val message: String) : HomeUiState()
    data class Success(val message: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

// --- Merged from HomeViewModelFactory.kt ---


class HomeViewModelFactory(
    private val application: Application,
    private val videoRepository: VideoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(application, videoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- Merged from BottomSheetGallery.kt ---



data class GalleryVideo(
    val id: Long,
    val uri: Uri,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int
)

enum class GallerySortOrder(val label: String, val column: String, val direction: String) {
    SIZE_DESC("Maior", MediaStore.Video.Media.SIZE, "DESC"),
    SIZE_ASC("Menor", MediaStore.Video.Media.SIZE, "ASC"),
    DATE_DESC("Recente", MediaStore.Video.Media.DATE_ADDED, "DESC"),
    DATE_ASC("Antigo", MediaStore.Video.Media.DATE_ADDED, "ASC");

    fun toSortClause(): String = "$column $direction"

    companion object {
        fun fromKey(key: String): GallerySortOrder =
            entries.find { it.name == key } ?: SIZE_DESC
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetGallery(
    onDismiss: () -> Unit,
    onVideoSelected: (Uri) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<GalleryVideo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentSort by remember {
        mutableStateOf(GallerySortOrder.SIZE_DESC)
    }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    LaunchedEffect(currentSort) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val videoList = mutableListOf<GalleryVideo>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )
            val sortOrder = currentSort.toSortClause()

            // DEBUG: restringir galeria ao diretório de vídeos de teste
            val testDir = "${Environment.getExternalStorageDirectory().absolutePath}/Movies/ChopCut/teste"
            val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("$testDir/%")

            val query = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            query?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val width = cursor.getShort(widthColumn).toInt()
                    val height = cursor.getShort(heightColumn).toInt()
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    videoList.add(GalleryVideo(id, contentUri, duration, size, width, height))
                }
            }
            withContext(Dispatchers.Main) {
                videos = videoList
                isLoading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 16.dp)
        ) {
            Text(
                text = "Selecione um Vídeo",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GallerySortOrder.entries.forEach { sort ->
                    FilterChip(
                        selected = currentSort == sort,
                        onClick = {
                            currentSort = sort
                        },
                        label = { Text(sort.label, fontSize = 13.sp) }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (videos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum vídeo encontrado.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(videos) { video ->
                        VideoGridItem(video, imageLoader, onVideoSelected)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoGridItem(video: GalleryVideo, imageLoader: ImageLoader, onClick: (Uri) -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.Black)
            .clickable {
                onClick(video.uri)
            }
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(video.uri)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Duration Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.7f), RectangleShape)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatDuration(video.durationMs),
                color = Color.White,
                fontSize = 10.sp
            )
        }

        // Ratio Badge (Bottom Start)
        val ratio = FormatUtils.getAspectRatio(video.width, video.height)
        if (ratio != "N/A") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RectangleShape)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = ratio,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }

        // Size Badge (Top Left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.7f), RectangleShape)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatSize(video.sizeBytes),
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
}

fun formatSize(bytes: Long): String {
    val mb = bytes / (1024 * 1024)
    if (mb > 1024) {
        return String.format(Locale.US, "%.1f GB", mb / 1024.0)
    }
    return "${mb} MB"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractionProgressBottomSheet(
    visible: Boolean,
    progressState: ExtractionProgressState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val lazyListState = rememberLazyListState()
    
    // Auto-scroll nos logs sempre que a lista cresce
    LaunchedEffect(progressState.logs.size) {
        if (progressState.logs.isNotEmpty()) {
            try {
                lazyListState.animateScrollToItem(progressState.logs.size - 1)
            } catch (e: Exception) {}
        }
    }

    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = {
            if (!progressState.isRunning) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Background,
        contentColor = OnBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (progressState.isRunning) "Extraindo Frames..." else "Extração Finalizada",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                if (!progressState.isRunning) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = OnSurface
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

            // Barra de Progresso
            if (progressState.isRunning) {
                val progress = if (progressState.total > 0) {
                    progressState.currentIndex.toFloat() / progressState.total
                } else 0f
                val percent = (progress * 100).toInt()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Progresso Geral",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "$percent% (${progressState.currentIndex}/${progressState.total})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = Primary,
                        trackColor = SurfaceVariant
                    )
                }
            }

            // Console estilo Terminal Dark
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Logs em Tempo Real (ADB Mirror)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.Black)
                        .border(1.dp, SurfaceVariant, RectangleShape)
                        .padding(12.dp)
                ) {
                    if (progressState.logs.isEmpty()) {
                        Text(
                            text = "Iniciando subsistema de extração...",
                            style = DurationTextStyle.copy(
                                fontFamily = ChopCutMonoFont,
                                color = TextDisabled
                            )
                        )
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(progressState.logs.size) { index ->
                                val log = progressState.logs[index]
                                val color = when {
                                    log.contains("❌") || log.contains("Erro") -> Error
                                    log.contains("⚠️") -> Warning
                                    log.contains("🎉") || log.contains("📸 Frame") -> Success
                                    log.contains("🚀") || log.contains("🎬") || log.contains("📁") || log.contains("📊") -> Primary
                                    else -> TextSecondary
                                }
                                Text(
                                    text = log,
                                    style = DurationTextStyle.copy(
                                        fontFamily = ChopCutMonoFont,
                                        color = color,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Detalhes e estatísticas de conclusão
            if (progressState.isComplete && progressState.statsSummary != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariant, RectangleShape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sumário de Performance",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Success
                    )
                    Text(
                        text = progressState.statsSummary,
                        style = DurationTextStyle.copy(
                            fontFamily = ChopCutMonoFont,
                            color = OnSurface,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // Display de Erro crítico
            if (progressState.error != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Error.copy(alpha = 0.15f), RectangleShape)
                        .border(1.dp, Error, RectangleShape)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Erro",
                        tint = Error
                    )
                    Text(
                        text = "Falha: ${progressState.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Botão de Cancelar / Confirmar
            if (progressState.isRunning) {
                ChopCutSecondaryButton(
                    text = "Cancelar Extração",
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Close
                )
            } else {
                ChopCutPrimaryButton(
                    text = "Fechar",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


// --- Merged from PreloadUiState.kt ---


object PreloadConfig {
    // MELHORIA: Delay removido - extração agora é rápida (67% mais rápido com ThumbnailExtractorBatch)
    // Antes: 3000ms (extração lenta 300-500ms/frame)
    // Agora: 0ms (extração rápida 137ms/frame com cache em disco)
    const val THUMBNAIL_EXTRACTION_DELAY_MS = 0L
}

sealed class PreloadUiState {
    object Idle : PreloadUiState()
    data class Loading(val progress: PreloadProgress) : PreloadUiState()
    data class Ready(val data: PreloadedData) : PreloadUiState()
    data class Error(
        val message: String,
        val isDurationExceeded: Boolean = false
    ) : PreloadUiState()
    object Cancelled : PreloadUiState()
}

data class PreloadedData(
    val videoInfo: VideoInfo,
    val audioAmplitudes: List<Float>,
    val preloadedStrips: Map<Int, Bitmap>,
    val totalSegments: Int,
    val preloadPercentage: Float
) {
    val videoUri: Uri get() = videoInfo.uri
}

data class PreloadProgress(
    val stage: PreloadStage,
    val audioPercent: Int = 0,
    val thumbnailPercent: Int = 0,
    val currentSegment: Int = 0,
    val totalSegments: Int = 0,
    val logs: List<String> = emptyList(),
    val preloadedStrips: Map<Int, Bitmap> = emptyMap(),
    val audioAmplitudesCount: Int = 0,
    val audioAmplitudesTotal: Int = 0,
    val thumbnailsExtracted: Int = 0,
    val thumbnailsTotal: Int = 0
)

enum class PreloadStage {
    Starting,
    Validating,
    ExtractingAudio,
    ExtractingThumbnails,
    Ready
}

// --- Merged from PreloadViewModel.kt ---



/**
 * ViewModel coordenadora para preparação de vídeo.
 * 
 * Responsabilidades:
 * - Configurar ThumbnailViewModel com metadados do vídeo
 * - Liberação de acesso ao editor (apenas após obter metadados)
 * - Gerenciar estado geral (Loading/Ready/Error)
 * - Fornecer métodos para verificar se o vídeo está pronto
 * 
 * Escopo: Activity (compartilhada entre HomeScreen e EditorScreen)
 * 
 * Estratégia On-Demand:
 * - Thumbnails são carregadas apenas quando o usuário rola a timeline
 * - Preload está DESATIVADO para maximizar performance de abertura
 * - ThumbnailViewModel.handleOnDemand() carrega strips conforme necessário
 * - AudioViewModel não é usado atualmente (áudio carregado sob demanda no EditorScreen)
 */
class PreloadViewModel(
    application: Application,
    private val thumbnailVM: ThumbnailViewModel,
    private val audioVM: AudioViewModel
) : AndroidViewModel(application) {
    
    // ========== ESTADO ==========

    private val _uiState = MutableStateFlow<PreloadUiState>(PreloadUiState.Idle)
    val uiState: StateFlow<PreloadUiState> = _uiState.asStateFlow()

    // StateFlow reativo para isReady — libera assim que o número de segmentos é conhecido
    // (logo após obter os metadados do vídeo, antes de qualquer extração)
    val isReadyFlow: StateFlow<Boolean> = thumbnailVM.totalSegments
        .map { total -> total > 0 }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    // ========== JOBS ==========
    
    private var activeUri: Uri? = null
    private var preloadJob: Job? = null
    private var thumbnailJob: Job? = null
    
    // ========== DEPENDÊNCIAS ==========

    // ========== MÉTODOS PÚBLICOS ==========
    
    /**
     * Inicia preparação de vídeo para o editor.
     * 
     * Estratégia On-Demand:
     * - Apenas obtém metadados do vídeo (duração, dimensões, segmentos)
     * - Configura ThumbnailViewModel para carregamento on-demand
     * - Thumbnails são carregadas apenas quando o usuário rola a timeline
     * - Áudio não é pré-carregado (carregado sob demanda no EditorScreen)
     * 
     * Benefícios:
     * - Abertura do editor é quase instantânea
     * - Menor uso de memória inicial
     * - Thumbnails carregadas apenas conforme necessário
     * 
     * @param uri URI do vídeo
     */
    fun startPreload(uri: Uri) {
        
        if (activeUri != null && activeUri != uri) {
            clear()
        }
        
        if (activeUri == uri && _uiState.value is PreloadUiState.Ready) {
            return
        }

        preloadJob?.cancel()
        thumbnailJob?.cancel()
        activeUri = uri
        
        preloadJob = viewModelScope.launch(DispatcherProvider.io) {
            try {
                _uiState.value = PreloadUiState.Loading(progress = PreloadProgress(
                    stage = PreloadStage.Starting,
                    audioPercent = 0,
                    thumbnailPercent = 0,
                    currentSegment = 0,
                    totalSegments = 0,
                    thumbnailsExtracted = 0,
                    thumbnailsTotal = 0
                ))
                
// Configurar ThumbnailViewModel (obter metadados, carregar strips on-demand)
                val thumbnailSetupJob = launch {
                    thumbnailVM.preload(uri)
                    thumbnailVM.uiState.first { it is ThumbnailViewModel.ThumbnailUiState.Ready }
                }

                // Áudio é pré-carregado em paralelo com thumbnails (background)
                // Isso reduce ~17s para ~2s quando o editor abre
                val audioJob = launch {
                    audioVM.loadWaveform(uri)
                }

                // Observar progresso de thumbnails para UI + áudio
                thumbnailJob = launch {
                    thumbnailVM.thumbnailProgress.collect { progress ->
                        _uiState.update { currentState ->
                             if (currentState is PreloadUiState.Loading) {
                                 // Verificar se áudio está pronto
                                 val audioPercent = if (audioVM.amplitudes.value.isNotEmpty()) 100 else 0
                                 currentState.copy(progress = currentState.progress.copy(
                                     stage = if (audioPercent < 100) PreloadStage.ExtractingAudio else PreloadStage.ExtractingThumbnails,
                                     audioPercent = audioPercent,
                                     thumbnailPercent = (progress * 100).toInt(),
                                     thumbnailsExtracted = thumbnailVM.strips.value.size,
                                     thumbnailsTotal = thumbnailVM.totalSegments.value
                                 ))
                             } else currentState
                        }
                    }
                }

                // AGUARDAR: Liberamos o acesso assim que o thumbnailSetupJob terminar (apenas metadados)
                // Thumbnails são carregadas on-demand quando o usuário rola a timeline
                thumbnailSetupJob.join()

                // AGUARDAR áudio também (até 5s ou até estar pronto)
                val audioAmplitudes = withTimeoutOrNull(5000) {
                    audioVM.amplitudes.first { it.isNotEmpty() }
                }

                // Áudio carregado está em audioVM.amplitudes
                val amplitudesList = audioAmplitudes?.toList() ?: emptyList()

                // Áudio carregado está em audioVM.amplitudes
                // Marcar como Ready
                val preloadedData = PreloadedData(
                    videoInfo = VideoInfo(
                        uri = uri,
                        fileName = "video.mp4",
                        mimeType = "video/mp4",
                        durationUs = 0,
                        width = 0,
                        height = 0,
                        rotation = 0,
                        bitrate = 0,
                        frameRate = 30,
                        videoCodec = null,
                        audioCodec = null,
                        hasAudio = true,
                        sizeBytes = 0
                    ),
                    audioAmplitudes = amplitudesList,
                    preloadedStrips = emptyMap(),
                    totalSegments = thumbnailVM.totalSegments.value,
                    preloadPercentage = 1f
                )
                _uiState.value = PreloadUiState.Ready(preloadedData)
                
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _uiState.value = PreloadUiState.Error(e.message ?: "Erro desconhecido")
                }
            }
        }
    }
    
    /**
     * Cancela a preparação do vídeo em andamento.
     */
    fun cancelPreload() {
        
        preloadJob?.cancel()
        preloadJob = null
        thumbnailJob?.cancel()
        thumbnailJob = null
    }
    
    /**
     * Limpa todo o estado de preparação do vídeo.
     */
    fun clear() {
        
        cancelPreload()
        activeUri = null
        _uiState.value = PreloadUiState.Idle
        thumbnailVM.clear()
        audioVM.clear()
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    /**
     * Calcula o número de barras de waveform.
     */
    private fun calculateTargetBarCount(durationMs: Long): Int {
        return when {
            durationMs < 60000 -> 100
            durationMs < 300000 -> 300
            else -> 600
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        clear()
    }
    
    // ========== CLASSES DE ESTADO ==========
    
    // PreloadUiState, PreloadProgress, PreloadStage já existem em PreloadUiState.kt
    // PreloadedData pode ser mantida para compatibilidade ou removida
    
    // ========== FACTORY ==========
    
    class PreloadViewModelFactory(
        private val application: Application,
        private val thumbnailVM: ThumbnailViewModel,
        private val audioVM: AudioViewModel
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PreloadViewModel::class.java)) {
                return PreloadViewModel(application, thumbnailVM, audioVM) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
        }
    }
}
