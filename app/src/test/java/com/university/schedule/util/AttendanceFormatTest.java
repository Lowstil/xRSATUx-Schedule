package com.university.schedule.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AttendanceFormatTest {

    @Test
    public void testFormatSkips() {
        assertEquals("0 Н-ок", AttendanceManager.formatSkips(0));
        assertEquals("1 Н-ка", AttendanceManager.formatSkips(1));
        assertEquals("2 Н-ки", AttendanceManager.formatSkips(2));
        assertEquals("3 Н-ки", AttendanceManager.formatSkips(3));
        assertEquals("4 Н-ки", AttendanceManager.formatSkips(4));
        assertEquals("5 Н-ок", AttendanceManager.formatSkips(5));
        assertEquals("11 Н-ок", AttendanceManager.formatSkips(11));
        assertEquals("12 Н-ок", AttendanceManager.formatSkips(12));
        assertEquals("21 Н-ка", AttendanceManager.formatSkips(21));
        assertEquals("22 Н-ки", AttendanceManager.formatSkips(22));
        assertEquals("25 Н-ок", AttendanceManager.formatSkips(25));
    }

    @Test
    public void testFormatLessonsCount() {
        assertEquals("0 пар", AttendanceManager.formatLessonsCount(0));
        assertEquals("1 пара", AttendanceManager.formatLessonsCount(1));
        assertEquals("2 пары", AttendanceManager.formatLessonsCount(2));
        assertEquals("4 пары", AttendanceManager.formatLessonsCount(4));
        assertEquals("5 пар", AttendanceManager.formatLessonsCount(5));
        assertEquals("11 пар", AttendanceManager.formatLessonsCount(11));
        assertEquals("21 пара", AttendanceManager.formatLessonsCount(21));
    }

    @Test
    public void testSkipBudgetMath() {
        // 20 planned lessons, 30% limit -> max 6 skips
        assertEquals(6, AttendanceManager.computeMaxAllowedSkips(20, 30));
        assertEquals(5, AttendanceManager.computeMaxAllowedSkips(20, 25));
        assertEquals(4, AttendanceManager.computeMaxAllowedSkips(20, 20));

        assertEquals(10, AttendanceManager.computeSkipPercent(2, 20));
        assertEquals(0, AttendanceManager.computeSkipPercent(0, 0));

        // Remaining budget
        assertEquals(4, AttendanceManager.computeRemainingSkipBudget(2, 20, 30));
        assertEquals(1, AttendanceManager.computeRemainingSkipBudget(5, 20, 30));
        assertEquals(0, AttendanceManager.computeRemainingSkipBudget(6, 20, 30));
        assertEquals(-1, AttendanceManager.computeRemainingSkipBudget(7, 20, 30));
    }

    @Test
    public void testEvaluateBudgetHealthAndFormatting() {
        // Safe: 20 planned, 2 missed, 30% limit -> 4 remaining
        assertEquals(
                AttendanceManager.BudgetHealth.SAFE,
                AttendanceManager.evaluateBudgetHealth(2, 20, 30)
        );
        assertEquals(
                "Можно пропустить ещё 4 пары",
                AttendanceManager.formatBudgetStatus(2, 20, 30)
        );

        // Warning: 1 remaining
        assertEquals(
                AttendanceManager.BudgetHealth.WARNING,
                AttendanceManager.evaluateBudgetHealth(5, 20, 30)
        );
        assertEquals(
                "Последний запас: 1 пара",
                AttendanceManager.formatBudgetStatus(5, 20, 30)
        );

        // Warning: exactly at limit (0 remaining, >0 missed)
        assertEquals(
                AttendanceManager.BudgetHealth.WARNING,
                AttendanceManager.evaluateBudgetHealth(6, 20, 30)
        );
        assertEquals(
                "Запас исчерпан (30%)",
                AttendanceManager.formatBudgetStatus(6, 20, 30)
        );

        // Exceeded: 7 missed out of 20 (35% > 30%)
        assertEquals(
                AttendanceManager.BudgetHealth.EXCEEDED,
                AttendanceManager.evaluateBudgetHealth(7, 20, 30)
        );
        assertEquals(
                "Лимит превышен! (35%)",
                AttendanceManager.formatBudgetStatus(7, 20, 30)
        );

        // Empty subject
        assertEquals(
                AttendanceManager.BudgetHealth.SAFE,
                AttendanceManager.evaluateBudgetHealth(0, 0, 30)
        );
        assertEquals(
                "Нет проведённых пар",
                AttendanceManager.formatBudgetStatus(0, 0, 30)
        );
    }
}
