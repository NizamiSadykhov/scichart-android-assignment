package com.snn.scichart.data.source

/** Предоставляет следующее приращение значения для генерируемой последовательности точек. */
fun interface ValueDeltaProvider {

    /** Возвращает приращение, которое нужно добавить к предыдущему значению. */
    fun nextDelta(): Double
}
