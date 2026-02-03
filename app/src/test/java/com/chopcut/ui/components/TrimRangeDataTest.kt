package com.chopcut.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimRangeDataTest {

    @Test
    fun `contains returns true when time is within range`() {
        val range = TrimRangeData(id = "1", startMs = 1000, endMs = 2000)
        assertTrue(range.contains(1000))
        assertTrue(range.contains(1500))
        assertTrue(range.contains(2000))
    }

    @Test
    fun `contains returns false when time is outside range`() {
        val range = TrimRangeData(id = "1", startMs = 1000, endMs = 2000)
        assertFalse(range.contains(999))
        assertFalse(range.contains(2001))
    }

    @Test
    fun `overlaps returns true when ranges overlap`() {
        val range1 = TrimRangeData(id = "1", startMs = 1000, endMs = 2000)
        val range2 = TrimRangeData(id = "2", startMs = 1500, endMs = 2500)
        assertTrue(range1.overlaps(range2))
        assertTrue(range2.overlaps(range1))
    }

    @Test
    fun `overlaps returns false when ranges do not overlap`() {
        val range1 = TrimRangeData(id = "1", startMs = 1000, endMs = 2000)
        val range2 = TrimRangeData(id = "2", startMs = 2001, endMs = 3000)
        assertFalse(range1.overlaps(range2))
        assertFalse(range2.overlaps(range1))
    }
}
