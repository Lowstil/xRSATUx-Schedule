package com.university.schedule.ui.adapter;

import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.university.schedule.R;
import com.university.schedule.logic.ScheduleFilter;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.util.Constants;
import com.university.schedule.util.ScheduleClock;

import java.time.LocalDate;
import java.util.List;

/**
 * Адаптер списка пар одного дня. Умеет подсвечивать пару, идущую сейчас,
 * и следующую по времени пару (через applyClock).
 */
public class DayScheduleAdapter extends RecyclerView.Adapter<DayScheduleAdapter.ViewHolder> {

    private List<ScheduleItem> lessons;
    private LocalDate dayDate;
    private ScheduleClock.LessonKey currentKey;
    private ScheduleClock.LessonKey nextKey;

    public DayScheduleAdapter(List<ScheduleItem> lessons) {
        this.lessons = lessons;
    }

    public void updateData(List<ScheduleItem> newLessons) {
        this.lessons = newLessons;
        notifyDataSetChanged();
    }

    /** Задать дату отображаемого дня и ключи подсветки; перерисовать список. */
    public void applyClock(LocalDate date, ScheduleClock.LessonKey current, ScheduleClock.LessonKey next) {
        this.dayDate = date;
        this.currentKey = current;
        this.nextKey = next;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lesson, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ScheduleItem it = lessons.get(position);
        int n = it.getLessonNumber();

        h.tvLessonNumber.setText(String.valueOf(n));
        h.tvTime.setText(Constants.getLessonTimeLabel(it.getDayOfWeek(), n));
        h.tvSubject.setText(it.getSubjectName());

        String type = ScheduleFilter.shortLessonType(it.getLessonType());
        h.tvLessonType.setText(type);
        h.tvLessonType.setVisibility(type.isEmpty() ? View.GONE : View.VISIBLE);

        int color;
        String lt = it.getLessonType();
        if (Constants.LESSON_TYPE_LECTURE.equals(lt) || Constants.LESSON_TYPE_ONLINE_LECTURE.equals(lt)) {
            color = R.color.badge_lecture;
        } else if (Constants.LESSON_TYPE_PRACTICE.equals(lt) || Constants.LESSON_TYPE_ONLINE_PRACTICE.equals(lt)) {
            color = R.color.badge_practice;
        } else if (Constants.LESSON_TYPE_LAB.equals(lt)) {
            color = R.color.badge_lab;
        } else if (Constants.LESSON_TYPE_EXAM.equals(lt)) {
            color = R.color.badge_exam;
        } else {
            color = R.color.badge_default;
        }
        h.tvLessonType.setBackgroundColor(ContextCompat.getColor(h.itemView.getContext(), color));

        setOrHide(h.tvTeacher, it.getTeacherName());
        setOrHide(h.tvRoom, it.getRoom());
        setOrHide(h.tvGroups, it.getGroupName());
        h.tvOnline.setVisibility(it.isOnline() ? View.VISIBLE : View.GONE);

        String spec = it.getWeekSpec();
        if (spec != null && !spec.isEmpty() && !"1-18".equals(spec)) {
            h.tvWeekRange.setText("Недели " + spec);
            h.tvWeekRange.setVisibility(View.VISIBLE);
        } else {
            h.tvWeekRange.setVisibility(View.GONE);
        }

        // --- подсветка текущей / следующей пары ---
        boolean isCur = matches(currentKey, it);
        boolean isNext = !isCur && matches(nextKey, it);

        int bg;
        if (isCur) bg = R.color.state_current_bg;
        else if (isNext) bg = R.color.state_next_bg;
        else bg = R.color.surface;
        h.cardView.setCardBackgroundColor(ContextCompat.getColor(h.itemView.getContext(), bg));

        // индикатор-точка
        Object tag = h.indicator.getTag();
        if (tag instanceof ValueAnimator) {
            ((ValueAnimator) tag).cancel();
            h.indicator.setTag(null);
        }
        h.indicator.animate().cancel();

        if (isCur) {
            h.stateRow.setVisibility(View.VISIBLE);
            h.tvState.setText("Идёт сейчас");
            h.tvState.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.state_current_text));
            h.indicator.setVisibility(View.VISIBLE);
            h.indicator.getBackground().mutate().setTint(
                    ContextCompat.getColor(h.itemView.getContext(), R.color.state_current_accent));
            ValueAnimator va = ValueAnimator.ofFloat(1f, 0.35f);
            va.setDuration(650);
            va.setRepeatMode(ValueAnimator.REVERSE);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.addUpdateListener(a -> h.indicator.setAlpha((float) a.getAnimatedValue()));
            va.start();
            h.indicator.setTag(va);
        } else if (isNext) {
            h.stateRow.setVisibility(View.VISIBLE);
            h.tvState.setText("Следующая");
            h.tvState.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.state_next_text));
            h.indicator.setVisibility(View.VISIBLE);
            h.indicator.setAlpha(1f);
            h.indicator.getBackground().mutate().setTint(
                    ContextCompat.getColor(h.itemView.getContext(), R.color.state_next_accent));
        } else {
            h.stateRow.setVisibility(View.GONE);
            h.indicator.setVisibility(View.GONE);
            h.indicator.setAlpha(1f);
        }
    }

    private boolean matches(ScheduleClock.LessonKey key, ScheduleItem it) {
        return key != null && dayDate != null
                && key.dayOfWeek == it.getDayOfWeek()
                && key.lesson == it.getLessonNumber()
                && key.date.equals(dayDate);
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

    private void setOrHide(TextView tv, String value) {
        if (value != null && !value.trim().isEmpty()) {
            tv.setText(value);
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return lessons != null ? lessons.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        androidx.cardview.widget.CardView cardView;
        View stateRow, indicator;
        TextView tvState, tvLessonNumber, tvTime, tvSubject, tvLessonType,
                tvTeacher, tvRoom, tvGroups, tvOnline, tvWeekRange;

        ViewHolder(@NonNull View v) {
            super(v);
            cardView = v.findViewById(R.id.cardView);
            stateRow = v.findViewById(R.id.stateRow);
            indicator = v.findViewById(R.id.indicator);
            tvState = v.findViewById(R.id.tvState);
            tvLessonNumber = v.findViewById(R.id.tvLessonNumber);
            tvTime = v.findViewById(R.id.tvTime);
            tvSubject = v.findViewById(R.id.tvSubject);
            tvLessonType = v.findViewById(R.id.tvLessonType);
            tvTeacher = v.findViewById(R.id.tvTeacher);
            tvRoom = v.findViewById(R.id.tvRoom);
            tvGroups = v.findViewById(R.id.tvGroups);
            tvOnline = v.findViewById(R.id.tvOnline);
            tvWeekRange = v.findViewById(R.id.tvWeekRange);
        }
    }
}