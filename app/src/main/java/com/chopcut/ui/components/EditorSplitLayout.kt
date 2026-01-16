package com.chopcut.ui.components

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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Área 1: Topo - Fixa (Preview, Timeline, etc)
        // Usamos weight(1f) para ocupar o espaço disponível, deixando a parte inferior crescer conforme necessário
        // ou podemos fixar o topo e deixar a base dinâmica. 
        // A especificação diz "Área 1 (Topo - Fixa)... Área 2 (Base - Dinâmica)".
        // Geralmente editores de vídeo dão prioridade ao preview.
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            topContent()
        }

        // Área 2: Base - Dinâmica (Controles)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            bottomContent()
        }
    }
}
