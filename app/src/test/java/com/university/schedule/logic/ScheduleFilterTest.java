package com.university.schedule.logic;

import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.util.Constants;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ScheduleFilterTest {

    private ScheduleFilter filter;

    @Before
    public void setUp() {
        filter = new ScheduleFilter(new HolidayChecker());
    }

    @Test
    public void buildDaySchedule_sortsLessonsByNumber_whenUnsorted() {
        LocalDate monday = LocalDate.of(2026, 11, 2); // Week 10 Even
        List<ScheduleItem> items = new ArrayList<>();

        // Add lesson 3 first
        ScheduleItem item3 = new ScheduleItem();
        item3.setDayOfWeek(1);
        item3.setLessonNumber(3);
        item3.setSubjectName("Физкультура");
        item3.setWeekType(Constants.WEEK_TYPE_EVEN);
        item3.setWeekSpec("1-17");
        items.add(item3);

        // Add lesson 2 second
        ScheduleItem item2 = new ScheduleItem();
        item2.setDayOfWeek(1);
        item2.setLessonNumber(2);
        item2.setSubjectName("Базы данных");
        item2.setWeekType(Constants.WEEK_TYPE_EVEN);
        item2.setWeekSpec("2-17");
        items.add(item2);

        DaySchedule day = filter.buildDaySchedule(items, 1, 10, Constants.WEEK_TYPE_EVEN, monday);

        assertFalse(day.isDayOff());
        assertEquals(2, day.getLessons().size());
        assertEquals(2, day.getLessons().get(0).getLessonNumber());
        assertEquals("Базы данных", day.getLessons().get(0).getSubjectName());
        assertEquals(3, day.getLessons().get(1).getLessonNumber());
        assertEquals("Физкультура", day.getLessons().get(1).getSubjectName());
    }

    @Test
    public void buildDaySchedule_filtersOutDifferentWeekType() {
        LocalDate monday = LocalDate.of(2026, 11, 2); // Week 10 Even
        List<ScheduleItem> items = new ArrayList<>();

        ScheduleItem itemOdd = new ScheduleItem();
        itemOdd.setDayOfWeek(1);
        itemOdd.setLessonNumber(2);
        itemOdd.setSubjectName("Базы данных (нечетная)");
        itemOdd.setWeekType(Constants.WEEK_TYPE_ODD);
        itemOdd.setWeekSpec("1-17");
        items.add(itemOdd);

        ScheduleItem itemEven = new ScheduleItem();
        itemEven.setDayOfWeek(1);
        itemEven.setLessonNumber(2);
        itemEven.setSubjectName("Базы данных (четная)");
        itemEven.setWeekType(Constants.WEEK_TYPE_EVEN);
        itemEven.setWeekSpec("2-17");
        items.add(itemEven);

        DaySchedule day = filter.buildDaySchedule(items, 1, 10, Constants.WEEK_TYPE_EVEN, monday);

        assertEquals(1, day.getLessons().size());
        assertEquals("Базы данных (четная)", day.getLessons().get(0).getSubjectName());
    }
}
