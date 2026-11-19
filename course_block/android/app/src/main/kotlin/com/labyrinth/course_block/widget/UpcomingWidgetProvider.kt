package com.labyrinth.course_block.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.labyrinth.course_block.MainActivity
import com.labyrinth.course_block.R

/**
 * 【近日课程】桌面小组件。
 *
 * 默认两格宽使用可滚动的单列分组列表；三格及以上切换为同样可滚动的 “今天 |明天”双列列表。两种布局共用同一套课程卡片字号与主题色。
 */
class UpcomingWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "UpcomingWidgetProvider"
        const val ACTION_REFRESH = "com.labyrinth.course_block.widget.UPCOMING_REFRESH"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids =
                manager.getAppWidgetIds(ComponentName(context, UpcomingWidgetProvider::class.java))
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
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        try {
            val prefs = context.getSharedPreferences("HomeWidgetPreferences", Context.MODE_PRIVATE)
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

    private fun isWideWidget(appWidgetManager: AppWidgetManager, widgetId: Int): Boolean {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val availableWidthsDp =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                val sizes =
                    options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                sizes.orEmpty().map { it.width }
            } else {
                emptyList()
            }
        return shouldUseWideUpcomingWidget(minWidthDp, availableWidthsDp)
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

        val serviceIntent =
            Intent(context, UpcomingWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
        views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
        views.setPendingIntentTemplate(
            R.id.widget_list_view,
            courseClickPendingIntent(context, widgetId),
        )
        views.setEmptyView(R.id.widget_list_view, R.id.tv_empty)
        views.setOnClickPendingIntent(R.id.tv_header, refreshPendingIntent(context, widgetId))
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
        views.setInt(R.id.divider_top, "setBackgroundColor", theme.divider)

        val serviceIntent =
            Intent(context, UpcomingWideWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
        views.setRemoteAdapter(R.id.widget_wide_list_view, serviceIntent)
        views.setPendingIntentTemplate(
            R.id.widget_wide_list_view,
            courseClickPendingIntent(context, widgetId),
        )
        views.setOnClickPendingIntent(R.id.tv_header, refreshPendingIntent(context, widgetId))
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent(context))

        appWidgetManager.updateAppWidget(widgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_wide_list_view)
    }

    private fun applyHeader(
        views: RemoteViews,
        prefs: SharedPreferences,
        theme: WidgetColors.WidgetTheme,
    ) {
        views.setInt(R.id.widget_root, "setBackgroundResource", theme.backgroundRes)
        views.setTextViewText(R.id.tv_header, prefs.getString("today_header", "近日课程") ?: "近日课程")
        views.setTextViewText(R.id.tv_subtitle, prefs.getString("today_subtitle", "") ?: "")
        views.setTextColor(R.id.tv_header, theme.headerText)
        views.setTextColor(R.id.tv_subtitle, theme.subtitleText)
    }

    private fun refreshPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent =
            Intent(context, UpcomingWidgetProvider::class.java).apply { action = ACTION_REFRESH }
        return PendingIntent.getBroadcast(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun courseClickPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
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
        val intent =
            Intent(context, MainActivity::class.java).apply {
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
