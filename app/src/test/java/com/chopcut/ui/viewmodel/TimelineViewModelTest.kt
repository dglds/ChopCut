package com.chopcut.ui.viewmodel

import android.app.Application
import app.cash.turbine.test
import com.chopcut.ui.components.TrimPosition
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TimelineViewModelTest {

    private lateinit var viewModel: TimelineViewModel

    @Before
    fun setup() {
        viewModel = TimelineViewModel(TestApplication())
    }

    private class TestApplication : Application()

    @Test
    fun `initial state should be empty`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.trimPosition.positions.isEmpty())
            assertEquals(0L, state.currentPosition)
            assertFalse(state.isDraftMode)
        }
    }

    @Test
    fun `addPosition should add first position as draft`() = runTest {
        viewModel.setCurrentPosition(200L)
        viewModel.addPosition(200L)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(1, state.trimPosition.positions.size)
            assertEquals(200L, state.trimPosition.positions[0])
            assertTrue(state.isDraftMode)
        }
    }

    @Test
    fun `addPosition second time should complete range`() = runTest {
        viewModel.setCurrentPosition(200L)
        viewModel.addPosition(200L)

        viewModel.setCurrentPosition(400L)
        viewModel.addPosition(400L)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2, state.trimPosition.positions.size)
            assertEquals(1, state.trimPosition.completeRanges.size)
            assertEquals(200L to 400L, state.trimPosition.completeRanges[0])
            assertFalse(state.isDraftMode)
        }
    }

    @Test
    fun `addPosition with reversed order should normalize range`() = runTest {
        viewModel.setCurrentPosition(400L)
        viewModel.addPosition(400L)

        viewModel.setCurrentPosition(200L)
        viewModel.addPosition(200L)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2, state.trimPosition.positions.size)
            assertEquals(1, state.trimPosition.completeRanges.size)
            assertEquals(200L to 400L, state.trimPosition.completeRanges[0])
        }
    }

    @Test
    fun `duplicate position should be ignored`() = runTest {
        viewModel.setCurrentPosition(200L)
        viewModel.addPosition(200L)

        viewModel.addPosition(200L)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(1, state.trimPosition.positions.size)
        }
    }

    @Test
    fun `multiple ranges should be merged if overlapping`() = runTest {
        viewModel.setCurrentPosition(200L)
        viewModel.addPosition(200L)

        viewModel.setCurrentPosition(400L)
        viewModel.addPosition(400L)

        viewModel.setCurrentPosition(300L)
        viewModel.addPosition(300L)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(3, state.trimPosition.positions.size)
            assertEquals(1, state.trimPosition.completeRanges.size)
            assertEquals(200L to 400L, state.trimPosition.completeRanges[0])
        }
    }

    @Test
    fun `non-overlapping ranges should remain separate`() = runTest {
        viewModel.setCurrentPosition(100L)
        viewModel.addPosition(100L)

        viewModel.setCurrentPosition(300L)
        viewModel.addPosition(300L)

        viewModel.setCurrentPosition(500L)
        viewModel.addPosition(500L)

        viewModel.setCurrentPosition(700L)
        viewModel.addPosition(700L)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(4, state.trimPosition.positions.size)
            assertEquals(2, state.trimPosition.completeRanges.size)
            assertEquals(100L to 300L, state.trimPosition.completeRanges[0])
            assertEquals(500L to 700L, state.trimPosition.completeRanges[1])
        }
    }

    @Test
    fun `connecting ranges should merge into one`() = runTest {
        viewModel.setCurrentPosition(100L)
        viewModel.addPosition(100L)

        viewModel.setCurrentPosition(300L)
        viewModel.addPosition(300L)

        viewModel.setCurrentPosition(200L)
        viewModel.addPosition(200L)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(1, state.trimPosition.completeRanges.size)
            assertEquals(100L to 300L, state.trimPosition.completeRanges[0])
        }
    }

    @Test
    fun `isPositionInRange should detect position inside range`() = runTest {
        viewModel.setCurrentPosition(200L)
        viewModel.addPosition(200L)

        viewModel.setCurrentPosition(400L)
        viewModel.addPosition(400L)

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.trimPosition.isPositionInRange(300L))
            assertFalse(state.trimPosition.isPositionInRange(500L))
        }
    }

    @Test
    fun `clear should reset all state`() = runTest {
        viewModel.setCurrentPosition(200L)
        viewModel.addPosition(200L)
        viewModel.setCurrentPosition(400L)
        viewModel.addPosition(400L)

        viewModel.clear()

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.trimPosition.positions.isEmpty())
            assertEquals(0L, state.currentPosition)
            assertFalse(state.isDraftMode)
        }
    }

    @Test
    fun `draftPosition should return last position when odd count`() = runTest {
        viewModel.setCurrentPosition(200L)
        viewModel.addPosition(200L)

        viewModel.setCurrentPosition(400L)
        viewModel.addPosition(400L)

        viewModel.setCurrentPosition(600L)
        viewModel.addPosition(600L)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(600L, state.trimPosition.draftPosition)
        }
    }

    @Test
    fun `draftPosition should return null when even count`() = runTest {
        viewModel.setCurrentPosition(200L)
        viewModel.addPosition(200L)

        viewModel.setCurrentPosition(400L)
        viewModel.addPosition(400L)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(null, state.trimPosition.draftPosition)
        }
    }

    @Test
    fun `getCompleteRanges should return all completed ranges`() = runTest {
        viewModel.setCurrentPosition(100L)
        viewModel.addPosition(100L)
        viewModel.setCurrentPosition(300L)
        viewModel.addPosition(300L)
        viewModel.setCurrentPosition(500L)
        viewModel.addPosition(500L)
        viewModel.setCurrentPosition(700L)
        viewModel.addPosition(700L)

        val ranges = viewModel.getCompleteRanges()
        assertEquals(2, ranges.size)
        assertEquals(100L to 300L, ranges[0])
        assertEquals(500L to 700L, ranges[1])
    }
}
