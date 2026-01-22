package com.chopcut.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chopcut.ui.timeline.model.VideoRange

/**
 * Componente para exibir a lista de ranges adicionados.
 */
@Composable
fun RangesList(
    ranges: List<VideoRange>,
    onRangeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (ranges.isEmpty()) {
        Text(
            text = "Nenhum range adicionado. Clique em '+ Range' para adicionar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(8.dp)
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items = ranges, key = { it.id }) { range ->
            RangeItem(
                range = range,
                onClick = { onRangeClick(range.id) }
            )
        }
    }
}

/**
 * Componente para exibir um range individual.
 */
@Composable
fun RangeItem(
    range: VideoRange,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (range.isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (range.isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .border(1.dp, borderColor)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (range.isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
        } else {
            Surface(
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) {}
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Range #${range.id.take(4)}",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "%.1fs - %.1fs (%.1fs)".format(
                    range.startMs / 1000.0,
                    range.endMs / 1000.0,
                    (range.endMs - range.startMs) / 1000.0
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
