package com.chopcut.data.pipeline

import java.io.File

sealed class TrimProgress {
    data class InProgress(val percent: Int) : TrimProgress()  // 0-100
    data class Completed(val file: File) : TrimProgress()
    data class Failed(val error: Throwable) : TrimProgress()
}
