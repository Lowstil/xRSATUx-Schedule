package com.university.schedule.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.HolidayChecker;
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
    public static final String PREF_SKIP_LIMIT_PCT = "attendance_skip_limit_pct";
    public static final int DEFAULT_SKIP_LIMIT_PCT = 30;
    private static final String PREF_ATTENDANCE_MANUAL_PREFIX = "att_manual_";

    public enum BudgetHealth {
        SAFE,
        WARNING,
        EXCEEDED
    }

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
    private String cachedSemesterId;
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
        cachedSemesterId = null;
        cachedStats = null;
        cachedOverall = null;
    }

    public synchronized Map<String, SubjectAttendance> getStatsMap(String groupName) {
        LocalDate today = DateUtils.todayMoscow();
        String semId = ScheduleRepository.getInstance(context).getActiveSemesterId();
        if (cachedStats != null && groupName != null && groupName.equalsIgnoreCase(cachedGroup)
                && semId != null && semId.equals(cachedSemesterId)
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

    public int getSkipLimitPercent() {
        int pct = prefs.getInt(PREF_SKIP_LIMIT_PCT, DEFAULT_SKIP_LIMIT_PCT);
        if (pct != 20 && pct != 25 && pct != 30) {
            return DEFAULT_SKIP_LIMIT_PCT;
        }
        return pct;
    }

    public void setSkipLimitPercent(int pct) {
        int normalized = (pct == 20 || pct == 25 || pct == 30) ? pct : DEFAULT_SKIP_LIMIT_PCT;
        prefs.edit().putInt(PREF_SKIP_LIMIT_PCT, normalized).apply();
    }

    public void adjustManualSkips(String groupName, String subjectName, int delta) {
        if (groupName == null || subjectName == null) return;
        if (delta < 0) {
            Map<String, SubjectAttendance> map = getStatsMap(groupName);
            SubjectAttendance sa = map.get(subjectName.trim());
            if (sa != null && sa.missedCount <= 0) {
                return;
            }
        }
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

    public synchronized Map<String, Integer> getAllManualAdjustments() {
        Map<String, Integer> result = new HashMap<>();
        Map<String, ?> all = prefs.getAll();
        if (all != null) {
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                String key = entry.getKey();
                if (key != null && key.startsWith(PREF_ATTENDANCE_MANUAL_PREFIX)
                        && entry.getValue() instanceof Integer) {
                    int val = (Integer) entry.getValue();
                    if (val != 0) {
                        result.put(key.substring(PREF_ATTENDANCE_MANUAL_PREFIX.length()), val);
                    }
                }
            }
        }
        return result;
    }

    public synchronized void replaceAllManualAdjustments(Map<String, Integer> adjustments) {
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> all = prefs.getAll();
        if (all != null) {
            for (String key : all.keySet()) {
                if (key != null && key.startsWith(PREF_ATTENDANCE_MANUAL_PREFIX)) {
                    editor.remove(key);
                }
            }
        }
        if (adjustments != null) {
            for (Map.Entry<String, Integer> entry : adjustments.entrySet()) {
                String suffix = entry.getKey();
                Integer val = entry.getValue();
                if (suffix != null && !suffix.isEmpty() && val != null && val != 0) {
                    editor.putInt(PREF_ATTENDANCE_MANUAL_PREFIX + suffix, val);
                }
            }
        }
        editor.apply();
        invalidateCache();
    }

    private void recalculate(String groupName, LocalDate today) {
        cachedGroup = groupName;
        cachedDate = today;
        cachedStats = new HashMap<>();
        cachedOverall = new OverallAttendance();

        if (groupName == null || groupName.isEmpty()) return;

        ScheduleRepository repo = ScheduleRepository.getInstance(context);
        String semId = repo.getActiveSemesterId();
        cachedSemesterId = semId;
        LocalDate semStart = repo.getSemesterStart();
        if (semStart == null) return;

        WeekCalculator calc = repo.getWeekCalculator(semId);
        HolidayChecker holidayChecker = repo.getHolidayChecker();
        Set<String> excluded = excludedMgr.getExcludedSubjects(groupName);

        // Сканируем от начала семестра до today + 14 дней (чтобы учесть заранее отмеченные пары)
        LocalDate scanEnd = today.plusDays(14);
        if (semStart.isAfter(scanEnd)) return;

        // Пакетная загрузка расписания и переносов вместо ~240 отдельных запросов к БД
        List<ScheduleItem> oddLessons = repo.getScheduleForGroupAndWeekType(groupName, Constants.WEEK_TYPE_ODD, semId);
        List<ScheduleItem> evenLessons = repo.getScheduleForGroupAndWeekType(groupName, Constants.WEEK_TYPE_EVEN, semId);
        List<TransferItem> allTransfers = repo.getTransfersForDateRange(semStart.toString(), scanEnd.toString());
        Map<String, List<TransferItem>> transfersByDate = new HashMap<>();
        if (allTransfers != null) {
            for (TransferItem t : allTransfers) {
                if (t.getDate() != null) {
                    transfersByDate.computeIfAbsent(t.getDate(), k -> new ArrayList<>()).add(t);
                }
            }
        }

        LocalDate cursor = semStart;
        while (!cursor.isAfter(scanEnd)) {
            int dow = DateUtils.toScheduleDayOfWeek(cursor);
            if (dow >= 1 && dow <= 6 && (holidayChecker == null || !holidayChecker.isDayOff(cursor))) {
                int weekNum = calc.getWeekNumber(cursor);
                if (weekNum >= 1 && weekNum <= 18) {
                    List<ScheduleItem> rawLessons = (weekNum % 2 == 0) ? evenLessons : oddLessons;
                    String dateStr = cursor.toString();
                    List<TransferItem> transfers = transfersByDate.get(dateStr);

                    Map<Integer, Integer> removalsByLesson = null;
                    if (transfers != null) {
                        for (TransferItem t : transfers) {
                            if (TransferItem.KIND_REMOVAL.equals(t.getKind()) && t.matchesGroup(groupName)) {
                                if (removalsByLesson == null) removalsByLesson = new HashMap<>();
                                int ln = t.getLessonNumber();
                                removalsByLesson.put(ln, removalsByLesson.getOrDefault(ln, 0) + 1);
                            }
                        }
                    }

                    boolean isFuture = cursor.isAfter(today);

                    if (rawLessons != null) {
                        for (ScheduleItem item : rawLessons) {
                            if (item.getDayOfWeek() != dow) continue;
                            if (item.isCancelled()) continue;
                            if (removalsByLesson != null) {
                                int remCount = removalsByLesson.getOrDefault(item.getLessonNumber(), 0);
                                if (remCount > 0) {
                                    removalsByLesson.put(item.getLessonNumber(), remCount - 1);
                                    continue;
                                }
                            }
                            String sub = item.getSubjectName() != null ? item.getSubjectName().trim() : "";
                            if (sub.isEmpty() || (excluded != null && excluded.contains(sub))) continue;

                            if (!item.isActiveOnWeek(weekNum)) continue;

                            boolean isSkipped = skippedMgr.isSkipped(dateStr, item.getLessonNumber(), sub);
                            boolean isAttended = skippedMgr.isAttended(dateStr, item.getLessonNumber(), sub);

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

                    // Учитываем только добавленные переносы (KIND_ADDITION), но не KIND_ROOM_CHANGE или KIND_REMOVAL
                    if (transfers != null) {
                        for (TransferItem t : transfers) {
                            if (!TransferItem.KIND_ADDITION.equals(t.getKind())) continue;
                            if (!t.matchesGroup(groupName)) continue;
                            String sub = t.getSubjectName() != null ? t.getSubjectName().trim() : "";
                            if (sub.isEmpty() || (excluded != null && excluded.contains(sub))) continue;

                            boolean isSkipped = skippedMgr.isSkipped(dateStr, t.getLessonNumber(), sub);
                            boolean isAttended = skippedMgr.isAttended(dateStr, t.getLessonNumber(), sub);

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

    public static String formatLessonsCount(int count) {
        int abs = Math.abs(count);
        int rem100 = abs % 100;
        int rem10 = abs % 10;
        if (rem100 >= 11 && rem100 <= 19) return count + " пар";
        if (rem10 == 1) return count + " пара";
        if (rem10 >= 2 && rem10 <= 4) return count + " пары";
        return count + " пар";
    }

    public static int computeSkipPercent(int missedCount, int totalPlanned) {
        if (totalPlanned <= 0 || missedCount <= 0) return 0;
        return Math.round((float) missedCount * 100f / totalPlanned);
    }

    public static int computeMaxAllowedSkips(int totalPlanned, int skipLimitPct) {
        if (totalPlanned <= 0 || skipLimitPct <= 0) return 0;
        return (int) Math.floor(totalPlanned * (skipLimitPct / 100.0));
    }

    public static int computeRemainingSkipBudget(int missedCount, int totalPlanned, int skipLimitPct) {
        int maxAllowed = computeMaxAllowedSkips(totalPlanned, skipLimitPct);
        return maxAllowed - Math.max(0, missedCount);
    }

    public static BudgetHealth evaluateBudgetHealth(int missedCount, int totalPlanned, int skipLimitPct) {
        if (totalPlanned <= 0) return BudgetHealth.SAFE;
        int remaining = computeRemainingSkipBudget(missedCount, totalPlanned, skipLimitPct);
        if (remaining < 0) {
            return BudgetHealth.EXCEEDED;
        }
        if (remaining == 1 || (remaining == 0 && missedCount > 0)) {
            return BudgetHealth.WARNING;
        }
        return BudgetHealth.SAFE;
    }

    public static String formatBudgetStatus(int missedCount, int totalPlanned, int skipLimitPct) {
        if (totalPlanned <= 0) {
            return "Нет проведённых пар";
        }
        int remaining = computeRemainingSkipBudget(missedCount, totalPlanned, skipLimitPct);
        int skipPct = computeSkipPercent(missedCount, totalPlanned);
        if (remaining < 0) {
            return "Лимит превышен! (" + skipPct + "%)";
        }
        if (remaining == 1) {
            return "Последний запас: 1 пара";
        }
        if (remaining == 0) {
            if (missedCount <= 0) {
                return "Пропусков нет · запас 0 пар";
            }
            return "Запас исчерпан (" + skipPct + "%)";
        }
        return "Можно пропустить ещё " + formatLessonsCount(remaining);
    }
}
