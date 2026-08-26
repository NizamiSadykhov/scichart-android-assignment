package com.snn.scichart.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Измеряет пользовательские сценарии из пункта 7 на release-подобной сборке.
 *
 * Тест следует запускать на физическом устройстве без зарядки и фоновой нагрузки. Результат содержит
 * frame timing и системные трассы, пригодные для сравнения до и после изменений реализации.
 */
@RunWith(AndroidJUnit4::class)
class ChartPerformanceBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** Измеряет холодный запуск до полностью показанного основного экрана. */
    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    /** Измеряет прокрутку списка и переключение серий при продолжающейся отрисовке графика. */
    @Test
    fun scrollAndToggleSources() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            checkNotNull(
                device.wait(
                    Until.findObject(By.res(SOURCE_LIST_TAG)),
                    UI_TIMEOUT_MILLIS,
                ),
            ) { "Список источников не появился за отведённое время" }
            // Ускоренный только для benchmark интервал создаёт около 2 000 точек суммарно.
            Thread.sleep(LOAD_WARMUP_MILLIS)
        },
    ) {
        val list = device.findObject(By.res(SOURCE_LIST_TAG))
        val listBounds = list.visibleBounds
        repeat(SCROLL_REPETITIONS) {
            device.swipe(
                listBounds.centerX(),
                listBounds.bottom - 1,
                listBounds.centerX(),
                listBounds.top + 1,
                SWIPE_STEPS,
            )
            device.swipe(
                listBounds.centerX(),
                listBounds.top + 1,
                listBounds.centerX(),
                listBounds.bottom - 1,
                SWIPE_STEPS,
            )
        }

        repeat(TOGGLED_SOURCE_COUNT) { index ->
            device.findObject(By.res("$SOURCE_VISIBILITY_TAG_PREFIX${index + 1}"))
                ?.visibleCenter
                ?.let { center ->
                    device.click(center.x, center.y)
                }
            }
    }

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private companion object {
        const val TARGET_PACKAGE = "com.snn.scichart"
        const val SOURCE_LIST_TAG = "source-list-pane"
        const val ITERATIONS = 5
        const val SCROLL_REPETITIONS = 3
        const val TOGGLED_SOURCE_COUNT = 3
        const val SWIPE_STEPS = 12
        const val UI_TIMEOUT_MILLIS = 10_000L
        const val LOAD_WARMUP_MILLIS = 10_000L
        const val SOURCE_VISIBILITY_TAG_PREFIX = "source-visibility-generator-"
    }
}
