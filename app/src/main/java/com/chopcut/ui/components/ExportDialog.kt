package com.chopcut.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chopcut.data.model.ExportPreset

@Composable
fun ExportDialog(
    presets: List<ExportPreset>,
    onPresetSelected: (ExportPreset) -> Unit,
    onDismiss: () -> Unit,
    originalWidth: Int = 0,
    originalHeight: Int = 0,
    originalBitrate: Int = 0
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Exportar Vídeo")
                Text(
                    "Escolha a qualidade desejada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(presets) { preset ->
                        val emoji = when {
                            preset.isOriginal -> "🎬"
                            preset.name.contains("Instagram", true) -> "📸"
                            preset.name.contains("TikTok", true) -> "🎵"
                            preset.name.contains("YouTube", true) -> "▶️"
                            preset.name.contains("WhatsApp", true) -> "💬"
                            else -> "⚙️"
                        }

                        // Mostra dimensões originais para preset Original
                        val displayInfo = if (preset.isOriginal && originalWidth > 0) {
                            "${originalWidth}x${originalHeight} • ${originalBitrate / 1_000_000}Mbps"
                        } else if (preset.isOriginal) {
                            "Igual ao original"
                        } else {
                            "${preset.width}x${preset.height} • ${preset.bitrate / 1_000_000}Mbps"
                        }

                        ListItem(
                            headlineContent = {
                                Text(preset.name, fontWeight = FontWeight.Bold)
                            },
                            supportingContent = {
                                Text(
                                    displayInfo,
                                    fontSize = 12.sp
                                )
                            },
                            leadingContent = {
                                Text(emoji, fontSize = 24.sp)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPresetSelected(preset) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR")
            }
        }
    )
}