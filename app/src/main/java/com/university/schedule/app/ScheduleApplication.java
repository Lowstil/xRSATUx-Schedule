package com.university.schedule.app;

import android.app.Application;
import android.util.Log;

import com.university.schedule.util.ThemeManager;

/**
 * Точка инициализации приложения. Репозиторий (БД/сеть) создаётся лениво —
 * при первом обращении из SplashActivity, поэтому здесь прямой зависимости от него нет.
 *
 * Здесь же применяется сохранённая тема (системная/светлая/тёмная) — это нужно
 * сделать до создания первой Activity, иначе она на долю секунды отрисуется
 * с темой по умолчанию и "моргнёт" при применении сохранённого выбора.
 */
public class ScheduleApplication extends Application {
    private static final String TAG = "ScheduleApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySavedMode(this);
        Log.d(TAG, "UniSchedule запущен");
    }
}
