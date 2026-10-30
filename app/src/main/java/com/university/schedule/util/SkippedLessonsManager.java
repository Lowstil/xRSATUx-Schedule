package com.university.schedule.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Управление точечными пропусками пар ("Не пойду на пару").
 * При зажатии на пару студент может отметить, что не пойдёт на неё:
 * пара помечается бейджем "Не пойду", вычёркивается и не создаёт лишних уведомлений.
 */
public class SkippedLessonsManager {
    private static final String PREF_SKIPPED = "skipped_lessons_set";
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
        // Резервный поиск по дате и номеру пары
        String shortKey = date + "#" + lessonNumber + "#";
        for (String k : set) {
            if (k.startsWith(shortKey)) return true;
        }
        return false;
    }

    public boolean isSkipped(java.time.LocalDate date, int lessonNumber, String subjectName) {
        return date != null && isSkipped(date.toString(), lessonNumber, subjectName);
    }

    public boolean isLessonSkipped(java.time.LocalDate date, int lessonNumber, String subjectName) {
        return isSkipped(date, lessonNumber, subjectName);
    }

    public boolean toggleSkipped(String date, int lessonNumber, String subjectName) {
        if (date == null) return false;
        boolean currentlySkipped = isSkipped(date, lessonNumber, subjectName);
        setSkipped(date, lessonNumber, subjectName, !currentlySkipped);
        return !currentlySkipped;
    }

    public void setSkipped(String date, int lessonNumber, String subjectName, boolean skipped) {
        if (date == null) return;
        Set<String> set = new HashSet<>(prefs.getStringSet(PREF_SKIPPED, Collections.emptySet()));
        String exactKey = buildKey(date, lessonNumber, subjectName);
        String shortKey = date + "#" + lessonNumber + "#";

        // Удаляем любые старые ключи для этой даты и номера пары
        set.removeIf(k -> k.equals(exactKey) || k.startsWith(shortKey));

        if (skipped) {
            set.add(exactKey);
        }
        prefs.edit().putStringSet(PREF_SKIPPED, set).apply();
    }

    public void setSkipped(java.time.LocalDate date, int lessonNumber, String subjectName, boolean skipped) {
        if (date != null) {
            setSkipped(date.toString(), lessonNumber, subjectName, skipped);
        }
    }

    public void markLessonSkipped(java.time.LocalDate date, int lessonNumber, String subjectName) {
        setSkipped(date, lessonNumber, subjectName, true);
    }

    public void unmarkLessonSkipped(java.time.LocalDate date, int lessonNumber, String subjectName) {
        setSkipped(date, lessonNumber, subjectName, false);
    }
}
