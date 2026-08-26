package com.snn.scichart.data.source

import com.snn.scichart.core.time.MillisClock
import com.snn.scichart.data.model.LineColor
import com.snn.scichart.data.model.PointSourceDescriptor
import com.snn.scichart.data.model.PointSourceId
import com.snn.scichart.di.PointIntervalMillis
import javax.inject.Inject
import kotlin.random.Random

/** Создаёт настроенный набор независимых источников случайного блуждания. */
class RandomWalkSourceFactory @Inject constructor(
    private val random: Random,
    private val clock: MillisClock,
    @param:PointIntervalMillis private val pointIntervalMillis: Long = DEFAULT_POINT_INTERVAL_MILLIS,
) {

    init {
        require(pointIntervalMillis > 0L) { "Point interval must be positive" }
    }

    /**
     * Создаёт [count] источников с уникальными идентификаторами, цветами, начальными значениями
     * и временем жизни.
     *
     * @throws IllegalArgumentException если [count] выходит за границы доступной палитры.
     */
    fun create(count: Int = DEFAULT_SOURCE_COUNT): List<PointSource> {
        require(count in 1..LINE_COLORS.size) {
            "Source count must be between 1 and ${LINE_COLORS.size}"
        }

        val startedAtMillis = clock.nowMillis()
        return (1..count).map { number ->
            val descriptor = PointSourceDescriptor(
                id = PointSourceId("generator-$number"),
                name = "Generator #$number",
                lineColor = LineColor(LINE_COLORS[number - 1]),
                initialValue = random.nextDouble(from = -1.0, until = 1.0),
                startedAtMillis = startedAtMillis,
                lifetimeMillis = random.nextInt(
                    from = MIN_LIFETIME_MINUTES,
                    until = MAX_LIFETIME_MINUTES + 1,
                ) * MILLIS_PER_MINUTE,
            )

            RandomWalkPointSource(
                descriptor = descriptor,
                clock = clock,
                deltaProvider = RandomValueDeltaProvider(Random(random.nextInt())),
                intervalMillis = pointIntervalMillis,
            )
        }
    }

    private companion object {
        const val DEFAULT_SOURCE_COUNT = 10
        const val MIN_LIFETIME_MINUTES = 1
        const val MAX_LIFETIME_MINUTES = 30
        const val MILLIS_PER_MINUTE = 60_000L
        const val DEFAULT_POINT_INTERVAL_MILLIS = 1_000L

        private val LINE_COLORS = listOf(
            0xFFE53935.toInt(),
            0xFF1E88E5.toInt(),
            0xFF43A047.toInt(),
            0xFFFDD835.toInt(),
            0xFF8E24AA.toInt(),
            0xFF00ACC1.toInt(),
            0xFFFB8C00.toInt(),
            0xFF3949AB.toInt(),
            0xFF6D4C41.toInt(),
            0xFFD81B60.toInt(),
        )
    }
}
