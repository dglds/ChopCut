package com.chopcut.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// DESIGN SYSTEM CHOPCUT - Paleta de Cores
// Video Editor Mobile | Cinema Dark + Play Red
// ============================================

// -----------------------
// Primary (Accent Blue)
// -----------------------
val Primary = Color(0xFF0000FF)            // CTA, Play button
val OnPrimary = Color(0xFFFFFFFF)          // Texto sobre Primary
val PrimaryContainer = Color(0xFF0000FF)   // BG de botão secundário
val OnPrimaryContainer = Color(0xFFFFFFFF) // Texto sobre container

// -----------------------
// Background (Pure Black)
// -----------------------
val Background = Color(0xFF000000)         // Fundo principal (Dark)
val OnBackground = Color(0xFFFFFFFF)       // Texto sobre fundo
val BackgroundLight = Color(0xFFFFFFFF)    // Fundo (Light)
val OnBackgroundLight = Color(0xFF000000)  // Texto sobre fundo (Light)

// -----------------------
// Surface (Pure Black)
// -----------------------
val Surface = Color(0xFF000000)            // Cards/Paneis (Dark)
val OnSurface = Color(0xFFFFFFFF)          // Texto sobre surface (Dark)
val SurfaceVariant = Color(0xFF000000)     // Elevação (Dark)
val SurfaceLight = Color(0xFFFFFFFF)       // Cards (Light)
val OnSurfaceLight = Color(0xFF000000)     // Texto sobre surface (Light)

// -----------------------
// Functional Colors (Mapped to Black/White/Blue to keep it simple)
// -----------------------
val Success = Color(0xFF0000FF)            // Export OK
val SuccessDark = Color(0xFF0000FF)        // Export OK (Dark mode)
val Warning = Color(0xFFFFFFFF)            // Alertas
val WarningDark = Color(0xFFFFFFFF)        // Alertas (Dark mode)
val Error = Color(0xFF0000FF)              // Erros (mapped to blue or white for extreme simplicity)
val ErrorDark = Color(0xFF0000FF)          // Erros (Dark mode)
val Info = Color(0xFF0000FF)               // Informações
val InfoDark = Color(0xFF0000FF)           // Informações (Dark mode)

// -----------------------
// Text States
// -----------------------
val TextPrimary = Color(0xFFFFFFFF)        // Texto principal (Dark)
val TextSecondary = Color(0xFFFFFFFF)      // Texto secundário (Dark)
val TextDisabled = Color(0xFF888888)       // Texto desabilitado (Dark)
val TextPrimaryLight = Color(0xFF000000)   // Texto principal (Light)
val TextSecondaryLight = Color(0xFF000000) // Texto secundário (Light)
val TextDisabledLight = Color(0xFF888888)  // Texto desabilitado (Light)

// -----------------------
// Timeline Colors (Dark/Flat)
// -----------------------
val TimelineBackground = Color(0xFF000000) // Fundo da timeline
val TimelineTrack = Color(0xFF000000)      // Trilha de vídeo
val Playhead = Color(0xFF0000FF)           // Indicador de play
val SelectionOverlay = Color(0x660000FF)   // Seleção ativa (com alpha)
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
val Border = Color(0xFFFFFFFF)             // Bordas (Dark)
val BorderLight = Color(0xFF000000)        // Bordas (Light)
val Divider = Color(0xFFFFFFFF)            // Divisores (Dark)
val DividerLight = Color(0xFF000000)       // Divisores (Light)

// -----------------------
// Extension functions para alpha
// -----------------------
fun Color.alpha(alpha: Float): Color = this.copy(alpha = alpha)

// Alpha constantes para timeline
val SelectionAlpha = 0.3f                  // 30% de opacidade para seleção
val DisabledAlpha = 0.5f                   // 50% para estado disabled
val PressedAlpha = 0.7f                    // 70% para estado pressed
val HoverAlpha = 0.85f                     // 85% para estado hover
