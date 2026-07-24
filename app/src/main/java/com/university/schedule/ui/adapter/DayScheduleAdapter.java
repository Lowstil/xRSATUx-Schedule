package com.university.schedule.ui.adapter;

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

import java.util.List;

public class DayScheduleAdapter extends RecyclerView.Adapter<DayScheduleAdapter.ViewHolder> {

    private List<ScheduleItem> lessons;

    public DayScheduleAdapter(List<ScheduleItem> lessons) {
        this.lessons = lessons;
    }

    public void updateData(List<ScheduleItem> newLessons) {
        this.lessons = newLessons;
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
        if (n >= 1 && n <= Constants.LESSON_TIMES.length) {
            h.tvTime.setText(Constants.LESSON_TIMES[n - 1][0] + " – " + Constants.LESSON_TIMES[n - 1][1]);
        } else {
            h.tvTime.setText("");
        }
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
        TextView tvLessonNumber, tvTime, tvSubject, tvLessonType,
                tvTeacher, tvRoom, tvGroups, tvOnline, tvWeekRange;
        ViewHolder(@NonNull View v) {
            super(v);
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