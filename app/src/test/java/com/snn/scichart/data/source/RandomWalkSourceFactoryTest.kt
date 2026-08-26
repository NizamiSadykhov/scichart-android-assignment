package com.snn.scichart.data.source

import com.snn.scichart.core.time.MillisClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomWalkSourceFactoryTest {

    @Test
    fun `creates ten uniquely identified generators in required ranges`() {
        val sources = RandomWalkSourceFactory(
            random = Random(42),
            clock = MillisClock { 123_456L },
        ).create()
        val descriptors = sources.map { it.descriptor }

        assertEquals(10, descriptors.size)
        assertEquals(10, descriptors.map { it.id }.distinct().size)
        assertEquals(10, descriptors.map { it.name }.distinct().size)
        assertEquals(10, descriptors.map { it.lineColor }.distinct().size)
        assertEquals((1..10).map { "Generator #$it" }, descriptors.map { it.name })
        assertTrue(descriptors.all { it.initialValue >= -1.0 && it.initialValue <= 1.0 })
        assertTrue(
            descriptors.all {
                it.lifetimeMillis in 60_000L..1_800_000L &&
                    it.lifetimeMillis % 60_000L == 0L
            },
        )
        assertTrue(descriptors.all { it.startedAtMillis == 123_456L })
    }
}
