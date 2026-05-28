package com.chopcut

import android.app.Application
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import java.io.File
import timber.log.Timber

class ChopCutApplication : Application() {

    companion object {
        lateinit var instance: ChopCutApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        android.util.Log.i("ChopCut", "!!! APP ONCREATE STARTED !!!")
        System.out.println("!!! CHOPCUT APP ONCREATE !!!")
        
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
    }

}
