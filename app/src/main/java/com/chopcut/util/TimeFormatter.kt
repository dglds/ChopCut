package com.chopcut.util

import java.util.Locale

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    // Formato MM:SS.cc (Minutos totais : Segundos . Centésimos)
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
}
