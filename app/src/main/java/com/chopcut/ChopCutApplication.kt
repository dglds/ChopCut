package com.chopcut

import android.app.Application
import com.chopcut.data.thumbnail.ThumbnailCacheManager
import com.chopcut.data.thumbnail.ThumbnailStripManager
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

        Timber.i("""
            ╔═══════════════════════════════════════════════════════╗
            ║          LIMPEZA DE CACHE AO INICIAR APP                   ║
            ╚═══════════════════════════════════════════════════════╝

            ⚙️  Configuração:
               • clearCacheOnStartup: $clearCacheOnStartup

            ${if (clearCacheOnStartup) "✅ Cache será limpo ao iniciar" else "❌ Cache NÃO será limpo ao iniciar"}

            ═══════════════════════════════════════════════════════
        """.trimIndent())

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

                        Timber.i("""
                            ╔═══════════════════════════════════════════════════════╗
                            ║              CACHE LIMPO COM SUCESSO                      ║
                            ╚═══════════════════════════════════════════════════════╝

                            📊 ESTATÍSTICAS:
                            • Arquivos deletados: $filesDeleted
                            • Espaço liberado: ${sizeSaved / 1024 / 1024}MB

                            🗄️  CACHE DISCO:
                            • Diretório: ${cacheDir.absolutePath}
                            • Status: LIMPO

                            🧠 CACHE MEMÓRIA:
                            • Status: LIMPO

                            ═══════════════════════════════════════════════════════
                        """.trimIndent())
                    }

                    // Logar estatísticas DEPOIS da limpeza
                    val statsAfter = ThumbnailCacheManager.getStats()
                    logCacheStats("DEPOIS da limpeza", statsAfter)

                } catch (e: Exception) {
                    Timber.e(e, "❌ Erro ao limpar cache no startup")
                }
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
