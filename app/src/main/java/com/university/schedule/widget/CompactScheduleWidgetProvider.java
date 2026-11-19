package com.university.schedule.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.NextLesson;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.ui.activity.MainActivity;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.ExcludedSubjectsManager;
import com.university.schedule.util.RoomFormatter;
import com.university.schedule.util.SkippedLessonsManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Компактный мини-виджет 2x1 на рабочий стол:
 * - Во время пары: название, аудитория, трёхсегментный прогресс-бар (45-5-45) и обратный отсчёт.
 * - На перемене / до пары: название следующей пары, аудитория и крупный таймер: "до звонка X мин".
 * - Вне занятий / на выходных: информация о ближайшей паре.
 *
 * Самоперезапускающийся AlarmManager-тикер ({@link #ACTION_COMPACT_WIDGET_TICK}) обновляет
 * виджет каждые 30 секунд во время активной пары (и за 15 минут до её начала) без открытия
 * приложения, а вне учебных часов засыпает до следующего учебного окна для экономии батареи.
 */
public class CompactScheduleWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "CompactScheduleWidget";
    public static final String ACTION_COMPACT_WIDGET_TICK = "com.university.schedule.action.COMPACT_WIDGET_TICK";
    private static final int RC_COMPACT_WIDGET_TICK = 3001;
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm");
    private static final ExecutorService ex = Executors.newSingleThreadExecutor();

    private static final class BuildResult {
        final RemoteViews views;
        final long nextTickDelayMs;

        BuildResult(RemoteViews views, long nextTickDelayMs) {
            this.views = views;
            this.nextTickDelayMs = nextTickDelayMs;
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        updateAllWidgets(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        cancelScheduledTick(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        if (!hasActiveWidgets(context)) {
            cancelScheduledTick(context);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_COMPACT_WIDGET_TICK.equals(intent.getAction())) {
            final PendingResult pendingResult = goAsync();
            final Context appCtx = context.getApplicationContext();
            ex.execute(() -> {
                try {
                    updateAllWidgetsSync(appCtx);
                } finally {
                    if (pendingResult != null) {
                        pendingResult.finish();
                    }
                }
            });
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) {
            if (!hasActiveWidgets(context)) {
                cancelScheduledTick(context);
            }
            return;
        }
        final PendingResult pendingResult = goAsync();
        final Context appCtx = context.getApplicationContext();
        ex.execute(() -> {
            try {
                BuildResult result = buildWidgetState(appCtx);
                for (int widgetId : appWidgetIds) {
                    try {
                        appWidgetManager.updateAppWidget(widgetId, result.views);
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка обновления компактного виджета", e);
                    }
                }
                scheduleNextTick(appCtx, result.nextTickDelayMs);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка сборки компактного виджета", e);
            } finally {
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        });
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        final Context appCtx = context.getApplicationContext();
        ex.execute(() -> {
            try {
                BuildResult result = buildWidgetState(appCtx);
                appWidgetManager.updateAppWidget(widgetId, result.views);
                scheduleNextTick(appCtx, result.nextTickDelayMs);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка обновления компактного виджета", e);
            }
        });
    }

    public static void updateAllWidgets(Context context) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        ex.execute(() -> updateAllWidgetsSync(appCtx));
    }

    private static void updateAllWidgetsSync(Context appCtx) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(appCtx);
            ComponentName provider = new ComponentName(appCtx, CompactScheduleWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(provider);
            if (ids == null || ids.length == 0) {
                cancelScheduledTick(appCtx);
                return;
            }
            BuildResult result = buildWidgetState(appCtx);
            for (int id : ids) {
                try {
                    manager.updateAppWidget(id, result.views);
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка обновления компактного виджета id=" + id, e);
                }
            }
            scheduleNextTick(appCtx, result.nextTickDelayMs);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка массового обновления компактных виджетов", e);
        }
    }

    private static boolean hasActiveWidgets(Context context) {
        if (context == null) return false;
        try {
            Context appCtx = context.getApplicationContext();
            AppWidgetManager manager = AppWidgetManager.getInstance(appCtx);
            int[] ids = manager.getAppWidgetIds(new ComponentName(appCtx, CompactScheduleWidgetProvider.class));
            return ids != null && ids.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void scheduleNextTick(Context context, long delayMs) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        if (!hasActiveWidgets(appCtx)) {
            cancelScheduledTick(appCtx);
            return;
        }
        AlarmManager am = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long safeDelay = Math.max(CompactWidgetTickCalculator.LIVE_TICK_INTERVAL_MS, delayMs);
        long triggerAt = System.currentTimeMillis() + safeDelay;
        PendingIntent pi = buildTickPendingIntent(appCtx);

        boolean canExact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canExact = am.canScheduleExactAlarms();
        }
        if (canExact) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                return;
            } catch (SecurityException e) {
                Log.w(TAG, "Точный будильник недоступен, используем setAndAllowWhileIdle", e);
            }
        }
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } catch (SecurityException e) {
            Log.e(TAG, "Не удалось запланировать тик компактного виджета", e);
        }
    }

    public static void cancelScheduledTick(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        AlarmManager am = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(buildTickPendingIntent(appCtx));
    }

    private static PendingIntent buildTickPendingIntent(Context appCtx) {
        Intent intent = new Intent(appCtx, CompactScheduleWidgetProvider.class);
        intent.setAction(ACTION_COMPACT_WIDGET_TICK);
        return PendingIntent.getBroadcast(
                appCtx,
                RC_COMPACT_WIDGET_TICK,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static BuildResult buildWidgetState(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_compact_schedule);
        ScheduleRepository repo = ScheduleRepository.getInstance(context);
        String selType = repo.getSelectionType();
        String selName = repo.getSelectionName();

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.compactWidgetRoot, pendingIntent);

        if (selName == null || selType == null) {
            views.setTextViewText(R.id.tvCompactStatus, "Настройка");
            views.setTextViewText(R.id.tvCompactSubject, "Выберите группу в приложении");
            views.setTextViewText(R.id.tvCompactRoom, "");
            views.setTextViewText(R.id.tvCompactTimeCountdown, "");
            views.setViewVisibility(R.id.compactProgressContainer, View.GONE);
            views.setViewVisibility(R.id.tvCompactSubInfo, View.GONE);
            return new BuildResult(views, CompactWidgetTickCalculator.IDLE_MAX_SLEEP_MS);
        }

        boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
        LocalDate today = DateUtils.todayMoscow();
        LocalTime now = DateUtils.nowTimeMoscow();
        int dow = DateUtils.toScheduleDayOfWeek(today);

        ExcludedSubjectsManager excludedMgr = new ExcludedSubjectsManager(context);
        SkippedLessonsManager skippedMgr = new SkippedLessonsManager(context);
        Set<String> excluded = isGroup ? excludedMgr.getExcludedSubjects(selName) : null;

        String[][] times = Constants.getLessonTimes(dow);
        DaySchedule daySchedule = isGroup ? repo.getTodayScheduleForGroup(selName) : repo.getTodayScheduleForTeacher(selName);
        List<ScheduleItem> lessons = (daySchedule != null && daySchedule.hasLessons()) ? daySchedule.getLessons() : null;

        ScheduleItem currentLesson = null;
        ScheduleItem nextLessonToday = null;
        int currentLessonNum = -1;
        int nextLessonNum = -1;
        LocalTime curStart = null, curEnd = null;
        LocalTime nxtStart = null;

        if (lessons != null && !lessons.isEmpty() && dow >= 1 && dow <= 6) {
            for (ScheduleItem it : lessons) {
                if (it.isCancelled()) continue;
                String sub = it.getSubjectName() != null ? it.getSubjectName().trim() : "";
                if (excluded != null && excluded.contains(sub)) continue;
                if (skippedMgr.isSkipped(today.toString(), it.getLessonNumber(), sub)) continue;

                int n = it.getLessonNumber();
                if (n >= 1 && n <= times.length) {
                    LocalTime start = LocalTime.parse(times[n - 1][0], TF);
                    LocalTime end = LocalTime.parse(times[n - 1][1], TF);
                    if (!now.isBefore(start) && !now.isAfter(end)) {
                        currentLesson = it;
                        currentLessonNum = n;
                        curStart = start;
                        curEnd = end;
                        break;
                    } else if (now.isBefore(start)) {
                        if (nextLessonToday == null) {
                            nextLessonToday = it;
                            nextLessonNum = n;
                            nxtStart = start;
                        }
                    }
                }
            }
        }

        long nextTickDelayMs = CompactWidgetTickCalculator.computeNextTickDelayMs(
                today, now, currentLesson != null, nxtStart);

        if (currentLesson != null && curStart != null && curEnd != null) {
            // --- СЕЙЧАС ИДЁТ ПАРА ---
            int totalMins = (int) ChronoUnit.MINUTES.between(curStart, curEnd);
            int elapsed = (int) ChronoUnit.MINUTES.between(curStart, now);
            if (elapsed < 0) elapsed = 0;
            if (elapsed > totalMins) elapsed = totalMins;

            views.setTextViewText(R.id.tvCompactStatus, currentLessonNum + " пара");
            views.setTextColor(R.id.tvCompactStatus, 0xFF81C995); // зелёный
            views.setTextViewText(R.id.tvCompactSubject, currentLesson.getSubjectName());

            String roomStr = formatRoom(currentLesson.getRoom());
            views.setTextViewText(R.id.tvCompactRoom, roomStr);

            views.setViewVisibility(R.id.compactProgressContainer, View.VISIBLE);
            views.setViewVisibility(R.id.tvCompactSubInfo, View.GONE);

            // 45 мин (1-я пол) -> 5 мин (перерыв) -> 45 мин (2-я пол)
            if (elapsed < 45) {
                int leftToBreak = 45 - elapsed;
                views.setTextViewText(R.id.tvCompactTimeCountdown, "1-я пол. (до перерыва " + leftToBreak + " мин)");
                views.setProgressBar(R.id.compactProgressPart1, 45, elapsed, false);
                views.setProgressBar(R.id.compactProgressBreak, 5, 0, false);
                views.setProgressBar(R.id.compactProgressPart2, 45, 0, false);
            } else if (elapsed < 50) {
                int leftBreak = 50 - elapsed;
                views.setTextViewText(R.id.tvCompactStatus, "Перерыв");
                views.setTextColor(R.id.tvCompactStatus, 0xFFF3A96B); // оранжевый
                views.setTextViewText(R.id.tvCompactTimeCountdown, "Перерыв (до звонка " + leftBreak + " мин)");
                views.setProgressBar(R.id.compactProgressPart1, 45, 45, false);
                views.setProgressBar(R.id.compactProgressBreak, 5, elapsed - 45, false);
                views.setProgressBar(R.id.compactProgressPart2, 45, 0, false);
            } else {
                int leftToEnd = Math.max(0, totalMins - elapsed);
                int part2Max = Math.max(1, totalMins - 50);
                views.setTextViewText(R.id.tvCompactTimeCountdown, "2-я пол. (до конца " + leftToEnd + " мин)");
                views.setProgressBar(R.id.compactProgressPart1, 45, 45, false);
                views.setProgressBar(R.id.compactProgressBreak, 5, 5, false);
                views.setProgressBar(R.id.compactProgressPart2, part2Max, Math.min(part2Max, elapsed - 50), false);
            }
            return new BuildResult(views, nextTickDelayMs);
        }

        if (nextLessonToday != null && nxtStart != null) {
            // --- ПЕРЕМЕНА ИЛИ СКОРО ПАРА СЕГОДНЯ ---
            int minsUntil = (int) ChronoUnit.MINUTES.between(now, nxtStart);
            if (minsUntil < 0) minsUntil = 0;

            views.setTextViewText(R.id.tvCompactStatus, (minsUntil <= 50) ? "Перемена" : "Скоро пара");
            views.setTextColor(R.id.tvCompactStatus, 0xFF8AB4F8); // синий
            views.setTextViewText(R.id.tvCompactSubject, nextLessonToday.getSubjectName());
            views.setTextViewText(R.id.tvCompactRoom, formatRoom(nextLessonToday.getRoom()));
            views.setTextViewText(R.id.tvCompactTimeCountdown, "до звонка " + minsUntil + " мин");

            views.setViewVisibility(R.id.compactProgressContainer, View.GONE);
            views.setViewVisibility(R.id.tvCompactSubInfo, View.VISIBLE);
            views.setTextViewText(R.id.tvCompactSubInfo, nextLessonNum + " пара · звонок в " + nxtStart.format(TF));
            return new BuildResult(views, nextTickDelayMs);
        }

        // --- НА СЕГОДНЯ ПАР БОЛЬШЕ НЕТ / ВЫХОДНОЙ ---
        NextLesson futureNext = repo.findNextLesson(selName, isGroup, today, now, 8);
        views.setViewVisibility(R.id.compactProgressContainer, View.GONE);

        if (futureNext != null) {
            boolean isTomorrow = futureNext.getDate().equals(today.plusDays(1));
            String dayLabel = isTomorrow ? "Завтра" : (futureNext.getDayOfWeek() >= 1 && futureNext.getDayOfWeek() <= 6
                    ? Constants.DAY_NAMES_SHORT[futureNext.getDayOfWeek() - 1] : "");

            views.setTextViewText(R.id.tvCompactStatus, (daySchedule != null && daySchedule.isDayOff()) ? "Выходной" : "Пар нет 🎉");
            views.setTextColor(R.id.tvCompactStatus, 0xFF81C995);
            views.setTextViewText(R.id.tvCompactSubject, futureNext.getSubjectName());
            views.setTextViewText(R.id.tvCompactRoom, formatRoom(futureNext.getRoom()));
            views.setTextViewText(R.id.tvCompactTimeCountdown, dayLabel + " в " + futureNext.getTimeStart());

            views.setViewVisibility(R.id.tvCompactSubInfo, View.VISIBLE);
            views.setTextViewText(R.id.tvCompactSubInfo, futureNext.getLessonNumber() + " пара · " + DateUtils.formatDisplayDateShort(futureNext.getDate()));
        } else {
            views.setTextViewText(R.id.tvCompactStatus, "Отдых");
            views.setTextColor(R.id.tvCompactStatus, 0xFF81C995);
            views.setTextViewText(R.id.tvCompactSubject, "Занятий нет 🎉");
            views.setTextViewText(R.id.tvCompactRoom, "");
            views.setTextViewText(R.id.tvCompactTimeCountdown, "");
            views.setViewVisibility(R.id.tvCompactSubInfo, View.GONE);
        }

        return new BuildResult(views, nextTickDelayMs);
    }

    private static String formatRoom(String rawRoom) {
        RoomFormatter.RoomInfo ri = RoomFormatter.parse(rawRoom, true);
        if (ri == null) return rawRoom != null ? rawRoom : "";
        if (ri.room != null && !ri.room.isEmpty()) {
            return ri.building + ", " + ri.room;
        }
        return ri.building != null ? ri.building : "";
    }
}
