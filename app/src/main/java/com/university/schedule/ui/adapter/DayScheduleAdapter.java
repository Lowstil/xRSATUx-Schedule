package com.university.schedule.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.university.schedule.R;
import com.university.schedule.logic.ScheduleFilter;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.util.Constants;
import com.university.schedule.util.RoomFormatter;

import java.util.List;

public class DayScheduleAdapter extends RecyclerView.Adapter<DayScheduleAdapter.ViewHolder> {

    private List<ScheduleItem> lessons;

    public DayScheduleAdapter(List<ScheduleItem> lessons) { this.lessons = lessons; }

    public void updateData(List<ScheduleItem> n) { this.lessons = n; notifyDataSetChanged(); }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lesson, parent, false));
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

        setOrHide(h.tvTeacher, it.getTeacherName());
        setOrHide(h.tvGroups, it.getGroupName());
        h.tvOnline.setVisibility(it.isOnline() ? View.VISIBLE : View.GONE);

        RoomFormatter.RoomInfo ri = RoomFormatter.parse(it.getRoom());
        if (ri == null) {
            h.tvRoom.setVisibility(View.GONE);
        } else {
            h.tvRoom.setVisibility(View.VISIBLE);
            h.tvRoom.setText(ri.room != null && !ri.room.isEmpty()
                    ? ri.building + ", каб. " + ri.room : ri.building);
            h.tvRoom.setTextColor(ri.color);
        }

        String spec = it.getWeekSpec();
        if (spec != null && !spec.isEmpty() && !"1-18".equals(spec)) {
            h.tvWeekRange.setText("Недели " + spec);
            h.tvWeekRange.setVisibility(View.VISIBLE);
        } else {
            h.tvWeekRange.setVisibility(View.GONE);
        }
    }

    private void setOrHide(TextView tv, String v) {
        if (v != null && !v.trim().isEmpty()) { tv.setText(v); tv.setVisibility(View.VISIBLE); }
        else tv.setVisibility(View.GONE);
    }

    @Override public int getItemCount() { return lessons != null ? lessons.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvLessonNumber, tvTime, tvSubject, tvLessonType,
                 tvTeacher, tvRoom, tvGroups, tvOnline, tvWeekRange;
        ViewHolder(@NonNull View v) {
            super(v);
            cardView       = v.findViewById(R.id.cardView);
            tvLessonNumber = v.findViewById(R.id.tvLessonNumber);
            tvTime         = v.findViewById(R.id.tvTime);
            tvSubject      = v.findViewById(R.id.tvSubject);
            tvLessonType   = v.findViewById(R.id.tvLessonType);
            tvTeacher      = v.findViewById(R.id.tvTeacher);
            tvRoom         = v.findViewById(R.id.tvRoom);
            tvGroups       = v.findViewById(R.id.tvGroups);
            tvOnline       = v.findViewById(R.id.tvOnline);
            tvWeekRange    = v.findViewById(R.id.tvWeekRange);
        }
    }
}