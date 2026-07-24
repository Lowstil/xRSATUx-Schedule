package com.university.schedule.logic;

import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сборка структурированного расписания из плоского списка ScheduleItem
 * с учётом типа недели, диапазона недель (weekSpec), дня и праздников.
 *
 * Фильтрация по группе/преподавателю здесь НЕ делается по подстроке:
 * в БД уже лежат строки с точным заголовком колонки листа, поэтому
 * ScheduleDao возвращает их точным равенством, а здесь мы только
 * раскладываем их по дням и проверяем weekSpec через isActiveOnWeek.
 */
public class ScheduleFilter {

    private final HolidayChecker holidayChecker;

    public ScheduleFilter(HolidayChecker holidayChecker) {
        this.holidayChecker = holidayChecker;
    }

    /** Расписание на учебную неделю (ПН-СБ). */
    public WeekSchedule buildWeekSchedule(List<ScheduleItem> allItems,
                                          int weekNumber,
                                          String weekType,
                                          LocalDate mondayOfWeek) {
        WeekSchedule week = new WeekSchedule(weekNumber, "even".equals(weekType));
        List<DaySchedule> days = new ArrayList<>();
        for (int dow = 1; dow <= 6; dow++) {
            LocalDate date = DateUtils.dateForDayInWeek(mondayOfWeek, dow);
            DaySchedule day = new DaySchedule(dow, date);
            if (holidayChecker.isDayOff(date)) {
                day.setDayOff(true);
                String hn = holidayChecker.getHolidayName(date);
                if (hn != null) day.setHolidayName(hn);
            }
            day.setLessons(filterLessonsForDay(allItems, dow, weekType, weekNumber));
            days.add(day);
        }
        week.setDays(days);
        return week;
    }

    /** Расписание на один день. */
    public DaySchedule buildDaySchedule(List<ScheduleItem> allItems,
                                        int dayOfWeek,
                                        int weekNumber,
                                        String weekType,
                                        LocalDate date) {
        DaySchedule day = new DaySchedule(dayOfWeek, date);
        if (holidayChecker.isDayOff(date)) {
            day.setDayOff(true);
            String hn = holidayChecker.getHolidayName(date);
            if (hn != null) day.setHolidayName(hn);
        }
        day.setLessons(filterLessonsForDay(allItems, dayOfWeek, weekType, weekNumber));
        return day;
    }

    private List<ScheduleItem> filterLessonsForDay(List<ScheduleItem> items,
                                                   int dayOfWeek,
                                                   String weekType,
                                                   int weekNumber) {
        List<ScheduleItem> out = new ArrayList<>();
        if (items == null) return out;
        for (ScheduleItem it : items) {
            if (it.getDayOfWeek() == dayOfWeek
                    && weekType != null && weekType.equals(it.getWeekType())
                    && it.isActiveOnWeek(weekNumber)) {
                out.add(it);
            }
        }
        return out;
    }

    /** Человекочитаемое название типа занятия. */
    public static String formatLessonType(String lessonType) {
        if (lessonType == null) return "";
        switch (lessonType) {
            case Constants.LESSON_TYPE_LECTURE: return "Лекция";
            case Constants.LESSON_TYPE_PRACTICE: return "Практика";
            case Constants.LESSON_TYPE_LAB: return "Лаб. работа";
            case Constants.LESSON_TYPE_ONLINE_LECTURE: return "Лекция (онлайн)";
            case Constants.LESSON_TYPE_ONLINE_PRACTICE: return "Практика (онлайн)";
            case Constants.LESSON_TYPE_EXAM: return "Экзамен";
            default: return lessonType;
        }
    }

    /** Короткая метка типа занятия (для бейджа). */
    public static String shortLessonType(String lessonType) {
        if (lessonType == null) return "";
        switch (lessonType) {
            case Constants.LESSON_TYPE_LECTURE: return "Л";
            case Constants.LESSON_TYPE_PRACTICE: return "П";
            case Constants.LESSON_TYPE_LAB: return "ЛР";
            case Constants.LESSON_TYPE_ONLINE_LECTURE: return "оЛ";
            case Constants.LESSON_TYPE_ONLINE_PRACTICE: return "оП";
            case Constants.LESSON_TYPE_EXAM: return "Экз";
            default: return lessonType;
        }
    }
}