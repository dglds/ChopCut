package com.chopcut

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.chopcut.data.local.PreferencesManager
import com.chopcut.R
import com.chopcut.ui.components.feedback.DebugViewModel
// import com.chopcut.ui.navigation.ChopCutNavGraph // Temporarily commented out for TimelineV3 testing
import com.chopcut.ui.screen.AudioViewModel
import com.chopcut.ui.screen.PreloadViewModel
import com.chopcut.ui.screen.ThumbnailViewModel
import com.chopcut.ui.theme.ChopCutTheme

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import android.net.Uri
import com.chopcut.ui.components.timeline.TimelineV3 // Import TimelineV3

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            ChopCutTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val preferencesManager = remember { PreferencesManager(context) }
                    val navController = rememberNavController()

                    val debugViewModel: DebugViewModel = viewModel()
                    val application = remember { context.applicationContext as Application }
                    val thumbnailViewModel: ThumbnailViewModel = viewModel(
                        factory = ThumbnailViewModel.ThumbnailViewModelFactory(application)
                    )
                    val audioViewModel: AudioViewModel = viewModel(
                        factory = AudioViewModel.AudioViewModelFactory(application)
                    )
                    val preloadViewModel: PreloadViewModel = viewModel(
                        factory = PreloadViewModel.PreloadViewModelFactory(
                            application,
                            thumbnailViewModel,
                            audioViewModel
                        )
                    )

                    // === TimelineV3 Testing Setup ===
                    var selectedVideoUri by remember { 
                        mutableStateOf<Uri>(Uri.parse("android.resource://${context.packageName}/${R.raw.sample_video}")) 
                    }

                    val exoPlayer = remember {
                        ExoPlayer.Builder(context).build().apply {
                            prepare()
                            playWhenReady = true
                        }
                    }

                    val pickVideoLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            selectedVideoUri = uri
                            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    }

                    // Initial media item setup
                    LaunchedEffect(Unit) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(selectedVideoUri))
                        exoPlayer.prepare()
                    }

                    DisposableEffect(key1 = exoPlayer) {
                        onDispose {
                            exoPlayer.release()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        // Video Picker Button
                        Button(
                            onClick = {
                                pickVideoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text("Selecionar Vídeo da Galeria")
                        }

                        TimelineV3(
                            modifier = Modifier.weight(1f),
                            exoPlayer = exoPlayer,
                            videoUri = selectedVideoUri
                        )
                    }
                    // === End TimelineV3 Testing Setup ===

                    /* Temporarily disabled for TimelineV3 testing
                    ChopCutNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        preferencesManager = preferencesManager,
                        debugViewModel = debugViewModel,
                        preloadViewModel = preloadViewModel,
                        thumbnailViewModel = thumbnailViewModel,
                        audioViewModel = audioViewModel
                    )
                    */
                }
            }
        }
    }
}
