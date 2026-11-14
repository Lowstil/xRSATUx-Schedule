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
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.NextLesson;
import com.university.schedule.ui.activity.MainActivity;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Виджет «Расписание на сегодня» для главного экрана. Показывает список пар
 * на сегодня или «Выходной»/«Нет занятий». Если пар нет — показывает, когда
 * ближайшая пара по реальному расписанию (любой номер пары, любой день).
 * Тап по виджету открывает главный экран приложения. Обновляется сразу после
 * успешной загрузки расписания (сеть/автообновление), а не только по
 * системному таймеру раз в 30 минут.
 */
public class TodayScheduleWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "TodayScheduleWidget";
    private static final ExecutorService ex = Executors.newSingleThreadExecutor();

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

        views.setTextViewText(R.id.widgetDate,
                DateUtils.monthNameRu(DateUtils.todayMoscow()) + ", " + DateUtils.formatDisplayDateShort(DateUtils.todayMoscow()));

        if (selName == null || selType == null) {
            views.setTextViewText(R.id.widgetTitle, context.getString(R.string.app_name));
            views.setViewVisibility(R.id.widgetListView, View.GONE);
            views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE);
            views.setTextViewText(R.id.widgetEmptyState, "Выберите группу в приложении");
            hasLessonsOut[0] = false;
        } else {
            views.setTextViewText(R.id.widgetTitle, selName);
            boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
            DaySchedule today = isGroup
                    ? repo.getTodayScheduleForGroup(selName)
                    : repo.getTodayScheduleForTeacher(selName);
            if (today == null) {
                views.setViewVisibility(R.id.widgetListView, View.GONE);
                views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE);
                views.setTextViewText(R.id.widgetEmptyState, "Нет данных");
                hasLessonsOut[0] = false;
            } else if (today.isDayOff()) {
                views.setViewVisibility(R.id.widgetListView, View.GONE);
                views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE);
                String text = today.getHolidayName() != null ? today.getHolidayName() : "Выходной день 😴";
                text += nextLessonHint(repo, selName, isGroup);
                views.setTextViewText(R.id.widgetEmptyState, text);
                hasLessonsOut[0] = false;
            } else if (!today.hasLessons()) {
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
        return views;
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
