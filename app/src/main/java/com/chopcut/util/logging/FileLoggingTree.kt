package com.chopcut.util.logging

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLoggingTree(context: Context) : Timber.Tree() {

    private val logFile: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "app_logs.txt")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Log apenas ERROR e ASSERT
        if (priority < Log.ERROR) return

        try {
            val timestamp = dateFormat.format(Date())
            val priorityStr = when (priority) {
                Log.ERROR -> "ERROR"
                Log.ASSERT -> "ASSERT"
                else -> return
            }

            // Filtro: apenas com.chopcut
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
                    FileWriter(logFile, true).use { writer ->
                        writer.append(logMessage)
                    }
                } catch (e: IOException) {
                    Log.e("FileLoggingTree", "Failed to write log to file", e)
                }
            }

        } catch (e: Exception) {
            Log.e("FileLoggingTree", "Error in logging mechanism", e)
        }
    }
}
