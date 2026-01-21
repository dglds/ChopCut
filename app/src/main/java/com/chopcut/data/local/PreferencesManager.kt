package com.chopcut.data.local

import android.content.Context
import android.content.SharedPreferences

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
        get() = prefs.getBoolean(KEY_THUMBNAIL_CACHE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_THUMBNAIL_CACHE_ENABLED, value).apply()
}
