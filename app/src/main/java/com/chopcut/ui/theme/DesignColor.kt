package com.chopcut.ui.theme

import androidx.compose.ui.graphics.Color

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
