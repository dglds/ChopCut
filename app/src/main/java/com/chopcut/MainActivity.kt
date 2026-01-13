package com.chopcut

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chopcut.ui.screen.EditorScreen
import com.chopcut.ui.screen.HomeScreen
import com.chopcut.ui.screen.SettingsScreen
import com.chopcut.ui.screen.TestScreen
import com.chopcut.ui.theme.ChopCutTheme

/**
 * Main activity for ChopCut app
 *
 * Navigation structure:
 * - "home" -> Home screen (main entry point)
 * - "editor/{videoUri}" -> Video editor screen
 * - "settings" -> Settings screen
 * - "tests" -> Test screen with all test operations
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
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        // ==================== HOME SCREEN ====================
                        composable("home") {
                            HomeScreen(
                                onNavigateToEditor = { videoUri ->
                                    val encodedUri = java.net.URLEncoder.encode(videoUri.toString(), "UTF-8")
                                    navController.navigate("editor/$encodedUri")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToTests = {
                                    navController.navigate("tests")
                                }
                            )
                        }

                        // ==================== EDITOR SCREEN ====================
                        composable(
                            route = "editor/{videoUri}",
                            arguments = listOf(
                                navArgument("videoUri") {
                                    type = NavType.StringType
                                }
                            )
                        ) { backStackEntry ->
                            val videoUriString = backStackEntry.arguments?.getString("videoUri")
                            val decodedUri = videoUriString?.let {
                                java.net.URLDecoder.decode(it, "UTF-8")
                            }
                            val videoUri = decodedUri?.let { Uri.parse(it) }

                            if (videoUri != null) {
                                EditorScreen(
                                    videoUri = videoUri,
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    },
                                    onExportComplete = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }

                        // ==================== SETTINGS SCREEN ====================
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // ==================== TEST SCREEN ====================
                        composable("tests") {
                            TestScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToEditor = { videoUri ->
                                    val encodedUri = java.net.URLEncoder.encode(videoUri.toString(), "UTF-8")
                                    navController.navigate("editor/$encodedUri")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
