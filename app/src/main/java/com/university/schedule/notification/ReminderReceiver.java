package com.university.schedule.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String subject = intent.getStringExtra("subject");
        String type = intent.getStringExtra("type");
        String room = intent.getStringExtra("room");
        String teacher = intent.getStringExtra("teacher");
        int lessonNum = intent.getIntExtra("lesson_number", 1);
        int minutesBefore = intent.getIntExtra("minutes_before", 15);

        if (subject != null && !subject.isEmpty()) {
            NotificationHelper.sendLessonReminder(
                    context, subject, type, room, teacher, lessonNum, minutesBefore
            );
        }

        // Планируем напоминание о следующей паре дня
        ReminderScheduler.scheduleNextReminder(context);
    }
}
