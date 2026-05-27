package com.chopcut

import android.app.Activity
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat


// --- Merged from Animation.kt ---


/**
 * Durações de animação do Design System ChopCut
 *
 * Baseado em Material Motion com ajustes para editor de vídeo
 */
object ChopCutAnimation {
    const val Fast = 50       // Micro-interações instantâneas
    const val Normal = 100    // Transições padrão
    const val Slow = 150      // Transições de tela
}

/**
 * Easing curves do Design System ChopCut
 */
object ChopCutEasing {
    /**
     * Padrão agora é linear para sensação brutalista e seca
     */
    val Standard = LinearEasing

    /**
     * Transições diretas
     */
    val Emphasized = LinearEasing

    /**
     * Linear puro
     */
    val Linear = LinearEasing

    /**
     * Para saídas rápidas
     */
    val FastOutLinearIn = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    /**
     * Para entradas suaves
     */
    val LinearOutSlowIn = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
}

/**
 * Animation specs pré-configurados
 */
object ChopCutAnimationSpec {
    /**
     * Spec para animações rápidas (feedback visual)
     */
    fun fast() = TweenSpec<Float>(
        durationMillis = ChopCutAnimation.Fast,
        easing = ChopCutEasing.Standard
    )

    /**
     * Spec para animações normais
     */
    fun normal() = TweenSpec<Float>(
        durationMillis = ChopCutAnimation.Normal,
        easing = ChopCutEasing.Standard
    )

    /**
     * Spec para animações lentas
     */
    fun slow() = TweenSpec<Float>(
        durationMillis = ChopCutAnimation.Slow,
        easing = ChopCutEasing.Emphasized
    )

    /**
     * Spec para animações com spring
     * Útil para movimentos naturais
     */
    fun spring() = SpringSpec<Float>(
        dampingRatio = 0.85f,
        stiffness = 300f
    )
}

/**
 * Respeita a configuração de movimento reduzido do sistema
 * @param defaultSpec Spec padrão quando movimento reduzido não está ativo
 */
@Composable
fun respectReducedMotion(defaultSpec: TweenSpec<Float>): TweenSpec<Float> {
    // TODO: Implementar verificação de reduced motion quando disponível
    // Por enquanto, retorna sempre o spec padrão
    return defaultSpec
}

/**
 * Tipos de transição suportados
 */
enum class ChopCutTransition {
    FADE,
    SLIDE_HORIZONTAL,
    SLIDE_VERTICAL,
    SCALE,
    NONE
}

// --- Merged from Color.kt ---


val Purple80 = Color(0xFFFFFFFF)
val PurpleGrey80 = Color(0xFFCCCCCC)
val Pink80 = Color(0xFF0000FF)

val Purple40 = Color(0xFF000000)
val PurpleGrey40 = Color(0xFF333333)
val Pink40 = Color(0xFF0000CC)
// --- Merged from DesignColor.kt ---


// ============================================
// DESIGN SYSTEM CHOPCUT - Paleta de Cores
// Video Editor Mobile | Cinema Dark + Play Red
// ============================================

// -----------------------
// Primary (Accent Blue Soft)
// -----------------------
val Primary = Color(0xFF6366F1)            // Indigo 500 (Soft Blue Accent)
val OnPrimary = Color(0xFFFFFFFF)          // Texto sobre Primary
val PrimaryContainer = Color(0xFF818CF8)   // Indigo 400
val OnPrimaryContainer = Color(0xFFFFFFFF) // Texto sobre container

// -----------------------
// Background (Pure Black -> Soft Dark)
// -----------------------
val Background = Color(0xFF171717)         // Fundo principal (Soft Dark)
val OnBackground = Color(0xFFFFFFFF)       // Texto sobre fundo
val BackgroundLight = Color(0xFFFAFAFA)    // Fundo (Light - Off white)
val OnBackgroundLight = Color(0xFF171717)  // Texto sobre fundo (Light)

// -----------------------
// Surface (Pure Black -> Soft Dark)
// -----------------------
val Surface = Color(0xFF262626)            // Cards/Paneis (Lighter Dark)
val OnSurface = Color(0xFFFFFFFF)          // Texto sobre surface (Dark)
val SurfaceVariant = Color(0xFF333333)     // Elevação (Dark)
val SurfaceLight = Color(0xFFFFFFFF)       // Cards (Light)
val OnSurfaceLight = Color(0xFF171717)     // Texto sobre surface (Light)

// -----------------------
// Functional Colors (Mapped to Black/White/Blue to keep it simple)
// -----------------------
val Success = Color(0xFF10B981)            // Export OK (Emerald)
val SuccessDark = Color(0xFF10B981)        // Export OK
val Warning = Color(0xFFF59E0B)            // Alertas (Amber)
val WarningDark = Color(0xFFF59E0B)        // Alertas
val Error = Color(0xFFEF4444)              // Erros (Red)
val ErrorDark = Color(0xFFEF4444)          // Erros
val Info = Color(0xFF6366F1)               // Informações (Indigo)
val InfoDark = Color(0xFF6366F1)           // Informações

// -----------------------
// Text States
// -----------------------
val TextPrimary = Color(0xFFFFFFFF)        // Texto principal (Dark)
val TextSecondary = Color(0xFFD4D4D4)      // Texto secundário (Dark)
val TextDisabled = Color(0xFF737373)       // Texto desabilitado (Dark)
val TextPrimaryLight = Color(0xFF171717)   // Texto principal (Light)
val TextSecondaryLight = Color(0xFF525252) // Texto secundário (Light)
val TextDisabledLight = Color(0xFFA3A3A3)  // Texto desabilitado (Light)

// -----------------------
// Timeline Colors (Dark/Flat)
// -----------------------
val TimelineBackground = Color(0xFF171717) // Fundo da timeline
val TimelineTrack = Color(0xFF262626)      // Trilha de vídeo
val Playhead = Color(0xFF6366F1)           // Indicador de play (Indigo)
val SelectionOverlay = Color(0x666366F1)   // Seleção ativa (com alpha)
val TrimHandle = Color(0xFFFFFFFF)         // Alças de corte
val Waveform = Color(0xFFFFFFFF)           // Forma de onda

// -----------------------
// Overlay & Elevation
// -----------------------
val OverlayDark = Color(0xCC000000)        // 80% black overlay
val OverlayMedium = Color(0x80000000)      // 50% black overlay
val OverlayLight = Color(0x4D000000)       // 30% black overlay
val GradientTransparent = Color(0x00000000) // Transparente para gradient

// -----------------------
// Border & Divider
// -----------------------
val Border = Color(0xFF525252)             // Bordas (Dark)
val BorderLight = Color(0xFF171717)        // Bordas (Light)
val Divider = Color(0xFF404040)            // Divisores (Dark)
val DividerLight = Color(0xFFE5E5E5)       // Divisores (Light)

// -----------------------
// Extension functions para alpha
// -----------------------
fun Color.alpha(alpha: Float): Color = this.copy(alpha = alpha)

// Alpha constantes para timeline
val SelectionAlpha = 0.3f                  // 30% de opacidade para seleção
val DisabledAlpha = 0.5f                   // 50% para estado disabled
val PressedAlpha = 0.7f                    // 70% para estado pressed
val HoverAlpha = 0.85f                     // 85% para estado hover

// --- Merged from DesignType.kt ---


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

// --- Merged from Spacing.kt ---


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

// --- Merged from Theme.kt ---


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
    surfaceVariant = SurfaceLight,

    // Error states
    error = Error,
    onError = Color.White,
    errorContainer = Color.White,
    onErrorContainer = Color.Black,

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

// --- Merged from Type.kt ---


// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)