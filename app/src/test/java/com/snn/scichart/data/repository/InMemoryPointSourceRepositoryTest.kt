package com.snn.scichart.data.repository

import com.snn.scichart.core.time.MillisClock
import com.snn.scichart.data.model.LineColor
import com.snn.scichart.data.model.PointSourceDescriptor
import com.snn.scichart.data.model.PointSourceId
import com.snn.scichart.data.model.PointSourceState
import com.snn.scichart.data.model.PointSourceStatus
import com.snn.scichart.data.source.ValueDeltaProvider
import com.snn.scichart.data.source.RandomWalkPointSource
import com.snn.scichart.data.source.PointSource
import com.snn.scichart.data.model.ChartPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryPointSourceRepositoryTest {

    @Test
    fun `delivers atomic history snapshot followed by live batch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pointChannel = Channel<ChartPoint>()
        val descriptor = PointSourceDescriptor(
            id = PointSourceId("controlled"),
            name = "controlled",
            lineColor = LineColor(0),
            initialValue = 0.0,
            startedAtMillis = 0L,
            lifetimeMillis = 60_000L,
        )
        val source = object : PointSource {
            override val descriptor: PointSourceDescriptor = descriptor
            override fun stream() = pointChannel.receiveAsFlow()
        }
        val repository = InMemoryPointSourceRepository(
            pointSources = listOf(source),
            scope = backgroundScope,
            dispatcher = dispatcher,
        )
        val historicalPoint = ChartPoint(timestampMillis = 1L, value = 1.0)
        val livePoint = ChartPoint(timestampMillis = 2L, value = 2.0)

        repository.start()
        runCurrent()
        pointChannel.send(historicalPoint)
        runCurrent()

        val batches = async { repository.pointBatches(descriptor.id).take(2).toList() }
        runCurrent()
        pointChannel.send(livePoint)
        runCurrent()

        assertEquals(listOf(listOf(historicalPoint), listOf(livePoint)), batches.await())
        pointChannel.close()
    }

    @Test
    fun `runs sources independently sorts them and retains their points`() = runTest {
        val clock = MillisClock { testScheduler.currentTime }
        val shortSource = source(id = "short", lifetimeMillis = 3_000L, clock = clock)
        val longSource = source(id = "long", lifetimeMillis = 5_000L, clock = clock)
        val repository = InMemoryPointSourceRepository(
            pointSources = listOf(longSource, shortSource),
            scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.start()
        repository.start()
        runCurrent()
        flushRepositoryState()

        assertEquals(listOf("short", "long"), repository.sources.value.ids())
        assertEquals(listOf(1, 1), repository.sources.value.map { it.generatedPoints })

        advanceTimeBy(2_001L.milliseconds)
        runCurrent()

        assertEquals(listOf("short", "long"), repository.sources.value.ids())
        assertEquals(listOf(1_000L, 3_000L), repository.sources.value.map { it.remainingMillis })

        advanceTimeBy(3_000L.milliseconds)
        runCurrent()

        assertTrue(repository.sources.value.all { it.status == PointSourceStatus.COMPLETED })
        assertEquals(3, repository.pointBatches(PointSourceId("short")).first().size)
        assertEquals(5, repository.pointBatches(PointSourceId("long")).first().size)
    }

    @Test
    fun `isolates source failure and keeps other sources active`() = runTest {
        val clock = MillisClock { testScheduler.currentTime }
        val failingSource = source(
            id = "failing",
            lifetimeMillis = 5_000L,
            clock = clock,
            deltaProvider = ValueDeltaProvider { error("Expected failure") },
        )
        val healthySource = source(id = "healthy", lifetimeMillis = 5_000L, clock = clock)
        val repository = InMemoryPointSourceRepository(
            pointSources = listOf(failingSource, healthySource),
            scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        repository.start()
        runCurrent()
        flushRepositoryState()
        advanceTimeBy(1_001L)
        runCurrent()

        val statesById = repository.sources.value.associateBy { it.descriptor.id.value }
        assertEquals(PointSourceStatus.FAILED, statesById.getValue("failing").status)
        assertEquals("Expected failure", statesById.getValue("failing").errorMessage)
        assertEquals(PointSourceStatus.ACTIVE, statesById.getValue("healthy").status)
        assertEquals(2, statesById.getValue("healthy").generatedPoints)
    }

    @Test
    fun `does not report coroutine cancellation as source failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repositoryScope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = InMemoryPointSourceRepository(
            pointSources = listOf(
                source(
                    id = "cancelled",
                    lifetimeMillis = 5_000L,
                    clock = MillisClock { testScheduler.currentTime },
                ),
            ),
            scope = repositoryScope,
            dispatcher = dispatcher,
        )

        repository.start()
        runCurrent()
        repositoryScope.cancel()
        runCurrent()

        assertEquals(PointSourceStatus.ACTIVE, repository.sources.value.single().status)
        assertNull(repository.sources.value.single().errorMessage)
    }

    @Test
    fun `retains full history for ten maximum lifetime sources`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = MillisClock { testScheduler.currentTime }
        val sources = List(SOURCE_COUNT) { index ->
            source(
                id = "source-$index",
                lifetimeMillis = MAX_LIFETIME_MILLIS,
                clock = clock,
            )
        }
        val repository = InMemoryPointSourceRepository(
            pointSources = sources,
            scope = backgroundScope,
            dispatcher = dispatcher,
        )

        repository.start()
        runCurrent()
        flushRepositoryState()
        advanceTimeBy(MAX_LIFETIME_MILLIS + 1L)
        runCurrent()

        assertTrue(repository.sources.value.all { it.status == PointSourceStatus.COMPLETED })
        assertTrue(
            repository.sources.value.all { it.generatedPoints == POINTS_PER_MAX_LIFETIME_SOURCE },
        )
        assertEquals(
            SOURCE_COUNT * POINTS_PER_MAX_LIFETIME_SOURCE,
            sources.sumOf { source ->
                repository.pointBatches(source.descriptor.id).first().size
            },
        )
    }

    private fun source(
        id: String,
        lifetimeMillis: Long,
        clock: MillisClock,
        deltaProvider: ValueDeltaProvider = ValueDeltaProvider { 0.5 },
    ) = RandomWalkPointSource(
        descriptor = PointSourceDescriptor(
            id = PointSourceId(id),
            name = id,
            lineColor = LineColor(id.hashCode()),
            initialValue = 0.0,
            startedAtMillis = 0L,
            lifetimeMillis = lifetimeMillis,
        ),
        clock = clock,
        deltaProvider = deltaProvider,
    )

    private fun List<PointSourceState>.ids(): List<String> =
        map { it.descriptor.id.value }

    /** Продвигает виртуальное время за пределы окна агрегации состояния репозитория. */
    private fun TestScope.flushRepositoryState() {
        advanceTimeBy(STATE_BATCH_SETTLE_MILLIS)
        runCurrent()
    }

    private companion object {
        const val SOURCE_COUNT = 10
        const val MAX_LIFETIME_MILLIS = 30L * 60L * 1_000L
        const val POINTS_PER_MAX_LIFETIME_SOURCE = 1_800
        const val STATE_BATCH_SETTLE_MILLIS = 17L
    }
}
