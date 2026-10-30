package com.university.schedule.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Управление точечной посещаемостью пар:
 * - Н-ка (пропуск / не пойду)
 * - Посещено / пойду (посещение)
 * - Без отметки (по умолчанию)
 *
 * Позволяет студенту заранее или по факту отметить статус каждой пары.
 */
public class SkippedLessonsManager {
    public enum AttendanceStatus {
        NONE,
        ATTENDED,
        SKIPPED
    }

    private static final String PREF_SKIPPED = "skipped_lessons_set";
    private static final String PREF_ATTENDED = "attended_lessons_set";
    private final SharedPreferences prefs;

    public SkippedLessonsManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String buildKey(String date, int lessonNumber, String subjectName) {
        return (date != null ? date : "") + "#" + lessonNumber + "#" + (subjectName != null ? subjectName.trim() : "");
    }

    public boolean isSkipped(String date, int lessonNumber, String subjectName) {
        if (date == null) return false;
        Set<String> set = prefs.getStringSet(PREF_SKIPPED, null);
        if (set == null || set.isEmpty()) return false;
        String exactKey = buildKey(date, lessonNumber, subjectName);
        if (set.contains(exactKey)) return true;
        String shortKey = date + "#" + lessonNumber + "#";
        for (String k : set) {
            if (k.startsWith(shortKey)) return true;
        }
        return false;
    }

    public boolean isSkipped(LocalDate date, int lessonNumber, String subjectName) {
        return date != null && isSkipped(date.toString(), lessonNumber, subjectName);
    }

    public boolean isLessonSkipped(LocalDate date, int lessonNumber, String subjectName) {
        return isSkipped(date, lessonNumber, subjectName);
    }

    public boolean isAttended(String date, int lessonNumber, String subjectName) {
        if (date == null) return false;
        Set<String> set = prefs.getStringSet(PREF_ATTENDED, null);
        if (set == null || set.isEmpty()) return false;
        String exactKey = buildKey(date, lessonNumber, subjectName);
        if (set.contains(exactKey)) return true;
        String shortKey = date + "#" + lessonNumber + "#";
        for (String k : set) {
            if (k.startsWith(shortKey)) return true;
        }
        return false;
    }

    public boolean isAttended(LocalDate date, int lessonNumber, String subjectName) {
        return date != null && isAttended(date.toString(), lessonNumber, subjectName);
    }

    public AttendanceStatus getStatus(String date, int lessonNumber, String subjectName) {
        if (isSkipped(date, lessonNumber, subjectName)) {
            return AttendanceStatus.SKIPPED;
        }
        if (isAttended(date, lessonNumber, subjectName)) {
            return AttendanceStatus.ATTENDED;
        }
        return AttendanceStatus.NONE;
    }

    public AttendanceStatus getStatus(LocalDate date, int lessonNumber, String subjectName) {
        return date != null ? getStatus(date.toString(), lessonNumber, subjectName) : AttendanceStatus.NONE;
    }

    public void markSkipped(String date, int lessonNumber, String subjectName) {
        if (date == null) return;
        String exactKey = buildKey(date, lessonNumber, subjectName);
        String shortKey = date + "#" + lessonNumber + "#";

        Set<String> skippedSet = new HashSet<>(prefs.getStringSet(PREF_SKIPPED, Collections.emptySet()));
        Set<String> attendedSet = new HashSet<>(prefs.getStringSet(PREF_ATTENDED, Collections.emptySet()));

        skippedSet.removeIf(k -> k.equals(exactKey) || k.startsWith(shortKey));
        attendedSet.removeIf(k -> k.equals(exactKey) || k.startsWith(shortKey));

        skippedSet.add(exactKey);

        prefs.edit()
                .putStringSet(PREF_SKIPPED, skippedSet)
                .putStringSet(PREF_ATTENDED, attendedSet)
                .apply();
    }

    public void markSkipped(LocalDate date, int lessonNumber, String subjectName) {
        if (date != null) markSkipped(date.toString(), lessonNumber, subjectName);
    }

    public void markLessonSkipped(LocalDate date, int lessonNumber, String subjectName) {
        markSkipped(date, lessonNumber, subjectName);
    }

    public void markAttended(String date, int lessonNumber, String subjectName) {
        if (date == null) return;
        String exactKey = buildKey(date, lessonNumber, subjectName);
        String shortKey = date + "#" + lessonNumber + "#";

        Set<String> skippedSet = new HashSet<>(prefs.getStringSet(PREF_SKIPPED, Collections.emptySet()));
        Set<String> attendedSet = new HashSet<>(prefs.getStringSet(PREF_ATTENDED, Collections.emptySet()));

        skippedSet.removeIf(k -> k.equals(exactKey) || k.startsWith(shortKey));
        attendedSet.removeIf(k -> k.equals(exactKey) || k.startsWith(shortKey));

        attendedSet.add(exactKey);

        prefs.edit()
                .putStringSet(PREF_SKIPPED, skippedSet)
                .putStringSet(PREF_ATTENDED, attendedSet)
                .apply();
    }

    public void markAttended(LocalDate date, int lessonNumber, String subjectName) {
        if (date != null) markAttended(date.toString(), lessonNumber, subjectName);
    }

    public void clearStatus(String date, int lessonNumber, String subjectName) {
        if (date == null) return;
        String exactKey = buildKey(date, lessonNumber, subjectName);
        String shortKey = date + "#" + lessonNumber + "#";

        Set<String> skippedSet = new HashSet<>(prefs.getStringSet(PREF_SKIPPED, Collections.emptySet()));
        Set<String> attendedSet = new HashSet<>(prefs.getStringSet(PREF_ATTENDED, Collections.emptySet()));

        skippedSet.removeIf(k -> k.equals(exactKey) || k.startsWith(shortKey));
        attendedSet.removeIf(k -> k.equals(exactKey) || k.startsWith(shortKey));

        prefs.edit()
                .putStringSet(PREF_SKIPPED, skippedSet)
                .putStringSet(PREF_ATTENDED, attendedSet)
                .apply();
    }

    public void clearStatus(LocalDate date, int lessonNumber, String subjectName) {
        if (date != null) clearStatus(date.toString(), lessonNumber, subjectName);
    }

    public void unmarkLessonSkipped(LocalDate date, int lessonNumber, String subjectName) {
        clearStatus(date, lessonNumber, subjectName);
    }

    public boolean toggleSkipped(String date, int lessonNumber, String subjectName) {
        if (date == null) return false;
        boolean currentlySkipped = isSkipped(date, lessonNumber, subjectName);
        if (currentlySkipped) {
            clearStatus(date, lessonNumber, subjectName);
            return false;
        } else {
            markSkipped(date, lessonNumber, subjectName);
            return true;
        }
    }

    public void setSkipped(String date, int lessonNumber, String subjectName, boolean skipped) {
        if (skipped) {
            markSkipped(date, lessonNumber, subjectName);
        } else {
            clearStatus(date, lessonNumber, subjectName);
        }
    }

    public void setSkipped(LocalDate date, int lessonNumber, String subjectName, boolean skipped) {
        setSkipped(date != null ? date.toString() : null, lessonNumber, subjectName, skipped);
    }
}
