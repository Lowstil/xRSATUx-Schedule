package com.university.schedule.logic;
import com.university.schedule.model.SemesterInfo;
import com.university.schedule.util.DateUtils;
import java.time.LocalDate;
/**
 * Определение начала учебного семестра.
 *
 * КЛЮЧЕВОЙ ФИКС: раньше для июля/августа (месяц 2..8) метод возвращал
 * 1 февраля ТЕКУЩЕГО года — то есть весенний семестр, который к июлю УЖЕ
 * закончился. Из-за этого после загрузки НОВОГО (осеннего) файла расписания
 * приложение продолжало раскладывать недели по датам прошедшей весны и
 * показывало "расписание прошлого семестра", хотя в базе уже лежал новый файл.
 *
 * Теперь логика такая:
 *  - если сегодня внутри весеннего семестра  -> весна текущего года;
 *  - если сегодня внутри осеннего семестра   -> осень текущего года;
 *  - если сегодня раньше весны (январь)      -> осень ПРОШЛОГО года;
 *  - если сегодня в летнем промежутке (весна кончилась, осень ещё не
 *    началась) -> предстоящая ОСЕНЬ текущего года. На сайте в это время
 *    уже лежит файл нового (осеннего) семестра, поэтому приложение должно
 *    показывать именно его, а не прошедшую весну.
 */
public final class SemesterManager {
    private SemesterManager() { }
    private static final int TOTAL_WEEKS = SemesterInfo.TOTAL_WEEKS;

    public static LocalDate getDefaultSemesterStart() {
        LocalDate today = DateUtils.todayMoscow();
        int year = today.getYear();
        LocalDate spring = DateUtils.mondayOfWeek(LocalDate.of(year, 2, 1));
        LocalDate autumn = DateUtils.mondayOfWeek(LocalDate.of(year, 9, 1));
        LocalDate springEnd = spring.plusWeeks(TOTAL_WEEKS);
        LocalDate autumnEnd = autumn.plusWeeks(TOTAL_WEEKS);

        if (!today.isBefore(spring) && today.isBefore(springEnd)) return spring;
        if (!today.isBefore(autumn) && today.isBefore(autumnEnd)) return autumn;
        if (today.isBefore(spring)) return DateUtils.mondayOfWeek(LocalDate.of(year - 1, 9, 1));
        // Летний промежуток: весенний семестр завершён, осенний ещё не стартовал.
        // Возвращаем предстоящую осень — именно её файл сейчас на сайте.
        // (Для 2026: mondayOfWeek(01.09.2026) = 31.08.2026 — совпадает с именем
        // файла Raspisanie-zanyatiy-31.08.2026.xlsx.)
        return autumn;
    }

    /** Человекочитаемое описание семестра для UI. */
    public static String describeSemester(SemesterInfo info) {
        if (info == null || info.getStartDate() == null) return "Семестр не определён";
        LocalDate start = info.getStartDate();
        int m = start.getMonthValue();
        // Начало семестра хранится как понедельник недели, на которую падает
        // 1 сентября / 1 февраля, поэтому месяц может быть 8 или 9 (осень)
        // и 1 или 2 (весна).
        boolean spring = (m == 1 || m == 2);
        int year = start.getYear();
        String academicYear = spring ? (year - 1) + "/" + year : year + "/" + (year + 1);
        String name = spring ? "Весенний" : "Осенний";
        return name + " семестр " + academicYear + " уч.г. (начало " + start + ")";
    }
}