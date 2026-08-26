package com.snn.scichart.data.model

/** Стабильный идентификатор источника во всех слоях приложения. */
@JvmInline
value class PointSourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Point source id must not be blank" }
    }
}

/** Независимый от UI-фреймворка ARGB-цвет линии источника. */
@JvmInline
value class LineColor(val argb: Int)

/**
 * Неизменяемые метаданные источника, общие для генераторов, репозитория и UI.
 *
 * @property id стабильный идентификатор источника.
 * @property name уникальное отображаемое имя.
 * @property lineColor цвет линии и маркера значения.
 * @property initialValue первое выдаваемое значение.
 * @property startedAtMillis временная метка Unix начала работы источника.
 * @property lifetimeMillis продолжительность активной работы источника.
 */
data class PointSourceDescriptor(
    val id: PointSourceId,
    val name: String,
    val lineColor: LineColor,
    val initialValue: Double,
    val startedAtMillis: Long,
    val lifetimeMillis: Long,
) {
    init {
        require(name.isNotBlank()) { "Point source name must not be blank" }
        require(initialValue.isFinite()) { "Initial value must be finite" }
        require(startedAtMillis >= 0L) { "Start timestamp must not be negative" }
        require(lifetimeMillis > 0L) { "Lifetime must be positive" }
        require(startedAtMillis <= Long.MAX_VALUE - lifetimeMillis) {
            "Expiration timestamp must fit into Long"
        }
    }

    /** Временная метка Unix, в которую источник завершает работу. */
    val expiresAtMillis: Long
        get() = startedAtMillis + lifetimeMillis

    /** Возвращает неотрицательное оставшееся время жизни на момент [timestampMillis]. */
    fun remainingAt(timestampMillis: Long): Long =
        (expiresAtMillis - timestampMillis).coerceIn(0L, lifetimeMillis)
}
