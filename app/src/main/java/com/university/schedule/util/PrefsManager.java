package com.university.schedule.util;
import android.content.Context;
import android.content.SharedPreferences;
import com.university.schedule.logic.SemesterManager;
import java.time.LocalDate;
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
    public String getSelectionType() { return prefs.getString(Constants.PREF_SELECTION_TYPE, null); }
    public String getSelectionName() { return prefs.getString(Constants.PREF_SELECTION_NAME, null); }
    public static final String PREF_ACTIVE_SEMESTER = "active_semester";
    private static final String PREF_SEMESTER_START_PREFIX = "semester_start_";
    private static final String PREF_SEMESTER_HASH_PREFIX = "semester_hash_";
    private static final String PREF_SEMESTER_PUBLISHED_PREFIX = "semester_published_";

    public String getActiveSemester() {
        String stored = prefs.getString(PREF_ACTIVE_SEMESTER, null);
        if (SemesterManager.SEMESTER_FALL.equals(stored) || SemesterManager.SEMESTER_SPRING.equals(stored)) {
            return stored;
        }
        return SemesterManager.getCurrentWindowSemester(DateUtils.todayMoscow());
    }

    public void setActiveSemester(String semesterId) {
        if (!SemesterManager.SEMESTER_FALL.equals(semesterId)
                && !SemesterManager.SEMESTER_SPRING.equals(semesterId)) {
            return;
        }
        prefs.edit().putString(PREF_ACTIVE_SEMESTER, semesterId).apply();
    }

    public void saveSemesterStart(String semesterId, LocalDate date) {
        if (semesterId == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        if (date == null) {
            editor.remove(PREF_SEMESTER_START_PREFIX + semesterId);
        } else {
            editor.putString(PREF_SEMESTER_START_PREFIX + semesterId, DateUtils.toIsoString(date));
        }
        if (semesterId.equals(getActiveSemester())) {
            if (date == null) {
                editor.remove(Constants.PREF_SEMESTER_START);
            } else {
                editor.putString(Constants.PREF_SEMESTER_START, DateUtils.toIsoString(date));
            }
        }
        editor.apply();
    }

    public LocalDate getSemesterStart(String semesterId) {
        if (semesterId == null) return null;
        LocalDate perSem = DateUtils.parseIsoDate(
                prefs.getString(PREF_SEMESTER_START_PREFIX + semesterId, null));
        if (perSem != null) return perSem;
        LocalDate legacy = DateUtils.parseIsoDate(
                prefs.getString(Constants.PREF_SEMESTER_START, null));
        if (legacy != null) {
            int m = legacy.getMonthValue();
            boolean legacyIsFall = (m >= 8 || m == 1);
            if ((SemesterManager.SEMESTER_FALL.equals(semesterId) && legacyIsFall)
                    || (SemesterManager.SEMESTER_SPRING.equals(semesterId) && !legacyIsFall)) {
                return legacy;
            }
        }
        return null;
    }

    public void saveSemesterHash(String semesterId, String sha256) {
        if (semesterId == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        if (sha256 == null) {
            editor.remove(PREF_SEMESTER_HASH_PREFIX + semesterId);
        } else {
            editor.putString(PREF_SEMESTER_HASH_PREFIX + semesterId, sha256);
        }
        editor.apply();
    }

    public String getSemesterHash(String semesterId) {
        if (semesterId == null) return null;
        return prefs.getString(PREF_SEMESTER_HASH_PREFIX + semesterId, null);
    }

    public void setSemesterPublished(String semesterId, boolean published) {
        if (semesterId == null) return;
        prefs.edit().putBoolean(PREF_SEMESTER_PUBLISHED_PREFIX + semesterId, published).apply();
    }

    public boolean isSemesterPublished(String semesterId) {
        if (semesterId == null) return false;
        return prefs.getBoolean(PREF_SEMESTER_PUBLISHED_PREFIX + semesterId, false);
    }

    public void saveSemesterStart(LocalDate date) {
        saveSemesterStart(getActiveSemester(), date);
    }

    public LocalDate getSemesterStart() {
        return getSemesterStart(getActiveSemester());
    }
    public void saveLastUpdated(String iso) {
        prefs.edit().putString(Constants.PREF_LAST_UPDATED, iso).apply();
    }
    public String getLastUpdated() { return prefs.getString(Constants.PREF_LAST_UPDATED, null); }
    public void saveTransfersLastUpdated(String iso) {
        prefs.edit().putString(Constants.PREF_TRANSFERS_LAST_UPDATED, iso).apply();
    }
    public String getTransfersLastUpdated() {
        return prefs.getString(Constants.PREF_TRANSFERS_LAST_UPDATED, null);
    }
    public void saveLastAutoRefreshAttempt(String iso) {
        prefs.edit().putString(Constants.PREF_LAST_AUTO_REFRESH_ATTEMPT, iso).apply();
    }
    public String getLastAutoRefreshAttempt() {
        return prefs.getString(Constants.PREF_LAST_AUTO_REFRESH_ATTEMPT, null);
    }
    public void clearAll() { prefs.edit().clear().apply(); }
    public void saveThemeMode(int mode) {
        prefs.edit().putInt(Constants.PREF_THEME_MODE, mode).apply();
    }
    public int getThemeMode(int defaultMode) {
        return prefs.getInt(Constants.PREF_THEME_MODE, defaultMode);
    }
    public static final String PREF_REMINDERS_ENABLED = "reminders_enabled";
    public static final String PREF_REMINDERS_MINUTES = "reminders_minutes";
    public static final String PREF_MORNING_DIGEST_ENABLED = "morning_digest_enabled";
    public static final String PREF_LAST_NOTIFIED_KEY = "last_notified_lesson_key";

    public String getLastNotifiedLessonKey() {
        return prefs.getString(PREF_LAST_NOTIFIED_KEY, "");
    }
    public void setLastNotifiedLessonKey(String key) {
        prefs.edit().putString(PREF_LAST_NOTIFIED_KEY, key != null ? key : "").apply();
    }

    public boolean isRemindersEnabled() {
        return prefs.getBoolean(PREF_REMINDERS_ENABLED, true);
    }
    public void setRemindersEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_REMINDERS_ENABLED, enabled).apply();
    }
    public int getReminderMinutesBefore() {
        return prefs.getInt(PREF_REMINDERS_MINUTES, 15);
    }
    public void setReminderMinutesBefore(int minutes) {
        prefs.edit().putInt(PREF_REMINDERS_MINUTES, minutes).apply();
    }
    public boolean isMorningDigestEnabled() {
        return prefs.getBoolean(PREF_MORNING_DIGEST_ENABLED, true);
    }
    public void setMorningDigestEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_MORNING_DIGEST_ENABLED, enabled).apply();
    }

    public static final String PREF_FIRST_LAUNCH = "pref_first_launch";
    public static final String PREF_REDUCED_TRANSPARENCY = "reduced_transparency";
    public static final String PREF_LIVE_LESSON_NOTIF_ENABLED = "live_lesson_notif_enabled";
    public static final String PREF_ATTENDANCE_SKIP_LIMIT_PCT = "attendance_skip_limit_pct";
    public static final String PREF_SCHEDULE_DIFF_JSON = "schedule_diff_json";

    public boolean isFirstLaunch() {
        return prefs.getBoolean(PREF_FIRST_LAUNCH, true);
    }
    public void setFirstLaunchCompleted(boolean completed) {
        prefs.edit().putBoolean(PREF_FIRST_LAUNCH, !completed).apply();
    }

    public boolean isReducedTransparency() {
        return prefs.getBoolean(PREF_REDUCED_TRANSPARENCY, false);
    }
    public void setReducedTransparency(boolean v) {
        prefs.edit().putBoolean(PREF_REDUCED_TRANSPARENCY, v).apply();
    }

    public boolean isLiveLessonNotificationEnabled() {
        return prefs.getBoolean(PREF_LIVE_LESSON_NOTIF_ENABLED, true);
    }
    public void setLiveLessonNotificationEnabled(boolean v) {
        prefs.edit().putBoolean(PREF_LIVE_LESSON_NOTIF_ENABLED, v).apply();
    }

    public int getAttendanceSkipLimitPercent() {
        return prefs.getInt(PREF_ATTENDANCE_SKIP_LIMIT_PCT, 30);
    }
    public void setAttendanceSkipLimitPercent(int pct) {
        prefs.edit().putInt(PREF_ATTENDANCE_SKIP_LIMIT_PCT, pct).apply();
    }

    public String getScheduleDiffJson() {
        return prefs.getString(PREF_SCHEDULE_DIFF_JSON, null);
    }
    public void saveScheduleDiffJson(String json) {
        prefs.edit().putString(PREF_SCHEDULE_DIFF_JSON, json).apply();
    }
    public void clearScheduleDiff() {
        prefs.edit().remove(PREF_SCHEDULE_DIFF_JSON).apply();
    }
}