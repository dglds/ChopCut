package com.chopcut

import android.app.Application
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
import com.chopcut.ui.screen.PreferencesScreen
import com.chopcut.ui.screen.TrimScreen
import com.chopcut.ui.screen.PreloadViewModel
import com.chopcut.ui.screen.ThumbnailViewModel
import com.chopcut.ui.screen.AudioViewModel
import com.chopcut.ui.screen.debug.AudioWaveFormsTestScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.chopcut.ui.theme.ChopCutTheme
import com.chopcut.BuildConfig
import com.chopcut.ui.components.loading.LoadingConstants

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NOTA: ThumbnailCacheManager é inicializado no ChopCutApplication.initSync()

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

                    // ViewModels especializadas no escopo da Activity
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

                    // Transições de fade suaves e sincronizadas com LoadingOverlay
                    val navFadeIn = fadeIn(
                        animationSpec = tween(LoadingConstants.NAV_FADE_IN_DURATION_MS, easing = FastOutSlowInEasing)
                    ) + scaleIn(
                        initialScale = LoadingConstants.NAV_SCALE_START,
                        animationSpec = tween(LoadingConstants.NAV_FADE_IN_DURATION_MS, easing = FastOutSlowInEasing)
                    )
                    val navFadeOut = fadeOut(
                        animationSpec = tween(LoadingConstants.NAV_FADE_OUT_DURATION_MS, easing = FastOutSlowInEasing)
                    ) + scaleOut(
                        targetScale = LoadingConstants.NAV_SCALE_START,
                        animationSpec = tween(LoadingConstants.NAV_FADE_OUT_DURATION_MS, easing = FastOutSlowInEasing)
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable(
                                route = "onboarding",
                                enterTransition = { navFadeIn },
                                exitTransition = { navFadeOut }
                            ) {
                                OnboardingScreen(
                                    onFinish = {
                                        preferencesManager.isFirstRun = false
                                        navController.navigate("home") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(
                                route = "home",
                                enterTransition = { navFadeIn },
                                exitTransition = { navFadeOut },
                                popEnterTransition = { navFadeIn },
                                popExitTransition = { navFadeOut }
                            ) {
                                HomeScreen(
                                    preloadViewModel = preloadViewModel,
                                    onNavigateToEditor = { videoUri ->
                                        val encodedUri = java.net.URLEncoder.encode(videoUri.toString(), "UTF-8")
                                        navController.navigate("editor?videoUri=$encodedUri")
                                    },
                                    onNavigateToPreferences = {
                                        navController.navigate("preferences")
                                    },
                                    onNavigateToTests = {
                                        navController.navigate("audio_waveforms_test")
                                    }
                                )
                            }

                            composable(
                                route = "audio_waveforms_test",
                                enterTransition = { navFadeIn },
                                exitTransition = { navFadeOut },
                                popEnterTransition = { navFadeIn },
                                popExitTransition = { navFadeOut }
                            ) {
                                AudioWaveFormsTestScreen(
                                    debugViewModel = debugViewModel
                                )
                            }

                            composable(
                                route = "preferences",
                                enterTransition = { navFadeIn },
                                exitTransition = { navFadeOut },
                                popEnterTransition = { navFadeIn },
                                popExitTransition = { navFadeOut }
                            ) {
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
                                enterTransition = { navFadeIn },
                                exitTransition = { navFadeOut },
                                popEnterTransition = { navFadeIn },
                                popExitTransition = { navFadeOut }
                            ) { backStackEntry ->
                                val videoUriString = backStackEntry.arguments?.getString("videoUri")
                                val videoUri = videoUriString?.let { Uri.parse(it) }

                                TrimScreen(
                                    videoUri = videoUri ?: Uri.EMPTY,
                                    preloadViewModel = preloadViewModel,
                                    thumbnailViewModel = thumbnailViewModel,
                                    audioViewModel = audioViewModel,
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }

                        if (BuildConfig.DEBUG) {
                            val debugState = debugViewModel.debugState.collectAsState()
                            val debugPosition = debugViewModel.position.collectAsState()

                            if (debugState.value is DebugState.Active) {
                                val alignment = when (debugPosition.value) {
                                    com.chopcut.ui.components.feedback.DebugPosition.TOP -> Alignment.TopEnd
                                    com.chopcut.ui.components.feedback.DebugPosition.BOTTOM -> Alignment.BottomEnd
                                }

                                DebugToast(
                                    entries = (debugState.value as DebugState.Active).entries,
                                    modifier = Modifier
                                        .align(alignment)
                                        .navigationBarsPadding()
                                        .padding(12.dp),
                                    onClose = { debugViewModel.clear() },
                                    onTogglePosition = { debugViewModel.togglePosition() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}