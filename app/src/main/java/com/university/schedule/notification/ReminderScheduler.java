package com.university.schedule.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.NextLesson;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.PrefsManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ReminderScheduler {
    private static final String TAG = "ReminderScheduler";
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");
    private static final int RC_REMINDER = 2001;
    private static final int RC_DIGEST = 2002;

    private ReminderScheduler() {
    }

    public static void rescheduleAll(Context context) {
        scheduleNextReminder(context);
        scheduleMorningDigest(context);
    }

    public static void scheduleNextReminder(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, RC_REMINDER, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PrefsManager prefs = new PrefsManager(context);
        if (!prefs.isRemindersEnabled() || !prefs.hasSelection()) {
            am.cancel(pi);
            return;
        }

        int minutesBefore = prefs.getReminderMinutesBefore();
        ScheduleRepository repo = ScheduleRepository.getInstance(context);
        boolean isGroup = "group".equals(repo.getSelectionType());
        String selName = repo.getSelectionName();

        LocalDate today = DateUtils.todayMoscow();
        LocalTime now = DateUtils.nowTimeMoscow();

        NextLesson next = repo.findNextLesson(selName, isGroup, today, now, 8);
        if (next == null || next.getDate() == null || next.getTimeStart() == null) {
            am.cancel(pi);
            return;
        }

        try {
            LocalTime start = LocalTime.parse(next.getTimeStart(), TF);
            LocalTime reminderTime = start.minusMinutes(minutesBefore);
            LocalDateTime triggerLdt = LocalDateTime.of(next.getDate(), reminderTime);
            long triggerMillis = triggerLdt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            if (triggerMillis <= System.currentTimeMillis()) {
                // Время напоминания уже прошло (например, пара уже через 5 мин, а настройка 15 мин),
                // но пара еще не началась — ставим напоминание на сейчас (через 1 сек)
                LocalDateTime lessonStartLdt = LocalDateTime.of(next.getDate(), start);
                long startMillis = lessonStartLdt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (startMillis > System.currentTimeMillis()) {
                    triggerMillis = System.currentTimeMillis() + 1000L;
                } else {
                    am.cancel(pi);
                    return;
                }
            }

            intent.putExtra("subject", next.getSubjectName());
            intent.putExtra("type", next.getLessonType());
            intent.putExtra("room", next.getRoom());
            intent.putExtra("teacher", next.getTeacherName());
            intent.putExtra("lesson_number", next.getLessonNumber());
            intent.putExtra("minutes_before", minutesBefore);

            PendingIntent exactPi = PendingIntent.getBroadcast(
                    context, RC_REMINDER, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, exactPi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, exactPi);
                }
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, exactPi);
            }
            Log.d(TAG, "Напоминание о паре запланировано на " + triggerLdt);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка планирования напоминания о паре", e);
        }
    }

    public static void scheduleMorningDigest(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, MorningDigestReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, RC_DIGEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PrefsManager prefs = new PrefsManager(context);
        if (!prefs.isMorningDigestEnabled() || !prefs.hasSelection()) {
            am.cancel(pi);
            return;
        }

        LocalDateTime next730 = LocalDate.now().atTime(7, 30);
        if (!LocalDateTime.now().isBefore(next730)) {
            next730 = next730.plusDays(1);
        }
        long triggerMillis = next730.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerMillis, pi);
        }
        Log.d(TAG, "Утренний дайджест запланирован на " + next730);
    }
}
