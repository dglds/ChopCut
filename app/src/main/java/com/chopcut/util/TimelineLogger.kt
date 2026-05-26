package com.chopcut.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logger de telemetria assíncrono e de alta performance.
 *
 * Registra movimentações de posição da timeline em arquivo privado no Android
 * sem causar jank na thread principal a 120 FPS.
 */
object TimelineLogger {
    private const val TAG = "TimelineTelemetry"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logChannel = Channel<String>(capacity = Channel.UNLIMITED)
    
    private var logFile: File? = null
    
    private var lastLogTimeNs = 0L
    private var lastPositionMs = -1L

    init {
        // Iniciar o worker assíncrono em background
        logScope.launch {
            for (logLine in logChannel) {
                writeLineToFile(logLine)
            }
        }
    }

    /**
     * Inicializa o arquivo físico de telemetria.
     * Limpa logs antigos para termos uma nova telemetria fresca a cada edição.
     */
    fun init(context: Context) {
        try {
            val externalDir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(externalDir, "timeline_telemetry.log")
            
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()
            logFile = file
            
            lastLogTimeNs = System.nanoTime()
            lastPositionMs = -1L
            
            logSystem("==========================================================================")
            logSystem("Timeline Telemetry Logger Inicializado.")
            logSystem("Diretório do Log: ${file.absolutePath}")
            logSystem("==========================================================================")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar arquivo de log", e)
        }
    }

    /**
     * Enfileira uma mensagem de movimentação de forma assíncrona.
     */
    fun logMovement(mode: String, positionMs: Long, reason: String) {
        val nowNs = System.nanoTime()
        val nowMs = System.currentTimeMillis()
        val formattedTime = timeFormat.format(Date(nowMs))
        
        // Variação de tempo decorrido desde a última telemetria
        val deltaTempoMs = (nowNs - lastLogTimeNs) / 1_000_000.0
        lastLogTimeNs = nowNs
        
        // Variação da posição de playback do vídeo
        val deltaPosMs = if (lastPositionMs == -1L) 0L else positionMs - lastPositionMs
        lastPositionMs = positionMs
        
        // Frequência instantânea do evento (Hz)
        val instantHz = if (deltaTempoMs > 0.0) 1000.0 / deltaTempoMs else 0.0
        
        val logLine = String.format(
            Locale.US,
            "[%s] [%-6s] Pos: %5dms (DeltaPos: %+4dms) | DeltaTempo: %5.1fms | Freq: %5.1fHz | Reason: %s",
            formattedTime,
            mode,
            positionMs,
            deltaPosMs,
            deltaTempoMs,
            instantHz,
            reason
        )
        
        logChannel.trySend(logLine)
        Log.d(TAG, logLine)
    }

    fun getLogFile(): File? = logFile

    private fun logSystem(message: String) {
        val logLine = String.format(
            Locale.US,
            "[%s] [SYSTEM] %s",
            timeFormat.format(Date()),
            message
        )
        logChannel.trySend(logLine)
        Log.i(TAG, logLine)
    }

    private fun writeLineToFile(line: String) {
        val file = logFile ?: return
        try {
            FileWriter(file, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println(line)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gravar linha de log no arquivo", e)
        }
    }
}
