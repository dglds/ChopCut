package com.chopcut.ui.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chopcut.data.model.EditOperation

/**
 * Painel de controle de rotação de vídeo
 *
 * @param initialRotation Rotação inicial em graus (0, 90, 180, 270)
 * @param onConfirm Callback quando a rotação é confirmada
 * @param onDismiss Callback para cancelar
 */
@Composable
fun RotationContent(
    initialRotation: Int = 0,
    onConfirm: (EditOperation.Rotation) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRotation by remember {
        mutableStateOf(initialRotation)
    }

    val rotations = listOf(
        "0°" to 0,
        "90°" to 90,
        "180°" to 180,
        "270°" to 270
    )

    val selectedLabel = rotations.firstOrNull { it.second == selectedRotation }?.first ?: "0°"

    Row(
        modifier = Modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rotations.forEach { (label, degrees) ->
            FilterChip(
                selected = selectedRotation == degrees,
                onClick = {
                    selectedRotation = degrees
                    onConfirm(EditOperation.Rotation(degrees))
                },
                label = {
                    Text(
                        label,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedRotation == degrees) FontWeight.Bold else FontWeight.Normal
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
