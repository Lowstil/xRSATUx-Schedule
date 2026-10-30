package com.university.schedule.widget;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.RoomFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Поставляет строки списка виджета — по одной паре на сегодня.
 * Работает синхронно: система виджетов вызывает onDataSetChanged()
 * не на UI-потоке приложения, поэтому обращение к БД здесь безопасно.
 */
public class ScheduleWidgetFactory implements RemoteViewsService.RemoteViewsFactory {
    private final Context context;
    private List<ScheduleItem> lessons = new ArrayList<>();

    public ScheduleWidgetFactory(Context context) {
        this.context = context;
    }

    @Override
    public void onCreate() {
        loadData();
    }

    @Override
    public void onDataSetChanged() {
        // Вызывается системой при каждом notifyAppWidgetViewDataChanged —
        // именно здесь перечитываются данные при каждом обновлении расписания.
        loadData();
    }

    private void loadData() {
        try {
            ScheduleRepository repo = ScheduleRepository.getInstance(context);
            String selType = repo.getSelectionType();
            String selName = repo.getSelectionName();
            if (selType == null || selName == null) {
                lessons = new ArrayList<>();
                return;
            }
            boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
            DaySchedule today = isGroup
                    ? repo.getTodayScheduleForGroup(selName)
                    : repo.getTodayScheduleForTeacher(selName);
            lessons = (today != null && today.hasLessons()) ? today.getLessons() : new ArrayList<>();
        } catch (Exception e) {
            lessons = new ArrayList<>();
        }
    }

    @Override
    public void onDestroy() {
        lessons = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return lessons.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_item_lesson);
        if (position < 0 || position >= lessons.size()) return row;
        ScheduleItem it = lessons.get(position);
        int n = it.getLessonNumber();
        // Перенесённые занятия привязаны к дате напрямую (dayOfWeek=0),
        // поэтому время звонка берём по-реальному "сегодня".
        int dow = DateUtils.toScheduleDayOfWeek(DateUtils.todayMoscow());
        String time = Constants.getLessonTimeLabel(dow, n);

        row.setTextViewText(R.id.widgetLessonNumber, String.valueOf(n));
        row.setTextViewText(R.id.widgetLessonTime, time);
        row.setTextViewText(R.id.widgetLessonSubject, it.getSubjectName());

        // --- тип занятия: цветной код (Л/П/ЛР/оЛ/оП/ЭКЗ) ---
        String code = shortType(it.getLessonType());
        if (code.isEmpty()) {
            row.setViewVisibility(R.id.widgetLessonType, View.GONE);
        } else {
            row.setViewVisibility(R.id.widgetLessonType, View.VISIBLE);
            row.setTextViewText(R.id.widgetLessonType, code);
            row.setTextColor(R.id.widgetLessonType, typeColor(it.getLessonType()));
        }

        // --- аудитория: корпус + кабинет, тёмная палитра виджета ---
        RoomFormatter.RoomInfo ri = RoomFormatter.parse(it.getRoom(), true);
        String roomText = "";
        if (ri != null) {
            roomText = (ri.room != null && !ri.room.isEmpty())
                    ? (ri.building + ", " + ri.room)
                    : (ri.building != null ? ri.building : "");
        }
        row.setTextViewText(R.id.widgetLessonRoom, roomText);

        // --- отменённое занятие приглушаем ---
        int alpha = it.isCancelled() ? 128 : 255;
        row.setInt(R.id.widgetLessonSubject, "setAlpha", alpha);
        row.setInt(R.id.widgetLessonTime, "setAlpha", alpha);
        row.setInt(R.id.widgetLessonRoom, "setAlpha", alpha);

        Intent fillInIntent = new Intent();
        row.setOnClickFillInIntent(R.id.widgetLessonSubject, fillInIntent);
        row.setOnClickFillInIntent(R.id.widgetLessonNumber, fillInIntent);
        return row;
    }

    /** Цвет кода типа занятия под тёмный фон виджета. */
    private static int typeColor(String t) {
        if (t == null) return 0xFFB0B4C4;
        switch (t) {
            case "Л": case "оЛ": return 0xFF8AB4F8;
            case "П": case "оП": return 0xFF81C995;
            case "ЛР": return 0xFFF3A96B;
            case "Экзамен": return 0xFFF29B95;
            default: return 0xFFB0B4C4;
        }
    }

    private static String shortType(String t) {
        if (t == null) return "";
        switch (t) {
            case "Л": return "Л";
            case "П": return "П";
            case "ЛР": return "ЛР";
            case "оЛ": return "оЛ";
            case "оП": return "оП";
            case "Экзамен": return "ЭКЗ";
            default: return t;
        }
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}