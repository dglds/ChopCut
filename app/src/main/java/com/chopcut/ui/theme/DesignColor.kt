package com.chopcut.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// DESIGN SYSTEM CHOPCUT - Paleta de Cores
// Video Editor Mobile | Cinema Dark + Play Red
// ============================================

// -----------------------
// Primary (Play Red)
// -----------------------
val Primary = Color(0xFFE11D48)           // CTA, Play button
val OnPrimary = Color(0xFFFFFFFF)          // Texto sobre Primary
val PrimaryContainer = Color(0xFFFFDDE5)   // BG de botão secundário
val OnPrimaryContainer = Color(0xFF9F1239) // Texto sobre container

// -----------------------
// Background (Cinema Dark)
// -----------------------
val Background = Color(0xFF0F0F23)         // Fundo principal (Dark)
val OnBackground = Color(0xFFF8FAFC)       // Texto sobre fundo
val BackgroundLight = Color(0xFFFFFFFF)    // Fundo (Light)
val OnBackgroundLight = Color(0xFF0F172A)  // Texto sobre fundo (Light)

// -----------------------
// Surface (Cards/Panels)
// -----------------------
val Surface = Color(0xFF1E1B4B)            // Cards/Paneis (Dark)
val OnSurface = Color(0xFFF8FAFC)          // Texto sobre surface (Dark)
val SurfaceVariant = Color(0xFF2D2B55)     // Elevação (Dark)
val SurfaceLight = Color(0xFFF8FAFC)       // Cards (Light)
val OnSurfaceLight = Color(0xFF475569)     // Texto sobre surface (Light)

// -----------------------
// Functional Colors
// -----------------------
val Success = Color(0xFF10B981)            // Export OK
val SuccessDark = Color(0xFF059669)        // Export OK (Dark mode)
val Warning = Color(0xFFF59E0B)            // Alertas
val WarningDark = Color(0xFFD97706)        // Alertas (Dark mode)
val Error = Color(0xFFEF4444)              // Erros
val ErrorDark = Color(0xFFDC2626)          // Erros (Dark mode)
val Info = Color(0xFF3B82F6)               // Informações
val InfoDark = Color(0xFF2563EB)           // Informações (Dark mode)

// -----------------------
// Text States
// -----------------------
val TextPrimary = Color(0xFFF8FAFC)        // Texto principal (Dark)
val TextSecondary = Color(0xFF94A3B8)      // Texto secundário (Dark)
val TextDisabled = Color(0xFF475569)       // Texto desabilitado (Dark)
val TextPrimaryLight = Color(0xFF0F172A)   // Texto principal (Light)
val TextSecondaryLight = Color(0xFF475569) // Texto secundário (Light)
val TextDisabledLight = Color(0xFFCBD5E1)  // Texto desabilitado (Light)

// -----------------------
// Timeline Colors (Dark)
// -----------------------
val TimelineBackground = Color(0xFF0A0A1A) // Fundo da timeline
val TimelineTrack = Color(0xFF2A2A4A)      // Trilha de vídeo
val Playhead = Color(0xFFE11D48)           // Indicador de play
val SelectionOverlay = Color(0xE11D48)      // Seleção ativa (com alpha)
val TrimHandle = Color(0xFFFFFFFF)         // Alças de corte
val Waveform = Color(0xFF6366F1)           // Forma de onda

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
val Border = Color(0xFF2D2B55)             // Bordas (Dark)
val BorderLight = Color(0xFFE2E8F0)        // Bordas (Light)
val Divider = Color(0xFF1E1B4B)            // Divisores (Dark)
val DividerLight = Color(0xFFE2E8F0)       // Divisores (Light)

// -----------------------
// Extension functions para alpha
// -----------------------
fun Color.alpha(alpha: Float): Color = this.copy(alpha = alpha)

// Alpha constantes para timeline
val SelectionAlpha = 0.3f                  // 30% de opacidade para seleção
val DisabledAlpha = 0.5f                   // 50% para estado disabled
val PressedAlpha = 0.7f                    // 70% para estado pressed
val HoverAlpha = 0.85f                     // 85% para estado hover
