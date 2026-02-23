package com.chopcut.ui.components.trim

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TrimControlPanel(
    isDraftMode: Boolean,
    isInsideRange: Boolean,
    onAddPosition: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    TrimButtons(
        isDraftMode = isDraftMode,
        isInsideRange = isInsideRange,
        onAddPosition = onAddPosition,
        onDelete = onDelete,
        modifier = modifier
    )
}
