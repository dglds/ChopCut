package com.chopcut.util.logging

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalFileLoggingTree {

    private val projectDir = File(System.getProperty("user.dir") ?: ".")
    private val logsDir = File(projectDir, "logs")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        cleanAllLogs()
    }

    private fun getCurrentLogFile(): File {
        val today = fileNameFormat.format(Date())
        return File(logsDir, "app_errors_$today.txt")
    }

    fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Logging removed
    }

    private fun cleanAllLogs() {
        // Logging removed
    }

    private fun cleanOldLogs() {
        // Logging removed
    }

    fun getLogDir(): File = logsDir
}
