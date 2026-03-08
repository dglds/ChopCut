package com.chopcut.util.logging


object ActivityLogger {
    private const val TAG = "ChopCut.Activity"

    fun started(activity: AppActivity, vararg params: Pair<String, Any?>) {
    }

    fun finished(activity: AppActivity, vararg params: Pair<String, Any?>) {
    }

    fun failed(activity: AppActivity, vararg params: Pair<String, Any?>) {
    }

    private fun fmtParams(params: Array<out Pair<String, Any?>>) =
        if (params.isEmpty()) "" else " — " + params.joinToString(", ") { "${it.first}=${it.second}" }
}

sealed class AppActivity(val label: String) {
    object ThumbnailExtraction : AppActivity("ThumbnailExtraction")
    object StripAssembly : AppActivity("StripAssembly")
    object Trim : AppActivity("Trim")
}
