package com.university.schedule.util;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
/**
 * Подсветка "Идёт сейчас" / "Следующая" по РЕАЛЬНЫМ парам отображаемой недели
 * и по времени УСТРОЙСТВА (а не по таблице звонков на 7 пар и не по Москве).
 *
 * Логика (именно та, что ожидается):
 *  - current = реальная пара сегодняшнего дня, чей интервал звонка содержит now;
 *  - next    = первая реальная пара сегодня, чей старт позже now;
 *  - если сегодня позже now пар нет (в т.ч. во время последней пары) —
 *    next = первая реальная пара ближайшего следующего дня, у которого есть
 *    пары (подсветка появится на вкладке того дня).
 * Если сегодняшняя дата не попадает в отображаемую неделю — подсветки нет
 * (корректно для прошлых/будущих недель).
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
        public final LessonKey next;    // null, если впереди на неделе пар нет
        public State(LessonKey current, LessonKey next) {
            this.current = current;
            this.next = next;
        }
    }

    private ScheduleClock() { }

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
            // список пар отсортирован по номеру, поэтому первый найденный
            // "старт позже now" и есть следующая пара
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
            // сегодня пар больше нет (в т.ч. идёт последняя) — ищем ближайший
            // следующий день недели с парами
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
}