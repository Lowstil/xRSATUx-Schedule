package com.university.schedule.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

/**
 * Точка входа для системы виджетов, чтобы получить фабрику строк списка
 * (см. ScheduleWidgetFactory). Отдельный класс требуется самим Android
 * API — RemoteViewsService должен быть отдельным <service> в манифесте.
 */
public class ScheduleWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new ScheduleWidgetFactory(getApplicationContext());
    }
}
