package com.university.schedule.model;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Юнит-тесты для TransferItem.matchesGroup/matchesTeacher — здесь дважды
 * находили реальные баги на реальных пользовательских данных:
 *  1. Перенос для родительской группы ("ИПБ-24" без указания подгруппы)
 *     не находил студентов, выбравших подгруппу ("ИПБ-24-1"/"ИПБ-24-2").
 *  2. Журнал переносов пишет ФИО преподавателя полностью ("Гагарина Алиса
 *     Игоревна"), а расписание — сокращённо ("Гагарина А.И."); точное
 *     сравнение строк никогда не совпадало, и переносы у преподавателей
 *     не показывались вообще.
 * Тесты ниже фиксируют оба случая как регрессионные, плюс проверяют
 * граничные случаи, которые ломали бы наивные реализации обоих фиксов
 * (короткие префиксы вроде "ИПБ-2", разные подгруппы одной группы, разные
 * преподаватели с похожими фамилиями).
 */
public class TransferItemTest {

    private TransferItem withGroup(String groupField) {
        TransferItem t = new TransferItem();
        t.setGroupName(groupField);
        return t;
    }

    private TransferItem withTeacher(String teacherField, String substituteField) {
        TransferItem t = new TransferItem();
        t.setTeacherName(teacherField);
        t.setSubstituteTeacher(substituteField);
        return t;
    }

    // ---------------- matchesGroup ----------------

    @Test
    public void regression_parentGroupMatchesBothSubgroups() {
        TransferItem t = withGroup("ИПБ-24");
        assertTrue(t.matchesGroup("ИПБ-24-1"));
        assertTrue(t.matchesGroup("ИПБ-24-2"));
    }

    @Test
    public void exactSubgroupStillMatchesItself() {
        TransferItem t = withGroup("ИПБ-24-2");
        assertTrue(t.matchesGroup("ИПБ-24-2"));
    }

    @Test
    public void differentSubgroupOfSameParentDoesNotMatch() {
        TransferItem t = withGroup("ИПБ-24-2");
        assertFalse(t.matchesGroup("ИПБ-24-1"));
    }

    @Test
    public void shortUnrelatedGroupDoesNotFalseMatch() {
        // "ИПБ-2" не должна считаться родителем "ИПБ-24" — это разные группы,
        // совпадение только по префиксу без учёта границы токена было бы багом.
        TransferItem t = withGroup("ИПБ-2");
        assertFalse(t.matchesGroup("ИПБ-24"));
    }

    @Test
    public void reverseDirectionDoesNotFalseMatch() {
        TransferItem t = withGroup("ИПБ-24");
        assertFalse(t.matchesGroup("ИПБ-2"));
    }

    @Test
    public void differentGroupFamilyWithSimilarPrefixDoesNotMatch() {
        // "ИВБк-24" не подгруппа "ИВБ-24" — это отдельное название группы
        // (суффикс "к" перед дефисом, а не после), не должно ложно совпадать.
        TransferItem t = withGroup("ИВБ-24");
        assertFalse(t.matchesGroup("ИВБк-24"));
    }

    @Test
    public void multiGroupCellMatchesAnyListedToken() {
        TransferItem t = withGroup("СПДк-25 СПДт-25 ТФБ-25");
        assertTrue(t.matchesGroup("ТФБ-25"));
        assertTrue(t.matchesGroup("СПДт-25"));
        assertFalse(t.matchesGroup("СПДк-26"));
    }

    @Test
    public void multiGroupCellWithTrailingWhitespaceStillMatches() {
        TransferItem t = withGroup("СПДт-25 ");
        assertTrue(t.matchesGroup("СПДт-25"));
    }

    @Test
    public void nullOrEmptyGroupNeverMatches() {
        TransferItem t = withGroup("ИПБ-24");
        assertFalse(t.matchesGroup(null));
        assertFalse(t.matchesGroup(""));
        assertFalse(withGroup(null).matchesGroup("ИПБ-24"));
    }

    // ---------------- matchesTeacher ----------------

    @Test
    public void regression_fullNameInJournalMatchesAbbreviatedSelection() {
        TransferItem t = withTeacher("Гагарина Алиса Игоревна", null);
        assertTrue(t.matchesTeacher("Гагарина А.И."));
    }

    @Test
    public void abbreviatedVsAbbreviatedStillMatches() {
        TransferItem t = withTeacher("Гагарина А.И.", null);
        assertTrue(t.matchesTeacher("Гагарина А.И."));
    }

    @Test
    public void fullVsFullStillMatches() {
        TransferItem t = withTeacher("Гагарина Алиса Игоревна", null);
        assertTrue(t.matchesTeacher("Гагарина Алиса Игоревна"));
    }

    @Test
    public void hyphenatedSurnameSurvivesNormalization() {
        TransferItem t = withTeacher("Лаукарт-Горбачева Ольга Викторовна", null);
        assertTrue(t.matchesTeacher("Лаукарт-Горбачева О.В."));
    }

    @Test
    public void substituteTeacherAlsoMatches() {
        TransferItem t = withTeacher("Сизов Петр Викторович", "Петрова Людмила Андреевна");
        assertTrue(t.matchesTeacher("Петрова Л.А."));
        // основной преподаватель тоже остаётся действительным совпадением
        assertTrue(t.matchesTeacher("Сизов П.В."));
    }

    @Test
    public void differentSurnameDoesNotMatch() {
        TransferItem t = withTeacher("Сизов Петр Викторович", null);
        assertFalse(t.matchesTeacher("Сизова П.В."));
    }

    @Test
    public void differentTeacherEntirelyDoesNotMatch() {
        TransferItem t = withTeacher("Гагарина Алиса Игоревна", null);
        assertFalse(t.matchesTeacher("Иванов И.И."));
    }

    @Test
    public void surnameOnlyQueryDoesNotFalseMatch() {
        // Фамилия без инициалов не должна давать ложное совпадение с
        // конкретным преподавателем — иначе "Гагарина" совпала бы с любым
        // преподавателем этой фамилии, даже с другим именем.
        TransferItem t = withTeacher("Гагарина Алиса Игоревна", null);
        assertFalse(t.matchesTeacher("Гагарина"));
    }

    @Test
    public void nullOrEmptyTeacherQueryNeverMatches() {
        TransferItem t = withTeacher("Гагарина Алиса Игоревна", null);
        assertFalse(t.matchesTeacher(null));
        assertFalse(t.matchesTeacher(""));
    }
}
