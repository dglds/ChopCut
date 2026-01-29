package com.chopcut.core.designsystem.tokens

import androidx.compose.ui.unit.dp

/**
 * Design Tokens para espaçamento no ChopCut.
 * 
 * Use estes valores para garantir consistência em todo o app.
 * Baseado em uma escala de 4dp (grid de 4).
 */
object SpacingTokens {
    
    /** 0.dp - Sem espaçamento */
    val none = 0.dp
    
    /** 2.dp - Espaçamento extra pequeno (xs) */
    val xs = 2.dp
    
    /** 4.dp - Espaçamento muito pequeno */
    val sm = 4.dp
    
    /** 8.dp - Espaçamento pequeno (padrão entre elementos relacionados) */
    val md = 8.dp
    
    /** 12.dp - Espaçamento médio-pequeno */
    val ml = 12.dp
    
    /** 16.dp - Espaçamento médio (padrão entre seções) */
    val lg = 16.dp
    
    /** 20.dp - Espaçamento médio-grande */
    val xl = 20.dp
    
    /** 24.dp - Espaçamento grande (padding de cards) */
    val xxl = 24.dp
    
    /** 32.dp - Espaçamento extra grande (seções principais) */
    val xxxl = 32.dp
    
    /** 48.dp - Espaçamento super grande (hero sections) */
    val huge = 48.dp
    
    /** 64.dp - Espaçamento máximo recomendado */
    val massive = 64.dp
    
    // ============================================================================
    // GRID E LAYOUT
    // ============================================================================
    
    /** Tamanho do grid base (4dp) - use múltiplos deste valor */
    val gridBase = 4.dp
    
    /** Padding horizontal padrão para telas */
    val screenHorizontalPadding = 16.dp
    
    /** Padding vertical padrão para telas */
    val screenVerticalPadding = 16.dp
    
    /** Espaçamento padrão entre itens de lista */
    val listItemSpacing = 8.dp
    
    /** Espaçamento entre elementos de um formulário */
    val formElementSpacing = 16.dp
}
