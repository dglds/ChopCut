package com.chopcut.util.logging

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLoggingTree(context: Context) {

    private val logDir: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    init {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        logDir = File(baseDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
    }

    private fun getCurrentLogFile(): File {
        val today = fileNameFormat.format(Date())
        return File(logDir, "app_errors_$today.txt")
    }

    fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Logging removed
    }

    private fun cleanOldLogs() {
        // Logging removed
    }

    fun getLogDir(): File = logDir
}

fun Context.getLogDir(): File {
    val baseDir = this.getExternalFilesDir(null) ?: this.filesDir
    return File(baseDir, "logs")
}

fun Context.getCurrentLogFile(): File {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = dateFormat.format(Date())
    return File(this.getLogDir(), "app_errors_$today.txt")
}
