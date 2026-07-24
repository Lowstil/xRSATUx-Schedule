package com.university.schedule.model;

import java.util.Objects;

/** Праздничный/выходной день РФ. year=0 — повторяется ежегодно. */
public class Holiday {
    private final int month;
    private final int day;
    private final String name;
    private final int year;

    public Holiday(int month, int day, String name) { this(month, day, name, 0); }

    public Holiday(int month, int day, String name, int year) {
        this.month = month; this.day = day; this.name = name; this.year = year;
    }

    public int getMonth() { return month; }
    public int getDay() { return day; }
    public String getName() { return name; }
    public int getYear() { return year; }

    public boolean matchesYear(int candidateYear) { return year == 0 || year == candidateYear; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Holiday)) return false;
        Holiday h = (Holiday) o;
        return month == h.month && day == h.day && year == h.year;
    }

    @Override
    public int hashCode() { return Objects.hash(month, day, year); }
}