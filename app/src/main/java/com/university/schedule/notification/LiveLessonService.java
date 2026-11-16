package com.university.schedule.notification;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.ui.activity.MainActivity;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.ExcludedSubjectsManager;
import com.university.schedule.util.PrefsManager;
import com.university.schedule.util.ScheduleClock;
import com.university.schedule.util.SkippedLessonsManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Управляет живым постоянным уведомлением 45-5-45 в шторке (Feature #4).
 * Вызывается через {@link #syncNow(Context)} и автоматически обновляется раз в минуту
 * через {@link TickReceiver}, пока сегодня есть текущая или предстоящая пара.
 */
public final class LiveLessonService {

    private static final String TAG = "LiveLessonService";
    public static final String CHANNEL_LIVE_LESSON = "channel_live_lesson";
    private static final int ID_LIVE_LESSON = 1004;
    private static final int RC_TICK = 2004;
    private static final long TICK_INTERVAL_MS = 60_000L;
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private LiveLessonService() {
    }

    public static class TickReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            PendingResult pr = goAsync();
            EXECUTOR.execute(() -> {
                try {
                    syncInternal(context.getApplicationContext());
                } finally {
                    pr.finish();
                }
            });
        }
    }

    public static void syncNow(Context context) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        EXECUTOR.execute(() -> syncInternal(appCtx));
    }

    private static void syncInternal(Context context) {
        try {
            PrefsManager prefs = new PrefsManager(context);
            if (!prefs.isLiveLessonNotificationEnabled() || !prefs.hasSelection()) {
                cancelNotification(context);
                cancelNextTick(context);
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                cancelNotification(context);
                cancelNextTick(context);
                return;
            }

            ScheduleRepository repo = ScheduleRepository.getInstance(context);
            String selType = repo.getSelectionType();
            String selName = repo.getSelectionName();
            if (selName == null || selName.isEmpty()) {
                cancelNotification(context);
                cancelNextTick(context);
                return;
            }

            boolean isGroup = "group".equals(selType);
            DaySchedule todaySchedule = isGroup
                    ? repo.getTodayScheduleForGroup(selName)
                    : repo.getTodayScheduleForTeacher(selName);

            if (todaySchedule == null || todaySchedule.isDayOff() || !todaySchedule.hasLessons()) {
                cancelNotification(context);
                cancelNextTick(context);
                return;
            }

            LocalDate today = DateUtils.todayMoscow();
            LocalTime now = DateUtils.nowTimeMoscow();
            int dow = DateUtils.toScheduleDayOfWeek(today);
            if (dow < 1 || dow > 6) {
                cancelNotification(context);
                cancelNextTick(context);
                return;
            }

            ExcludedSubjectsManager excludedMgr = new ExcludedSubjectsManager(context);
            SkippedLessonsManager skippedMgr = new SkippedLessonsManager(context);

            List<ScheduleItem> activeList = new ArrayList<>();
            for (ScheduleItem item : todaySchedule.getLessons()) {
                if (item.isCancelled()) continue;
                if (isGroup && excludedMgr.isExcluded(selName, item.getSubjectName())) continue;
                if (skippedMgr.isSkipped(today.toString(), item.getLessonNumber(), item.getSubjectName())) continue;
                activeList.add(item);
            }

            if (activeList.isEmpty()) {
                cancelNotification(context);
                cancelNextTick(context);
                return;
            }

            Collections.sort(activeList, (a, b) -> Integer.compare(a.getLessonNumber(), b.getLessonNumber()));
            String[][] times = Constants.getLessonTimes(dow);

            ScheduleItem currentItem = null;
            LocalTime currentStart = null;
            LocalTime currentEnd = null;
            ScheduleItem nextItem = null;
            LocalTime nextStart = null;

            for (ScheduleItem item : activeList) {
                int n = item.getLessonNumber();
                if (n < 1 || n > times.length) continue;
                LocalTime start = LocalTime.parse(times[n - 1][0], TF);
                LocalTime end = LocalTime.parse(times[n - 1][1], TF);

                if (!now.isBefore(start) && !now.isAfter(end)) {
                    if (currentItem == null) {
                        currentItem = item;
                        currentStart = start;
                        currentEnd = end;
                    }
                } else if (now.isBefore(start)) {
                    if (nextItem == null) {
                        nextItem = item;
                        nextStart = start;
                    }
                }
            }

            if (currentItem == null) {
                cancelNotification(context);
                if (nextItem != null && nextStart != null) {
                    long triggerAt = today.atTime(nextStart)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();
                    if (triggerAt > System.currentTimeMillis()) {
                        scheduleTickAt(context, triggerAt);
                    } else {
                        cancelNextTick(context);
                    }
                } else {
                    cancelNextTick(context);
                }
                return;
            }

            ensureChannel(context);

            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentPi = PendingIntent.getActivity(
                    context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_LIVE_LESSON)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setColor(ContextCompat.getColor(context, R.color.glass_accent))
                    .setContentIntent(contentPi)
                    .setPriority(NotificationCompat.PRIORITY_LOW);

            ScheduleClock.LessonProgress lp = ScheduleClock.computeLessonProgress(currentStart, currentEnd, now);
            String room = currentItem.getRoom();
            String title = currentItem.getLessonNumber() + " пара · " + safe(currentItem.getSubjectName());
            if (room != null && !room.isEmpty()) {
                title += " · ауд. " + room;
            }

            String stageText;
            if (lp != null) {
                if (lp.stage == 1) {
                    int toBreak = Math.max(0, lp.part1Max - lp.part1Progress);
                    stageText = "1-я половина 45м · до перерыва " + toBreak + " мин (ост. " + lp.remainingMinutes + " мин)";
                } else if (lp.stage == 2) {
                    int toPart2 = Math.max(0, lp.breakMax - lp.breakProgress);
                    stageText = "Перерыв 5м · до 2-й половины " + toPart2 + " мин";
                } else {
                    stageText = "2-я половина 45м · осталось " + lp.remainingMinutes + " мин";
                }
                builder.setProgress(lp.totalMinutes, lp.elapsedMinutes, false);
            } else {
                stageText = "Идёт занятие";
            }

            builder.setContentTitle(title);
            builder.setContentText(stageText);

            if (nextItem != null) {
                String nextLine = "Далее: " + nextItem.getLessonNumber() + " пара · " + safe(nextItem.getSubjectName());
                if (nextItem.getRoom() != null && !nextItem.getRoom().isEmpty()) {
                    nextLine += " (ауд. " + nextItem.getRoom() + ")";
                }
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(stageText + "\n" + nextLine));
            }

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(ID_LIVE_LESSON, builder.build());
            }
            scheduleNextTick(context);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка обновления live-уведомления", e);
        }
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_LIVE_LESSON,
                    "Живой таймер пары (45-5-45)",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("Показывает текущий этап пары 45-5-45, аудиторию и следующую пару");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    private static void cancelNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(ID_LIVE_LESSON);
        }
    }

    private static void scheduleNextTick(Context context) {
        scheduleTickAt(context, System.currentTimeMillis() + TICK_INTERVAL_MS);
    }

    private static void scheduleTickAt(Context context, long triggerAt) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(
                context, RC_TICK,
                new Intent(context, TickReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    private static void cancelNextTick(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(
                context, RC_TICK,
                new Intent(context, TickReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.cancel(pi);
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
