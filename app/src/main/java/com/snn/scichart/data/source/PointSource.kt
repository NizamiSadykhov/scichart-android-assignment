package com.snn.scichart.data.source

import com.snn.scichart.data.model.ChartPoint
import com.snn.scichart.data.model.PointSourceDescriptor
import kotlinx.coroutines.flow.Flow

/**
 * Контракт источника точек внутри data-слоя.
 *
 * Благодаря этой границе локальный генератор можно заменить сервером, базой данных или потоком
 * устройства без изменения репозитория и UI.
 */
interface PointSource {

    /** Неизменяемые метаданные источника и его времени жизни. */
    val descriptor: PointSourceDescriptor

    /** Выдаёт упорядоченные точки и завершается после истечения времени жизни источника. */
    fun stream(): Flow<ChartPoint>
}
