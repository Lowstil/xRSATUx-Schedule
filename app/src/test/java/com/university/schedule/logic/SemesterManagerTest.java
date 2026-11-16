package com.university.schedule.logic;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;

public class SemesterManagerTest {

    @Test
    public void getCurrentWindowSemester_boundaryDates() {
        assertEquals(SemesterManager.SEMESTER_FALL,
                SemesterManager.getCurrentWindowSemester(LocalDate.of(2027, 1, 29)));
        assertEquals(SemesterManager.SEMESTER_SPRING,
                SemesterManager.getCurrentWindowSemester(LocalDate.of(2027, 1, 30)));
        assertEquals(SemesterManager.SEMESTER_SPRING,
                SemesterManager.getCurrentWindowSemester(LocalDate.of(2027, 2, 1)));
        assertEquals(SemesterManager.SEMESTER_SPRING,
                SemesterManager.getCurrentWindowSemester(LocalDate.of(2027, 8, 24)));
        assertEquals(SemesterManager.SEMESTER_FALL,
                SemesterManager.getCurrentWindowSemester(LocalDate.of(2027, 8, 25)));
        assertEquals(SemesterManager.SEMESTER_FALL,
                SemesterManager.getCurrentWindowSemester(LocalDate.of(2026, 11, 16)));
    }

    @Test
    public void getDefaultStartForSemester_fromNov2026() {
        LocalDate ref = LocalDate.of(2026, 11, 16);
        assertEquals(LocalDate.of(2026, 8, 31),
                SemesterManager.getDefaultStartForSemester(SemesterManager.SEMESTER_FALL, ref));
        assertEquals(LocalDate.of(2027, 2, 1),
                SemesterManager.getDefaultStartForSemester(SemesterManager.SEMESTER_SPRING, ref));
    }

    @Test
    public void getDefaultStartForSemester_fromJan30_2027() {
        LocalDate ref = LocalDate.of(2027, 1, 30);
        assertEquals(LocalDate.of(2026, 8, 31),
                SemesterManager.getDefaultStartForSemester(SemesterManager.SEMESTER_FALL, ref));
        assertEquals(LocalDate.of(2027, 2, 1),
                SemesterManager.getDefaultStartForSemester(SemesterManager.SEMESTER_SPRING, ref));
    }

    @Test
    public void describeSemester_formatsAcademicYear() {
        assertEquals("Осенний семестр 2026/2027",
                SemesterManager.describeSemester(SemesterManager.SEMESTER_FALL, LocalDate.of(2026, 8, 31)));
        assertEquals("Весенний семестр 2026/2027",
                SemesterManager.describeSemester(SemesterManager.SEMESTER_SPRING, LocalDate.of(2027, 2, 1)));
    }
}
