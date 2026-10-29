package com.university.schedule.util;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoRefreshDueTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 20, 12, 0);

    @Test
    public void neverUpdatedBefore_isDue() {
        assertTrue(DateUtils.isAutoRefreshDue(null, NOW));
    }

    @Test
    public void updatedOneHourAgo_isNotDue() {
        assertFalse(DateUtils.isAutoRefreshDue(NOW.minusHours(1), NOW));
    }

    @Test
    public void updatedExactlyAtThreshold_isDue() {
        assertTrue(DateUtils.isAutoRefreshDue(NOW.minusHours(DateUtils.AUTO_REFRESH_INTERVAL_HOURS), NOW));
    }

    @Test
    public void updatedOneHourBeforeThreshold_isNotDue() {
        assertFalse(DateUtils.isAutoRefreshDue(NOW.minusHours(DateUtils.AUTO_REFRESH_INTERVAL_HOURS - 1), NOW));
    }

    @Test
    public void updatedSeveralDaysAgo_isDue() {
        assertTrue(DateUtils.isAutoRefreshDue(NOW.minusDays(5), NOW));
    }
}
