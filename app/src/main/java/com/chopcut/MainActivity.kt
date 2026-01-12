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
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        // Home Screen (test screen)
                        composable("home") {
                            HomeScreen(
                                onNavigateToEditor = { videoUri ->
                                    // Encode URI to handle special characters
                                    val encodedUri = java.net.URLEncoder.encode(videoUri.toString(), "UTF-8")
                                    navController.navigate("editor/$encodedUri")
                                }
                            )
                        }

                        // Editor Screen
                        composable(
                            route = "editor/{videoUri}",
                            arguments = listOf(
                                navArgument("videoUri") {
                                    type = NavType.StringType
                                }
                            )
                        ) { backStackEntry ->
                            val videoUriString = backStackEntry.arguments?.getString("videoUri")
                            // Decode URI
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
                    }
                }
            }
        }
    }
}
