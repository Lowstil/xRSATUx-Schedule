package com.university.schedule.logic;

import com.university.schedule.model.SemesterInfo;
import com.university.schedule.util.DateUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Расчёт номера учебной недели и её чётности по дате начала семестра.
 *
 * Неделя 1 стартует в понедельник = semesterInfo.getStartDate().
 * Всего 18 недель. Нечётные: 1,3,5..17; чётные: 2,4,6..18.
 * Дата до начала семестра или после 18-й недели -> номер = -1 (вне семестра).
 */
public class WeekCalculator {

    private final SemesterInfo semesterInfo;

    public WeekCalculator(SemesterInfo semesterInfo) {
        this.semesterInfo = semesterInfo;
    }

    /** Номер учебной недели (1..18) для даты; -1 если вне семестра. */
    public int getWeekNumber(LocalDate date) {
        if (date == null || semesterInfo == null || semesterInfo.getStartDate() == null) {
            return -1;
        }
        LocalDate start = semesterInfo.getStartDate();
        if (date.isBefore(start)) return -1;
        long days = ChronoUnit.DAYS.between(start, date);
        int week = (int) (days / 7) + 1;
        return week > SemesterInfo.TOTAL_WEEKS ? -1 : week;
    }

    public boolean isEvenWeek(int weekNumber) {
        return weekNumber > 0 && weekNumber % 2 == 0;
    }

    public boolean isOddWeek(int weekNumber) {
        return weekNumber > 0 && weekNumber % 2 != 0;
    }

    /** "odd" / "even" для даты; null если вне семестра. */
    public String getWeekTypeForDate(LocalDate date) {
        int w = getWeekNumber(date);
        if (w < 0) return null;
        return isEvenWeek(w) ? "even" : "odd";
    }

    /** Понедельник заданной учебной недели (1..18); null если номер некорректен. */
    public LocalDate getMondayOfWeek(int weekNumber) {
        if (weekNumber < 1 || weekNumber > SemesterInfo.TOTAL_WEEKS) return null;
        return semesterInfo.getStartDate().plusWeeks(weekNumber - 1L);
    }

    /** Суббота заданной учебной недели. */
    public LocalDate getSaturdayOfWeek(int weekNumber) {
        LocalDate mon = getMondayOfWeek(weekNumber);
        return mon != null ? mon.plusDays(5) : null;
    }

    public int getCurrentWeekNumber() {
        return getWeekNumber(DateUtils.todayMoscow());
    }

    public String getCurrentWeekType() {
        return getWeekTypeForDate(DateUtils.todayMoscow());
    }
}