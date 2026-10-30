package com.university.schedule.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/** Создание/обновление БД. Синглтон, чтобы не плодить соединений. */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static volatile DatabaseHelper instance;

    private DatabaseHelper(Context context) {
        super(context.getApplicationContext(), ScheduleContract.DATABASE_NAME, null,
                ScheduleContract.DATABASE_VERSION);
    }

    public static DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (DatabaseHelper.class) {
                if (instance == null) {
                    instance = new DatabaseHelper(context);
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Создание таблиц БД");
        db.execSQL(ScheduleContract.SQL_CREATE_USER_SETTINGS);
        db.execSQL(ScheduleContract.SQL_CREATE_SCHEDULE);
        db.execSQL(ScheduleContract.SQL_CREATE_INDEX_GROUP);
        db.execSQL(ScheduleContract.SQL_CREATE_INDEX_TEACHER);
        db.execSQL(ScheduleContract.SQL_CREATE_HOLIDAYS);
        db.execSQL(ScheduleContract.SQL_CREATE_TRANSFERS);
        db.execSQL(ScheduleContract.SQL_CREATE_INDEX_TRANSFERS);
        db.execSQL(ScheduleContract.SQL_CREATE_NOTES);
        db.execSQL(ScheduleContract.SQL_CREATE_INDEX_NOTES);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.execSQL(ScheduleContract.SQL_CREATE_NOTES);
        db.execSQL(ScheduleContract.SQL_CREATE_INDEX_NOTES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Обновление БД " + oldVersion + " -> " + newVersion);
        db.execSQL(ScheduleContract.SQL_DELETE_SCHEDULE);
        db.execSQL(ScheduleContract.SQL_DELETE_USER_SETTINGS);
        db.execSQL(ScheduleContract.SQL_DELETE_HOLIDAYS);
        db.execSQL(ScheduleContract.SQL_DELETE_TRANSFERS);
        onCreate(db);
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + ScheduleContract.NoteEntry.TABLE_NAME +
                        " ADD COLUMN " + ScheduleContract.NoteEntry.COLUMN_IMAGE_PATH + " TEXT");
            } catch (Exception e) {
                Log.w(TAG, "Колонка image_path уже существует или не может быть добавлена: " + e.getMessage());
            }
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }
}