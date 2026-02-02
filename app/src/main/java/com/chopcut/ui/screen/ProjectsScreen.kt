package com.chopcut.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.data.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel = viewModel(),
    onNavigateToEditor: (String?, android.net.Uri?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTimelineComparison: ((android.net.Uri?) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Video picker launcher for new project
    val videoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { onNavigateToEditor(null, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ChopCut") },
                actions = {
                    // Botão de debug para comparar timelines (só aparece se callback fornecido)
                    if (onNavigateToTimelineComparison != null) {
                        IconButton(onClick = { 
                            onNavigateToTimelineComparison(null) 
                        }) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Comparar Timelines (Debug)"
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { videoPickerLauncher.launch("video/*") }) {
                Icon(Icons.Default.Add, contentDescription = "Novo Projeto")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ProjectsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ProjectsUiState.Error -> {
                    Text(
                        text = "Erro: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ProjectsUiState.Success -> {
                    if (state.projects.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Nenhum projeto ainda",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Clique no + para começar",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.projects) { project ->
                                ProjectItem(
                                    project = project,
                                    onClick = { onNavigateToEditor(project.id, null) },
                                    onDelete = { viewModel.deleteProject(project) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectItem(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val thumbnailBitmap = remember(project.thumbnail) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(project.thumbnail) {
        if (project.thumbnail != null) {
            withContext(Dispatchers.IO) {
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(project.thumbnail)
                    thumbnailBitmap.value = bitmap
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load project thumbnail")
                }
            }
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail Background
            if (thumbnailBitmap.value != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnailBitmap.value!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎬", style = MaterialTheme.typography.displayMedium)
                }
            }

            // Menu Button (Top Right, Semi-transparent)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            ) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Excluir") },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }

            // Bottom Info (Gradient overlay)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatDate(project.modifiedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = formatDuration(project.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
}