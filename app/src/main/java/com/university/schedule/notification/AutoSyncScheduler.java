package com.university.schedule.notification;

import android.content.Context;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.university.schedule.util.PrefsManager;

import java.util.concurrent.TimeUnit;

/**
 * Планировщик периодической фоновой автосинхронизации расписания и переносов через WorkManager.
 */
public final class AutoSyncScheduler {

    private static final String TAG = "AutoSyncScheduler";

    public static final String WORK_NAME = "xrsatux_auto_sync";
    public static final long REPEAT_INTERVAL_HOURS = 12L;
    public static final long FLEX_INTERVAL_HOURS = 2L;
    public static final long INITIAL_BACKOFF_MINUTES = 15L;

    private AutoSyncScheduler() {
    }

    public static PeriodicWorkRequest buildPeriodicWorkRequest() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        return new PeriodicWorkRequest.Builder(
                AutoSyncWorker.class,
                REPEAT_INTERVAL_HOURS, TimeUnit.HOURS,
                FLEX_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        INITIAL_BACKOFF_MINUTES,
                        TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build();
    }

    /**
     * Синхронизирует состояние задачи WorkManager с настройкой пользователя в {@link PrefsManager}.
     * Вызывать из фонового потока (чтобы не дёргать SQLite WorkManager на UI-потоке).
     */
    public static void syncScheduleState(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        PrefsManager prefs = new PrefsManager(appCtx);
        if (prefs.isAutoSyncEnabled()) {
            schedule(appCtx);
        } else {
            cancel(appCtx);
        }
    }

    public static void schedule(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        try {
            WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    buildPeriodicWorkRequest()
            );
            Log.d(TAG, "Периодическая автосинхронизация запланирована (каждые "
                    + REPEAT_INTERVAL_HOURS + " ч)");
        } catch (Exception e) {
            Log.w(TAG, "Не удалось запланировать автосинхронизацию: " + e.getMessage());
        }
    }

    public static void cancel(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        try {
            WorkManager.getInstance(appCtx).cancelUniqueWork(WORK_NAME);
            Log.d(TAG, "Периодическая автосинхронизация отменена");
        } catch (Exception e) {
            Log.w(TAG, "Не удалось отменить автосинхронизацию: " + e.getMessage());
        }
    }
}
