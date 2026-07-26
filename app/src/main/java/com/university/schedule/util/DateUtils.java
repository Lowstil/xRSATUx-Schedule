package com.university.schedule.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DateUtils {
    private DateUtils() { }

    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static String toIsoString(LocalDate date) {
        return date == null ? null : date.format(ISO_DATE);
    }
    public static LocalDate parseIsoDate(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try { return LocalDate.parse(iso, ISO_DATE); } catch (DateTimeParseException e) { return null; }
    }
    public static String nowIsoDateTime() { return LocalDateTime.now().format(ISO_DATE_TIME); }
    public static LocalDateTime parseIsoDateTime(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try { return LocalDateTime.parse(iso, ISO_DATE_TIME); } catch (DateTimeParseException e) { return null; }
    }
    public static int toScheduleDayOfWeek(LocalDate date) {
        int v = date.getDayOfWeek().getValue();
        return v == 7 ? 0 : v;
    }
    public static LocalDate dateForDayInWeek(LocalDate monday, int dayOfWeek) { return monday.plusDays(dayOfWeek - 1L); }
    public static LocalDate mondayOfWeek(LocalDate date) { return date.minusDays(date.getDayOfWeek().getValue() - 1L); }
    public static String formatDisplayDate(LocalDate date) { return date == null ? "" : date.format(DISPLAY_DATE); }
}