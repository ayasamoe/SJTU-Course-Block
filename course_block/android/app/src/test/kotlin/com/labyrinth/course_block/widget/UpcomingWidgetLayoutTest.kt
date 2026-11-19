package com.labyrinth.course_block.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingWidgetLayoutTest {

    @Test
    fun defaultTwoCellWidthKeepsSingleColumn() {
        assertFalse(
            shouldUseWideUpcomingWidget(
                minWidthDp = 180,
                availableWidthsDp = listOf(110f, 180f, 250f, 320f),
            )
        )
    }

    @Test
    fun threeCellWidthUsesDoubleColumn() {
        assertTrue(
            shouldUseWideUpcomingWidget(
                minWidthDp = 250,
                availableWidthsDp = listOf(110f, 180f, 250f, 320f),
            )
        )
    }

    @Test
    fun defaultTwoCellWidthStaysSingleColumnWithNewHostSizes() {
        assertFalse(
            shouldUseWideUpcomingWidget(
                minWidthDp = 220,
                availableWidthsDp = listOf(150f, 220f, 290f, 360f),
            )
        )
    }

    @Test
    fun threeCellWidthUsesDoubleColumnWithNewHostSizes() {
        assertTrue(
            shouldUseWideUpcomingWidget(
                minWidthDp = 290,
                availableWidthsDp = listOf(150f, 220f, 290f, 360f),
            )
        )
    }

    @Test
    fun missingWidthFallsBackToSingleColumn() {
        assertFalse(shouldUseWideUpcomingWidget(0))
    }

    @Test
    fun legacyHostUsesConservativeWidthFallback() {
        assertFalse(shouldUseWideUpcomingWidget(220))
        assertTrue(shouldUseWideUpcomingWidget(240))
    }
}
