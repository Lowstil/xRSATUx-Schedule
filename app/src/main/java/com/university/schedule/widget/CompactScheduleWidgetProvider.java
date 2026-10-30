package com.university.schedule.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import com.university.schedule.util.ScheduleClock;
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
 */
public class CompactScheduleWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "CompactScheduleWidget";
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm");
    private static final ExecutorService ex = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        ex.execute(() -> {
            try {
                RemoteViews views = buildRemoteViews(context);
                appWidgetManager.updateAppWidget(widgetId, views);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка обновления компактного виджета", e);
            }
        });
    }

    public static void updateAllWidgets(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());
            ComponentName provider = new ComponentName(context, CompactScheduleWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(provider);
            for (int id : ids) {
                updateWidget(context, manager, id);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка массового обновления компактных виджетов", e);
        }
    }

    private static RemoteViews buildRemoteViews(Context context) {
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
            return views;
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
                int leftToEnd = 95 - elapsed;
                if (leftToEnd < 0) leftToEnd = 0;
                views.setTextViewText(R.id.tvCompactTimeCountdown, "2-я пол. (до конца " + leftToEnd + " мин)");
                views.setProgressBar(R.id.compactProgressPart1, 45, 45, false);
                views.setProgressBar(R.id.compactProgressBreak, 5, 5, false);
                views.setProgressBar(R.id.compactProgressPart2, 45, elapsed - 50, false);
            }
            return views;
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
            return views;
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

        return views;
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
