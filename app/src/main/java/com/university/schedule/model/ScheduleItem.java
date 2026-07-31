package com.university.schedule.model;

/**
 * Одна запись расписания = одна пара для одной строки листа.
 * Поле weekSpec хранит диапазон/список недель в том виде, как в файле
 * (например "1-16", "15", "1-5,6,8,10,12,14,16,18", "1,18");
 * если пусто — занятие идёт весь семестр (недели 1..18).
 * Поле source = "group" или "teacher" — из какого листа взята строка
 * (нужно, чтобы при показе расписания группы не дублировались пары из листа преподавателей).
 */
public class ScheduleItem {

    private long id;
    private String weekType;       // "odd" или "even"
    private int dayOfWeek;         // 1=ПН .. 6=СБ
    private int lessonNumber;      // 1..7
    private String groupName;      // может содержать несколько групп через пробел
    private String teacherName;
    private String subjectName;
    private String lessonType;     // "Л","П","ЛР","оЛ","оП","Экзамен"
    private String room;
    private String weekSpec;       // нормализованный список недель или ""
    private String source;         // "group" или "teacher"

    // --- поля переносов/замен (заполняются на этапе слияния в ScheduleFilter,
    // НЕ хранятся в таблице schedule — исходный ScheduleItem из БД их не имеет) ---
    private boolean cancelled;     // true = занятие отменено переносом (показывать зачёркнутым/скрыто)
    private boolean movedIn;       // true = занятие добавлено переносом (пары изначально не было)
    private boolean roomChanged;   // true = аудитория изменена переносом (исходная room затёрта новой)
    private String transferNote;   // например "Перенесено с 09.02.2026, 3 пара" или "Замена: Иванов И.И."

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public boolean isMovedIn() { return movedIn; }
    public void setMovedIn(boolean movedIn) { this.movedIn = movedIn; }

    public boolean isRoomChanged() { return roomChanged; }
    public void setRoomChanged(boolean roomChanged) { this.roomChanged = roomChanged; }

    public String getTransferNote() { return transferNote; }
    public void setTransferNote(String transferNote) { this.transferNote = transferNote; }

    /** Любой вид переноса/замены — удобный флаг для UI (бейдж/подсветка). */
    public boolean isTransferred() { return movedIn || roomChanged || (transferNote != null && !transferNote.isEmpty()); }

    public ScheduleItem() {
        this.weekSpec = "";
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getWeekType() { return weekType; }
    public void setWeekType(String weekType) { this.weekType = weekType; }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public int getLessonNumber() { return lessonNumber; }
    public void setLessonNumber(int lessonNumber) { this.lessonNumber = lessonNumber; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getLessonType() { return lessonType; }
    public void setLessonType(String lessonType) { this.lessonType = lessonType; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getWeekSpec() { return weekSpec; }
    public void setWeekSpec(String weekSpec) { this.weekSpec = weekSpec == null ? "" : weekSpec; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    /** Онлайн-занятие (тип начинается с "о": оЛ, оП). */
    public boolean isOnline() {
        return lessonType != null && lessonType.startsWith("о");
    }

    /**
     * Идёт ли занятие на заданной учебной неделе (1..18).
     * Пустая спека = весь семестр. Иначе разбираем список вида "1-5,6,8,10-12".
     */
    public boolean isActiveOnWeek(int week) {
        if (weekSpec == null || weekSpec.trim().isEmpty()) {
            return true;
        }
        String s = weekSpec.replaceAll("\\s+", "");
        for (String part : s.split(",")) {
            if (part.isEmpty()) continue;
            int dash = part.indexOf('-');
            if (dash >= 0) {
                try {
                    int a = Integer.parseInt(part.substring(0, dash));
                    int b = Integer.parseInt(part.substring(dash + 1));
                    if (week >= a && week <= b) return true;
                } catch (NumberFormatException ignored) { }
            } else {
                try {
                    if (Integer.parseInt(part) == week) return true;
                } catch (NumberFormatException ignored) { }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ScheduleItem{" + weekType + ", d=" + dayOfWeek + ", p=" + lessonNumber +
                ", subj='" + subjectName + "', type='" + lessonType +
                "', room='" + room + "', weeks='" + weekSpec + "'}";
    }
}