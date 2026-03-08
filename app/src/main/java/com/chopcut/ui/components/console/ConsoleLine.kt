package com.chopcut.ui.components.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.LocalContext
import com.chopcut.util.debug.LogLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedLevel by viewModel.selectedLevel.collectAsStateWithLifecycle()
    val maxLines by viewModel.maxDisplayLines.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
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
    
    // Smart Auto-scroll: Only scroll if user is already at the bottom
    val isAtBottom = remember {
        derivedStateOf {
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItemIndex >= totalItems - 2
        }
    }

    LaunchedEffect(logHistory.size) {
        if (logHistory.isNotEmpty() && isAtBottom.value) {
            listState.animateScrollToItem(logHistory.size - 1)
        }
    }

    // Lógica para detectar triplo toque
    var tapCount by remember { mutableStateOf(0) }
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            delay(300)
            if (tapCount >= 3) {
                viewModel.clear()
            }
            tapCount = 0
        }
    }
    
    if (isVisible) {
        val dynamicHeight = if (isMultiLine) {
            // Header (aprox 48dp) + (linhas * altura da linha) + padding
            (48 + (maxLines * (theme.fontSize.toInt() + 4)) + 16).dp
        } else {
            40.dp
        }

        Column(
            modifier = modifier
                .padding(8.dp)
                .fillMaxWidth()
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
                        if (verticalDrag > 50) viewModel.dismiss()
                    }
                }
        ) {
            if (isMultiLine) {
                // Header com Busca e Ações na mesma linha
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Filter...", color = theme.textColor.copy(alpha = 0.5f), fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = theme.textColor
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        singleLine = true
                    )
                    
                    IconButton(onClick = { viewModel.copyToClipboard(context) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = theme.textColor, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { viewModel.clear() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = theme.textColor, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { viewModel.toggleMultiLine() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.CloseFullscreen, contentDescription = "Minimize", tint = theme.textColor, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dynamicHeight)
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
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
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
                
                // Indicador de novos logs (LED)
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
}
