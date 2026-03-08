package com.chopcut.ui.components.console

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlin.math.max
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConsoleLine(
    viewModel: ConsoleLineViewModel,
    modifier: Modifier = Modifier
) {
    val logHistory by viewModel.logHistory.collectAsStateWithLifecycle()
    val theme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val isVisible by viewModel.isVisible.collectAsStateWithLifecycle()
    val hasPendingLogs by viewModel.hasPendingLogs.collectAsStateWithLifecycle()
    val isMultiLine by viewModel.isMultiLine.collectAsStateWithLifecycle()
    
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
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll para o final quando novos logs chegam
    LaunchedEffect(logHistory.size) {
        if (logHistory.isNotEmpty()) {
            listState.animateScrollToItem(logHistory.size - 1)
        }
    }

    // Lógica para detectar triplo toque
    var tapCount by remember { mutableStateOf(0) }
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            delay(300) // Janela de tempo para o triplo toque
            if (tapCount >= 3) {
                viewModel.clear()
            }
            tapCount = 0
        }
    }
    
    if (isVisible) {
        Box(
            modifier = modifier
                .padding(8.dp)
                .fillMaxWidth()
                .height(if (isMultiLine) 200.dp else 40.dp)
                .background(
                    color = theme.backgroundColor.copy(alpha = 0.9f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .border(
                    width = 1.dp,
                    color = theme.textColor.copy(alpha = 0.2f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        val verticalDrag = dragAmount.y
                        when {
                            verticalDrag > 50 -> viewModel.dismiss()
                            verticalDrag < -50 -> viewModel.setPosition(ConsoleLineViewModel.ConsolePosition.HEADER)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapCount++ },
                        onDoubleTap = { viewModel.toggleMultiLine() },
                        onLongPress = { viewModel.togglePosition() }
                    )
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(logHistory) { log ->
                    val levelColor = when {
                        log.fullText.contains("[ERROR]") -> Color(0xFFFF5252)
                        log.fullText.contains("[WARN]") -> Color(0xFFFFD740)
                        log.fullText.contains("[INFO]") -> Color(0xFF40C4FF)
                        log.fullText.contains("[VERBOSE]") -> Color(0xFFBDBDBD)
                        else -> theme.textColor
                    }

                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = levelColor.copy(alpha = 0.7f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (theme.fontSize - 1).sp
                                )
                            ) {
                                append("[${log.count}]")
                            }
                            withStyle(
                                SpanStyle(
                                    color = levelColor,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = theme.fontSize.sp
                                )
                            ) {
                                append(" ${log.tag}")
                            }
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = theme.fontSize.sp
                                )
                            ) {
                                append(log.message)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = if (isMultiLine) Int.MAX_VALUE else 1
                    )
                }
            }
            
            if (hasPendingLogs) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(6.dp)
                        .alpha(alpha)
                        .background(
                            color = theme.textColor,
                            shape = CircleShape
                        )
                        .align(androidx.compose.ui.Alignment.TopEnd)
                )
            }
        }
    }
}