package com.university.schedule.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.university.schedule.data.db.ScheduleContract.ScheduleEntry;
import com.university.schedule.model.ScheduleItem;

import java.util.ArrayList;
import java.util.List;

/** CRUD для таблицы schedule. Поиск по группе/преподавателю — точным равенством. */
public class ScheduleDao {

    private static final String TAG = "ScheduleDao";
    public static final String SOURCE_GROUP = "group";
    public static final String SOURCE_TEACHER = "teacher";

    private final DatabaseHelper dbHelper;

    public ScheduleDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /** Полностью заменяет расписание новым набором (удаление + вставка в транзакции). */
    public void replaceAll(List<ScheduleItem> items) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(ScheduleEntry.TABLE_NAME, null, null);
            if (items != null) {
                for (ScheduleItem item : items) {
                    db.insert(ScheduleEntry.TABLE_NAME, null, toContentValues(item));
                }
            }
            db.setTransactionSuccessful();
            Log.d(TAG, "Расписание заменено, записей: " + (items == null ? 0 : items.size()));
        } finally {
            db.endTransaction();
        }
    }

    public void clearAll() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int deleted = db.delete(ScheduleEntry.TABLE_NAME, null, null);
        Log.d(TAG, "Удалено записей расписания: " + deleted);
    }

    /** Строки листа "группы" для конкретной группы и типа недели. */
    public List<ScheduleItem> getScheduleForGroup(String groupName, String weekType) {
        String selection = ScheduleEntry.COLUMN_GROUP_NAME + " = ? AND " +
                ScheduleEntry.COLUMN_SOURCE + " = ? AND " +
                ScheduleEntry.COLUMN_WEEK_TYPE + " = ?";
        String[] args = {groupName, SOURCE_GROUP, weekType};
        return query(selection, args);
    }

    /** Строки листа "преподаватели" для конкретного преподавателя и типа недели. */
    public List<ScheduleItem> getScheduleForTeacher(String teacherName, String weekType) {
        String selection = ScheduleEntry.COLUMN_TEACHER_NAME + " = ? AND " +
                ScheduleEntry.COLUMN_SOURCE + " = ? AND " +
                ScheduleEntry.COLUMN_WEEK_TYPE + " = ?";
        String[] args = {teacherName, SOURCE_TEACHER, weekType};
        return query(selection, args);
    }

    public boolean isEmpty() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + ScheduleEntry.TABLE_NAME, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0) == 0;
            }
        }
        return true;
    }

    /** Уникальный список групп (из листа групп), по алфавиту. */
    public List<String> extractDistinctGroups() {
        return extractDistinctColumn(ScheduleEntry.COLUMN_GROUP_NAME, SOURCE_GROUP);
    }

    /** Уникальный список преподавателей (из листа преподавателей), по алфавиту. */
    public List<String> extractDistinctTeachers() {
        return extractDistinctColumn(ScheduleEntry.COLUMN_TEACHER_NAME, SOURCE_TEACHER);
    }

    private List<String> extractDistinctColumn(String column, String source) {
        List<String> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT DISTINCT " + column + " FROM " + ScheduleEntry.TABLE_NAME +
                " WHERE " + ScheduleEntry.COLUMN_SOURCE + " = ? AND " +
                column + " IS NOT NULL AND " + column + " != '' " +
                "ORDER BY " + column + " COLLATE NOCASE ASC";
        try (Cursor cursor = db.rawQuery(sql, new String[]{source})) {
            while (cursor.moveToNext()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) {
                    result.add(value.trim());
                }
            }
        }
        return result;
    }

    private List<ScheduleItem> query(String selection, String[] args) {
        List<ScheduleItem> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                ScheduleEntry.TABLE_NAME,
                null,
                selection,
                args,
                null, null,
                ScheduleEntry.COLUMN_DAY_OF_WEEK + " ASC, " +
                        ScheduleEntry.COLUMN_LESSON_NUMBER + " ASC")) {
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        }
        return result;
    }

    private ContentValues toContentValues(ScheduleItem item) {
        ContentValues v = new ContentValues();
        v.put(ScheduleEntry.COLUMN_WEEK_TYPE, item.getWeekType());
        v.put(ScheduleEntry.COLUMN_DAY_OF_WEEK, item.getDayOfWeek());
        v.put(ScheduleEntry.COLUMN_LESSON_NUMBER, item.getLessonNumber());
        v.put(ScheduleEntry.COLUMN_GROUP_NAME, item.getGroupName());
        v.put(ScheduleEntry.COLUMN_TEACHER_NAME, item.getTeacherName());
        v.put(ScheduleEntry.COLUMN_SUBJECT_NAME, item.getSubjectName());
        v.put(ScheduleEntry.COLUMN_LESSON_TYPE, item.getLessonType());
        v.put(ScheduleEntry.COLUMN_ROOM, item.getRoom());
        v.put(ScheduleEntry.COLUMN_WEEK_SPEC, item.getWeekSpec());
        v.put(ScheduleEntry.COLUMN_SOURCE, item.getSource());
        return v;
    }

    private ScheduleItem fromCursor(Cursor c) {
        ScheduleItem item = new ScheduleItem();
        item.setId(c.getLong(c.getColumnIndexOrThrow(ScheduleEntry._ID)));
        item.setWeekType(c.getString(c.getColumnIndexOrThrow(ScheduleEntry.COLUMN_WEEK_TYPE)));
        item.setDayOfWeek(c.getInt(c.getColumnIndexOrThrow(ScheduleEntry.COLUMN_DAY_OF_WEEK)));
        item.setLessonNumber(c.getInt(c.getColumnIndexOrThrow(ScheduleEntry.COLUMN_LESSON_NUMBER)));
        item.setGroupName(c.getString(c.getColumnIndexOrThrow(ScheduleEntry.COLUMN_GROUP_NAME)));
        item.setTeacherName(c.getString(c.getColumnIndexOrThrow(ScheduleEntry.COLUMN_TEACHER_NAME)));
        item.setSubjectName(c.getString(c.getColumnIndexOrThrow(ScheduleEntry.COLUMN_SUBJECT_NAME)));
        item.setLessonType(c.getString(c.getColumnIndexOrThrow(ScheduleEntry.COLUMN_LESSON_TYPE)));
        item.setRoom(c.getString(c.getColumnIndexOrThrow(ScheduleEntry.COLUMN_ROOM)));
        item.setWeekSpec(c.getString(c.getColumnIndexOrThrow(ScheduleEntry.COLUMN_WEEK_SPEC)));
        item.setSource(c.getString(c.getColumnIndexOrThrow(ScheduleEntry.COLUMN_SOURCE)));
        return item;
    }
}