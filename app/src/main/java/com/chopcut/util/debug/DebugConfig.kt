package com.chopcut.util.debug

import android.os.Environment
import java.io.File

/**
 * Configuração central de debug e testes.
 * Todas as flags são hardcoded - alterar aqui para habilitar funcionalidades.
 * 
 * ATENÇÃO: Desativar em builds de release!
 */
object DebugConfig {
    
    // ===== FLAGS PRINCIPAIS =====
    
    /**
     * Habilita modo debug geral
     * Quando true, ativa logs verbose e funcionalidades de debug
     */
    const val DEBUG_MODE = true
    
    /**
     * Habilita logs ultra-verbose do ThumbnailLoader
     * Mostra cada operação de cache (hit/miss/put) no Logcat
     */
    const val VERBOSE_THUMBNAIL_LOGS = true
    
    /**
     * Habilita estatísticas periódicas automáticas
     * Loga estatísticas do cache a cada X segundos
     */
    const val AUTO_LOG_STATS = true
    const val AUTO_LOG_INTERVAL_MS = 5000L // 5 segundos
    
    // ===== SCREENSHOTS =====
    
    /**
     * Habilita captura automática de screenshots durante testes
     */
    const val ENABLE_SCREENSHOTS = true
    
    /**
     * Captura screenshot em momentos chave:
     * - Início de carregamento de thumbnails
     * - Progresso (a cada 25%)
     * - Completude
     * - Erros
     */
    const val SCREENSHOT_ON_THUMBNAIL_EVENTS = true
    
    /**
     * Formato dos screenshots (PNG = qualidade, JPEG = tamanho)
     */
    const val SCREENSHOT_FORMAT = "PNG" // ou "JPEG"
    
    /**
     * Qualidade para JPEG (0-100)
     */
    const val SCREENSHOT_QUALITY = 90
    
    // ===== GRAVAÇÃO DE VÍDEO =====
    
    /**
     * Habilita gravação de vídeo durante testes
     * Grava a timeline em ação para análise posterior
     */
    const val ENABLE_VIDEO_RECORDING = false // Desabilitado por padrão (pesado)
    
    /**
     * FPS da gravação de vídeo
     */
    const val VIDEO_FPS = 15
    
    /**
     * Duração máxima da gravação (em segundos)
     * 0 = ilimitado
     */
    const val VIDEO_MAX_DURATION_SECONDS = 30
    
    /**
     * Resolução do vídeo (largura em pixels)
     * Altura é calculada automaticamente mantendo aspect ratio
     */
    const val VIDEO_WIDTH = 720
    
    // ===== TIMELINE DEBUG =====
    
    /**
     * Mostra overlays visuais de debug na timeline:
     * - Bordas nos frames
     * - Indicadores de cache hit/miss
     * - Estatísticas em tempo real
     */
    const val SHOW_DEBUG_OVERLAYS = true
    
    /**
     * Cor dos overlays para cache HIT (thumbnail carregado)
     */
    const val OVERLAY_HIT_COLOR = 0xFF00FF00 // Verde
    
    /**
     * Cor dos overlays para cache MISS (placeholder)
     */
    const val OVERLAY_MISS_COLOR = 0xFFFF0000 // Vermelho
    
    /**
     * Cor dos overlays para posições que falharam
     */
    const val OVERLAY_FAILED_COLOR = 0xFFFFA500 // Laranja
    
    // ===== PERFOMANCE MONITORING =====
    
    /**
     * Habilita monitoramento de performance
     * Mede tempo de extração, uso de memória, etc
     */
    const val ENABLE_PERF_MONITORING = true
    
    /**
     * Alerta quando extração demora mais que X ms
     */
    const val PERF_SLOW_THRESHOLD_MS = 3000L
    
    /**
     * Alerta quando memória do cache ultrapassa X%
     */
    const val PERF_MEMORY_THRESHOLD_PERCENT = 80
    
    // ===== TESTES AUTOMÁTICOS =====
    
    /**
     * Habilita modo de teste automático
     * Executa cenários de teste automaticamente ao iniciar
     */
    const val ENABLE_AUTO_TEST = false
    
    /**
     * Cenários de teste a executar
     */
    val AUTO_TEST_SCENARIOS = listOf(
        TestScenario.THUMBNAIL_LOADING,
        TestScenario.ZOOM_TRANSITIONS,
        TestScenario.RANGE_MANIPULATION
    )
    
    enum class TestScenario {
        THUMBNAIL_LOADING,
        ZOOM_TRANSITIONS,
        RANGE_MANIPULATION,
        SCRUBBING_PERFORMANCE,
        UNDO_REDO
    }
    
    // ===== PATHS E ARQUIVOS =====
    
    /**
     * Diretório base para arquivos de debug
     */
    fun getDebugDirectory(): File {
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val debugDir = File(baseDir, "ChopCut/Debug")
        if (!debugDir.exists()) {
            debugDir.mkdirs()
        }
        return debugDir
    }
    
    /**
     * Subdiretório para screenshots
     */
    fun getScreenshotsDirectory(): File {
        val dir = File(getDebugDirectory(), "Screenshots")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Subdiretório para vídeos
     */
    fun getVideosDirectory(): File {
        val dir = File(getDebugDirectory(), "Videos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Subdiretório para logs detalhados
     */
    fun getLogsDirectory(): File {
        val dir = File(getDebugDirectory(), "Logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    // ===== HELPERS =====
    
    /**
     * Gera nome de arquivo timestamped
     */
    fun generateTimestampedFilename(prefix: String, extension: String): String {
        val timestamp = System.currentTimeMillis()
        return "${prefix}_${timestamp}.${extension}"
    }
    
    /**
     * Verifica se modo debug está ativo
     */
    fun isDebugMode(): Boolean = DEBUG_MODE
    
    /**
     * Verifica se screenshots estão habilitados
     */
    fun isScreenshotsEnabled(): Boolean = DEBUG_MODE && ENABLE_SCREENSHOTS
    
    /**
     * Verifica se vídeo está habilitado
     */
    fun isVideoEnabled(): Boolean = DEBUG_MODE && ENABLE_VIDEO_RECORDING
    
    /**
     * Log de configuração atual
     */
    fun logConfiguration() {
        if (!DEBUG_MODE) return
        
        println("╔══════════════════════════════════════════════════════════╗")
        println("║           CHOPCUT DEBUG CONFIGURATION                    ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║ DEBUG_MODE:              ${DEBUG_MODE.toString().padEnd(30)} ║")
        println("║ VERBOSE_THUMBNAIL_LOGS:  ${VERBOSE_THUMBNAIL_LOGS.toString().padEnd(30)} ║")
        println("║ ENABLE_SCREENSHOTS:      ${ENABLE_SCREENSHOTS.toString().padEnd(30)} ║")
        println("║ ENABLE_VIDEO_RECORDING:  ${ENABLE_VIDEO_RECORDING.toString().padEnd(30)} ║")
        println("║ SHOW_DEBUG_OVERLAYS:     ${SHOW_DEBUG_OVERLAYS.toString().padEnd(30)} ║")
        println("║ ENABLE_PERF_MONITORING:  ${ENABLE_PERF_MONITORING.toString().padEnd(30)} ║")
        println("║ ENABLE_AUTO_TEST:        ${ENABLE_AUTO_TEST.toString().padEnd(30)} ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║ Screenshots:  ${getScreenshotsDirectory().absolutePath.padEnd(38)} ║")
        println("║ Videos:       ${getVideosDirectory().absolutePath.padEnd(38)} ║")
        println("║ Logs:         ${getLogsDirectory().absolutePath.padEnd(38)} ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }
}
