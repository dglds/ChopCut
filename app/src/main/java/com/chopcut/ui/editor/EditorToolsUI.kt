package com.chopcut

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


// --- Merged from CompressToolPanel.kt ---


@Composable
fun CompressToolPanel(
    currentLevel: CompressionLevel,
    onLevelSelected: (CompressionLevel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RectangleShape)
            .border(2.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompressionLevel.values().forEach { level ->
                    CompressionButton(
                        level = level,
                        isSelected = currentLevel == level,
                        onClick = { onLevelSelected(level) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentLevel.description,
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        
        IconButton(onClick = onClose, modifier = Modifier.padding(start = 16.dp)) {
            Icon(Icons.Default.Check, contentDescription = "OK", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CompressionButton(
    level: CompressionLevel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RectangleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = level.label,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// --- Merged from FormatToolPanel.kt ---


@Composable
fun FormatToolPanel(
    currentRatio: Float?,
    onRatioSelected: (Float?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RectangleShape)
            .border(2.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RatioButton(label = "Original", isSelected = currentRatio == null, onClick = { onRatioSelected(null) })
            RatioButton(label = "16:9", isSelected = currentRatio == 16f/9f, onClick = { onRatioSelected(16f/9f) })
            RatioButton(label = "9:16", isSelected = currentRatio == 9f/16f, onClick = { onRatioSelected(9f/16f) })
            RatioButton(label = "1:1", isSelected = currentRatio == 1f, onClick = { onRatioSelected(1f) })
        }
        
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Check, contentDescription = "OK", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RatioButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RectangleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
        )
    }
}

// --- Merged from MainToolBar.kt ---


@Composable
fun MainToolBar(
    onToolSelected: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolButton(
            icon = Icons.Default.ContentCut,
            label = "Cortar",
            onClick = { onToolSelected(EditorTool.TRIM) }
        )
        ToolButton(
            icon = Icons.AutoMirrored.Filled.CallMerge,
            label = "Juntar",
            onClick = { onToolSelected(EditorTool.ADD_MEDIA) }
        )
        ToolButton(
            icon = Icons.Default.AspectRatio,
            label = "Tamanho",
            onClick = { onToolSelected(EditorTool.FORMAT) }
        )
        ToolButton(
            icon = Icons.Default.Crop,
            label = "Recortar",
            onClick = { onToolSelected(EditorTool.CROP) }
        )
        ToolButton(
            icon = Icons.Default.Compress,
            label = "Comprimir",
            onClick = { onToolSelected(EditorTool.COMPRESS) }
        )
        ToolButton(
            icon = Icons.Default.MusicNote,
            label = "Áudio",
            onClick = { onToolSelected(EditorTool.AUDIO) }
        )
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(
            text = label, 
            color = Color.White, 
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// --- Merged from TrimToolPanel.kt ---


@Composable
fun TrimToolPanel(
    isDraftMode: Boolean,
    isInsideRange: Boolean,
    onAddPosition: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Aproveitamos o botão TrimButtons já existente
        TrimButtons(
            isDraftMode = isDraftMode,
            isInsideRange = isInsideRange,
            onAddPosition = onAddPosition,
            onDelete = onDelete,
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        )
        
        IconButton(onClick = onClose, modifier = Modifier.padding(end = 16.dp)) {
            Icon(Icons.Default.Check, contentDescription = "OK", tint = Color(0xFF00E5FF))
        }
    }
}

// --- Merged from ToolButton.kt ---


/**
 * Botão de ferramenta para a toolbar do editor.
 *
 * Exibe um ícone com um indicador visual quando a ferramenta está ativa.
 *
 * @param icon Ícone da ferramenta
 * @param contentDescription Descrição para acessibilidade
 * @param isActive Se a ferramenta está ativa
 * @param hasAppliedChanges Se há alterações aplicadas (filtro, velocidade, etc)
 * @param onClick Ação ao clicar
 * @param modifier Modificador
 * @param tint Cor do ícone (usa Primary por padrão)
 */
@Composable
fun ToolButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    hasAppliedChanges: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Primary
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (isActive) {
                        Modifier.clip(RectangleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        Modifier
                    }
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else if (hasAppliedChanges) {
                    Primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        // Indicador de alterações aplicadas (ponto pequeno)
        if (hasAppliedChanges && !isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .clip(RectangleShape)
                    .background(Primary)
            )
        }
    }
}

/**
 * Botão de ferramenta com rótulo abaixo (para uso em painéis)
 */
@Composable
fun ToolButtonWithLabel(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    isActive: Boolean = false,
    hasAppliedChanges: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ToolButton(
            icon = icon,
            contentDescription = contentDescription,
            isActive = isActive,
            hasAppliedChanges = hasAppliedChanges,
            onClick = onClick
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) {
                Primary
            } else if (hasAppliedChanges) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Enum com ícones para as ferramentas do editor
 */
enum class EditorToolIcon(val icon: ImageVector, val contentDescription: String) {
    FILTER(androidx.compose.material.icons.Icons.Default.FilterList, "Filtros"),
    SPEED(androidx.compose.material.icons.Icons.Default.Speed, "Velocidade"),
    EXPORT(androidx.compose.material.icons.Icons.Default.Share, "Exportar"),
    EFFECTS(androidx.compose.material.icons.Icons.Default.AutoFixHigh, "Efeitos")
}

// --- Merged from CompressionLevel.kt ---

enum class CompressionLevel(val label: String, val description: String) {
    ORIGINAL("Original", "Máxima qualidade, arquivo maior"),
    MEDIUM("Média", "Equilíbrio entre qualidade e tamanho"),
    LOW("Baixa", "Ideal para enviar rápido (WhatsApp)")
}

// --- Merged from EditorTool.kt ---

/**
 * Representa as ferramentas disponíveis no Editor Unificado.
 */
enum class EditorTool {
    NONE,       // Menu principal (sem ferramenta selecionada)
    TRIM,       // Cortar trechos (Trim)
    ADD_MEDIA,  // Concatenar vídeos (Join)
    FORMAT,     // Alterar resolução (Resize / Aspect Ratio)
    CROP,       // Recortar área (Crop)
    COMPRESS,   // Reduzir tamanho (Compress)
    AUDIO       // Extrair/Modificar trilha sonora (Audio)
}

// --- Merged from TimelineScrollMode.kt ---

/**
 * Modo de scroll da timeline.
 *
 * A timeline opera em exatamente um destes 3 modos por vez,
 * derivado exclusivamente de [isPlaying] e [isScrubbing] do EditorState.
 *
 * NUNCA adicione um 4to modo sem atualizar TODOS os [when] exaustivos.
 */
enum class TimelineScrollMode {
    /** Player parado, sem gesto do usuário. Posição = ExoPlayer.currentPosition. */
    IDLE,
    /** Player rodando. Scroll automático interpolado via VSYNC. */
    AUTO,
    /** Usuário arrastando. Player é ignorado, posição é do gesto. */
    MANUAL
}
