package com.snn.scichart.data.model

/**
 * Неизменяемая точка, полученная от источника данных.
 *
 * @property timestampMillis временная метка Unix в миллисекундах для оси X.
 * @property value конечное числовое значение для оси Y.
 */
data class ChartPoint(
    val timestampMillis: Long,
    val value: Double,
) {
    init {
        require(timestampMillis >= 0L) { "Point timestamp must not be negative" }
        require(value.isFinite()) { "Point value must be finite" }
    }
}
