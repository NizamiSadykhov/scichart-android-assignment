package com.snn.scichart.data.source

import com.snn.scichart.core.time.MillisClock
import com.snn.scichart.data.model.ChartPoint
import com.snn.scichart.data.model.PointSourceDescriptor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

/**
 * Создаёт независимую последовательность случайного блуждания с заданным интервалом.
 *
 * Начальное значение выдаётся сразу. Каждое следующее значение равно предыдущему плюс приращение
 * от [deltaProvider]. Поток не выдаёт точки в момент завершения времени жизни или после него.
 *
 * @param descriptor неизменяемые идентификатор, оформление, начальное значение и время жизни.
 * @param clock источник времени для планирования и детерминированных тестов.
 * @param deltaProvider источник приращений для последовательных значений.
 * @param intervalMillis интервал между соседними точками в миллисекундах.
 */
class RandomWalkPointSource(
    override val descriptor: PointSourceDescriptor,
    private val clock: MillisClock,
    private val deltaProvider: ValueDeltaProvider,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
) : PointSource {

    init {
        require(intervalMillis > 0) { "Point interval must be positive" }
    }

    override fun stream(): Flow<ChartPoint> = flow {
        if (clock.nowMillis() >= descriptor.expiresAtMillis) {
            return@flow
        }

        var currentValue = descriptor.initialValue
        emit(
            ChartPoint(
                timestampMillis = descriptor.startedAtMillis,
                value = currentValue,
            ),
        )

        var nextPointAtMillis = descriptor.startedAtMillis + intervalMillis
        while (nextPointAtMillis < descriptor.expiresAtMillis) {
            val waitMillis = (nextPointAtMillis - clock.nowMillis()).coerceAtLeast(0L)
            delay(waitMillis.milliseconds)

            if (clock.nowMillis() >= descriptor.expiresAtMillis) {
                break
            }

            currentValue += deltaProvider.nextDelta()
            emit(
                ChartPoint(
                    timestampMillis = nextPointAtMillis,
                    value = currentValue,
                ),
            )
            nextPointAtMillis += intervalMillis
        }

        val waitUntilExpirationMillis =
            (descriptor.expiresAtMillis - clock.nowMillis()).coerceAtLeast(0L)
        delay(waitUntilExpirationMillis.milliseconds)
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 1_000L
    }
}
