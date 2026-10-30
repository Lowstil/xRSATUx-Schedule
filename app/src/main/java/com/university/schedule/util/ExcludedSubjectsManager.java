package com.university.schedule.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Управление списком исключённых предметов (перезачёт) для конкретной группы.
 * Исключённые предметы остаются в расписании, но отображаются вычеркнутыми со
 * специальной плашкой "Перезачёт", а напоминания и активные таймеры по ним отключаются.
 */
public class ExcludedSubjectsManager {
    private static final String PREF_PREFIX = "excluded_subjects_";
    private final SharedPreferences prefs;

    public ExcludedSubjectsManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Set<String> getExcludedSubjects(String groupName) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> set = prefs.getStringSet(PREF_PREFIX + groupName.trim(), null);
        return set != null ? new HashSet<>(set) : new HashSet<>();
    }

    public boolean isExcluded(String groupName, String subjectName) {
        if (groupName == null || subjectName == null) return false;
        Set<String> set = prefs.getStringSet(PREF_PREFIX + groupName.trim(), null);
        return set != null && set.contains(subjectName.trim());
    }

    public void setExcluded(String groupName, String subjectName, boolean excluded) {
        if (groupName == null || subjectName == null) return;
        Set<String> set = getExcludedSubjects(groupName);
        if (excluded) {
            set.add(subjectName.trim());
        } else {
            set.remove(subjectName.trim());
        }
        prefs.edit().putStringSet(PREF_PREFIX + groupName.trim(), set).apply();
    }

    public void setExcludedSubjects(String groupName, Set<String> subjects) {
        if (groupName == null || groupName.trim().isEmpty()) return;
        prefs.edit().putStringSet(PREF_PREFIX + groupName.trim(), subjects != null ? subjects : Collections.emptySet()).apply();
    }
}
