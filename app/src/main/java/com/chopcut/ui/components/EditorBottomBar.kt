package com.chopcut.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chopcut.data.model.EditOperation
import com.chopcut.data.model.FilterType

/**
 * Bottom bar para o editor com todas as ferramentas organizadas
 */
@Composable
fun EditorBottomBar(
    isExporting: Boolean,
    videoInfo: Boolean,
    trimRange: TrimRange?,
    currentFilter: EditOperation.Filter?,
    currentSpeed: EditOperation.Speed?,
    currentVolume: EditOperation.Volume?,
    onExportClick: () -> Unit,
    onTrimClick: (TrimRange?) -> Unit,
    onRotateClick: () -> Unit,
    onFilterClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onVolumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Linha 1: Ações principais (Export, Trim, Rotate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exportar (destaque)
                Button(
                    onClick = onExportClick,
                    enabled = !isExporting && videoInfo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar", style = MaterialTheme.typography.labelMedium)
                }

                // Trim
                Button(
                    onClick = { onTrimClick(trimRange) },
                    enabled = !isExporting && trimRange != null,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trim", style = MaterialTheme.typography.labelSmall)
                }

                // Rotacionar
                Button(
                    onClick = onRotateClick,
                    enabled = !isExporting,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Rotacionar", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Linha 2: Efeitos (Filtros, Velocidade, Volume)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filtros
                BottomBarButton(
                    icon = Icons.Default.Settings,
                    label = getFilterLabel(currentFilter),
                    intensity = currentFilter?.intensity,
                    isActive = currentFilter != null,
                    activeColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onFilterClick,
                    enabled = !isExporting,
                    modifier = Modifier.height(44.dp)
                )

                // Velocidade
                val speedLabel = if (currentSpeed?.speed != 1.0f && currentSpeed != null) {
                    String.format("%.1fx", currentSpeed.speed)
                } else {
                    "Velocidade"
                }
                val isSlow = currentSpeed?.speed != null && currentSpeed.speed < 1f
                BottomBarButton(
                    icon = if (isSlow) Icons.Default.ArrowBack else Icons.Default.ArrowForward,
                    label = speedLabel,
                    isActive = currentSpeed != null && currentSpeed.speed != 1.0f,
                    activeColor = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = onSpeedClick,
                    enabled = !isExporting,
                    modifier = Modifier.height(44.dp)
                )

                // Volume
                val volumePercent = currentVolume?.volume?.let { (it * 100).toInt() } ?: 100
                val volumeLabel = if (currentVolume != null && currentVolume.volume != 1.0f) {
                    "$volumePercent%"
                } else {
                    "Volume"
                }
                val isMuted = currentVolume?.volume == 0f
                BottomBarButton(
                    icon = if (isMuted) Icons.Default.Close else Icons.Default.Notifications,
                    label = volumeLabel,
                    isActive = currentVolume != null && currentVolume.volume != 1.0f,
                    activeColor = when {
                        isMuted -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    onClick = onVolumeClick,
                    enabled = !isExporting,
                    modifier = Modifier.height(44.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    intensity: Float? = null,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isActive) activeColor else Color.Transparent
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isActive) {
                when (activeColor) {
                    MaterialTheme.colorScheme.secondaryContainer -> MaterialTheme.colorScheme.primary
                    MaterialTheme.colorScheme.tertiaryContainer -> MaterialTheme.colorScheme.tertiary
                    MaterialTheme.colorScheme.primaryContainer -> MaterialTheme.colorScheme.primary
                    MaterialTheme.colorScheme.errorContainer -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            } else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
        if (isActive && intensity != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                String.format("%.0f%%", intensity * 100),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun getFilterLabel(filter: EditOperation.Filter?): String {
    return when (filter?.filterType) {
        FilterType.GRAYSCALE -> "P&B"
        FilterType.SEPIA -> "Sépia"
        FilterType.BRIGHTNESS -> "Brilho"
        FilterType.CONTRAST -> "Contraste"
        FilterType.SATURATION -> "Saturação"
        else -> "Filtros"
    }
}
