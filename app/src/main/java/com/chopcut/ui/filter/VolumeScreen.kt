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
 * Data class representando uma opção de volume.
 */
data class VolumePreset(
    val value: Float,
    val name: String,
    val icon: ImageVector,
    val description: String
)

/**
 * Lista de presets de volume disponíveis.
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
 * Estado gerenciado do VolumeScreen.
 */
@Composable
fun rememberVolumeState(
    currentVolume: EditOperation.Volume? = null
): VolumeState {
    return remember(currentVolume) {
        VolumeState(
            selectedValue = currentVolume?.volume ?: 1.0f
        )
    }
}

class VolumeState(
    selectedValue: Float = 1.0f
) {
    var selectedValue by mutableStateOf(selectedValue)

    val currentPreset: VolumePreset
        get() = VOLUME_PRESETS.minByOrNull { kotlin.math.abs(it.value - selectedValue) }
            ?: VOLUME_PRESETS[4]

    fun applyPreset(preset: VolumePreset) {
        selectedValue = preset.value
    }

    fun toEditOperation(): EditOperation.Volume {
        return EditOperation.Volume(selectedValue)
    }

    fun hasChanges(): Boolean {
        return selectedValue != 1.0f
    }

    fun getVolumeIcon(): ImageVector {
        return when {
            selectedValue == 0f -> Icons.Default.Close
            else -> Icons.Default.Notifications
        }
    }
}

/**
 * Bottom sheet de seleção de volume.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeBottomSheet(
    volumeState: VolumeState,
    onConfirm: (EditOperation.Volume) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BottomSheetDefaults.DragHandle()
                Text(
                    text = "Volume do Áudio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    ) {
        VolumeContent(
            volumeState = volumeState,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

/**
 * Conteúdo da tela de volume.
 */
@Composable
fun VolumeContent(
    volumeState: VolumeState,
    onConfirm: (EditOperation.Volume) -> Unit,
    onDismiss: () -> Unit
) {
    @Composable
    fun getDisplayColor(): Color {
        return when {
            volumeState.selectedValue == 0f -> Color(0xFFF44336) // Vermelho para mudo
            volumeState.selectedValue < 0.5f -> Color(0xFFFF9800) // Laranja para baixo
            volumeState.selectedValue > 1.5f -> Color(0xFFF44336) // Vermelho para muito alto
            else -> Color(0xFF4CAF50) // Verde para normal
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Preview do volume atual
        VolumePreview(volumeState = volumeState, getDisplayColor = { getDisplayColor() })

        Spacer(Modifier.height(24.dp))

        // Slider principal
        VolumeSlider(volumeState = volumeState, getDisplayColor = { getDisplayColor() })

        Spacer(Modifier.height(24.dp))

        // Lista de presets
        Text(
            text = "Presets de volume",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // Grid de presets
        VolumePresetGrid(
            selectedValue = volumeState.selectedValue,
            onPresetSelected = { volumeState.applyPreset(it) }
        )

        Spacer(Modifier.height(24.dp))

        // Botões de ação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    volumeState.applyPreset(VOLUME_PRESETS[4]) // Reset para 100%
                    onConfirm(EditOperation.Volume(1.0f))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Normal")
            }

            Button(
                onClick = { onConfirm(volumeState.toEditOperation()) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Aplicar")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Preview do volume selecionado.
 */
@Composable
fun VolumePreview(
    volumeState: VolumeState,
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
            imageVector = volumeState.getVolumeIcon(),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = getDisplayColor()
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "${(volumeState.selectedValue * 100).toInt()}%",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = getDisplayColor()
        )

        Text(
            text = volumeState.currentPreset.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Slider de volume com visualização.
 */
@Composable
fun VolumeSlider(
    volumeState: VolumeState,
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
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = "${(volumeState.selectedValue * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = getDisplayColor()
            )

            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Slider(
            value = volumeState.selectedValue,
            onValueChange = { volumeState.selectedValue = it.coerceIn(0f, 2f) },
            valueRange = 0f..2f,
            modifier = Modifier.fillMaxWidth()
        )

        // Labels min/max
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "100%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "200%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Grid de presets de volume.
 */
@Composable
fun VolumePresetGrid(
    selectedValue: Float,
    onPresetSelected: (VolumePreset) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Primeira linha (0%, 25%, 50%, 75%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

        // Segunda linha (100%, 125%, 150%, 200%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

/**
 * Item individual de preset de volume.
 */
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
            .background(
                if (isSelected) selectedColor.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = preset.icon,
            contentDescription = null,
            tint = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp)
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp
        )
    }
}

/**
 * Diálogo simples para seleção de volume.
 */
@Composable
fun VolumeDialog(
    currentVolume: EditOperation.Volume? = null,
    onConfirm: (EditOperation.Volume) -> Unit,
    onDismiss: () -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(true) }
    val volumeState = rememberVolumeState(currentVolume)

    if (showBottomSheet) {
        VolumeBottomSheet(
            volumeState = volumeState,
            onConfirm = { volume ->
                onConfirm(volume)
                showBottomSheet = false
            },
            onDismiss = {
                onDismiss()
                showBottomSheet = false
            }
        )
    }
}
