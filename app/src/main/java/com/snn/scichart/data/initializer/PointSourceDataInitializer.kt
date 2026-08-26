package com.snn.scichart.data.initializer

import com.snn.scichart.data.repository.InMemoryPointSourceRepository
import javax.inject.Inject

/**
 * Запускает операции data-слоя, которые должны жить столько же, сколько процесс приложения.
 *
 * Отдельный инициализатор не раскрывает методы управления жизненным циклом в публичном контракте
 * [com.snn.scichart.data.repository.PointSourceRepository].
 */
class PointSourceDataInitializer @Inject constructor(
    private val repository: InMemoryPointSourceRepository,
) {

    /** Идемпотентно запускает сбор данных всех настроенных источников. */
    fun initialize() {
        repository.start()
    }
}
