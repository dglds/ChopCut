package com.chopcut.ui.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun EditorSplitLayout(
    topContent: @Composable BoxScope.() -> Unit,
    bottomContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    topWeight: Float = 0.6f,
    bottomWeight: Float = 0.4f
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Área 1: Topo - Fixa
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(topWeight)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            topContent()
        }

        // Área 2: Base - Dinâmica
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(bottomWeight)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            bottomContent()
        }
    }
}