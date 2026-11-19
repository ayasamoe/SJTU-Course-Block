package com.labyrinth.course_block.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.labyrinth.course_block.R
import org.json.JSONArray

/** 双列布局使用一个 ListView：首行是两侧日期标题，之后每行是一对课程。 两列共享同一个滚动位置，和单列布局一样只依赖 RemoteViews支持的标准控件。 */
internal class UpcomingWideWidgetFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private data class Course(
        val name: String,
        val room: String,
        val timeRange: String,
        val color: String,
    )

    private sealed class Row {
        data class Header(val todayLabel: String, val tomorrowLabel: String) : Row()

        data class Pair(
            val left: Course?,
            val right: Course?,
            val showLeftPlaceholder: Boolean,
            val showRightPlaceholder: Boolean,
        ) : Row()
    }

    private var rows: List<Row> = emptyList()
    private lateinit var theme: WidgetColors.WidgetTheme

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        return when (val row = rows.getOrNull(position)) {
            is Row.Header -> headerView(row)
            is Row.Pair -> pairView(row)
            null -> headerView(Row.Header("今天", "明天"))
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun loadData() {
        val prefs = context.getSharedPreferences("HomeWidgetPreferences", Context.MODE_PRIVATE)
        theme = WidgetColors.resolve(context, prefs)

        val groups = linkedMapOf<String, MutableList<Course>>()
        var currentLabel: String? = null
        try {
            val items = JSONArray(prefs.getString("upcoming_list", "[]") ?: "[]")
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                when (item.optString("t")) {
                    "header" -> {
                        currentLabel = item.optString("label", "")
                        groups.getOrPut(currentLabel.orEmpty()) { mutableListOf() }
                    }

                    "course" ->
                        currentLabel?.let { label ->
                            groups
                                .getOrPut(label) { mutableListOf() }
                                .add(
                                    Course(
                                        name = item.optString("name", "--"),
                                        room = item.optString("room", ""),
                                        timeRange = item.optString("timeRange", ""),
                                        color = item.optString("color", ""),
                                    )
                                )
                        }
                }
            }
        } catch (exception: Exception) {
            Log.w("UpcomingWideFactory", "Failed to parse upcoming_list", exception)
            groups.clear()
        }

        val todayEntry = groups.entries.firstOrNull { it.key.startsWith("今天") }
        val tomorrowEntry = groups.entries.firstOrNull { it.key.startsWith("明天") }
        val todayLabel =
            prefs.getString("upcoming_today_label", "").orEmpty().ifBlank {
                todayEntry?.key?.ifBlank { "今天" } ?: "今天"
            }
        val tomorrowLabel =
            prefs.getString("upcoming_tomorrow_label", "").orEmpty().ifBlank {
                tomorrowEntry?.key?.ifBlank { "明天" } ?: "明天"
            }
        val todayCourses = todayEntry?.value.orEmpty()
        val tomorrowCourses = tomorrowEntry?.value.orEmpty()
        val pairCount = maxOf(todayCourses.size, tomorrowCourses.size).coerceAtLeast(1)

        rows = buildList {
            add(Row.Header(todayLabel, tomorrowLabel))
            repeat(pairCount) { index ->
                val left = todayCourses.getOrNull(index)
                val right = tomorrowCourses.getOrNull(index)
                add(
                    Row.Pair(
                        left = left,
                        right = right,
                        showLeftPlaceholder = index == 0 && todayCourses.isEmpty(),
                        showRightPlaceholder = index == 0 && tomorrowCourses.isEmpty(),
                    )
                )
            }
        }
    }

    private fun headerView(row: Row.Header): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_upcoming_wide_header_row)
        views.setTextViewText(R.id.wide_header_today, row.todayLabel)
        views.setTextViewText(R.id.wide_header_tomorrow, row.tomorrowLabel)
        views.setTextColor(R.id.wide_header_today, theme.accent)
        views.setTextColor(R.id.wide_header_tomorrow, theme.accent)
        views.setInt(R.id.wide_header_divider, "setBackgroundColor", theme.divider)
        return views
    }

    private fun pairView(row: Row.Pair): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_upcoming_wide_pair_row)
        views.setInt(R.id.wide_pair_divider, "setBackgroundColor", theme.divider)
        bindCourse(
            views = views,
            course = row.left,
            showPlaceholder = row.showLeftPlaceholder,
            cardId = R.id.wide_left_card,
            barId = R.id.wide_left_bar,
            titleId = R.id.wide_left_title,
            detailId = R.id.wide_left_detail,
            timeId = R.id.wide_left_time,
        )
        bindCourse(
            views = views,
            course = row.right,
            showPlaceholder = row.showRightPlaceholder,
            cardId = R.id.wide_right_card,
            barId = R.id.wide_right_bar,
            titleId = R.id.wide_right_title,
            detailId = R.id.wide_right_detail,
            timeId = R.id.wide_right_time,
        )
        return views
    }

    private fun bindCourse(
        views: RemoteViews,
        course: Course?,
        showPlaceholder: Boolean,
        cardId: Int,
        barId: Int,
        titleId: Int,
        detailId: Int,
        timeId: Int,
    ) {
        if (course == null && !showPlaceholder) {
            views.setViewVisibility(cardId, View.INVISIBLE)
            return
        }

        views.setViewVisibility(cardId, View.VISIBLE)
        if (course == null) {
            views.setTextViewText(titleId, "暂无课程")
            views.setTextColor(titleId, theme.emptyText)
            views.setTextViewText(detailId, "")
            views.setTextViewText(timeId, "")
            views.setViewVisibility(detailId, View.GONE)
            views.setViewVisibility(timeId, View.GONE)
            views.setInt(barId, "setBackgroundColor", WidgetColors.withAlpha(theme.emptyText, 180))
            views.setContentDescription(cardId, "暂无课程")
            views.setOnClickFillInIntent(cardId, Intent())
            return
        }

        val courseColor = WidgetColors.forCourse(course.name, theme, course.color)
        views.setTextViewText(titleId, course.name)
        views.setTextViewText(detailId, if (course.room.isNotEmpty()) "地点：${course.room}" else "")
        views.setTextViewText(timeId, course.timeRange)
        views.setTextColor(titleId, theme.courseTitle)
        views.setTextColor(detailId, theme.courseDetail)
        views.setTextColor(timeId, courseColor)
        views.setInt(barId, "setBackgroundColor", courseColor)
        views.setViewVisibility(detailId, if (course.room.isEmpty()) View.GONE else View.VISIBLE)
        views.setViewVisibility(timeId, if (course.timeRange.isEmpty()) View.GONE else View.VISIBLE)
        views.setContentDescription(cardId, "${course.name}，点击打开课程表")
        views.setOnClickFillInIntent(cardId, Intent().putExtra("course_name", course.name))
    }
}
