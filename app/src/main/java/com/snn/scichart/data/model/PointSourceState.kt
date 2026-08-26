package com.snn.scichart.data.model

/** Состояние жизненного цикла источника, публикуемое репозиторием. */
enum class PointSourceStatus {
    /** Источник продолжает создавать точки. */
    ACTIVE,

    /** Заданное время жизни источника закончилось. */
    COMPLETED,

    /** Сбор точек остановлен из-за ошибки источника. */
    FAILED,
}

/**
 * Наблюдаемый снимок состояния источника для слоя представления.
 *
 * @property descriptor неизменяемые метаданные источника.
 * @property status текущее состояние жизненного цикла.
 * @property remainingMillis неотрицательное оставшееся время жизни.
 * @property currentValue последнее полученное значение.
 * @property generatedPoints количество точек, сохранённых репозиторием.
 * @property errorMessage описание ошибки, если [status] равен [PointSourceStatus.FAILED].
 */
data class PointSourceState(
    val descriptor: PointSourceDescriptor,
    val status: PointSourceStatus,
    val remainingMillis: Long,
    val currentValue: Double,
    val generatedPoints: Int,
    val errorMessage: String? = null,
) {
    init {
        require(remainingMillis >= 0L) { "Remaining lifetime must not be negative" }
        require(currentValue.isFinite()) { "Current value must be finite" }
        require(generatedPoints >= 0) { "Generated point count must not be negative" }
        require(status == PointSourceStatus.FAILED || errorMessage == null) {
            "Only a failed source may expose an error message"
        }
    }
}
