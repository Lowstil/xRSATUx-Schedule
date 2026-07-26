package com.university.schedule.util;

public final class Constants {
    private Constants() { }

    public static final String SCHEDULE_URL =
            "https://university.ru/schedule/Raspisanie-zanyatiy.xlsx";
    public static final String CACHE_FILE_NAME = "schedule_cache.xlsx";
    public static final int SCHEDULE_TTL_HOURS = 12;

    public static final int SHEET_INDEX_GROUPS = 0;
    public static final int SHEET_INDEX_TEACHERS = 1;

    public static final String PREFS_NAME = "unischedule_prefs";
    public static final String PREF_SELECTION_TYPE = "selection_type";
    public static final String PREF_SELECTION_NAME = "selection_name";
    public static final String PREF_SEMESTER_START = "semester_start";
    public static final String PREF_LAST_UPDATED = "last_updated";

    public static final String LESSON_TYPE_LECTURE = "Л";
    public static final String LESSON_TYPE_PRACTICE = "П";
    public static final String LESSON_TYPE_LAB = "ЛР";
    public static final String LESSON_TYPE_ONLINE_LECTURE = "оЛ";
    public static final String LESSON_TYPE_ONLINE_PRACTICE = "оП";
    public static final String LESSON_TYPE_EXAM = "Экзамен";

    public static final String WEEK_TYPE_ODD = "odd";
    public static final String WEEK_TYPE_EVEN = "even";

    private static final String[][] TIMES_WEEKDAY = {
            {"08:30", "10:05"}, {"10:15", "11:50"}, {"12:40", "14:15"},
            {"14:25", "16:00"}, {"16:10", "17:45"}, {"18:00", "19:25"}, {"19:35", "21:00"}
    };
    private static final String[][] TIMES_WEEKEND = {
            {"08:30", "10:05"}, {"10:15", "11:50"}, {"12:00", "13:35"},
            {"13:45", "15:20"}, {"15:30", "17:05"}, {"17:15", "18:40"}, {"18:50", "20:15"}
    };

    public static final String[] DAY_NAMES_SHORT = { "ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ" };

    public static String getLessonTimeLabel(int dayOfWeek, int lessonNumber) {
        String[][] t = (dayOfWeek >= 1 && dayOfWeek <= 5) ? TIMES_WEEKDAY : TIMES_WEEKEND;
        if (lessonNumber < 1 || lessonNumber > t.length) return "";
        return t[lessonNumber - 1][0] + "–" + t[lessonNumber - 1][1];
    }
}