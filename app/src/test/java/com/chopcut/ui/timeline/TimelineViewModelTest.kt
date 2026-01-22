package com.chopcut.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineViewModelTest {

    @Test
    fun `initial state should have correct duration`() {
        val duration = 10000L
        val viewModel = TimelineV5ViewModel(duration)
        val state = viewModel.state.value
        
        assertEquals(duration, state.totalDurationMs)
        assertEquals(0L, state.selectedStartMs)
        assertEquals(duration, state.selectedEndMs)
        assertEquals(0L, state.playheadPositionMs)
    }

    @Test
    fun `updateSelectedStart should update state and respect boundaries`() {
        val duration = 10000L
        val viewModel = TimelineV5ViewModel(duration)
        
        viewModel.updateSelectedStart(2000L)
        assertEquals(2000L, viewModel.state.value.selectedStartMs)
        
        // Should not exceed endMs - 1
        viewModel.updateSelectedStart(11000L)
        assertEquals(9999L, viewModel.state.value.selectedStartMs)
        
        // Should not be less than 0
        viewModel.updateSelectedStart(-100L)
        assertEquals(0L, viewModel.state.value.selectedStartMs)
    }

    @Test
    fun `updateSelectedEnd should update state and respect boundaries`() {
        val duration = 10000L
        val viewModel = TimelineV5ViewModel(duration)
        
        viewModel.updateSelectedEnd(8000L)
        assertEquals(8000L, viewModel.state.value.selectedEndMs)
        
        // Should not be less than startMs + 1
        viewModel.updateSelectedStart(5000L)
        viewModel.updateSelectedEnd(4000L)
        assertEquals(5001L, viewModel.state.value.selectedEndMs)
        
        // Should not exceed totalDuration
        viewModel.updateSelectedEnd(12000L)
        assertEquals(duration, viewModel.state.value.selectedEndMs)
    }

    @Test
    fun `updatePlayheadPosition should update state and stay within duration`() {
        val duration = 10000L
        val viewModel = TimelineV5ViewModel(duration)
        
        viewModel.updatePlayheadPosition(5000L)
        assertEquals(5000L, viewModel.state.value.playheadPositionMs)
        
        viewModel.updatePlayheadPosition(15000L)
        assertEquals(duration, viewModel.state.value.playheadPositionMs)
        
        viewModel.updatePlayheadPosition(-1000L)
        assertEquals(0L, viewModel.state.value.playheadPositionMs)
    }

    @Test
    fun `updateTotalDuration should update duration and adjust endMs if needed`() {
        val viewModel = TimelineV5ViewModel(10000L)
        
        viewModel.updateTotalDuration(20000L)
        assertEquals(20000L, viewModel.state.value.totalDurationMs)
        assertEquals(20000L, viewModel.state.value.selectedEndMs)
        
        viewModel.updateSelectedEnd(15000L)
        viewModel.updateTotalDuration(12000L)
        assertEquals(12000L, viewModel.state.value.totalDurationMs)
        assertEquals(12000L, viewModel.state.value.selectedEndMs)
    }
}
