package com.university.schedule.ui.adapter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.university.schedule.R;
import com.university.schedule.model.LessonNote;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.RoomFormatter;
import com.university.schedule.util.ScheduleClock;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DayScheduleAdapter extends RecyclerView.Adapter<DayScheduleAdapter.ViewHolder> {
    public interface OnLessonClickListener {
        void onLessonClicked(ScheduleItem item, LocalDate date, LessonNote existingNote);
    }

    private static final float BOX_ALPHA_LIGHT = 0.20f;
    private static final float BOX_ALPHA_DARK = 0.28f;
    private static final float ROOM_ALPHA_LIGHT = 0.14f;
    private static final float ROOM_ALPHA_DARK = 0.22f;
    private static final int PRIMARY_LIGHT = 0xFF1565C0;
    private static final int PRIMARY_DARK = 0xFF8AB4F8;
    private static final int TRANSFER_LIGHT = 0xFFB26A00;
    private static final int TRANSFER_DARK = 0xFFF3A96B;
    private static final int CANCELLED_LIGHT = 0xFF9E9E9E;
    private static final int CANCELLED_DARK = 0xFF7C8098;
    private static final float STROKE_WIDTH_DP = 2f;

    private final Map<Long, GradientDrawable> drawableCache = new HashMap<>();
    private List<ScheduleItem> lessons;
    private LocalDate dayDate;
    private ScheduleClock.LessonKey currentKey;
    private ScheduleClock.LessonKey nextKey;
    private ScheduleClock.State clockState;
    private Map<Integer, LessonNote> notesMap = new HashMap<>();
    private OnLessonClickListener lessonClickListener;

    public DayScheduleAdapter(List<ScheduleItem> lessons) {
        this.lessons = lessons;
    }

    public void setOnLessonClickListener(OnLessonClickListener l) {
        this.lessonClickListener = l;
    }

    public void updateData(List<ScheduleItem> n) {
        this.lessons = n;
        notifyDataSetChanged();
    }

    public void setNotes(Map<Integer, LessonNote> notes) {
        this.notesMap = (notes != null) ? notes : new HashMap<>();
        notifyDataSetChanged();
    }

    public void applyClock(LocalDate date, ScheduleClock.State state, ScheduleClock.LessonKey next) {
        this.dayDate = date;
        this.clockState = state;
        this.currentKey = (state != null) ? state.current : null;
        this.nextKey = next;
        notifyDataSetChanged();
    }

    private boolean matchesKey(ScheduleClock.LessonKey key, ScheduleItem it) {
        return key != null && dayDate != null
                && key.date.equals(dayDate)
                && key.lesson == it.getLessonNumber();
    }

    @NonNull
    @Override
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

        h.tvLessonNumber.setText(String.valueOf(n));
        h.tvLessonNumber.setBackground(tintedRound(ctx, primary, boxAlpha, 12));
        h.tvLessonNumber.setTextColor(primary);

        int displayDow = (dayDate != null) ? DateUtils.toScheduleDayOfWeek(dayDate) : it.getDayOfWeek();
        String[][] t_arr = Constants.getLessonTimes(displayDow);
        if (n >= 1 && n <= t_arr.length) {
            h.tvTimeStart.setText(t_arr[n - 1][0]);
            h.tvTimeEnd.setText(t_arr[n - 1][1]);
        } else {
            h.tvTimeStart.setText("");
            h.tvTimeEnd.setText("");
        }

        h.tvSubject.setText(it.getSubjectName());
        String code = shortType(it.getLessonType());
        int tc = typeColor(it.getLessonType(), dark);
        h.tvLessonType.setText(code);
        h.tvLessonType.setVisibility(code.isEmpty() ? View.GONE : View.VISIBLE);
        h.tvLessonType.setBackground(tintedRound(ctx, tc, boxAlpha, 8));
        h.tvLessonType.setTextColor(tc);

        if ("teacher".equals(it.getSource())) {
            String grp = it.getGroupName();
            if (grp != null && !grp.trim().isEmpty()) {
                setOrHide(h.tvTeacher, formatTeacherGroupLabel(grp));
            } else {
                setOrHide(h.tvTeacher, it.getTeacherName());
            }
            h.tvGroups.setVisibility(View.GONE);
        } else {
            setOrHide(h.tvTeacher, it.getTeacherName());
            h.tvGroups.setVisibility(View.GONE);
        }

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

        h.tvOnline.setVisibility(it.isOnline() ? View.VISIBLE : View.GONE);
        String spec = it.getWeekSpec();
        if (spec != null && !spec.isEmpty() && !"1-18".equals(spec)) {
            h.tvWeekRange.setText("недели " + spec);
            h.tvWeekRange.setVisibility(View.VISIBLE);
        } else {
            h.tvWeekRange.setVisibility(View.GONE);
        }

        if (it.isCancelled()) {
            h.tvSubject.setPaintFlags(h.tvSubject.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.itemView.setAlpha(0.55f);
            int cc = dark ? CANCELLED_DARK : CANCELLED_LIGHT;
            h.tvTransferNote.setVisibility(View.VISIBLE);
            h.tvTransferNote.setText(it.getTransferNote() != null ? it.getTransferNote() : "Перенесено");
            h.tvTransferNote.setBackground(tintedRound(ctx, cc, boxAlpha, 6));
            h.tvTransferNote.setTextColor(cc);
        } else {
            h.tvSubject.setPaintFlags(h.tvSubject.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.itemView.setAlpha(1f);
            if (it.isTransferred()) {
                int tcColor = dark ? TRANSFER_DARK : TRANSFER_LIGHT;
                h.tvTransferNote.setVisibility(View.VISIBLE);
                String note = it.getTransferNote();
                h.tvTransferNote.setText(note != null && !note.isEmpty()
                        ? note
                        : (it.isMovedIn() ? "Перенесённое занятие" : "Замена аудитории"));
                h.tvTransferNote.setBackground(tintedRound(ctx, tcColor, boxAlpha, 6));
                h.tvTransferNote.setTextColor(tcColor);
            } else {
                h.tvTransferNote.setVisibility(View.GONE);
            }
        }

        // Отображение личной заметки или ДЗ к паре
        LessonNote noteItem = notesMap.get(n);
        if (noteItem != null && noteItem.getNoteText() != null && !noteItem.getNoteText().isEmpty()) {
            h.noteBadge.setVisibility(View.VISIBLE);
            h.tvNoteText.setText(noteItem.getNoteText());
            if (noteItem.isCompleted()) {
                h.tvNoteText.setPaintFlags(h.tvNoteText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                h.tvNoteText.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
                h.ivNoteIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.online));
            } else {
                h.tvNoteText.setPaintFlags(h.tvNoteText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                h.tvNoteText.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
                h.ivNoteIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent));
            }
        } else {
            h.noteBadge.setVisibility(View.GONE);
        }

        boolean isCurrent = !it.isCancelled() && matchesKey(currentKey, it);
        boolean isNext = !it.isCancelled() && !isCurrent && matchesKey(nextKey, it);
        Object animTag = h.indicator.getTag();
        if (animTag instanceof ValueAnimator) {
            ((ValueAnimator) animTag).cancel();
            h.indicator.setTag(null);
        }
        h.indicator.animate().cancel();

        if (isCurrent) {
            h.cardView.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.state_current_bg));
            h.cardView.setStrokeColor(ContextCompat.getColor(ctx, R.color.state_current_accent));
            h.cardView.setStrokeWidth(strokePx(ctx));
            h.stateRow.setVisibility(View.VISIBLE);

            String stateTxt = "Идёт сейчас";
            if (clockState != null && clockState.minutesLeftInCurrent >= 0) {
                stateTxt += " (осталось " + clockState.minutesLeftInCurrent + " мин)";
            }
            h.tvState.setText(stateTxt);
            h.tvState.setTextColor(ContextCompat.getColor(ctx, R.color.state_current_text));
            h.progressCurrentLesson.setVisibility(View.VISIBLE);
            h.progressCurrentLesson.setProgress(clockState != null ? clockState.currentProgressPercent : 0);

            h.indicator.setVisibility(View.VISIBLE);
            h.indicator.setAlpha(1f);
            h.indicator.getBackground().mutate().setTint(ContextCompat.getColor(ctx, R.color.state_current_accent));
            ValueAnimator va = ValueAnimator.ofFloat(1f, 0.35f);
            va.setDuration(650);
            va.setRepeatMode(ValueAnimator.REVERSE);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.addUpdateListener(a -> h.indicator.setAlpha((float) a.getAnimatedValue()));
            va.start();
            h.indicator.setTag(va);
        } else if (isNext) {
            h.cardView.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.state_next_bg));
            h.cardView.setStrokeColor(ContextCompat.getColor(ctx, R.color.state_next_accent));
            h.cardView.setStrokeWidth(strokePx(ctx));
            h.stateRow.setVisibility(View.VISIBLE);

            String nextTxt = "Следующая";
            if (clockState != null && clockState.isBreak && clockState.minutesUntilNext >= 0) {
                nextTxt += " · перемена (до пары " + clockState.minutesUntilNext + " мин)";
            }
            h.tvState.setText(nextTxt);
            h.tvState.setTextColor(ContextCompat.getColor(ctx, R.color.state_next_text));
            h.progressCurrentLesson.setVisibility(View.GONE);

            h.indicator.setVisibility(View.VISIBLE);
            h.indicator.setAlpha(1f);
            h.indicator.getBackground().mutate().setTint(ContextCompat.getColor(ctx, R.color.state_next_accent));
        } else {
            h.cardView.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface));
            h.cardView.setStrokeWidth(0);
            h.stateRow.setVisibility(View.GONE);
            h.progressCurrentLesson.setVisibility(View.GONE);
            h.indicator.setVisibility(View.GONE);
            h.indicator.setAlpha(1f);
        }

        h.itemView.setOnClickListener(v -> {
            if (lessonClickListener != null) {
                lessonClickListener.onLessonClicked(it, dayDate, notesMap.get(n));
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Object tag = holder.indicator.getTag();
        if (tag instanceof ValueAnimator) {
            ((ValueAnimator) tag).cancel();
            holder.indicator.setTag(null);
        }
        holder.indicator.animate().cancel();
        super.onViewRecycled(holder);
    }

    private void setOrHide(TextView tv, String v) {
        if (v != null && !v.trim().isEmpty()) {
            tv.setText(v);
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private static String formatTeacherGroupLabel(String grp) {
        if (grp == null || grp.trim().isEmpty()) return "";
        String label = grp.trim();
        if (!label.toLowerCase().startsWith("гр")) {
            return (label.contains(",") || label.contains(" ")) ? "Группы: " + label : "Группа: " + label;
        }
        return label;
    }

    private static int strokePx(Context c) {
        return Math.round(STROKE_WIDTH_DP * c.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable tintedRound(Context c, int color, float alpha, int radiusDp) {
        float radiusPx = radiusDp * c.getResources().getDisplayMetrics().density;
        int argb = withAlpha(color, alpha);
        long key = (((long) argb) << 32) ^ Float.floatToRawIntBits(radiusPx);
        GradientDrawable cached = drawableCache.get(key);
        if (cached != null) {
            Drawable.ConstantState state = cached.getConstantState();
            if (state != null) return (GradientDrawable) state.newDrawable(c.getResources()).mutate();
        }
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(argb);
        g.setCornerRadius(radiusPx);
        drawableCache.put(key, g);
        return g;
    }

    private static int withAlpha(int rgb, float a) {
        return Color.argb((int) (a * 255f), Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

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

    @Override
    public int getItemCount() {
        return lessons != null ? lessons.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.card.MaterialCardView cardView;
        View roomChip, stateRow, indicator, noteBadge;
        ProgressBar progressCurrentLesson;
        ImageView ivNoteIcon;
        TextView tvNoteText;
        TextView tvLessonNumber, tvTimeStart, tvTimeEnd, tvSubject, tvLessonType,
                 tvTeacher, tvBuilding, tvRoom, tvGroups, tvOnline, tvWeekRange, tvTransferNote, tvState;

        ViewHolder(@NonNull View v) {
            super(v);
            cardView              = v.findViewById(R.id.cardView);
            roomChip              = v.findViewById(R.id.roomChip);
            stateRow              = v.findViewById(R.id.stateRow);
            indicator             = v.findViewById(R.id.indicator);
            progressCurrentLesson = v.findViewById(R.id.progressCurrentLesson);
            noteBadge             = v.findViewById(R.id.noteBadge);
            ivNoteIcon            = v.findViewById(R.id.ivNoteIcon);
            tvNoteText            = v.findViewById(R.id.tvNoteText);
            tvState               = v.findViewById(R.id.tvState);
            tvLessonNumber        = v.findViewById(R.id.tvLessonNumber);
            tvTimeStart           = v.findViewById(R.id.tvTimeStart);
            tvTimeEnd             = v.findViewById(R.id.tvTimeEnd);
            tvSubject             = v.findViewById(R.id.tvSubject);
            tvLessonType          = v.findViewById(R.id.tvLessonType);
            tvTeacher             = v.findViewById(R.id.tvTeacher);
            tvBuilding            = v.findViewById(R.id.tvBuilding);
            tvRoom                = v.findViewById(R.id.tvRoom);
            tvGroups              = v.findViewById(R.id.tvGroups);
            tvOnline              = v.findViewById(R.id.tvOnline);
            tvWeekRange           = v.findViewById(R.id.tvWeekRange);
            tvTransferNote        = v.findViewById(R.id.tvTransferNote);
        }
    }
}
