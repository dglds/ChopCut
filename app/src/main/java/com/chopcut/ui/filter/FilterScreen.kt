package com.chopcut.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chopcut.data.model.FilterType
import com.chopcut.data.model.EditOperation

/**
 * Data class representando um filtro disponível na UI.
 */
data class FilterPreset(
    val type: FilterType,
    val name: String,
    val defaultIntensity: Float = 1.0f,
    val minIntensity: Float = 0.0f,
    val maxIntensity: Float = 1.0f,
    val intensityStep: Float = 0.1f,
    val previewColor: Brush? = null
)

/**
 * Lista de filtros predefinidos disponíveis no app.
 */
val AVAILABLE_FILTERS = listOf(
    FilterPreset(
        type = FilterType.NONE,
        name = "Normal",
        previewColor = Brush.horizontalGradient(listOf(Color.Gray, Color.Gray))
    ),
    FilterPreset(
        type = FilterType.GRAYSCALE,
        name = "P&B",
        previewColor = Brush.horizontalGradient(listOf(Color(0.5f, 0.5f, 0.5f), Color(0.3f, 0.3f, 0.3f)))
    ),
    FilterPreset(
        type = FilterType.SEPIA,
        name = "Sépia",
        previewColor = Brush.horizontalGradient(listOf(Color(0.8f, 0.6f, 0.4f), Color(0.6f, 0.4f, 0.2f)))
    ),
    FilterPreset(
        type = FilterType.BRIGHTNESS,
        name = "Brilho",
        minIntensity = -1.0f,
        maxIntensity = 1.0f,
        intensityStep = 0.1f
    ),
    FilterPreset(
        type = FilterType.CONTRAST,
        name = "Contraste",
        minIntensity = 0.5f,
        maxIntensity = 1.5f,
        intensityStep = 0.1f
    ),
    FilterPreset(
        type = FilterType.SATURATION,
        name = "Saturação",
        maxIntensity = 2.0f,
        intensityStep = 0.1f
    ),
)

/**
 * Estado do filtro selecionado.
 */
@Composable
fun rememberFilterState(
    currentFilter: EditOperation.Filter? = null
): FilterState {
    return remember(currentFilter) {
        FilterState(
            selectedType = currentFilter?.filterType ?: FilterType.NONE,
            intensity = currentFilter?.intensity ?: 1.0f
        )
    }
}

/**
 * Estado gerenciado pelo FilterScreen.
 */
class FilterState(
    selectedType: FilterType = FilterType.NONE,
    intensity: Float = 1.0f
) {
    var selectedType by mutableStateOf(selectedType)
    var intensity by mutableStateOf(intensity)

    val currentPreset: FilterPreset
        get() = AVAILABLE_FILTERS.firstOrNull { it.type == selectedType }
            ?: AVAILABLE_FILTERS[0]

    val intensityRange: ClosedFloatingPointRange<Float>
        get() = currentPreset.minIntensity..currentPreset.maxIntensity

    fun applyPreset(preset: FilterPreset) {
        selectedType = preset.type
        intensity = preset.defaultIntensity
    }

    fun updateIntensity(value: Float) {
        intensity = value.coerceIn(intensityRange)
    }

    fun toEditOperation(): EditOperation.Filter? {
        return if (selectedType != FilterType.NONE) {
            EditOperation.Filter(selectedType, intensity)
        } else null
    }

    fun hasChanges(): Boolean {
        return selectedType != FilterType.NONE
    }
}

/**
 * Bottom sheet de seleção de filtros com preview e controle de intensidade.
 *
 * @param filterState Estado gerenciado do filtro
 * @param onConfirm Callback quando usuário confirma a seleção
 * @param onDismiss Callback quando usuário cancela
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    filterState: FilterState,
    onConfirm: (EditOperation.Filter?) -> Unit,
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
                    text = "Filtros de Vídeo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    ) {
        FilterContent(
            filterState = filterState,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

/**
 * Conteúdo da tela de filtros.
 */
@Composable
fun FilterContent(
    filterState: FilterState,
    onConfirm: (EditOperation.Filter?) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Lista horizontal de filtros
        FilterSelector(
            selectedType = filterState.selectedType,
            onFilterSelected = { filterState.applyPreset(it) }
        )

        Spacer(Modifier.height(24.dp))

        // Controle de intensidade
        IntensityControl(
            filterState = filterState
        )

        Spacer(Modifier.height(24.dp))

        // Botões de ação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    filterState.applyPreset(AVAILABLE_FILTERS[0]) // Reset para NONE
                    onConfirm(null)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Remover")
            }

            Button(
                onClick = { onConfirm(filterState.toEditOperation()) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Aplicar")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Seletor de filtros em lista horizontal.
 */
@Composable
fun FilterSelector(
    selectedType: FilterType,
    onFilterSelected: (FilterPreset) -> Unit
) {
    Text(
        text = "Selecione um filtro",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(12.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(AVAILABLE_FILTERS) { preset ->
            FilterPresetItem(
                preset = preset,
                isSelected = preset.type == selectedType,
                onClick = { onFilterSelected(preset) }
            )
        }
    }
}

/**
 * Item individual de filtro na lista horizontal.
 */
@Composable
fun FilterPresetItem(
    preset: FilterPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preview do filtro
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    preset.previewColor
                        ?: Brush.horizontalGradient(listOf(Color.Gray, Color.LightGray))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Nome do filtro
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp
        )
    }
}

/**
 * Controle deslizante de intensidade do filtro.
 */
@Composable
fun IntensityControl(
    filterState: FilterState
) {
    if (filterState.selectedType == FilterType.NONE) {
        // Mostra mensagem quando nenhum filtro selecionado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Selecione um filtro para ajustar a intensidade",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val preset = filterState.currentPreset

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
            Text(
                text = "Intensidade",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = String.format("%.1f", filterState.intensity),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(12.dp))

        Slider(
            value = filterState.intensity,
            onValueChange = { filterState.updateIntensity(it) },
            valueRange = filterState.intensityRange,
            steps = ((filterState.currentPreset.maxIntensity - filterState.currentPreset.minIntensity) /
                    filterState.currentPreset.intensityStep).toInt() - 1,
            modifier = Modifier.fillMaxWidth()
        )

        // Labels min/max
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = preset.minIntensity.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = preset.maxIntensity.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Diálogo simples para seleção rápida de filtros.
 */
@Composable
fun FilterDialog(
    currentFilter: EditOperation.Filter? = null,
    onConfirm: (EditOperation.Filter?) -> Unit,
    onDismiss: () -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(true) }
    val filterState = rememberFilterState(currentFilter)

    if (showBottomSheet) {
        FilterBottomSheet(
            filterState = filterState,
            onConfirm = { filter ->
                onConfirm(filter)
                showBottomSheet = false
            },
            onDismiss = {
                onDismiss()
                showBottomSheet = false
            }
        )
    }
}
