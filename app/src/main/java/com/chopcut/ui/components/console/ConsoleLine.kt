package com.chopcut.ui.components.console

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ConsoleLine(
    viewModel: ConsoleLineViewModel,
    modifier: Modifier = Modifier
) {
    val logs = viewModel.logs.collectAsStateWithLifecycle()
    val logHistory = viewModel.logHistory.collectAsStateWithLifecycle()
    val theme = viewModel.currentTheme.collectAsStateWithLifecycle()
    val isVisible = viewModel.isVisible.collectAsStateWithLifecycle()
    val hasPendingLogs = viewModel.hasPendingLogs.collectAsStateWithLifecycle()
    val isMultiLine = viewModel.isMultiLine.collectAsStateWithLifecycle()
    val maxDisplayLines = viewModel.maxDisplayLines.collectAsStateWithLifecycle()
    
    val infiniteTransition = rememberInfiniteTransition(label = "led")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ledAlpha"
    )
    
    if (isVisible.value) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(theme.value.backgroundColor)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        val horizontalDrag = dragAmount.x
                        val verticalDrag = dragAmount.y
                        
                        when {
                            horizontalDrag < -100 -> viewModel.dismiss()
                            verticalDrag < -100 -> viewModel.setPosition(ConsoleLineViewModel.ConsolePosition.HEADER)
                            verticalDrag > 100 -> viewModel.setPosition(ConsoleLineViewModel.ConsolePosition.FOOTER)
                        }
                    }
                }
                .then(
                    if (theme.value.scanlineEnabled) {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(
                                color = Color(0x10000000),
                                size = size,
                                style = Stroke(width = 1f)
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            if (hasPendingLogs.value) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(6.dp)
                        .alpha(alpha)
                        .background(
                            color = theme.value.textColor,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            if (isMultiLine.value) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    logHistory.value.forEach { log ->
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = theme.value.textColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = theme.value.fontSize.sp
                                    )
                                ) {
                                    append("[${log.count}]")
                                }
                                withStyle(
                                    SpanStyle(
                                        color = theme.value.textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = theme.value.fontSize.sp
                                    )
                                ) {
                                    append(log.tag)
                                }
                                append(" ")
                                withStyle(
                                    SpanStyle(
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = theme.value.fontSize.sp
                                    )
                                ) {
                                    append(log.message)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                    }
                }
            } else {
                logs.value?.let { log ->
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = theme.value.textColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = theme.value.fontSize.sp
                                )
                            ) {
                                append("[${log.count}]")
                            }
                            withStyle(
                                SpanStyle(
                                    color = theme.value.textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = theme.value.fontSize.sp
                                )
                            ) {
                                append(log.tag)
                            }
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = theme.value.fontSize.sp
                                )
                            ) {
                                append(log.message)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}


