package com.university.schedule.util;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DateUtilsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 20, 12, 0);

    @Test
    public void freshDataProducesNoWarning() {
        LocalDateTime updated = NOW.minusDays(1);
        assertNull(DateUtils.staleDataMessage(updated, NOW));
    }

    @Test
    public void exactlyAtThresholdProducesWarning() {
        LocalDateTime updated = NOW.minusDays(DateUtils.STALE_DATA_THRESHOLD_DAYS);
        assertNotNull(DateUtils.staleDataMessage(updated, NOW));
    }

    @Test
    public void oneDayBeforeThresholdProducesNoWarning() {
        LocalDateTime updated = NOW.minusDays(DateUtils.STALE_DATA_THRESHOLD_DAYS - 1);
        assertNull(DateUtils.staleDataMessage(updated, NOW));
    }

    @Test
    public void veryOldDataProducesWarningWithCorrectPluralForm() {
        LocalDateTime updated = NOW.minusDays(21);
        String msg = DateUtils.staleDataMessage(updated, NOW);
        assertNotNull(msg);
        assertTrue(msg.contains("21 день"));
    }

    @Test
    public void nullLastUpdatedProducesNoWarning() {
        assertNull(DateUtils.staleDataMessage(null, NOW));
    }

    @Test
    public void relativeDayLabelsAreCorrect() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 29);
        org.junit.Assert.assertEquals("Сегодня", DateUtils.relativeDayLabel(today, today));
        org.junit.Assert.assertEquals("Завтра", DateUtils.relativeDayLabel(today.plusDays(1), today));
        org.junit.Assert.assertEquals("Наступит через 2 дня", DateUtils.relativeDayLabel(today.plusDays(2), today));
        org.junit.Assert.assertEquals("Наступит через 5 дней", DateUtils.relativeDayLabel(today.plusDays(5), today));
        assertNull(DateUtils.relativeDayLabel(today.minusDays(1), today));
    }
}
