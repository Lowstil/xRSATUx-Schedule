package com.university.schedule.model;

public class LessonNote {
    private long id;
    private String date; // ISO "2026-10-29"
    private int lessonNumber;
    private String subjectName;
    private String noteText;
    private boolean isCompleted;
    private String createdAt;

    public LessonNote() {
    }

    public LessonNote(long id, String date, int lessonNumber, String subjectName, String noteText, boolean isCompleted, String createdAt) {
        this.id = id;
        this.date = date;
        this.lessonNumber = lessonNumber;
        this.subjectName = subjectName;
        this.noteText = noteText;
        this.isCompleted = isCompleted;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public int getLessonNumber() { return lessonNumber; }
    public void setLessonNumber(int lessonNumber) { this.lessonNumber = lessonNumber; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }
    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
