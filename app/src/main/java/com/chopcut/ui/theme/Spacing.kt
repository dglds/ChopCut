package com.chopcut.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Escala de espaçamento do Design System ChopCut
 *
 * Baseado em múltiplos de 4dp para consistência
 */
object ChopCutSpacing {
    val xxs = 4.dp    // Gap entre ícones
    val xs = 8.dp     // Padding pequeno
    val sm = 12.dp    // Gap entre elementos relacionados
    val md = 16.dp    // Padding padrão
    val lg = 24.dp    // Margem de seção
    val xl = 32.dp    // Espaçamento entre seções
    val xxl = 48.dp   // Margem de tela

    // Touch target mínimo
    val touchTarget = 48.dp

    // Alturas de componente
    val buttonHeight = 48.dp
    val inputHeight = 48.dp
    val fabSize = 56.dp
    val smallFabSize = 40.dp

    // Timeline específico
    val timelineHeight = 72.dp
    val waveformHeight = 48.dp
    val trimHandleSize = 24.dp
}
