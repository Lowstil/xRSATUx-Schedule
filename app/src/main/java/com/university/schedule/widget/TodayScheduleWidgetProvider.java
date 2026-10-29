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
import com.university.schedule.util.DateUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
Виджет «Расписание на сегодня» для главного экрана. Показывает список пар
на сегодня или «Выходной»/«Нет занятий». Если пар нет — показывает, когда
ближайшая пара по реальному расписанию (любой номер пары, любой день).
Тап по виджету открывает главный экран приложения. Обновляется сразу после
успешной загрузки расписания (сеть/автообновление), а не только по
системному таймеру раз в 30 минут.
*/
public class TodayScheduleWidgetProvider extends AppWidgetProvider {
private static final String TAG = "TodayScheduleWidget";
private static final ExecutorService ex = Executors.newSingleThreadExecutor();
@Override
public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
for (int widgetId : appWidgetIds) {
updateWidget(context, appWidgetManager, widgetId);
}
}
public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
ex.execute(() -> {
RemoteViews views = buildRemoteViews(context);
try {
appWidgetManager.updateAppWidget(widgetId, views);
appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widgetListView);
} catch (Exception e) {
Log.e(TAG, "Ошибка обновления виджета", e);
}
});
}
public static void updateAllWidgets(Context context) {
AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());
ComponentName provider = new ComponentName(context, TodayScheduleWidgetProvider.class);
int[] ids = manager.getAppWidgetIds(provider);
for (int id : ids) {
updateWidget(context, manager, id);
}
}
private static RemoteViews buildRemoteViews(Context context) {
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
} else {
views.setTextViewText(R.id.widgetTitle, selName);
boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
DaySchedule today = isGroup ? repo.getTodayScheduleForGroup(selName) : repo.getTodayScheduleForTeacher(selName);
if (today == null) {
views.setViewVisibility(R.id.widgetListView, View.GONE);
views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE);
views.setTextViewText(R.id.widgetEmptyState, "Нет данных");
} else if (today.isDayOff()) {
views.setViewVisibility(R.id.widgetListView, View.GONE);
views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE);
String text = today.getHolidayName() != null ? today.getHolidayName() : "Выходной день 😴";
text += nextLessonHint(repo, selName, isGroup);
views.setTextViewText(R.id.widgetEmptyState, text);
} else if (!today.hasLessons()) {
views.setViewVisibility(R.id.widgetListView, View.GONE);
views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE);
views.setTextViewText(R.id.widgetEmptyState, "Нет занятий 🎉" + nextLessonHint(repo, selName, isGroup));
} else {
views.setViewVisibility(R.id.widgetEmptyState, View.GONE);
views.setViewVisibility(R.id.widgetListView, View.VISIBLE);
Intent serviceIntent = new Intent(context, ScheduleWidgetService.class);
serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
views.setRemoteAdapter(R.id.widgetListView, serviceIntent);
views.setEmptyView(R.id.widgetListView, R.id.widgetEmptyState);
}
}
Intent openApp = new Intent(context, MainActivity.class);
openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
PendingIntent pendingIntent = PendingIntent.getActivity(
context, 0, openApp,
PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent);
return views;
}
/**
Подсказка «Следующая: ...» для пустого дня. Ищет ближайшую реальную пару
по расписанию группы/преподавателя (любой номер пары, любой день).
ВАЖНО: перевод строки внутри строкового литерала обязан быть записан как
escape-последовательность \n — живой перенос строки в Java-литерале
недопустим и ломает компиляцию ("unclosed string literal").
Время берём через DateUtils.nowTimeMoscow() — тот же источник правды,
что и в MainActivity/ScheduleClock, чтобы виджет и приложение одинаково
понимали «сейчас» независимо от часового пояса устройства.
*/
private static String nextLessonHint(ScheduleRepository repo, String selName, boolean isGroup) {
try {
NextLesson next = repo.findNextLesson(selName, isGroup,
DateUtils.todayMoscow(), DateUtils.nowTimeMoscow(), 8);
if (next != null) {
String dowName = (next.getDayOfWeek() >= 1 && next.getDayOfWeek() <= 6)
? com.university.schedule.util.Constants.DAY_NAMES_SHORT[next.getDayOfWeek() - 1] : "";
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