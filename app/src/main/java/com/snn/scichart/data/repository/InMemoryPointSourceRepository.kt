package com.snn.scichart.data.repository

import com.snn.scichart.data.model.ChartPoint
import com.snn.scichart.data.model.PointSourceId
import com.snn.scichart.data.model.PointSourceState
import com.snn.scichart.data.model.PointSourceStatus
import com.snn.scichart.data.source.PointSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Репозиторий уровня приложения, параллельно собирающий и хранящий данные источников в памяти.
 *
 * Для каждого источника запускается отдельная корутина. История хранится отдельно от транспорта:
 * новый потребитель получает один атомарный снимок, а затем пакетный поток новых точек. Такой контракт
 * сохраняет данные между экранами и не создаёт replay-шторм из тысяч отдельных событий на UI-потоке.
 */
class InMemoryPointSourceRepository(
    pointSources: List<PointSource>,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PointSourceRepository {

    private val pointSources = pointSources.toList()
    private val started = AtomicBoolean(false)
    private val sourceOrder = pointSources
        .mapIndexed { index, source -> source.descriptor.id to index }
        .toMap()

    private val pointHistories = pointSources.associate { source ->
        source.descriptor.id to PointHistory()
    }
    private val stateEvents = Channel<SourceStateEvent>(capacity = Channel.BUFFERED)

    private val mutableSources = MutableStateFlow(
        sortStates(
            pointSources.map { source ->
                PointSourceState(
                    descriptor = source.descriptor,
                    status = PointSourceStatus.ACTIVE,
                    remainingMillis = source.descriptor.lifetimeMillis,
                    currentValue = source.descriptor.initialValue,
                    generatedPoints = 0,
                )
            },
        ),
    )

    override val sources: StateFlow<List<PointSourceState>> = mutableSources.asStateFlow()

    init {
        require(pointSources.isNotEmpty()) { "At least one point source is required" }
        require(pointSources.map { it.descriptor.id }.distinct().size == pointSources.size) {
            "Point source ids must be unique"
        }
    }

    override fun pointBatches(sourceId: PointSourceId): Flow<List<ChartPoint>> =
        requireNotNull(pointHistories[sourceId]) { "Unknown point source: ${sourceId.value}" }
            .observe()

    /** Запускает однократный сбор всех источников в области приложения. */
    internal fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        startStateAggregator()
        pointSources.forEach { source ->
            scope.launch(dispatcher + CoroutineName(source.descriptor.name)) {
                try {
                    source.stream().collect { point ->
                        pointHistories.getValue(source.descriptor.id).append(point)
                        stateEvents.send(SourceStateEvent.PointGenerated(source.descriptor.id, point))
                    }
                    stateEvents.send(SourceStateEvent.Completed(source.descriptor.id))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    stateEvents.send(
                        SourceStateEvent.Failed(
                            sourceId = source.descriptor.id,
                            message = error.message,
                        ),
                    )
                }
            }
        }
    }

    /** Объединяет события близких по времени источников в одно состояние для UI. */
    private fun startStateAggregator() {
        scope.launch(dispatcher + CoroutineName(STATE_AGGREGATOR_COROUTINE_NAME)) {
            for (firstEvent in stateEvents) {
                delay(STATE_BATCH_WINDOW_MILLIS.milliseconds)
                val batch = buildList {
                    add(firstEvent)
                    while (true) {
                        add(stateEvents.tryReceive().getOrNull() ?: break)
                    }
                }
                applyStateEvents(batch)
            }
        }
    }

    private fun applyStateEvents(events: List<SourceStateEvent>) {
        val eventsBySource = events.groupBy(SourceStateEvent::sourceId)
        mutableSources.update { currentStates ->
            sortStates(
                currentStates.map { state ->
                    eventsBySource[state.descriptor.id]
                        ?.fold(state) { currentState, event -> currentState.apply(event) }
                        ?: state
                },
            )
        }
    }

    private fun PointSourceState.apply(event: SourceStateEvent): PointSourceState = when (event) {
        is SourceStateEvent.PointGenerated -> copy(
            currentValue = event.point.value,
            remainingMillis = descriptor.remainingAt(event.point.timestampMillis),
            generatedPoints = generatedPoints + 1,
        )

        is SourceStateEvent.Completed -> copy(
            status = PointSourceStatus.COMPLETED,
            remainingMillis = 0L,
        )

        is SourceStateEvent.Failed -> copy(
            status = PointSourceStatus.FAILED,
            remainingMillis = 0L,
            errorMessage = event.message,
        )
    }

    private fun sortStates(states: List<PointSourceState>): List<PointSourceState> =
        states.sortedWith(
            compareBy<PointSourceState> { it.remainingMillis }
                .thenBy { sourceOrder.getValue(it.descriptor.id) },
        )

    private sealed interface SourceStateEvent {
        val sourceId: PointSourceId

        data class PointGenerated(
            override val sourceId: PointSourceId,
            val point: ChartPoint,
        ) : SourceStateEvent

        data class Completed(override val sourceId: PointSourceId) : SourceStateEvent

        data class Failed(
            override val sourceId: PointSourceId,
            val message: String?,
        ) : SourceStateEvent
    }

    /**
     * Синхронизирует снимок истории с регистрацией live-подписчика без окна потери данных.
     *
     * Канал каждого подписчика ограничен стандартной ёмкостью [callbackFlow], поэтому медленный
     * потребитель создаёт backpressure только своему источнику вместо неограниченного роста памяти.
     */
    private class PointHistory {
        private val lock = Any()
        private val points = mutableListOf<ChartPoint>()
        private val subscribers = mutableSetOf<SendChannel<List<ChartPoint>>>()

        fun observe(): Flow<List<ChartPoint>> = callbackFlow {
            val isRegistered = synchronized(lock) {
                if (trySend(points.toList()).isSuccess) {
                    subscribers += channel
                    true
                } else {
                    false
                }
            }
            if (!isRegistered) close()

            awaitClose {
                synchronized(lock) {
                    subscribers -= channel
                }
            }
        }

        suspend fun append(point: ChartPoint) {
            val currentSubscribers = synchronized(lock) {
                points += point
                subscribers.toList()
            }
            if (currentSubscribers.isEmpty()) return

            val batch = listOf(point)
            currentSubscribers.forEach { subscriber ->
                try {
                    subscriber.send(batch)
                } catch (_: ClosedSendChannelException) {
                    synchronized(lock) {
                        subscribers -= subscriber
                    }
                }
            }
        }
    }

    private companion object {
        const val STATE_BATCH_WINDOW_MILLIS = 16L
        const val STATE_AGGREGATOR_COROUTINE_NAME = "Point source state aggregator"
    }
}
