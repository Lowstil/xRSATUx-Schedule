package com.university.schedule.data;

import com.university.schedule.data.db.ScheduleDao;
import com.university.schedule.model.ScheduleItem;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Юнит-тесты для ExcelParser.parseLine — самой критичной части парсера,
 * где уже находили реальные баги (см. историю правок):
 *  - взятие последнего вместо первого совпадения TYPE_PATTERN ломало разбор
 *    предметов вроде "Английский язык", если инициалы преподавателя (Л.А.)
 *    случайно совпадали с кодом типа занятия;
 *  - GROUP_TOKEN не распознавал группы со строчными буквами (ИВБк-24),
 *    из-за чего вторая группа в ячейке с несколькими группами утекала в
 *    название предмета.
 * Эти тесты фиксируют оба случая как регрессионные — если кто-то в будущем
 * случайно вернёт старое поведение, тесты покраснеют раньше, чем баг
 * доберётся до пользователей.
 */
public class ExcelParserTest {

    private final ExcelParser parser = new ExcelParser();

    private ScheduleItem parse(String cellText) {
        return parser.parseLine(cellText, "odd", 1, 1, "ИВБ-24", ScheduleDao.SOURCE_GROUP);
    }

    @Test
    public void parsesSimpleLecture() {
        ScheduleItem item = parse("ИВБ-24 ИВБк-24 Системное программное обеспечение Л Сизов П.В. Г-519");
        assertEquals("Системное программное обеспечение", item.getSubjectName());
        assertEquals("Л", item.getLessonType());
        assertEquals("Сизов П.В.", item.getTeacherName());
        assertEquals("Г-519", item.getRoom());
    }

    @Test
    public void regression_englishLanguageDoesNotStealTeacherInitialAsType() {
        // Баг: тип "П" (практика) подменялся на "Л" из инициала "Л.А.",
        // потому что старый код брал ПОСЛЕДНЕЕ совпадение регэкспа типа
        // занятия вместо ПЕРВОГО.
        ScheduleItem item = parse("ИВБ-24 ИВБк-24 Английский язык П Петрова Л.А. 3-201");
        assertEquals("Английский язык", item.getSubjectName());
        assertEquals("П", item.getLessonType());
        assertEquals("Петрова Л.А.", item.getTeacherName());
        assertEquals("3-201", item.getRoom());
    }

    @Test
    public void regression_lowercaseGroupSuffixDoesNotLeakIntoSubject() {
        // Баг: GROUP_TOKEN не распознавал группы со строчной буквой в суффиксе
        // (например "к" в "ИВБк-24"), поэтому вторая группа в ячейке с
        // несколькими группами оставалась приклеенной к названию предмета.
        ScheduleItem item = parse("ИВБ-24 ИВБк-24 Английский язык П Петрова Л.А. 3-201");
        assertEquals("Английский язык", item.getSubjectName());
        assertTrue("Название предмета не должно содержать токен группы",
                !item.getSubjectName().contains("ИВБк"));
    }

    @Test
    public void parsesWeekRangeInParentheses() {
        ScheduleItem item = parse(
                "КРБ-24 Аналитические и функционально-параметр. \"цифровые\" двойники " +
                "усилительных каскадов беспилотных авиационных систем Л (Недели 1-16) Печаткин А.В. Г-425");
        assertEquals("1-16", item.getWeekSpec());
        assertEquals("Л", item.getLessonType());
        assertEquals("Печаткин А.В.", item.getTeacherName());
        assertEquals("Г-425", item.getRoom());
        assertTrue("Диапазон недель не должен остаться внутри названия предмета",
                !item.getSubjectName().contains("Недели"));
    }

    @Test
    public void parsesLabWork() {
        ScheduleItem item = parse("ИПБ-25-1 Основы программирования ЛР Шаров В.Г. Г-512");
        assertEquals("Основы программирования", item.getSubjectName());
        assertEquals("ЛР", item.getLessonType());
        assertEquals("Шаров В.Г.", item.getTeacherName());
        assertEquals("Г-512", item.getRoom());
    }

    @Test
    public void parsesOnlinePracticeWithMultiWordSubjectAndOnlineRoom() {
        ScheduleItem item = parse(
                "МЛБ2-25 ТЭБ-25 Практические занятия по физической культуре (общая группа) оП Шитиков С.В. Онлайн");
        assertEquals("оП", item.getLessonType());
        assertEquals("Шитиков С.В.", item.getTeacherName());
        assertEquals("Онлайн", item.getRoom());
        assertTrue(item.isOnline());
    }

    @Test
    public void emptyOrBlankCellReturnsNull() {
        assertNull(parse(""));
        assertNull(parse("   "));
        assertNull(parse(null));
    }

    @Test
    public void groupNameAssignedFromColumnForGroupSheet() {
        ScheduleItem item = parser.parseLine(
                "Основы программирования ЛР Шаров В.Г. Г-512", "even", 2, 3,
                "ИПБ-25-1", ScheduleDao.SOURCE_GROUP);
        assertEquals("ИПБ-25-1", item.getGroupName());
    }

    @Test
    public void teacherColumnOverridesTeacherNameForTeacherSheet() {
        // На листе "преподаватели" колонка сама и есть имя преподавателя —
        // важно, чтобы оно не терялось и не путалось с тем, что распарсено из ячейки.
        ScheduleItem item = parser.parseLine(
                "ИПБ-25-1 Основы программирования ЛР Г-512", "even", 2, 3,
                "Шаров В.Г.", ScheduleDao.SOURCE_TEACHER);
        assertEquals("Шаров В.Г.", item.getTeacherName());
    }
}
