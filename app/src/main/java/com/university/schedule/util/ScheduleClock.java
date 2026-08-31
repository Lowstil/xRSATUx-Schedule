package com.university.schedule.util;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
public final class ScheduleClock {
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");
    public static final class LessonKey {
        public final int dayOfWeek;
        public final int lesson;
        public final LocalDate date;
        public LessonKey(int dayOfWeek, int lesson, LocalDate date) {
            this.dayOfWeek = dayOfWeek;
            this.lesson = lesson;
            this.date = date;
        }
    }
    public static final class State {
        public final LessonKey current;
        public final LessonKey next;
        public State(LessonKey current, LessonKey next) {
            this.current = current;
            this.next = next;
        }
    }
    private ScheduleClock() { }
    public static State compute() {
        return compute(DateUtils.todayMoscow(), DateUtils.nowTimeMoscow());
    }
    public static State compute(LocalDate today, LocalTime now) {
        int dow = DateUtils.toScheduleDayOfWeek(today);
        String[][] times = (dow >= 1 && dow <= 6) ? Constants.getLessonTimes(dow) : null;
        LessonKey current = null;
        LessonKey next = null;
        if (times != null) {
            for (int i = 0; i < times.length; i++) {
                LocalTime start = LocalTime.parse(times[i][0], TF);
                LocalTime end = LocalTime.parse(times[i][1], TF);
                if (!now.isBefore(start) && !now.isAfter(end)) {
                    current = new LessonKey(dow, i + 1, today);
                }
                if (next == null && now.isBefore(start)) {
                    next = new LessonKey(dow, i + 1, today);
                }
            }
        }
        if (next == null) {
            LocalDate nd = nextStudyDate(today);
            if (nd != null) {
                next = new LessonKey(DateUtils.toScheduleDayOfWeek(nd), 1, nd);
            }
        }
        return new State(current, next);
    }
    public static State computeForWeek(List<DaySchedule> days, LocalDate today, LocalTime now) {
        if (days == null || today == null || now == null) return new State(null, null);
        DaySchedule todayDay = null;
        for (DaySchedule d : days) {
            if (d != null && today.equals(d.getDate())) { todayDay = d; break; }
        }
        if (todayDay == null) return new State(null, null);
        LessonKey current = null;
        LessonKey next = null;
        int dow = DateUtils.toScheduleDayOfWeek(today);
        String[][] bells = Constants.getLessonTimes(dow);
        if (!todayDay.isDayOff() && todayDay.getLessons() != null) {
            for (ScheduleItem it : todayDay.getLessons()) {
                if (it == null || it.isCancelled()) continue;
                int n = it.getLessonNumber();
                if (n < 1 || n > bells.length) continue;
                LocalTime start = LocalTime.parse(bells[n - 1][0], TF);
                LocalTime end = LocalTime.parse(bells[n - 1][1], TF);
                if (current == null && !now.isBefore(start) && !now.isAfter(end)) {
                    current = new LessonKey(dow, n, today);
                }
                if (next == null && now.isBefore(start)) {
                    next = new LessonKey(dow, n, today);
                }
            }
        }
        if (next == null) {
            for (DaySchedule d : days) {
                if (d == null || d.getDate() == null || !d.getDate().isAfter(today)) continue;
                if (d.isDayOff() || !d.hasLessons()) continue;
                for (ScheduleItem it : d.getLessons()) {
                    if (it != null && !it.isCancelled()) {
                        next = new LessonKey(DateUtils.toScheduleDayOfWeek(d.getDate()),
                                it.getLessonNumber(), d.getDate());
                        break;
                    }
                }
                if (next != null) break;
            }
        }
        return new State(current, next);
    }
    private static LocalDate nextStudyDate(LocalDate today) {
        for (int k = 1; k <= 8; k++) {
            LocalDate c = today.plusDays(k);
            int d = DateUtils.toScheduleDayOfWeek(c);
            if (d >= 1 && d <= 6) return c;
        }
        return null;
    }
}