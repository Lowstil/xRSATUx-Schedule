package com.university.schedule.util;

/** Глобальные константы приложения. */
public final class Constants {
    private Constants() { }

    /**
     * Ссылка на .xlsx расписания на сайте университета.
     * ЗАМЕНИ на реальный адрес перед сборкой (сейчас заглушка).
     */
    public static final String SCHEDULE_URL =
            "https://www.rsatu.ru/upload/iblock/831/mvbiwfk6qnymfs76qp9vqousaogcdhnc/Raspisanie-zanyatiy-18.05.2026.xlsx";

    public static final String CACHE_FILE_NAME = "schedule_cache.xlsx";
    public static final int SCHEDULE_TTL_HOURS = 12;

    /** Индексы листов в xlsx. Лист помещений не парсим — он только дублирует данные. */
    public static final int SHEET_INDEX_GROUPS = 0;
    public static final int SHEET_INDEX_TEACHERS = 1;
    public static final int SHEET_HEADER_ROWS = 2;

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

    /** Время пар, индекс = номер пары - 1. */
    public static final String[][] LESSON_TIMES = {
            {"08:00", "09:30"}, {"09:45", "11:15"}, {"11:30", "13:00"},
            {"13:30", "15:00"}, {"15:15", "16:45"}, {"17:00", "18:30"},
            {"18:45", "20:15"}
    };

    public static final String[] DAY_NAMES_FULL = {
            "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"
    };
    public static final String[] DAY_NAMES_SHORT = { "ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ" };
}