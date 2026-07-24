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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Обновление БД " + oldVersion + " -> " + newVersion);
        db.execSQL(ScheduleContract.SQL_DELETE_SCHEDULE);
        db.execSQL(ScheduleContract.SQL_DELETE_USER_SETTINGS);
        db.execSQL(ScheduleContract.SQL_DELETE_HOLIDAYS);
        onCreate(db);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }
}