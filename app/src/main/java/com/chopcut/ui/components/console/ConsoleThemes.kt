package com.chopcut.ui.components.console

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

data class ConsoleTheme(
    val backgroundColor: Color,
    val textColor: Color,
    val fontSize: Float,
    val fontFamily: FontFamily,
    val scanlineEnabled: Boolean = false
)

object ConsoleThemes {
    
    val DEFAULT = ConsoleTheme(
        backgroundColor = Color(0xFF212121), // Cinza escuro (Material Grey 900)
        textColor = Color(0xFF00FF00),
        fontSize = 8f,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = false
    )
    
    val EIGHTIES = ConsoleTheme(
        backgroundColor = Color(0xFF1A1A1A), // Cinza um pouco mais escuro
        textColor = Color(0xFF33FF33),
        fontSize = 8f,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = true
    )
    
    val AMBER_EIGHTIES = ConsoleTheme(
        backgroundColor = Color(0xFF1E1E1E), // Cinza muito escuro
        textColor = Color(0xFFFFB000),
        fontSize = 8f,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = true
    )
}
