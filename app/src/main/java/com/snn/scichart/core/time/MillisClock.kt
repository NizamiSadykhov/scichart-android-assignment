package com.snn.scichart.core.time

/**
 * Предоставляет текущее время и позволяет детерминированно тестировать зависящий от времени код.
 */
fun interface MillisClock {

    /** Возвращает текущую временную метку Unix в миллисекундах. */
    fun nowMillis(): Long
}

/** Рабочая реализация [MillisClock], использующая системные часы. */
object SystemMillisClock : MillisClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
