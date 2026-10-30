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
import com.university.schedule.util.AttendanceManager;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.RoomFormatter;
import com.university.schedule.util.ScheduleClock;
import com.university.schedule.util.SkippedLessonsManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DayScheduleAdapter extends RecyclerView.Adapter<DayScheduleAdapter.ViewHolder> {
    public interface OnLessonClickListener {
        void onLessonClicked(ScheduleItem item, LocalDate date, LessonNote existingNote);
    }

    public interface OnLessonLongClickListener {
        void onLessonLongClicked(ScheduleItem item, LocalDate date);
    }

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");
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
    private OnLessonLongClickListener lessonLongClickListener;
    private Set<String> excludedSubjects = new HashSet<>();
    private SkippedLessonsManager skippedLessonsManager;
    private AttendanceManager attendanceManager;

    public DayScheduleAdapter(List<ScheduleItem> lessons) {
        this.lessons = lessons;
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

    public void updateData(List<ScheduleItem> n) {
        this.lessons = n;
        notifyDataSetChanged();
    }

    public void setNotes(Map<Integer, LessonNote> notes) {
        this.notesMap = (notes != null) ? notes : new HashMap<>();
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

        String spec = it.getWeekSpec();
        if (spec != null && !spec.isEmpty() && !"1-18".equals(spec)) {
            h.tvWeekRange.setText("недели " + spec);
            h.tvWeekRange.setVisibility(View.VISIBLE);
        } else {
            h.tvWeekRange.setVisibility(View.GONE);
        }

        // Проверка: исключён ли предмет для группы (перезачёт)
        boolean isExempt = (excludedSubjects != null && it.getSubjectName() != null
                && excludedSubjects.contains(it.getSubjectName().trim()));

        // Проверка: отмечен ли точечный пропуск пары ("Не пойду")
        boolean isSkipped = (skippedLessonsManager != null && dayDate != null
                && skippedLessonsManager.isSkipped(dayDate.toString(), n, it.getSubjectName()));
        boolean isAttended = (skippedLessonsManager != null && dayDate != null && skippedLessonsManager.isAttended(dayDate.toString(), n, it.getSubjectName()));

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
            int nkaColor = ContextCompat.getColor(ctx, R.color.badge_nka_text);
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
                int attendedColor = ContextCompat.getColor(ctx, R.color.badge_attended_text);
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
                int warnTxt = ContextCompat.getColor(ctx, R.color.badge_warning_text);
                h.tvAttendanceWarning.setBackground(tintedRound(ctx, warnTxt, boxAlpha, 6));
                h.tvAttendanceWarning.setTextColor(warnTxt);
            } else {
                h.tvAttendanceWarning.setVisibility(View.GONE);
            }
        } else {
            h.tvAttendanceWarning.setVisibility(View.GONE);
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

        // Расчёт статусов "Идёт сейчас" и "Следующая пара"
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
                // Ищем, является ли эта пара первой активной после текущего момента
                boolean hasEarlierActive = false;
                ScheduleItem prevActiveLesson = null;
                for (ScheduleItem check : lessons) {
                    if (check == it) break;
                    int cn = check.getLessonNumber();
                    boolean checkActive = !check.isCancelled()
                            && !(excludedSubjects != null && check.getSubjectName() != null && excludedSubjects.contains(check.getSubjectName().trim()))
                            && !(skippedLessonsManager != null && skippedLessonsManager.isSkipped(dayDate.toString(), cn, check.getSubjectName()));
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
                    minsUntilNext = (int) java.time.Duration.between(now, start).toMinutes();

                    // Проверяем: это перемена перед парой или свободное окно?
                    // Перемена:
                    // 1) Сейчас НЕ идёт ни одна пара по общему расписанию звонков
                    // 2) Предыдущая пара студента уже закончилась
                    // 3) До начала следующей пары <= 50 мин
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

        Object animTag = h.indicator.getTag();
        if (animTag instanceof ValueAnimator) {
            ((ValueAnimator) animTag).cancel();
            h.indicator.setTag(null);
        }
        h.indicator.animate().cancel();

        if (isCurrent && lessonProg != null) {
            h.cardView.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.state_current_bg));
            h.cardView.setStrokeColor(ContextCompat.getColor(ctx, R.color.state_current_accent));
            h.cardView.setStrokeWidth(strokePx(ctx));
            h.stateRow.setVisibility(View.VISIBLE);

            h.tvState.setText(lessonProg.statusText);
            if (lessonProg.stage == 2) {
                h.tvState.setTextColor(ContextCompat.getColor(ctx, R.color.progress_segment_orange));
                h.indicator.getBackground().mutate().setTint(ContextCompat.getColor(ctx, R.color.progress_segment_orange));
            } else {
                h.tvState.setTextColor(ContextCompat.getColor(ctx, R.color.state_current_text));
                h.indicator.getBackground().mutate().setTint(ContextCompat.getColor(ctx, R.color.state_current_accent));
            }

            // 3-сегментный прогресс-бар (45 мин / 5 мин / 45 мин)
            h.progressSegmentContainer.setVisibility(View.VISIBLE);
            h.progressPart1.setMax(lessonProg.part1Max);
            h.progressPart1.setProgress(lessonProg.part1Progress);
            h.progressBreak.setMax(lessonProg.breakMax);
            h.progressBreak.setProgress(lessonProg.breakProgress);
            h.progressPart2.setMax(lessonProg.part2Max);
            h.progressPart2.setProgress(lessonProg.part2Progress);

            h.indicator.setVisibility(View.VISIBLE);
            h.indicator.setAlpha(1f);
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

            String nextTxt;
            if (isToday && minsUntilNext >= 0) {
                nextTxt = ScheduleClock.formatTimeUntilNext(minsUntilNext, isBreakBeforeNext);
            } else {
                nextTxt = "Следующая пара";
            }
            h.tvState.setText(nextTxt);
            h.tvState.setTextColor(ContextCompat.getColor(ctx, R.color.state_next_text));
            h.progressSegmentContainer.setVisibility(View.GONE);

            h.indicator.setVisibility(View.VISIBLE);
            h.indicator.setAlpha(1f);
            h.indicator.getBackground().mutate().setTint(ContextCompat.getColor(ctx, R.color.state_next_accent));
        } else {
            h.cardView.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface));
            h.cardView.setStrokeWidth(0);
            h.stateRow.setVisibility(View.GONE);
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

    private String formatTeacherGroupLabel(String grp) {
        if (grp == null || grp.trim().isEmpty()) return "";
        String trimmed = grp.trim();
        return trimmed.toLowerCase().startsWith("гр.") ? trimmed : ("гр. " + trimmed);
    }

    private Drawable tintedRound(Context ctx, int color, float alpha, int radiusDp) {
        long key = (((long) color) << 32)
                ^ (((long) Float.floatToIntBits(alpha)) << 16)
                ^ radiusDp;
        GradientDrawable cached = drawableCache.get(key);
        if (cached != null) return cached;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(radiusDp * ctx.getResources().getDisplayMetrics().density);
        gd.setColor(withAlpha(color, alpha));
        drawableCache.put(key, gd);
        return gd;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int strokePx(Context ctx) {
        return Math.round(STROKE_WIDTH_DP * ctx.getResources().getDisplayMetrics().density);
    }

    private static boolean isNightMode(Context ctx) {
        int night = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int typeColor(String type, boolean dark) {
        if (type == null) return dark ? 0xFF8A8E9E : 0xFF616161;
        String t = type.trim();
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_LECTURE) || t.equalsIgnoreCase(Constants.LESSON_TYPE_ONLINE_LECTURE))
            return dark ? 0xFF5C6BC0 : 0xFF1565C0;
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_PRACTICE) || t.equalsIgnoreCase(Constants.LESSON_TYPE_ONLINE_PRACTICE))
            return dark ? 0xFF66BB6A : 0xFF2E7D32;
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_LAB))
            return dark ? 0xFFFFA726 : 0xFFE65100;
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_EXAM))
            return dark ? 0xFFEF5350 : 0xFFC62828;
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

    @Override
    public int getItemCount() {
        return lessons != null ? lessons.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.card.MaterialCardView cardView;
        View roomChip, stateRow, indicator, noteBadge, progressSegmentContainer;
        ProgressBar progressPart1, progressBreak, progressPart2;
        ImageView ivNoteIcon, ivNotePhotoIcon;
        TextView tvNoteText;
        TextView tvLessonNumber, tvTimeStart, tvTimeEnd, tvSubject, tvLessonType,
                 tvTeacher, tvBuilding, tvRoom, tvGroups, tvWeekRange, tvTransferNote,
                 tvSkippedBadge, tvAttendedBadge, tvExemptBadge, tvAttendanceWarning, tvState;

        ViewHolder(@NonNull View v) {
            super(v);
            cardView                 = v.findViewById(R.id.cardView);
            roomChip                 = v.findViewById(R.id.roomChip);
            stateRow                 = v.findViewById(R.id.stateRow);
            indicator                = v.findViewById(R.id.indicator);
            progressSegmentContainer = v.findViewById(R.id.progressSegmentContainer);
            progressPart1            = v.findViewById(R.id.progressPart1);
            progressBreak            = v.findViewById(R.id.progressBreak);
            progressPart2            = v.findViewById(R.id.progressPart2);
            noteBadge                = v.findViewById(R.id.noteBadge);
            ivNoteIcon               = v.findViewById(R.id.ivNoteIcon);
            ivNotePhotoIcon          = v.findViewById(R.id.ivNotePhotoIcon);
            tvNoteText               = v.findViewById(R.id.tvNoteText);
            tvState                  = v.findViewById(R.id.tvState);
            tvLessonNumber           = v.findViewById(R.id.tvLessonNumber);
            tvTimeStart              = v.findViewById(R.id.tvTimeStart);
            tvTimeEnd                = v.findViewById(R.id.tvTimeEnd);
            tvSubject                = v.findViewById(R.id.tvSubject);
            tvLessonType             = v.findViewById(R.id.tvLessonType);
            tvTeacher                = v.findViewById(R.id.tvTeacher);
            tvBuilding               = v.findViewById(R.id.tvBuilding);
            tvRoom                   = v.findViewById(R.id.tvRoom);
            tvGroups                 = v.findViewById(R.id.tvGroups);
            tvWeekRange              = v.findViewById(R.id.tvWeekRange);
            tvTransferNote           = v.findViewById(R.id.tvTransferNote);
            tvSkippedBadge           = v.findViewById(R.id.tvSkippedBadge);
            tvAttendedBadge      = v.findViewById(R.id.tvAttendedBadge);
            tvExemptBadge            = v.findViewById(R.id.tvExemptBadge);
            tvAttendanceWarning      = v.findViewById(R.id.tvAttendanceWarning);
        }
    }
}
