package com.labyrinth.course_block.widget

/**
 * Keep the default two-cell widget on the scrollable single-column layout. Launchers expose
 * different absolute widths for the same cell span, so the provider compares the current width with
 * the boundary between two and three cells from the widget host's available sizes.
 */
internal const val WIDE_UPCOMING_WIDGET_FALLBACK_WIDTH_DP = 240
private const val DEFAULT_TARGET_COLUMN_COUNT = 2

internal fun shouldUseWideUpcomingWidget(
    minWidthDp: Int,
    availableWidthsDp: List<Float> = emptyList(),
): Boolean {
    val widths = availableWidthsDp.filter { it > 0f }.distinctBy { it.toInt() }.sorted()
    val twoColumnWidthDp = widths.getOrNull(DEFAULT_TARGET_COLUMN_COUNT - 1)
    val threeColumnWidthDp = widths.getOrNull(DEFAULT_TARGET_COLUMN_COUNT)

    if (twoColumnWidthDp != null && threeColumnWidthDp != null) {
        val wideBreakpointDp = (twoColumnWidthDp + threeColumnWidthDp) / 2f
        return minWidthDp >= wideBreakpointDp
    }

    return minWidthDp >= WIDE_UPCOMING_WIDGET_FALLBACK_WIDTH_DP
}
