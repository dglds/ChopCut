package com.chopcut.util.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gerenciador de captura de screenshots e vídeos para debug.
 * Usado durante testes para documentar visualmente o comportamento.
 */
class DebugCaptureManager(private val context: Context) {
    
    companion object {
        private const val TAG = "[DebugCapture]"
    }
    
    private var videoRecorder: MediaRecorder? = null
    private var isRecording = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureScheduler: ScheduledExecutorService? = null
    private var videoStartTime: Long = 0L
    
    /**
     * Captura screenshot de uma View e salva em arquivo.
     * 
     * @param view View a ser capturada
     * @param label Identificador do screenshot (ex: "thumbnail_load_50pct")
     * @return File? Arquivo salvo ou null se falhou
     */
    suspend fun captureScreenshot(view: View, label: String): File? = withContext(Dispatchers.IO) {
        if (!DebugConfig.isScreenshotsEnabled()) {
            return@withContext null
        }
        
        try {
            val filename = generateFilename(label, "png")
            val file = File(DebugConfig.getScreenshotsDirectory(), filename)
            
            // Captura bitmap da view
            val bitmap = captureViewToBitmap(view)
            
            if (bitmap != null) {
                // Adiciona overlay de debug se necessário
                val finalBitmap = if (DebugConfig.SHOW_DEBUG_OVERLAYS) {
                    addDebugOverlay(bitmap, label)
                } else {
                    bitmap
                }
                
                // Salva arquivo
                FileOutputStream(file).use { out ->
                    finalBitmap.compress(
                        if (DebugConfig.SCREENSHOT_FORMAT == "PNG") 
                            Bitmap.CompressFormat.PNG 
                        else 
                            Bitmap.CompressFormat.JPEG,
                        DebugConfig.SCREENSHOT_QUALITY,
                        out
                    )
                }
                
                // Limpa bitmap se criamos overlay
                if (DebugConfig.SHOW_DEBUG_OVERLAYS && finalBitmap != bitmap) {
                    finalBitmap.recycle()
                }
                
                Timber.d("$TAG Screenshot salvo: ${file.absolutePath}")
                file
            } else {
                Timber.w("$TAG Falha ao capturar bitmap da view")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG Erro ao capturar screenshot: ${e.message}")
            null
        }
    }
    
    /**
     * Captura screenshot da timeline em momento específico.
     * Método convenience para o ThumbnailLoader.
     * 
     * @param view View da timeline
     * @param eventType Tipo do evento ("start", "progress", "complete", "error")
     * @param progress Progresso atual (0-100)
     */
    suspend fun captureTimelineScreenshot(
        view: View,
        eventType: String,
        progress: Int = 0
    ): File? {
        val label = "timeline_${eventType}_${progress}pct"
        return captureScreenshot(view, label)
    }
    
    /**
     * Inicia gravação de vídeo da timeline.
     * Apenas disponível em API 29+ (Android 10+)
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun startVideoRecording(
        window: Window,
        onError: ((Exception) -> Unit)? = null
    ) {
        if (!DebugConfig.isVideoEnabled() || isRecording) {
            return
        }
        
        try {
            val filename = generateFilename("timeline_recording", "mp4")
            val file = File(DebugConfig.getVideosDirectory(), filename)
            
            videoRecorder = MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(DebugConfig.VIDEO_WIDTH, DebugConfig.VIDEO_WIDTH * 16 / 9)
                setVideoFrameRate(DebugConfig.VIDEO_FPS)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            
            isRecording = true
            videoStartTime = System.currentTimeMillis()
            
            // Agenda parada automática se necessário
            if (DebugConfig.VIDEO_MAX_DURATION_SECONDS > 0) {
                mainHandler.postDelayed({
                    stopVideoRecording()
                }, DebugConfig.VIDEO_MAX_DURATION_SECONDS * 1000L)
            }
            
            Timber.d("$TAG Gravação de vídeo iniciada: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG Erro ao iniciar gravação: ${e.message}")
            onError?.invoke(e)
        }
    }
    
    /**
     * Para gravação de vídeo.
     */
    fun stopVideoRecording(): File? {
        if (!isRecording || videoRecorder == null) {
            return null
        }
        
        return try {
            videoRecorder?.apply {
                stop()
                reset()
                release()
            }
            
            val duration = System.currentTimeMillis() - videoStartTime
            Timber.d("$TAG Gravação finalizada após ${duration}ms")
            
            isRecording = false
            videoRecorder = null
            
            // Retorna arquivo gravado
            DebugConfig.getVideosDirectory()
                .listFiles()
                ?.maxByOrNull { it.lastModified() }
                
        } catch (e: Exception) {
            Timber.e(e, "$TAG Erro ao parar gravação: ${e.message}")
            isRecording = false
            videoRecorder = null
            null
        }
    }
    
    /**
     * Inicia captura periódica automática durante testes.
     * Captura screenshots em intervalos regulares.
     */
    fun startAutoCapture(view: View, intervalMs: Long = 1000L) {
        if (!DebugConfig.isScreenshotsEnabled()) return
        
        stopAutoCapture()
        
        captureScheduler = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleAtFixedRate({
                try {
                    mainHandler.post {
                        captureSnapshot(view, "auto_${System.currentTimeMillis()}")
                    }
                } catch (e: Exception) {
                    Timber.w("$TAG Erro na captura automática: ${e.message}")
                }
            }, 0, intervalMs, TimeUnit.MILLISECONDS)
        }
        
        Timber.d("$TAG Captura automática iniciada (intervalo: ${intervalMs}ms)")
    }
    
    /**
     * Para captura automática.
     */
    fun stopAutoCapture() {
        captureScheduler?.apply {
            shutdown()
            try {
                awaitTermination(1, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                shutdownNow()
            }
        }
        captureScheduler = null
        Timber.d("$TAG Captura automática parada")
    }
    
    /**
     * Captura snapshot de debug com informações adicionais.
     * Útil para documentar estado em momento específico.
     */
    private fun captureSnapshot(view: View, label: String) {
        // Usa coroutines de forma fire-and-forget
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            captureScreenshot(view, label)
        }
    }
    
    /**
     * Captura View para Bitmap.
     * Usa PixelCopy para SurfaceViews ou drawToBitmap para Views normais.
     */
    private suspend fun captureViewToBitmap(view: View): Bitmap? = 
        suspendCancellableCoroutine { continuation ->
            try {
                if (view is SurfaceView) {
                    // Para SurfaceView (ExoPlayer), usa PixelCopy
                    val bitmap = Bitmap.createBitmap(
                        view.width.coerceAtLeast(1),
                        view.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    
                    PixelCopy.request(
                        view,
                        bitmap,
                        { result ->
                            if (result == PixelCopy.SUCCESS) {
                                continuation.resume(bitmap)
                            } else {
                                bitmap.recycle()
                                continuation.resume(null)
                            }
                        },
                        mainHandler
                    )
                } else {
                    // Para Views normais, usa Canvas
                    val bitmap = Bitmap.createBitmap(
                        view.width.coerceAtLeast(1),
                        view.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    view.draw(canvas)
                    continuation.resume(bitmap)
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    
    /**
     * Adiciona overlay informativo ao screenshot.
     */
    private fun addDebugOverlay(original: Bitmap, label: String): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val paint = Paint().apply {
            color = Color.BLACK
            alpha = 160
            style = Paint.Style.FILL
        }
        
        // Fundo semi-transparente para texto
        val textHeight = 60f
        canvas.drawRect(0f, 0f, result.width.toFloat(), textHeight, paint)
        
        // Texto
        paint.apply {
            color = Color.WHITE
            alpha = 255
            textSize = 24f
        }
        
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            .format(Date())
        canvas.drawText("$label | $timestamp", 10f, 40f, paint)
        
        // Borda de debug
        paint.apply {
            color = Color.GREEN
            alpha = 128
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), paint)
        
        return result
    }
    
    /**
     * Gera nome de arquivo timestamped.
     */
    private fun generateFilename(label: String, extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
            .format(Date())
        return "${label}_${timestamp}.${extension}"
    }
    
    /**
     * Limpa arquivos antigos de debug.
     */
    fun cleanupOldFiles(maxAgeHours: Int = 24) {
        val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000)
        
        listOf(
            DebugConfig.getScreenshotsDirectory(),
            DebugConfig.getVideosDirectory()
        ).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                    Timber.d("$TAG Arquivo antigo removido: ${file.name}")
                }
            }
        }
    }
    
    /**
     * Libera recursos.
     */
    fun release() {
        stopAutoCapture()
        stopVideoRecording()
    }
}
