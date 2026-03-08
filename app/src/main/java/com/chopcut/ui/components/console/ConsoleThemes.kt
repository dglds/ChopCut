package com.chopcut.ui.components.console

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

data class ConsoleTheme(
    val backgroundColor: Color,
    val textColor: Color,
    val fontSize: Float,
    val fontFamily: FontFamily,
    val scanlineEnabled: Boolean = false,
    val crtEnabled: Boolean = false,
    val glowIntensity: Float = 0f,
    val borderColor: Color = Color.Transparent,
    val headerColor: Color = Color.Transparent
)

object ConsoleThemes {
    
    val DEFAULT = ConsoleTheme(
        backgroundColor = Color(0xFF212121),
        textColor = Color(0xFF00FF00),
        fontSize = 8f,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = false,
        crtEnabled = false,
        glowIntensity = 0f,
        borderColor = Color(0xFF333333),
        headerColor = Color(0xFF2D2D2D)
    )
    
    val EIGHTIES = ConsoleTheme(
        backgroundColor = Color(0xFF0A0A0A),
        textColor = Color(0xFF33FF33),
        fontSize = 9f,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = true,
        crtEnabled = true,
        glowIntensity = 0.3f,
        borderColor = Color(0xFF00FF00),
        headerColor = Color(0xFF111111)
    )
    
    val AMBER_EIGHTIES = ConsoleTheme(
        backgroundColor = Color(0xFF0D0D0D),
        textColor = Color(0xFFFFB000),
        fontSize = 9f,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = true,
        crtEnabled = true,
        glowIntensity = 0.35f,
        borderColor = Color(0xFFFFB000),
        headerColor = Color(0xFF141414)
    )
    
    val CYBERPUNK = ConsoleTheme(
        backgroundColor = Color(0xFF050510),
        textColor = Color(0xFF00FFFF),
        fontSize = 9f,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = true,
        crtEnabled = true,
        glowIntensity = 0.5f,
        borderColor = Color(0xFF00FFFF),
        headerColor = Color(0xFF0A0A1A)
    )
    
    val MATRIX = ConsoleTheme(
        backgroundColor = Color(0xFF000000),
        textColor = Color(0xFF00FF00),
        fontSize = 10f,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = false,
        crtEnabled = false,
        glowIntensity = 0.4f,
        borderColor = Color(0xFF003300),
        headerColor = Color(0xFF001100)
    )
    
    val FIRE = ConsoleTheme(
        backgroundColor = Color(0xFF0A0000),
        textColor = Color(0xFFFF6600),
        fontSize = 9f,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = true,
        crtEnabled = true,
        glowIntensity = 0.4f,
        borderColor = Color(0xFFFF3300),
        headerColor = Color(0xFF110000)
    )
    
    val CUSTOM_THEME = ConsoleTheme(
        backgroundColor = Color(0xFF121212),
        textColor = Color(0xFFE0E0E0),
        fontSize = 10f,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = false,
        crtEnabled = false,
        glowIntensity = 0f,
        borderColor = Color(0xFF2A2A2A),
        headerColor = Color(0xFF1A1A1A)
    )
}
