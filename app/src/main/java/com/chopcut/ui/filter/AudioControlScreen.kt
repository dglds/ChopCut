package com.chopcut.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chopcut.data.model.EditOperation

/**
 * Data class representing a volume preset.
 */
data class VolumePreset(
    val value: Float,
    val name: String,
    val icon: ImageVector,
    val description: String
)

/**
 * List of available volume presets.
 */
val VOLUME_PRESETS = listOf(
    VolumePreset(0f, "Mudo", Icons.Default.Close, "Sem áudio"),
    VolumePreset(0.25f, "25%", Icons.Default.Close, "Volume muito baixo"),
    VolumePreset(0.5f, "50%", Icons.Default.Notifications, "Volume baixo"),
    VolumePreset(0.75f, "75%", Icons.Default.Notifications, "Volume médio-baixo"),
    VolumePreset(1.0f, "100%", Icons.Default.Notifications, "Volume normal"),
    VolumePreset(1.25f, "125%", Icons.Default.Notifications, "Volume alto"),
    VolumePreset(1.5f, "150%", Icons.Default.Notifications, "Volume muito alto"),
    VolumePreset(2.0f, "200%", Icons.Default.Notifications, "Volume máximo"),
)

/**
 * Managed state for AudioControlScreen.
 */
@Composable
fun rememberAudioState(
    currentVolume: EditOperation.Volume? = null,
    currentFade: EditOperation.Fade? = null
): AudioState {
    return remember(currentVolume, currentFade) {
        AudioState(
            volume = currentVolume?.volume ?: 1.0f,
            fadeInMs = currentFade?.fadeInMs ?: 0L,
            fadeOutMs = currentFade?.fadeOutMs ?: 0L
        )
    }
}

class AudioState(
    volume: Float = 1.0f,
    fadeInMs: Long = 0L,
    fadeOutMs: Long = 0L
) {
    var volume by mutableStateOf(volume)
    var fadeInMs by mutableStateOf(fadeInMs)
    var fadeOutMs by mutableStateOf(fadeOutMs)

    var selectedTab by mutableStateOf(0) // 0 = Volume, 1 = Fade

    val currentVolumePreset: VolumePreset
        get() = VOLUME_PRESETS.minByOrNull { kotlin.math.abs(it.value - volume) }
            ?: VOLUME_PRESETS[4]

    fun applyVolumePreset(preset: VolumePreset) {
        volume = preset.value
    }

    fun toEditOperations(): List<EditOperation> {
        return listOf(
            EditOperation.Volume(volume),
            EditOperation.Fade(fadeInMs, fadeOutMs)
        )
    }

    fun getVolumeIcon(): ImageVector {
        return when {
            volume == 0f -> Icons.Default.Close
            else -> Icons.Default.Notifications
        }
    }
}

/**
 * Content for Audio Control (Volume + Fade).
 */
@Composable
fun AudioControlContent(
    audioState: AudioState,
    onConfirm: (List<EditOperation>) -> Unit,
    onDismiss: () -> Unit
) {
    @Composable
    fun getDisplayColor(): Color {
        return when {
            audioState.volume == 0f -> Color(0xFFF44336)
            audioState.volume < 0.5f -> Color(0xFFFF9800)
            audioState.volume > 1.5f -> Color(0xFFF44336)
            else -> Color(0xFF4CAF50)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Tabs
        TabRow(
            selectedTabIndex = audioState.selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = audioState.selectedTab == 0,
                onClick = { audioState.selectedTab = 0 },
                text = { Text("Volume") }
            )
            Tab(
                selected = audioState.selectedTab == 1,
                onClick = { audioState.selectedTab = 1 },
                text = { Text("Fade") }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (audioState.selectedTab == 0) {
            // Volume Tab
            VolumeControl(audioState, { getDisplayColor() })
        } else {
            // Fade Tab
            FadeControl(audioState)
        }

        Spacer(Modifier.height(24.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    audioState.volume = 1.0f
                    audioState.fadeInMs = 0L
                    audioState.fadeOutMs = 0L
                    onConfirm(audioState.toEditOperations())
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Resetar")
            }

            Button(
                onClick = { onConfirm(audioState.toEditOperations()) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Aplicar")
            }
        }
    }
}

@Composable
fun VolumeControl(
    audioState: AudioState,
    getDisplayColor: @Composable () -> Color
) {
    Column {
        // Preview
        VolumePreview(audioState = audioState, getDisplayColor = getDisplayColor)

        Spacer(Modifier.height(24.dp))

        // Slider
        VolumeSlider(audioState = audioState, getDisplayColor = getDisplayColor)

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Presets",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        VolumePresetGrid(
            selectedValue = audioState.volume,
            onPresetSelected = { audioState.applyVolumePreset(it) }
        )
    }
}

@Composable
fun FadeControl(
    audioState: AudioState
) {
    Column {
        // Fade In
        Text(
            text = "Fade In: ${(audioState.fadeInMs / 1000f)}s",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = audioState.fadeInMs.toFloat(),
            onValueChange = { audioState.fadeInMs = it.toLong() },
            valueRange = 0f..5000f, // Max 5s
            steps = 49, // 0.1s steps approx
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // Fade Out
        Text(
            text = "Fade Out: ${(audioState.fadeOutMs / 1000f)}s",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = audioState.fadeOutMs.toFloat(),
            onValueChange = { audioState.fadeOutMs = it.toLong() },
            valueRange = 0f..5000f, // Max 5s
            steps = 49,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun VolumePreview(
    audioState: AudioState,
    getDisplayColor: @Composable () -> Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(getDisplayColor().copy(alpha = 0.1f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = audioState.getVolumeIcon(),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = getDisplayColor()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${(audioState.volume * 100).toInt()}%",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = getDisplayColor()
        )
        Text(
            text = audioState.currentVolumePreset.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun VolumeSlider(
    audioState: AudioState,
    getDisplayColor: @Composable () -> Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(
                text = "${(audioState.volume * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = getDisplayColor()
            )
            Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Slider(
            value = audioState.volume,
            onValueChange = { audioState.volume = it.coerceIn(0f, 2f) },
            valueRange = 0f..2f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun VolumePresetGrid(
    selectedValue: Float,
    onPresetSelected: (VolumePreset) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VOLUME_PRESETS.take(4).forEach { preset ->
                VolumePresetItem(
                    preset = preset,
                    isSelected = kotlin.math.abs(preset.value - selectedValue) < 0.01f,
                    onClick = { onPresetSelected(preset) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VOLUME_PRESETS.drop(4).forEach { preset ->
                VolumePresetItem(
                    preset = preset,
                    isSelected = kotlin.math.abs(preset.value - selectedValue) < 0.01f,
                    onClick = { onPresetSelected(preset) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun VolumePresetItem(
    preset: VolumePreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColor = when {
        preset.value == 0f -> Color(0xFFF44336)
        preset.value < 1.0f -> Color(0xFFFF9800)
        preset.value == 1.0f -> Color(0xFF4CAF50)
        else -> Color(0xFFF44336)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) selectedColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(preset.icon, null, tint = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(2.dp))
        Text(preset.name, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
    }
}