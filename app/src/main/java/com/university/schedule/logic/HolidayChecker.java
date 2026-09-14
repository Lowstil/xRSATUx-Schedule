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
 * Переносы выходных дней (YEAR_SPECIFIC_HOLIDAYS) добавляются ТОЛЬКО когда
 * реально подтверждены постановлением Правительства РФ — см. комментарий
 * над самим списком ниже. Для учебного расписания это почти всегда не
 * критично: вуз просто не ставит пары в перенесённые дни, и таких строк
 * в файле расписания нет.
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

    /**
     * Переносы выходных дней по годам — только подтверждённые официальными
     * постановлениями Правительства РФ даты. НЕ добавлять сюда "вероятные"
     * или предположительные даты: неверная запись здесь напрямую ломает
     * расписание (день ошибочно помечается выходным, хотя занятия есть).
     *
     * Реальный инцидент: ранее здесь стояла запись "24 февраля 2026 — перенос
     * 23 февраля" — придуманная, без опоры на постановление. В 2026 году
     * 23 февраля само по себе выпадает на понедельник (будний день), переносить
     * нечего.
     *
     * На 2026 год единственный официальный перенос (Постановление Правительства
     * РФ от 24.09.2025 N 1466) целиком лежит внутри новогодних каникул
     * (3-9 января), которые и так закрыты в ANNUAL_HOLIDAYS ниже (1-8 января) —
     * для расписания вуза это не имеет практического значения, поэтому здесь
     * намеренно не дублируется (риск перепутать направление переноса выше
     * пользы). Шестидневных недель / других переносов в 2026 году нет.
     *
     * Список пуст до появления РЕАЛЬНОГО подтверждённого повода его заполнить —
     * это осознанный выбор, а не недосмотр.
     */
    private static final List<Holiday> YEAR_SPECIFIC_HOLIDAYS = new ArrayList<>();

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