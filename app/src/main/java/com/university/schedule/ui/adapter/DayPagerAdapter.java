package com.university.schedule.ui.adapter;

import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.university.schedule.R;
import com.university.schedule.logic.SemesterManager;
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

    public static final int WEEK_COUNT = 18;
    public static final int DAYS_PER_WEEK = 6;
    public static final int PAGES_PER_SEMESTER = WEEK_COUNT * DAYS_PER_WEEK;
    public static final int TOTAL_PAGES = PAGES_PER_SEMESTER * 2;

    public interface OnSemesterCheckListener {
        void onCheckSemesterNow(String semesterId);
    }

    private final SparseArray<PageViewHolder> boundHolders = new SparseArray<>();
    private final Map<String, WeekSchedule> weekCache = new HashMap<>();
    private final Map<String, Map<Integer, LessonNote>> notesByDate = new HashMap<>();
    private boolean fallPublished = true;
    private boolean springPublished = true;
    private ScheduleClock.State clockState;
    private ScheduleClock.LessonKey nextKey;
    private NextLesson nextLesson;

    private Set<String> excludedSubjects = new HashSet<>();
    private SkippedLessonsManager skippedLessonsManager;
    private AttendanceManager attendanceManager;
    private DayScheduleAdapter.OnLessonClickListener lessonClickListener;
    private DayScheduleAdapter.OnLessonLongClickListener lessonLongClickListener;
    private DayScheduleAdapter.OnTeacherClickListener teacherClickListener;
    private DayScheduleAdapter.OnRoomClickListener roomClickListener;
    private OnSemesterCheckListener semesterCheckListener;
    private int bottomInsetPx = 0;

    public static String semesterOf(int position) {
        int clamped = clampPosition(position);
        return clamped < PAGES_PER_SEMESTER
                ? SemesterManager.SEMESTER_FALL
                : SemesterManager.SEMESTER_SPRING;
    }

    public static int weekOf(int position) {
        int clamped = clampPosition(position);
        return ((clamped / DAYS_PER_WEEK) % WEEK_COUNT) + 1;
    }

    public static int dayOf(int position) {
        int clamped = clampPosition(position);
        return clamped % DAYS_PER_WEEK;
    }

    public static int positionOf(String semId, int weekNumber, int dayIndex) {
        int w = Math.max(1, Math.min(WEEK_COUNT, weekNumber));
        int d = Math.max(0, Math.min(DAYS_PER_WEEK - 1, dayIndex));
        int semOffset = SemesterManager.SEMESTER_SPRING.equals(semId) ? PAGES_PER_SEMESTER : 0;
        return semOffset + (w - 1) * DAYS_PER_WEEK + d;
    }

    private static int clampPosition(int position) {
        return Math.max(0, Math.min(TOTAL_PAGES - 1, position));
    }

    private static String cacheKey(String semId, int weekNumber) {
        String sem = SemesterManager.SEMESTER_SPRING.equals(semId)
                ? SemesterManager.SEMESTER_SPRING
                : SemesterManager.SEMESTER_FALL;
        return sem + ":" + weekNumber;
    }

    public void setBottomInsetPx(int bottomInsetPx) {
        this.bottomInsetPx = bottomInsetPx;
        for (int i = 0; i < boundHolders.size(); i++) {
            PageViewHolder h = boundHolders.valueAt(i);
            if (h != null) h.applyBottomPadding(bottomInsetPx);
        }
    }

    public void setSemesterPublished(String semId, boolean published) {
        boolean isSpring = SemesterManager.SEMESTER_SPRING.equals(semId);
        boolean changed = isSpring ? (springPublished != published) : (fallPublished != published);
        if (isSpring) {
            springPublished = published;
        } else {
            fallPublished = published;
        }
        if (changed) {
            int offset = isSpring ? PAGES_PER_SEMESTER : 0;
            notifyItemRangeChanged(offset, PAGES_PER_SEMESTER);
        }
    }

    public boolean isSemesterPublished(String semId) {
        return SemesterManager.SEMESTER_SPRING.equals(semId) ? springPublished : fallPublished;
    }

    public void setWeekSchedule(String semId, int weekNumber, WeekSchedule ws, Map<String, Map<Integer, LessonNote>> weekNotes) {
        if (weekNumber < 1 || weekNumber > WEEK_COUNT) return;
        String key = cacheKey(semId, weekNumber);
        boolean weekChanged = weekCache.get(key) != ws;
        if (ws != null) {
            weekCache.put(key, ws);
        } else {
            weekCache.remove(key);
        }
        boolean notesChanged = weekNotes != null && !weekNotes.isEmpty();
        if (notesChanged) {
            this.notesByDate.putAll(weekNotes);
        }
        if (weekChanged || notesChanged) {
            notifyItemRangeChanged(positionOf(semId, weekNumber, 0), DAYS_PER_WEEK);
        }
    }

    public void clearWeeks() {
        weekCache.clear();
        notesByDate.clear();
        notifyDataSetChanged();
    }

    public void updateNotesForDate(String dateIso, Map<Integer, LessonNote> notes) {
        if (dateIso == null) return;
        if (notes != null) {
            notesByDate.put(dateIso, notes);
        } else {
            notesByDate.remove(dateIso);
        }
        for (int i = 0; i < boundHolders.size(); i++) {
            int pos = boundHolders.keyAt(i);
            PageViewHolder h = boundHolders.valueAt(i);
            if (h != null) {
                bindPage(h, pos);
            }
        }
    }

    public void applyClock(ScheduleClock.State state, ScheduleClock.LessonKey nextKey, NextLesson nextLesson) {
        updateClockOnly(state, nextKey, nextLesson);
    }

    public void updateClockOnly(ScheduleClock.State state, ScheduleClock.LessonKey nextKey, NextLesson nextLesson) {
        this.clockState = state;
        this.nextKey = nextKey;
        this.nextLesson = nextLesson;
        for (int i = 0; i < boundHolders.size(); i++) {
            int pos = boundHolders.keyAt(i);
            PageViewHolder h = boundHolders.valueAt(i);
            if (h == null) continue;
            String semId = semesterOf(pos);
            if (!isSemesterPublished(semId)) {
                h.dayAdapter.updateClockOnly(null, null, null);
                continue;
            }
            int weekNumber = weekOf(pos);
            int dayIndex = dayOf(pos);
            WeekSchedule ws = weekCache.get(cacheKey(semId, weekNumber));
            if (ws == null || ws.getDays() == null || dayIndex >= ws.getDays().size()) {
                h.dayAdapter.updateClockOnly(null, null, null);
                continue;
            }
            DaySchedule day = ws.getDays().get(dayIndex);
            LocalDate dayDate = day.getDate();
            if (day.isDayOff() || !day.hasLessons()) {
                h.dayAdapter.updateClockOnly(dayDate, null, null);
            } else {
                h.dayAdapter.updateClockOnly(dayDate, state, nextKey);
            }
        }
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

    public void setOnTeacherClickListener(DayScheduleAdapter.OnTeacherClickListener l) {
        this.teacherClickListener = l;
    }

    public void setOnRoomClickListener(DayScheduleAdapter.OnRoomClickListener l) {
        this.roomClickListener = l;
    }

    public void setOnSemesterCheckListener(OnSemesterCheckListener l) {
        this.semesterCheckListener = l;
    }

    public boolean canPageScrollUp(int position) {
        PageViewHolder h = boundHolders.get(position);
        return h != null && h.recyclerView.getVisibility() == View.VISIBLE && h.recyclerView.canScrollVertically(-1);
    }

    @Override
    public int getItemCount() {
        return TOTAL_PAGES;
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
        bindPage(holder, position);
    }

    private void bindPage(@NonNull PageViewHolder holder, int position) {
        holder.applyBottomPadding(bottomInsetPx);

        DayScheduleAdapter dayAdapter = holder.dayAdapter;
        dayAdapter.setExcludedSubjects(excludedSubjects);
        dayAdapter.setSkippedLessonsManager(skippedLessonsManager);
        dayAdapter.setAttendanceManager(attendanceManager);
        dayAdapter.setOnLessonClickListener(lessonClickListener);
        dayAdapter.setOnLessonLongClickListener(lessonLongClickListener);
        dayAdapter.setOnTeacherClickListener(teacherClickListener);
        dayAdapter.setOnRoomClickListener(roomClickListener);

        String semId = semesterOf(position);
        int weekNumber = weekOf(position);
        int dayIndex = dayOf(position);

        if (!isSemesterPublished(semId)) {
            dayAdapter.updateData(new ArrayList<>());
            dayAdapter.setNotes(new HashMap<>());
            dayAdapter.applyClock(null, null, null);
            holder.showUnpublished(semId, semesterCheckListener);
            return;
        }

        holder.hideUnpublished();
        WeekSchedule ws = weekCache.get(cacheKey(semId, weekNumber));

        if (ws == null || ws.getDays() == null || dayIndex >= ws.getDays().size()) {
            dayAdapter.updateData(new ArrayList<>());
            dayAdapter.setNotes(new HashMap<>());
            dayAdapter.applyClock(null, null, null);
            holder.showEmpty("Нет данных", nextLesson);
            return;
        }

        DaySchedule day = ws.getDays().get(dayIndex);
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
        final View cardPageUnpublished;
        final TextView tvPageUnpublishedTitle, tvPageUnpublishedSubtitle, btnPageCheckSemesterNow;
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
            cardPageUnpublished = v.findViewById(R.id.cardPageUnpublished);
            tvPageUnpublishedTitle = v.findViewById(R.id.tvPageUnpublishedTitle);
            tvPageUnpublishedSubtitle = v.findViewById(R.id.tvPageUnpublishedSubtitle);
            btnPageCheckSemesterNow = v.findViewById(R.id.btnPageCheckSemesterNow);

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

        void showUnpublished(String semId, OnSemesterCheckListener checkListener) {
            recyclerView.setVisibility(View.GONE);
            emptyContainer.setVisibility(View.GONE);
            cardPageUnpublished.setVisibility(View.VISIBLE);
            GlassSurfaceHelper.applyGlassCard(cardPageUnpublished, 24f, false);
            GlassSurfaceHelper.applyGlassCard(btnPageCheckSemesterNow, 999f, true);

            boolean isSpring = SemesterManager.SEMESTER_SPRING.equals(semId);
            tvPageUnpublishedTitle.setText(isSpring
                    ? "Расписание на весенний семестр ещё не опубликовано"
                    : "Расписание на осенний семестр ещё не опубликовано");
            tvPageUnpublishedSubtitle.setText(isSpring
                    ? "Оно появится автоматически после 30 января, как только вуз выложит новый файл на сайте."
                    : "Оно появится автоматически после 25 августа, как только вуз выложит новый файл на сайте.");
            btnPageCheckSemesterNow.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (checkListener != null) {
                    checkListener.onCheckSemesterNow(semId);
                }
            });
        }

        void hideUnpublished() {
            cardPageUnpublished.setVisibility(View.GONE);
        }

        void showEmpty(String msg, NextLesson next) {
            cardPageUnpublished.setVisibility(View.GONE);
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
