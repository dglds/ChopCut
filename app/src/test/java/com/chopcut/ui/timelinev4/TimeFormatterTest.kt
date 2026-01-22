package com.chopcut.ui.timelinev4

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {

    @Test
    fun `formatTimecode formats milliseconds correctly`() {
        assertEquals("00:00:00.00", TimeFormatter.formatTimecode(0))
        assertEquals("00:00:01.00", TimeFormatter.formatTimecode(1000))
        assertEquals("00:01:00.00", TimeFormatter.formatTimecode(60000))
        assertEquals("01:00:00.00", TimeFormatter.formatTimecode(3600000))
        assertEquals("00:00:00.50", TimeFormatter.formatTimecode(500))
        assertEquals("00:00:01.23", TimeFormatter.formatTimecode(1234)) // Truncate/Round to 2 decimals
    }
}
