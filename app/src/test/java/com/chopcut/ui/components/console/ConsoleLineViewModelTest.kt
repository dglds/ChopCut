package com.chopcut.ui.components.console

import com.chopcut.util.debug.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleLineViewModelTest {

    private lateinit var viewModel: ConsoleLineViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        DebugLogger.clear()
        viewModel = ConsoleLineViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should change position when setPosition is called`() {
        viewModel.setPosition(ConsoleLineViewModel.ConsolePosition.HEADER)
        assertEquals(ConsoleLineViewModel.ConsolePosition.HEADER, viewModel.position.value)

        viewModel.setPosition(ConsoleLineViewModel.ConsolePosition.FOOTER)
        assertEquals(ConsoleLineViewModel.ConsolePosition.FOOTER, viewModel.position.value)
    }

    @Test
    fun `should toggle visibility correctly`() {
        assertTrue(viewModel.isVisible.value)
        viewModel.toggleVisibility()
        assertFalse(viewModel.isVisible.value)
        viewModel.toggleVisibility()
        assertTrue(viewModel.isVisible.value)
    }

    @Test
    fun `should dismiss console correctly`() {
        viewModel.show()
        assertTrue(viewModel.isVisible.value)
        viewModel.dismiss()
        assertFalse(viewModel.isVisible.value)
    }

    @Test
    fun `should clear logs when clear is called`() {
        DebugLogger.d("TAG", "Message")
        viewModel.clear()
        assertTrue(viewModel.logHistory.value.isEmpty())
        assertTrue(DebugLogger.logs.value.isEmpty())
    }
}