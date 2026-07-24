package com.university.schedule.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.university.schedule.data.db.ScheduleContract.HolidayEntry;
import com.university.schedule.model.Holiday;

import java.util.ArrayList;
import java.util.List;

/** CRUD для доп. праздников (поверх встроенного списка HolidayChecker). */
public class HolidayDao {

    private final DatabaseHelper dbHelper;

    public HolidayDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public void insertAll(List<Holiday> holidays) {
        if (holidays == null || holidays.isEmpty()) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Holiday h : holidays) {
                db.insert(HolidayEntry.TABLE_NAME, null, toContentValues(h));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Holiday> getAll() {
        List<Holiday> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(HolidayEntry.TABLE_NAME, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                int month = cursor.getInt(cursor.getColumnIndexOrThrow(HolidayEntry.COLUMN_MONTH));
                int day = cursor.getInt(cursor.getColumnIndexOrThrow(HolidayEntry.COLUMN_DAY));
                int year = cursor.getInt(cursor.getColumnIndexOrThrow(HolidayEntry.COLUMN_YEAR));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(HolidayEntry.COLUMN_NAME));
                result.add(new Holiday(month, day, name, year));
            }
        }
        return result;
    }

    public void clear() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(HolidayEntry.TABLE_NAME, null, null);
    }

    public boolean isEmpty() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + HolidayEntry.TABLE_NAME, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0) == 0;
            }
        }
        return true;
    }

    private ContentValues toContentValues(Holiday h) {
        ContentValues v = new ContentValues();
        v.put(HolidayEntry.COLUMN_MONTH, h.getMonth());
        v.put(HolidayEntry.COLUMN_DAY, h.getDay());
        v.put(HolidayEntry.COLUMN_YEAR, h.getYear());
        v.put(HolidayEntry.COLUMN_NAME, h.getName());
        return v;
    }
}