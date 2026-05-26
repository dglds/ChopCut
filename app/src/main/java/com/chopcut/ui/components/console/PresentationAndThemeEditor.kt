package com.chopcut.ui.components.console

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.RectangleShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PresentationMode(
    val isEnabled: Boolean = false,
    val autoScroll: Boolean = true,
    val showAnnotations: Boolean = true,
    val highlightErrors: Boolean = true,
    val currentSlide: Int = 0,
    val slides: List<PresentationSlide> = emptyList()
)

data class PresentationSlide(
    val id: String,
    val title: String,
    val description: String,
    val logs: List<com.chopcut.util.debug.LogEntry>,
    val timestamp: Long = System.currentTimeMillis()
)

class PresentationManager {
    
    private val _mode = MutableStateFlow(PresentationMode())
    val mode: StateFlow<PresentationMode> = _mode.asStateFlow()
    
    fun enablePresentationMode() {
        _mode.value = _mode.value.copy(isEnabled = true, currentSlide = 0)
    }
    
    fun disablePresentationMode() {
        _mode.value = _mode.value.copy(isEnabled = false, currentSlide = 0)
    }
    
    fun togglePresentationMode() {
        _mode.value = _mode.value.copy(
            isEnabled = !_mode.value.isEnabled,
            currentSlide = if (!_mode.value.isEnabled) 0 else _mode.value.currentSlide
        )
    }
    
    fun createSlide(title: String, description: String, logs: List<com.chopcut.util.debug.LogEntry>) {
        val slide = PresentationSlide(
            id = "slide_${System.currentTimeMillis()}",
            title = title,
            description = description,
            logs = logs
        )
        
        val currentSlides = _mode.value.slides.toMutableList()
        currentSlides.add(slide)
        
        _mode.value = _mode.value.copy(slides = currentSlides)
    }
    
    fun clearSlides() {
        _mode.value = _mode.value.copy(slides = emptyList(), currentSlide = 0)
    }
    
    fun goToSlide(index: Int) {
        if (index >= 0 && index < _mode.value.slides.size) {
            _mode.value = _mode.value.copy(currentSlide = index)
        }
    }
    
    fun nextSlide() {
        val nextIndex = _mode.value.currentSlide + 1
        if (nextIndex < _mode.value.slides.size) {
            _mode.value = _mode.value.copy(currentSlide = nextIndex)
        }
    }
    
    fun previousSlide() {
        val prevIndex = _mode.value.currentSlide - 1
        if (prevIndex >= 0) {
            _mode.value = _mode.value.copy(currentSlide = prevIndex)
        }
    }
    
    fun toggleAutoScroll() {
        _mode.value = _mode.value.copy(autoScroll = !_mode.value.autoScroll)
    }
    
    fun toggleShowAnnotations() {
        _mode.value = _mode.value.copy(showAnnotations = !_mode.value.showAnnotations)
    }
    
    fun toggleHighlightErrors() {
        _mode.value = _mode.value.copy(highlightErrors = !_mode.value.highlightErrors)
    }
}

@Composable
fun ThemeEditorDialog(
    currentTheme: ConsoleTheme,
    onThemeChanged: (ConsoleTheme) -> Unit,
    onDismiss: () -> Unit
) {
    var backgroundColor by remember { mutableStateOf(currentTheme.backgroundColor) }
    var textColor by remember { mutableStateOf(currentTheme.textColor) }
    var fontSize by remember { mutableStateOf(currentTheme.fontSize) }
    var scanlineEnabled by remember { mutableStateOf(currentTheme.scanlineEnabled) }
    var crtEnabled by remember { mutableStateOf(currentTheme.crtEnabled) }
    var glowIntensity by remember { mutableStateOf(currentTheme.glowIntensity) }
    var borderColor by remember { mutableStateOf(currentTheme.borderColor) }
    var headerColor by remember { mutableStateOf(currentTheme.headerColor) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = RectangleShape,
            color = Color(0xFF1E1E1E),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFF333333)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Theme Editor",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SettingRow(
                            title = "Background Color",
                            value = backgroundColor,
                            onValueChange = { backgroundColor = it }
                        )
                    }
                    
                    item {
                        SettingRow(
                            title = "Text Color",
                            value = textColor,
                            onValueChange = { textColor = it }
                        )
                    }
                    
                    item {
                        FontSizeSetting(
                            title = "Font Size",
                            value = fontSize,
                            onValueChange = { fontSize = it }
                        )
                    }
                    
                    item {
                        ToggleSetting(
                            title = "Scanlines",
                            value = scanlineEnabled,
                            onValueChange = { scanlineEnabled = it }
                        )
                    }
                    
                    item {
                        ToggleSetting(
                            title = "CRT Effect",
                            value = crtEnabled,
                            onValueChange = { crtEnabled = it }
                        )
                    }
                    
                    item {
                        GlowIntensitySetting(
                            title = "Glow Intensity",
                            value = glowIntensity,
                            onValueChange = { glowIntensity = it }
                        )
                    }
                    
                    item {
                        SettingRow(
                            title = "Border Color",
                            value = borderColor,
                            onValueChange = { borderColor = it }
                        )
                    }
                    
                    item {
                        SettingRow(
                            title = "Header Color",
                            value = headerColor,
                            onValueChange = { headerColor = it }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF444444)
                                )
                            ) {
                                Text("Cancel", color = Color.White)
                            }
                            
                            Button(
                                onClick = {
                                    onThemeChanged(
                                        ConsoleTheme(
                                            backgroundColor = backgroundColor,
                                            textColor = textColor,
                                            fontSize = fontSize,
                                            fontFamily = FontFamily.Monospace,
                                            scanlineEnabled = scanlineEnabled,
                                            crtEnabled = crtEnabled,
                                            glowIntensity = glowIntensity,
                                            borderColor = borderColor,
                                            headerColor = headerColor
                                        )
                                    )
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Text("Apply", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: Color,
    onValueChange: (Color) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A2A), RectangleShape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(value, RectangleShape)
                .border(
                    1.dp,
                    Color(0xFF444444),
                    RectangleShape
                )
                .clickable {
                }
        )
    }
}

@Composable
private fun FontSizeSetting(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A2A), RectangleShape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(6f)) }) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
            }
            
            Text(
                text = "${value.toInt()}sp",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            IconButton(onClick = { onValueChange((value + 1).coerceAtMost(16f)) }) {
                Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ToggleSetting(
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A2A), RectangleShape)
            .padding(16.dp)
            .clickable { onValueChange(!value) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        
        Switch(
            checked = value,
            onCheckedChange = onValueChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF4CAF50),
                uncheckedThumbColor = Color(0xFF666666),
                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                uncheckedTrackColor = Color(0xFF444444)
            )
        )
    }
}

@Composable
private fun GlowIntensitySetting(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A2A), RectangleShape)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp
            )
            
            Text(
                text = "${(value * 100).toInt()}%",
                color = Color(0xFF4CAF50),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4CAF50),
                activeTrackColor = Color(0xFF4CAF50),
                inactiveTrackColor = Color(0xFF444444)
            )
        )
    }
}

fun createCustomTheme(
    backgroundColor: Color,
    textColor: Color,
    fontSize: Float = 10f
): ConsoleTheme {
    return ConsoleTheme(
        backgroundColor = backgroundColor,
        textColor = textColor,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
        scanlineEnabled = false,
        crtEnabled = false,
        glowIntensity = 0f,
        borderColor = Color(0xFF2A2A2A),
        headerColor = Color(0xFF1A1A1A)
    )
}