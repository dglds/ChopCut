package com.chopcut

import android.net.Uri
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chopcut.data.local.PreferencesManager
import com.chopcut.ui.onboarding.OnboardingScreen
import com.chopcut.ui.screen.HomeScreen
import com.chopcut.ui.screen.PreferencesScreen
import com.chopcut.ui.screen.TrimEditionScreen
import com.chopcut.ui.screen.debug.AudioWaveFormsTestScreen
import com.chopcut.ui.theme.ChopCutTheme

/**
 * Main activity for ChopCut app
 *
 * Navigation structure:
 * - "onboarding" -> Onboarding screen (first run only)
 * - "home" -> Home screen (start destination)
 * - "editor?videoUri={videoUri}" -> Video editor screen
 */
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

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        // ==================== ONBOARDING SCREEN ====================
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

                        // ==================== HOME SCREEN ====================
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
                                }
                            )
                        }

                        // ==================== AUDIO WAVEFORMS TEST SCREEN ====================
                        composable("audio_waveforms_test") {
                            AudioWaveFormsTestScreen()
                        }

                        // ==================== PREFERENCES SCREEN ====================
                        composable("preferences") {
                            PreferencesScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // ==================== EDITOR SCREEN ====================
                        composable(
                            route = "editor?videoUri={videoUri}",
                            arguments = listOf(
                                navArgument("videoUri") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val videoUriString = backStackEntry.arguments?.getString("videoUri")

                            val videoUri = videoUriString?.let { Uri.parse(it) }

                            TrimEditionScreen(
                                videoUri = videoUri ?: Uri.EMPTY,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
