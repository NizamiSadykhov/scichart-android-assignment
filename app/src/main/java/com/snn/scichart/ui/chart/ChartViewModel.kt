package com.snn.scichart.ui.chart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snn.scichart.data.model.PointSourceId
import com.snn.scichart.data.model.PointSourceState
import com.snn.scichart.data.model.PointSourceStatus
import com.snn.scichart.data.repository.PointSourceRepository
import com.snn.scichart.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Владеет состоянием экрана графика и преобразует модели data-слоя в модели presentation-слоя.
 *
 * ViewModel реализует однонаправленный поток данных: UI наблюдает только [uiState], а действия
 * пользователя передаёт через публичные методы. Репозиторий не раскрывается слою Compose.
 */
@HiltViewModel
class ChartViewModel @Inject constructor(
    private val pointSourceRepository: PointSourceRepository,
    @param:DefaultDispatcher private val pointMappingDispatcher: CoroutineDispatcher,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val hiddenSourceIds = savedStateHandle.getStateFlow(
        HIDDEN_SOURCE_IDS_KEY,
        emptyList<String>(),
    )

    /** Состояние экрана, доступное потребителям только для чтения. */
    val uiState: StateFlow<ChartUiState> = combine(
        pointSourceRepository.sources,
        hiddenSourceIds,
    ) { sources, hiddenIds ->
        val hiddenIdSet = hiddenIds.toSet()
        ChartUiState(
            isLoading = false,
            sources = sources.map { source ->
                source.toUiModel(
                    isVisible = source.descriptor.id.value !in hiddenIdSet,
                )
            },
        )
    }
        .catch {
            emit(
                ChartUiState(
                    isLoading = false,
                    hasUnexpectedError = true,
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ChartUiState(),
        )

    /**
     * Стабильная конфигурация графика, которая не меняется при обновлении таймеров и значений списка.
     */
    val chartSeries: StateFlow<List<ChartSeriesUiModel>> = uiState
        .map { state -> state.sources.map(ChartSourceUiModel::toChartSeriesUiModel) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    /** Изменяет и сохраняет видимость линии [sourceId], не изменяя состояние data-слоя. */
    fun onSourceVisibilityChanged(sourceId: String, isVisible: Boolean) {
        val updatedHiddenIds = hiddenSourceIds.value.toMutableSet().apply {
            if (isVisible) remove(sourceId) else add(sourceId)
        }
        savedStateHandle[HIDDEN_SOURCE_IDS_KEY] = ArrayList(updatedHiddenIds)
    }

    /**
     * Возвращает историю и новые точки [sourceId] пакетами presentation-моделей.
     *
     * Преобразование потенциально большой истории выполняется вне главного потока. Благодаря этому
     * новая SciChart-поверхность восстанавливается одним пакетом без блокировки Compose множеством
     * отдельных map-вызовов.
     */
    fun pointBatches(sourceId: String): Flow<List<ChartPointUiModel>> =
        pointSourceRepository.pointBatches(PointSourceId(sourceId))
            .map { points ->
                points.map { point ->
                    ChartPointUiModel(
                        timestampMillis = point.timestampMillis,
                        value = point.value,
                    )
                }
            }
            .flowOn(pointMappingDispatcher)

    private fun PointSourceState.toUiModel(isVisible: Boolean): ChartSourceUiModel =
        ChartSourceUiModel(
            id = descriptor.id.value,
            name = descriptor.name,
            lineColorArgb = descriptor.lineColor.argb,
            status = status.toUiStatus(),
            remainingMillis = remainingMillis,
            currentValue = currentValue,
            generatedPoints = generatedPoints,
            isVisible = isVisible,
        )

    private fun PointSourceStatus.toUiStatus(): ChartSourceUiStatus = when (this) {
        PointSourceStatus.ACTIVE -> ChartSourceUiStatus.ACTIVE
        PointSourceStatus.COMPLETED -> ChartSourceUiStatus.COMPLETED
        PointSourceStatus.FAILED -> ChartSourceUiStatus.FAILED
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val HIDDEN_SOURCE_IDS_KEY = "hiddenSourceIds"
    }
}
