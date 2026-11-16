package com.university.schedule.ui.adapter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.university.schedule.R;
import com.university.schedule.model.LessonNote;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.AttendanceManager;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.RoomFormatter;
import com.university.schedule.util.ScheduleClock;
import com.university.schedule.util.SkippedLessonsManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DayScheduleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnLessonClickListener {
        void onLessonClicked(ScheduleItem item, LocalDate date, LessonNote existingNote);
    }

    public interface OnLessonLongClickListener {
        void onLessonLongClicked(ScheduleItem item, LocalDate date);
    }

    public interface OnTeacherClickListener {
        void onTeacherClicked(String teacherName);
    }

    public interface OnRoomClickListener {
        void onRoomClicked(String rawRoom);
    }

    private static final int TYPE_LESSON = 0;
    private static final int TYPE_WINDOW = 1;

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");
    private static final float BOX_ALPHA_LIGHT = 0.20f;
    private static final float BOX_ALPHA_DARK = 0.28f;
    private static final float ROOM_ALPHA_LIGHT = 0.14f;
    private static final float ROOM_ALPHA_DARK = 0.22f;
    private static final int PRIMARY_LIGHT = 0xFF4361EE;
    private static final int PRIMARY_DARK = 0xFF6C8CFF;
    private static final int TRANSFER_LIGHT = 0xFFB26A00;
    private static final int TRANSFER_DARK = 0xFFF3A96B;
    private static final int CANCELLED_LIGHT = 0xFF9E9E9E;
    private static final int CANCELLED_DARK = 0xFF7C8098;

    private interface RowItem {
        int getType();
    }

    private static final class LessonRow implements RowItem {
        final ScheduleItem item;

        LessonRow(ScheduleItem item) {
            this.item = item;
        }

        @Override
        public int getType() {
            return TYPE_LESSON;
        }
    }

    private static final class WindowRow implements RowItem {
        final long durationMinutes;
        final String startTime;
        final String endTime;

        WindowRow(long durationMinutes, String startTime, String endTime) {
            this.durationMinutes = durationMinutes;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        @Override
        public int getType() {
            return TYPE_WINDOW;
        }
    }

    private List<ScheduleItem> lessons = new ArrayList<>();
    private final List<RowItem> rows = new ArrayList<>();
    private LocalDate dayDate;
    private ScheduleClock.LessonKey currentKey;
    private ScheduleClock.LessonKey nextKey;
    private ScheduleClock.State clockState;
    private Map<Integer, LessonNote> notesMap = new HashMap<>();
    private OnLessonClickListener lessonClickListener;
    private OnLessonLongClickListener lessonLongClickListener;
    private OnTeacherClickListener teacherClickListener;
    private OnRoomClickListener roomClickListener;
    private Set<String> excludedSubjects = new HashSet<>();
    private SkippedLessonsManager skippedLessonsManager;
    private AttendanceManager attendanceManager;

    public DayScheduleAdapter(List<ScheduleItem> lessons) {
        this.lessons = (lessons != null) ? lessons : new ArrayList<>();
        rebuildRows();
    }

    public void setAttendanceManager(AttendanceManager m) {
        this.attendanceManager = m;
        notifyDataSetChanged();
    }

    public void setOnLessonClickListener(OnLessonClickListener l) {
        this.lessonClickListener = l;
    }

    public void setOnLessonLongClickListener(OnLessonLongClickListener l) {
        this.lessonLongClickListener = l;
    }

    public void setOnTeacherClickListener(OnTeacherClickListener l) {
        this.teacherClickListener = l;
    }

    public void setOnRoomClickListener(OnRoomClickListener l) {
        this.roomClickListener = l;
    }

    public void updateData(List<ScheduleItem> n) {
        this.lessons = (n != null) ? n : new ArrayList<>();
        rebuildRows();
        notifyDataSetChanged();
    }

    public void setNotes(Map<Integer, LessonNote> notes) {
        this.notesMap = (notes != null) ? notes : new HashMap<>();
        notifyDataSetChanged();
    }

    public void setExcludedSubjects(Set<String> subjects) {
        this.excludedSubjects = subjects != null ? subjects : new HashSet<>();
        rebuildRows();
        notifyDataSetChanged();
    }

    public void setSkippedLessonsManager(SkippedLessonsManager m) {
        this.skippedLessonsManager = m;
        rebuildRows();
        notifyDataSetChanged();
    }

    public void applyClock(LocalDate date, ScheduleClock.State state, ScheduleClock.LessonKey next) {
        this.dayDate = date;
        this.clockState = state;
        this.currentKey = (state != null) ? state.current : null;
        this.nextKey = next;
        rebuildRows();
        notifyDataSetChanged();
    }

    public void updateClockOnly(LocalDate date, ScheduleClock.State state, ScheduleClock.LessonKey next) {
        ScheduleClock.LessonKey newCurrent = (state != null) ? state.current : null;
        boolean dateChanged = !Objects.equals(this.dayDate, date);
        boolean isToday = date != null && date.equals(DateUtils.todayMoscow());
        boolean nextAffectsThisDay = (this.nextKey != null && Objects.equals(this.nextKey.date, date))
                || (next != null && Objects.equals(next.date, date));
        boolean currentAffectsThisDay = (this.currentKey != null && Objects.equals(this.currentKey.date, date))
                || (newCurrent != null && Objects.equals(newCurrent.date, date));
        boolean currentChanged = !sameKey(this.currentKey, newCurrent);
        boolean nextChanged = !sameKey(this.nextKey, next);

        this.dayDate = date;
        this.clockState = state;
        this.currentKey = newCurrent;
        this.nextKey = next;

        if (dateChanged || isToday || (nextAffectsThisDay && nextChanged) || (currentAffectsThisDay && currentChanged)) {
            notifyDataSetChanged();
        }
    }

    private static boolean sameKey(ScheduleClock.LessonKey a, ScheduleClock.LessonKey b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.dayOfWeek == b.dayOfWeek && a.lesson == b.lesson && Objects.equals(a.date, b.date);
    }

    private boolean isLessonActive(ScheduleItem it) {
        if (it == null || it.isCancelled()) return false;
        String subj = it.getSubjectName();
        if (excludedSubjects != null && subj != null && excludedSubjects.contains(subj.trim())) {
            return false;
        }
        if (skippedLessonsManager != null && dayDate != null
                && skippedLessonsManager.isSkipped(dayDate.toString(), it.getLessonNumber(), subj)) {
            return false;
        }
        return true;
    }

    private void rebuildRows() {
        rows.clear();
        if (lessons == null || lessons.isEmpty()) return;

        int displayDow = 1;
        if (dayDate != null) {
            displayDow = DateUtils.toScheduleDayOfWeek(dayDate);
        } else if (lessons.get(0) != null) {
            displayDow = lessons.get(0).getDayOfWeek();
        }
        String[][] bellTimes = Constants.getLessonTimes(displayDow);

        ScheduleItem prevActive = null;
        for (ScheduleItem it : lessons) {
            boolean active = isLessonActive(it);
            if (active && prevActive != null) {
                int prevNum = prevActive.getLessonNumber();
                int currNum = it.getLessonNumber();
                if (currNum - prevNum >= 2
                        && prevNum >= 1 && prevNum <= bellTimes.length
                        && currNum >= 1 && currNum <= bellTimes.length) {
                    try {
                        String prevEndStr = bellTimes[prevNum - 1][1];
                        String currStartStr = bellTimes[currNum - 1][0];
                        LocalTime prevEnd = LocalTime.parse(prevEndStr, TF);
                        LocalTime currStart = LocalTime.parse(currStartStr, TF);
                        long gapMins = Duration.between(prevEnd, currStart).toMinutes();
                        if (gapMins > 0) {
                            rows.add(new WindowRow(gapMins, prevEndStr, currStartStr));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            rows.add(new LessonRow(it));
            if (active) {
                prevActive = it;
            }
        }
    }

    private boolean matchesKey(ScheduleClock.LessonKey key, ScheduleItem it) {
        return key != null && key.date != null && dayDate != null
                && key.date.equals(dayDate)
                && key.lesson == it.getLessonNumber();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).getType();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_WINDOW) {
            return new WindowViewHolder(inf.inflate(R.layout.item_lesson_window, parent, false));
        }
        return new LessonViewHolder(inf.inflate(R.layout.item_lesson, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        RowItem row = rows.get(pos);
        if (row.getType() == TYPE_WINDOW && holder instanceof WindowViewHolder) {
            bindWindow((WindowViewHolder) holder, (WindowRow) row);
        } else if (row.getType() == TYPE_LESSON && holder instanceof LessonViewHolder) {
            bindLesson((LessonViewHolder) holder, ((LessonRow) row).item);
        }
    }

    private void bindWindow(WindowViewHolder h, WindowRow w) {
        GlassSurfaceHelper.applyGlassCard(h.windowCard, 16f, false);
        long hours = w.durationMinutes / 60;
        long mins = w.durationMinutes % 60;
        StringBuilder sb = new StringBuilder("☕ Окно · ");
        if (hours > 0) {
            sb.append(hours).append(" ч");
            if (mins > 0) sb.append(" ").append(mins).append(" мин");
        } else {
            sb.append(mins).append(" мин");
        }
        h.tvWindowDuration.setText(sb.toString());
        h.tvWindowTimeRange.setText(w.startTime + " – " + w.endTime);
    }

    private void bindLesson(LessonViewHolder h, ScheduleItem it) {
        Context ctx = h.itemView.getContext();
        boolean dark = isNightMode(ctx);
        float boxAlpha = dark ? BOX_ALPHA_DARK : BOX_ALPHA_LIGHT;
        float roomAlpha = dark ? ROOM_ALPHA_DARK : ROOM_ALPHA_LIGHT;
        int primary = dark ? PRIMARY_DARK : PRIMARY_LIGHT;
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
        if (h.tvCustomBadge != null) {
            if (it.isCustom()) {
                int customColor = dark ? 0xFFA78BFA : 0xFF7C3AED;
                h.tvCustomBadge.setVisibility(View.VISIBLE);
                h.tvCustomBadge.setBackground(tintedRound(ctx, customColor, boxAlpha, 8));
                h.tvCustomBadge.setTextColor(customColor);
            } else {
                h.tvCustomBadge.setVisibility(View.GONE);
            }
        }
        String code = shortType(it.getLessonType());
        int tc = typeColor(it.getLessonType(), dark);
        h.tvLessonType.setText(code);
        h.tvLessonType.setVisibility(code.isEmpty() ? View.GONE : View.VISIBLE);
        h.tvLessonType.setBackground(tintedRound(ctx, tc, boxAlpha, 8));
        h.tvLessonType.setTextColor(tc);

        // 4dp vertical type pill on left
        if (h.typePill != null) {
            h.typePill.setBackground(tintedRound(ctx, tc, 0.92f, 2));
        }

        if ("teacher".equals(it.getSource())) {
            String grp = it.getGroupName();
            if (grp != null && !grp.trim().isEmpty()) {
                setOrHide(h.tvTeacher, formatTeacherGroupLabel(grp));
            } else {
                setOrHide(h.tvTeacher, it.getTeacherName());
            }
            h.tvGroups.setVisibility(View.GONE);
            h.tvTeacher.setOnClickListener(null);
            h.tvTeacher.setClickable(false);
        } else {
            String teacherName = it.getTeacherName();
            setOrHide(h.tvTeacher, teacherName);
            h.tvGroups.setVisibility(View.GONE);
            if (teacherClickListener != null && teacherName != null && !teacherName.trim().isEmpty()) {
                String trimmedTeacher = teacherName.trim();
                h.tvTeacher.setClickable(true);
                h.tvTeacher.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    teacherClickListener.onTeacherClicked(trimmedTeacher);
                });
            } else {
                h.tvTeacher.setOnClickListener(null);
                h.tvTeacher.setClickable(false);
            }
        }

        RoomFormatter.RoomInfo ri = RoomFormatter.parse(it.getRoom(), dark);
        if (ri == null) {
            h.roomChip.setVisibility(View.GONE);
            h.roomChip.setOnClickListener(null);
            h.roomChip.setClickable(false);
        } else {
            h.roomChip.setVisibility(View.VISIBLE);
            h.roomChip.setBackground(tintedRound(ctx, ri.color, roomAlpha, 10));
            h.tvBuilding.setText(ri.building);
            h.tvBuilding.setTextColor(ri.color);
            if (ri.room != null && !ri.room.isEmpty()) {
                h.tvRoom.setVisibility(View.VISIBLE);
                h.tvRoom.setText("Каб. " + ri.room);
                h.tvRoom.setTextColor(withAlpha(ri.color, 0.85f));
            } else {
                h.tvRoom.setVisibility(View.GONE);
            }
            String rawRoom = it.getRoom();
            if (roomClickListener != null && rawRoom != null && !rawRoom.trim().isEmpty()) {
                String trimmedRoom = rawRoom.trim();
                h.roomChip.setClickable(true);
                h.roomChip.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    roomClickListener.onRoomClicked(trimmedRoom);
                });
            } else {
                h.roomChip.setOnClickListener(null);
                h.roomChip.setClickable(false);
            }
            h.roomChip.requestLayout();
        }

        String spec = it.getWeekSpec();
        if (spec != null && !spec.isEmpty() && !"1-18".equals(spec)) {
            h.tvWeekRange.setText("недели " + spec);
            h.tvWeekRange.setVisibility(View.VISIBLE);
        } else {
            h.tvWeekRange.setVisibility(View.GONE);
        }

        boolean isExempt = (excludedSubjects != null && it.getSubjectName() != null
                && excludedSubjects.contains(it.getSubjectName().trim()));
        boolean isSkipped = (skippedLessonsManager != null && dayDate != null
                && skippedLessonsManager.isSkipped(dayDate.toString(), n, it.getSubjectName()));
        boolean isAttended = (skippedLessonsManager != null && dayDate != null
                && skippedLessonsManager.isAttended(dayDate.toString(), n, it.getSubjectName()));

        if (it.isCancelled()) {
            h.tvSubject.setPaintFlags(h.tvSubject.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.itemView.setAlpha(0.55f);
            int cc = dark ? CANCELLED_DARK : CANCELLED_LIGHT;
            h.tvTransferNote.setVisibility(View.VISIBLE);
            h.tvTransferNote.setText(it.getTransferNote() != null ? it.getTransferNote() : "Перенесено");
            h.tvTransferNote.setBackground(tintedRound(ctx, cc, boxAlpha, 6));
            h.tvTransferNote.setTextColor(cc);
            h.tvExemptBadge.setVisibility(View.GONE);
            h.tvSkippedBadge.setVisibility(View.GONE);
            if (h.tvAttendedBadge != null) h.tvAttendedBadge.setVisibility(View.GONE);
        } else if (isExempt) {
            h.tvSubject.setPaintFlags(h.tvSubject.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.itemView.setAlpha(0.55f);
            h.tvTransferNote.setVisibility(View.GONE);
            h.tvSkippedBadge.setVisibility(View.GONE);
            if (h.tvAttendedBadge != null) h.tvAttendedBadge.setVisibility(View.GONE);
            h.tvExemptBadge.setVisibility(View.VISIBLE);
            int exemptColor = ContextCompat.getColor(ctx, R.color.badge_exempt_text);
            h.tvExemptBadge.setBackground(tintedRound(ctx, exemptColor, boxAlpha, 6));
            h.tvExemptBadge.setTextColor(exemptColor);
        } else if (isSkipped) {
            h.tvSubject.setPaintFlags(h.tvSubject.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.itemView.setAlpha(0.55f);
            h.tvTransferNote.setVisibility(View.GONE);
            h.tvExemptBadge.setVisibility(View.GONE);
            if (h.tvAttendedBadge != null) h.tvAttendedBadge.setVisibility(View.GONE);
            h.tvSkippedBadge.setVisibility(View.VISIBLE);
            h.tvSkippedBadge.setText("🔴 Н-ка");
            int nkaColor = ContextCompat.getColor(ctx, R.color.glass_danger);
            h.tvSkippedBadge.setBackground(tintedRound(ctx, nkaColor, boxAlpha, 6));
            h.tvSkippedBadge.setTextColor(nkaColor);
        } else if (isAttended) {
            h.tvSubject.setPaintFlags(h.tvSubject.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.itemView.setAlpha(1f);
            h.tvTransferNote.setVisibility(View.GONE);
            h.tvExemptBadge.setVisibility(View.GONE);
            h.tvSkippedBadge.setVisibility(View.GONE);
            if (h.tvAttendedBadge != null) {
                h.tvAttendedBadge.setVisibility(View.VISIBLE);
                boolean isFutureDay = dayDate != null && dayDate.isAfter(DateUtils.todayMoscow());
                h.tvAttendedBadge.setText(isFutureDay ? "✓ Пойду" : "✓ Посещено");
                int attendedColor = ContextCompat.getColor(ctx, R.color.glass_success);
                h.tvAttendedBadge.setBackground(tintedRound(ctx, attendedColor, boxAlpha, 6));
                h.tvAttendedBadge.setTextColor(attendedColor);
            }
        } else {
            h.tvSubject.setPaintFlags(h.tvSubject.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.itemView.setAlpha(1f);
            h.tvExemptBadge.setVisibility(View.GONE);
            h.tvSkippedBadge.setVisibility(View.GONE);
            if (h.tvAttendedBadge != null) h.tvAttendedBadge.setVisibility(View.GONE);
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

        if (!isExempt && !isSkipped && !it.isCancelled() && attendanceManager != null) {
            String warn = attendanceManager.getWarningForSubject(it.getSubjectName());
            if (warn != null) {
                h.tvAttendanceWarning.setVisibility(View.VISIBLE);
                h.tvAttendanceWarning.setText(warn);
                int warnTxt = ContextCompat.getColor(ctx, R.color.glass_warning);
                h.tvAttendanceWarning.setBackground(tintedRound(ctx, warnTxt, boxAlpha, 6));
                h.tvAttendanceWarning.setTextColor(warnTxt);
            } else {
                h.tvAttendanceWarning.setVisibility(View.GONE);
            }
        } else {
            h.tvAttendanceWarning.setVisibility(View.GONE);
        }

        LessonNote noteItem = notesMap.get(n);
        if (noteItem != null && noteItem.getNoteText() != null && !noteItem.getNoteText().isEmpty()) {
            h.noteBadge.setVisibility(View.VISIBLE);
            h.tvNoteText.setText(noteItem.getNoteText());
            if (noteItem.isCompleted()) {
                h.tvNoteText.setPaintFlags(h.tvNoteText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                h.tvNoteText.setTextColor(ContextCompat.getColor(ctx, R.color.glass_text_secondary));
                h.ivNoteIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.glass_success));
            } else {
                h.tvNoteText.setPaintFlags(h.tvNoteText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                h.tvNoteText.setTextColor(ContextCompat.getColor(ctx, R.color.glass_text_primary));
                h.ivNoteIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.glass_accent));
            }
            if (noteItem.getImagePath() != null && !noteItem.getImagePath().isEmpty()
                    && new java.io.File(noteItem.getImagePath()).exists()) {
                h.ivNotePhotoIcon.setVisibility(View.VISIBLE);
            } else {
                h.ivNotePhotoIcon.setVisibility(View.GONE);
            }
        } else {
            h.noteBadge.setVisibility(View.GONE);
            h.ivNotePhotoIcon.setVisibility(View.GONE);
        }

        LocalDate today = DateUtils.todayMoscow();
        LocalTime now = DateUtils.nowTimeMoscow();
        boolean isToday = dayDate != null && dayDate.equals(today);
        boolean isActive = !it.isCancelled() && !isExempt && !isSkipped;

        boolean isCurrent = false;
        boolean isNext = false;
        ScheduleClock.LessonProgress lessonProg = null;
        int minsUntilNext = -1;
        boolean isBreakBeforeNext = false;

        if (isActive && isToday && n >= 1 && n <= t_arr.length) {
            LocalTime start = LocalTime.parse(t_arr[n - 1][0], TF);
            LocalTime end = LocalTime.parse(t_arr[n - 1][1], TF);

            if (!now.isBefore(start) && !now.isAfter(end)) {
                isCurrent = true;
                lessonProg = ScheduleClock.computeLessonProgress(start, end, now);
            } else if (now.isBefore(start)) {
                boolean hasEarlierActive = false;
                ScheduleItem prevActiveLesson = null;
                for (ScheduleItem check : lessons) {
                    if (check == it) break;
                    int cn = check.getLessonNumber();
                    boolean checkActive = isLessonActive(check);
                    if (checkActive) {
                        prevActiveLesson = check;
                        if (cn >= 1 && cn <= t_arr.length) {
                            LocalTime cStart = LocalTime.parse(t_arr[cn - 1][0], TF);
                            if (now.isBefore(cStart)) {
                                hasEarlierActive = true;
                                break;
                            }
                        }
                    }
                }

                if (!hasEarlierActive) {
                    isNext = true;
                    minsUntilNext = (int) Duration.between(now, start).toMinutes();

                    boolean duringAnyBell = false;
                    for (String[] pair : t_arr) {
                        LocalTime bStart = LocalTime.parse(pair[0], TF);
                        LocalTime bEnd = LocalTime.parse(pair[1], TF);
                        if (!now.isBefore(bStart) && !now.isAfter(bEnd)) {
                            duringAnyBell = true;
                            break;
                        }
                    }

                    if (!duringAnyBell && prevActiveLesson != null && minsUntilNext <= 50) {
                        int pn = prevActiveLesson.getLessonNumber();
                        if (pn >= 1 && pn <= t_arr.length) {
                            LocalTime pEnd = LocalTime.parse(t_arr[pn - 1][1], TF);
                            if (!now.isBefore(pEnd)) {
                                isBreakBeforeNext = true;
                            }
                        }
                    }
                }
            }
        } else if (isActive && !isToday) {
            isNext = matchesKey(nextKey, it);
        }

        cancelIndicatorAnim(h);

        // Apply Liquid Glass Card surface + state-aware specular border + glow
        GlassSurfaceHelper.applyLessonStateGlass(h.cardView, 22f, isCurrent ? 1 : (isNext ? 2 : 0));

        if (isCurrent && lessonProg != null) {
            int liveAccent = ContextCompat.getColor(ctx,
                    lessonProg.stage == 2 ? R.color.glass_warning : R.color.glass_success);

            if (lessonProg.stage == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                h.cardView.setOutlineSpotShadowColor(liveAccent);
                h.cardView.setOutlineAmbientShadowColor(liveAccent);
            }

            h.stateRow.setVisibility(View.VISIBLE);
            if (h.tvLiveBadge != null) {
                h.tvLiveBadge.setVisibility(View.VISIBLE);
                h.tvLiveBadge.setText("LIVE");
                h.tvLiveBadge.setTextColor(dark ? 0xFF0B1220 : Color.WHITE);
                h.tvLiveBadge.setBackground(tintedRound(ctx, liveAccent, 0.92f, 999));
            }

            h.tvState.setText(lessonProg.statusText);
            h.tvState.setTextColor(liveAccent);
            if (h.indicator.getBackground() != null) {
                h.indicator.getBackground().mutate().setTint(liveAccent);
            }

            h.progressSegmentContainer.setVisibility(View.VISIBLE);
            h.progressPart1.setMax(lessonProg.part1Max);
            h.progressPart1.setProgress(lessonProg.part1Progress);
            h.progressBreak.setMax(lessonProg.breakMax);
            h.progressBreak.setProgress(lessonProg.breakProgress);
            h.progressPart2.setMax(lessonProg.part2Max);
            h.progressPart2.setProgress(lessonProg.part2Progress);

            h.indicator.setVisibility(View.VISIBLE);
            h.indicator.setAlpha(1f);
            ValueAnimator va = ValueAnimator.ofFloat(1f, 0.25f);
            va.setDuration(650);
            va.setRepeatMode(ValueAnimator.REVERSE);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.addUpdateListener(a -> {
                float alpha = (float) a.getAnimatedValue();
                h.indicator.setAlpha(alpha);
                if (h.tvLiveBadge != null) {
                    h.tvLiveBadge.setAlpha(0.65f + alpha * 0.35f);
                }
            });
            va.start();
            h.indicator.setTag(va);
        } else if (isNext) {
            int nextAccent = ContextCompat.getColor(ctx, R.color.glass_accent);

            h.stateRow.setVisibility(View.VISIBLE);
            if (h.tvLiveBadge != null) {
                h.tvLiveBadge.setVisibility(View.VISIBLE);
                h.tvLiveBadge.setAlpha(1f);
                h.tvLiveBadge.setText("ДАЛЕЕ");
                h.tvLiveBadge.setTextColor(nextAccent);
                h.tvLiveBadge.setBackground(tintedRound(ctx, nextAccent, dark ? 0.26f : 0.14f, 999));
            }

            String nextTxt;
            if (isToday && minsUntilNext >= 0) {
                nextTxt = ScheduleClock.formatTimeUntilNext(minsUntilNext, isBreakBeforeNext);
            } else {
                nextTxt = "Следующая пара";
            }
            h.tvState.setText(nextTxt);
            h.tvState.setTextColor(nextAccent);
            h.progressSegmentContainer.setVisibility(View.GONE);

            h.indicator.setVisibility(View.VISIBLE);
            h.indicator.setAlpha(1f);
            if (h.indicator.getBackground() != null) {
                h.indicator.getBackground().mutate().setTint(nextAccent);
            }
        } else {
            h.stateRow.setVisibility(View.GONE);
            if (h.tvLiveBadge != null) {
                h.tvLiveBadge.setVisibility(View.GONE);
                h.tvLiveBadge.setAlpha(1f);
            }
            h.progressSegmentContainer.setVisibility(View.GONE);
            h.indicator.setVisibility(View.GONE);
            h.indicator.setAlpha(1f);
        }

        h.itemView.setOnClickListener(v -> {
            if (lessonClickListener != null) {
                lessonClickListener.onLessonClicked(it, dayDate, notesMap.get(n));
            }
        });

        h.itemView.setOnLongClickListener(v -> {
            if (lessonLongClickListener != null) {
                lessonLongClickListener.onLessonLongClicked(it, dayDate);
                return true;
            }
            return false;
        });
    }

    private void cancelIndicatorAnim(LessonViewHolder lh) {
        if (lh == null || lh.indicator == null) return;
        Object tag = lh.indicator.getTag();
        if (tag instanceof ValueAnimator) {
            ((ValueAnimator) tag).cancel();
            lh.indicator.setTag(null);
        }
        lh.indicator.animate().cancel();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof LessonViewHolder) {
            cancelIndicatorAnim((LessonViewHolder) holder);
        }
        super.onViewRecycled(holder);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof LessonViewHolder) {
            cancelIndicatorAnim((LessonViewHolder) holder);
        }
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            if (vh instanceof LessonViewHolder) {
                cancelIndicatorAnim((LessonViewHolder) vh);
            }
        }
        super.onDetachedFromRecyclerView(recyclerView);
    }

    private void setOrHide(TextView tv, String v) {
        if (v != null && !v.trim().isEmpty()) {
            tv.setText(v);
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private String formatTeacherGroupLabel(String grp) {
        if (grp == null || grp.trim().isEmpty()) return "";
        String trimmed = grp.trim();
        return trimmed.toLowerCase(Locale.ROOT).startsWith("гр.") ? trimmed : ("гр. " + trimmed);
    }

    private Drawable tintedRound(Context ctx, int color, float alpha, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(radiusDp * ctx.getResources().getDisplayMetrics().density);
        gd.setColor(withAlpha(color, alpha));
        return gd;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static boolean isNightMode(Context ctx) {
        int night = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int typeColor(String type, boolean dark) {
        if (type == null) return dark ? 0xFF8A8E9E : 0xFF616161;
        String t = type.trim();
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_LECTURE) || t.equalsIgnoreCase(Constants.LESSON_TYPE_ONLINE_LECTURE))
            return dark ? 0xFF6C8CFF : 0xFF4361EE;
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_PRACTICE) || t.equalsIgnoreCase(Constants.LESSON_TYPE_ONLINE_PRACTICE))
            return dark ? 0xFF34D399 : 0xFF059669;
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_LAB))
            return dark ? 0xFFFBBF24 : 0xFFD97706;
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_EXAM))
            return dark ? 0xFFF87171 : 0xFFDC2626;
        return dark ? 0xFF8A8E9E : 0xFF616161;
    }

    private static String shortType(String type) {
        if (type == null) return "";
        String t = type.trim();
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_LECTURE)) return "Лекция";
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_PRACTICE)) return "Практика";
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_LAB)) return "Лабораторная";
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_ONLINE_LECTURE)) return "Онлайн-лекция";
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_ONLINE_PRACTICE)) return "Онлайн-практика";
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_EXAM)) return "Экзамен";
        return t;
    }

    static class WindowViewHolder extends RecyclerView.ViewHolder {
        final View windowCard;
        final TextView tvWindowDuration;
        final TextView tvWindowTimeRange;

        WindowViewHolder(@NonNull View v) {
            super(v);
            windowCard = v.findViewById(R.id.windowCard);
            tvWindowDuration = v.findViewById(R.id.tvWindowDuration);
            tvWindowTimeRange = v.findViewById(R.id.tvWindowTimeRange);
        }
    }

    static class LessonViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        View typePill, roomChip, stateRow, indicator, noteBadge, progressSegmentContainer;
        ProgressBar progressPart1, progressBreak, progressPart2;
        ImageView ivNoteIcon, ivNotePhotoIcon;
        TextView tvNoteText, tvLiveBadge, tvCustomBadge;
        TextView tvLessonNumber, tvTimeStart, tvTimeEnd, tvSubject, tvLessonType,
                tvTeacher, tvBuilding, tvRoom, tvGroups, tvWeekRange, tvTransferNote,
                tvSkippedBadge, tvAttendedBadge, tvExemptBadge, tvAttendanceWarning, tvState;

        LessonViewHolder(@NonNull View v) {
            super(v);
            cardView = v.findViewById(R.id.cardView);
            typePill = v.findViewById(R.id.typePill);
            roomChip = v.findViewById(R.id.roomChip);
            stateRow = v.findViewById(R.id.stateRow);
            indicator = v.findViewById(R.id.indicator);
            tvLiveBadge = v.findViewById(R.id.tvLiveBadge);
            tvCustomBadge = v.findViewById(R.id.tvCustomBadge);
            progressSegmentContainer = v.findViewById(R.id.progressSegmentContainer);
            progressPart1 = v.findViewById(R.id.progressPart1);
            progressBreak = v.findViewById(R.id.progressBreak);
            progressPart2 = v.findViewById(R.id.progressPart2);
            noteBadge = v.findViewById(R.id.noteBadge);
            ivNoteIcon = v.findViewById(R.id.ivNoteIcon);
            ivNotePhotoIcon = v.findViewById(R.id.ivNotePhotoIcon);
            tvNoteText = v.findViewById(R.id.tvNoteText);
            tvState = v.findViewById(R.id.tvState);
            tvLessonNumber = v.findViewById(R.id.tvLessonNumber);
            tvTimeStart = v.findViewById(R.id.tvTimeStart);
            tvTimeEnd = v.findViewById(R.id.tvTimeEnd);
            tvSubject = v.findViewById(R.id.tvSubject);
            tvLessonType = v.findViewById(R.id.tvLessonType);
            tvTeacher = v.findViewById(R.id.tvTeacher);
            tvBuilding = v.findViewById(R.id.tvBuilding);
            tvRoom = v.findViewById(R.id.tvRoom);
            tvGroups = v.findViewById(R.id.tvGroups);
            tvWeekRange = v.findViewById(R.id.tvWeekRange);
            tvTransferNote = v.findViewById(R.id.tvTransferNote);
            tvSkippedBadge = v.findViewById(R.id.tvSkippedBadge);
            tvAttendedBadge = v.findViewById(R.id.tvAttendedBadge);
            tvExemptBadge = v.findViewById(R.id.tvExemptBadge);
            tvAttendanceWarning = v.findViewById(R.id.tvAttendanceWarning);
        }
    }
}
