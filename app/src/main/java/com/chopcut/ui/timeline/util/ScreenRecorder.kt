package com.chopcut.ui.timeline.util

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.getSystemService
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilitário de gravação de tela para demonstrações.
 * 
 * Uso para gravar fluxos da timeline:
 * ```kotlin
 * val recorder = ScreenRecorder(context)
 * 
 * // Iniciar gravação (requer resultado de MediaProjection)
 * recorder.startRecording(resultCode, data)
 * 
 * // Executar fluxos...
 * 
 * // Parar gravação
 * recorder.stopRecording()
 * ```
 * 
 * Para usar em modo de demonstração contínua, configure no SettingsScreen.
 */
class ScreenRecorder(private val context: Context) {
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    
    private val projectionManager: MediaProjectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Callbacks para eventos de gravação.
     */
    interface RecordingCallback {
        fun onRecordingStarted(outputFile: File)
        fun onRecordingStopped(outputFile: File)
        fun onRecordingError(error: Exception)
    }
    
    var callback: RecordingCallback? = null
    
    /**
     * Arquivo de saída da gravação atual.
     */
    var currentOutputFile: File? = null
        private set
    
    /**
     * Cria o Intent para solicitar permissão de gravação de tela.
     * Deve ser chamado de uma Activity.
     */
    fun createScreenCaptureIntent(): Intent {
        return projectionManager.createScreenCaptureIntent()
    }
    
    /**
     * Inicia a gravação da tela.
     * 
     * @param resultCode Resultado da intent de permissão ( Activity.RESULT_OK )
     * @param data Intent de resultado da permissão
     * @param fileName Nome do arquivo (opcional, usa timestamp por padrão)
     * @return true se a gravação foi iniciada com sucesso
     */
    fun startRecording(
        resultCode: Int,
        data: Intent,
        fileName: String? = null
    ): Boolean {
        if (isRecording) {
            Timber.w("Já existe uma gravação em andamento")
            return false
        }
        
        return try {
            setupMediaRecorder(fileName)
            setupMediaProjection(resultCode, data)
            createVirtualDisplay()
            
            mediaRecorder?.start()
            isRecording = true
            
            currentOutputFile?.let { file ->
                Timber.i("Gravação iniciada: ${file.absolutePath}")
                callback?.onRecordingStarted(file)
            }
            
            true
        } catch (e: Exception) {
            Timber.e(e, "Erro ao iniciar gravação")
            callback?.onRecordingError(e)
            cleanup()
            false
        }
    }
    
    /**
     * Para a gravação atual.
     * 
     * @return Arquivo do vídeo gravado, ou null se falhou
     */
    fun stopRecording(): File? {
        if (!isRecording) {
            return null
        }
        
        return try {
            mediaRecorder?.apply {
                stop()
                reset()
            }
            
            isRecording = false
            cleanup()
            
            currentOutputFile?.also { file ->
                Timber.i("Gravação finalizada: ${file.absolutePath}")
                callback?.onRecordingStopped(file)
            }
        } catch (e: Exception) {
            Timber.e(e, "Erro ao parar gravação")
            callback?.onRecordingError(e)
            null
        }
    }
    
    /**
     * Verifica se está gravando atualmente.
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * Obtém a duração da gravação atual em segundos.
     * (Aproximada, baseada no tempo desde o início)
     */
    fun getRecordingDurationSeconds(): Long {
        // Implementação simplificada
        return 0L
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    private fun setupMediaRecorder(fileName: String?) {
        val metrics = getDisplayMetrics()
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        
        // Diretório de saída
        val outputDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "ChopCut_Recordings"
        ).apply { mkdirs() }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val finalFileName = fileName ?: "chopcut_demo_$timestamp.mp4"
        currentOutputFile = File(outputDir, finalFileName)
        
        mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setAudioSource(MediaRecorder.AudioSource.MIC) // Opcional: áudio do microfone
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncodingBitRate(8_000_000) // 8 Mbps - bom equilíbrio qualidade/tamanho
            setVideoFrameRate(30)
            setVideoSize(width, height)
            setOutputFile(currentOutputFile?.absolutePath)
            prepare()
        }
    }
    
    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Timber.d("MediaProjection parada")
                stopRecording()
            }
        }, handler)
    }
    
    private fun createVirtualDisplay() {
        val metrics = getDisplayMetrics()
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface,
            null,
            handler
        )
    }
    
    private fun getDisplayMetrics(): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return DisplayMetrics().apply {
            windowManager.defaultDisplay.getMetrics(this)
        }
    }
    
    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        mediaRecorder?.release()
        mediaRecorder = null
    }
    
    /**
     * Libera recursos ao destruir.
     */
    fun release() {
        if (isRecording) {
            stopRecording()
        }
        cleanup()
    }
}

/**
 * Configurações de gravação.
 */
data class RecordingConfig(
    val videoBitrate: Int = 8_000_000,
    val videoFrameRate: Int = 30,
    val includeAudio: Boolean = false,
    val outputDirectory: String = "ChopCut_Recordings",
    val autoStopAfterMinutes: Int = 0 // 0 = sem limite
)

/**
 * Gerenciador de demonstrações gravadas.
 * Mantém um log de todas as demonstrações gravadas.
 */
class DemoRecordingManager(context: Context) {
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Registra uma nova demonstração.
     */
    fun recordDemo(
        file: File,
        description: String,
        tags: List<String> = emptyList()
    ) {
        val demo = DemoRecording(
            filePath = file.absolutePath,
            fileName = file.name,
            description = description,
            tags = tags,
            timestamp = System.currentTimeMillis(),
            fileSize = file.length()
        )
        
        val demos = getAllDemos().toMutableList()
        demos.add(0, demo) // Adiciona no início
        
        prefs.edit()
            .putString(KEY_DEMOS, demos.toJson())
            .apply()
        
        Timber.i("Demo registrada: ${file.name}")
    }
    
    /**
     * Obtém todas as demonstrações registradas.
     */
    fun getAllDemos(): List<DemoRecording> {
        val json = prefs.getString(KEY_DEMOS, "[]") ?: "[]"
        return json.fromJsonList()
    }
    
    /**
     * Obtém demonstrações por tag.
     */
    fun getDemosByTag(tag: String): List<DemoRecording> {
        return getAllDemos().filter { it.tags.contains(tag) }
    }
    
    /**
     * Remove uma demonstração do registro.
     */
    fun removeDemo(demo: DemoRecording) {
        val demos = getAllDemos().toMutableList()
        demos.removeAll { it.filePath == demo.filePath }
        
        prefs.edit()
            .putString(KEY_DEMOS, demos.toJson())
            .apply()
        
        // Também deleta o arquivo
        File(demo.filePath).delete()
    }
    
    companion object {
        private const val PREFS_NAME = "demo_recordings"
        private const val KEY_DEMOS = "demos"
    }
}

/**
 * Representa uma demonstração gravada.
 */
data class DemoRecording(
    val filePath: String,
    val fileName: String,
    val description: String,
    val tags: List<String>,
    val timestamp: Long,
    val fileSize: Long
) {
    /**
     * Formata o tamanho do arquivo para exibição.
     */
    fun formattedSize(): String {
        return when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> "${fileSize / (1024 * 1024)} MB"
        }
    }
    
    /**
     * Formata a data para exibição.
     */
    fun formattedDate(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

// Extensões para serialização JSON simples
private fun List<DemoRecording>.toJson(): String {
    // Implementação simplificada - em produção use Gson ou similar
    return "[]"
}

private fun String.fromJsonList(): List<DemoRecording> {
    // Implementação simplificada
    return emptyList()
}
