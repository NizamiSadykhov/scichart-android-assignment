package com.snn.scichart.ui.chart

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.scichart.charting.ClipMode
import com.scichart.charting.Direction2D
import com.scichart.charting.model.dataSeries.XyDataSeries
import com.scichart.charting.modifiers.PinchZoomModifier
import com.scichart.charting.modifiers.ZoomPanModifier
import com.scichart.charting.visuals.SciChartSurface
import com.scichart.charting.visuals.annotations.AxisMarkerAnnotation
import com.scichart.charting.visuals.annotations.HorizontalLineAnnotation
import com.scichart.charting.visuals.annotations.TextFormattedValueProvider
import com.scichart.charting.visuals.axes.AutoRange
import com.scichart.charting.visuals.axes.DateAxis
import com.scichart.charting.visuals.axes.NumericAxis
import com.scichart.charting.visuals.renderableSeries.FastLineRenderableSeries
import com.scichart.core.framework.UpdateSuspender
import com.scichart.data.model.DateRange
import com.scichart.data.model.DoubleRange
import com.scichart.drawing.common.FontStyle
import com.scichart.drawing.common.SolidPenStyle
import com.snn.scichart.R
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Отображает потоковые линии SciChart и собирает точки только при активном lifecycle экрана.
 *
 * Данные добавляются в существующие [XyDataSeries] инкрементально. Перекомпозиция обновляет только
 * метаданные и видимость, не создавая заново поверхность или накопленную историю.
 */
@Composable
fun StreamingLineChart(
    sources: List<ChartSeriesUiModel>,
    pointFlowProvider: (sourceId: String) -> Flow<List<ChartPointUiModel>>,
    modifier: Modifier = Modifier,
) {
    val controller = remember { StreamingChartController() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPointFlowProvider by rememberUpdatedState(pointFlowProvider)
    val sourceIds = sources.map(ChartSeriesUiModel::id)
    val chartDescription = stringResource(R.string.chart_content_description)

    AndroidView(
        modifier = modifier.semantics {
            contentDescription = chartDescription
        },
        factory = controller::createSurface,
        update = { controller.updateSources(sources) },
    )

    LaunchedEffect(controller, lifecycleOwner, sourceIds) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coroutineScope {
                sourceIds.forEach { sourceId ->
                    launch {
                        currentPointFlowProvider(sourceId).collect { points ->
                            controller.enqueuePoints(sourceId, points)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(controller) {
        onDispose(controller::dispose)
    }
}

/** Управляет изменяемыми объектами SciChart строго внутри UI-слоя и главного потока. */
@Suppress("UsePropertyAccessSyntax") // Часть Java-setter'ов SciChart не представлена изменяемыми свойствами.
private class StreamingChartController {

    private var surface: SciChartSurface? = null
    private var xAxis: DateAxis? = null
    private var initialXRangeApplied = false
    private var yAutoRangeScheduled = false
    private var pointFlushScheduled = false

    private val seriesById = LinkedHashMap<String, SeriesBundle>()
    private val pendingPointsById = mutableMapOf<String, MutableList<ChartPointUiModel>>()

    fun createSurface(context: Context): SciChartSurface {
        val dateAxis = DateAxis(context).apply {
            axisTitle = context.getString(R.string.axis_time)
            autoRange = AutoRange.Never
            textFormatting = TIME_FORMAT
            subDayTextFormatting = TIME_FORMAT
        }
        val valueAxis = NumericAxis(context).apply {
            axisTitle = context.getString(R.string.axis_value)
            autoRange = AutoRange.Never
            growBy = DoubleRange(Y_GROW_BY, Y_GROW_BY)
            textFormatting = VALUE_FORMAT
        }
        val chartSurface = SciChartSurface(context)

        dateAxis.setVisibleRangeChangeListener { _, _, _, _ ->
            scheduleYAxisAutoRange()
        }

        UpdateSuspender.using(chartSurface) {
            chartSurface.xAxes.add(dateAxis)
            chartSurface.yAxes.add(valueAxis)
            chartSurface.chartModifiers.add(createZoomPanModifier())
            chartSurface.chartModifiers.add(createPinchZoomModifier())
        }

        surface = chartSurface
        xAxis = dateAxis
        return chartSurface
    }

    fun updateSources(sources: List<ChartSeriesUiModel>) {
        val chartSurface = surface ?: return
        val activeIds = sources.mapTo(mutableSetOf(), ChartSeriesUiModel::id)
        var visibleRangeChanged = false

        UpdateSuspender.using(chartSurface) {
            visibleRangeChanged = removeMissingSeries(activeIds, chartSurface)
            sources.forEach { source ->
                val bundle = seriesById[source.id] ?: createSeries(source, chartSurface)
                updateAnnotationAttachment(
                    bundle = bundle,
                    shouldBeAttached = source.status == ChartSourceUiStatus.ACTIVE,
                    chartSurface = chartSurface,
                )
                if (bundle.isVisible != source.isVisible) {
                    bundle.isVisible = source.isVisible
                    bundle.renderableSeries.isVisible = source.isVisible
                    visibleRangeChanged = visibleRangeChanged || bundle.hasPoint
                }
                val shouldShowLatestValue =
                    source.isVisible && source.status == ChartSourceUiStatus.ACTIVE
                if (bundle.shouldShowLatestValue != shouldShowLatestValue) {
                    bundle.shouldShowLatestValue = shouldShowLatestValue
                    updateLatestValueVisibility(bundle)
                }
            }
        }

        if (visibleRangeChanged) scheduleYAxisAutoRange()
    }

    /** Ставит пакет в очередь и объединяет близкие обновления в одну отрисовку кадра. */
    fun enqueuePoints(sourceId: String, points: List<ChartPointUiModel>) {
        if (points.isEmpty()) return
        pendingPointsById.getOrPut(sourceId, ::mutableListOf).addAll(points)
        schedulePointFlush()
    }

    fun dispose() {
        surface = null
        xAxis = null
        seriesById.clear()
        pendingPointsById.clear()
        initialXRangeApplied = false
        yAutoRangeScheduled = false
        pointFlushScheduled = false
    }

    private fun createSeries(
        source: ChartSeriesUiModel,
        chartSurface: SciChartSurface,
    ): SeriesBundle {
        val dataSeries = XyDataSeries(
            Date::class.java,
            Double::class.javaObjectType,
        ).apply {
            seriesName = source.name
        }
        val renderableSeries = FastLineRenderableSeries().apply {
            this.dataSeries = dataSeries
            strokeStyle = SolidPenStyle(
                source.lineColorArgb,
                true,
                SERIES_STROKE_THICKNESS,
                null,
            )
            isVisible = source.isVisible
        }
        val latestValueLine = HorizontalLineAnnotation(chartSurface.context).apply {
            stroke = SolidPenStyle(
                source.lineColorArgb,
                true,
                ANNOTATION_STROKE_THICKNESS,
                ANNOTATION_DASH_PATTERN,
            )
            horizontalGravity = Gravity.END
            setIsHidden(true)
        }
        val latestValueMarker = AxisMarkerAnnotation(chartSurface.context).apply {
            setBackgroundColor(source.lineColorArgb)
            fontStyle = FontStyle(MARKER_TEXT_SIZE_PX, Color.WHITE)
            markerPointWidth = MARKER_POINTER_WIDTH_PX
            setIsHidden(true)
        }
        val bundle = SeriesBundle(
            dataSeries = dataSeries,
            renderableSeries = renderableSeries,
            latestValueLine = latestValueLine,
            latestValueMarker = latestValueMarker,
            isVisible = source.isVisible,
            annotationsAttached = source.status == ChartSourceUiStatus.ACTIVE,
        )

        seriesById[source.id] = bundle
        chartSurface.renderableSeries.add(renderableSeries)
        if (bundle.annotationsAttached) {
            chartSurface.annotations.add(latestValueLine)
            chartSurface.annotations.add(latestValueMarker)
        }
        if (pendingPointsById[source.id].isNullOrEmpty().not()) schedulePointFlush()
        return bundle
    }

    private fun removeMissingSeries(
        activeIds: Set<String>,
        chartSurface: SciChartSurface,
    ): Boolean {
        var removedVisibleSeries = false
        val removedIds = seriesById.keys.filterNot(activeIds::contains)
        removedIds.forEach { sourceId ->
            val bundle = seriesById.remove(sourceId) ?: return@forEach
            removedVisibleSeries = removedVisibleSeries || bundle.isVisible && bundle.hasPoint
            chartSurface.renderableSeries.remove(bundle.renderableSeries)
            if (bundle.annotationsAttached) {
                chartSurface.annotations.remove(bundle.latestValueLine)
                chartSurface.annotations.remove(bundle.latestValueMarker)
            }
            pendingPointsById.remove(sourceId)
        }
        return removedVisibleSeries
    }

    private fun schedulePointFlush() {
        val chartSurface = surface ?: return
        if (pointFlushScheduled) return

        pointFlushScheduled = true
        chartSurface.postOnAnimation {
            pointFlushScheduled = false
            if (surface === chartSurface) flushPendingPoints(chartSurface)
        }
    }

    private fun flushPendingPoints(chartSurface: SciChartSurface) {
        val batches = pendingPointsById.toMap()
        pendingPointsById.clear()

        UpdateSuspender.using(chartSurface) {
            batches.forEach { (sourceId, points) ->
                appendBatch(sourceId, points)
            }
        }
        scheduleYAxisAutoRange()
    }

    private fun appendBatch(sourceId: String, points: List<ChartPointUiModel>) {
        val bundle = seriesById[sourceId]
        if (bundle == null) {
            pendingPointsById.getOrPut(sourceId, ::mutableListOf).addAll(points)
            return
        }
        val newPoints = points.filter { it.timestampMillis > bundle.lastTimestampMillis }
        if (newPoints.isEmpty()) return

        bundle.dataSeries.append(
            newPoints.map { point -> Date(point.timestampMillis) },
            newPoints.map(ChartPointUiModel::value),
        )
        val latestPoint = newPoints.last()
        bundle.lastTimestampMillis = latestPoint.timestampMillis
        bundle.hasPoint = true
        bundle.latestValueLine.x1 = Date(latestPoint.timestampMillis)
        bundle.latestValueLine.y1 = latestPoint.value
        bundle.latestValueMarker.y1 = latestPoint.value
        bundle.latestValueMarker.formattedValueProvider = TextFormattedValueProvider(
            formatLatestValue(latestPoint.value),
        )
        updateLatestValueVisibility(bundle)

        if (!initialXRangeApplied) {
            xAxis?.setVisibleRange(
                DateRange(
                    Date(latestPoint.timestampMillis),
                    Date(latestPoint.timestampMillis + INITIAL_VISIBLE_WINDOW_MILLIS),
                ),
            )
            initialXRangeApplied = true
        }
    }

    private fun scheduleYAxisAutoRange() {
        val chartSurface = surface ?: return
        if (yAutoRangeScheduled) return

        yAutoRangeScheduled = true
        chartSurface.postOnAnimation {
            if (surface === chartSurface) {
                chartSurface.zoomExtentsY()
            }
            yAutoRangeScheduled = false
        }
    }

    /**
     * Подключает аннотации только к активной серии.
     *
     * Физическое удаление завершённых аннотаций не оставляет на оси устаревший маркер, даже если
     * сторонний SDK отложил обработку изменения свойства видимости до следующего кадра.
     */
    private fun updateAnnotationAttachment(
        bundle: SeriesBundle,
        shouldBeAttached: Boolean,
        chartSurface: SciChartSurface,
    ) {
        if (bundle.annotationsAttached == shouldBeAttached) return

        if (shouldBeAttached) {
            chartSurface.annotations.add(bundle.latestValueLine)
            chartSurface.annotations.add(bundle.latestValueMarker)
        } else {
            bundle.latestValueLine.setIsHidden(true)
            bundle.latestValueMarker.setIsHidden(true)
            chartSurface.annotations.remove(bundle.latestValueLine)
            chartSurface.annotations.remove(bundle.latestValueMarker)
        }
        bundle.annotationsAttached = shouldBeAttached
    }

    private fun updateLatestValueVisibility(bundle: SeriesBundle) {
        val hidden = !bundle.shouldShowLatestValue || !bundle.hasPoint
        bundle.latestValueLine.setIsHidden(hidden)
        bundle.latestValueMarker.setIsHidden(hidden)
    }

    private fun createZoomPanModifier(): ZoomPanModifier = ZoomPanModifier().apply {
        direction = Direction2D.XDirection
        clipModeX = ClipMode.None
    }

    private fun createPinchZoomModifier(): PinchZoomModifier = PinchZoomModifier().apply {
        direction = Direction2D.XDirection
    }

    private data class SeriesBundle(
        val dataSeries: XyDataSeries<Date, Double>,
        val renderableSeries: FastLineRenderableSeries,
        val latestValueLine: HorizontalLineAnnotation,
        val latestValueMarker: AxisMarkerAnnotation,
        var isVisible: Boolean,
        var annotationsAttached: Boolean,
        var lastTimestampMillis: Long = Long.MIN_VALUE,
        var hasPoint: Boolean = false,
        var shouldShowLatestValue: Boolean = false,
    )

    private companion object {
        const val TIME_FORMAT = "HH:mm:ss"
        const val VALUE_FORMAT = "0.000"
        const val INITIAL_VISIBLE_WINDOW_MILLIS = 60_000L
        const val SERIES_STROKE_THICKNESS = 2f
        const val ANNOTATION_STROKE_THICKNESS = 1f
        const val Y_GROW_BY = 0.1
        const val MARKER_TEXT_SIZE_PX = 20f
        const val MARKER_POINTER_WIDTH_PX = 8

        val ANNOTATION_DASH_PATTERN = floatArrayOf(8f, 6f)
    }
}

/** Форматирует значение маркера независимо от локали устройства. */
internal fun formatLatestValue(value: Double): String =
    String.format(Locale.ROOT, "%.3f", value)
