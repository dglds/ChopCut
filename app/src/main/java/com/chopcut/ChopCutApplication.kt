package com.chopcut

import android.app.Application
import com.chopcut.data.thumbnail.ThumbnailCacheManager
import com.chopcut.data.thumbnail.ThumbnailStripManager
import com.chopcut.data.thumbnail.PerformanceMonitor
import com.chopcut.util.logging.FileLoggingTree
import com.chopcut.util.logging.LocalFileLoggingTree
import com.chopcut.data.local.PreferencesManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import java.io.File
import timber.log.Timber

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
            ThumbnailCacheManager.clearAll()
        }
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        // Setup Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Setup File Logger (no dispositivo)

        // Setup global exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Inicializar ThumbnailCacheManager singleton (thread-safe)
        ThumbnailCacheManager.initSync(this)
        
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
    private fun clearThumbnailCacheOnStartup() {
        val prefsManager = PreferencesManager(this)
        val clearCacheOnStartup = prefsManager.clearCacheOnStartup

        if (clearCacheOnStartup) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Logar estatísticas ANTES da limpeza
                    val statsBefore = ThumbnailCacheManager.getStats()
                    logCacheStats("ANTES da limpeza", statsBefore)

                    // Limpar cache de memória (ThumbnailCacheManager)
                    ThumbnailCacheManager.clearMemoryCache()

                    // Limpar cache de disco (ThumbnailStripManager)
                    val cacheDir = File(cacheDir, "thumbnail_strips")
                    if (cacheDir.exists()) {
                        val filesDeleted = cacheDir.listFiles()?.size ?: 0
                        val sizeBefore = cacheDir.walk().map { it.length() }.sum()

                        cacheDir.deleteRecursively()
                        cacheDir.mkdirs()

                        val sizeAfter = 0L
                        val sizeSaved = sizeBefore - sizeAfter

                    }

                    // Logar estatísticas DEPOIS da limpeza
                    val statsAfter = ThumbnailCacheManager.getStats()
                    logCacheStats("DEPOIS da limpeza", statsAfter)

                } catch (e: Exception) {
                }
            }
        }
    }

    /**
     * Loga estatísticas detalhadas do cache
     */
    private fun logCacheStats(title: String, stats: ThumbnailCacheManager.CacheStats) {
    }
}
