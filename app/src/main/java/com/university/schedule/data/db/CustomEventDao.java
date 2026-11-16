package com.university.schedule.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.university.schedule.data.db.ScheduleContract.CustomEventEntry;
import com.university.schedule.model.CustomEvent;

import java.util.ArrayList;
import java.util.List;

/** CRUD для таблицы пользовательских событий (custom_events). */
public class CustomEventDao {

    private final DatabaseHelper dbHelper;

    public CustomEventDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public long insert(CustomEvent event) {
        if (event == null || event.getGroupName() == null) return -1;
        String target = event.getGroupName().trim();
        if (target.isEmpty()) return -1;
        event.setGroupName(target);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long id = db.insert(CustomEventEntry.TABLE_NAME, null, toContentValues(event));
        event.setId(id);
        return id;
    }

    public int delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(CustomEventEntry.TABLE_NAME,
                CustomEventEntry._ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(CustomEventEntry.TABLE_NAME, null, null);
    }

    public List<CustomEvent> getAll() {
        return query(null, null);
    }

    public List<CustomEvent> getForTarget(String groupName, boolean isGroup) {
        if (groupName == null) return new ArrayList<>();
        String target = groupName.trim();
        if (target.isEmpty()) return new ArrayList<>();
        String selection = CustomEventEntry.COLUMN_GROUP_NAME + " = ? AND " +
                CustomEventEntry.COLUMN_IS_GROUP + " = ?";
        String[] args = {target, isGroup ? "1" : "0"};
        return query(selection, args);
    }

    private List<CustomEvent> query(String selection, String[] args) {
        List<CustomEvent> result = new ArrayList<>();
        if (args != null) {
            for (String a : args) {
                if (a == null) return result;
            }
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor c = db.query(
                CustomEventEntry.TABLE_NAME,
                null,
                selection,
                args,
                null,
                null,
                CustomEventEntry.COLUMN_DAY_OF_WEEK + " ASC, " +
                        CustomEventEntry.COLUMN_LESSON_NUMBER + " ASC, " +
                        CustomEventEntry._ID + " ASC")) {
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        }
        return result;
    }

    private ContentValues toContentValues(CustomEvent e) {
        ContentValues v = new ContentValues();
        v.put(CustomEventEntry.COLUMN_GROUP_NAME, e.getGroupName());
        v.put(CustomEventEntry.COLUMN_IS_GROUP, e.isGroup() ? 1 : 0);
        v.put(CustomEventEntry.COLUMN_DATE_ISO, e.getDateIso());
        v.put(CustomEventEntry.COLUMN_DAY_OF_WEEK, e.getDayOfWeek());
        v.put(CustomEventEntry.COLUMN_WEEK_TYPE, e.getWeekType());
        v.put(CustomEventEntry.COLUMN_LESSON_NUMBER, e.getLessonNumber());
        v.put(CustomEventEntry.COLUMN_SUBJECT_NAME, e.getSubjectName() != null ? e.getSubjectName() : "");
        v.put(CustomEventEntry.COLUMN_TEACHER_NAME, e.getTeacherName());
        v.put(CustomEventEntry.COLUMN_ROOM, e.getRoom());
        v.put(CustomEventEntry.COLUMN_NOTE, e.getNote());
        return v;
    }

    private CustomEvent fromCursor(Cursor c) {
        CustomEvent e = new CustomEvent();
        e.setId(c.getLong(c.getColumnIndexOrThrow(CustomEventEntry._ID)));
        e.setGroupName(c.getString(c.getColumnIndexOrThrow(CustomEventEntry.COLUMN_GROUP_NAME)));
        e.setGroup(c.getInt(c.getColumnIndexOrThrow(CustomEventEntry.COLUMN_IS_GROUP)) != 0);
        e.setDateIso(c.getString(c.getColumnIndexOrThrow(CustomEventEntry.COLUMN_DATE_ISO)));
        e.setDayOfWeek(c.getInt(c.getColumnIndexOrThrow(CustomEventEntry.COLUMN_DAY_OF_WEEK)));
        e.setWeekType(c.getString(c.getColumnIndexOrThrow(CustomEventEntry.COLUMN_WEEK_TYPE)));
        e.setLessonNumber(c.getInt(c.getColumnIndexOrThrow(CustomEventEntry.COLUMN_LESSON_NUMBER)));
        e.setSubjectName(c.getString(c.getColumnIndexOrThrow(CustomEventEntry.COLUMN_SUBJECT_NAME)));
        e.setTeacherName(c.getString(c.getColumnIndexOrThrow(CustomEventEntry.COLUMN_TEACHER_NAME)));
        e.setRoom(c.getString(c.getColumnIndexOrThrow(CustomEventEntry.COLUMN_ROOM)));
        e.setNote(c.getString(c.getColumnIndexOrThrow(CustomEventEntry.COLUMN_NOTE)));
        return e;
    }
}
