package com.university.schedule.util;

public final class Constants {
    private Constants() { }

    public static final String SCHEDULE_URL =
            "https://university.ru/schedule/Raspisanie-zanyatiy.xlsx";
    public static final String CACHE_FILE_NAME = "schedule_cache.xlsx";
    public static final int SCHEDULE_TTL_HOURS = 12;

    /**
     * Переносы занятий: имя файла на сайте вуза может меняться (в нём зашит
     * хэш вида ".../0f4/dcx2qn9n5x336rmt8w9bf012quaydf8e/ZHurnal-perenosov-....xlsx"),
     * поэтому вместо прямой ссылки на файл указывается страница, на которой
     * этот файл размещён — NetworkClient сначала скачивает HTML этой
     * страницы и ищет на ней ссылку на .xlsx с "переносы" в названии
     * (см. TRANSFERS_FILENAME_HINT), и уже её скачивает. TRANSFERS_PAGE_URL
     * нужно один раз проверить и при необходимости поправить на реальный
     * адрес раздела "Расписание" / "Учебный процесс" сайта вуза, где
     * фактически размещена ссылка на файл переносов.
     */
    public static final String TRANSFERS_PAGE_URL =
            "https://www.rsatu.ru/students/raspisanie-zanyatiy/";
    /** Резервная прямая ссылка — используется, если поиск по странице не нашёл файл
     *  (например, изменилась структура страницы). Имя файла тоже может устареть,
     *  поэтому это именно запасной, а не основной путь. */
    public static final String TRANSFERS_FALLBACK_URL =
            "https://www.rsatu.ru/upload/iblock/0f4/dcx2qn9n5x336rmt8w9bf012quaydf8e/ZHurnal-perenosov-2025_2026-uch.g..xlsx";
    /** Фрагмент имени файла (без учёта регистра), по которому ищем нужную ссылку на странице. */
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