package com.chopcut.runner

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runner customizado que injeta o [TestSummaryListener] em todas as execuções.
 *
 * Configurado em build.gradle.kts:
 *   testInstrumentationRunner = "com.chopcut.runner.ChopCutTestRunner"
 */
class ChopCutTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle) {
        val args = Bundle(arguments)

        val existing = arguments.getString("listener", "")
        val ours = TestSummaryListener::class.java.name
        args.putString("listener", if (existing.isEmpty()) ours else "$existing,$ours")

        super.onCreate(args)
    }
}
