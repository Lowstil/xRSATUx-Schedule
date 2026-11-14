package com.university.schedule.ui.adapter;

import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.university.schedule.R;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.LessonNote;
import com.university.schedule.model.NextLesson;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.AttendanceManager;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.ScheduleClock;
import com.university.schedule.util.SkippedLessonsManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DayPagerAdapter extends RecyclerView.Adapter<DayPagerAdapter.PageViewHolder> {

    private static final int PAGE_COUNT = 6;

    private final SparseArray<PageViewHolder> boundHolders = new SparseArray<>();

    private WeekSchedule currentWeek;
    private Map<String, Map<Integer, LessonNote>> notesByDate = new HashMap<>();
    private ScheduleClock.State clockState;
    private ScheduleClock.LessonKey nextKey;
    private NextLesson nextLesson;

    private Set<String> excludedSubjects = new HashSet<>();
    private SkippedLessonsManager skippedLessonsManager;
    private AttendanceManager attendanceManager;
    private DayScheduleAdapter.OnLessonClickListener lessonClickListener;
    private DayScheduleAdapter.OnLessonLongClickListener lessonLongClickListener;
    private int bottomInsetPx = 0;

    public void setBottomInsetPx(int bottomInsetPx) {
        this.bottomInsetPx = bottomInsetPx;
        for (int i = 0; i < boundHolders.size(); i++) {
            PageViewHolder h = boundHolders.valueAt(i);
            if (h != null) h.applyBottomPadding(bottomInsetPx);
        }
    }

    public void setWeekSchedule(WeekSchedule ws, Map<String, Map<Integer, LessonNote>> notesByDate) {
        this.currentWeek = ws;
        this.notesByDate = (notesByDate != null) ? notesByDate : new HashMap<>();
        notifyDataSetChanged();
    }

    public void updateNotesForDate(String dateIso, Map<Integer, LessonNote> notes) {
        if (dateIso == null) return;
        if (notes != null) {
            notesByDate.put(dateIso, notes);
        } else {
            notesByDate.remove(dateIso);
        }
        notifyDataSetChanged();
    }

    public void applyClock(ScheduleClock.State state, ScheduleClock.LessonKey nextKey, NextLesson nextLesson) {
        this.clockState = state;
        this.nextKey = nextKey;
        this.nextLesson = nextLesson;
        notifyDataSetChanged();
    }

    public void setExcludedSubjects(Set<String> subjects) {
        this.excludedSubjects = subjects != null ? subjects : new HashSet<>();
        notifyDataSetChanged();
    }

    public void setSkippedLessonsManager(SkippedLessonsManager m) {
        this.skippedLessonsManager = m;
        notifyDataSetChanged();
    }

    public void setAttendanceManager(AttendanceManager m) {
        this.attendanceManager = m;
        notifyDataSetChanged();
    }

    public void setOnLessonClickListener(DayScheduleAdapter.OnLessonClickListener l) {
        this.lessonClickListener = l;
    }

    public void setOnLessonLongClickListener(DayScheduleAdapter.OnLessonLongClickListener l) {
        this.lessonLongClickListener = l;
    }

    public boolean canPageScrollUp(int position) {
        PageViewHolder h = boundHolders.get(position);
        return h != null && h.recyclerView.canScrollVertically(-1);
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.page_day_schedule, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        boundHolders.put(position, holder);
        holder.applyBottomPadding(bottomInsetPx);

        DayScheduleAdapter dayAdapter = holder.dayAdapter;
        dayAdapter.setExcludedSubjects(excludedSubjects);
        dayAdapter.setSkippedLessonsManager(skippedLessonsManager);
        dayAdapter.setAttendanceManager(attendanceManager);
        dayAdapter.setOnLessonClickListener(lessonClickListener);
        dayAdapter.setOnLessonLongClickListener(lessonLongClickListener);

        if (currentWeek == null || currentWeek.getDays() == null || position >= currentWeek.getDays().size()) {
            dayAdapter.updateData(new ArrayList<>());
            dayAdapter.setNotes(new HashMap<>());
            dayAdapter.applyClock(null, null, null);
            holder.showEmpty("Нет данных", nextLesson);
            return;
        }

        DaySchedule day = currentWeek.getDays().get(position);
        LocalDate dayDate = day.getDate();

        if (dayDate != null) {
            Map<Integer, LessonNote> notes = notesByDate.get(dayDate.toString());
            dayAdapter.setNotes(notes != null ? notes : new HashMap<>());
        } else {
            dayAdapter.setNotes(new HashMap<>());
        }

        if (day.isDayOff()) {
            dayAdapter.updateData(new ArrayList<>());
            dayAdapter.applyClock(dayDate, null, null);
            holder.showEmpty(day.getHolidayName() != null ? day.getHolidayName() : "Выходной день", nextLesson);
        } else if (!day.hasLessons()) {
            dayAdapter.updateData(new ArrayList<>());
            dayAdapter.applyClock(dayDate, null, null);
            holder.showEmpty("Нет занятий", nextLesson);
        } else {
            holder.hideEmpty();
            dayAdapter.updateData(day.getLessons());
            dayAdapter.applyClock(dayDate, clockState, nextKey);
        }
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        int idx = boundHolders.indexOfValue(holder);
        if (idx >= 0) {
            boundHolders.removeAt(idx);
        }
        super.onViewRecycled(holder);
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final RecyclerView recyclerView;
        final DayScheduleAdapter dayAdapter;
        final View emptyContainer;
        final TextView tvEmpty;
        final LinearLayout nextLessonPanel;
        final TextView tvNextWhen, tvNextSubject, tvNextTeacherRoom;
        private final int baseBottomPad;

        PageViewHolder(@NonNull View v) {
            super(v);
            recyclerView = v.findViewById(R.id.pageRecyclerView);
            emptyContainer = v.findViewById(R.id.pageEmptyContainer);
            tvEmpty = v.findViewById(R.id.tvPageEmpty);
            nextLessonPanel = v.findViewById(R.id.pageNextLessonPanel);
            tvNextWhen = v.findViewById(R.id.tvPageNextWhen);
            tvNextSubject = v.findViewById(R.id.tvPageNextSubject);
            tvNextTeacherRoom = v.findViewById(R.id.tvPageNextTeacherRoom);

            baseBottomPad = recyclerView.getPaddingBottom();
            dayAdapter = new DayScheduleAdapter(new ArrayList<>());
            recyclerView.setLayoutManager(new LinearLayoutManager(v.getContext()));
            recyclerView.setAdapter(dayAdapter);
        }

        void applyBottomPadding(int bottomInsetPx) {
            recyclerView.setPadding(
                    recyclerView.getPaddingLeft(),
                    recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(),
                    baseBottomPad + bottomInsetPx
            );
        }

        void showEmpty(String msg, NextLesson next) {
            recyclerView.setVisibility(View.GONE);
            emptyContainer.setVisibility(View.VISIBLE);
            tvEmpty.setText(msg);

            if (next == null) {
                nextLessonPanel.setVisibility(View.GONE);
            } else {
                GlassSurfaceHelper.applyLessonStateGlass(nextLessonPanel, 22f, 2);
                String dowName = (next.getDayOfWeek() >= 1 && next.getDayOfWeek() <= 6)
                        ? Constants.DAY_NAMES_SHORT[next.getDayOfWeek() - 1] : "";
                String when = dowName + ", " + DateUtils.formatDisplayDateShort(next.getDate())
                        + " · " + next.getLessonNumber() + " пара · "
                        + next.getTimeStart() + "–" + next.getTimeEnd();
                tvNextWhen.setText(when);
                tvNextSubject.setText(next.getSubjectName());
                String tr = next.getTeacherName() != null ? next.getTeacherName() : "";
                if (next.getRoom() != null && !next.getRoom().isEmpty()) {
                    tr += (tr.isEmpty() ? "" : " · ") + next.getRoom();
                }
                tvNextTeacherRoom.setText(tr);
                tvNextTeacherRoom.setVisibility(tr.isEmpty() ? View.GONE : View.VISIBLE);
                nextLessonPanel.setVisibility(View.VISIBLE);
            }
        }

        void hideEmpty() {
            emptyContainer.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
}
