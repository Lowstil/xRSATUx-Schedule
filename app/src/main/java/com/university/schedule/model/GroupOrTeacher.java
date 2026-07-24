package com.university.schedule.model;

/** Элемент списка выбора на экране SelectionActivity. */
public class GroupOrTeacher {
    public static final String TYPE_GROUP = "group";
    public static final String TYPE_TEACHER = "teacher";

    private String name;
    private String type;

    public GroupOrTeacher(String name, String type) { this.name = name; this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isGroup() { return TYPE_GROUP.equals(type); }

    @Override
    public String toString() { return name; }
}