package com.university.schedule.logic;
import com.university.schedule.model.SemesterInfo;
import com.university.schedule.util.DateUtils;
import java.time.LocalDate;
/**
* Семестры: осенний начинается 1 сентября, весенний — 1 февраля.
*
* ВАЖНО: раньше здесь было "суббота/понедельник рядом с 1 сентября",
* но по правилам РГАТУ семестр всегда начинается ровно 1 сентября,
* даже если это не понедельник (тогда первая учебная неделя просто
* начинается с середины). Поэтому используем LocalDate.of(), а не
* DateUtils.mondayOfWeek().
*
* Логика выбора семестра по текущему месяцу:
*   Февраль–Август  -> весенний (1 февраля)
*   Сентябрь–Декабрь -> осенний (1 сентября)
*   Январь           -> осенний прошлого года (1 сентября прошлого года)
*/
public final class SemesterManager {
    public static final String SEMESTER_FALL = "fall";
    public static final String SEMESTER_SPRING = "spring";

    private SemesterManager() { }

    /**
     * Определяет семестр по календарному окну:
     * весенний — с 30 января по 24 августа включительно;
     * осенний — с 25 августа по 29 января включительно.
     */
    public static String getCurrentWindowSemester(LocalDate today) {
        LocalDate d = today != null ? today : DateUtils.todayMoscow();
        int month = d.getMonthValue();
        int day = d.getDayOfMonth();
        if ((month == 1 && day >= 30) || (month >= 2 && month <= 7) || (month == 8 && day < 25)) {
            return SEMESTER_SPRING;
        }
        return SEMESTER_FALL;
    }

    /**
     * Возвращает дату начала указанного семестра (понедельник первой учебной недели)
     * относительно referenceDate.
     */
    public static LocalDate getDefaultStartForSemester(String semesterId, LocalDate referenceDate) {
        LocalDate ref = referenceDate != null ? referenceDate : DateUtils.todayMoscow();
        int year = ref.getYear();
        int month = ref.getMonthValue();
        LocalDate base;
        if (SEMESTER_SPRING.equals(semesterId)) {
            int springYear = (month >= 8) ? (year + 1) : year;
            base = LocalDate.of(springYear, 2, 1);
        } else {
            int fallYear = (month < 8) ? (year - 1) : year;
            base = LocalDate.of(fallYear, 9, 1);
        }
        return DateUtils.mondayOfWeek(base);
    }

    public static LocalDate getDefaultSemesterStart() {
        LocalDate today = DateUtils.todayMoscow();
        return getDefaultStartForSemester(getCurrentWindowSemester(today), today);
    }

    /**
     * Человекочитаемое название семестра и учебного года, например:
     * "Осенний семестр 2026/2027" или "Весенний семестр 2026/2027".
     */
    public static String describeSemester(String semesterId, LocalDate startDate) {
        boolean isSpring = SEMESTER_SPRING.equals(semesterId);
        String semesterName = isSpring ? "Весенний семестр" : "Осенний семестр";
        if (startDate == null) {
            return semesterName;
        }
        int year = startDate.getYear();
        int month = startDate.getMonthValue();
        String academicYear;
        if (isSpring) {
            academicYear = (month >= 8) ? (year + "/" + (year + 1)) : ((year - 1) + "/" + year);
        } else {
            academicYear = (month >= 8) ? (year + "/" + (year + 1)) : ((year - 1) + "/" + year);
        }
        return semesterName + " " + academicYear;
    }

    /**
     * Человекочитаемое описание семестра для настроек.
     * Пример: "Осенний семестр 2026/2027 уч.г., начался 31.08.2026"
     */
    public static String describeSemester(SemesterInfo info) {
        if (info == null || info.getStartDate() == null) return "Семестр не задан";
        LocalDate start = info.getStartDate();
        int month = start.getMonthValue();
        int day = start.getDayOfMonth();
        String semId = ((month == 8 && day >= 25) || month >= 9 || (month == 1 && day < 25))
                ? SEMESTER_FALL
                : SEMESTER_SPRING;
        return describeSemester(semId, start) + " уч.г., начался " + DateUtils.formatDisplayDate(start);
    }
}