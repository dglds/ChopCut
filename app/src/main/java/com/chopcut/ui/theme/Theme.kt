package com.chopcut.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================
// DESIGN SYSTEM CHOPCUT - Tema
// Cinema Dark + Play Red
// ============================================

/**
 * Color Scheme Dark (padrão para editor de vídeo)
 * Modo escuro cinematográfico para foco no conteúdo
 */
private val ChopCutDarkColorScheme = darkColorScheme(
    // Primary (Play Red)
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,

    // Background (Cinema Dark)
    background = Background,
    onBackground = OnBackground,

    // Surface (Cards/Panels)
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,

    // Error states
    error = ErrorDark,
    onError = Color.White,
    errorContainer = Error,
    onErrorContainer = Color.White,

    // Outros
    outline = Border,
    outlineVariant = Divider
)

/**
 * Color Scheme Light (para ambientes claros)
 * Mantém a identidade visual com adaptação para luz
 */
private val ChopCutLightColorScheme = lightColorScheme(
    // Primary (Play Red - mantido)
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,

    // Background
    background = BackgroundLight,
    onBackground = OnBackgroundLight,

    // Surface
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFF1F5F9),

    // Error states
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFFDAE5FF),
    onErrorContainer = Color(0xFF001F3F),

    // Outros
    outline = BorderLight,
    outlineVariant = DividerLight
)

@Composable
fun ChopCutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color desabilitado por padrão para manter identidade visual
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ChopCutDarkColorScheme else ChopCutLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChopCutTypography,
        content = content
    )
}

// ============================================
// Extensões úteis para o tema
// ============================================

/**
 * Retorna a cor apropriada para texto sobre fundo (baseada no tema)
 */
@Composable
fun textOnBackground() = MaterialTheme.colorScheme.onBackground

/**
 * Retorna a cor apropriada para texto sobre surface
 */
@Composable
fun textOnSurface() = MaterialTheme.colorScheme.onSurface

/**
 * Retorna a cor primária do tema
 */
@Composable
fun primaryColor() = MaterialTheme.colorScheme.primary

/**
 * Retorna a cor de surface do tema
 */
@Composable
fun surfaceColor() = MaterialTheme.colorScheme.surface
