package com.chopcut.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TimeRange(
    val startMs: Long,
    val endMs: Long
) : Parcelable {
    init {
        require(startMs >= 0) { "startMs must be >= 0" }
        require(endMs > startMs) { "endMs must be > startMs" }
    }

    val durationMs: Long get() = endMs - startMs

    fun contains(timeMs: Long): Boolean {
        return timeMs in startMs..endMs
    }

    fun overlaps(other: TimeRange): Boolean {
        return startMs < other.endMs && endMs > other.startMs
    }

    companion object {
        fun fromUs(startUs: Long, endUs: Long): TimeRange {
            return TimeRange(
                startMs = startUs / 1000,
                endMs = endUs / 1000
            )
        }
    }
}
