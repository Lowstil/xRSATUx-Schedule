package com.university.schedule.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.university.schedule.widget.WidgetUpdateHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            final PendingResult pendingResult = goAsync();
            final Context appCtx = context.getApplicationContext();
            EXECUTOR.execute(() -> {
                try {
                    ReminderScheduler.rescheduleAll(appCtx);
                    WidgetUpdateHelper.updateAllWidgets(appCtx);
                    LiveLessonService.syncNow(appCtx);
                } finally {
                    if (pendingResult != null) {
                        pendingResult.finish();
                    }
                }
            });
        }
    }
}
