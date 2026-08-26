package com.snn.scichart.ui.chart

/** Состояние жизненного цикла источника в терминах presentation-слоя. */
enum class ChartSourceUiStatus {
    /** Источник продолжает создавать точки. */
    ACTIVE,

    /** Источник штатно завершил работу. */
    COMPLETED,

    /** Источник остановлен из-за ошибки. */
    FAILED,
}

/**
 * Точка графика в терминах presentation-слоя.
 *
 * @property timestampMillis временная метка Unix в миллисекундах.
 * @property value конечное числовое значение линии.
 */
data class ChartPointUiModel(
    val timestampMillis: Long,
    val value: Double,
)

/**
 * Стабильная конфигурация серии, не содержащая часто изменяющиеся данные строки списка.
 *
 * @property id стабильный идентификатор серии.
 * @property name имя для легенды.
 * @property lineColorArgb цвет линии, маркера и элемента легенды.
 * @property status состояние жизненного цикла источника.
 * @property isVisible должна ли серия отображаться на графике.
 */
data class ChartSeriesUiModel(
    val id: String,
    val name: String,
    val lineColorArgb: Int,
    val status: ChartSourceUiStatus,
    val isVisible: Boolean,
)

/**
 * Неизменяемая модель строки источника, готовая для отображения Compose UI.
 *
 * @property id стабильный идентификатор для событий UI и ключей списка.
 * @property name отображаемое имя источника.
 * @property lineColorArgb ARGB-цвет линии и индикатора.
 * @property status состояние жизненного цикла в терминах UI.
 * @property remainingMillis неотрицательное оставшееся время работы.
 * @property currentValue последнее полученное значение.
 * @property generatedPoints количество накопленных точек.
 * @property isVisible должна ли линия отображаться на графике.
 */
data class ChartSourceUiModel(
    val id: String,
    val name: String,
    val lineColorArgb: Int,
    val status: ChartSourceUiStatus,
    val remainingMillis: Long,
    val currentValue: Double,
    val generatedPoints: Int,
    val isVisible: Boolean,
) {
    /** Возвращает конфигурацию серии без таймера, значения и счётчика точек. */
    fun toChartSeriesUiModel(): ChartSeriesUiModel = ChartSeriesUiModel(
        id = id,
        name = name,
        lineColorArgb = lineColorArgb,
        status = status,
        isVisible = isVisible,
    )
}

/**
 * Полное неизменяемое состояние экрана графика.
 *
 * Единственным владельцем и источником изменений этого состояния является [ChartViewModel].
 */
data class ChartUiState(
    val isLoading: Boolean = true,
    val hasUnexpectedError: Boolean = false,
    val sources: List<ChartSourceUiModel> = emptyList(),
)
