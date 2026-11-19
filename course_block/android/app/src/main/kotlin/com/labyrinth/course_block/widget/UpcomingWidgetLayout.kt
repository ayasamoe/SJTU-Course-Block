package com.labyrinth.course_block.widget

/**
 * In portrait orientation, the three-cell widget width starts around180dp.
 * Keep the default two-cell widget on the scrollable single-column layout.
 */
internal const val WIDE_UPCOMING_WIDGET_MIN_WIDTH_DP = 180

internal fun shouldUseWideUpcomingWidget(minWidthDp: Int): Boolean =
    minWidthDp >= WIDE_UPCOMING_WIDGET_MIN_WIDTH_DP
