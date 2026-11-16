package com.university.schedule.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.NextLesson;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.ui.activity.MainActivity;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.ExcludedSubjectsManager;
import com.university.schedule.util.SkippedLessonsManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Виджет «Расписание на сегодня» для главного экрана. Показывает список пар
 * на сегодня или «Выходной»/«Нет занятий». После окончания последней активной
 * пары (или в выходной/воскресенье) автоматически переключается на превью
 * расписания на завтра («Завтра · <День>»). Если пар нет — показывает, когда
 * ближайшая пара по реальному расписанию (любой номер пары, любой день).
 * Тап по виджету открывает главный экран приложения, тап по «↻» обновляет виджеты.
 */
public class TodayScheduleWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "TodayScheduleWidget";
    public static final String ACTION_MANUAL_REFRESH = "com.university.schedule.widget.ACTION_MANUAL_REFRESH";
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");
    private static final ExecutorService ex = Executors.newSingleThreadExecutor();

    static final class WidgetScheduleResult {
        final LocalDate date;
        final boolean isPreview;
        final DaySchedule daySchedule;

        WidgetScheduleResult(LocalDate date, boolean isPreview, DaySchedule daySchedule) {
            this.date = date;
            this.isPreview = isPreview;
            this.daySchedule = daySchedule;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_MANUAL_REFRESH.equals(intent.getAction())) {
            final PendingResult pendingResult = goAsync();
            final Context appCtx = context.getApplicationContext();
            ex.execute(() -> {
                try {
                    AppWidgetManager manager = AppWidgetManager.getInstance(appCtx);
                    int[] ids = manager.getAppWidgetIds(new ComponentName(appCtx, TodayScheduleWidgetProvider.class));
                    if (ids != null) {
                        for (int id : ids) {
                            updateWidgetSync(appCtx, manager, id);
                        }
                    }
                    CompactScheduleWidgetProvider.updateAllWidgets(appCtx);
                } finally {
                    if (pendingResult != null) {
                        pendingResult.finish();
                    }
                }
            });
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) return;
        final PendingResult pendingResult = goAsync();
        final Context appCtx = context.getApplicationContext();
        ex.execute(() -> {
            try {
                for (int widgetId : appWidgetIds) {
                    updateWidgetSync(appCtx, appWidgetManager, widgetId);
                }
            } finally {
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        });
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        final Context appCtx = context.getApplicationContext();
        ex.execute(() -> updateWidgetSync(appCtx, appWidgetManager, widgetId));
    }

    public static void updateAllWidgets(Context context) {
        try {
            Context appCtx = context.getApplicationContext();
            AppWidgetManager manager = AppWidgetManager.getInstance(appCtx);
            ComponentName provider = new ComponentName(appCtx, TodayScheduleWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(provider);
            if (ids == null || ids.length == 0) return;
            for (int id : ids) {
                updateWidget(appCtx, manager, id);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка массового обновления виджета", e);
        }
    }

    private static void updateWidgetSync(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        try {
            boolean[] hasLessonsOut = new boolean[1];
            RemoteViews views = buildRemoteViews(context, widgetId, hasLessonsOut);
            appWidgetManager.updateAppWidget(widgetId, views);
            if (hasLessonsOut[0]) {
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widgetListView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка обновления виджета " + widgetId, e);
        }
    }

    private static RemoteViews buildRemoteViews(Context context, int widgetId, boolean[] hasLessonsOut) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_schedule);
        ScheduleRepository repo = ScheduleRepository.getInstance(context);
        String selType = repo.getSelectionType();
        String selName = repo.getSelectionName();

        LocalDate todayDate = DateUtils.todayMoscow();
        String dateBadge = DateUtils.monthNameRu(todayDate) + ", " + DateUtils.formatDisplayDateShort(todayDate);

        if (selName == null || selType == null) {
            views.setTextViewText(R.id.widgetDate, dateBadge);
            views.setTextViewText(R.id.widgetTitle, context.getString(R.string.app_name));
            views.setViewVisibility(R.id.widgetListView, View.GONE);
            views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE);
            views.setTextViewText(R.id.widgetEmptyState, "Выберите группу в приложении");
            hasLessonsOut[0] = false;
        } else {
            views.setTextViewText(R.id.widgetTitle, selName);
            boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
            WidgetScheduleResult res = resolveWidgetSchedule(context, repo, selName, isGroup);
            DaySchedule displayDay = res.daySchedule;

            if (res.isPreview) {
                int dow = DateUtils.toScheduleDayOfWeek(res.date);
                String dowShort = (dow >= 1 && dow <= 6) ? Constants.DAY_NAMES_SHORT[dow - 1] : "";
                long daysDiff = ChronoUnit.DAYS.between(todayDate, res.date);
                if (daysDiff == 1) {
                    dateBadge = dowShort.isEmpty() ? "Завтра" : ("Завтра · " + dowShort);
                } else {
                    dateBadge = dowShort.isEmpty()
                            ? DateUtils.formatDisplayDateShort(res.date)
                            : (dowShort + ", " + DateUtils.formatDisplayDateShort(res.date));
                }
            }
            views.setTextViewText(R.id.widgetDate, dateBadge);

            if (displayDay == null) {
                views.setViewVisibility(R.id.widgetListView, View.GONE);
                views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE);
                views.setTextViewText(R.id.widgetEmptyState, "Нет данных");
                hasLessonsOut[0] = false;
            } else if (displayDay.isDayOff()) {
                views.setViewVisibility(R.id.widgetListView, View.GONE);
                views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE);
                String text = displayDay.getHolidayName() != null ? displayDay.getHolidayName() : "Выходной день 😴";
                text += nextLessonHint(repo, selName, isGroup);
                views.setTextViewText(R.id.widgetEmptyState, text);
                hasLessonsOut[0] = false;
            } else if (!displayDay.hasLessons()) {
                views.setViewVisibility(R.id.widgetListView, View.GONE);
                views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE);
                views.setTextViewText(R.id.widgetEmptyState, "Нет занятий 🎉" + nextLessonHint(repo, selName, isGroup));
                hasLessonsOut[0] = false;
            } else {
                views.setViewVisibility(R.id.widgetEmptyState, View.GONE);
                views.setViewVisibility(R.id.widgetListView, View.VISIBLE);
                views.setTextViewText(R.id.widgetEmptyState, "Загрузка расписания…");

                Intent serviceIntent = new Intent(context, ScheduleWidgetService.class);
                serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
                views.setRemoteAdapter(R.id.widgetListView, serviceIntent);
                views.setEmptyView(R.id.widgetListView, R.id.widgetEmptyState);
                hasLessonsOut[0] = true;
            }
        }

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 1, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent);
        views.setPendingIntentTemplate(R.id.widgetListView, pendingIntent);

        Intent refreshIntent = new Intent(context, TodayScheduleWidgetProvider.class);
        refreshIntent.setAction(ACTION_MANUAL_REFRESH);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context, 2, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRefreshButton, refreshPendingIntent);
        return views;
    }

    /**
     * Определяет, какое расписание показывать в виджете:
     * сегодня (если ещё есть хотя бы одна активная неотменённая пара до конца дня)
     * или превью на завтра (пропуская воскресенье, если завтра — ВС).
     */
    static WidgetScheduleResult resolveWidgetSchedule(ScheduleRepository repo, String selName, boolean isGroup) {
        return resolveWidgetSchedule(null, repo, selName, isGroup);
    }

    static WidgetScheduleResult resolveWidgetSchedule(Context context, ScheduleRepository repo, String selName, boolean isGroup) {
        LocalDate today = DateUtils.todayMoscow();
        LocalTime now = DateUtils.nowTimeMoscow();
        DaySchedule todaySchedule = isGroup
                ? repo.getTodayScheduleForGroup(selName)
                : repo.getTodayScheduleForTeacher(selName);

        ExcludedSubjectsManager excludedMgr = context != null ? new ExcludedSubjectsManager(context) : null;
        SkippedLessonsManager skippedMgr = context != null ? new SkippedLessonsManager(context) : null;
        Set<String> excluded = (excludedMgr != null && isGroup) ? excludedMgr.getExcludedSubjects(selName) : null;

        boolean hasRemainingToday = false;
        if (todaySchedule != null && !todaySchedule.isDayOff() && todaySchedule.hasLessons() && todaySchedule.getLessons() != null) {
            int dowToday = DateUtils.toScheduleDayOfWeek(today);
            if (dowToday >= 1 && dowToday <= 6) {
                String[][] times = Constants.getLessonTimes(dowToday);
                for (ScheduleItem it : todaySchedule.getLessons()) {
                    if (it.isCancelled()) continue;
                    String sub = it.getSubjectName() != null ? it.getSubjectName().trim() : "";
                    if (excluded != null && excluded.contains(sub)) continue;
                    if (skippedMgr != null && skippedMgr.isSkipped(today, it.getLessonNumber(), sub)) continue;
                    int n = it.getLessonNumber();
                    if (n >= 1 && n <= times.length) {
                        LocalTime end = LocalTime.parse(times[n - 1][1], TF);
                        if (now.isBefore(end)) {
                            hasRemainingToday = true;
                            break;
                        }
                    }
                }
            }
        }

        if (hasRemainingToday) {
            return new WidgetScheduleResult(today, false, todaySchedule);
        }

        LocalDate candidate = today.plusDays(1);
        if (candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.plusDays(1);
        }
        DaySchedule candidateSchedule = loadDaySchedule(repo, selName, isGroup, candidate);
        if (candidateSchedule != null && !candidateSchedule.isDayOff() && candidateSchedule.hasLessons()) {
            return new WidgetScheduleResult(candidate, true, candidateSchedule);
        }

        return new WidgetScheduleResult(today, false, todaySchedule);
    }

    private static DaySchedule loadDaySchedule(ScheduleRepository repo, String selName, boolean isGroup, LocalDate date) {
        WeekCalculator wc = repo.getWeekCalculator();
        if (wc == null) return null;
        int week = wc.getWeekNumber(date);
        if (week < 1) return null;
        WeekSchedule ws = isGroup
                ? repo.getWeekScheduleForGroup(selName, week)
                : repo.getWeekScheduleForTeacher(selName, week);
        if (ws == null || ws.getDays() == null) return null;
        int dow = DateUtils.toScheduleDayOfWeek(date);
        for (DaySchedule d : ws.getDays()) {
            if (date.equals(d.getDate()) || d.getDayOfWeek() == dow) {
                return d;
            }
        }
        return null;
    }

    /**
     * Подсказка «Следующая: ...» для пустого дня. Ищет ближайшую реальную пару
     * по расписанию группы/преподавателя (любой номер пары, любой день).
     */
    private static String nextLessonHint(ScheduleRepository repo, String selName, boolean isGroup) {
        try {
            NextLesson next = repo.findNextLesson(selName, isGroup,
                    DateUtils.todayMoscow(), DateUtils.nowTimeMoscow(), 8);
            if (next != null) {
                String dowName = (next.getDayOfWeek() >= 1 && next.getDayOfWeek() <= 6)
                        ? Constants.DAY_NAMES_SHORT[next.getDayOfWeek() - 1] : "";
                return "\nСледующая: " + dowName + ", "
                        + DateUtils.formatDisplayDateShort(next.getDate()) + ", "
                        + next.getLessonNumber() + " пара (" + next.getTimeStart() + ")";
            }
        } catch (Exception e) {
            Log.w(TAG, "Не удалось найти следующую пару для виджета", e);
        }
        return "";
    }
}
