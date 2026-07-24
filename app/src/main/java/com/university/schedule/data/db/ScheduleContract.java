package com.university.schedule.data.db;

import android.provider.BaseColumns;

/**
 * Имена таблиц/столбцов SQLite и SQL для их создания/удаления.
 *
 * schedule хранит плоский список пар. source отличает строки листа "группы"
 * (source='group') от строк листа "преподаватели" (source='teacher').
 * group_name / teacher_name = заголовок колонки листа (одна группа / один
 * преподаватель), поэтому поиск идёт точным равенством. week_spec хранит
 * диапазон/список недель строкой в том виде, как в файле.
 */
public final class ScheduleContract {

    private ScheduleContract() {
    }

    public static final String DATABASE_NAME = "unischedule.db";
    public static final int DATABASE_VERSION = 1;

    public static final class UserSettingsEntry implements BaseColumns {
        public static final String TABLE_NAME = "user_settings";
        public static final String COLUMN_SELECTION_TYPE = "selection_type";
        public static final String COLUMN_SELECTION_NAME = "selection_name";
        public static final String COLUMN_SEMESTER_START = "semester_start";
        public static final String COLUMN_LAST_UPDATED = "last_updated";
    }

    public static final class ScheduleEntry implements BaseColumns {
        public static final String TABLE_NAME = "schedule";
        public static final String COLUMN_WEEK_TYPE = "week_type";
        public static final String COLUMN_DAY_OF_WEEK = "day_of_week";
        public static final String COLUMN_LESSON_NUMBER = "lesson_number";
        public static final String COLUMN_GROUP_NAME = "group_name";
        public static final String COLUMN_TEACHER_NAME = "teacher_name";
        public static final String COLUMN_SUBJECT_NAME = "subject_name";
        public static final String COLUMN_LESSON_TYPE = "lesson_type";
        public static final String COLUMN_ROOM = "room";
        public static final String COLUMN_WEEK_SPEC = "week_spec";
        public static final String COLUMN_SOURCE = "source";

        public static final String INDEX_GROUP = "idx_schedule_group";
        public static final String INDEX_TEACHER = "idx_schedule_teacher";
    }

    public static final class HolidayEntry implements BaseColumns {
        public static final String TABLE_NAME = "holidays";
        public static final String COLUMN_MONTH = "month";
        public static final String COLUMN_DAY = "day";
        public static final String COLUMN_YEAR = "year";
        public static final String COLUMN_NAME = "name";
    }

    public static final String SQL_CREATE_USER_SETTINGS =
            "CREATE TABLE " + UserSettingsEntry.TABLE_NAME + " (" +
                    UserSettingsEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    UserSettingsEntry.COLUMN_SELECTION_TYPE + " TEXT NOT NULL, " +
                    UserSettingsEntry.COLUMN_SELECTION_NAME + " TEXT NOT NULL, " +
                    UserSettingsEntry.COLUMN_SEMESTER_START + " TEXT NOT NULL, " +
                    UserSettingsEntry.COLUMN_LAST_UPDATED + " TEXT)";

    public static final String SQL_CREATE_SCHEDULE =
            "CREATE TABLE " + ScheduleEntry.TABLE_NAME + " (" +
                    ScheduleEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ScheduleEntry.COLUMN_WEEK_TYPE + " TEXT NOT NULL, " +
                    ScheduleEntry.COLUMN_DAY_OF_WEEK + " INTEGER NOT NULL, " +
                    ScheduleEntry.COLUMN_LESSON_NUMBER + " INTEGER NOT NULL, " +
                    ScheduleEntry.COLUMN_GROUP_NAME + " TEXT, " +
                    ScheduleEntry.COLUMN_TEACHER_NAME + " TEXT, " +
                    ScheduleEntry.COLUMN_SUBJECT_NAME + " TEXT NOT NULL, " +
                    ScheduleEntry.COLUMN_LESSON_TYPE + " TEXT NOT NULL, " +
                    ScheduleEntry.COLUMN_ROOM + " TEXT, " +
                    ScheduleEntry.COLUMN_WEEK_SPEC + " TEXT, " +
                    ScheduleEntry.COLUMN_SOURCE + " TEXT NOT NULL)";

    public static final String SQL_CREATE_INDEX_GROUP =
            "CREATE INDEX " + ScheduleEntry.INDEX_GROUP + " ON " + ScheduleEntry.TABLE_NAME +
                    "(" + ScheduleEntry.COLUMN_GROUP_NAME + ", " +
                    ScheduleEntry.COLUMN_SOURCE + ", " +
                    ScheduleEntry.COLUMN_WEEK_TYPE + ", " +
                    ScheduleEntry.COLUMN_DAY_OF_WEEK + ")";

    public static final String SQL_CREATE_INDEX_TEACHER =
            "CREATE INDEX " + ScheduleEntry.INDEX_TEACHER + " ON " + ScheduleEntry.TABLE_NAME +
                    "(" + ScheduleEntry.COLUMN_TEACHER_NAME + ", " +
                    ScheduleEntry.COLUMN_SOURCE + ", " +
                    ScheduleEntry.COLUMN_WEEK_TYPE + ", " +
                    ScheduleEntry.COLUMN_DAY_OF_WEEK + ")";

    public static final String SQL_CREATE_HOLIDAYS =
            "CREATE TABLE " + HolidayEntry.TABLE_NAME + " (" +
                    HolidayEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    HolidayEntry.COLUMN_MONTH + " INTEGER NOT NULL, " +
                    HolidayEntry.COLUMN_DAY + " INTEGER NOT NULL, " +
                    HolidayEntry.COLUMN_YEAR + " INTEGER NOT NULL DEFAULT 0, " +
                    HolidayEntry.COLUMN_NAME + " TEXT NOT NULL)";

    public static final String SQL_DELETE_USER_SETTINGS =
            "DROP TABLE IF EXISTS " + UserSettingsEntry.TABLE_NAME;
    public static final String SQL_DELETE_SCHEDULE =
            "DROP TABLE IF EXISTS " + ScheduleEntry.TABLE_NAME;
    public static final String SQL_DELETE_HOLIDAYS =
            "DROP TABLE IF EXISTS " + HolidayEntry.TABLE_NAME;
}