package com.chopcut

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import com.chopcut.ui.viewmodel.AudioViewModel
import com.chopcut.ui.viewmodel.PreloadViewModel
import com.chopcut.ui.viewmodel.ThumbnailViewModel
import com.chopcut.ui.theme.ChopCutTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        Toast.makeText(this, "ChopCut v${BuildConfig.VERSION_NAME}", Toast.LENGTH_SHORT).show()
        Timber.tag("ChopCutVersion").i("App Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Timber.tag("MainActivity").d("MainActivity: onCreate called. App launching.")
        setContent {
            val context = LocalContext.current
            val preferencesManager = remember { PreferencesManager(context) }
            
            val themeMode by preferencesManager.themeModeFlow.collectAsState()
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                1 -> false // Light
                2 -> true  // Dark
                else -> isSystemDark // System (0)
            }

            ChopCutTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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

                    // Suporte a ACTION_VIEW: abre o editor diretamente quando
                    // lançado com uma URI de vídeo (ex: pelo script de captura Perfetto)
                    val intentVideoUri: Uri? = intent
                        ?.takeIf { it.action == android.content.Intent.ACTION_VIEW }
                        ?.data

                    val startDestination = when {
                        intentVideoUri != null -> {
                            val encoded = Uri.encode(intentVideoUri.toString())
                            "editor?videoUri=$encoded"
                        }
                        else -> "home"
                    }

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
