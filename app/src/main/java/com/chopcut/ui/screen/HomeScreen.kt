package com.chopcut.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.core.designsystem.atoms.BodyText
import com.chopcut.core.designsystem.atoms.HeadlineText
import com.chopcut.core.designsystem.atoms.SmallText
import com.chopcut.core.designsystem.organisms.DefaultFeatures
import com.chopcut.core.designsystem.organisms.ErrorState
import com.chopcut.core.designsystem.organisms.FeatureList
import com.chopcut.core.designsystem.organisms.TopBarAction
import com.chopcut.core.designsystem.organisms.VideoSelector
import com.chopcut.core.designsystem.organisms.VideoSelectorLoading
import com.chopcut.core.designsystem.organisms.VideoSelectorWithVideo
import com.chopcut.core.designsystem.templates.LazyScreenTemplate
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Home screen - Main entry point for video editing
 * Refatorada para usar o Design System
 *
 * @param viewModel HomeViewModel
 * @param onNavigateToEditor Callback to navigate to editor screen
 * @param onNavigateToSettings Callback to navigate to settings screen
 * @param onNavigateToTests Callback to navigate to tests screen
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToEditor: (android.net.Uri) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTests: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()

    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectVideo(it) }
    }

    LazyScreenTemplate(
        title = "ChopCut",
        actions = {
            TopBarAction(
                icon = Icons.Default.Info,
                contentDescription = "Testes",
                onClick = onNavigateToTests
            )
            TopBarAction(
                icon = Icons.Default.Settings,
                contentDescription = "Configurações",
                onClick = onNavigateToSettings
            )
        }
    ) {
        // Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = SpacingTokens.xxl)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(SpacingTokens.iconHero),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(SpacingTokens.lg))
                HeadlineText(
                    text = "ChopCut",
                    fontWeight = FontWeight.Bold
                )
                BodyText(
                    text = "Editor de Vídeo Android",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Video Selector Section
        item {
            when {
                selectedUri == null -> {
                    VideoSelector(
                        onSelectVideo = { videoPickerLauncher.launch("video/*") },
                        title = "Começar a Editar",
                        subtitle = "Selecione um vídeo do seu dispositivo"
                    )
                }
                uiState is HomeUiState.Loading -> {
                    VideoSelectorLoading(
                        message = "Carregando informações do vídeo..."
                    )
                }
                uiState is HomeUiState.VideoLoaded -> {
                    val videoInfo = (uiState as HomeUiState.VideoLoaded).videoInfo
                    VideoSelectorWithVideo(
                        videoInfo = videoInfo,
                        onSelectVideo = { videoPickerLauncher.launch("video/*") },
                        onOpenEditor = { onNavigateToEditor(selectedUri!!) }
                    )
                }
                else -> {
                    // Fallback quando tem URI mas não tem info carregada
                    VideoSelector(
                        onSelectVideo = { videoPickerLauncher.launch("video/*") },
                        title = "Vídeo Selecionado",
                        subtitle = "Carregando informações..."
                    )
                }
            }
        }

        // Features Section
        item {
            FeatureList(
                features = DefaultFeatures.all,
                title = "Recursos"
            )
        }

        // Error State
        if (uiState is HomeUiState.Error) {
            item {
                ErrorState(
                    message = (uiState as HomeUiState.Error).message,
                    onDismiss = { viewModel.resetState() }
                )
            }
        }
    }
}
