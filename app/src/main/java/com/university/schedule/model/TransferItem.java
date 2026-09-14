package com.university.schedule.model;

/**
 * Одна запись переноса/замены занятия — привязана к конкретной календарной
 * дате (не к дню недели + чётности, как обычное расписание).
 *
 * kind:
 *  - REMOVAL     — исходное занятие в эту дату/пару отменяется (перенесено).
 *  - ADDITION    — занятие проводится в эту дату/пару взамен отменённого
 *                  (может быть в другой день, на другой неделе, даже в другом месяце).
 *  - ROOM_CHANGE — тот же день/пара, что и в обычном расписании, но
 *                  меняется аудитория (и, возможно, преподаватель) — из листа "Аудитории".
 */
public class TransferItem {

    public static final String KIND_REMOVAL = "removal";
    public static final String KIND_ADDITION = "addition";
    public static final String KIND_ROOM_CHANGE = "room_change";

    private long id;
    private String kind;
    private String date;              // ISO "2026-02-09"
    private int lessonNumber;         // 1..7
    private String groupName;         // может содержать несколько групп через пробел
    private String subjectName;
    private String teacherName;       // основной преподаватель по журналу переносов
    private String substituteTeacher; // заменяющий преподаватель, если есть
    private String room;
    private long linkId;              // связывает REMOVAL и ADDITION одной строки листа "Переносы"
    private String note;              // например "Перенос из 1-210"

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getLessonNumber() { return lessonNumber; }
    public void setLessonNumber(int lessonNumber) { this.lessonNumber = lessonNumber; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getSubstituteTeacher() { return substituteTeacher; }
    public void setSubstituteTeacher(String substituteTeacher) { this.substituteTeacher = substituteTeacher; }

    /** Преподаватель, который реально ведёт занятие (замена, если назначена, иначе основной). */
    public String getEffectiveTeacher() {
        return (substituteTeacher != null && !substituteTeacher.trim().isEmpty())
                ? substituteTeacher : teacherName;
    }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public long getLinkId() { return linkId; }
    public void setLinkId(long linkId) { this.linkId = linkId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    /**
     * Совпадает ли группа пользователя с одним из токенов в groupName.
     *
     * ВАЖНО: журнал переносов пишет то полную подгруппу ("ИПБ-24-2"), то
     * только родительскую группу без подгруппы ("ИПБ-24") — во втором
     * случае перенос относится ко ВСЕМ подгруппам этой группы сразу
     * (лектор один на весь курс, просто подгруппы физически объединены).
     * Раньше здесь была только точная строка-в-строку проверка, из-за чего
     * перенос с группой "ИПБ-24" в журнале не находил студента, выбравшего
     * "ИПБ-24-1" или "ИПБ-24-2" — переносы для таких студентов просто не
     * применялись.
     *
     * Правило: myGroup ("ИПБ-24-2") считается подгруппой токена ("ИПБ-24"),
     * если myGroup = токен + "-" + (одна и более цифр), т.е. отличие —
     * ровно один дополнительный суффикс "-N" в конце. Это защищает от
     * случайных совпадений вроде "ИПБ-2" внутри "ИПБ-24" (там суффикс не
     * начинается с дефиса перед цифрой на границе токена).
     */
    public boolean matchesGroup(String group) {
        if (groupName == null || group == null || group.isEmpty()) return false;
        for (String token : groupName.trim().split("\\s+")) {
            if (token.equalsIgnoreCase(group)) return true;
            if (isSubgroupOf(group, token)) return true;
        }
        return false;
    }

    /** true, если myGroup — подгруппа parentToken (см. matchesGroup). */
    private static boolean isSubgroupOf(String myGroup, String parentToken) {
        if (myGroup.length() <= parentToken.length()) return false;
        if (!myGroup.regionMatches(true, 0, parentToken, 0, parentToken.length())) return false;
        String rest = myGroup.substring(parentToken.length());
        // rest должен быть вида "-2", "-12" и т.п. — дефис и только цифры дальше.
        if (rest.length() < 2 || rest.charAt(0) != '-') return false;
        for (int i = 1; i < rest.length(); i++) {
            if (!Character.isDigit(rest.charAt(i))) return false;
        }
        return true;
    }

    /**
     * Совпадает ли по преподавателю — либо основной, либо заменяющий.
     *
     * ВАЖНО: журнал переносов пишет ФИО ПОЛНОСТЬЮ ("Гагарина Алиса
     * Игоревна"), а основной файл расписания (и, соответственно, выбор
     * пользователя в приложении) использует сокращённую форму "Фамилия И.О."
     * ("Гагарина А.И.") — это видно по регэкспу разбора преподавателя в
     * ExcelParser (INITIALS/NAME_WORD собирают именно инициалы, а не полные
     * имена). Точное сравнение строк между этими двумя форматами никогда не
     * совпадало, поэтому переносы у преподавателей не показывались вообще —
     * не только у некоторых, а у всех сразу, что и было замечено. Теперь
     * обе стороны приводятся к единому каноничному виду "Фамилия И.О."
     * перед сравнением — это работает независимо от того, в каком из двух
     * форматов реально пришла строка с обеих сторон.
     */
    public boolean matchesTeacher(String teacher) {
        if (teacher == null || teacher.isEmpty()) return false;
        String needle = toShortForm(teacher);
        if (needle.isEmpty()) return false;
        return needle.equalsIgnoreCase(toShortForm(teacherName))
                || needle.equalsIgnoreCase(toShortForm(substituteTeacher));
    }

    /**
     * Приводит ФИО к ключу вида "фамилия|ИО" для сравнения — независимо от
     * того, пришло оно как "Фамилия И.О." (без пробела между инициалами)
     * или как "Фамилия Имя Отчество" полностью. Раньше здесь была наивная
     * реализация, которая делила строку по пробелам и склеивала обратно
     * через пробел — из-за этого "Фамилия И.О." (один токен "И.О." без
     * пробела внутри) не совпадало с результатом разбора "Фамилия Имя
     * Отчество" (тут инициалы получались "И. О." через пробел) — сравнение
     * никогда не срабатывало. Теперь из "хвоста" ФИО достаются только первые буквы
     * каждого слова (слово = последовательность букв; точки/пробелы/дефисы
     * — просто разделители), поэтому оба формата дают одинаковый ключ.
     * Фамилия (первое слово целиком, включая дефис в двойных фамилиях)
     * сравнивается отдельно и без изменений.
     */
    private static String toShortForm(String fullOrShort) {
        if (fullOrShort == null) return "";
        String s = fullOrShort.trim();
        if (s.isEmpty()) return "";
        int firstSpace = indexOfWhitespace(s);
        String surname = (firstSpace < 0 ? s : s.substring(0, firstSpace)).toLowerCase();
        String rest = firstSpace < 0 ? "" : s.substring(firstSpace + 1);

        StringBuilder initials = new StringBuilder();
        boolean atWordStart = true;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (Character.isLetter(c)) {
                if (atWordStart) {
                    initials.append(Character.toUpperCase(c));
                    atWordStart = false;
                }
            } else {
                atWordStart = true; // точка/пробел/дефис — начало нового слова
            }
        }
        return surname + "|" + initials;
    }

    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }
}
