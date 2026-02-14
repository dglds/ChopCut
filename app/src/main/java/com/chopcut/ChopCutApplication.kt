package com.chopcut

import android.app.Application
import com.chopcut.util.logging.FileLoggingTree
import com.chopcut.util.logging.LocalFileLoggingTree
import timber.log.Timber

class ChopCutApplication : Application() {

    override fun onCreate() {
        super.onCreate()

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
    }
}
