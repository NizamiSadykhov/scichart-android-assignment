package com.snn.scichart.ui.chart

import androidx.lifecycle.SavedStateHandle
import com.snn.scichart.data.model.ChartPoint
import com.snn.scichart.data.model.LineColor
import com.snn.scichart.data.model.PointSourceDescriptor
import com.snn.scichart.data.model.PointSourceId
import com.snn.scichart.data.model.PointSourceState
import com.snn.scichart.data.model.PointSourceStatus
import com.snn.scichart.data.repository.PointSourceRepository
import com.snn.scichart.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `maps repository state and keeps visibility as ui state`() = runTest {
        val repository = FakePointSourceRepository()
        val viewModel = createViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        repository.mutableSources.value = listOf(
            sourceState(
                id = "generator-1",
                status = PointSourceStatus.ACTIVE,
                remainingMillis = 42_000L,
                currentValue = 1.25,
                generatedPoints = 8,
            ),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.hasUnexpectedError)
        assertEquals(
            ChartSourceUiModel(
                id = "generator-1",
                name = "Generator generator-1",
                lineColorArgb = 0xFFE53935.toInt(),
                status = ChartSourceUiStatus.ACTIVE,
                remainingMillis = 42_000L,
                currentValue = 1.25,
                generatedPoints = 8,
                isVisible = true,
            ),
            viewModel.uiState.value.sources.single(),
        )

        viewModel.onSourceVisibilityChanged("generator-1", isVisible = false)
        repository.mutableSources.value = listOf(
            sourceState(
                id = "generator-1",
                status = PointSourceStatus.COMPLETED,
                remainingMillis = 0L,
                currentValue = 2.5,
                generatedPoints = 10,
            ),
        )
        advanceUntilIdle()

        val updatedSource = viewModel.uiState.value.sources.single()
        assertFalse(updatedSource.isVisible)
        assertEquals(ChartSourceUiStatus.COMPLETED, updatedSource.status)
        assertEquals(2.5, updatedSource.currentValue, 0.0)
        assertEquals(10, updatedSource.generatedPoints)
    }

    @Test
    fun `exposes unexpected repository failure as ui state`() = runTest {
        val repository = object : PointSourceRepository {
            override val sources: Flow<List<PointSourceState>> = flow {
                error("Repository failure")
            }

            override fun pointBatches(sourceId: PointSourceId): Flow<List<ChartPoint>> = emptyFlow()
        }
        val viewModel = createViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.hasUnexpectedError)
        assertTrue(viewModel.uiState.value.sources.isEmpty())
    }

    @Test
    fun `maps repository point batches to presentation models`() = runTest {
        val repository = FakePointSourceRepository()
        val viewModel = createViewModel(repository)
        val batch = async {
            viewModel.pointBatches("generator-1").first()
        }
        runCurrent()

        repository.mutablePoints.emit(
            listOf(
                ChartPoint(
                    timestampMillis = 12_345L,
                    value = 4.25,
                ),
            ),
        )

        assertEquals(
            listOf(
                ChartPointUiModel(
                    timestampMillis = 12_345L,
                    value = 4.25,
                ),
            ),
            batch.await(),
        )
    }

    @Test
    fun `restores hidden sources after view model replacement`() = runTest {
        val repository = FakePointSourceRepository()
        val savedStateHandle = SavedStateHandle()
        val firstViewModel = createViewModel(repository, savedStateHandle)
        firstViewModel.onSourceVisibilityChanged("generator-1", isVisible = false)

        val restoredViewModel = createViewModel(repository, savedStateHandle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            restoredViewModel.uiState.collect()
        }
        repository.mutableSources.value = listOf(
            sourceState(
                id = "generator-1",
                status = PointSourceStatus.ACTIVE,
                remainingMillis = 42_000L,
                currentValue = 1.25,
                generatedPoints = 8,
            ),
        )
        advanceUntilIdle()

        assertFalse(restoredViewModel.uiState.value.sources.single().isVisible)
    }

    private fun createViewModel(
        repository: PointSourceRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): ChartViewModel = ChartViewModel(
        pointSourceRepository = repository,
        pointMappingDispatcher = mainDispatcherRule.testDispatcher,
        savedStateHandle = savedStateHandle,
    )

    private fun sourceState(
        id: String,
        status: PointSourceStatus,
        remainingMillis: Long,
        currentValue: Double,
        generatedPoints: Int,
    ): PointSourceState = PointSourceState(
        descriptor = PointSourceDescriptor(
            id = PointSourceId(id),
            name = "Generator $id",
            lineColor = LineColor(0xFFE53935.toInt()),
            initialValue = 0.0,
            startedAtMillis = 0L,
            lifetimeMillis = 60_000L,
        ),
        status = status,
        remainingMillis = remainingMillis,
        currentValue = currentValue,
        generatedPoints = generatedPoints,
    )

    private class FakePointSourceRepository : PointSourceRepository {
        val mutableSources = MutableStateFlow<List<PointSourceState>>(emptyList())
        val mutablePoints = MutableSharedFlow<List<ChartPoint>>()

        override val sources: Flow<List<PointSourceState>> = mutableSources

        override fun pointBatches(sourceId: PointSourceId): Flow<List<ChartPoint>> = mutablePoints
    }
}
