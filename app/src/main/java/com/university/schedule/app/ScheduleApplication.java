package com.university.schedule.app;

import android.app.Application;
import android.os.StrictMode;
import android.util.Log;

import com.university.schedule.BuildConfig;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.util.ThemeManager;

import java.util.concurrent.Executors;

/**
 * Точка инициализации приложения.
 *
 * ВАЖНО про источник подвисаний: ScheduleRepository — синглтон, и раньше
 * комментарий здесь гласил "создаётся лениво при первом обращении из
 * SplashActivity" — это предположение оказалось НЕВЕРНЫМ на практике.
 * Android может убить процесс в фоне и затем восстановить его сразу с той
 * Activity, что была на экране (MainActivity, SelectionActivity,
 * SettingsActivity) — минуя SplashActivity полностью. В этом случае именно
 * та Activity становится ПЕРВЫМ вызовом ScheduleRepository.getInstance(),
 * а конструктор репозитория открывает SQLite. Фикс: прогреваем синглтон
 * здесь, в фоновом потоке, сразу при старте процесса.
 *
 * StrictMode (только в debug-сборке, никогда не включается в релизе) ловит
 * ЛЮБОЕ обращение к диску/сети на главном потоке и сразу пишет в Logcat
 * точный стек вызова с меткой "StrictMode policy violation" — это надёжнее,
 * чем находить оставшиеся источники подвисаний чтением кода вручную: если
 * после этого патча зависания продолжатся, лог StrictMode покажет ТОЧНОЕ
 * место (класс, метод, строку), а не предположение.
 */
public class ScheduleApplication extends Application {
    private static final String TAG = "ScheduleApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            enableStrictMode();
        }
        ThemeManager.applySavedMode(this);
        Executors.newSingleThreadExecutor().execute(() -> {
            ScheduleRepository.getInstance(this);
            Log.d(TAG, "ScheduleRepository прогрет в фоне");
        });
        Log.d(TAG, "UniSchedule запущен");
    }

    private void enableStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build());
        Log.d(TAG, "StrictMode включён (debug) — обращения к диску/сети на UI-потоке будут видны в Logcat");
    }
}
