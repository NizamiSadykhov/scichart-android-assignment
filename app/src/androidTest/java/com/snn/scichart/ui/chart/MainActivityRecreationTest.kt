package com.snn.scichart.ui.chart

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.snn.scichart.BuildConfig
import com.snn.scichart.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityRecreationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun preservesDataAndUserVisibilityAfterActivityRecreation() {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            firstVisiblePointCount() >= MINIMUM_POINTS_BEFORE_RECREATION
        }
        val pointCountBeforeRecreation = firstVisiblePointCount()

        composeRule
            .onAllNodes(hasContentDescription("Показывать линию", substring = true))
            .onFirst()
            .assertIsOn()
            .performClick()
            .assertIsOff()

        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            firstVisiblePointCount() >= pointCountBeforeRecreation
        }
        composeRule.onNodeWithText("График случайного блуждания").assertIsDisplayed()
        composeRule.onNodeWithText("Источники данных").assertIsDisplayed()
        composeRule
            .onAllNodes(hasContentDescription("Показывать линию", substring = true))
            .onFirst()
            .assertIsOff()

        if (BuildConfig.SCICHART_LICENSE_KEY.isBlank()) {
            composeRule.onNodeWithText("Требуется пробный ключ SciChart").assertIsDisplayed()
        } else {
            composeRule
                .onNodeWithContentDescription("График значений десяти источников во времени")
                .assertIsDisplayed()
        }

        assertTrue(firstVisiblePointCount() >= pointCountBeforeRecreation)
    }

    private fun firstVisiblePointCount(): Int {
        val metricsNode = composeRule
            .onAllNodes(hasText("точек", substring = true))
            .fetchSemanticsNodes()
            .firstOrNull()
            ?: return -1
        val metrics = metricsNode.config[SemanticsProperties.Text]
            .joinToString(separator = "") { text -> text.text }

        return POINT_COUNT_REGEX.find(metrics)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: -1
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS = 10_000L
        const val MINIMUM_POINTS_BEFORE_RECREATION = 2
        val POINT_COUNT_REGEX = Regex("точек (\\d+)")
    }
}
