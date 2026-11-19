package com.labyrinth.course_block.widget

import android.content.Intent
import android.widget.RemoteViewsService

/** 为宽尺寸的“今天 |明天”双列小组件提供可滚动数据。 */
class UpcomingWideWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        UpcomingWideWidgetFactory(applicationContext)
}
