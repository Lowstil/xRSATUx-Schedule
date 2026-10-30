package com.university.schedule.widget;

import android.content.Context;

/**
 * Утилитный класс для одновременного обновления всех виджетов приложения
 * (основного списка расписания на день и компактного 2x1 виджета).
 */
public class WidgetUpdateHelper {
    public static void updateAllWidgets(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        TodayScheduleWidgetProvider.updateAllWidgets(appCtx);
        CompactScheduleWidgetProvider.updateAllWidgets(appCtx);
    }
}
