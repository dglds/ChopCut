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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlin.math.max
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val callStackMode = viewModel.callStackMode.collectAsStateWithLifecycle()
    
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
    
    val scrollState = rememberScrollState()
    
    // Auto-scroll para o final quando novos logs chegam
    LaunchedEffect(logHistory.value.size) {
        if (scrollState.canScrollForward) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
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
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .height(
                    if (isMultiLine.value) 200.dp else 20.dp
                )
        ) {
            if (hasPendingLogs.value) {
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(4.dp)
                        .alpha(alpha)
                        .background(
                            color = theme.value.textColor,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            
            if (isMultiLine.value) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    Column {
                    if (callStackMode.value) {
                        // Modo Call Stack: empilhar logs por tag
                        val logsByTag = logHistory.value.groupBy { it.tag }
                        
                        logsByTag.forEach { (tag, entries) ->
                            Column(
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                entries.forEachIndexed { index, log ->
                                    // Calcular tamanho proporcional: a cada 2 linhas, diminui
                                    val scaleFactor = max(0.5f, 1f - (index / 10f))
                                    val fontSizeValue = (theme.value.fontSize * scaleFactor).coerceAtLeast(6f)
                                    val fontSize = fontSizeValue.sp
                                    
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(
                                                SpanStyle(
                                                    color = theme.value.textColor,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = fontSize
                                                )
                                            ) {
                                                append("[${log.count}]")
                                            }
                                            withStyle(
                                                SpanStyle(
                                                    color = theme.value.textColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = fontSize
                                                )
                                            ) {
                                                append(log.tag)
                                            }
                                            append(" ")
                                            withStyle(
                                                SpanStyle(
                                                    color = Color.White,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = fontSize
                                                )
                                            ) {
                                                append(log.message)
                                            }
                                        },
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = (index * 8).dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Modo normal
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


