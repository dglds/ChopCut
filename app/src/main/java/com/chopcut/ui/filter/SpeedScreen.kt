package com.chopcut.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
 * Data class representando uma opção de velocidade.
 */
data class SpeedPreset(
    val value: Float,
    val name: String,
    val icon: ImageVector,
    val description: String
)

/**
 * Lista de presets de velocidade disponíveis.
 */
val SPEED_PRESETS = listOf(
    SpeedPreset(0.25f, "0.25x", Icons.Default.ArrowBack, "Super Lento"),
    SpeedPreset(0.5f, "0.5x", Icons.Default.ArrowBack, "Lento"),
    SpeedPreset(0.75f, "0.75x", Icons.Default.ArrowBack, "Um pouco lento"),
    SpeedPreset(1.0f, "Normal", Icons.Default.PlayArrow, "Velocidade normal"),
    SpeedPreset(1.5f, "1.5x", Icons.Default.ArrowForward, "Um pouco rápido"),
    SpeedPreset(2.0f, "2x", Icons.Default.ArrowForward, "Rápido"),
    SpeedPreset(3.0f, "3x", Icons.Default.ArrowForward, "Muito rápido"),
    SpeedPreset(4.0f, "4x", Icons.Default.ArrowForward, "Super rápido"),
)

/**
 * Estado gerenciado do SpeedScreen.
 */
@Composable
fun rememberSpeedState(
    currentSpeed: EditOperation.Speed? = null
): SpeedState {
    return remember(currentSpeed) {
        SpeedState(
            selectedValue = currentSpeed?.speed ?: 1.0f
        )
    }
}

class SpeedState(
    selectedValue: Float = 1.0f
) {
    var selectedValue by mutableStateOf(selectedValue)

    val currentPreset: SpeedPreset
        get() = SPEED_PRESETS.minByOrNull { kotlin.math.abs(it.value - selectedValue) }
            ?: SPEED_PRESETS[3]

    fun applyPreset(preset: SpeedPreset) {
        selectedValue = preset.value
    }

    fun toEditOperation(): EditOperation.Speed {
        return EditOperation.Speed(selectedValue)
    }

    fun hasChanges(): Boolean {
        return selectedValue != 1.0f
    }
}

/**
 * Bottom sheet de seleção de velocidade.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedBottomSheet(
    speedState: SpeedState,
    onConfirm: (EditOperation.Speed) -> Unit,
    onDismiss: () -> Unit
) {
    @Composable
    fun getDisplayColor(): Color {
        return when {
            speedState.selectedValue < 1.0f -> Color(0xFF4CAF50) // Verde para slow motion
            speedState.selectedValue > 1.0f -> Color(0xFFFF9800) // Laranja para fast forward
            else -> MaterialTheme.colorScheme.primary
        }
    }
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
                    text = "Velocidade do Vídeo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    ) {
        SpeedContent(
            speedState = speedState,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            getDisplayColor = { getDisplayColor() }
        )
    }
}

/**
 * Conteúdo da tela de velocidade.
 */
@Composable
fun SpeedContent(
    speedState: SpeedState,
    onConfirm: (EditOperation.Speed) -> Unit,
    onDismiss: () -> Unit,
    getDisplayColor: @Composable () -> Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Preview da velocidade atual
        SpeedPreview(speedState = speedState, getDisplayColor = getDisplayColor)

        Spacer(Modifier.height(24.dp))

        // Lista de presets
        Text(
            text = "Selecione a velocidade",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(SPEED_PRESETS) { preset ->
                SpeedPresetItem(
                    preset = preset,
                    isSelected = kotlin.math.abs(preset.value - speedState.selectedValue) < 0.01f,
                    onClick = { speedState.applyPreset(preset) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Slider fino
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ajuste fino",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format("%.2fx", speedState.selectedValue),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = getDisplayColor()
                )
            }

            Spacer(Modifier.height(12.dp))

            Slider(
                value = speedState.selectedValue,
                onValueChange = { speedState.selectedValue = it.coerceIn(0.25f, 4.0f) },
                valueRange = 0.25f..4.0f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))

        // Botões de ação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    speedState.applyPreset(SPEED_PRESETS[3]) // Reset para normal
                    onConfirm(EditOperation.Speed(1.0f))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Normal")
            }

            Button(
                onClick = { onConfirm(speedState.toEditOperation()) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Aplicar")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Preview da velocidade selecionada.
 */
@Composable
fun SpeedPreview(
    speedState: SpeedState,
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
            imageVector = speedState.currentPreset.icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = getDisplayColor()
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = speedState.currentPreset.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = getDisplayColor()
        )

        Text(
            text = speedState.currentPreset.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Item individual de preset de velocidade.
 */
@Composable
fun SpeedPresetItem(
    preset: SpeedPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val selectedColor = when {
        preset.value < 1.0f -> Color(0xFF4CAF50)
        preset.value > 1.0f -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) selectedColor.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = preset.icon,
            contentDescription = null,
            tint = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp
        )
    }
}

/**
 * Diálogo simples para seleção de velocidade.
 */
@Composable
fun SpeedDialog(
    currentSpeed: EditOperation.Speed? = null,
    onConfirm: (EditOperation.Speed) -> Unit,
    onDismiss: () -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(true) }
    val speedState = rememberSpeedState(currentSpeed)

    if (showBottomSheet) {
        SpeedBottomSheet(
            speedState = speedState,
            onConfirm = { speed ->
                onConfirm(speed)
                showBottomSheet = false
            },
            onDismiss = {
                onDismiss()
                showBottomSheet = false
            }
        )
    }
}
