package com.university.schedule.widget;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.Assert.assertEquals;

public class CompactWidgetTickCalculatorTest {

    // 2026-10-12 is Monday, 2026-10-17 is Saturday, 2026-10-18 is Sunday
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 17);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 18);

    @Test
    public void liveLessonReturns30Seconds() {
        long delay = CompactWidgetTickCalculator.computeNextTickDelayMs(
                MONDAY, LocalTime.of(9, 10), true, LocalTime.of(10, 15));
        assertEquals(30_000L, delay);
    }

    @Test
    public void lessonStartingWithin15MinutesReturns30Seconds() {
        // 10 minutes before 08:30
        long delay10 = CompactWidgetTickCalculator.computeNextTickDelayMs(
                MONDAY, LocalTime.of(8, 20), false, LocalTime.of(8, 30));
        assertEquals(30_000L, delay10);

        // Exact 15-minute boundary
        long delay15 = CompactWidgetTickCalculator.computeNextTickDelayMs(
                MONDAY, LocalTime.of(8, 15), false, LocalTime.of(8, 30));
        assertEquals(30_000L, delay15);
    }

    @Test
    public void breakBetweenLessonsReturns60Seconds() {
        // 45-minute lunch break before 12:40
        long delay = CompactWidgetTickCalculator.computeNextTickDelayMs(
                MONDAY, LocalTime.of(11, 55), false, LocalTime.of(12, 40));
        assertEquals(60_000L, delay);
    }

    @Test
    public void distantLessonLaterTodayCapsSleepAt30Minutes() {
        // 08:30 when first lesson is at 14:25
        long delay = CompactWidgetTickCalculator.computeNextTickDelayMs(
                MONDAY, LocalTime.of(8, 30), false, LocalTime.of(14, 25));
        assertEquals(30 * 60_000L, delay);
    }

    @Test
    public void afterLastLessonOnWeekdaySleepsUntilNextMorning0815() {
        // Monday 21:30 -> Tuesday 08:15 = 10h 45m
        long delay = CompactWidgetTickCalculator.computeNextTickDelayMs(
                MONDAY, LocalTime.of(21, 30), false, null);
        long expected = Duration.ofHours(10).plusMinutes(45).toMillis();
        assertEquals(expected, delay);
    }

    @Test
    public void afterLastLessonOnSaturdaySkipsSundayToMonday0815() {
        // Saturday 20:30 -> Monday 08:15 = 35h 45m
        long delay = CompactWidgetTickCalculator.computeNextTickDelayMs(
                SATURDAY, LocalTime.of(20, 30), false, null);
        long expected = Duration.ofHours(35).plusMinutes(45).toMillis();
        assertEquals(expected, delay);
    }

    @Test
    public void sundaySleepsUntilMonday0815() {
        // Sunday 12:00 -> Monday 08:15 = 20h 15m
        long delay = CompactWidgetTickCalculator.computeNextTickDelayMs(
                SUNDAY, LocalTime.of(12, 0), false, null);
        long expected = Duration.ofHours(20).plusMinutes(15).toMillis();
        assertEquals(expected, delay);
    }

    @Test
    public void fallbackWindowBasedCalculationWorksForActiveAndIdleHours() {
        // Inside weekday active window
        assertEquals(30_000L,
                CompactWidgetTickCalculator.computeNextTickDelayMs(MONDAY, LocalTime.of(8, 15)));
        assertEquals(30_000L,
                CompactWidgetTickCalculator.computeNextTickDelayMs(MONDAY, LocalTime.of(14, 0)));
        assertEquals(30_000L,
                CompactWidgetTickCalculator.computeNextTickDelayMs(MONDAY, LocalTime.of(21, 0)));

        // Early morning Monday 07:00 -> 1h 15m until 08:15
        assertEquals(Duration.ofHours(1).plusMinutes(15).toMillis(),
                CompactWidgetTickCalculator.computeNextTickDelayMs(MONDAY, LocalTime.of(7, 0)));

        // Saturday 20:30 (past 20:15 last lesson) -> Monday 08:15 = 35h 45m
        assertEquals(Duration.ofHours(35).plusMinutes(45).toMillis(),
                CompactWidgetTickCalculator.computeNextTickDelayMs(SATURDAY, LocalTime.of(20, 30)));

        // Sunday 15:00 -> Monday 08:15 = 17h 15m
        assertEquals(Duration.ofHours(17).plusMinutes(15).toMillis(),
                CompactWidgetTickCalculator.computeNextTickDelayMs(SUNDAY, LocalTime.of(15, 0)));
    }

    @Test
    public void nullArgumentsReturnSafeIdleFallback() {
        assertEquals(CompactWidgetTickCalculator.IDLE_MAX_SLEEP_MS,
                CompactWidgetTickCalculator.computeNextTickDelayMs(null, LocalTime.of(10, 0), false, null));
        assertEquals(CompactWidgetTickCalculator.IDLE_MAX_SLEEP_MS,
                CompactWidgetTickCalculator.computeNextTickDelayMs(MONDAY, null));
    }
}
