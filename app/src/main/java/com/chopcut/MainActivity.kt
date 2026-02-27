package com.chopcut

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chopcut.data.local.PreferencesManager
import com.chopcut.ui.components.feedback.DebugState
import com.chopcut.ui.components.feedback.DebugToast
import com.chopcut.ui.components.feedback.DebugViewModel
import com.chopcut.ui.onboarding.OnboardingScreen
import com.chopcut.ui.screen.HomeScreen
import com.chopcut.ui.screen.PreloadDataStore
import com.chopcut.ui.screen.PreferencesScreen
import com.chopcut.ui.screen.TrimScreen
import com.chopcut.ui.screen.debug.AudioWaveFormsTestScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.chopcut.ui.theme.ChopCutTheme
import com.chopcut.BuildConfig

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
                    val startDestination = if (preferencesManager.isFirstRun) "onboarding" else "home"
                    val navController = rememberNavController()

                    val debugViewModel: DebugViewModel = viewModel()

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable("onboarding") {
                                OnboardingScreen(
                                    onFinish = {
                                        preferencesManager.isFirstRun = false
                                        navController.navigate("home") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable("home") {
                                HomeScreen(
                                    onNavigateToEditor = { videoUri ->
                                        val encodedUri = java.net.URLEncoder.encode(videoUri.toString(), "UTF-8")
                                        navController.navigate("editor?videoUri=$encodedUri")
                                    },
                                    onNavigateToPreferences = {
                                        navController.navigate("preferences")
                                    },
                                    onNavigateToTests = {
                                        navController.navigate("audio_waveforms_test")
                                    },
                                    debugViewModel = debugViewModel
                                )
                            }

                            composable("audio_waveforms_test") {
                                AudioWaveFormsTestScreen(
                                    debugViewModel = debugViewModel
                                )
                            }

                            composable("preferences") {
                                PreferencesScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "editor?videoUri={videoUri}",
                                arguments = listOf(
                                    navArgument("videoUri") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                ),
                                enterTransition = { EnterTransition.None },
                                exitTransition = { ExitTransition.None },
                                popEnterTransition = { EnterTransition.None },
                                popExitTransition = { ExitTransition.None }
                            ) { backStackEntry ->
                                val videoUriString = backStackEntry.arguments?.getString("videoUri")
                                val videoUri = videoUriString?.let { Uri.parse(it) }
                                val preloadedData = PreloadDataStore.getData()

                                TrimScreen(
                                    videoUri = videoUri ?: Uri.EMPTY,
                                    preloadedData = preloadedData,
                                    onNavigateBack = {
                                        PreloadDataStore.clearData()
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }

                        if (BuildConfig.DEBUG) {
                            val debugState = debugViewModel.debugState.collectAsState()
                            
                            if (debugState.value is DebugState.Active) {
                                DebugToast(
                                    entries = (debugState.value as DebugState.Active).entries,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .navigationBarsPadding()
                                        .padding(12.dp),
                                    onClose = { debugViewModel.clear() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}