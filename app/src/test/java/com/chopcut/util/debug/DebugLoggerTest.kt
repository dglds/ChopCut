package com.chopcut.util.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebugLoggerTest {

    @Before
    fun setup() {
        DebugLogger.clear()
    }

    @Test
    fun `should add logs with correct levels`() {
        DebugLogger.v("TAG", "Verbose message")
        DebugLogger.d("TAG", "Debug message")
        DebugLogger.i("TAG", "Info message")
        DebugLogger.w("TAG", "Warn message")
        DebugLogger.e("TAG", "Error message")

        val logs = DebugLogger.logs.value
        assertEquals(5, logs.size)
        assertEquals(LogLevel.VERBOSE, logs[0].level)
        assertEquals(LogLevel.DEBUG, logs[1].level)
        assertEquals(LogLevel.INFO, logs[2].level)
        assertEquals(LogLevel.WARN, logs[3].level)
        assertEquals(LogLevel.ERROR, logs[4].level)
    }

    @Test
    fun `should respect maximum log retention of 1000 items`() {
        val maxRetention = 1000
        repeat(maxRetention + 10) { i ->
            DebugLogger.d("TAG", "Message $i")
        }

        val logs = DebugLogger.logs.value
        assertEquals(maxRetention, logs.size)
        assertEquals("Message 10", logs[0].message)
        assertEquals("Message 1009", logs[maxRetention - 1].message)
    }

    @Test
    fun `should clear logs when requested`() {
        DebugLogger.d("TAG", "Message 1")
        DebugLogger.d("TAG", "Message 2")
        assertEquals(2, DebugLogger.logs.value.size)

        DebugLogger.clear()
        assertTrue(DebugLogger.logs.value.isEmpty())
    }

    @Test
    fun `should filter logs by level correctly`() {
        DebugLogger.d("TAG", "Debug 1")
        DebugLogger.e("TAG", "Error 1")
        DebugLogger.d("TAG", "Debug 2")

        val errorLogs = DebugLogger.getFilteredLogs(level = LogLevel.ERROR)
        assertEquals(1, errorLogs.size)
        assertEquals(LogLevel.ERROR, errorLogs[0].level)
        
        val debugLogs = DebugLogger.getFilteredLogs(level = LogLevel.DEBUG)
        assertEquals(2, debugLogs.size)
    }

    @Test
    fun `should filter logs by search query correctly`() {
        DebugLogger.d("AUTH", "User logged in")
        DebugLogger.d("NETWORK", "Request success")
        DebugLogger.d("AUTH", "User logged out")

        val authLogs = DebugLogger.getFilteredLogs(query = "AUTH")
        assertEquals(2, authLogs.size)
        assertTrue(authLogs.all { it.tag == "AUTH" })

        val requestLogs = DebugLogger.getFilteredLogs(query = "success")
        assertEquals(1, requestLogs.size)
        assertTrue(requestLogs[0].message.contains("success"))
    }
}