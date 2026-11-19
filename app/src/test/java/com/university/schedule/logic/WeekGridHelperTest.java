package com.university.schedule.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.util.Constants;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WeekGridHelperTest {

    private static ScheduleItem lesson(int dow, int slot, String subject, String type) {
        ScheduleItem item = new ScheduleItem();
        item.setDayOfWeek(dow);
        item.setLessonNumber(slot);
        item.setSubjectName(subject);
        item.setLessonType(type);
        return item;
    }

    @Test
    public void computeSummary_nullOrEmptyWeek_returnsDefaults() {
        WeekGridHelper.WeekSummary s = WeekGridHelper.computeSummary(null, null, null);
        assertEquals(0, s.totalActiveLessons);
        assertEquals(-1, s.busiestDayIndex);
        assertEquals(0, s.busiestDayCount);
        assertEquals(0, s.freeWindowsCount);
        assertEquals(WeekGridHelper.DEFAULT_MAX_SLOTS, s.maxLessonSlot);
        assertEquals(0, s.holidayDaysCount);
    }

    @Test
    public void computeSummary_countsLessonsBusiestDayWindowsAndSlot7() {
        LocalDate mon = LocalDate.of(2026, 11, 16);
        WeekSchedule ws = new WeekSchedule(12, true);

        // Mon (0): slots 1, 4 -> 2 lessons, 2 free windows (slots 2, 3)
        DaySchedule d0 = new DaySchedule(1, mon);
        d0.setLessons(Arrays.asList(
                lesson(1, 1, "Математика", Constants.LESSON_TYPE_LECTURE),
                lesson(1, 4, "Физика", Constants.LESSON_TYPE_LAB)
        ));

        // Tue (1): holiday
        DaySchedule d1 = new DaySchedule(2, mon.plusDays(1));
        d1.setDayOff(true);
        d1.setHolidayName("Праздник");

        // Wed (2): slots 1, 2, 3, 7 -> 4 lessons, 3 free windows (slots 4, 5, 6), maxSlot=7
        DaySchedule d2 = new DaySchedule(3, mon.plusDays(2));
        d2.setLessons(Arrays.asList(
                lesson(3, 1, "Программирование", Constants.LESSON_TYPE_PRACTICE),
                lesson(3, 2, "Базы данных", Constants.LESSON_TYPE_LECTURE),
                lesson(3, 3, "Сети", Constants.LESSON_TYPE_LAB),
                lesson(3, 7, "Факультатив", Constants.LESSON_TYPE_PRACTICE)
        ));

        // Thu..Sat empty
        DaySchedule d3 = new DaySchedule(4, mon.plusDays(3));
        DaySchedule d4 = new DaySchedule(5, mon.plusDays(4));
        DaySchedule d5 = new DaySchedule(6, mon.plusDays(5));

        ws.setDays(Arrays.asList(d0, d1, d2, d3, d4, d5));

        WeekGridHelper.WeekSummary s = WeekGridHelper.computeSummary(ws, Collections.emptySet(), null);
        assertEquals(6, s.totalActiveLessons);
        assertEquals(2, s.busiestDayIndex); // Wed
        assertEquals(4, s.busiestDayCount);
        assertEquals(5, s.freeWindowsCount); // 2 on Mon + 3 on Wed
        assertEquals(7, s.maxLessonSlot);
        assertEquals(1, s.holidayDaysCount);
    }

    @Test
    public void computeSummary_ignoresCancelledExcludedAndSkipped() {
        LocalDate mon = LocalDate.of(2026, 11, 16);
        WeekSchedule ws = new WeekSchedule(12, true);

        ScheduleItem active = lesson(1, 1, "Алгоритмы", Constants.LESSON_TYPE_LECTURE);
        ScheduleItem cancelled = lesson(1, 2, "История", Constants.LESSON_TYPE_PRACTICE);
        cancelled.setCancelled(true);
        ScheduleItem excluded = lesson(1, 3, "Физкультура", Constants.LESSON_TYPE_PRACTICE);
        ScheduleItem skipped = lesson(1, 4, "Физика", Constants.LESSON_TYPE_LAB);

        DaySchedule d0 = new DaySchedule(1, mon);
        d0.setLessons(Arrays.asList(active, cancelled, excluded, skipped));
        ws.setDays(Collections.singletonList(d0));

        Set<String> excludedSet = new HashSet<>(Collections.singletonList("Физкультура"));
        WeekGridHelper.WeekSummary s = WeekGridHelper.computeSummary(
                ws,
                excludedSet,
                (date, num, subj) -> num == 4 && "Физика".equals(subj)
        );

        assertEquals(1, s.totalActiveLessons);
        assertEquals(0, s.busiestDayIndex);
        assertEquals(1, s.busiestDayCount);
        assertEquals(0, s.freeWindowsCount);
        assertEquals(6, s.maxLessonSlot);
    }

    @Test
    public void pickPrimaryLesson_prefersNonCancelled() {
        ScheduleItem cancelled = lesson(1, 2, "Отменённая", Constants.LESSON_TYPE_LECTURE);
        cancelled.setCancelled(true);
        ScheduleItem replacement = lesson(1, 2, "Замена", Constants.LESSON_TYPE_PRACTICE);

        ScheduleItem picked = WeekGridHelper.pickPrimaryLesson(Arrays.asList(cancelled, replacement));
        assertEquals("Замена", picked.getSubjectName());

        ScheduleItem onlyCancelled = WeekGridHelper.pickPrimaryLesson(Collections.singletonList(cancelled));
        assertEquals("Отменённая", onlyCancelled.getSubjectName());

        assertNull(WeekGridHelper.pickPrimaryLesson(Collections.emptyList()));
    }

    @Test
    public void findLessonsForSlot_filtersCorrectly() {
        DaySchedule day = new DaySchedule(1, LocalDate.of(2026, 11, 16));
        day.setLessons(Arrays.asList(
                lesson(1, 1, "A", Constants.LESSON_TYPE_LECTURE),
                lesson(1, 2, "B1", Constants.LESSON_TYPE_PRACTICE),
                lesson(1, 2, "B2", Constants.LESSON_TYPE_LAB)
        ));
        List<ScheduleItem> slot2 = WeekGridHelper.findLessonsForSlot(day, 2);
        assertEquals(2, slot2.size());
        assertEquals(0, WeekGridHelper.findLessonsForSlot(day, 5).size());
    }

    @Test
    public void formatCompactRoom_handlesAllRoomTypes() {
        assertEquals("Г-214", WeekGridHelper.formatCompactRoom("Г-214"));
        assertEquals("1-305", WeekGridHelper.formatCompactRoom(" 1 - 305 "));
        assertEquals("Онлайн", WeekGridHelper.formatCompactRoom("Онлайн (Teams)"));
        assertEquals("Спорт", WeekGridHelper.formatCompactRoom("Большой спортзал"));
        assertEquals("Точка", WeekGridHelper.formatCompactRoom("Точка кипения"));
        assertEquals("Практ.", WeekGridHelper.formatCompactRoom("Предприятие"));
        assertEquals("", WeekGridHelper.formatCompactRoom("   "));
        assertEquals("", WeekGridHelper.formatCompactRoom(null));
    }

    @Test
    public void shortTypeCode_formatsCorrectly() {
        assertEquals("★", WeekGridHelper.shortTypeCode("П", true));
        assertEquals("Экз", WeekGridHelper.shortTypeCode(Constants.LESSON_TYPE_EXAM, false));
        assertEquals("ЛР", WeekGridHelper.shortTypeCode(Constants.LESSON_TYPE_LAB, false));
        assertEquals("", WeekGridHelper.shortTypeCode(null, false));
    }
}
