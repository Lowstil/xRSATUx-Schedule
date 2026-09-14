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
private SemesterManager() { }
public static LocalDate getDefaultSemesterStart() {
LocalDate today = DateUtils.todayMoscow();
int year = today.getYear();
int month = today.getMonthValue();
if (month >= 2 && month <= 8) {
// Февраль–Август: весенний семестр
return LocalDate.of(year, 2, 1);
} else if (month >= 9) {
// Сентябрь–Декабрь: осенний семестр текущего года
return LocalDate.of(year, 9, 1);
} else {
// Январь: осенний семестр прошлого года
return LocalDate.of(year - 1, 9, 1);
}
}
/**
* Человекочитаемое описание семестра для настроек.
* Пример: "Осенний семестр 2026/2027, начался 1 сентября 2026"
*/
public static String describeSemester(SemesterInfo info) {
if (info == null || info.getStartDate() == null) return "Семестр не задан";
LocalDate start = info.getStartDate();
int year = start.getYear();
int month = start.getMonthValue();
String semesterName;
String academicYear;
if (month >= 2 && month <= 8) {
semesterName = "Весенний семестр";
academicYear = year + "/" + (year + 1);
} else if (month >= 9) {
semesterName = "Осенний семестр";
academicYear = year + "/" + (year + 1);
} else {
semesterName = "Осенний семестр";
academicYear = (year - 1) + "/" + year;
}
return semesterName + " " + academicYear + " уч.г., начался " + DateUtils.formatDisplayDate(start);
}
}