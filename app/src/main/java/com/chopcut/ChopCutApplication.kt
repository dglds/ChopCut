package com.chopcut

import android.app.Application
import com.chopcut.data.thumbnail.ThumbnailCacheManager
import com.chopcut.data.thumbnail.ThumbnailStripManager
import com.chopcut.data.thumbnail.PerformanceMonitor
import com.chopcut.util.logging.FileLoggingTree
import com.chopcut.util.logging.LocalFileLoggingTree
import com.chopcut.data.local.PreferencesManager
import timber.log.Timber
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import java.io.File

class ChopCutApplication : Application() {

    companion object {
        lateinit var instance: ChopCutApplication
            private set

        /**
         * Método público para limpar o cache de thumbnails manualmente
         * Pode ser chamado de qualquer lugar no app
         * Limpa cache de memória (LRU) e cache de disco
         */
        fun clearThumbnailCache() {
            Timber.i("🧹 Limpando cache de thumbnails (chamada manual)")
            ThumbnailCacheManager.clearAll()
        }
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        // Setup Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(LocalFileLoggingTree())
        }

        // Setup File Logger (no dispositivo)
        Timber.plant(FileLoggingTree(this))

        // Setup global exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "FATAL: Uncaught Exception in thread: ${thread.name}")
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Inicializar ThumbnailCacheManager singleton (thread-safe)
        Timber.d("ChopCutApplication: Chamando ThumbnailCacheManager.initSync()")
        ThumbnailCacheManager.initSync(this)
        Timber.d("ChopCutApplication: ThumbnailCacheManager.initSync() completado")
        
        // Inicializar Telemetria de Performance
        PerformanceMonitor.init(this)

        // 🧹 LIMPAR CACHE DE THUMBNAILS AO INICIAR
        clearThumbnailCacheOnStartup()
    }

    /**
     * Limpa o cache de thumbnails ao iniciar o app
     *
     * Estratégia:
     * 1. Verificar preferência de limpar cache no startup
     * 2. Se habilitado, limpar cache de memória e disco
     * 3. Logar estatísticas antes/depois
     *
     * Útil para testes e para garantir cache limpo entre sessões
     */
    /**
     * Limpa o cache de thumbnails ao iniciar o app (Middleware de Inicialização)
     *
     * Estratégia:
     * 1. Limpar obrigatoriamente o cache de disco para evitar resíduos de sessões anteriores
     * 2. Limpar cache de memória
     * 3. Logar estatísticas antes/depois
     */
    private fun clearThumbnailCacheOnStartup() {
        Timber.i("🚀 Middleware: Iniciando limpeza obrigatória de cache no startup")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Limpar cache de memória
                ThumbnailCacheManager.clearMemoryCache()

                // 2. Limpar cache de disco (thumbnail_strips)
                val cacheDir = File(cacheDir, "thumbnail_strips")
                if (cacheDir.exists()) {
                    val sizeBefore = cacheDir.walk().map { it.length() }.sum()
                    val filesDeleted = cacheDir.listFiles()?.size ?: 0
                    
                    cacheDir.deleteRecursively()
                    cacheDir.mkdirs()

                    Timber.i("🧹 Cache de disco limpo: $filesDeleted arquivos removidos (${sizeBefore / 1024} KB liberados)")
                }

                // 3. Limpar cache de áudio (se houver diretório específico)
                val audioCacheDir = File(cacheDir, "audio_waveforms")
                if (audioCacheDir.exists()) {
                    audioCacheDir.deleteRecursively()
                    audioCacheDir.mkdirs()
                    Timber.i("🧹 Cache de áudio limpo")
                }

            } catch (e: Exception) {
                Timber.e(e, "❌ Erro no middleware de limpeza de cache")
            }
        }
    }

    /**
     * Loga estatísticas detalhadas do cache
     */
    private fun logCacheStats(title: String, stats: ThumbnailCacheManager.CacheStats) {
        Timber.i("""
            ╔═══════════════════════════════════════════════════════╗
            ║      $title                    ║
            ╚═══════════════════════════════════════════════════════╝

            🧠 CACHE DE MEMÓRIA:
            • Tamanho atual: ${stats.memoryCacheSize} / ${stats.memoryCacheMaxSize}
            • Hit rate: ${"%.2f".format(stats.memoryCacheHitRate)}%
            • Hits: ${stats.memoryCacheHits}
            • Misses: ${stats.memoryCacheMisses}

            🔨 JOBS ATIVOS:
            • Jobs de vídeo: ${stats.activeVideoJobsCount}
            • Jobs de segmento: ${stats.activeSegmentJobsCount}

            ═══════════════════════════════════════════════════════
        """.trimIndent())
    }
}
