package com.chopcut.ui.screen

import android.graphics.Bitmap
import android.net.Uri

object PreloadDataStore {
    private var _data: PreloadedData? = null
    
    fun setData(data: PreloadedData) {
        _data = data
    }
    
    fun getData(): PreloadedData? {
        return _data
    }
    
    fun clearData() {
        _data?.preloadedStrips?.values?.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        _data = null
    }
}
