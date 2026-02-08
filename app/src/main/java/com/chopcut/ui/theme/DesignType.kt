package com.chopcut.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chopcut.R

// ============================================
// DESIGN SYSTEM CHOPCUT - Tipografia
// Heading: Fredoka | Body: Nunito | Mono: Roboto Mono
// ============================================

// Fontes personalizadas do projeto
private val FredokaFont = FontFamily(
    Font(R.font.fredoka, FontWeight.Normal)
)

private val NunitoFont = FontFamily(
    Font(R.font.nunito, FontWeight.Normal)
)

private val RobotoMonoFont = FontFamily(
    Font(R.font.roboto_mono, FontWeight.Normal)
)

// Alias para uso no Design System
internal val ChopCutHeadingFont = FredokaFont
internal val ChopCutBodyFont = NunitoFont
internal val ChopCutMonoFont = RobotoMonoFont

/**
 * Tipografia do Design System ChopCut
 *
 * Escala baseada em Material 3 adaptada para editor de vídeos
 */
val ChopCutTypography = Typography(
    // Display - Títulos hero (raramente usado em mobile)
    displayLarge = TextStyle(
        fontFamily = ChopCutHeadingFont,
        fontWeight = FontWeight.W700,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = ChopCutHeadingFont,
        fontWeight = FontWeight.W700,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = ChopCutHeadingFont,
        fontWeight = FontWeight.W700,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // Headline - Títulos de tela
    headlineLarge = TextStyle(
        fontFamily = ChopCutHeadingFont,
        fontWeight = FontWeight.W700,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ChopCutHeadingFont,
        fontWeight = FontWeight.W700,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ChopCutHeadingFont,
        fontWeight = FontWeight.W600,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // Title - Títulos de seção
    titleLarge = TextStyle(
        fontFamily = ChopCutBodyFont,
        fontWeight = FontWeight.W600,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ChopCutBodyFont,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ChopCutBodyFont,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body - Texto corrido (principal)
    bodyLarge = TextStyle(
        fontFamily = ChopCutBodyFont,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ChopCutBodyFont,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ChopCutBodyFont,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // Label - Rótulos, botões, tags
    labelLarge = TextStyle(
        fontFamily = ChopCutBodyFont,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ChopCutBodyFont,
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ChopCutBodyFont,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// ============================================
// Estilos Específicos para Video Editor
// ============================================

/**
 * Estilo para duração de vídeos (timestamps)
 * Usa fonte monoespaçada para alinhamento correto
 */
val DurationTextStyle = TextStyle(
    fontFamily = ChopCutMonoFont,
    fontWeight = FontWeight.W400,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp
)

/**
 * Estilo para botões principais
 */
val ButtonTextStyle = TextStyle(
    fontFamily = ChopCutBodyFont,
    fontWeight = FontWeight.W600,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.2.sp
)

/**
 * Estilo para cards de vídeo (título)
 */
val VideoCardTitleStyle = TextStyle(
    fontFamily = ChopCutBodyFont,
    fontWeight = FontWeight.W500,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)
