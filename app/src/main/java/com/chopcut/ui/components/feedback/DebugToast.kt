package com.chopcut.ui.components.feedback

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
    maxHeight: Float = 350f,
    onClose: () -> Unit = {}
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
            .border(1.dp, TerminalBorder, shape)
            .background(TerminalBg)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        TitleBar(
            entryCount = entries.size,
            expanded = expanded,
            onToggleExpand = { expanded = !expanded },
            onClose = onClose
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = TerminalBorder
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = displayHeight),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(entries) { entry ->
                DebugEntryItem(entry)
            }
        }

        StatusBar(entryCount = entries.size)
    }
}

@Composable
private fun TitleBar(
    entryCount: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TitleBarBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(DotRed, CircleShape))
            Box(Modifier.size(6.dp).background(DotYellow, CircleShape))
            Box(Modifier.size(6.dp).background(DotGreen, CircleShape))

            Spacer(Modifier.width(8.dp))

            Text(
                text = "DEBUG",
                color = TerminalGreenMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text = "[$entryCount]",
                color = TerminalGreenDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.3.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        if (expanded) TerminalGreenDim.copy(alpha = 0.3f) else TitleBarBg,
                        RoundedCornerShape(4.dp)
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (expanded) "▼" else "▲",
                    color = if (expanded) TerminalGreen else TerminalGreenDim,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        TerminalBorder.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .padding(2.dp)
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
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
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
            .background(TerminalBg.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[$timestamp]",
            color = TerminalGreenDim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(end = 6.dp)
        )

        Text(
            text = entry.message,
            color = TerminalGreen,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            lineHeight = 14.sp,
            letterSpacing = 0.2.sp,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusBar(entryCount: Int) {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = TerminalBorder
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TitleBarBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .background(TerminalGreen, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "LIVE",
            color = TerminalGreenDim,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}