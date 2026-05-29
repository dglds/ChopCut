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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt


// --- Merged from HomeScreen.kt ---




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEditor: (String) -> Unit = {}
) {
    val application = LocalContext.current.applicationContext as Application
    val videoRepository = remember { VideoRepository(application) }
    val factory = remember { HomeViewModelFactory(application, videoRepository) }
    val viewModel: HomeViewModel = viewModel(factory = factory)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showExtractionProgress by remember { mutableStateOf(false) }
    val extractionProgress by viewModel.extractionProgress.collectAsStateWithLifecycle()

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

    val hasCuts = selectedUri?.let { AppliedCutsRegistry.hasCuts(it) } ?: false
    val features = remember(selectedUri, hasCuts) {
        listOf(
            HomeFeatureItem(
                title = "Recortar Vídeo",
                description = "Corte trechos com precisão cirúrgica de frames",
                icon = Icons.Default.ContentCut,
                showDot = hasCuts,
                onClick = {
                    val uri = selectedUri
                    if (uri != null) {
                        val encodedUri = java.net.URLEncoder.encode(uri.toString(), "UTF-8")
                        onNavigateToEditor("editor?videoUri=$encodedUri")
                    } else {
                        requestGallery()
                    }
                }
            ),
            HomeFeatureItem(
                title = "Mesclar Clipes",
                description = "Combine múltiplos vídeos em um único arquivo",
                icon = Icons.AutoMirrored.Filled.CallMerge,
                onClick = {
                    android.widget.Toast.makeText(context, "Mesclagem de clipes disponível em breve!", android.widget.Toast.LENGTH_SHORT).show()
                }
            ),
            HomeFeatureItem(
                title = "Compactar",
                description = "Reduza o tamanho do vídeo preservando qualidade",
                icon = Icons.Default.Compress,
                onClick = {
                    android.widget.Toast.makeText(context, "Compactação de vídeo disponível em breve!", android.widget.Toast.LENGTH_SHORT).show()
                }
            ),
            HomeFeatureItem(
                title = "Extrair Áudio",
                description = "Salve a faixa de áudio em formato de alta fidelidade",
                icon = Icons.Default.MusicNote,
                onClick = {
                    android.widget.Toast.makeText(context, "Extração de áudio disponível em breve!", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        )
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
                            Column(verticalArrangement = Arrangement.spacedBy(ChopCutSpacing.sm)) {
                                VideoPickerLoaded(
                                    videoInfo = (uiState as HomeUiState.VideoLoaded).videoInfo,
                                    videoUri = uri,
                                    isPreloading = false,
                                    onChangeVideo = requestGallery,
                                    onOpenEditor = {
                                        val encodedUri = java.net.URLEncoder.encode(uri.toString(), "UTF-8")
                                        onNavigateToEditor("editor?videoUri=$encodedUri")
                                    },
                                    onRemoveVideo = {
                                        viewModel.clearSelectedVideo()
                                    }
                                )
                                val density = LocalDensity.current.density
                                val videoInfo = (uiState as HomeUiState.VideoLoaded).videoInfo
                                val (wDp, hDp) = ThumbnailConfig.TimelineThumbs.computeDp(videoInfo.width, videoInfo.height)
                                val extractW = (wDp * density).roundToInt()
                                val extractH = (hDp * density).roundToInt()
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
                                                extractionQuality = ThumbnailQuality.HIGH,
                                                explicitWidthPx = extractW,
                                                explicitHeightPx = extractH
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

                item {
                    Text(
                        text = "Ferramentas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        modifier = Modifier.padding(top = ChopCutSpacing.xs)
                    )
                }

                // Render feature items in rows of 2 (2 columns grid)
                items(features.chunked(2).size) { rowIndex ->
                    val rowItems = features.chunked(2)[rowIndex]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.sm)
                    ) {
                        rowItems.forEach { feature ->
                            FeatureCard(
                                feature = feature,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
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
            current.copy(
                isRunning = false,
                error = "Cancelada pelo usuário"
            )
        }
    }

    fun startExtraction(uri: Uri, settings: ThumbnailSettings) {
        val videoInfo = (uiState.value as? HomeUiState.VideoLoaded)?.videoInfo ?: return
        extractionJob?.cancel()
        extractionJob = viewModelScope.launch {
            try {
                ThumbnailExtraction(getApplication()).extract(uri, videoInfo, settings) { state ->
                    _extractionProgress.value = state
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            } catch (e: Exception) {
                _extractionProgress.update { it.copy(isRunning = false, error = e.message ?: "Erro desconhecido") }
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

}

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

data class HomeFeatureItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val showDot: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun FeatureCard(
    feature: HomeFeatureItem,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .border(1.dp, Divider, RectangleShape)
                .clickable(onClick = feature.onClick)
                .padding(ChopCutSpacing.md)
                .height(130.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Primary.copy(alpha = 0.1f), RectangleShape)
                    .border(1.dp, Primary.copy(alpha = 0.3f), RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }

        if (feature.showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(8.dp)
                    .background(Color(0xFF00E5FF), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            )
        }
    }
}



