package com.chopcut.core.designsystem.tokens

import androidx.compose.ui.graphics.Color

/**
 * Design Tokens para cores do ChopCut.
 * 
 * Estes valores são a fonte única de verdade para todas as cores do app.
 * Não use valores hardcoded diretamente nos componentes.
 */
object ColorTokens {
    
    // ============================================================================
    // BRAND COLORS - Identidade visual do ChopCut
    // ============================================================================
    val brandPrimary = Color(0xFF6650a4)
    val brandOnPrimary = Color(0xFFFFFFFF)
    val brandPrimaryContainer = Color(0xFFEADDFF)
    val brandOnPrimaryContainer = Color(0xFF21005D)
    
    val brandSecondary = Color(0xFF625b71)
    val brandOnSecondary = Color(0xFFFFFFFF)
    val brandSecondaryContainer = Color(0xFFE8DEF8)
    val brandOnSecondaryContainer = Color(0xFF1D192B)
    
    val brandTertiary = Color(0xFF7D5260)
    val brandOnTertiary = Color(0xFFFFFFFF)
    val brandTertiaryContainer = Color(0xFFFFD8E4)
    val brandOnTertiaryContainer = Color(0xFF31111D)
    
    // ============================================================================
    // SEMANTIC COLORS - Estados e feedback
    // ============================================================================
    val success = Color(0xFF4CAF50)
    val onSuccess = Color(0xFFFFFFFF)
    val successContainer = Color(0xFFE8F5E9)
    val onSuccessContainer = Color(0xFF1B5E20)
    
    val warning = Color(0xFFFF9800)
    val onWarning = Color(0xFF000000)
    val warningContainer = Color(0xFFFFF3E0)
    val onWarningContainer = Color(0xFFE65100)
    
    val error = Color(0xFFE53935)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = Color(0xFFFFE5E5)
    val onErrorContainer = Color(0xFFB71C1C)
    
    val info = Color(0xFF2196F3)
    val onInfo = Color(0xFFFFFFFF)
    val infoContainer = Color(0xFFE3F2FD)
    val onInfoContainer = Color(0xFF0D47A1)
    
    // ============================================================================
    // NEUTRAL COLORS - Fundos, superfícies e textos
    // ============================================================================
    val surface = Color(0xFFFFFBFE)
    val onSurface = Color(0xFF1C1B1F)
    val onSurfaceVariant = Color(0xFF49454F)
    
    val surfaceVariant = Color(0xFFE7E0EC)
    val onSurfaceVariantLight = Color(0xFF49454F)
    
    val background = Color(0xFFFFFBFE)
    val onBackground = Color(0xFF1C1B1F)
    
    val outline = Color(0xFF79747E)
    val outlineVariant = Color(0xFFCAC4D0)
    
    // ============================================================================
    // DARK THEME - Cores para tema escuro
    // ============================================================================
    object Dark {
        val surface = Color(0xFF1C1B1F)
        val onSurface = Color(0xFFE6E1E5)
        val onSurfaceVariant = Color(0xFFCAC4D0)
        
        val surfaceVariant = Color(0xFF49454F)
        val background = Color(0xFF1C1B1F)
        val onBackground = Color(0xFFE6E1E5)
        
        val outline = Color(0xFF938F99)
        val outlineVariant = Color(0xFF49454F)
    }
    
    // ============================================================================
    // VIDEO EDITOR SPECIFIC - Cores específicas para edição de vídeo
    // ============================================================================
    val timelineBackground = Color(0xFF1A1A2E)
    val timelineWaveform = Color(0xFF0F3460)
    val timelinePlayhead = Color(0xFFE53935)
    val timelineSelection = Color(0x4D6650A4)  // 30% opacity
    val timelineText = Color(0xFFFFFFFF)
    
    val videoSurface = Color(0xFF000000)
    val videoControlsBackground = Color(0xB3000000)  // 70% opacity
}
