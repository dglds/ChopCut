package com.chopcut.core.designsystem.tokens

import androidx.compose.ui.unit.dp

/**
 * Design Tokens para tamanhos e dimensões no ChopCut.
 */
object SizeTokens {
    
    // ============================================================================
    // COMPONENT SIZES - Tamanhos padrão de componentes
    // ============================================================================
    
    /** Altura padrão de botões (56dp - touch target Material) */
    val buttonHeight = 56.dp
    
    /** Altura compacta de botões (40dp) */
    val buttonHeightCompact = 40.dp
    
    /** Altura mínima de touch targets (48dp - acessibilidade) */
    val touchTargetMin = 48.dp
    
    // ============================================================================
    // ICON SIZES - Tamanhos de ícones
    // ============================================================================
    
    /** Tamanho pequeno de ícone (16dp) */
    val iconSm = 16.dp
    
    /** Tamanho padrão de ícone (24dp) */
    val iconMd = 24.dp
    
    /** Tamanho grande de ícone (32dp) */
    val iconLg = 32.dp
    
    /** Tamanho extra grande de ícone (48dp) */
    val iconXl = 48.dp
    
    /** Tamanho hero de ícone (80dp) */
    val iconHero = 80.dp
    
    // ============================================================================
    // RADIUS - Cantos arredondados
    // ============================================================================
    
    /** Sem arredondamento */
    val radiusNone = 0.dp
    
    /** Arredondamento pequeno (4dp) */
    val radiusSm = 4.dp
    
    /** Arredondamento médio (8dp) */
    val radiusMd = 8.dp
    
    /** Arredondamento grande (12dp) */
    val radiusLg = 12.dp
    
    /** Arredondamento extra grande (16dp) */
    val radiusXl = 16.dp
    
    /** Arredondamento completo (circular) */
    val radiusFull = 9999.dp
    
    // ============================================================================
    // VIDEO EDITOR SPECIFIC - Tamanhos específicos do editor
    // ============================================================================
    
    /** Altura padrão da timeline */
    val timelineHeight = 120.dp
    
    /** Altura do waveform */
    val waveformHeight = 48.dp
    
    /** Altura dos controles do player */
    val playerControlsHeight = 56.dp
    
    /** Tamanho da thumbnail na timeline */
    val timelineThumbnailSize = 64.dp
    
    /** Largura mínima de um trim handle */
    val trimHandleMinWidth = 24.dp
}
