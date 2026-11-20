package com.university.schedule.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    public synchronized Set<String> getExcludedSubjects(String groupName) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> set = prefs.getStringSet(PREF_PREFIX + groupName.trim(), null);
        return set != null ? new HashSet<>(set) : new HashSet<>();
    }

    public synchronized boolean isExcluded(String groupName, String subjectName) {
        if (groupName == null || subjectName == null) return false;
        Set<String> set = prefs.getStringSet(PREF_PREFIX + groupName.trim(), null);
        return set != null && set.contains(subjectName.trim());
    }

    public synchronized void setExcluded(String groupName, String subjectName, boolean excluded) {
        if (groupName == null || subjectName == null) return;
        Set<String> set = getExcludedSubjects(groupName);
        if (excluded) {
            set.add(subjectName.trim());
        } else {
            set.remove(subjectName.trim());
        }
        prefs.edit().putStringSet(PREF_PREFIX + groupName.trim(), set).apply();
    }

    public synchronized void setExcludedSubjects(String groupName, Set<String> subjects) {
        if (groupName == null || groupName.trim().isEmpty()) return;
        prefs.edit().putStringSet(PREF_PREFIX + groupName.trim(), subjects != null ? new HashSet<>(subjects) : Collections.emptySet()).apply();
    }

    public synchronized Map<String, Set<String>> getAllExcludedSubjects() {
        Map<String, Set<String>> result = new HashMap<>();
        Map<String, ?> all = prefs.getAll();
        if (all != null) {
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                String key = entry.getKey();
                if (key != null && key.startsWith(PREF_PREFIX) && entry.getValue() instanceof Set<?>) {
                    String group = key.substring(PREF_PREFIX.length());
                    if (group.isEmpty()) continue;
                    Set<String> subjects = new HashSet<>();
                    for (Object item : (Set<?>) entry.getValue()) {
                        if (item instanceof String && !((String) item).trim().isEmpty()) {
                            subjects.add(((String) item).trim());
                        }
                    }
                    if (!subjects.isEmpty()) {
                        result.put(group, subjects);
                    }
                }
            }
        }
        return result;
    }

    public synchronized void replaceAllExcludedSubjects(Map<String, Set<String>> allExcluded) {
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> all = prefs.getAll();
        if (all != null) {
            for (String key : all.keySet()) {
                if (key != null && key.startsWith(PREF_PREFIX)) {
                    editor.remove(key);
                }
            }
        }
        if (allExcluded != null) {
            for (Map.Entry<String, Set<String>> entry : allExcluded.entrySet()) {
                String group = entry.getKey();
                Set<String> subjects = entry.getValue();
                if (group == null || group.trim().isEmpty() || subjects == null || subjects.isEmpty()) {
                    continue;
                }
                Set<String> clean = new HashSet<>();
                for (String s : subjects) {
                    if (s != null && !s.trim().isEmpty()) {
                        clean.add(s.trim());
                    }
                }
                if (!clean.isEmpty()) {
                    editor.putStringSet(PREF_PREFIX + group.trim(), clean);
                }
            }
        }
        editor.apply();
    }
}
