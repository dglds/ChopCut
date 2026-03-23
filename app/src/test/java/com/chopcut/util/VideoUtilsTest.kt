package com.chopcut.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoUtilsTest {

    @Test
    fun `TimeUtils should format time with millis correctly`() {
        // 65432 ms -> 01:05:43 (65s, 43cs)
        val result = TimeUtils.formatTimeWithMillis(65432L)
        assertEquals("01:05:43", result)
    }

    @Test
    fun `TimeUtils should format short time correctly`() {
        assertEquals("01:05", TimeUtils.formatTimeShort(65000L))
        assertEquals("00:59", TimeUtils.formatTimeShort(59900L))
    }

    @Test
    fun `FormatUtils should format file size correctly`() {
        assertEquals("500 B", FormatUtils.formatFileSize(500L))
        assertEquals("1.00 KB", FormatUtils.formatFileSize(1024L))
        assertEquals("1.50 MB", FormatUtils.formatFileSize((1024 * 1024 * 1.5).toLong()))
    }

    @Test
    fun `RangeUtils should merge overlapping ranges`() {
        // [0, 10], [5, 15] -> [0, 15]
        val positions = listOf(0L, 10000L, 5000L, 15000L)
        val merged = RangeUtils.mergeRanges(positions)
        
        assertEquals(1, merged.size)
        assertEquals(0L, merged[0].first)
        assertEquals(15000L, merged[0].second)
    }

    @Test
    fun `RangeUtils should calculate keep ranges correctly`() {
        // Total 100s. Remove [10, 20]. Keep [0, 10] and [20, 100].
        val trimRanges = listOf(10000L to 20000L)
        val keepRanges = RangeUtils.calculateKeepRanges(trimRanges, 100000L)
        
        assertEquals(2, keepRanges.size)
        assertEquals(0L, keepRanges[0].startMs)
        assertEquals(10000L, keepRanges[0].endMs)
        assertEquals(20000L, keepRanges[1].startMs)
        assertEquals(100000L, keepRanges[1].endMs)
    }

    @Test
    fun `FileNameUtils should sanitize file name`() {
        val dirty = "Video @!# (2023).mp4"
        val clean = FileNameUtils.sanitizeFileName(dirty)
        assertTrue(clean.contains("Video"))
        assertFalse(clean.contains("@"))
        assertFalse(clean.contains(" "))
    }
}

// Extension for test to avoid importing assertFalse if choosing to keep it simple
private fun assertFalse(condition: Boolean) = org.junit.Assert.assertFalse(condition)
private fun assertTrue(condition: Boolean) = org.junit.Assert.assertTrue(condition)
