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
    startDestination: String
) {
    val navFadeIn = fadeIn(
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    ) + scaleIn(
        initialScale = 0.95f,
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    )
    val navFadeOut = fadeOut(
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = tween(400, easing = FastOutSlowInEasing)
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
                    onNavigateToEditor = { route ->
                        navController.navigate(route)
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

                TimelineScreen(
                    videoUri = videoUri,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
