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

    public static final class LessonProgress {
        public final int elapsedMinutes;
        public final int totalMinutes;
        public final int remainingMinutes;
        public final int part1Max;      // 45
        public final int part1Progress; // 0..45
        public final int breakMax;      // 5
        public final int breakProgress; // 0..5
        public final int part2Max;      // 45
        public final int part2Progress; // 0..45
        public final int stage;         // 1 = 1-я половина, 2 = перерыв 5 мин, 3 = 2-я половина
        public final String statusText;

        public LessonProgress(int elapsedMinutes, int totalMinutes, int remainingMinutes,
                              int part1Max, int part1Progress,
                              int breakMax, int breakProgress,
                              int part2Max, int part2Progress,
                              int stage, String statusText) {
            this.elapsedMinutes = elapsedMinutes;
            this.totalMinutes = totalMinutes;
            this.remainingMinutes = remainingMinutes;
            this.part1Max = part1Max;
            this.part1Progress = part1Progress;
            this.breakMax = breakMax;
            this.breakProgress = breakProgress;
            this.part2Max = part2Max;
            this.part2Progress = part2Progress;
            this.stage = stage;
            this.statusText = statusText;
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

    /**
     * Вычисляет 3-сегментный прогресс пары:
     * 1) 45 минут идёт пара (зелёная)
     * 2) 5 минут перерыв в середине пары (оранжевая)
     * 3) 45 минут продолжается пара (зелёная)
     */
    public static LessonProgress computeLessonProgress(LocalTime start, LocalTime end, LocalTime now) {
        if (start == null || end == null || now == null) return null;
        int total = (int) java.time.Duration.between(start, end).toMinutes();
        int elapsed = (int) Math.max(0, Math.min(total, java.time.Duration.between(start, now).toMinutes()));
        int remaining = Math.max(0, total - elapsed);

        int half1 = total >= 90 ? 45 : Math.max(10, (total - 5) / 2);
        int midBreak = 5;
        int half2 = Math.max(0, total - half1 - midBreak);

        int p1, pb, p2, stage;
        String status;

        if (elapsed <= half1) {
            p1 = elapsed;
            pb = 0;
            p2 = 0;
            stage = 1;
            int toBreak = half1 - elapsed;
            status = "Идёт 1-я половина (до перерыва " + toBreak + " мин · ост. " + remaining + " мин)";
        } else if (elapsed <= half1 + midBreak) {
            p1 = half1;
            pb = elapsed - half1;
            p2 = 0;
            stage = 2;
            int toPart2 = (half1 + midBreak) - elapsed;
            status = "Перерыв в паре 5 мин (до 2-й половины " + toPart2 + " мин)";
        } else {
            p1 = half1;
            pb = midBreak;
            p2 = Math.min(half2, elapsed - half1 - midBreak);
            stage = 3;
            status = "Идёт 2-я половина (осталось " + remaining + " мин)";
        }

        return new LessonProgress(elapsed, total, remaining, half1, p1, midBreak, pb, half2, p2, stage, status);
    }

    public static String formatTimeUntilNext(int minutesUntil, boolean isBreak) {
        if (isBreak) {
            return "Следующая · перемена (до пары " + minutesUntil + " мин)";
        }
        if (minutesUntil >= 60) {
            int h = minutesUntil / 60;
            int m = minutesUntil % 60;
            return "Следующая · до пары " + h + " ч " + (m > 0 ? (m + " мин") : "");
        } else {
            return "Следующая (до пары " + minutesUntil + " мин)";
        }
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
