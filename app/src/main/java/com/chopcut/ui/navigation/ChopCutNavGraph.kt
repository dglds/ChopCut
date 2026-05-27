package com.chopcut

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

@Composable
fun ChopCutNavGraph(
    navController: NavHostController,
    startDestination: String,
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
                    }
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
        }
    }
}
