package com.university.schedule.util;

public final class Constants {
    private Constants() { }

    // ------------------------------------------------------------------
    // ОСНОВНОЕ РАСПИСАНИЕ
    // ------------------------------------------------------------------

    /**
     * Страница сайта вуза, на которой размещены актуальные файлы расписания.
     * С этой страницы динамически извлекается ссылка на .xlsx-файл расписания
     * (см. SCHEDULE_FILENAME_HINT), чтобы не зависеть от меняющихся путей/дат.
     */
    public static final String SCHEDULE_PAGE_URL =
            "https://www.rsatu.ru/students/raspisanie-zanyatiy/";

    /**
     * Фрагмент имени файла (без учёта регистра), по которому ищем ссылку
     * на основной файл расписания на странице.
     */
    public static final String SCHEDULE_FILENAME_HINT = "raspisanie";

    /**
     * Запасная прямая ссылка на файл расписания — используется, если
     * динамический поиск на странице не дал результата.
     */
    public static final String SCHEDULE_FALLBACK_URL =
            "https://www.rsatu.ru/upload/iblock/daf/p5jdqaiy99a16vhqmkyohmkjmoz8ppku/Raspisanie-zanyatiy-29.10.2026.xlsx";

    /** Прямая ссылка (совпадает с резервной). */
    public static final String SCHEDULE_URL = SCHEDULE_FALLBACK_URL;

    public static final String CACHE_FILE_NAME = "schedule_cache.xlsx";
    public static final int SCHEDULE_TTL_HOURS = 12;

    // ------------------------------------------------------------------
    // ПЕРЕНОСЫ ЗАНЯТИЙ
    // ------------------------------------------------------------------

    /**
     * Страница сайта вуза, на которой размещён файл переносов занятий.
     * Ссылка извлекается динамически.
     */
    public static final String TRANSFERS_PAGE_URL =
            "https://www.rsatu.ru/students/raspisanie-zanyatiy/";

    /**
     * Запасная прямая ссылка на файл переносов — используется, если
     * динамический поиск на странице не дал результата.
     */
    public static final String TRANSFERS_FALLBACK_URL =
            "https://www.rsatu.ru/upload/iblock/5af/bn9ogs1h4fqoqca7ckkox1s8d24wnyiw/ZHurnal-perenosov-2026_2027-uch.g.xlsx";

    /** Фрагмент имени файла переносов (без учёта регистра). */
    public static final String TRANSFERS_FILENAME_HINT = "perenos";

    public static final String TRANSFERS_CACHE_FILE_NAME = "transfers_cache.xlsx";
    public static final String PREF_TRANSFERS_LAST_UPDATED = "transfers_last_updated";
    public static final String PREF_LAST_AUTO_REFRESH_ATTEMPT = "last_auto_refresh_attempt";

    public static final int SHEET_INDEX_GROUPS = 0;
    public static final int SHEET_INDEX_TEACHERS = 1;

    public static final String PREFS_NAME = "unischedule_prefs";
    public static final String PREF_SELECTION_TYPE = "selection_type";
    public static final String PREF_SELECTION_NAME = "selection_name";
    public static final String PREF_SEMESTER_START = "semester_start";
    public static final String PREF_LAST_UPDATED = "last_updated";
    public static final String PREF_THEME_MODE = "theme_mode";

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
        String[][] t = getLessonTimes(dayOfWeek);
        if (lessonNumber < 1 || lessonNumber > t.length) return "";
        return t[lessonNumber - 1][0] + "–" + t[lessonNumber - 1][1];
    }

    /** Полная таблица времени звонков для дня недели (1=ПН..6=СБ). Будни/суббота отличаются. */
    public static String[][] getLessonTimes(int dayOfWeek) {
        return (dayOfWeek >= 1 && dayOfWeek <= 5) ? TIMES_WEEKDAY : TIMES_WEEKEND;
    }
}
