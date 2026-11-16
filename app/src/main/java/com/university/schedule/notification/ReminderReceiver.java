package com.university.schedule.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.university.schedule.util.PrefsManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String key = intent.getStringExtra("lesson_key");
        String subject = intent.getStringExtra("subject");
        String type = intent.getStringExtra("type");
        String room = intent.getStringExtra("room");
        String teacher = intent.getStringExtra("teacher");
        int lessonNum = intent.getIntExtra("lesson_number", 1);
        int minutesBefore = intent.getIntExtra("minutes_before", 15);

        final PendingResult pendingResult = goAsync();
        final Context appCtx = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                if (key != null && !key.isEmpty()) {
                    new PrefsManager(appCtx).setLastNotifiedLessonKey(key);
                }

                if (subject != null && !subject.isEmpty()) {
                    NotificationHelper.sendLessonReminder(
                            appCtx, subject, type, room, teacher, lessonNum, minutesBefore
                    );
                }

                // Планируем напоминание о следующей паре
                ReminderScheduler.scheduleNextReminder(appCtx);
            } finally {
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        });
    }
}
