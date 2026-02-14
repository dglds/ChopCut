package com.chopcut.util.logging

import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalFileLoggingTree : Timber.Tree() {

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

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.ERROR) return

        try {
            val timestamp = dateFormat.format(Date())
            val priorityStr = when (priority) {
                Log.ERROR -> "ERROR"
                Log.ASSERT -> "ASSERT"
                else -> return
            }

            val isOurPackage = t?.stackTrace?.any { it.className.contains("com.chopcut") } ?: true
            val tagIsOurPackage = tag?.contains("com.chopcut") ?: true
            
            if (!isOurPackage && !tagIsOurPackage) return

            val logMessage = buildString {
                append("[$timestamp] $priorityStr/$tag: $message\n")
                t?.let {
                    append(Log.getStackTraceString(it))
                    append("\n")
                }
            }

            synchronized(this) {
                try {
                    FileWriter(getCurrentLogFile(), true).use { writer ->
                        writer.append(logMessage)
                    }
                    cleanOldLogs()
                } catch (e: IOException) {
                    System.err.println("LocalFileLoggingTree: Failed to write log to file")
                    e.printStackTrace()
                }
            }

        } catch (e: Exception) {
            System.err.println("LocalFileLoggingTree: Error in logging mechanism")
            e.printStackTrace()
        }
    }

    private fun cleanAllLogs() {
        try {
            logsDir.listFiles()?.filter { file ->
                file.name.startsWith("app_errors_")
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            System.err.println("LocalFileLoggingTree: Failed to clean all logs")
            e.printStackTrace()
        }
    }

    private fun cleanOldLogs() {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            logsDir.listFiles()?.filter { file ->
                file.name.startsWith("app_errors_") && file.lastModified() < sevenDaysAgo
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            System.err.println("LocalFileLoggingTree: Failed to clean old logs")
            e.printStackTrace()
        }
    }

    fun getLogDir(): File = logsDir
}
