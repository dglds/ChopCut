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
import com.chopcut.ui.screen.DevelopScreen
import com.chopcut.ui.screen.HomeScreen
import com.chopcut.ui.screen.TrimEditionScreen
import com.chopcut.ui.screen.ProjectsScreen
import com.chopcut.ui.screen.SettingsScreen
import com.chopcut.ui.theme.ChopCutTheme

/**
 * Main activity for ChopCut app
 *
 * Navigation structure:
 * - "onboarding" -> Onboarding screen (first run only)
 * - "projects" -> Projects list screen (start destination)
 * - "home" -> Home screen (legacy/test)
 * - "editor?videoUri={videoUri}&projectId={projectId}" -> Video editor screen
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
                    val context = LocalContext.current
                    val preferencesManager = remember { PreferencesManager(context) }
                    val startDestination = if (preferencesManager.isFirstRun) "onboarding" else "projects"
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
                                    navController.navigate("projects") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ==================== PROJECTS SCREEN ====================
                        composable("projects") {
                            ProjectsScreen(
                                onNavigateToEditor = { projectId, videoUri ->
                                    val route = if (projectId != null) {
                                        "editor?projectId=$projectId"
                                    } else {
                                        val encodedUri = java.net.URLEncoder.encode(videoUri.toString(), "UTF-8")
                                        "editor?videoUri=$encodedUri"
                                    }
                                    navController.navigate(route)
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
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
                            route = "editor?videoUri={videoUri}&projectId={projectId}",
                            arguments = listOf(
                                navArgument("videoUri") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                                navArgument("projectId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val videoUriString = backStackEntry.arguments?.getString("videoUri")
                            val projectId = backStackEntry.arguments?.getString("projectId")
                            
                            val decodedUri = videoUriString?.let {
                                java.net.URLDecoder.decode(it, "UTF-8")
                            }
                            val videoUri = decodedUri?.let { Uri.parse(it) }

                            TrimEditionScreen(
                                videoUri = videoUri ?: Uri.EMPTY,
                                projectId = projectId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        // ==================== SETTINGS SCREEN ====================
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToDevelop = {
                                    navController.navigate("develop")
                                }
                            )
                        }
                        
                        // ==================== DEVELOP SCREEN (DEBUG) ====================
                        composable("develop") {
                            DevelopScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                    }
                }
            }
        }
    }
}