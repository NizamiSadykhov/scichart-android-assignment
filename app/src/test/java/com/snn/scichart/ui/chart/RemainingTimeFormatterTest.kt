package com.snn.scichart.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemainingTimeFormatterTest {

    @Test
    fun `formats assignment countdown boundaries`() {
        assertEquals("29:59", formatRemainingTime(29L * 60L * 1_000L + 59_000L))
        assertEquals("00:00", formatRemainingTime(0L))
    }

    @Test
    fun `rounds a partial second up to avoid premature visual decrement`() {
        assertEquals("00:01", formatRemainingTime(1L))
        assertEquals("00:01", formatRemainingTime(999L))
        assertEquals("00:01", formatRemainingTime(1_000L))
        assertEquals("00:02", formatRemainingTime(1_001L))
    }

    @Test
    fun `rejects an invalid negative duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            formatRemainingTime(-1L)
        }
    }
}
