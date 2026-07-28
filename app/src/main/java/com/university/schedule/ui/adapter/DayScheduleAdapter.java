package com.university.schedule.ui.adapter;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.university.schedule.R;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.util.RoomFormatter;

import java.util.List;

public class DayScheduleAdapter extends RecyclerView.Adapter<DayScheduleAdapter.ViewHolder> {

    /** Прозрачность фонов боксов "номер пары" и "тип" — чуть плотнее, чем у корпуса (0.14). */
    private static final float BOX_ALPHA_LIGHT = 0.20f;
    private static final float BOX_ALPHA_DARK = 0.28f;
    /** Прозрачность фона плашки корпуса/кабинета (самая лёгкая). */
    private static final float ROOM_ALPHA_LIGHT = 0.14f;
    private static final float ROOM_ALPHA_DARK = 0.22f;
    /** Основной синий для бокса номера пары — отдельные тона под каждую тему,
     *  по той же причине, что и в RoomFormatter: тёмный насыщенный синий
     *  почти не виден на карточке #1C1F2E и не держит контраст как текст. */
    private static final int PRIMARY_LIGHT = 0xFF1565C0;
    private static final int PRIMARY_DARK = 0xFF8AB4F8;

    /** Время звонков по будням (Пн-Пт). Индекс = номер пары - 1. */
    private static final String[][] WEEK = {
            {"08:30", "10:05"}, {"10:15", "11:50"}, {"12:40", "14:15"},
            {"14:25", "16:00"}, {"16:10", "17:45"}, {"18:00", "19:25"}, {"19:35", "21:00"}
    };
    /** Время звонков по субботе. */
    private static final String[][] SAT = {
            {"08:30", "10:05"}, {"10:15", "11:50"}, {"12:00", "13:35"},
            {"13:45", "15:20"}, {"15:30", "17:05"}, {"17:15", "18:40"}, {"18:50", "20:15"}
    };

    private List<ScheduleItem> lessons;

    public DayScheduleAdapter(List<ScheduleItem> lessons) { this.lessons = lessons; }

    public void updateData(List<ScheduleItem> n) { this.lessons = n; notifyDataSetChanged(); }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_lesson, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        Context ctx = h.itemView.getContext();
        boolean dark = isNightMode(ctx);
        float boxAlpha = dark ? BOX_ALPHA_DARK : BOX_ALPHA_LIGHT;
        float roomAlpha = dark ? ROOM_ALPHA_DARK : ROOM_ALPHA_LIGHT;
        int primary = dark ? PRIMARY_DARK : PRIMARY_LIGHT;
        ScheduleItem it = lessons.get(pos);
        int n = it.getLessonNumber();

        // --- номер пары: прозрачный цветной бокс + цветная цифра ---
        h.tvLessonNumber.setText(String.valueOf(n));
        h.tvLessonNumber.setBackground(tintedRound(ctx, primary, boxAlpha, 12));
        h.tvLessonNumber.setTextColor(primary);

        // --- время пары (по будням/субботе) ---
        String[][] t = (it.getDayOfWeek() == 6) ? SAT : WEEK;
        if (n >= 1 && n <= t.length) {
            h.tvTimeStart.setText(t[n - 1][0]);
            h.tvTimeEnd.setText(t[n - 1][1]);
        } else {
            h.tvTimeStart.setText("");
            h.tvTimeEnd.setText("");
        }

        // --- предмет ---
        h.tvSubject.setText(it.getSubjectName());

        // --- тип занятия: прозрачный цветной бокс + цветной код ---
        String code = shortType(it.getLessonType());
        int tc = typeColor(it.getLessonType(), dark);
        h.tvLessonType.setText(code);
        h.tvLessonType.setVisibility(code.isEmpty() ? View.GONE : View.VISIBLE);
        h.tvLessonType.setBackground(tintedRound(ctx, tc, boxAlpha, 8));
        h.tvLessonType.setTextColor(tc);

        // --- преподаватель ---
        setOrHide(h.tvTeacher, it.getTeacherName());

        // --- корпус + кабинет (самый лёгкий фон) ---
        RoomFormatter.RoomInfo ri = RoomFormatter.parse(it.getRoom(), dark);
        if (ri == null) {
            h.roomChip.setVisibility(View.GONE);
        } else {
            h.roomChip.setVisibility(View.VISIBLE);
            h.roomChip.setBackground(tintedRound(ctx, ri.color, roomAlpha, 10));
            h.tvBuilding.setText(ri.building);
            h.tvBuilding.setTextColor(ri.color);
            if (ri.room != null && !ri.room.isEmpty()) {
                h.tvRoom.setVisibility(View.VISIBLE);
                h.tvRoom.setText("Кабинет " + ri.room);
                h.tvRoom.setTextColor(withAlpha(ri.color, 0.85f));
            } else {
                h.tvRoom.setVisibility(View.GONE);
            }
        }

        // --- онлайн / группы / диапазон недель ---
        h.tvOnline.setVisibility(it.isOnline() ? View.VISIBLE : View.GONE);
        setOrHide(h.tvGroups, it.getGroupName());
        String spec = it.getWeekSpec();
        if (spec != null && !spec.isEmpty() && !"1-18".equals(spec)) {
            h.tvWeekRange.setText("недели " + spec);
            h.tvWeekRange.setVisibility(View.VISIBLE);
        } else {
            h.tvWeekRange.setVisibility(View.GONE);
        }
    }

    private void setOrHide(TextView tv, String v) {
        if (v != null && !v.trim().isEmpty()) { tv.setText(v); tv.setVisibility(View.VISIBLE); }
        else tv.setVisibility(View.GONE);
    }

    /** Прямоугольник со скруглением и полупрозрачной заливкой цветом. */
    private static GradientDrawable tintedRound(Context c, int color, float alpha, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(withAlpha(color, alpha));
        g.setCornerRadius(radiusDp * c.getResources().getDisplayMetrics().density);
        return g;
    }

    private static int withAlpha(int rgb, float a) {
        return Color.argb((int) (a * 255f), Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    /** Определяет, активна ли сейчас тёмная тема — по фактической конфигурации
     *  Context'а, поэтому корректно учитывает и режим "Системная" из настроек. */
    private static boolean isNightMode(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int typeColor(String t, boolean dark) {
        if (t == null) return dark ? 0xFFB0B4C4 : 0xFF616161;
        switch (t) {
            case "Л": case "оЛ": return dark ? 0xFF8AB4F8 : 0xFF1565C0;
            case "П": case "оП": return dark ? 0xFF81C995 : 0xFF2E7D32;
            case "ЛР": return dark ? 0xFFF3A96B : 0xFFEF6C00;
            case "Экзамен": return dark ? 0xFFF29B95 : 0xFFC62828;
            default: return dark ? 0xFFB0B4C4 : 0xFF616161;
        }
    }

    private static String shortType(String t) {
        if (t == null) return "";
        switch (t) {
            case "Л": return "Л"; case "П": return "П"; case "ЛР": return "ЛР";
            case "оЛ": return "оЛ"; case "оП": return "оП"; case "Экзамен": return "ЭКЗ";
            default: return t;
        }
    }

    @Override public int getItemCount() { return lessons != null ? lessons.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        View roomChip;
        TextView tvLessonNumber, tvTimeStart, tvTimeEnd, tvSubject, tvLessonType,
                 tvTeacher, tvBuilding, tvRoom, tvGroups, tvOnline, tvWeekRange;
        ViewHolder(@NonNull View v) {
            super(v);
            cardView       = v.findViewById(R.id.cardView);
            roomChip       = v.findViewById(R.id.roomChip);
            tvLessonNumber = v.findViewById(R.id.tvLessonNumber);
            tvTimeStart    = v.findViewById(R.id.tvTimeStart);
            tvTimeEnd      = v.findViewById(R.id.tvTimeEnd);
            tvSubject      = v.findViewById(R.id.tvSubject);
            tvLessonType   = v.findViewById(R.id.tvLessonType);
            tvTeacher      = v.findViewById(R.id.tvTeacher);
            tvBuilding     = v.findViewById(R.id.tvBuilding);
            tvRoom         = v.findViewById(R.id.tvRoom);
            tvGroups       = v.findViewById(R.id.tvGroups);
            tvOnline       = v.findViewById(R.id.tvOnline);
            tvWeekRange    = v.findViewById(R.id.tvWeekRange);
        }
    }
}