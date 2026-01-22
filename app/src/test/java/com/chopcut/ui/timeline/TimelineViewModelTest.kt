package com.chopcut.ui.timeline

import com.chopcut.ui.timeline.model.VideoRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineViewModelTest {

    @Test
    fun `initial state should have correct duration and empty ranges`() {
        val duration = 10000L
        val viewModel = TimelineViewModel(duration)
        val state = viewModel.state.value

        assertEquals(duration, state.totalDurationMs)
        assertEquals(0L, state.playheadPositionMs)
        assertTrue(state.ranges.isEmpty())
        assertNull(state.selectedRange)
    }

    @Test
    fun `addRange should create range with 25 percent duration centered`() {
        val duration = 10000L // 10 seconds
        val viewModel = TimelineViewModel(duration)

        viewModel.addRange()
        val state = viewModel.state.value

        assertEquals(1, state.ranges.size)
        val range = state.ranges[0]

        // 25% of 10000 = 2500 (2.5 seconds)
        assertEquals(2500L, range.durationMs)
        // Centered: (10000 - 2500) / 2 = 3750
        assertEquals(3750L, range.startMs)
        assertEquals(6250L, range.endMs)
        assertFalse(range.isSelected)
    }

    @Test
    fun `video timeline with realistic frame rate - 10 seconds video has 10 frames`() {
        val durationMs = 10000L // 10 seconds
        val frameRate = 1 // 1 frame per second
        val totalFrames = durationMs / 1000 * frameRate // 10 frames

        assertEquals(10, totalFrames.toInt())

        // Each frame represents 1 second (1000ms)
        val msPerFrame = 1000L / frameRate
        assertEquals(1000L, msPerFrame)

        // Scrolling through entire video (10 seconds) should take 10 seconds
        // at normal playback speed
        val scrollDurationSeconds = durationMs / 1000
        assertEquals(10, scrollDurationSeconds)
    }

    @Test
    fun `addRange should create range with minimum duration if video is short`() {
        val duration = 2000L
        val viewModel = TimelineViewModel(duration)

        viewModel.addRange()
        val state = viewModel.state.value

        assertEquals(1, state.ranges.size)
        val range = state.ranges[0]

        // Minimum 1000ms for short videos
        assertEquals(1000L, range.durationMs)
    }

    @Test
    fun `addRange should add multiple ranges`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.addRange()
        viewModel.addRange()
        viewModel.addRange()

        val state = viewModel.state.value
        assertEquals(3, state.ranges.size)
    }

    @Test
    fun `selectRange should mark only specified range as selected`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.addRange()
        viewModel.addRange()

        val state = viewModel.state.value
        val range1 = state.ranges[0]
        val range2 = state.ranges[1]

        // Initially nothing selected
        assertNull(state.selectedRange)

        // Select first range
        viewModel.selectRange(range1.id)
        var newState = viewModel.state.value
        assertEquals(range1.id, newState.selectedRange?.id)
        assertTrue(newState.selectedRange?.isSelected == true)

        // Select second range (first should be deselected)
        viewModel.selectRange(range2.id)
        newState = viewModel.state.value
        assertEquals(range2.id, newState.selectedRange?.id)
        assertTrue(newState.ranges[0].isSelected == false)
        assertTrue(newState.ranges[1].isSelected == true)
    }

    @Test
    fun `removeSelectedRange should remove selected range only`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.addRange()
        viewModel.addRange()
        viewModel.addRange()

        val state = viewModel.state.value
        viewModel.selectRange(state.ranges[1].id)

        viewModel.removeSelectedRange()
        val newState = viewModel.state.value

        assertEquals(2, newState.ranges.size)
        assertNull(newState.selectedRange)
    }

    @Test
    fun `removeSelectedRange should do nothing if no range selected`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.addRange()
        viewModel.addRange()

        val initialCount = viewModel.state.value.ranges.size

        viewModel.removeSelectedRange()
        val newState = viewModel.state.value

        assertEquals(initialCount, newState.ranges.size)
    }

    @Test
    fun `updateSelectedRangeStart should update start and respect boundaries`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.addRange()
        val state = viewModel.state.value
        val range = state.ranges[0]
        viewModel.selectRange(range.id)

        // Normal update
        viewModel.updateSelectedRangeStart(4000L)
        var newState = viewModel.state.value
        assertEquals(4000L, newState.selectedRange?.startMs)

        // Should not exceed endMs - 1
        viewModel.updateSelectedRangeStart(7000L)
        newState = viewModel.state.value
        assertEquals(6249L, newState.selectedRange?.startMs) // endMs is 6250

        // Should not be less than 0
        viewModel.updateSelectedRangeStart(-100L)
        newState = viewModel.state.value
        assertEquals(0L, newState.selectedRange?.startMs)
    }

    @Test
    fun `updateSelectedRangeStart should do nothing if no range selected`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.addRange()
        val initialState = viewModel.state.value

        viewModel.updateSelectedRangeStart(2000L)
        val newState = viewModel.state.value

        assertEquals(initialState.ranges[0].startMs, newState.ranges[0].startMs)
    }

    @Test
    fun `updateSelectedRangeEnd should update end and respect boundaries`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.addRange()
        val state = viewModel.state.value
        val range = state.ranges[0]
        viewModel.selectRange(range.id)

        // Normal update
        viewModel.updateSelectedRangeEnd(7000L)
        var newState = viewModel.state.value
        assertEquals(7000L, newState.selectedRange?.endMs)

        // Should not be less than startMs + 1
        viewModel.updateSelectedRangeStart(5000L)
        viewModel.updateSelectedRangeEnd(4500L)
        newState = viewModel.state.value
        assertEquals(5001L, newState.selectedRange?.endMs)

        // Should not exceed totalDuration
        viewModel.updateSelectedRangeEnd(15000L)
        newState = viewModel.state.value
        assertEquals(10000L, newState.selectedRange?.endMs)
    }

    @Test
    fun `updateSelectedRangeEnd should do nothing if no range selected`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.addRange()
        val initialState = viewModel.state.value

        viewModel.updateSelectedRangeEnd(8000L)
        val newState = viewModel.state.value

        assertEquals(initialState.ranges[0].endMs, newState.ranges[0].endMs)
    }

    @Test
    fun `updatePlayheadPosition should update state and stay within duration`() {
        val duration = 10000L
        val viewModel = TimelineViewModel(duration)

        viewModel.updatePlayheadPosition(5000L)
        assertEquals(5000L, viewModel.state.value.playheadPositionMs)

        viewModel.updatePlayheadPosition(15000L)
        assertEquals(duration, viewModel.state.value.playheadPositionMs)

        viewModel.updatePlayheadPosition(-1000L)
        assertEquals(0L, viewModel.state.value.playheadPositionMs)
    }

    @Test
    fun `updateTotalDuration should update duration`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.updateTotalDuration(20000L)
        assertEquals(20000L, viewModel.state.value.totalDurationMs)

        viewModel.updateTotalDuration(5000L)
        assertEquals(5000L, viewModel.state.value.totalDurationMs)
    }

    @Test
    fun `ranges should maintain unique IDs`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.addRange()
        viewModel.addRange()
        viewModel.addRange()

        val state = viewModel.state.value
        val ids = state.ranges.map { it.id }.toSet()

        assertEquals(3, ids.size)
    }

    @Test
    fun `selectedRange should return correct range`() {
        val viewModel = TimelineViewModel(10000L)

        viewModel.addRange()
        viewModel.addRange()

        val state = viewModel.state.value
        val range2 = state.ranges[1]

        viewModel.selectRange(range2.id)

        val newState = viewModel.state.value
        assertNotNull(newState.selectedRange)
        assertEquals(range2.id, newState.selectedRange?.id)
        assertTrue(newState.selectedRange?.isSelected == true)
    }
}
