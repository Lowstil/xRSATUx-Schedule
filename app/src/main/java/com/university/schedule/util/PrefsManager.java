package com.university.schedule.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

/** Обёртка над SharedPreferences для быстрого доступа к выбору пользователя. */
public class PrefsManager {
    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasSelection() {
        return prefs.contains(Constants.PREF_SELECTION_TYPE)
                && prefs.contains(Constants.PREF_SELECTION_NAME);
    }

    public void saveSelection(String type, String name) {
        prefs.edit()
                .putString(Constants.PREF_SELECTION_TYPE, type)
                .putString(Constants.PREF_SELECTION_NAME, name)
                .apply();
    }

    public String getSelectionType() {
        return prefs.getString(Constants.PREF_SELECTION_TYPE, null);
    }

    public String getSelectionName() {
        return prefs.getString(Constants.PREF_SELECTION_NAME, null);
    }

    public void saveSemesterStart(LocalDate date) {
        prefs.edit().putString(Constants.PREF_SEMESTER_START, DateUtils.toIsoString(date)).apply();
    }

    public LocalDate getSemesterStart() {
        return DateUtils.parseIsoDate(prefs.getString(Constants.PREF_SEMESTER_START, null));
    }

    public void saveLastUpdated(String iso) {
        prefs.edit().putString(Constants.PREF_LAST_UPDATED, iso).apply();
    }

    public String getLastUpdated() {
        return prefs.getString(Constants.PREF_LAST_UPDATED, null);
    }

    public void clearAll() { prefs.edit().clear().apply(); }
}