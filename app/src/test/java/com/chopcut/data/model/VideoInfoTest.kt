package com.chopcut.data.model

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoInfoTest {

    @Test
    fun testAspectRatioForStandardLandscapeVideo() {
        val uri: Uri = mockk(relaxed = true)
        val videoInfo = VideoInfo(
            uri = uri,
            fileName = "test.mp4",
            mimeType = "video/mp4",
            durationUs = 1000L,
            width = 1920,
            height = 1080,
            rotation = 0,
            bitrate = 0L,
            frameRate = 30,
            videoCodec = null,
            audioCodec = null,
            hasAudio = false,
            sizeBytes = 0L
        )

        assertEquals(1920f / 1080f, videoInfo.aspectRatio, 0.001f)
    }

    @Test
    fun testAspectRatioForRotatedPortraitVideo() {
        val uri: Uri = mockk(relaxed = true)
        val videoInfo = VideoInfo(
            uri = uri,
            fileName = "test.mp4",
            mimeType = "video/mp4",
            durationUs = 1000L,
            width = 1920,
            height = 1080,
            rotation = 90,
            bitrate = 0L,
            frameRate = 30,
            videoCodec = null,
            audioCodec = null,
            hasAudio = false,
            sizeBytes = 0L
        )

        assertEquals(1080f / 1920f, videoInfo.aspectRatio, 0.001f)
    }

    @Test
    fun testAspectRatioForStandardPortraitVideo() {
        val uri: Uri = mockk(relaxed = true)
        val videoInfo = VideoInfo(
            uri = uri,
            fileName = "test.mp4",
            mimeType = "video/mp4",
            durationUs = 1000L,
            width = 1080,
            height = 1920,
            rotation = 0,
            bitrate = 0L,
            frameRate = 30,
            videoCodec = null,
            audioCodec = null,
            hasAudio = false,
            sizeBytes = 0L
        )

        assertEquals(1080f / 1920f, videoInfo.aspectRatio, 0.001f)
    }

    @Test
    fun testAspectRatioFallbackForZeroDimensions() {
        val uri: Uri = mockk(relaxed = true)
        val videoInfo = VideoInfo(
            uri = uri,
            fileName = "test.mp4",
            mimeType = "video/mp4",
            durationUs = 1000L,
            width = 0,
            height = 0,
            rotation = 0,
            bitrate = 0L,
            frameRate = 30,
            videoCodec = null,
            audioCodec = null,
            hasAudio = false,
            sizeBytes = 0L
        )

        assertEquals(16f / 9f, videoInfo.aspectRatio, 0.001f)
    }
}