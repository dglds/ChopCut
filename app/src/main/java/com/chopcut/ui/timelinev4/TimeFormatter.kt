package com.chopcut.ui.timelinev4

import java.util.Locale

object TimeFormatter {
    fun formatTimecode(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val millis = (timeMs % 1000) / 10 // Show 2 digits (centiseconds)

        return String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, millis)
    }
}
