package com.university.schedule.model;

import java.time.LocalDate;

/**
 * Пользовательское событие в расписании (разовое по dateIso или повторяющееся по dayOfWeek + weekType).
 */
public class CustomEvent {

    private long id;
    private String groupName;
    private boolean isGroup;
    private String dateIso;      // "2026-09-01" для разового события, или null/пусто для повторяющегося
    private int dayOfWeek;       // 1=ПН .. 6=СБ
    private String weekType;     // "odd", "even", "both" или ""
    private int lessonNumber;    // 1..7
    private String subjectName;
    private String teacherName;
    private String room;
    private String note;

    public CustomEvent() {
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }

    public String getDateIso() { return dateIso; }
    public void setDateIso(String dateIso) { this.dateIso = dateIso; }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getWeekType() { return weekType; }
    public void setWeekType(String weekType) { this.weekType = weekType; }

    public int getLessonNumber() { return lessonNumber; }
    public void setLessonNumber(int lessonNumber) { this.lessonNumber = lessonNumber; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    /** Проверяет, подходит ли событие под конкретную календарную дату и тип недели. */
    public boolean matchesDate(LocalDate date, int dow, String targetWeekType) {
        if (dateIso != null && !dateIso.trim().isEmpty()) {
            return date != null && dateIso.trim().equals(date.toString());
        }
        if (dayOfWeek != dow) return false;
        if (weekType == null || weekType.trim().isEmpty() || "both".equalsIgnoreCase(weekType.trim())) {
            return true;
        }
        return targetWeekType != null && weekType.trim().equalsIgnoreCase(targetWeekType);
    }

    /** Преобразует событие в ScheduleItem для отображения в расписании. */
    public ScheduleItem toScheduleItem(int dow) {
        ScheduleItem item = new ScheduleItem();
        item.setCustom(true);
        item.setCustomId(id);
        item.setDayOfWeek(dow);
        item.setLessonNumber(lessonNumber);
        item.setSubjectName(subjectName);
        item.setTeacherName(teacherName);
        item.setRoom(room);
        item.setLessonType("");
        item.setWeekType(weekType);
        item.setTransferNote(note);
        if (isGroup) {
            item.setGroupName(groupName);
        } else {
            item.setTeacherName(teacherName != null && !teacherName.isEmpty() ? teacherName : groupName);
        }
        return item;
    }
}
