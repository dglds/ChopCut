package com.chopcut.ui.timeline.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chopcut.ui.timeline.util.DemoRecordingManager
import com.chopcut.ui.timeline.util.ScreenRecorder
import timber.log.Timber
import java.io.File

/**
 * Overlay de controle de gravação para demonstrações.
 * 
 * Pode ser adicionado ao NovoEditorScreen para permitir gravação
 * manual de demonstrações de fluxos.
 * 
 * Uso:
 * ```kotlin
 * NovoEditorScreen(...) {
 *     RecordingOverlay(
 *         isVisible = showRecordingControls,
 *         onRecordingComplete = { file -> /* compartilhar salvar etc */ }
 *     )
 * }
 * ```
 */
@Composable
fun RecordingOverlay(
    isVisible: Boolean,
    onRecordingComplete: (File) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    
    val recorder = remember { ScreenRecorder(context) }
    
    // Launcher para permissão de gravação de tela
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            val started = recorder.startRecording(
                result.resultCode,
                data,
                fileName = "chopcut_demo_${System.currentTimeMillis()}.mp4"
            )
            if (started) {
                isRecording = true
                recordingTime = 0
            }
        }
    }
    
    // Callbacks do recorder
    DisposableEffect(recorder) {
        recorder.callback = object : ScreenRecorder.RecordingCallback {
            override fun onRecordingStarted(outputFile: File) {
                Timber.i("Gravação iniciada: ${outputFile.name}")
            }
            
            override fun onRecordingStopped(outputFile: File) {
                Timber.i("Gravação finalizada: ${outputFile.name}")
                DemoRecordingManager(context).recordDemo(
                    file = outputFile,
                    description = "Demo gravada via RecordingOverlay",
                    tags = listOf("manual", "timeline")
                )
                onRecordingComplete(outputFile)
            }
            
            override fun onRecordingError(error: Exception) {
                Timber.e(error, "Erro na gravação")
                isRecording = false
            }
        }
        
        onDispose {
            recorder.release()
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            RecordingControls(
                isRecording = isRecording,
                recordingTime = recordingTime,
                onStartRecording = {
                    val intent = recorder.createScreenCaptureIntent()
                    screenCaptureLauncher.launch(intent)
                },
                onStopRecording = {
                    recorder.stopRecording()
                    isRecording = false
                }
            )
            
            // Indicador de gravação (piscante)
            if (isRecording) {
                RecordingIndicator(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * Controles de gravação (botões iniciar/parar).
 */
@Composable
private fun RecordingControls(
    isRecording: Boolean,
    recordingTime: Int,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Timer
        if (isRecording) {
            val minutes = recordingTime / 60
            val seconds = recordingTime % 60
            Text(
                text = String.format("%02d:%02d", minutes, seconds),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Botão de controle
        if (isRecording) {
            Button(
                onClick = onStopRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF1744) // Vermelho
                ),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Parar gravação",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("PARAR")
            }
        } else {
            Button(
                onClick = onStartRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50) // Verde
                ),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Iniciar gravação",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("GRAVAR")
            }
        }
    }
}

/**
 * Indicador visual de que está gravando.
 */
@Composable
private fun RecordingIndicator(
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }
    
    // Animação de piscar
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            visible = !visible
            kotlinx.coroutines.delay(500)
        }
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Círculo vermelho piscante
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (visible) Color(0xFFFF1744) else Color(0xFFFF1744).copy(alpha = 0.3f)
                )
        )
        
        Text(
            text = "REC",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Botão flutuante para ativar controles de gravação.
 * Pode ser adicionado ao lado do FAB principal.
 */
@Composable
fun RecordingFab(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        containerColor = if (isRecording) {
            Color(0xFFFF1744)
        } else {
            MaterialTheme.colorScheme.secondary
        }
    ) {
        Icon(
            imageVector = if (isRecording) {
                Icons.Default.PlayArrow
            } else {
                Icons.Default.CheckCircle
            },
            contentDescription = if (isRecording) "Parar gravação" else "Iniciar gravação",
            tint = Color.White
        )
    }
}

/**
 * Preview player para assistir demonstrações gravadas.
 */
@Composable
fun DemoVideoPlayer(
    videoUri: Uri,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Usa o VideoPreview existente
        VideoPreview(
            videoUri = videoUri,
            exoPlayer = null, // Criar player internamente ou receber
            isReady = true,
            isPlaying = false,
            currentPosition = 0,
            duration = 0,
            onTogglePlayPause = {},
            modifier = Modifier.fillMaxSize()
        )
        
        // Botão fechar
        Button(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Fechar")
        }
    }
}

/**
 * Extensão para converter File em Uri.
 */
fun File.toUri(): Uri = Uri.fromFile(this)
