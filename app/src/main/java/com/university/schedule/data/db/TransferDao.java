package com.university.schedule.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.university.schedule.data.db.ScheduleContract.TransferEntry;
import com.university.schedule.model.TransferItem;

import java.util.ArrayList;
import java.util.List;

/**
 * CRUD для таблицы transfers. В отличие от ScheduleDao (точное равенство по
 * названию колонки листа), здесь group_name может содержать несколько групп
 * через пробел — поэтому выборка по группе идёт через LIKE с последующей
 * точной проверкой токена в Java (TransferItem.matchesGroup), чтобы не
 * словить случайное совпадение подстроки (например "ИВБ-2" внутри "ИВБ-24").
 */
public class TransferDao {

    private static final String TAG = "TransferDao";
    private final DatabaseHelper dbHelper;

    public TransferDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /** Полностью заменяет переносы новым набором (удаление + вставка в транзакции). */
    public void replaceAll(List<TransferItem> items) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TransferEntry.TABLE_NAME, null, null);
            if (items != null) {
                for (TransferItem item : items) {
                    db.insert(TransferEntry.TABLE_NAME, null, toContentValues(item));
                }
            }
            db.setTransactionSuccessful();
            Log.d(TAG, "Переносы заменены, записей: " + (items == null ? 0 : items.size()));
        } finally {
            db.endTransaction();
        }
    }

    public void clearAll() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int deleted = db.delete(TransferEntry.TABLE_NAME, null, null);
        Log.d(TAG, "Удалено переносов: " + deleted);
    }

    /** Все переносы/замены на конкретную календарную дату (все виды kind). */
    public List<TransferItem> getForDate(String isoDate) {
        String selection = TransferEntry.COLUMN_DATE + " = ?";
        return query(selection, new String[]{isoDate});
    }

    /** Переносы за диапазон дат включительно — удобно грузить сразу на всю отображаемую неделю. */
    public List<TransferItem> getForDateRange(String isoDateFrom, String isoDateTo) {
        String selection = TransferEntry.COLUMN_DATE + " BETWEEN ? AND ?";
        return query(selection, new String[]{isoDateFrom, isoDateTo});
    }

    public boolean isEmpty() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TransferEntry.TABLE_NAME, null)) {
            if (cursor.moveToFirst()) return cursor.getInt(0) == 0;
        }
        return true;
    }

    private List<TransferItem> query(String selection, String[] args) {
        List<TransferItem> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                TransferEntry.TABLE_NAME, null, selection, args, null, null,
                TransferEntry.COLUMN_LESSON_NUMBER + " ASC")) {
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        }
        return result;
    }

    private ContentValues toContentValues(TransferItem item) {
        ContentValues v = new ContentValues();
        v.put(TransferEntry.COLUMN_KIND, item.getKind());
        v.put(TransferEntry.COLUMN_DATE, item.getDate());
        v.put(TransferEntry.COLUMN_LESSON_NUMBER, item.getLessonNumber());
        v.put(TransferEntry.COLUMN_GROUP_NAME, item.getGroupName());
        v.put(TransferEntry.COLUMN_SUBJECT_NAME, item.getSubjectName());
        v.put(TransferEntry.COLUMN_TEACHER_NAME, item.getTeacherName());
        v.put(TransferEntry.COLUMN_SUBSTITUTE_TEACHER, item.getSubstituteTeacher());
        v.put(TransferEntry.COLUMN_ROOM, item.getRoom());
        v.put(TransferEntry.COLUMN_LINK_ID, item.getLinkId());
        v.put(TransferEntry.COLUMN_NOTE, item.getNote());
        return v;
    }

    private TransferItem fromCursor(Cursor c) {
        TransferItem item = new TransferItem();
        item.setId(c.getLong(c.getColumnIndexOrThrow(TransferEntry._ID)));
        item.setKind(c.getString(c.getColumnIndexOrThrow(TransferEntry.COLUMN_KIND)));
        item.setDate(c.getString(c.getColumnIndexOrThrow(TransferEntry.COLUMN_DATE)));
        item.setLessonNumber(c.getInt(c.getColumnIndexOrThrow(TransferEntry.COLUMN_LESSON_NUMBER)));
        item.setGroupName(c.getString(c.getColumnIndexOrThrow(TransferEntry.COLUMN_GROUP_NAME)));
        item.setSubjectName(c.getString(c.getColumnIndexOrThrow(TransferEntry.COLUMN_SUBJECT_NAME)));
        item.setTeacherName(c.getString(c.getColumnIndexOrThrow(TransferEntry.COLUMN_TEACHER_NAME)));
        item.setSubstituteTeacher(c.getString(c.getColumnIndexOrThrow(TransferEntry.COLUMN_SUBSTITUTE_TEACHER)));
        item.setRoom(c.getString(c.getColumnIndexOrThrow(TransferEntry.COLUMN_ROOM)));
        item.setLinkId(c.getLong(c.getColumnIndexOrThrow(TransferEntry.COLUMN_LINK_ID)));
        item.setNote(c.getString(c.getColumnIndexOrThrow(TransferEntry.COLUMN_NOTE)));
        return item;
    }
}
