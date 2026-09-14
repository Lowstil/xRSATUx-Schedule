package com.university.schedule.logic;

import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.TransferItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сборка структурированного расписания из плоского списка ScheduleItem
 * с учётом типа недели, диапазона недель (weekSpec), дня, праздников и,
 * теперь, переносов/замен занятий (TransferItem) на конкретные даты.
 *
 * Слияние переносов с обычным расписанием на день (mergeTransfers):
 *  1. Для каждой пары дня ищем TransferItem(REMOVAL) с тем же номером пары
 *     и совпадающей группой/преподавателем на эту дату — если нашли,
 *     занятие помечается cancelled=true (не удаляется из списка, чтобы
 *     UI мог показать его зачёркнутым с пометкой "Перенесено", а не
 *     молча исчезающим — это было бы непонятно пользователю).
 *  2. Для каждого TransferItem(ADDITION) на эту дату, совпадающего по
 *     группе/преподавателю, создаём синтетический ScheduleItem и
 *     добавляем в список (movedIn=true) — это "занятие пришло откуда-то".
 *  3. Для каждого TransferItem(ROOM_CHANGE) на эту дату, совпадающего по
 *     номеру пары и группе/преподавателю, находим соответствующий
 *     ScheduleItem дня и заменяем его room на новый (roomChanged=true).
 *
 * Разделение по группе/преподавателю: ScheduleFilter не знает точно, что
 * это (group.equals) или (teacher.equals) — ему передают уже отфильтрованный
 * список transfers на дату (см. TransferItem.matchesGroup/matchesTeacher),
 * либо все transfers на дату и предикат отдельно (см. перегрузку ниже).
 */
public class ScheduleFilter {

    private final HolidayChecker holidayChecker;

    public ScheduleFilter(HolidayChecker holidayChecker) {
        this.holidayChecker = holidayChecker;
    }

    /** Расписание на учебную неделю (ПН-СБ) без переносов (обратная совместимость). */
    public WeekSchedule buildWeekSchedule(List<ScheduleItem> allItems,
                                          int weekNumber,
                                          String weekType,
                                          LocalDate mondayOfWeek) {
        return buildWeekSchedule(allItems, weekNumber, weekType, mondayOfWeek, null, null, false);
    }

    /**
     * Расписание на учебную неделю (ПН-СБ) со слиянием переносов.
     * @param allTransfers все переносы за неделю (любых групп/преподавателей) —
     *                     фильтрация по конкретной группе/преподавателю происходит внутри.
     * @param matchName    имя группы или преподавателя, для которого строим расписание.
     * @param isGroup      true — matchName это группа, false — преподаватель.
     */
    public WeekSchedule buildWeekSchedule(List<ScheduleItem> allItems,
                                          int weekNumber,
                                          String weekType,
                                          LocalDate mondayOfWeek,
                                          List<TransferItem> allTransfers,
                                          String matchName,
                                          boolean isGroup) {
        WeekSchedule week = new WeekSchedule(weekNumber, "even".equals(weekType));
        List<DaySchedule> days = new ArrayList<>();
        for (int dow = 1; dow <= 6; dow++) {
            LocalDate date = DateUtils.dateForDayInWeek(mondayOfWeek, dow);
            DaySchedule day = new DaySchedule(dow, date);
            if (holidayChecker.isDayOff(date)) {
                day.setDayOff(true);
                String hn = holidayChecker.getHolidayName(date);
                if (hn != null) day.setHolidayName(hn);
            }
            List<ScheduleItem> lessons = filterLessonsForDay(allItems, dow, weekType, weekNumber);
            List<TransferItem> dayTransfers = filterTransfersForDate(allTransfers, date, matchName, isGroup);
            if (!dayTransfers.isEmpty()) {
                lessons = mergeTransfers(lessons, dayTransfers);
            }
            day.setLessons(lessons);
            days.add(day);
        }
        week.setDays(days);
        return week;
    }

    /** Расписание на один день без переносов (обратная совместимость). */
    public DaySchedule buildDaySchedule(List<ScheduleItem> allItems,
                                        int dayOfWeek,
                                        int weekNumber,
                                        String weekType,
                                        LocalDate date) {
        return buildDaySchedule(allItems, dayOfWeek, weekNumber, weekType, date, null, null, false);
    }

    /** Расписание на один день со слиянием переносов. */
    public DaySchedule buildDaySchedule(List<ScheduleItem> allItems,
                                        int dayOfWeek,
                                        int weekNumber,
                                        String weekType,
                                        LocalDate date,
                                        List<TransferItem> allTransfers,
                                        String matchName,
                                        boolean isGroup) {
        DaySchedule day = new DaySchedule(dayOfWeek, date);
        if (holidayChecker.isDayOff(date)) {
            day.setDayOff(true);
            String hn = holidayChecker.getHolidayName(date);
            if (hn != null) day.setHolidayName(hn);
        }
        List<ScheduleItem> lessons = filterLessonsForDay(allItems, dayOfWeek, weekType, weekNumber);
        List<TransferItem> dayTransfers = filterTransfersForDate(allTransfers, date, matchName, isGroup);
        if (!dayTransfers.isEmpty()) {
            lessons = mergeTransfers(lessons, dayTransfers);
        }
        day.setLessons(lessons);
        return day;
    }

    private List<TransferItem> filterTransfersForDate(List<TransferItem> all, LocalDate date,
                                                       String matchName, boolean isGroup) {
        List<TransferItem> out = new ArrayList<>();
        if (all == null || matchName == null) return out;
        String isoDate = date.toString();
        for (TransferItem t : all) {
            if (!isoDate.equals(t.getDate())) continue;
            boolean matches = isGroup ? t.matchesGroup(matchName) : t.matchesTeacher(matchName);
            if (matches) out.add(t);
        }
        return out;
    }

    /** Слияние: применяет REMOVAL/ADDITION/ROOM_CHANGE к списку пар одного дня. */
    private List<ScheduleItem> mergeTransfers(List<ScheduleItem> original, List<TransferItem> transfers) {
        List<ScheduleItem> result = new ArrayList<>(original);

        // 1) отмены — помечаем совпавшую по номеру пары запись как cancelled
        for (TransferItem t : transfers) {
            if (!TransferItem.KIND_REMOVAL.equals(t.getKind())) continue;
            for (ScheduleItem it : result) {
                if (it.getLessonNumber() == t.getLessonNumber() && !it.isCancelled()) {
                    it.setCancelled(true);
                    it.setTransferNote("Перенесено на другое время");
                    break; // одна отмена — одна пара
                }
            }
        }

        // 2) добавления — новые пары, пришедшие переносом
        for (TransferItem t : transfers) {
            if (!TransferItem.KIND_ADDITION.equals(t.getKind())) continue;
            ScheduleItem synthetic = new ScheduleItem();
            synthetic.setDayOfWeek(0); // не используется для отображения — уже привязано к дате
            synthetic.setLessonNumber(t.getLessonNumber());
            synthetic.setSubjectName(t.getSubjectName());
            synthetic.setTeacherName(t.getEffectiveTeacher());
            synthetic.setGroupName(t.getGroupName());
            synthetic.setRoom(t.getRoom());
            // Журнал переносов не фиксирует тип занятия (Л/П/ЛР) — оставляем
            // пустым, UI показывает бейдж "Перенос" вместо кода типа для таких пар.
            synthetic.setLessonType("");
            synthetic.setMovedIn(true);
            synthetic.setTransferNote(t.getNote() != null ? t.getNote() : "Перенесённое занятие");
            result.add(synthetic);
        }

        // 3) смена аудитории — патчим room у совпавшей по номеру пары записи
        for (TransferItem t : transfers) {
            if (!TransferItem.KIND_ROOM_CHANGE.equals(t.getKind())) continue;
            for (ScheduleItem it : result) {
                if (it.getLessonNumber() == t.getLessonNumber() && !it.isCancelled()) {
                    it.setRoom(t.getRoom());
                    it.setRoomChanged(true);
                    String note = "Замена аудитории";
                    if (t.getNote() != null && !t.getNote().isEmpty()) note += " (" + t.getNote() + ")";
                    it.setTransferNote(note);
                    break;
                }
            }
        }

        result.sort((a, b) -> Integer.compare(a.getLessonNumber(), b.getLessonNumber()));
        return result;
    }

    private List<ScheduleItem> filterLessonsForDay(List<ScheduleItem> items,
                                                   int dayOfWeek,
                                                   String weekType,
                                                   int weekNumber) {
        List<ScheduleItem> out = new ArrayList<>();
        if (items == null) return out;
        for (ScheduleItem it : items) {
            if (it.getDayOfWeek() == dayOfWeek
                    && weekType != null && weekType.equals(it.getWeekType())
                    && it.isActiveOnWeek(weekNumber)) {
                out.add(it);
            }
        }
        return out;
    }

    /** Человекочитаемое название типа занятия. */
    public static String formatLessonType(String lessonType) {
        if (lessonType == null) return "";
        switch (lessonType) {
            case Constants.LESSON_TYPE_LECTURE: return "Лекция";
            case Constants.LESSON_TYPE_PRACTICE: return "Практика";
            case Constants.LESSON_TYPE_LAB: return "Лаб. работа";
            case Constants.LESSON_TYPE_ONLINE_LECTURE: return "Лекция (онлайн)";
            case Constants.LESSON_TYPE_ONLINE_PRACTICE: return "Практика (онлайн)";
            case Constants.LESSON_TYPE_EXAM: return "Экзамен";
            default: return lessonType;
        }
    }

    /** Короткая метка типа занятия (для бейджа). */
    public static String shortLessonType(String lessonType) {
        if (lessonType == null) return "";
        switch (lessonType) {
            case Constants.LESSON_TYPE_LECTURE: return "Л";
            case Constants.LESSON_TYPE_PRACTICE: return "П";
            case Constants.LESSON_TYPE_LAB: return "ЛР";
            case Constants.LESSON_TYPE_ONLINE_LECTURE: return "оЛ";
            case Constants.LESSON_TYPE_ONLINE_PRACTICE: return "оП";
            case Constants.LESSON_TYPE_EXAM: return "Экз";
            default: return lessonType;
        }
    }
}
