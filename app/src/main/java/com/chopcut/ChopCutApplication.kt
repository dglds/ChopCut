package com.chopcut

import android.app.Application
import timber.log.Timber

class ChopCutApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Setup Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
