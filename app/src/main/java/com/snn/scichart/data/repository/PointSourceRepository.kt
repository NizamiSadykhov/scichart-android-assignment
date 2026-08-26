package com.snn.scichart.data.repository

import com.snn.scichart.data.model.ChartPoint
import com.snn.scichart.data.model.PointSourceId
import com.snn.scichart.data.model.PointSourceState
import kotlinx.coroutines.flow.Flow

/**
 * Контракт data-слоя, который координирует источники и предоставляет данные приложения.
 *
 * Реализация может получать данные от локальных генераторов, сервера или другого транспорта,
 * не связывая потребителей с конкретным источником и способом хранения.
 */
interface PointSourceRepository {

    /** Поток актуальных состояний, упорядоченных по оставшемуся времени жизни. */
    val sources: Flow<List<PointSourceState>>

    /**
     * Возвращает историю и новые точки источника [sourceId] пакетами.
     *
     * Новый подписчик сначала атомарно получает один снимок всей накопленной истории, а затем —
     * непустые пакеты новых точек в исходном порядке. Пакетный контракт не заставляет presentation-
     * слой обрабатывать большую историю по одной точке на главном потоке.
     *
     * @throws IllegalArgumentException если источник [sourceId] не зарегистрирован в репозитории.
     */
    fun pointBatches(sourceId: PointSourceId): Flow<List<ChartPoint>>
}
