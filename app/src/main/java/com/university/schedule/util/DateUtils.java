package com.university.schedule.util;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;
public final class DateUtils {
    private DateUtils() { }
    private static final Locale RU = new Locale("ru");
    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DISPLAY_DATE_SHORT = DateTimeFormatter.ofPattern("dd.MM");
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
    public static String formatDisplayDateShort(LocalDate date) { return date == null ? "" : date.format(DISPLAY_DATE_SHORT); }
    /**
     * "Сегодня" по часам УСТРОЙСТВА — единая точка правды для всего
     * приложения. Раньше здесь была жёсткая зона Europe/Moscow, из-за
     * чего подсветка "Идёт сейчас/Следующая" расходилась с временем на
     * экране телефона (особенно при ручной смене даты для проверки).
     */
    public static LocalDate todayMoscow() {
        return LocalDate.now(ZoneId.systemDefault());
    }
    public static java.time.LocalTime nowTimeMoscow() {
        return java.time.LocalTime.now(ZoneId.systemDefault());
    }
    public static String monthNameRu(LocalDate date) {
        String name = date.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, RU);
        if (name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    public static String relativeDayLabel(LocalDate date, LocalDate today) {
        if (date == null || today == null) return null;
        long diff = java.time.temporal.ChronoUnit.DAYS.between(today, date);
        if (diff == 0) return "Сегодня";
        if (diff == 1) return "Завтра";
        if (diff < 0) return null;
        return "через " + diff + " " + pluralDays((int) diff);
    }
    private static String pluralDays(int n) {
        int rem100 = n % 100;
        int rem10 = n % 10;
        if (rem100 >= 11 && rem100 <= 14) return "дней";
        if (rem10 == 1) return "день";
        if (rem10 >= 2 && rem10 <= 4) return "дня";
        return "дней";
    }
    public static final int STALE_DATA_THRESHOLD_DAYS = 7;
    public static String staleDataMessage(LocalDateTime lastUpdated, LocalDateTime now) {
        if (lastUpdated == null || now == null) return null;
        long days = java.time.temporal.ChronoUnit.DAYS.between(lastUpdated, now);
        if (days < STALE_DATA_THRESHOLD_DAYS) return null;
        return "Расписание не обновлялось " + days + " " + pluralDays((int) days)
                + ". Данные могут быть неактуальны.";
    }
    public static final int AUTO_REFRESH_INTERVAL_HOURS = 24;
    public static boolean isAutoRefreshDue(LocalDateTime lastSuccessOrAttempt, LocalDateTime now) {
        if (lastSuccessOrAttempt == null || now == null) return true;
        long hours = java.time.temporal.ChronoUnit.HOURS.between(lastSuccessOrAttempt, now);
        return hours >= AUTO_REFRESH_INTERVAL_HOURS;
    }
}