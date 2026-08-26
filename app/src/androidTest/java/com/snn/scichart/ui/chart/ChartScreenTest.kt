package com.snn.scichart.ui.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.snn.scichart.ui.theme.ScichartTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChartScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysSourceAndForwardsVisibilityEventWithoutLicense() {
        var visibilityEvent: Pair<String, Boolean>? = null

        composeRule.setContent {
            ScichartTheme(dynamicColor = false) {
                ChartScreen(
                    uiState = ChartUiState(
                        isLoading = false,
                        sources = listOf(source()),
                    ),
                    pointFlowProvider = { emptyFlow() },
                    onSourceVisibilityChanged = { sourceId, isVisible ->
                        visibilityEvent = sourceId to isVisible
                    },
                    isSciChartLicenseConfigured = false,
                )
            }
        }

        composeRule.onNodeWithText("Требуется пробный ключ SciChart").assertIsDisplayed()
        composeRule.onAllNodesWithText("Generator #1").onFirst().assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Показывать линию Generator #1")
            .assertIsOn()
            .performClick()

        composeRule.runOnIdle {
            assertEquals("generator-1" to false, visibilityEvent)
        }
    }

    @Test
    fun displaysUnexpectedRepositoryError() {
        composeRule.setContent {
            ScichartTheme(dynamicColor = false) {
                ChartScreen(
                    uiState = ChartUiState(
                        isLoading = false,
                        hasUnexpectedError = true,
                    ),
                    pointFlowProvider = { emptyFlow() },
                    onSourceVisibilityChanged = { _, _ -> },
                    isSciChartLicenseConfigured = false,
                )
            }
        }

        composeRule
            .onNodeWithText("Не удалось получить состояние источников")
            .assertIsDisplayed()
    }

    @Test
    fun displaysCompletedSourceState() {
        composeRule.setContent {
            ScichartTheme(dynamicColor = false) {
                ChartScreen(
                    uiState = ChartUiState(
                        isLoading = false,
                        sources = listOf(
                            source().copy(
                                status = ChartSourceUiStatus.COMPLETED,
                                remainingMillis = 0L,
                            ),
                        ),
                    ),
                    pointFlowProvider = { emptyFlow() },
                    onSourceVisibilityChanged = { _, _ -> },
                    isSciChartLicenseConfigured = false,
                )
            }
        }

        composeRule.onNodeWithText("Завершён", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("точек 8", substring = true).assertIsDisplayed()
    }

    @Test
    fun legendContainsOnlyVisibleSources() {
        composeRule.setContent {
            ScichartTheme(dynamicColor = false) {
                ChartScreen(
                    uiState = ChartUiState(
                        isLoading = false,
                        sources = listOf(
                            source(),
                            source().copy(
                                id = "generator-2",
                                name = "Generator #2",
                                isVisible = false,
                            ),
                        ),
                    ),
                    pointFlowProvider = { emptyFlow() },
                    onSourceVisibilityChanged = { _, _ -> },
                    isSciChartLicenseConfigured = false,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Легенда линии Generator #1")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Легенда линии Generator #2")
            .assertDoesNotExist()
        composeRule.onNodeWithText("Generator #2").assertIsDisplayed()
    }

    @Test
    fun usesSideBySidePanesWhenWidthExceedsHeight() {
        composeRule.setContent {
            ScichartTheme(dynamicColor = false) {
                Box(modifier = Modifier.size(width = 900.dp, height = 400.dp)) {
                    ChartScreen(
                        uiState = ChartUiState(isLoading = false, sources = listOf(source())),
                        pointFlowProvider = { emptyFlow() },
                        onSourceVisibilityChanged = { _, _ -> },
                        isSciChartLicenseConfigured = false,
                    )
                }
            }
        }

        val chartBounds = composeRule.onNodeWithTag(CHART_PANE_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot
        val sourceListBounds = composeRule.onNodeWithTag(SOURCE_LIST_PANE_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(sourceListBounds.left >= chartBounds.right)
        assertTrue(chartBounds.height > 0f)
    }

    private fun source(): ChartSourceUiModel = ChartSourceUiModel(
        id = "generator-1",
        name = "Generator #1",
        lineColorArgb = 0xFFE53935.toInt(),
        status = ChartSourceUiStatus.ACTIVE,
        remainingMillis = 42_000L,
        currentValue = 1.25,
        generatedPoints = 8,
        isVisible = true,
    )
}
