package com.university.schedule.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Часы расписания: вычисляют статус текущей пары (с оставшимся временем и прогрессом),
 * следующей пары и перемены.
 */
public final class ScheduleClock {
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");

    public static final class LessonKey {
        public final int dayOfWeek; // 1..6
        public final int lesson;    // 1..7
        public final LocalDate date;

        public LessonKey(int dayOfWeek, int lesson, LocalDate date) {
            this.dayOfWeek = dayOfWeek;
            this.lesson = lesson;
            this.date = date;
        }
    }

    public static final class State {
        public final LessonKey current; // null, если сейчас перерыв/пар нет
        public final LessonKey next;    // null, если впереди нет учебных дней
        public final int minutesLeftInCurrent;   // минут до конца текущей пары, или -1
        public final int currentProgressPercent; // 0..100% пройденного времени пары
        public final int minutesUntilNext;       // минут до начала следующей пары сегодня, или -1
        public final boolean isBreak;            // true, если сейчас перемена между парами

        public State(LessonKey current, LessonKey next, int minutesLeftInCurrent,
                     int currentProgressPercent, int minutesUntilNext, boolean isBreak) {
            this.current = current;
            this.next = next;
            this.minutesLeftInCurrent = minutesLeftInCurrent;
            this.currentProgressPercent = currentProgressPercent;
            this.minutesUntilNext = minutesUntilNext;
            this.isBreak = isBreak;
        }
    }

    private ScheduleClock() {
    }

    public static State compute() {
        return compute(DateUtils.todayMoscow(), DateUtils.nowTimeMoscow());
    }

    public static State compute(LocalDate today, LocalTime now) {
        int dow = DateUtils.toScheduleDayOfWeek(today);
        String[][] times = (dow >= 1 && dow <= 6) ? Constants.getLessonTimes(dow) : null;

        LessonKey current = null;
        LessonKey next = null;
        int minutesLeft = -1;
        int progress = 0;
        int minutesUntilNext = -1;
        boolean isBreak = false;

        if (times != null && times.length > 0) {
            LocalTime firstLessonStart = LocalTime.parse(times[0][0], TF);
            LocalTime lastLessonEnd = LocalTime.parse(times[times.length - 1][1], TF);

            for (int i = 0; i < times.length; i++) {
                LocalTime start = LocalTime.parse(times[i][0], TF);
                LocalTime end = LocalTime.parse(times[i][1], TF);

                if (!now.isBefore(start) && !now.isAfter(end)) {
                    current = new LessonKey(dow, i + 1, today);
                    long totalMins = java.time.Duration.between(start, end).toMinutes();
                    long elapsedMins = java.time.Duration.between(start, now).toMinutes();
                    minutesLeft = (int) Math.max(0, totalMins - elapsedMins);
                    progress = totalMins > 0 ? (int) Math.min(100, Math.max(0, (elapsedMins * 100) / totalMins)) : 0;
                }

                if (next == null && now.isBefore(start)) {
                    next = new LessonKey(dow, i + 1, today);
                    minutesUntilNext = (int) java.time.Duration.between(now, start).toMinutes();
                    if (now.isAfter(firstLessonStart) && now.isBefore(lastLessonEnd)) {
                        isBreak = true;
                    }
                }
            }
        }

        if (next == null) {
            LocalDate nd = nextStudyDate(today);
            if (nd != null) {
                next = new LessonKey(DateUtils.toScheduleDayOfWeek(nd), 1, nd);
            }
        }

        return new State(current, next, minutesLeft, progress, minutesUntilNext, isBreak);
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
