package com.chopcut.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.chopcut.BuildConfig
import com.chopcut.data.local.PreferencesManager
import com.chopcut.ui.components.feedback.DebugPosition
import com.chopcut.ui.components.feedback.DebugState
import com.chopcut.ui.components.feedback.DebugToast
import com.chopcut.ui.components.feedback.DebugViewModel
import com.chopcut.ui.components.loading.LoadingConstants
import com.chopcut.ui.onboarding.OnboardingScreen
import com.chopcut.ui.viewmodel.AudioViewModel
import com.chopcut.ui.screen.HomeScreen
import com.chopcut.ui.screen.PreferencesScreen
import com.chopcut.ui.viewmodel.PreloadViewModel
import com.chopcut.ui.viewmodel.ThumbnailViewModel
import com.chopcut.ui.screen.EditorScreen
import com.chopcut.ui.screen.RecyclerEditorScreen

@Composable
fun ChopCutNavGraph(
    navController: NavHostController,
    startDestination: String,
    preferencesManager: PreferencesManager,
    debugViewModel: DebugViewModel,
    preloadViewModel: PreloadViewModel,
    thumbnailViewModel: ThumbnailViewModel,
    audioViewModel: AudioViewModel
) {
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
                    onNavigateToRecyclerEditor = { videoUri ->
                        val encodedUri = java.net.URLEncoder.encode(videoUri.toString(), "UTF-8")
                        navController.navigate("editor-recycler?videoUri=$encodedUri")
                    },
                    onNavigateToPreferences = {
                        navController.navigate("preferences")
                    }
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

                EditorScreen(
                    videoUri = videoUri ?: Uri.EMPTY,
                    preloadViewModel = preloadViewModel,
                    thumbnailViewModel = thumbnailViewModel,
                    audioViewModel = audioViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = "editor-recycler?videoUri={videoUri}",
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

                RecyclerEditorScreen(
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
                    DebugPosition.TOP -> Alignment.TopEnd
                    DebugPosition.BOTTOM -> Alignment.BottomEnd
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
