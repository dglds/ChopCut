package com.chopcut.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gerenciador de preferências do usuário (SharedPreferences)
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)


    companion object {
        private const val PREFS_NAME = "chopcut_prefs"
        private const val KEY_IS_FIRST_RUN = "is_first_run"
        private const val KEY_THEME_MODE = "theme_mode" // 0=System, 1=Light, 2=Dark
        private const val KEY_THUMBNAIL_CACHE_ENABLED = "thumbnail_cache_enabled" // Cache de thumbnails
        private const val KEY_GALLERY_SORT_ORDER = "gallery_sort_order"
        private const val KEY_DEBUG_ENABLED = "debug_enabled" // Debugger toast
        private const val KEY_THUMBS_PER_STRIP = "thumbs_per_strip" // Thumbs por strip (padrão: 10)
        private const val KEY_CLEAR_CACHE_ON_STARTUP = "clear_cache_on_startup" // Limpar cache ao iniciar
    }

    private val _themeModeFlow = MutableStateFlow(prefs.getInt(KEY_THEME_MODE, 0))
    val themeModeFlow: StateFlow<Int> = _themeModeFlow.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_THEME_MODE) {
            _themeModeFlow.value = themeMode
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    /**
     * Verifica se é a primeira execução do app
     */
    var isFirstRun: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_RUN, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_FIRST_RUN, value).apply()

    /**
     * Modo de tema preferido
     */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    /**
     * Cache de thumbnails habilitado (INATIVO por padrão)
     */
    var thumbnailCacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_THUMBNAIL_CACHE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_THUMBNAIL_CACHE_ENABLED, value).apply()

    /**
     * Debug toast habilitado (ATIVO por padrão em debug builds)
     */
    var debugEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_ENABLED, value).apply()

    /**
     * Ordenação da galeria de vídeos (persiste entre sessões)
     */
    var gallerySortOrder: String
        get() = prefs.getString(KEY_GALLERY_SORT_ORDER, "SIZE_DESC") ?: "SIZE_DESC"
        set(value) = prefs.edit().putString(KEY_GALLERY_SORT_ORDER, value).apply()

    /**
     * Thumbs por strip no cache (padrão: 10)
     * Cada strip contém N segundos de vídeo (1 thumb por segundo)
     * Valores permitidos: 5-30
     */
    var thumbsPerStrip: Int
        get() = prefs.getInt(KEY_THUMBS_PER_STRIP, 10).coerceIn(5, 30)
        set(value) = prefs.edit().putInt(KEY_THUMBS_PER_STRIP, value.coerceIn(5, 30)).apply()

    /**
     * Limpar cache de thumbnails ao iniciar o app (DESABILITADO por padrão)
     *
     * Útil para:
     * - Testes (garantir cache limpo entre sessões)
     * - Debug (recomeçar sempre do zero)
     *
     * Nota: Se desabilitado, o cache persiste entre sessões (RECOMENDADO para produção)
     * Se habilitado, o cache é limpo sempre que o app inicia ou retoma
     *
     * Estratégia de cache LRU:
     * - Memória: 100 strips (~35-40MB) com eviction automático
     * - Disco: 200MB com LRU baseado em lastModified
     * - Portanto, o cache se gerencia automaticamente sem limpeza manual
     */
    var clearCacheOnStartup: Boolean
        get() = prefs.getBoolean(KEY_CLEAR_CACHE_ON_STARTUP, false)  // ❌ DESABILITADO por padrão (RECOMENDADO)
        set(value) = prefs.edit().putBoolean(KEY_CLEAR_CACHE_ON_STARTUP, value).apply()
}
