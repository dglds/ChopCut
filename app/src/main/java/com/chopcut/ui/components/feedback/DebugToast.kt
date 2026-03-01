package com.chopcut.ui.components.feedback

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chopcut.BuildConfig
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TerminalBg = Color(0xF2101418)
private val TerminalBorder = Color(0xFF1E2A1E)
private val TerminalGreen = Color(0xFF00FF41)
private val TerminalGreenMuted = Color(0xFF00CC33)
private val TerminalGreenDim = Color(0xFF0A7A2B)
private val TitleBarBg = Color(0xFF161B1F)

private val DotRed = Color(0xFFFF5F57)
private val DotYellow = Color(0xFFFFBD2E)
private val DotGreen = Color(0xFF28C840)

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun DebugToast(
    entries: List<DebugEntry>,
    modifier: Modifier = Modifier,
    maxHeight: Float = 300f,
    onClose: () -> Unit = {},
    onTogglePosition: () -> Unit = {}
) {
    if (!BuildConfig.DEBUG || entries.isEmpty()) return

    val shape = RoundedCornerShape(12.dp)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }

    val shouldAutoScroll by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisibleItem?.index == 0
        }
    }

    val displayHeight = if (expanded) maxHeight.dp else 200.dp

    LaunchedEffect(entries) {
        if (shouldAutoScroll && entries.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column(
        modifier = modifier
            .width(320.dp)
            .clip(shape)
            .background(TerminalBg)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        MinimalTitleBar(
            entryCount = entries.size,
            expanded = expanded,
            onToggleExpand = { expanded = !expanded },
            onTogglePosition = onTogglePosition,
            onClose = onClose
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = displayHeight),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(entries) { entry ->
                DebugEntryItem(entry)
            }
        }
    }
}

@Composable
private fun MinimalTitleBar(
    entryCount: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onTogglePosition: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "DEBUG [$entryCount]",
            color = TerminalGreenDim,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Botão de alternar posição (top/bottom)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clickable(
                        onClick = onTogglePosition,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⇅",
                    color = DotYellow,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Botão de expandir/colapsar
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clickable(
                        onClick = onToggleExpand,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (expanded) "▼" else "▲",
                    color = TerminalGreenDim,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Botão de fechar
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clickable(
                        onClick = onClose,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = DotRed,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun DebugEntryItem(entry: DebugEntry) {
    val timestamp = remember(entry.timestamp) {
        timeFormat.format(Date(entry.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[$timestamp]",
            color = TerminalGreenDim,
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 9.sp,
            modifier = Modifier.padding(end = 4.dp)
        )

        Text(
            text = entry.message,
            color = TerminalGreen,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 9.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}