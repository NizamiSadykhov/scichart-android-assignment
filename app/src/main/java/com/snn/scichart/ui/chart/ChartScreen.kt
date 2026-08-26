package com.snn.scichart.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snn.scichart.BuildConfig
import com.snn.scichart.R
import com.snn.scichart.ui.theme.ScichartTheme
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Связывает [ChartViewModel] с независимым от DI экраном и собирает состояние с учётом lifecycle.
 */
@Composable
fun ChartRoute(
    viewModel: ChartViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chartSeries by viewModel.chartSeries.collectAsStateWithLifecycle()
    val pointFlowProvider = remember(viewModel) {
        { sourceId: String -> viewModel.pointBatches(sourceId) }
    }
    val onSourceVisibilityChanged = remember(viewModel) {
        { sourceId: String, isVisible: Boolean ->
            viewModel.onSourceVisibilityChanged(sourceId, isVisible)
        }
    }

    ChartScreen(
        uiState = uiState,
        chartSeries = chartSeries,
        pointFlowProvider = pointFlowProvider,
        onSourceVisibilityChanged = onSourceVisibilityChanged,
        modifier = modifier,
    )
}

/**
 * Отображает экран графика как чистую функцию от [uiState] и пользовательских событий.
 */
@Composable
fun ChartScreen(
    uiState: ChartUiState,
    pointFlowProvider: (sourceId: String) -> Flow<List<ChartPointUiModel>>,
    onSourceVisibilityChanged: (sourceId: String, isVisible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isSciChartLicenseConfigured: Boolean = BuildConfig.SCICHART_LICENSE_KEY.isNotBlank(),
    chartSeries: List<ChartSeriesUiModel> = uiState.sources.map(
        ChartSourceUiModel::toChartSeriesUiModel,
    ),
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            val useHorizontalLayout = maxWidth > maxHeight

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.chart_integration_title),
                    style = MaterialTheme.typography.headlineSmall,
                )

                if (useHorizontalLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ChartPane(
                            chartSeries = chartSeries,
                            pointFlowProvider = pointFlowProvider,
                            isSciChartLicenseConfigured = isSciChartLicenseConfigured,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        SourceList(
                            uiState = uiState,
                            onSourceVisibilityChanged = onSourceVisibilityChanged,
                            modifier = Modifier
                                .widthIn(max = 360.dp)
                                .fillMaxHeight()
                                .weight(LANDSCAPE_LIST_WEIGHT),
                        )
                    }
                } else {
                    ChartPane(
                        chartSeries = chartSeries,
                        pointFlowProvider = pointFlowProvider,
                        isSciChartLicenseConfigured = isSciChartLicenseConfigured,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    SourceList(
                        uiState = uiState,
                        onSourceVisibilityChanged = onSourceVisibilityChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartPane(
    chartSeries: List<ChartSeriesUiModel>,
    pointFlowProvider: (sourceId: String) -> Flow<List<ChartPointUiModel>>,
    isSciChartLicenseConfigured: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(CHART_PANE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VisibleSourcesLegend(sources = chartSeries)
        if (!isSciChartLicenseConfigured) {
            MissingLicenseMessage()
        } else {
            StreamingLineChart(
                sources = chartSeries,
                pointFlowProvider = pointFlowProvider,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/** Легенда строится только по отображаемым линиям и переносится на доступной ширине. */
@Composable
private fun VisibleSourcesLegend(
    sources: List<ChartSeriesUiModel>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sources.filter(ChartSeriesUiModel::isVisible).forEach { source ->
            val legendItemDescription = stringResource(
                R.string.legend_item_content_description,
                source.name,
            )
            Row(
                modifier = Modifier.semantics {
                    contentDescription = legendItemDescription
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(source.lineColorArgb), CircleShape),
                )
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SourceList(
    uiState: ChartUiState,
    onSourceVisibilityChanged: (sourceId: String, isVisible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(SOURCE_LIST_PANE_TEST_TAG)) {
        Text(
            text = stringResource(R.string.sources_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        when {
            uiState.isLoading -> Column(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.sources_loading),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            uiState.hasUnexpectedError -> Text(
                text = stringResource(R.string.sources_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )

            else -> LazyColumn {
                items(
                    items = uiState.sources,
                    key = ChartSourceUiModel::id,
                ) { source ->
                    SourceRow(
                        source = source,
                        onVisibilityChanged = { isVisible ->
                            onSourceVisibilityChanged(source.id, isVisible)
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: ChartSourceUiModel,
    onVisibilityChanged: (Boolean) -> Unit,
) {
    val status = when (source.status) {
        ChartSourceUiStatus.ACTIVE -> stringResource(
            R.string.source_remaining_time,
            formatRemainingTime(source.remainingMillis),
        )

        ChartSourceUiStatus.COMPLETED -> stringResource(R.string.source_completed)
        ChartSourceUiStatus.FAILED -> stringResource(R.string.source_failed)
    }
    val visibilityDescription = stringResource(R.string.source_visibility, source.name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color(source.lineColorArgb), CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    R.string.source_metrics,
                    status,
                    source.currentValue,
                    source.generatedPoints,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(
            checked = source.isVisible,
            onCheckedChange = onVisibilityChanged,
            modifier = Modifier
                .testTag("$SOURCE_VISIBILITY_TEST_TAG_PREFIX${source.id}")
                .semantics {
                    contentDescription = visibilityDescription
                },
        )
    }
}

@Composable
private fun MissingLicenseMessage() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.missing_license_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.missing_license_message),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Форматирует неотрицательное время как стабильный для любой локали таймер `MM:SS`. */
internal fun formatRemainingTime(remainingMillis: Long): String {
    require(remainingMillis >= 0L) { "Remaining time must not be negative" }
    val totalSeconds = (remainingMillis + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true)
@Composable
private fun ChartScreenPreview() {
    ScichartTheme {
        ChartScreen(
            uiState = ChartUiState(
                isLoading = false,
                sources = listOf(
                    ChartSourceUiModel(
                        id = "generator-1",
                        name = "Generator #1",
                        lineColorArgb = 0xFFE53935.toInt(),
                        status = ChartSourceUiStatus.ACTIVE,
                        remainingMillis = 83_000L,
                        currentValue = 1.234,
                        generatedPoints = 42,
                        isVisible = true,
                    ),
                ),
            ),
            pointFlowProvider = { emptyFlow() },
            onSourceVisibilityChanged = { _, _ -> },
            isSciChartLicenseConfigured = false,
        )
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val LANDSCAPE_LIST_WEIGHT = 0.45f
internal const val CHART_PANE_TEST_TAG = "chart-pane"
internal const val SOURCE_LIST_PANE_TEST_TAG = "source-list-pane"
internal const val SOURCE_VISIBILITY_TEST_TAG_PREFIX = "source-visibility-"
