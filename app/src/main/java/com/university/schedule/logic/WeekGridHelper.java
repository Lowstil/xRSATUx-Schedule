package com.university.schedule.logic;

import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.util.Constants;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure-Java data and layout logic for the Week Grid (ПН–СБ) overview.
 * Kept Android-free for fast JUnit testing.
 */
public final class WeekGridHelper {

    public static final int DEFAULT_MAX_SLOTS = 6;
    public static final int ABSOLUTE_MAX_SLOTS = 7;

    @FunctionalInterface
    public interface SkippedPredicate {
        boolean isSkipped(LocalDate date, int lessonNumber, String subjectName);
    }

    public static final class WeekSummary {
        public final int totalActiveLessons;
        public final int busiestDayIndex; // 0..5, or -1 if none
        public final int busiestDayCount;
        public final int freeWindowsCount;
        public final int maxLessonSlot; // 6 or 7
        public final int holidayDaysCount;

        public WeekSummary(
                int totalActiveLessons,
                int busiestDayIndex,
                int busiestDayCount,
                int freeWindowsCount,
                int maxLessonSlot,
                int holidayDaysCount
        ) {
            this.totalActiveLessons = totalActiveLessons;
            this.busiestDayIndex = busiestDayIndex;
            this.busiestDayCount = busiestDayCount;
            this.freeWindowsCount = freeWindowsCount;
            this.maxLessonSlot = maxLessonSlot;
            this.holidayDaysCount = holidayDaysCount;
        }
    }

    private WeekGridHelper() {
    }

    public static boolean isLessonActiveForCount(
            ScheduleItem item,
            Set<String> excludedSubjects,
            boolean isSkipped
    ) {
        if (item == null || item.isCancelled() || isSkipped) {
            return false;
        }
        String subj = item.getSubjectName();
        if (excludedSubjects != null && subj != null && excludedSubjects.contains(subj.trim())) {
            return false;
        }
        return true;
    }

    public static WeekSummary computeSummary(
            WeekSchedule week,
            Set<String> excludedSubjects,
            SkippedPredicate skippedPredicate
    ) {
        if (week == null || week.getDays() == null || week.getDays().isEmpty()) {
            return new WeekSummary(0, -1, 0, 0, DEFAULT_MAX_SLOTS, 0);
        }

        int totalActive = 0;
        int busiestIdx = -1;
        int busiestCount = 0;
        int totalWindows = 0;
        int maxSlot = DEFAULT_MAX_SLOTS;
        int holidays = 0;

        List<DaySchedule> days = week.getDays();
        int dayLimit = Math.min(days.size(), 6);

        for (int dayIdx = 0; dayIdx < dayLimit; dayIdx++) {
            DaySchedule day = days.get(dayIdx);
            if (day == null) continue;
            if (day.isDayOff()) {
                holidays++;
                continue;
            }
            List<ScheduleItem> lessons = day.getLessons();
            if (lessons == null || lessons.isEmpty()) continue;

            LocalDate date = day.getDate();
            boolean[] activeSlots = new boolean[ABSOLUTE_MAX_SLOTS + 1];
            int dayActiveCount = 0;
            int firstActiveSlot = Integer.MAX_VALUE;
            int lastActiveSlot = -1;

            for (ScheduleItem item : lessons) {
                if (item == null) continue;
                int slot = item.getLessonNumber();
                if (slot > maxSlot && slot <= ABSOLUTE_MAX_SLOTS) {
                    maxSlot = slot;
                }
                boolean skipped = skippedPredicate != null
                        && date != null
                        && skippedPredicate.isSkipped(date, slot, item.getSubjectName());
                if (isLessonActiveForCount(item, excludedSubjects, skipped)) {
                    if (slot >= 1 && slot <= ABSOLUTE_MAX_SLOTS) {
                        if (!activeSlots[slot]) {
                            activeSlots[slot] = true;
                            dayActiveCount++;
                        }
                        if (slot < firstActiveSlot) firstActiveSlot = slot;
                        if (slot > lastActiveSlot) lastActiveSlot = slot;
                    } else {
                        dayActiveCount++;
                    }
                }
            }

            totalActive += dayActiveCount;
            if (dayActiveCount > busiestCount) {
                busiestCount = dayActiveCount;
                busiestIdx = dayIdx;
            }

            if (firstActiveSlot < lastActiveSlot) {
                for (int s = firstActiveSlot + 1; s < lastActiveSlot; s++) {
                    if (!activeSlots[s]) {
                        totalWindows++;
                    }
                }
            }
        }

        return new WeekSummary(totalActive, busiestIdx, busiestCount, totalWindows, maxSlot, holidays);
    }

    public static List<ScheduleItem> findLessonsForSlot(DaySchedule day, int slotNumber) {
        List<ScheduleItem> result = new ArrayList<>();
        if (day == null || day.getLessons() == null) {
            return result;
        }
        for (ScheduleItem item : day.getLessons()) {
            if (item != null && item.getLessonNumber() == slotNumber) {
                result.add(item);
            }
        }
        return result;
    }

    public static ScheduleItem pickPrimaryLesson(List<ScheduleItem> slotLessons) {
        if (slotLessons == null || slotLessons.isEmpty()) {
            return null;
        }
        for (ScheduleItem item : slotLessons) {
            if (item != null && !item.isCancelled()) {
                return item;
            }
        }
        return slotLessons.get(0);
    }

    /**
     * Formats room strings into compact badges that fit cleanly in a narrow 6-column cell.
     */
    public static String formatCompactRoom(String rawRoom) {
        if (rawRoom == null) return "";
        String s = rawRoom.trim();
        if (s.isEmpty()) return "";
        String low = s.toLowerCase(Locale.ROOT);
        if (low.contains("онлайн")) return "Онлайн";
        if (low.contains("спортзал")) return "Спорт";
        if (low.contains("точка кипения")) return "Точка";
        if (low.contains("предприят")) return "Практ.";
        return s.replaceAll("\\s+", "");
    }

    /**
     * Short 1–3 char code for compact type pill or accessibility label.
     */
    public static String shortTypeCode(String lessonType, boolean isCustom) {
        if (isCustom) return "★";
        if (lessonType == null) return "";
        String t = lessonType.trim();
        if (t.isEmpty()) return "";
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_EXAM)) return "Экз";
        return t;
    }
}
