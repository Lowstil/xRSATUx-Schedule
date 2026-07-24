package com.university.schedule.app;

import android.app.Application;
import android.util.Log;

/**
 * Точка инициализации приложения. Репозиторий (БД/сеть) создаётся лениво —
 * при первом обращении из SplashActivity, поэтому здесь прямой зависимости от него нет.
 */
public class ScheduleApplication extends Application {
    private static final String TAG = "ScheduleApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "UniSchedule запущен");
    }
}