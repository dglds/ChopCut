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
import com.chopcut.ui.components.feedback.DebugViewModel
import com.chopcut.ui.navigation.ChopCutNavGraph
import com.chopcut.ui.screen.AudioViewModel
import com.chopcut.ui.screen.PreloadViewModel
import com.chopcut.ui.screen.ThumbnailViewModel
import com.chopcut.ui.theme.ChopCutTheme

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

                    val startDestination = if (preferencesManager.isFirstRun) "onboarding" else "home"

                    ChopCutNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        preferencesManager = preferencesManager,
                        debugViewModel = debugViewModel,
                        preloadViewModel = preloadViewModel,
                        thumbnailViewModel = thumbnailViewModel,
                        audioViewModel = audioViewModel
                    )
                }
            }
        }
    }
}
