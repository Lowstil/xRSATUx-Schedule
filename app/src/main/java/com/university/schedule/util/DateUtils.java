package com.university.schedule.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Утилиты дат. java.time работает на API 24+ благодаря desugaring. */
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
        try { return LocalDate.parse(iso, ISO_DATE); }
        catch (DateTimeParseException e) { return null; }
    }

    public static String nowIsoDateTime() {
        return LocalDateTime.now().format(ISO_DATE_TIME);
    }

    public static LocalDateTime parseIsoDateTime(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try { return LocalDateTime.parse(iso, ISO_DATE_TIME); }
        catch (DateTimeParseException e) { return null; }
    }

    /** День недели в наш формат 1=ПН..6=СБ; воскресенье = 0. */
    public static int toScheduleDayOfWeek(LocalDate date) {
        int v = date.getDayOfWeek().getValue();
        return v == 7 ? 0 : v;
    }

    public static LocalDate dateForDayInWeek(LocalDate mondayOfWeek, int dayOfWeek) {
        return mondayOfWeek.plusDays(dayOfWeek - 1L);
    }

    public static LocalDate mondayOfWeek(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    public static String formatDisplayDate(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY_DATE);
    }

    /** "Сегодня" по московскому времени (сверка с Москвой). */
    public static LocalDate todayMoscow() {
        return LocalDate.now(ZoneId.of(Constants.MOSCOW_ZONE));
    }

    /** Текущее время по московскому времени. */
    public static LocalTime nowTimeMoscow() {
        return LocalTime.now(ZoneId.of(Constants.MOSCOW_ZONE));
    }
}