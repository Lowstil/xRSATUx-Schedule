package com.university.schedule.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Часы расписания: по московскому времени определяют, какая пара идёт СЕЙЧАС
 * (current) и какая будет СЛЕДУЮЩЕЙ (next). next может указывать на следующий
 * учебный день (вплоть до понедельника, если пары на сегодня кончились или
 * сегодня выходной). Подсветка в UI привязывается к дате конкретного дня,
 * поэтому корректно работает только на реальной текущей неделе — на любой
 * другой неделе (прошлой/будущей) current и next просто не совпадут ни с
 * одной парой, и подсветки не будет, что и требуется.
 *
 * ВАЖНО: этот класс раньше существовал в проекте, но был потерян при
 * переносе кода между версиями приложения (see DayScheduleAdapter/MainActivity
 * — там раньше были поля currentKey/nextKey и вызовы applyClock/updateClock,
 * которые пропали вместе с этим файлом). Восстановлен и заново подключён.
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

        public State(LessonKey current, LessonKey next) {
            this.current = current;
            this.next = next;
        }
    }

    private ScheduleClock() {
    }

    /** Расчёт по текущему московскому времени. */
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

    /** Ближайший будущий учебный день (Пн-Сб), пропуская воскресенье. */
    private static LocalDate nextStudyDate(LocalDate today) {
        for (int k = 1; k <= 8; k++) {
            LocalDate c = today.plusDays(k);
            int d = DateUtils.toScheduleDayOfWeek(c);
            if (d >= 1 && d <= 6) return c;
        }
        return null;
    }
}
