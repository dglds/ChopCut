package com.chopcut.ui.components.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.RectangleShape
import com.chopcut.ui.theme.Primary

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
