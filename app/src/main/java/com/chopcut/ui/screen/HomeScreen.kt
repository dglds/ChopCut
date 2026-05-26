package com.chopcut.ui.screen

import android.app.Application
import android.net.Uri
import com.chopcut.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.repository.VideoRepository
import com.chopcut.ui.components.atoms.formatDuration
import com.chopcut.ui.components.buttons.ChopCutPrimaryButton
import com.chopcut.ui.components.buttons.ChopCutSecondaryButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.RectangleShape
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.components.gallery.BottomSheetGallery
import com.chopcut.ui.theme.Border
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.ui.theme.DurationTextStyle
import com.chopcut.ui.theme.Info
import com.chopcut.ui.theme.OnPrimary
import com.chopcut.ui.theme.OnSurface
import com.chopcut.ui.theme.OverlayDark
import com.chopcut.ui.theme.Primary
import com.chopcut.ui.theme.Success
import com.chopcut.ui.theme.Surface
import com.chopcut.ui.theme.SurfaceVariant
import com.chopcut.ui.theme.TextSecondary
import com.chopcut.ui.theme.Warning
import com.chopcut.ui.theme.Waveform
import com.chopcut.ui.viewmodel.HomeViewModel
import com.chopcut.ui.viewmodel.HomeViewModelFactory
import com.chopcut.ui.viewmodel.PreloadViewModel
import com.chopcut.ui.viewmodel.HomeUiState
import com.chopcut.ui.viewmodel.ClearCacheState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    preloadViewModel: PreloadViewModel,
    onNavigateToEditor: (Uri) -> Unit = {},
    onNavigateToRecyclerEditor: (Uri) -> Unit = {},
    onNavigateToPreferences: () -> Unit = {}
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
                title = { Text("ChopCut") },
                actions = {
                    IconButton(onClick = onNavigateToPreferences) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Preferências"
                        )
                    }
                }
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

                            VideoPickerLoaded(
                                videoInfo = (uiState as HomeUiState.VideoLoaded).videoInfo,
                                videoUri = uri,
                                isPreloading = isLoading,
                                onChangeVideo = requestGallery,
                                onOpenEditor = {
                                    onNavigateToEditor(uri)
                                },
                                onOpenRecyclerEditor = {
                                    onNavigateToRecyclerEditor(uri)
                                },
                                onRemoveVideo = {
                                    preloadViewModel.clear()
                                    viewModel.clearSelectedVideo()
                                }
                            )
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
    onOpenRecyclerEditor: () -> Unit = {},
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
            BadgeText(text = com.chopcut.util.FormatUtils.getAspectRatio(videoInfo.width, videoInfo.height))
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
                        .width(44.dp)
                        .background(
                            Color(0xFF00E5FF).copy(alpha = 0.3f),
                            RectangleShape
                        )
                        .clickable(onClick = onOpenRecyclerEditor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RV",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
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