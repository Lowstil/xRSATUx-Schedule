package com.university.schedule.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AttendanceFormatTest {

    @Test
    public void testFormatSkips() {
        assertEquals("0 Н-ок", AttendanceManager.formatSkips(0));
        assertEquals("1 Н-ка", AttendanceManager.formatSkips(1));
        assertEquals("2 Н-ки", AttendanceManager.formatSkips(2));
        assertEquals("3 Н-ки", AttendanceManager.formatSkips(3));
        assertEquals("4 Н-ки", AttendanceManager.formatSkips(4));
        assertEquals("5 Н-ок", AttendanceManager.formatSkips(5));
        assertEquals("11 Н-ок", AttendanceManager.formatSkips(11));
        assertEquals("12 Н-ок", AttendanceManager.formatSkips(12));
        assertEquals("21 Н-ка", AttendanceManager.formatSkips(21));
        assertEquals("22 Н-ки", AttendanceManager.formatSkips(22));
        assertEquals("25 Н-ок", AttendanceManager.formatSkips(25));
    }
}
