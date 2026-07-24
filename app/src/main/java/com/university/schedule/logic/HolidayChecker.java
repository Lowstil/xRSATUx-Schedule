package com.university.schedule.logic;

import com.university.schedule.model.Holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Проверка выходных и официальных праздничных дней РФ.
 *
 * ВАЖНО по семантике расписания: в этом вузе СУББОТА — учебный день
 * (в файле на субботу реально стоят пары), поэтому выходным по календарю
 * недели считается ТОЛЬКО воскресенье. Праздники, выпавшие на субботу,
 * всё равно помечаются как день отдыха (isDayOff = true).
 *
 * Ежегодный список взят из ст. 112 ТК РФ (фиксированные нерабочие дни).
 * Переносы выходных (YEAR_SPECIFIC_HOLIDAYS) правительство утверждает
 * ежегодно постановлением — ниже приведён правдоподобный набор для
 * 2025-2027; перед релизом его стоит сверить с производственным календарём
 * на publication.pravo.gov.ru. Для учебного расписания это не критично:
 * вуз просто не ставит пары в перенесённые дни, и таких строк в файле нет.
 */
public class HolidayChecker {

    /** Ежегодные нерабочие праздничные дни РФ (ст. 112 ТК РФ). */
    private static final List<Holiday> ANNUAL_HOLIDAYS = Arrays.asList(
            new Holiday(1, 1, "Новогодние каникулы"),
            new Holiday(1, 2, "Новогодние каникулы"),
            new Holiday(1, 3, "Новогодние каникулы"),
            new Holiday(1, 4, "Новогодние каникулы"),
            new Holiday(1, 5, "Новогодние каникулы"),
            new Holiday(1, 6, "Новогодние каникулы"),
            new Holiday(1, 7, "Рождество Христово"),
            new Holiday(1, 8, "Новогодние каникулы"),
            new Holiday(2, 23, "День защитника Отечества"),
            new Holiday(3, 8, "Международный женский день"),
            new Holiday(5, 1, "Праздник Весны и Труда"),
            new Holiday(5, 9, "День Победы"),
            new Holiday(6, 12, "День России"),
            new Holiday(11, 4, "День народного единства")
    );

    /** Переносы выходных / доп. нерабочие дни по годам (пример, сверять ежегодно). */
    private static final List<Holiday> YEAR_SPECIFIC_HOLIDAYS = Arrays.asList(
            // 2025
            new Holiday(5, 2, "Перенос выходного (майские)", 2025),
            new Holiday(6, 13, "Перенос выходного (День России)", 2025),
            new Holiday(11, 3, "Перенос выходного (День народного единства)", 2025),
            new Holiday(12, 31, "Предновогодний нерабочий день", 2025),
            // 2026
            new Holiday(2, 24, "Перенос выходного (23 февраля)", 2026),
            new Holiday(5, 4, "Перенос выходного (майские)", 2026),
            new Holiday(5, 8, "Перенос выходного (майские)", 2026),
            new Holiday(12, 31, "Предновогодний нерабочий день", 2026),
            // 2027
            new Holiday(3, 5, "Перенос выходного (8 марта)", 2027),
            new Holiday(5, 3, "Перенос выходного (майские)", 2027),
            new Holiday(12, 31, "Предновогодний нерабочий день", 2027)
    );

    private final List<Holiday> extraHolidays;

    public HolidayChecker() {
        this.extraHolidays = new ArrayList<>();
    }

    /** Доп. праздники, загруженные из БД (HolidayDao). */
    public HolidayChecker(List<Holiday> additionalHolidays) {
        this.extraHolidays = additionalHolidays != null ? additionalHolidays : new ArrayList<>();
    }

    /** Официальный праздник ли это (с учётом переносов конкретного года). */
    public boolean isHoliday(LocalDate date) {
        return getHolidayName(date) != null;
    }

    /** Выходной по календарю недели. В этом вузе — только воскресенье. */
    public boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** День отдыха = выходной недели ИЛИ праздник. */
    public boolean isDayOff(LocalDate date) {
        return isWeekend(date) || isHoliday(date);
    }

    /** Название праздника для даты или null. */
    public String getHolidayName(LocalDate date) {
        int m = date.getMonthValue();
        int d = date.getDayOfMonth();
        int y = date.getYear();
        for (Holiday h : ANNUAL_HOLIDAYS) {
            if (h.getMonth() == m && h.getDay() == d) return h.getName();
        }
        for (Holiday h : YEAR_SPECIFIC_HOLIDAYS) {
            if (h.getMonth() == m && h.getDay() == d && h.matchesYear(y)) return h.getName();
        }
        for (Holiday h : extraHolidays) {
            if (h.getMonth() == m && h.getDay() == d && h.matchesYear(y)) return h.getName();
        }
        return null;
    }
}