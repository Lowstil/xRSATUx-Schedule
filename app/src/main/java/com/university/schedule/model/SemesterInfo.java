package com.university.schedule.model;

import java.time.LocalDate;

/** Информация о семестре: дата начала (понедельник 1-й недели) и длина = 18 недель. */
public class SemesterInfo {
    public static final int TOTAL_WEEKS = 18;

    private LocalDate startDate;

    public SemesterInfo(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    /** Суббота 18-й недели. */
    public LocalDate getEndDate() {
        return startDate.plusWeeks(TOTAL_WEEKS - 1L).plusDays(5);
    }
}