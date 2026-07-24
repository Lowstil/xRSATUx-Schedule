package com.university.schedule.logic;

import com.university.schedule.model.SemesterInfo;
import com.university.schedule.util.DateUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Управление информацией о семестре: дефолтная дата начала, валидация, описание.
 * Дефолт — эвристика: месяц >= 9 -> осенний (первый понедельник сентября),
 * иначе весенний (первый понедельник февраля). Реальную дату пользователь
 * задаёт в настройках (SettingsActivity).
 */
public class SemesterManager {

    public static LocalDate getDefaultSemesterStart() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        LocalDate ref = now.getMonthValue() >= 9
                ? LocalDate.of(year, 9, 1)
                : LocalDate.of(year, 2, 1);
        LocalDate monday = DateUtils.mondayOfWeek(ref);
        if (monday.isBefore(ref)) monday = monday.plusWeeks(1);
        return monday;
    }

    public static SemesterInfo createSemesterInfo(LocalDate startDate) {
        return new SemesterInfo(startDate);
    }

    /** Дата начала обязана быть понедельником. */
    public static boolean isValidSemesterStart(LocalDate date) {
        return date != null && date.getDayOfWeek() == DayOfWeek.MONDAY;
    }

    public static String describeSemester(SemesterInfo info) {
        if (info == null || info.getStartDate() == null) return "Семестр не задан";
        LocalDate start = info.getStartDate();
        LocalDate end = info.getEndDate();
        int m = start.getMonthValue();
        String name = (m >= 9 || m <= 1)
                ? "Осенний семестр " + start.getYear() + "/" + (start.getYear() + 1)
                : "Весенний семестр " + start.getYear();
        return name + ", недели 1-" + SemesterInfo.TOTAL_WEEKS
                + " (" + DateUtils.formatDisplayDate(start) + " - "
                + DateUtils.formatDisplayDate(end) + ")";
    }
}