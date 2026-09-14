package com.university.schedule.util;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Управление выбором темы (Системная / Светлая / Тёмная). Значение хранится
 * в SharedPreferences и применяется через AppCompatDelegate.setDefaultNightMode,
 * который сразу перекрашивает все Activity (включая уже открытые) без перезапуска.
 */
public final class ThemeManager {

    private ThemeManager() {
    }

    public static final int MODE_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    public static final int MODE_LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    public static final int MODE_DARK = AppCompatDelegate.MODE_NIGHT_YES;

    /** Читает сохранённый режим (по умолчанию — системная тема) и применяет его. */
    public static void applySavedMode(Context context) {
        PrefsManager prefs = new PrefsManager(context);
        int mode = prefs.getThemeMode(MODE_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /** Сохраняет и сразу применяет выбранный пользователем режим темы. */
    public static void setMode(Context context, int mode) {
        new PrefsManager(context).saveThemeMode(mode);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static int getSavedMode(Context context) {
        return new PrefsManager(context).getThemeMode(MODE_SYSTEM);
    }
}
