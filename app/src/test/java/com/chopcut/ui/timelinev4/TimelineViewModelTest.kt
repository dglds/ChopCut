package com.chopcut.ui.timelinev4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineViewModelTest {

    @Test
    fun `initial state is correct`() {
        val viewModel = TimelineViewModel()
        val state = viewModel.state.value
        assertEquals(0L, state.currentTimeMs)
        assertEquals(false, state.isScrubbing)
    }

    @Test
    fun `onSeek updates time and scrubbing state`() {
        val viewModel = TimelineViewModel()
        
        // Simulate Scrub Start
        viewModel.onEvent(TimelineEvent.ScrubStart)
        assertTrue(viewModel.state.value.isScrubbing)

        // Simulate Seek
        viewModel.onEvent(TimelineEvent.Seek(5000L))
        assertEquals(5000L, viewModel.state.value.currentTimeMs)

        // Simulate Scrub End
        viewModel.onEvent(TimelineEvent.ScrubEnd)
        assertEquals(false, viewModel.state.value.isScrubbing)
    }
}
