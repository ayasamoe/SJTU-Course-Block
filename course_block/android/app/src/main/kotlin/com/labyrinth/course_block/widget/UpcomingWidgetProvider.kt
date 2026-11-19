package com.labyrinth.course_block.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.labyrinth.course_block.MainActivity
import com.labyrinth.course_block.R
import org.json.JSONArray

/**
 * 【近日课程】桌面小组件。
 *
 *默认宽度（不超过两格）沿用可滚动的分组列表；拉到三格及以上时，
 *切换为原 v1.1.1的“今天 |明天”双列紧凑布局。
 */
class UpcomingWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "UpcomingWidgetProvider"
        const val ACTION_REFRESH = "com.labyrinth.course_block.widget.UPCOMING_REFRESH"
    }

    private data class CourseRow(
        val name: String,
        val room: String,
        val timeRange: String,
        val color: String,
    )

    private data class Group(
        val label: String,
        val courses: MutableList<CourseRow> = mutableListOf(),
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, UpcomingWidgetProvider::class.java),
            )
            onUpdate(context, manager, ids)
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetDataRefresher.refresh(context)
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetDataRefresher.refresh(context)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
    ) {
        try {
            val prefs = context.getSharedPreferences(
                "HomeWidgetPreferences",
                Context.MODE_PRIVATE,
            )
            val theme = WidgetColors.resolve(context, prefs)
            if (isWideWidget(appWidgetManager, widgetId)) {
                updateWideWidget(context, appWidgetManager, widgetId, prefs, theme)
            } else {
                updateNarrowWidget(context, appWidgetManager, widgetId, prefs, theme)
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to update widget $widgetId", exception)
        }
    }

    private fun isWideWidget(
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
    ): Boolean {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)

        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        return shouldUseWideUpcomingWidget(minWidth)
    }

    private fun updateNarrowWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        prefs: SharedPreferences,
        theme: WidgetColors.WidgetTheme,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_upcoming)
        applyHeader(views, prefs, theme)
        views.setTextColor(R.id.tv_empty, theme.emptyText)
        views.setInt(R.id.divider_top, "setBackgroundColor", theme.divider)

        val serviceIntent = Intent(context, UpcomingWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
        views.setPendingIntentTemplate(
            R.id.widget_list_view,
            courseClickPendingIntent(context, widgetId),
        )
        views.setEmptyView(R.id.widget_list_view, R.id.tv_empty)
        views.setOnClickPendingIntent(
            R.id.tv_header,
            refreshPendingIntent(context, widgetId),
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent(context))

        appWidgetManager.updateAppWidget(widgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list_view)
    }

    private fun updateWideWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        prefs: SharedPreferences,
        theme: WidgetColors.WidgetTheme,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_upcoming_wide)
        applyHeader(views, prefs, theme)

        views.setTextViewText(R.id.lbl_today, "今天")
        views.setTextViewText(R.id.lbl_tmr, "明天")
        views.setTextColor(R.id.lbl_today, theme.headerText)
        views.setTextColor(R.id.lbl_tmr, theme.subtitleText)
        views.setTextColor(R.id.btn_refresh, theme.accent)
        views.setTextColor(R.id.btn_open, theme.openText)
        views.setInt(R.id.divider_top, "setBackgroundColor", theme.divider)
        views.setInt(R.id.divider_center, "setBackgroundColor", theme.divider)
        views.setInt(R.id.divider_bottom, "setBackgroundColor", theme.divider)

        views.removeAllViews(R.id.col_today_content)
        views.removeAllViews(R.id.col_tmr_content)

        val groups = parseUpcomingGroups(prefs)
        val todayGroup = groups.firstOrNull { it.label.startsWith("今天") }
        val tomorrowGroup = groups.firstOrNull { it.label.startsWith("明天") }
        addCourseCards(
            context,
            views,
            theme,
            R.id.col_today_content,
            todayGroup?.courses.orEmpty(),
        )
        addCourseCards(
            context,
            views,
            theme,
            R.id.col_tmr_content,
            tomorrowGroup?.courses.orEmpty(),
        )

        views.setOnClickPendingIntent(
            R.id.btn_refresh,
            refreshPendingIntent(context, widgetId),
        )
        val openIntent = openPendingIntent(context)
        views.setOnClickPendingIntent(R.id.btn_open, openIntent)
        views.setOnClickPendingIntent(R.id.widget_root, openIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun applyHeader(
        views: RemoteViews,
        prefs: SharedPreferences,
        theme: WidgetColors.WidgetTheme,
    ) {
        views.setInt(R.id.widget_root, "setBackgroundResource", theme.backgroundRes)
        views.setTextViewText(
            R.id.tv_header,
            prefs.getString("today_header", "近日课程") ?: "近日课程",
        )
        views.setTextViewText(
            R.id.tv_subtitle,
            prefs.getString("today_subtitle", "") ?: "",
        )
        views.setTextColor(R.id.tv_header, theme.headerText)
        views.setTextColor(R.id.tv_subtitle, theme.subtitleText)
    }

    private fun parseUpcomingGroups(prefs: SharedPreferences): List<Group> {
        val json = prefs.getString("upcoming_list", "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            val groups = mutableListOf<Group>()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                when (item.optString("t")) {
                    "header" -> groups.add(Group(item.optString("label", "")))
                    "course" -> groups.lastOrNull()?.courses?.add(
                        CourseRow(
                            name = item.optString("name", "--"),
                            room = item.optString("room", ""),
                            timeRange = item.optString("timeRange", ""),
                            color = item.optString("color", ""),
                        ),
                    )
                }
            }
            groups
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to parse upcoming_list", exception)
            emptyList()
        }
    }

    private fun addCourseCards(
        context: Context,
        views: RemoteViews,
        theme: WidgetColors.WidgetTheme,
        columnId: Int,
        courses: List<CourseRow>,
    ) {
        if (courses.isEmpty()) {
            val card = RemoteViews(context.packageName, R.layout.widget_mini_card)
            card.setTextViewText(R.id.mini_name, "暂无课程")
            card.setTextViewText(R.id.mini_info, "")
            card.setTextColor(R.id.mini_name, theme.emptyText)
            card.setTextColor(R.id.mini_info, theme.courseDetail)
            card.setInt(
                R.id.mini_bar,
                "setBackgroundColor",
                WidgetColors.withAlpha(theme.emptyText, 180),
            )
            views.addView(columnId, card)
            return
        }

        for (course in courses) {
            val card = RemoteViews(context.packageName, R.layout.widget_mini_card)
            val color = WidgetColors.forCourse(course.name, theme, course.color)
            val details = listOf(course.room, course.timeRange)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            card.setTextViewText(R.id.mini_name, course.name)
            card.setTextViewText(R.id.mini_info, details)
            card.setTextColor(R.id.mini_name, theme.courseTitle)
            card.setTextColor(R.id.mini_info, color)
            card.setInt(R.id.mini_bar, "setBackgroundColor", color)
            views.addView(columnId, card)
        }
    }

    private fun refreshPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, UpcomingWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun courseClickPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            widgetId + 10_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
