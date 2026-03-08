package com.chopcut.ui.components.console

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

object HapticFeedback {
    
    private fun vibrate(context: Context, pattern: LongArray, repeat: Int = -1) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(pattern, repeat)
            }
        }
    }
    
    fun lightTap(context: Context) {
        vibrate(context, longArrayOf(0, 10))
    }
    
    fun mediumTap(context: Context) {
        vibrate(context, longArrayOf(0, 20))
    }
    
    fun heavyTap(context: Context) {
        vibrate(context, longArrayOf(0, 30, 10, 30))
    }
    
    fun success(context: Context) {
        vibrate(context, longArrayOf(0, 30, 50, 30, 50, 50))
    }
    
    fun error(context: Context) {
        vibrate(context, longArrayOf(0, 100, 50, 100, 50, 100))
    }
    
    fun warning(context: Context) {
        vibrate(context, longArrayOf(0, 50, 50, 50))
    }
    
    fun toggleOn(context: Context) {
        vibrate(context, longArrayOf(0, 25, 10, 15))
    }
    
    fun toggleOff(context: Context) {
        vibrate(context, longArrayOf(0, 15))
    }
    
    fun expand(context: Context) {
        vibrate(context, longArrayOf(0, 10, 10, 15, 10, 20))
    }
    
    fun collapse(context: Context) {
        vibrate(context, longArrayOf(0, 20, 10, 15, 10, 10))
    }
    
    fun clear(context: Context) {
        vibrate(context, longArrayOf(0, 15, 10, 15, 10, 15, 10, 30))
    }
    
    fun copy(context: Context) {
        vibrate(context, longArrayOf(0, 20, 50, 20))
    }
    
    fun search(context: Context) {
        vibrate(context, longArrayOf(0, 10, 5, 10, 5, 10))
    }
}

@Composable
fun rememberHapticInteractionSource(
    context: Context,
    onPress: () -> Unit = {},
    onRelease: () -> Unit = {}
): MutableInteractionSource {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            HapticFeedback.lightTap(context)
            onPress()
        } else {
            onRelease()
        }
    }
    
    return interactionSource
}

data class RippleConfig(
    val color: androidx.compose.ui.graphics.Color,
    val radius: Float = 300f,
    val duration: Int = 500,
    val alpha: Float = 0.3f
)

object RipplePresets {
    val DEFAULT = RippleConfig(
        color = androidx.compose.ui.graphics.Color.White,
        radius = 300f,
        duration = 500,
        alpha = 0.3f
    )
    
    val NEON = RippleConfig(
        color = androidx.compose.ui.graphics.Color(0xFF00FF00),
        radius = 400f,
        duration = 600,
        alpha = 0.4f
    )
    
    val CYBER = RippleConfig(
        color = androidx.compose.ui.graphics.Color(0xFF00FFFF),
        radius = 350f,
        duration = 700,
        alpha = 0.35f
    )
    
    val AMBER = RippleConfig(
        color = androidx.compose.ui.graphics.Color(0xFFFFB000),
        radius = 320f,
        duration = 550,
        alpha = 0.35f
    )
}

enum class HapticAction {
    LIGHT_TAP,
    MEDIUM_TAP,
    HEAVY_TAP,
    SUCCESS,
    ERROR,
    WARNING,
    TOGGLE_ON,
    TOGGLE_OFF,
    EXPAND,
    COLLAPSE,
    CLEAR,
    COPY,
    SEARCH
}

fun performHaptic(context: Context, action: HapticAction) {
    when (action) {
        HapticAction.LIGHT_TAP -> HapticFeedback.lightTap(context)
        HapticAction.MEDIUM_TAP -> HapticFeedback.mediumTap(context)
        HapticAction.HEAVY_TAP -> HapticFeedback.heavyTap(context)
        HapticAction.SUCCESS -> HapticFeedback.success(context)
        HapticAction.ERROR -> HapticFeedback.error(context)
        HapticAction.WARNING -> HapticFeedback.warning(context)
        HapticAction.TOGGLE_ON -> HapticFeedback.toggleOn(context)
        HapticAction.TOGGLE_OFF -> HapticFeedback.toggleOff(context)
        HapticAction.EXPAND -> HapticFeedback.expand(context)
        HapticAction.COLLAPSE -> HapticFeedback.collapse(context)
        HapticAction.CLEAR -> HapticFeedback.clear(context)
        HapticAction.COPY -> HapticFeedback.copy(context)
        HapticAction.SEARCH -> HapticFeedback.search(context)
    }
}