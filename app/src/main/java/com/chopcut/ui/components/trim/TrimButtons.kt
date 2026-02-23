package com.chopcut.ui.components.trim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TrimButtons(
    isDraftMode: Boolean,
    isInsideRange: Boolean,
    onAddPosition: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        TrimActionButton(
            isDraftMode = isDraftMode,
            isInsideRange = isInsideRange,
            onAddPosition = onAddPosition,
            onDelete = onDelete
        )
    }
}
