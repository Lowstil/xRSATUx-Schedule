package com.university.schedule.model;

import com.university.schedule.util.DateUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель изменений (diff) между предыдущим и новым расписанием выбранной группы/преподавателя.
 */
public class ScheduleDiff {

    public enum ChangeType {
        ADDED,
        REMOVED,
        ROOM_CHANGED,
        TEACHER_CHANGED
    }

    public static class DiffEntry {
        public final ChangeType type;
        public final String weekType;
        public final int dayOfWeek;
        public final int lessonNumber;
        public final String subjectName;
        public final String oldValue;
        public final String newValue;

        public DiffEntry(ChangeType type, String weekType, int dayOfWeek, int lessonNumber,
                         String subjectName, String oldValue, String newValue) {
            this.type = type;
            this.weekType = weekType != null ? weekType : "";
            this.dayOfWeek = dayOfWeek;
            this.lessonNumber = lessonNumber;
            this.subjectName = subjectName != null ? subjectName : "";
            this.oldValue = oldValue != null ? oldValue : "";
            this.newValue = newValue != null ? newValue : "";
        }
    }

    private final String selectionType;
    private final String selectionName;
    private final String timestamp;
    private final List<DiffEntry> entries;

    public ScheduleDiff(String selectionType, String selectionName, String timestamp, List<DiffEntry> entries) {
        this.selectionType = selectionType != null ? selectionType : "";
        this.selectionName = selectionName != null ? selectionName : "";
        this.timestamp = timestamp != null ? timestamp : "";
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public String getSelectionType() { return selectionType; }
    public String getSelectionName() { return selectionName; }
    public String getTimestamp() { return timestamp; }
    public List<DiffEntry> getEntries() { return entries; }
    public boolean hasChanges() { return entries != null && !entries.isEmpty(); }

    public static ScheduleDiff compute(String selectionType, String selectionName,
                                       List<ScheduleItem> oldItems, List<ScheduleItem> newItems) {
        Map<String, ScheduleItem> oldMap = indexByKey(oldItems);
        Map<String, ScheduleItem> newMap = indexByKey(newItems);
        List<DiffEntry> diffs = new ArrayList<>();
        boolean isGroup = "group".equals(selectionType);

        for (Map.Entry<String, ScheduleItem> e : newMap.entrySet()) {
            ScheduleItem n = e.getValue();
            ScheduleItem o = oldMap.get(e.getKey());
            if (o == null) {
                diffs.add(new DiffEntry(
                        ChangeType.ADDED,
                        n.getWeekType(), n.getDayOfWeek(), n.getLessonNumber(),
                        n.getSubjectName(), "", formatSlotInfo(n, isGroup)));
            } else {
                String oldRoom = norm(o.getRoom());
                String newRoom = norm(n.getRoom());
                if (!oldRoom.equals(newRoom)) {
                    diffs.add(new DiffEntry(
                            ChangeType.ROOM_CHANGED,
                            n.getWeekType(), n.getDayOfWeek(), n.getLessonNumber(),
                            n.getSubjectName(), oldRoom, newRoom));
                }
                String oldParty = isGroup ? norm(o.getTeacherName()) : norm(o.getGroupName());
                String newParty = isGroup ? norm(n.getTeacherName()) : norm(n.getGroupName());
                if (!oldParty.equals(newParty)) {
                    diffs.add(new DiffEntry(
                            ChangeType.TEACHER_CHANGED,
                            n.getWeekType(), n.getDayOfWeek(), n.getLessonNumber(),
                            n.getSubjectName(), oldParty, newParty));
                }
            }
        }

        for (Map.Entry<String, ScheduleItem> e : oldMap.entrySet()) {
            if (!newMap.containsKey(e.getKey())) {
                ScheduleItem o = e.getValue();
                diffs.add(new DiffEntry(
                        ChangeType.REMOVED,
                        o.getWeekType(), o.getDayOfWeek(), o.getLessonNumber(),
                        o.getSubjectName(), formatSlotInfo(o, isGroup), ""));
            }
        }

        diffs.sort(Comparator.comparing((DiffEntry d) -> d.weekType)
                .thenComparingInt(d -> d.dayOfWeek)
                .thenComparingInt(d -> d.lessonNumber)
                .thenComparing(d -> d.type.name()));

        return new ScheduleDiff(selectionType, selectionName, DateUtils.nowIsoDateTime(), diffs);
    }

    private static Map<String, ScheduleItem> indexByKey(List<ScheduleItem> items) {
        Map<String, ScheduleItem> map = new HashMap<>();
        if (items == null) return map;
        for (ScheduleItem it : items) {
            String key = norm(it.getWeekType()) + "#" + it.getDayOfWeek() + "#" + it.getLessonNumber()
                    + "#" + norm(it.getSubjectName()).toLowerCase() + "#" + norm(it.getLessonType()).toLowerCase();
            map.putIfAbsent(key, it);
        }
        return map;
    }

    private static String formatSlotInfo(ScheduleItem it, boolean isGroup) {
        StringBuilder sb = new StringBuilder();
        if (it.getLessonType() != null && !it.getLessonType().isEmpty()) {
            sb.append(it.getLessonType());
        }
        if (it.getRoom() != null && !it.getRoom().isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("ауд. ").append(it.getRoom());
        }
        String party = isGroup ? it.getTeacherName() : it.getGroupName();
        if (party != null && !party.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(party);
        }
        return sb.toString();
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }

    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("selectionType", selectionType);
            root.put("selectionName", selectionName);
            root.put("timestamp", timestamp);
            JSONArray arr = new JSONArray();
            for (DiffEntry d : entries) {
                JSONObject obj = new JSONObject();
                obj.put("type", d.type.name());
                obj.put("weekType", d.weekType);
                obj.put("dayOfWeek", d.dayOfWeek);
                obj.put("lessonNumber", d.lessonNumber);
                obj.put("subjectName", d.subjectName);
                obj.put("oldValue", d.oldValue);
                obj.put("newValue", d.newValue);
                arr.put(obj);
            }
            root.put("entries", arr);
            return root.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static ScheduleDiff fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(json);
            String selType = root.optString("selectionType", "");
            String selName = root.optString("selectionName", "");
            String ts = root.optString("timestamp", "");
            JSONArray arr = root.optJSONArray("entries");
            List<DiffEntry> list = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    ChangeType ct = ChangeType.valueOf(obj.getString("type"));
                    list.add(new DiffEntry(
                            ct,
                            obj.optString("weekType", ""),
                            obj.optInt("dayOfWeek", 1),
                            obj.optInt("lessonNumber", 1),
                            obj.optString("subjectName", ""),
                            obj.optString("oldValue", ""),
                            obj.optString("newValue", "")
                    ));
                }
            }
            return new ScheduleDiff(selType, selName, ts, list);
        } catch (Exception e) {
            return null;
        }
    }
}
