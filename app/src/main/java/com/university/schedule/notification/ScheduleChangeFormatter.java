package com.university.schedule.notification;

import com.university.schedule.model.ScheduleDiff;
import com.university.schedule.model.TransferItem;
import com.university.schedule.util.AppError;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-Java логика для фоновой автосинхронизации:
 *  - фильтрация и вычисление новых переносов, затрагивающих выбранную группу/преподавателя;
 *  - форматирование {@link ScheduleDiff} и новых {@link TransferItem} в текст push-уведомления;
 *  - принятие решения о повторе (retry) при транзиентных сетевых ошибках.
 */
public final class ScheduleChangeFormatter {

    public static final int MAX_LINES_IN_NOTIFICATION = 8;
    public static final int MAX_RETRY_ATTEMPTS = 3;

    private ScheduleChangeFormatter() {
    }

    public static final class FormattedNotification {
        public final String title;
        public final String summary;
        public final String bigText;
        public final int changeCount;

        public FormattedNotification(String title, String summary, String bigText, int changeCount) {
            this.title = title;
            this.summary = summary;
            this.bigText = bigText;
            this.changeCount = changeCount;
        }
    }

    /**
     * Отбирает переносы, относящиеся к выбранной группе или преподавателю.
     */
    public static List<TransferItem> filterTransfersForSelection(
            List<TransferItem> transfers, String selectionType, String selectionName) {
        if (transfers == null || transfers.isEmpty()
                || selectionName == null || selectionName.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String target = selectionName.trim();
        boolean isGroup = "group".equals(selectionType);
        List<TransferItem> matched = new ArrayList<>();
        for (TransferItem item : transfers) {
            if (item == null) continue;
            boolean affects = isGroup ? item.matchesGroup(target) : item.matchesTeacher(target);
            if (affects) {
                matched.add(item);
            }
        }
        return matched;
    }

    /**
     * Возвращает переносы из {@code newTransfers}, которых не было в {@code oldTransfers}
     * (сравнение по каноническому ключу без учёта БД-id).
     */
    public static List<TransferItem> findNewTransfers(
            List<TransferItem> oldTransfers, List<TransferItem> newTransfers) {
        if (newTransfers == null || newTransfers.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> oldKeys = new HashSet<>();
        if (oldTransfers != null) {
            for (TransferItem item : oldTransfers) {
                if (item != null) {
                    oldKeys.add(transferSignature(item));
                }
            }
        }
        Set<String> seenNew = new HashSet<>();
        List<TransferItem> added = new ArrayList<>();
        for (TransferItem item : newTransfers) {
            if (item == null) continue;
            String key = transferSignature(item);
            if (!oldKeys.contains(key) && seenNew.add(key)) {
                added.add(item);
            }
        }
        return added;
    }

    static String transferSignature(TransferItem item) {
        return norm(item.getKind()).toLowerCase()
                + "|" + norm(item.getDate())
                + "|" + item.getLessonNumber()
                + "|" + norm(item.getSubjectName()).toLowerCase()
                + "|" + norm(item.getRoom()).toLowerCase()
                + "|" + norm(item.getEffectiveTeacher()).toLowerCase()
                + "|" + norm(item.getNote()).toLowerCase();
    }

    /**
     * Формирует заголовок, краткое описание и подробный BigText-список изменений.
     * Возвращает {@code null}, если реальных изменений нет.
     */
    public static FormattedNotification format(
            String selectionName, ScheduleDiff diff, List<TransferItem> newTransfers) {
        List<String> lines = new ArrayList<>();

        if (diff != null && diff.hasChanges()) {
            for (ScheduleDiff.DiffEntry entry : diff.getEntries()) {
                lines.add(formatDiffEntry(entry));
            }
        }
        if (newTransfers != null) {
            for (TransferItem transfer : newTransfers) {
                if (transfer != null) {
                    lines.add(formatTransferEntry(transfer));
                }
            }
        }

        if (lines.isEmpty()) {
            return null;
        }

        int total = lines.size();
        String targetSuffix = (selectionName != null && !selectionName.trim().isEmpty())
                ? " (" + selectionName.trim() + ")"
                : "";
        String title = "Расписание изменилось" + targetSuffix;

        String summary;
        if (total == 1) {
            summary = lines.get(0);
        } else {
            summary = "Найдено " + total + " " + pluralChanges(total) + ": " + lines.get(0);
        }

        StringBuilder bigText = new StringBuilder();
        int limit = Math.min(total, MAX_LINES_IN_NOTIFICATION);
        for (int i = 0; i < limit; i++) {
            if (i > 0) bigText.append('\n');
            bigText.append(lines.get(i));
        }
        if (total > MAX_LINES_IN_NOTIFICATION) {
            int remaining = total - MAX_LINES_IN_NOTIFICATION;
            bigText.append("\n… и ещё ").append(remaining).append(' ').append(pluralChanges(remaining));
        }

        return new FormattedNotification(title, summary, bigText.toString(), total);
    }

    static String formatDiffEntry(ScheduleDiff.DiffEntry entry) {
        String dayName = (entry.dayOfWeek >= 1 && entry.dayOfWeek <= 6)
                ? Constants.DAY_NAMES_SHORT[entry.dayOfWeek - 1]
                : "";
        String weekLabel = Constants.WEEK_TYPE_EVEN.equals(entry.weekType) ? "чёт." : "нечёт.";
        String slotPrefix = dayName + " (" + weekLabel + "), " + entry.lessonNumber + " пара: " + norm(entry.subjectName);

        switch (entry.type) {
            case ADDED:
                return "+ " + slotPrefix + (norm(entry.newValue).isEmpty() ? "" : " (" + norm(entry.newValue) + ")");
            case REMOVED:
                return "− " + slotPrefix + " (отменена)";
            case ROOM_CHANGED:
                return "⇄ " + slotPrefix + " — ауд. " + safeVal(entry.oldValue) + " → " + safeVal(entry.newValue);
            case TEACHER_CHANGED:
            default:
                return "⇄ " + slotPrefix + " — " + safeVal(entry.oldValue) + " → " + safeVal(entry.newValue);
        }
    }

    static String formatTransferEntry(TransferItem item) {
        String dateLabel = formatShortDate(item.getDate());
        String subject = norm(item.getSubjectName());
        String slot = (dateLabel.isEmpty() ? "" : dateLabel + ", ")
                + item.getLessonNumber() + " пара"
                + (subject.isEmpty() ? "" : ": " + subject);

        String kind = norm(item.getKind());
        String room = norm(item.getRoom());
        String subTeacher = norm(item.getSubstituteTeacher());

        if (TransferItem.KIND_REMOVAL.equals(kind)) {
            return "− " + slot + " (перенос/отмена)";
        }
        if (TransferItem.KIND_ADDITION.equals(kind)) {
            StringBuilder sb = new StringBuilder("+ ").append(slot);
            if (!room.isEmpty()) sb.append(" · ауд. ").append(room);
            if (!subTeacher.isEmpty()) sb.append(" · ").append(subTeacher);
            sb.append(" (перенос)");
            return sb.toString();
        }
        // KIND_ROOM_CHANGE или иной вид замены
        StringBuilder sb = new StringBuilder("⇄ ").append(slot);
        if (!room.isEmpty()) sb.append(" · ауд. ").append(room);
        if (!subTeacher.isEmpty()) sb.append(" · ").append(subTeacher);
        return sb.toString();
    }

    /**
     * Решает, стоит ли повторять фоновую попытку синхронизации в WorkManager.
     * Повторяем только при транзиентных сетевых ошибках и до {@link #MAX_RETRY_ATTEMPTS} попыток.
     */
    public static boolean shouldRetry(AppError error, int runAttemptCount) {
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            return false;
        }
        if (error == null || error.category == null) {
            return false;
        }
        return error.category == AppError.Category.NO_INTERNET
                || error.category == AppError.Category.SERVER_UNAVAILABLE
                || error.category == AppError.Category.TIMEOUT;
    }

    private static String formatShortDate(String isoDate) {
        LocalDate parsed = DateUtils.parseIsoDate(isoDate);
        if (parsed != null) {
            return DateUtils.formatDisplayDateShort(parsed);
        }
        return norm(isoDate);
    }

    private static String safeVal(String s) {
        String trimmed = norm(s);
        return trimmed.isEmpty() ? "—" : trimmed;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }

    static String pluralChanges(int n) {
        int rem100 = n % 100;
        int rem10 = n % 10;
        if (rem100 >= 11 && rem100 <= 14) return "изменений";
        if (rem10 == 1) return "изменение";
        if (rem10 >= 2 && rem10 <= 4) return "изменения";
        return "изменений";
    }
}
