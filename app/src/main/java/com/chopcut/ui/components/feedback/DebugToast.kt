package com.chopcut.ui.components.feedback

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TerminalBg = Color(0xF2101418)
private val TerminalBorder = Color(0xFF1E2A1E)
private val TerminalGreen = Color(0xFF00FF41)
private val TerminalGreenMuted = Color(0xFF00CC33)
private val TerminalGreenDim = Color(0xFF0A7A2B)
private val TitleBarBg = Color(0xFF161B1F)

private val DotRed = Color(0xFFFF5F57)
private val DotYellow = Color(0xFFFFBD2E)
private val DotGreen = Color(0xFF28C840)

@Composable
fun DebugToast(
    text: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(shape)
            .border(1.dp, TerminalBorder, shape)
            .background(TerminalBg)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .heightIn(max = 300.dp)
    ) {
        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TitleBarBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Window dots
            Box(Modifier.size(8.dp).background(DotRed, CircleShape))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(8.dp).background(DotYellow, CircleShape))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(8.dp).background(DotGreen, CircleShape))

            Spacer(Modifier.width(12.dp))

            Text(
                text = "chopcut ~ preload",
                color = TerminalGreenMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = TerminalBorder
        )

        // Log content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = text,
                color = TerminalGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                lineHeight = 16.sp,
                letterSpacing = 0.3.sp
            )
        }

        // Bottom status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TitleBarBg)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(TerminalGreen, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "DEBUG",
                color = TerminalGreenDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}
