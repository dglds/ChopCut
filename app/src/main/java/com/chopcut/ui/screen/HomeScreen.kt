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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.chopcut.ui.components.feedback.DebugEntry
import com.chopcut.ui.components.feedback.DebugViewModel
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
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    preloadViewModel: PreloadViewModel,
    onNavigateToEditor: (Uri) -> Unit = {},
    onNavigateToTests: () -> Unit = {},
    onNavigateToPreferences: () -> Unit = {},
    debugViewModel: DebugViewModel? = null
) {
    val application = LocalContext.current.applicationContext as Application
    val videoRepository = remember { VideoRepository(application) }
    val factory = remember { HomeViewModelFactory(application, videoRepository) }
    val viewModel: HomeViewModel = viewModel(factory = factory)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()
    
    // Observar PreloadViewModel
    val preloadUiState by preloadViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showGallery by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            showGallery = true
        } else {
            Timber.w("Permission denied for gallery access")
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
                    
                    // Iniciar preload ao selecionar vídeo
                    LaunchedEffect(uri) {
                        if (uri != null && uiState is HomeUiState.VideoLoaded) {
                            Timber.d("Video loaded, starting preload with 6 strips")
                            preloadViewModel.startPreload(uri, stripsToPreload = 6)
                        }
                    }
                    
                    when {
                        uri != null && uiState is HomeUiState.VideoLoaded -> {
                            VideoPickerLoaded(
                                videoInfo = (uiState as HomeUiState.VideoLoaded).videoInfo,
                                videoUri = uri,
                                isPreloading = preloadUiState is PreloadUiState.Loading,
                                isReady = preloadViewModel.isPreloadReady(6),
                                onChangeVideo = requestGallery,
                                onOpenEditor = {
                                    // Só navegar se estiver pronto
                                    if (preloadViewModel.isPreloadReady(6)) {
                                        onNavigateToEditor(uri)
                                    }
                                },
                                onRemoveVideo = {
                                    preloadViewModel.cancelPreload()
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
                    FeatureGrid()
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
 
            // Debug logging de preload
            if (com.chopcut.BuildConfig.DEBUG && debugViewModel != null) {
                if (preloadUiState is PreloadUiState.Loading) {
                    val progress = (preloadUiState as PreloadUiState.Loading).progress
                    LaunchedEffect(progress.logs) {
                        progress.logs.forEach { log ->
                            debugViewModel.log(log)
                        }
                    }
                }
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
            .clip(RoundedCornerShape(16.dp))
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
                    RoundedCornerShape(16.dp)
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
                .background(Primary, RoundedCornerShape(12.dp)),
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
            .clip(RoundedCornerShape(16.dp))
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
    isPreloading: Boolean,
    isReady: Boolean,
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
            .clip(RoundedCornerShape(16.dp))
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
            BadgeText(text = formatAspectRatio(videoInfo.aspectRatio))
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
                        .background(
                            if (isReady) Primary else Color.Gray,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = isReady, onClick = onOpenEditor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPreloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = OnPrimary
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = OnPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Editar",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = OnPrimary
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(80.dp)
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
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
                            RoundedCornerShape(12.dp)
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

private data class FeatureInfo(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val accentColor: Color
)

@Composable
private fun FeatureGrid() {
        val features = remember {
            listOf(
                FeatureInfo(Icons.Default.ContentCut, "Trim", "Cortar trechos", Primary),
                FeatureInfo(Icons.AutoMirrored.Filled.CallMerge, "Join", "Concatenar vídeos", Waveform),
                FeatureInfo(Icons.Default.Compress, "Compress", "Reduzir tamanho", Warning),
                FeatureInfo(Icons.Default.AspectRatio, "Resize", "Alterar resolução", Info),
                FeatureInfo(Icons.Default.Crop, "Crop", "Recortar área", Success),
                FeatureInfo(Icons.Default.MusicNote, "Áudio", "Extrair trilha sonora", Color(0xFFEC4899))
            )
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(ChopCutSpacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recursos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Text(
                text = "${features.size} disponíveis",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        features.chunked(2).forEach { rowFeatures ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.xs)
            ) {
                rowFeatures.forEach { feature ->
                    FeatureCard(
                        feature = feature,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowFeatures.size < 2) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    feature: FeatureInfo,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable { } // TODO: Implement feature navigation
            .padding(ChopCutSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    feature.accentColor.copy(alpha = 0.1f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = feature.accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(ChopCutSpacing.xs))
        Text(
            text = feature.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = OnSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = feature.description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BadgeText(text: String) {
    Text(
        text = text,
        style = DurationTextStyle.copy(color = Color.White),
        modifier = Modifier
            .background(
                Color.Black.copy(alpha = 0.6f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private fun formatAspectRatio(ratio: Float): String {
    return when {
        (ratio - 16f / 9f).let { kotlin.math.abs(it) } < 0.01f -> "16:9"
        (ratio - 9f / 16f).let { kotlin.math.abs(it) } < 0.01f -> "9:16"
        (ratio - 4f / 3f).let { kotlin.math.abs(it) } < 0.01f -> "4:3"
        (ratio - 1f).let { kotlin.math.abs(it) } < 0.01f -> "1:1"
        else -> "%.2f".format(ratio)
    }
}