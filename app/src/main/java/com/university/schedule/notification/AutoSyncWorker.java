package com.university.schedule.notification;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.util.AppError;
import com.university.schedule.util.PrefsManager;

/**
 * Фоновый WorkManager-воркер для периодической проверки обновлений расписания и переносов
 * с rsatu.ru и отправки push-уведомления при обнаружении реальных изменений для группы/преподавателя.
 */
public class AutoSyncWorker extends Worker {

    private static final String TAG = "AutoSyncWorker";

    public AutoSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context appCtx = getApplicationContext();
        PrefsManager prefs = new PrefsManager(appCtx);

        if (!prefs.isAutoSyncEnabled()) {
            Log.d(TAG, "Автосинхронизация отключена пользователем — пропуск");
            return Result.success();
        }

        String selName = prefs.getSelectionName();
        if (!prefs.hasSelection() || selName == null || selName.trim().isEmpty()) {
            Log.d(TAG, "Группа/преподаватель ещё не выбраны — пропуск фоновой проверки");
            return Result.success();
        }

        try {
            ScheduleRepository repo = ScheduleRepository.getInstance(appCtx);
            ScheduleRepository.BackgroundSyncResult syncResult = repo.syncInBackgroundSync();

            if (!syncResult.fromAssetsFallback && syncResult.hasUserVisibleChanges()) {
                ScheduleChangeFormatter.FormattedNotification notification =
                        ScheduleChangeFormatter.format(
                                selName,
                                syncResult.scheduleDiff,
                                syncResult.newTransfers
                        );
                if (notification != null) {
                    NotificationHelper.sendScheduleChangesNotification(
                            appCtx,
                            notification.title,
                            notification.summary,
                            notification.bigText
                    );
                    Log.d(TAG, "Отправлено уведомление об изменениях (" + notification.changeCount + ")");
                }
            } else {
                Log.d(TAG, "Фоновая проверка завершена: значимых изменений для " + selName + " нет");
            }
            return Result.success();
        } catch (Exception e) {
            AppError error = AppError.from(e);
            int attempt = getRunAttemptCount();
            if (ScheduleChangeFormatter.shouldRetry(error, attempt)) {
                Log.w(TAG, "Транзиентная сетевая ошибка при автосинхронизации (попытка "
                        + (attempt + 1) + "): " + error.category + " — " + error.technicalDetail);
                return Result.retry();
            }
            Log.e(TAG, "Ошибка автосинхронизации без повтора (попытка "
                    + (attempt + 1) + ", категория " + error.category + "): " + error.technicalDetail, e);
            return Result.success();
        }
    }
}
