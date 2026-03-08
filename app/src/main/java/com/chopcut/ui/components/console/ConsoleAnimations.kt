package com.chopcut.ui.components.console

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun slideInFromBottom(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    duration: Int = 300
): Modifier {
    val slideProgress by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 300f
        ),
        label = "slideIn"
    )
    
    return modifier.graphicsLayer {
        translationY = size.height * slideProgress
        alpha = 1f - slideProgress * 0.3f
    }
}

@Composable
fun slideInFromTop(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    duration: Int = 300
): Modifier {
    val slideProgress by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 300f
        ),
        label = "slideInTop"
    )
    
    return modifier.graphicsLayer {
        translationY = -size.height * slideProgress
        alpha = 1f - slideProgress * 0.3f
    }
}

@Composable
fun fadeInWithDelay(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    delay: Int = 0,
    duration: Int = 300
): Modifier {
    val fadeIn by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = duration,
            delayMillis = delay,
            easing = FastOutSlowInEasing
        ),
        label = "fadeIn"
    )
    
    return modifier.graphicsLayer {
        alpha = fadeIn
        translationY = (1f - fadeIn) * 20f
    }
}

@Composable
fun scaleIn(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    duration: Int = 250
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 400f
        ),
        label = "scaleIn"
    )
    
    val scaleAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = duration,
            easing = FastOutSlowInEasing
        ),
        label = "scaleInAlpha"
    )
    
    return modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = scaleAlpha
    }
}

@Composable
fun pulseEffect(
    modifier: Modifier = Modifier,
    isPulsing: Boolean = true,
    minScale: Float = 0.95f,
    maxScale: Float = 1.05f,
    duration: Int = 1000
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    return if (isPulsing) {
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    } else {
        modifier
    }
}

@Composable
fun shimmerEffect(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAlpha"
    )
    
    return if (isEnabled) {
        modifier.graphicsLayer {
            alpha = 0.95f + (shimmerAlpha * 0.05f)
        }
    } else {
        modifier
    }
}

@Composable
fun bounceIn(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    duration: Int = 400
): Modifier {
    val bounce by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 200f
        ),
        label = "bounceIn"
    )
    
    val scale = 1f + (kotlin.math.sin(bounce * kotlin.math.PI.toFloat() * 3f) * 0.1f * (1f - bounce))
    
    return modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationY = bounce * 50f
        alpha = 1f - bounce * 0.5f
    }
}

@Composable
fun expandHeight(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    minHeight: Float,
    maxHeight: Float
): Modifier {
    val heightProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 350f
        ),
        label = "expandHeight"
    )
    
    val currentHeight = minHeight + (maxHeight - minHeight) * heightProgress
    
    return modifier.graphicsLayer {
        scaleY = currentHeight / size.height
    }
}