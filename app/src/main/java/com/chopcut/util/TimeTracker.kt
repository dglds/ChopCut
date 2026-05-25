package com.chopcut.util

import timber.log.Timber

object TimeTracker {
    fun start(operation: String): TimeToken = TimeToken(operation)

    class TimeToken(private val operation: String) {
        private val startMs = System.currentTimeMillis()

        fun end(): Long {
            val elapsedMs = System.currentTimeMillis() - startMs
            Timber.d("⏱ %s: %dms (%.2fs)", operation, elapsedMs, elapsedMs / 1000.0)
            return elapsedMs
        }
    }
}
