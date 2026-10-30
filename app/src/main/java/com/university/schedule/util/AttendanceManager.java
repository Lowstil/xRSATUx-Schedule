package com.university.schedule.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.TransferItem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Менеджер учёта посещаемости и статистики пропусков ("Н-ок").
 * Подсчитывает количество запланированных пар, количество пропусков и % посещаемости
 * по каждому предмету и в целом за текущий семестр.
 * Если по предмету накопилось >= 3 пропусков, выдаёт предупреждение:
 * "🟡 Внимание: уже X Н-ок, пора ходить!".
 */
public class AttendanceManager {
    public static final int DEFAULT_WARNING_THRESHOLD = 3;
    private static final String PREF_ATTENDANCE_MANUAL_PREFIX = "att_manual_";

    public static class SubjectAttendance {
        public final String subjectName;
        public int totalPlanned;
        public int missedCount;
        public int attendedCount;
        public int attendancePercent;

        public SubjectAttendance(String subjectName) {
            this.subjectName = subjectName;
        }

        public boolean hasWarning() {
            return missedCount >= DEFAULT_WARNING_THRESHOLD;
        }
    }

    public static class OverallAttendance {
        public int totalPlanned;
        public int missedCount;
        public int attendedCount;
        public int attendancePercent;
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final SkippedLessonsManager skippedMgr;
    private final ExcludedSubjectsManager excludedMgr;

    private String cachedGroup;
    private LocalDate cachedDate;
    private Map<String, SubjectAttendance> cachedStats;
    private OverallAttendance cachedOverall;

    public AttendanceManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.skippedMgr = new SkippedLessonsManager(this.context);
        this.excludedMgr = new ExcludedSubjectsManager(this.context);
    }

    public synchronized void invalidateCache() {
        cachedGroup = null;
        cachedStats = null;
        cachedOverall = null;
    }

    public synchronized Map<String, SubjectAttendance> getStatsMap(String groupName) {
        LocalDate today = DateUtils.todayMoscow();
        if (cachedStats != null && groupName != null && groupName.equalsIgnoreCase(cachedGroup)
                && today.equals(cachedDate)) {
            return cachedStats;
        }
        recalculate(groupName, today);
        return cachedStats != null ? cachedStats : Collections.emptyMap();
    }

    public synchronized List<SubjectAttendance> getSubjectStatsList(String groupName) {
        Map<String, SubjectAttendance> map = getStatsMap(groupName);
        List<SubjectAttendance> list = new ArrayList<>(map.values());
        Collections.sort(list, (a, b) -> {
            if (a.missedCount != b.missedCount) {
                return Integer.compare(b.missedCount, a.missedCount);
            }
            return a.subjectName.compareToIgnoreCase(b.subjectName);
        });
        return list;
    }

    public synchronized OverallAttendance getOverallStats(String groupName) {
        getStatsMap(groupName);
        return cachedOverall != null ? cachedOverall : new OverallAttendance();
    }

    public String getWarningForSubject(String subjectName) {
        if (subjectName == null || subjectName.trim().isEmpty()) return null;
        ScheduleRepository repo = ScheduleRepository.getInstance(context);
        String group = repo.getSelectionName();
        if (group == null) return null;

        Map<String, SubjectAttendance> map = getStatsMap(group);
        SubjectAttendance sa = map.get(subjectName.trim());
        if (sa != null && sa.hasWarning()) {
            return "🟡 Внимание: уже " + formatSkips(sa.missedCount) + ", пора ходить!";
        }
        return null;
    }

    public void adjustManualSkips(String groupName, String subjectName, int delta) {
        if (groupName == null || subjectName == null) return;
        String key = PREF_ATTENDANCE_MANUAL_PREFIX + groupName.trim() + "_" + subjectName.trim();
        int current = prefs.getInt(key, 0);
        int next = current + delta;
        prefs.edit().putInt(key, next).apply();
        invalidateCache();
    }

    private int getManualAdjustment(String groupName, String subjectName) {
        if (groupName == null || subjectName == null) return 0;
        String key = PREF_ATTENDANCE_MANUAL_PREFIX + groupName.trim() + "_" + subjectName.trim();
        return prefs.getInt(key, 0);
    }

    private void recalculate(String groupName, LocalDate today) {
        cachedGroup = groupName;
        cachedDate = today;
        cachedStats = new HashMap<>();
        cachedOverall = new OverallAttendance();

        if (groupName == null || groupName.isEmpty()) return;

        ScheduleRepository repo = ScheduleRepository.getInstance(context);
        LocalDate semStart = repo.getSemesterStart();
        if (semStart == null) return;

        WeekCalculator calc = repo.getWeekCalculator();
        Set<String> excluded = excludedMgr.getExcludedSubjects(groupName);

        // Сканируем от начала семестра до today + 14 дней (чтобы учесть заранее отмеченные пары)
        LocalDate scanEnd = today.plusDays(14);
        LocalDate cursor = semStart;
        while (!cursor.isAfter(scanEnd)) {
            int dow = DateUtils.toScheduleDayOfWeek(cursor);
            if (dow >= 1 && dow <= 6) {
                int weekNum = calc.getWeekNumber(cursor);
                if (weekNum >= 1 && weekNum <= 18) {
                    String wt = (weekNum % 2 == 0) ? Constants.WEEK_TYPE_EVEN : Constants.WEEK_TYPE_ODD;
                    List<ScheduleItem> rawLessons = repo.getScheduleForGroupAndWeekType(groupName, wt);
                    List<TransferItem> transfers = repo.getTransfersForDate(cursor.toString());

                    boolean isFuture = cursor.isAfter(today);

                    if (rawLessons != null) {
                        for (ScheduleItem item : rawLessons) {
                            if (item.getDayOfWeek() != dow) continue;
                            if (item.isCancelled()) continue;
                            String sub = item.getSubjectName() != null ? item.getSubjectName().trim() : "";
                            if (sub.isEmpty() || (excluded != null && excluded.contains(sub))) continue;

                            if (!item.isActiveOnWeek(weekNum)) continue;

                            boolean isSkipped = skippedMgr.isSkipped(cursor.toString(), item.getLessonNumber(), sub);
                            boolean isAttended = skippedMgr.isAttended(cursor.toString(), item.getLessonNumber(), sub);

                            // Будущие пары учитываем только если пользователь заранее выставил им отметку
                            if (isFuture && !isSkipped && !isAttended) continue;

                            SubjectAttendance sa = cachedStats.get(sub);
                            if (sa == null) {
                                sa = new SubjectAttendance(sub);
                                cachedStats.put(sub, sa);
                            }
                            sa.totalPlanned++;

                            if (isSkipped) {
                                sa.missedCount++;
                            } else {
                                sa.attendedCount++;
                            }
                        }
                    }

                    // Учитываем переносы, перенесённые на этот день
                    if (transfers != null) {
                        for (TransferItem t : transfers) {
                            if (TransferItem.KIND_REMOVAL.equals(t.getKind())) continue;
                            if (!t.matchesGroup(groupName)) continue;
                            String sub = t.getSubjectName() != null ? t.getSubjectName().trim() : "";
                            if (sub.isEmpty() || (excluded != null && excluded.contains(sub))) continue;

                            boolean isSkipped = skippedMgr.isSkipped(cursor.toString(), t.getLessonNumber(), sub);
                            boolean isAttended = skippedMgr.isAttended(cursor.toString(), t.getLessonNumber(), sub);

                            if (isFuture && !isSkipped && !isAttended) continue;

                            SubjectAttendance sa = cachedStats.get(sub);
                            if (sa == null) {
                                sa = new SubjectAttendance(sub);
                                cachedStats.put(sub, sa);
                            }
                            sa.totalPlanned++;
                            if (isSkipped) {
                                sa.missedCount++;
                            } else {
                                sa.attendedCount++;
                            }
                        }
                    }
                }
            }
            cursor = cursor.plusDays(1);
        }

        // Применяем ручные корректировки и пересчитываем проценты
        int totalPlannedAll = 0;
        int totalMissedAll = 0;

        for (SubjectAttendance sa : cachedStats.values()) {
            int adj = getManualAdjustment(groupName, sa.subjectName);
            sa.missedCount = Math.max(0, sa.missedCount + adj);
            if (sa.missedCount > sa.totalPlanned && sa.totalPlanned > 0) {
                sa.totalPlanned = sa.missedCount;
            }
            sa.attendedCount = Math.max(0, sa.totalPlanned - sa.missedCount);
            sa.attendancePercent = sa.totalPlanned > 0
                    ? Math.round((float) sa.attendedCount * 100f / sa.totalPlanned)
                    : 100;

            totalPlannedAll += sa.totalPlanned;
            totalMissedAll += sa.missedCount;
        }

        cachedOverall.totalPlanned = totalPlannedAll;
        cachedOverall.missedCount = totalMissedAll;
        cachedOverall.attendedCount = Math.max(0, totalPlannedAll - totalMissedAll);
        cachedOverall.attendancePercent = totalPlannedAll > 0
                ? Math.round((float) cachedOverall.attendedCount * 100f / totalPlannedAll)
                : 100;
    }

    public static String formatSkips(int count) {
        int rem100 = count % 100;
        int rem10 = count % 10;
        if (rem100 >= 11 && rem100 <= 19) return count + " Н-ок";
        if (rem10 == 1) return count + " Н-ка";
        if (rem10 >= 2 && rem10 <= 4) return count + " Н-ки";
        return count + " Н-ок";
    }
}
