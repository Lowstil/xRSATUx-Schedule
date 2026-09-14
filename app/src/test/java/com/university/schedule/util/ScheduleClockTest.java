package com.university.schedule.util;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

/**
 * Юнит-тесты для ScheduleClock.compute — граничные случаи (полночь, конец
 * дня, суббота с другим расписанием звонков, переход через воскресенье).
 * Даты подобраны на реальный календарь (9, 14, 15, 16 февраля 2026 —
 * соответственно понедельник, суббота, воскресенье, понедельник), чтобы
 * тесты были проверяемы вручную по обычному календарю, а не только по
 * относительным смещениям.
 */
public class ScheduleClockTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 2, 9);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 2, 14);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 2, 15);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 2, 16);

    @Test
    public void duringFirstLessonOnWeekday_currentIsLessonOne() {
        ScheduleClock.State st = ScheduleClock.compute(MONDAY, LocalTime.of(9, 0));
        assertNotNull(st.current);
        assertEquals(1, st.current.lesson);
        assertEquals(MONDAY, st.current.date);
        assertNotNull(st.next);
        assertEquals(2, st.next.lesson);
    }

    @Test
    public void exactEndBoundaryOfLesson_isStillCurrent() {
        // Пара 1 на будни заканчивается в 10:05 — конец включительный.
        ScheduleClock.State st = ScheduleClock.compute(MONDAY, LocalTime.of(10, 5));
        assertNotNull("Ровно в момент окончания пара должна ещё считаться текущей", st.current);
        assertEquals(1, st.current.lesson);
    }

    @Test
    public void oneMinuteAfterLessonEnd_noLongerCurrentButNextIsSecond() {
        ScheduleClock.State st = ScheduleClock.compute(MONDAY, LocalTime.of(10, 6));
        assertNull("Через минуту после окончания пара уже не текущая", st.current);
        assertNotNull(st.next);
        assertEquals(2, st.next.lesson);
    }

    @Test
    public void midnight_noCurrentLesson_nextIsFirstLessonSameDay() {
        ScheduleClock.State st = ScheduleClock.compute(MONDAY, LocalTime.of(0, 0));
        assertNull(st.current);
        assertNotNull(st.next);
        assertEquals(1, st.next.lesson);
        assertEquals(MONDAY, st.next.date);
    }

    @Test
    public void lateEvening_afterAllLessons_nextRollsToTomorrow() {
        ScheduleClock.State st = ScheduleClock.compute(MONDAY, LocalTime.of(23, 59));
        assertNull(st.current);
        assertNotNull(st.next);
        assertEquals(1, st.next.lesson);
        assertEquals(LocalDate.of(2026, 2, 10), st.next.date); // вторник
    }

    @Test
    public void saturdayUsesDifferentTimeTableThanWeekday() {
        // На будни 3-я пара 12:40-14:15, по субботам — 12:00-13:35. Если
        // тест проходит с субботним временем, значит таблица звонков не
        // перепутана с будничной.
        ScheduleClock.State st = ScheduleClock.compute(SATURDAY, LocalTime.of(13, 0));
        assertNotNull(st.current);
        assertEquals(3, st.current.lesson);
        assertEquals(SATURDAY, st.current.date);
    }

    @Test
    public void saturdayWeekdayLessonStartTimeFallsInsideDifferentSaturdayLesson() {
        // 12:40 — момент начала 3-й пары по БУДНЯМ, но в субботу в это время
        // всё ещё идёт 3-я пара по СУББОТНЕМУ расписанию (12:00-13:35, а не
        // 12:40-14:15) — то есть тот же самый момент времени должен
        // распознаваться относительно субботней, а не будничной таблицы.
        // Если бы таблицы звонков перепутались, тест бы не прошёл на
        // границе, где будничное и субботнее расписание расходятся.
        ScheduleClock.State st = ScheduleClock.compute(SATURDAY, LocalTime.of(12, 40));
        assertNotNull("12:40 в субботу — ещё внутри 3-й пары по субботнему расписанию", st.current);
        assertEquals(3, st.current.lesson);
    }

    @Test
    public void saturdayGapBetweenThirdAndFourthLesson_noCurrent() {
        // Реальный перерыв по субботам: 3-я пара заканчивается в 13:35,
        // 4-я начинается в 13:45 — с 13:36 по 13:44 пары нет.
        ScheduleClock.State st = ScheduleClock.compute(SATURDAY, LocalTime.of(13, 40));
        assertNull("13:40 в субботу — перерыв между 3-й и 4-й парой", st.current);
        assertNotNull(st.next);
        assertEquals(4, st.next.lesson);
    }

    @Test
    public void afterAllSaturdayLessons_nextSkipsSundayToMonday() {
        ScheduleClock.State st = ScheduleClock.compute(SATURDAY, LocalTime.of(21, 0));
        assertNull(st.current);
        assertNotNull(st.next);
        assertEquals(1, st.next.lesson);
        assertEquals(NEXT_MONDAY, st.next.date);
    }

    @Test
    public void sunday_noLessonsAtAll_nextIsMonday() {
        ScheduleClock.State st = ScheduleClock.compute(SUNDAY, LocalTime.of(12, 0));
        assertNull(st.current);
        assertNotNull(st.next);
        assertEquals(1, st.next.lesson);
        assertEquals(NEXT_MONDAY, st.next.date);
        assertEquals(1, st.next.dayOfWeek); // понедельник
    }

    @Test
    public void justBeforeFirstLessonStarts_nextIsFirstLessonToday() {
        ScheduleClock.State st = ScheduleClock.compute(MONDAY, LocalTime.of(8, 29));
        assertNull(st.current);
        assertNotNull(st.next);
        assertEquals(1, st.next.lesson);
        assertEquals(MONDAY, st.next.date);
    }

    @Test
    public void exactStartBoundaryOfLesson_isCurrent() {
        ScheduleClock.State st = ScheduleClock.compute(MONDAY, LocalTime.of(8, 30));
        assertNotNull("Ровно в момент начала пара должна уже считаться текущей", st.current);
        assertEquals(1, st.current.lesson);
    }
}
