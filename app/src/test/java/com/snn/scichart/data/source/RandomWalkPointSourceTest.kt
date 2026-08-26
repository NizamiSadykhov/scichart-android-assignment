package com.snn.scichart.data.source

import com.snn.scichart.core.time.MillisClock
import com.snn.scichart.data.model.LineColor
import com.snn.scichart.data.model.PointSourceDescriptor
import com.snn.scichart.data.model.PointSourceId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RandomWalkPointSourceTest {

    @Test
    fun `emits initial value then adds one delta every second`() = runTest {
        val deltas = ArrayDeque(listOf(0.25, -0.5, 1.0))
        val source = RandomWalkPointSource(
            descriptor = descriptor(lifetimeMillis = 3_500L),
            clock = MillisClock { testScheduler.currentTime },
            deltaProvider = ValueDeltaProvider { deltas.removeFirst() },
        )

        val points = source.stream().toList()

        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), points.map { it.timestampMillis })
        assertEquals(listOf(0.5, 0.75, 0.25, 1.25), points.map { it.value })
    }

    @Test
    fun `does not emit a point at or after expiration`() = runTest {
        val source = RandomWalkPointSource(
            descriptor = descriptor(lifetimeMillis = 3_000L),
            clock = MillisClock { testScheduler.currentTime },
            deltaProvider = ValueDeltaProvider { 0.1 },
        )

        val points = source.stream().toList()

        assertEquals(listOf(0L, 1_000L, 2_000L), points.map { it.timestampMillis })
    }

    @Test
    fun `does not emit when collection starts after expiration`() = runTest {
        val source = RandomWalkPointSource(
            descriptor = descriptor(lifetimeMillis = 1_000L),
            clock = MillisClock { 1_000L },
            deltaProvider = ValueDeltaProvider { 0.1 },
        )

        val points = source.stream().toList()

        assertTrue(points.isEmpty())
    }

    private fun descriptor(lifetimeMillis: Long) = PointSourceDescriptor(
        id = PointSourceId("test-source"),
        name = "Test source",
        lineColor = LineColor(0xFF000000.toInt()),
        initialValue = 0.5,
        startedAtMillis = 0L,
        lifetimeMillis = lifetimeMillis,
    )
}
