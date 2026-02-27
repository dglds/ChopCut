package com.chopcut.ui.screen

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.chopcut.data.repository.VideoRepository

class HomeViewModelFactory(
    private val application: Application,
    private val videoRepository: VideoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(application, videoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
