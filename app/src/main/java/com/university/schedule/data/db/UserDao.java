package com.university.schedule.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.university.schedule.data.db.ScheduleContract.UserSettingsEntry;
import com.university.schedule.util.DateUtils;

import java.time.LocalDate;

/** CRUD для user_settings. Хранится не более одной активной записи. */
public class UserDao {

    private final DatabaseHelper dbHelper;

    public UserDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public static class UserSettings {
        public String selectionType;
        public String selectionName;
        public LocalDate semesterStart;
        public String lastUpdated;
    }

    /** Перезаписывает выбор пользователя и дату начала семестра. */
    public void saveSelection(String type, String name, LocalDate semesterStart) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(UserSettingsEntry.TABLE_NAME, null, null);
            ContentValues v = new ContentValues();
            v.put(UserSettingsEntry.COLUMN_SELECTION_TYPE, type);
            v.put(UserSettingsEntry.COLUMN_SELECTION_NAME, name);
            v.put(UserSettingsEntry.COLUMN_SEMESTER_START, DateUtils.toIsoString(semesterStart));
            v.put(UserSettingsEntry.COLUMN_LAST_UPDATED, DateUtils.nowIsoDateTime());
            db.insert(UserSettingsEntry.TABLE_NAME, null, v);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void updateSemesterStart(LocalDate semesterStart) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(UserSettingsEntry.COLUMN_SEMESTER_START, DateUtils.toIsoString(semesterStart));
        db.update(UserSettingsEntry.TABLE_NAME, v, null, null);
    }

    public void touchLastUpdated() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(UserSettingsEntry.COLUMN_LAST_UPDATED, DateUtils.nowIsoDateTime());
        db.update(UserSettingsEntry.TABLE_NAME, v, null, null);
    }

    public UserSettings getSettings() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                UserSettingsEntry.TABLE_NAME,
                null, null, null, null, null,
                UserSettingsEntry._ID + " DESC",
                "1")) {
            if (cursor.moveToFirst()) {
                UserSettings s = new UserSettings();
                s.selectionType = cursor.getString(
                        cursor.getColumnIndexOrThrow(UserSettingsEntry.COLUMN_SELECTION_TYPE));
                s.selectionName = cursor.getString(
                        cursor.getColumnIndexOrThrow(UserSettingsEntry.COLUMN_SELECTION_NAME));
                s.semesterStart = DateUtils.parseIsoDate(cursor.getString(
                        cursor.getColumnIndexOrThrow(UserSettingsEntry.COLUMN_SEMESTER_START)));
                s.lastUpdated = cursor.getString(
                        cursor.getColumnIndexOrThrow(UserSettingsEntry.COLUMN_LAST_UPDATED));
                return s;
            }
        }
        return null;
    }

    public boolean hasSettings() {
        return getSettings() != null;
    }

    public void clear() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(UserSettingsEntry.TABLE_NAME, null, null);
    }
}