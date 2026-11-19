package com.labyrinth.course_block.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingWidgetLayoutTest {

    @Test
    fun defaultTwoCellWidthKeepsSingleColumn() {
        assertFalse(shouldUseWideUpcomingWidget(110))
    }

    @Test
    fun threeCellWidthUsesDoubleColumn() {
        assertTrue(shouldUseWideUpcomingWidget(180))
    }

    @Test
    fun missingWidthFallsBackToSingleColumn() {
        assertFalse(shouldUseWideUpcomingWidget(0))
    }
}
