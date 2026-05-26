package com.chopcut.ui.components.waveform

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.RectangleShape
import com.chopcut.ui.theme.ChopCutSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveformConfigPanel(
    currentStyle: WaveformStyle,
    onStyleChange: (WaveformStyle) -> Unit,
    modifier: Modifier = Modifier,
    onApply: () -> Unit = {}
) {
    var tempStyle by remember(currentStyle) { mutableStateOf(currentStyle) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Wave Config:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            // Mirrored Toggle
            FilterChip(
                selected = tempStyle.isMirrored,
                onClick = { tempStyle = tempStyle.copy(isMirrored = !tempStyle.isMirrored) },
                label = { Text("Espelhado") }
            )

            // Smoothed Toggle
            FilterChip(
                selected = tempStyle.isSmoothed,
                onClick = { tempStyle = tempStyle.copy(isSmoothed = !tempStyle.isSmoothed) },
                label = { Text("Suavizado") }
            )

            // Height Scale
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Escala: ${(tempStyle.heightScale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = tempStyle.heightScale,
                    onValueChange = { tempStyle = tempStyle.copy(heightScale = it) },
                    valueRange = 0.1f..3.0f,
                    modifier = Modifier.width(100.dp)
                )
            }

            // Stroke Width
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Text("Traço: ${tempStyle.strokeWidth.value.toInt()}dp", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = tempStyle.strokeWidth.value,
                    onValueChange = { tempStyle = tempStyle.copy(strokeWidth = it.dp) },
                    valueRange = 0.5f..5.0f,
                    modifier = Modifier.width(100.dp)
                )
            }
            
            // Baseline
            Column {
                Text("Alinhamento", style = MaterialTheme.typography.labelSmall)
                Row {
                    WaveformStyle.Baseline.values().forEach { baseline ->
                        IconToggleButton(
                            checked = tempStyle.baseline == baseline,
                            onCheckedChange = { tempStyle = tempStyle.copy(baseline = baseline) }
                        ) {
                            Text(baseline.name.take(1), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }
            }

            // APPLY BUTTON
            Button(
                onClick = { 
                    onStyleChange(tempStyle)
                    onApply() 
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("APLICAR")
            }
        }
    }
}
