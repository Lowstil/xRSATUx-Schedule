package com.university.schedule.widget;

import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Чистая логика расчёта задержки до следующего тика компактного 2x1 виджета:
 * - Во время активной пары или за 15 минут до её начала: 30 секунд.
 * - На перемене между парами (15..50 мин до следующей): 60 секунд.
 * - Если до ближайшей пары сегодня > 50 мин: спим до 30 мин (или до окна T-15 мин).
 * - Вне учебных часов (после последней пары, выходной, воскресенье, ночь):
 *   спим напрямую до 08:15 следующего учебного дня, не расходуя батарею ночью.
 */
public final class CompactWidgetTickCalculator {

    public static final long LIVE_TICK_INTERVAL_MS = 30_000L;
    public static final long BREAK_TICK_INTERVAL_MS = 60_000L;
    public static final long IDLE_MAX_SLEEP_MS = 30 * 60_000L;
    public static final int PRE_LESSON_LEAD_MINUTES = 15;
    public static final int BREAK_WINDOW_MINUTES = 50;

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");

    private CompactWidgetTickCalculator() {
    }

    /**
     * Вычисляет задержку (в мс) до следующего обновления с учётом фактического расписания на сегодня.
     */
    public static long computeNextTickDelayMs(LocalDate today,
                                              LocalTime now,
                                              boolean hasLiveLesson,
                                              LocalTime nextLessonStartToday) {
        if (today == null || now == null) {
            return IDLE_MAX_SLEEP_MS;
        }
        if (hasLiveLesson) {
            return LIVE_TICK_INTERVAL_MS;
        }
        if (nextLessonStartToday != null) {
            long msUntilNext = Duration.between(now, nextLessonStartToday).toMillis();
            if (msUntilNext <= PRE_LESSON_LEAD_MINUTES * 60_000L) {
                return LIVE_TICK_INTERVAL_MS;
            }
            if (msUntilNext <= BREAK_WINDOW_MINUTES * 60_000L) {
                return BREAK_TICK_INTERVAL_MS;
            }
            long msUntilPreWindow = msUntilNext - PRE_LESSON_LEAD_MINUTES * 60_000L;
            return Math.max(LIVE_TICK_INTERVAL_MS, Math.min(IDLE_MAX_SLEEP_MS, msUntilPreWindow));
        }

        return computeDelayUntilNextStudyWindow(today, now);
    }

    /**
     * Запасной расчёт только по времени суток и расписанию звонков (когда расписание группы
     * ещё не загружено). В учебное окно (08:15..21:00 будни, 08:15..20:15 сб) возвращает 30 сек,
     * вне его — сон до 08:15 ближайшего учебного дня.
     */
    public static long computeNextTickDelayMs(LocalDate today, LocalTime now) {
        if (today == null || now == null) {
            return IDLE_MAX_SLEEP_MS;
        }
        int dow = DateUtils.toScheduleDayOfWeek(today);
        if (dow >= 1 && dow <= 6) {
            String[][] times = Constants.getLessonTimes(dow);
            if (times != null && times.length > 0) {
                LocalTime firstStart = LocalTime.parse(times[0][0], TF);
                LocalTime lastEnd = LocalTime.parse(times[times.length - 1][1], TF);
                LocalTime windowStart = firstStart.minusMinutes(PRE_LESSON_LEAD_MINUTES);

                if (!now.isBefore(windowStart) && !now.isAfter(lastEnd)) {
                    return LIVE_TICK_INTERVAL_MS;
                }
                if (now.isBefore(windowStart)) {
                    long delayMs = Duration.between(now, windowStart).toMillis();
                    return Math.max(LIVE_TICK_INTERVAL_MS, delayMs);
                }
            }
        }
        return computeDelayUntilNextStudyWindow(today, now);
    }

    private static long computeDelayUntilNextStudyWindow(LocalDate today, LocalTime now) {
        LocalDate nextStudyDate = findNextStudyDate(today);
        if (nextStudyDate == null) {
            return IDLE_MAX_SLEEP_MS;
        }
        int nextDow = DateUtils.toScheduleDayOfWeek(nextStudyDate);
        String[][] times = Constants.getLessonTimes(nextDow);
        LocalTime firstStart = (times != null && times.length > 0)
                ? LocalTime.parse(times[0][0], TF)
                : LocalTime.of(8, 30);
        LocalTime wakeTime = firstStart.minusMinutes(PRE_LESSON_LEAD_MINUTES);

        LocalDateTime nowDt = LocalDateTime.of(today, now);
        LocalDateTime wakeDt = LocalDateTime.of(nextStudyDate, wakeTime);
        long delayMs = Duration.between(nowDt, wakeDt).toMillis();
        return Math.max(LIVE_TICK_INTERVAL_MS, delayMs);
    }

    private static LocalDate findNextStudyDate(LocalDate fromDate) {
        for (int offset = 1; offset <= 7; offset++) {
            LocalDate candidate = fromDate.plusDays(offset);
            int dow = DateUtils.toScheduleDayOfWeek(candidate);
            if (dow >= 1 && dow <= 6) {
                return candidate;
            }
        }
        return null;
    }
}
