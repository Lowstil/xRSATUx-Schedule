package com.university.schedule.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Расписание на один день с учётом даты и признака выходного/праздника. */
public class DaySchedule {
    private int dayOfWeek;
    private LocalDate date;
    private boolean dayOff;
    private String holidayName;
    private List<ScheduleItem> lessons = new ArrayList<>();

    public DaySchedule() { }

    public DaySchedule(int dayOfWeek, LocalDate date) {
        this.dayOfWeek = dayOfWeek; this.date = date;
    }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public boolean isDayOff() { return dayOff; }
    public void setDayOff(boolean dayOff) { this.dayOff = dayOff; }

    public String getHolidayName() { return holidayName; }
    public void setHolidayName(String holidayName) { this.holidayName = holidayName; }

    public List<ScheduleItem> getLessons() { return lessons; }
    public void setLessons(List<ScheduleItem> lessons) { this.lessons = lessons; }

    public boolean hasLessons() { return lessons != null && !lessons.isEmpty(); }
}