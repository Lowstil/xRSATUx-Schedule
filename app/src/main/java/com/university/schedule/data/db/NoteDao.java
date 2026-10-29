package com.university.schedule.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.university.schedule.model.LessonNote;
import com.university.schedule.util.DateUtils;

import java.util.HashMap;
import java.util.Map;

public class NoteDao {
    private static final String TAG = "NoteDao";
    private final DatabaseHelper dbHelper;

    public NoteDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long saveOrUpdate(String date, int lessonNumber, String subjectName, String text, boolean isCompleted) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(ScheduleContract.NoteEntry.COLUMN_DATE, date);
        cv.put(ScheduleContract.NoteEntry.COLUMN_LESSON_NUMBER, lessonNumber);
        cv.put(ScheduleContract.NoteEntry.COLUMN_SUBJECT_NAME, subjectName != null ? subjectName : "");
        cv.put(ScheduleContract.NoteEntry.COLUMN_NOTE_TEXT, text != null ? text.trim() : "");
        cv.put(ScheduleContract.NoteEntry.COLUMN_IS_COMPLETED, isCompleted ? 1 : 0);
        cv.put(ScheduleContract.NoteEntry.COLUMN_CREATED_AT, DateUtils.nowIsoDateTime());

        String where = ScheduleContract.NoteEntry.COLUMN_DATE + " = ? AND "
                + ScheduleContract.NoteEntry.COLUMN_LESSON_NUMBER + " = ?";
        String[] args = new String[]{date, String.valueOf(lessonNumber)};

        int rows = db.update(ScheduleContract.NoteEntry.TABLE_NAME, cv, where, args);
        if (rows == 0) {
            return db.insert(ScheduleContract.NoteEntry.TABLE_NAME, null, cv);
        }
        return rows;
    }

    public boolean delete(String date, int lessonNumber) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String where = ScheduleContract.NoteEntry.COLUMN_DATE + " = ? AND "
                + ScheduleContract.NoteEntry.COLUMN_LESSON_NUMBER + " = ?";
        String[] args = new String[]{date, String.valueOf(lessonNumber)};
        return db.delete(ScheduleContract.NoteEntry.TABLE_NAME, where, args) > 0;
    }

    public LessonNote getNote(String date, int lessonNumber) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String where = ScheduleContract.NoteEntry.COLUMN_DATE + " = ? AND "
                + ScheduleContract.NoteEntry.COLUMN_LESSON_NUMBER + " = ?";
        String[] args = new String[]{date, String.valueOf(lessonNumber)};
        try (Cursor c = db.query(ScheduleContract.NoteEntry.TABLE_NAME, null, where, args, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return mapCursor(c);
            }
        }
        return null;
    }

    public Map<Integer, LessonNote> getNotesForDate(String date) {
        Map<Integer, LessonNote> map = new HashMap<>();
        if (date == null || date.isEmpty()) return map;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String where = ScheduleContract.NoteEntry.COLUMN_DATE + " = ?";
        String[] args = new String[]{date};
        try (Cursor c = db.query(ScheduleContract.NoteEntry.TABLE_NAME, null, where, args, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    LessonNote note = mapCursor(c);
                    map.put(note.getLessonNumber(), note);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения заметок за дату " + date, e);
        }
        return map;
    }

    private LessonNote mapCursor(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(ScheduleContract.NoteEntry._ID));
        String date = c.getString(c.getColumnIndexOrThrow(ScheduleContract.NoteEntry.COLUMN_DATE));
        int lessonNum = c.getInt(c.getColumnIndexOrThrow(ScheduleContract.NoteEntry.COLUMN_LESSON_NUMBER));
        String subject = c.getString(c.getColumnIndexOrThrow(ScheduleContract.NoteEntry.COLUMN_SUBJECT_NAME));
        String text = c.getString(c.getColumnIndexOrThrow(ScheduleContract.NoteEntry.COLUMN_NOTE_TEXT));
        int done = c.getInt(c.getColumnIndexOrThrow(ScheduleContract.NoteEntry.COLUMN_IS_COMPLETED));
        String created = c.getString(c.getColumnIndexOrThrow(ScheduleContract.NoteEntry.COLUMN_CREATED_AT));
        return new LessonNote(id, date, lessonNum, subject, text, done == 1, created);
    }
}
