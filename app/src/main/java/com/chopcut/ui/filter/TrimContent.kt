package com.chopcut.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chopcut.ui.components.TrimRange

@Composable
fun TrimContent(
    currentPosition: Long,
    duration: Long,
    initialTrim: TrimRange?,
    onConfirm: (TrimRange) -> Unit,
    onDismiss: () -> Unit
) {
    var startMs by remember { mutableStateOf(initialTrim?.startMs ?: 0L) }
    var endMs by remember { mutableStateOf(initialTrim?.endMs ?: duration) }

    // Ensure valid range
    if (endMs > duration) endMs = duration
    if (startMs >= endMs) startMs = 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Cortar Vídeo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        // Info Display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TimeDisplay(label = "Início", timeMs = startMs)
            TimeDisplay(label = "Atual", timeMs = currentPosition, highlight = true)
            TimeDisplay(label = "Fim", timeMs = endMs)
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Duração: ${formatTime(endMs - startMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(24.dp))

        // Set Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { 
                    if (currentPosition < endMs) startMs = currentPosition 
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("Definir Início")
            }

            Button(
                onClick = { 
                    if (currentPosition > startMs) endMs = currentPosition 
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("Definir Fim")
            }
        }

        Spacer(Modifier.height(24.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = { 
                    onConfirm(TrimRange(startMs, endMs)) 
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Aplicar Corte")
            }
        }
    }
}

@Composable
private fun TimeDisplay(
    label: String,
    timeMs: Long,
    highlight: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatTime(timeMs),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (timeMs % 1000) / 100
    return String.format("%02d:%02d.%d", minutes, seconds, millis)
}
