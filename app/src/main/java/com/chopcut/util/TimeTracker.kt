package com.chopcut.util

import timber.log.Timber

object TimeTracker {
    fun start(operation: String): TimeToken = TimeToken(operation)

    class TimeToken(private val operation: String) {
        private val startMs = System.currentTimeMillis()

        fun end() {
            val elapsedMs = System.currentTimeMillis() - startMs
            val elapsedSec = elapsedMs / 1000.0
            Timber.tag("TIME").i("⏱ $operation finished in ${"%.2f".format(elapsedSec)}s")
        }
    }
}
