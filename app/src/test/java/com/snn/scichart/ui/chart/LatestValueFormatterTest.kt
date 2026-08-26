package com.snn.scichart.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class LatestValueFormatterTest {

    @Test
    fun formatsPositiveNegativeAndZeroValuesWithThreeDecimalPlaces() {
        assertEquals("1.235", formatLatestValue(1.2346))
        assertEquals("-0.125", formatLatestValue(-0.125))
        assertEquals("0.000", formatLatestValue(0.0))
    }
}
