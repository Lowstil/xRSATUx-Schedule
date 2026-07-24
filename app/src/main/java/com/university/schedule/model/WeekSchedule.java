package com.university.schedule.model;

import java.util.ArrayList;
import java.util.List;

/** Расписание на учебную неделю (ПН-СБ). */
public class WeekSchedule {
    private int weekNumber;
    private boolean even;
    private List<DaySchedule> days = new ArrayList<>();

    public WeekSchedule() { }

    public WeekSchedule(int weekNumber, boolean even) {
        this.weekNumber = weekNumber; this.even = even;
    }

    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }

    public boolean isEven() { return even; }
    public void setEven(boolean even) { this.even = even; }

    public List<DaySchedule> getDays() { return days; }
    public void setDays(List<DaySchedule> days) { this.days = days; }
}