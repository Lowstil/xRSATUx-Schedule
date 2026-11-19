package com.university.schedule.notification;

import com.university.schedule.model.ScheduleDiff;
import com.university.schedule.model.TransferItem;
import com.university.schedule.util.AppError;
import com.university.schedule.util.Constants;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ScheduleChangeFormatterTest {

    @Test
    public void format_returnsNullWhenNoChanges() {
        ScheduleDiff empty = new ScheduleDiff("group", "ИПБ-24-1", "2026-09-01T08:00:00", Collections.emptyList());
        assertNull(ScheduleChangeFormatter.format("ИПБ-24-1", empty, Collections.emptyList()));
        assertNull(ScheduleChangeFormatter.format("ИПБ-24-1", null, null));
    }

    @Test
    public void format_formatsAllScheduleDiffEntryTypes() {
        List<ScheduleDiff.DiffEntry> entries = Arrays.asList(
                new ScheduleDiff.DiffEntry(
                        ScheduleDiff.ChangeType.ADDED, Constants.WEEK_TYPE_ODD, 1, 2,
                        "Физика", "", "Л · ауд. 1-204 · Иванов И.И."),
                new ScheduleDiff.DiffEntry(
                        ScheduleDiff.ChangeType.REMOVED, Constants.WEEK_TYPE_EVEN, 2, 3,
                        "История", "П · ауд. 2-105", ""),
                new ScheduleDiff.DiffEntry(
                        ScheduleDiff.ChangeType.ROOM_CHANGED, Constants.WEEK_TYPE_ODD, 3, 1,
                        "Математика", "1-101", "2-305"),
                new ScheduleDiff.DiffEntry(
                        ScheduleDiff.ChangeType.TEACHER_CHANGED, Constants.WEEK_TYPE_EVEN, 4, 4,
                        "Информатика", "Петров П.П.", "Сидоров С.С.")
        );
        ScheduleDiff diff = new ScheduleDiff("group", "ИПБ-24-1", "2026-09-01T08:00:00", entries);

        ScheduleChangeFormatter.FormattedNotification notif =
                ScheduleChangeFormatter.format("ИПБ-24-1", diff, Collections.emptyList());

        assertNotNull(notif);
        assertEquals("Расписание изменилось (ИПБ-24-1)", notif.title);
        assertEquals(4, notif.changeCount);
        assertTrue(notif.summary.startsWith("Найдено 4 изменения:"));
        assertTrue(notif.bigText.contains("+ ПН (нечёт.), 2 пара: Физика (Л · ауд. 1-204 · Иванов И.И.)"));
        assertTrue(notif.bigText.contains("− ВТ (чёт.), 3 пара: История (отменена)"));
        assertTrue(notif.bigText.contains("⇄ СР (нечёт.), 1 пара: Математика — ауд. 1-101 → 2-305"));
        assertTrue(notif.bigText.contains("⇄ ЧТ (чёт.), 4 пара: Информатика — Петров П.П. → Сидоров С.С."));
    }

    @Test
    public void format_formatsTransfersAndCombinesWithScheduleDiff() {
        ScheduleDiff.DiffEntry diffEntry = new ScheduleDiff.DiffEntry(
                ScheduleDiff.ChangeType.ROOM_CHANGED, Constants.WEEK_TYPE_ODD, 1, 1,
                "Физика", "1-100", "1-200");
        ScheduleDiff diff = new ScheduleDiff("group", "ИПБ-24-1", "2026-09-01T08:00:00",
                Collections.singletonList(diffEntry));

        TransferItem removal = makeTransfer(TransferItem.KIND_REMOVAL, "2026-02-12", 2,
                "ИПБ-24", "Физика", "Иванов И.И.", "", "1-204");
        TransferItem addition = makeTransfer(TransferItem.KIND_ADDITION, "2026-02-14", 3,
                "ИПБ-24-1", "Физика", "Иванов И.И.", "Петров П.П.", "2-301");
        TransferItem roomChange = makeTransfer(TransferItem.KIND_ROOM_CHANGE, "2026-02-15", 1,
                "ИПБ-24-1", "Алгебра", "Сидоров С.С.", "", "3-105");

        ScheduleChangeFormatter.FormattedNotification notif =
                ScheduleChangeFormatter.format("ИПБ-24-1", diff, Arrays.asList(removal, addition, roomChange));

        assertNotNull(notif);
        assertEquals(4, notif.changeCount);
        assertTrue(notif.bigText.contains("− 12.02, 2 пара: Физика (перенос/отмена)"));
        assertTrue(notif.bigText.contains("+ 14.02, 3 пара: Физика · ауд. 2-301 · Петров П.П. (перенос)"));
        assertTrue(notif.bigText.contains("⇄ 15.02, 1 пара: Алгебра · ауд. 3-105"));
    }

    @Test
    public void format_truncatesBeyondMaxLines() {
        List<ScheduleDiff.DiffEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            entries.add(new ScheduleDiff.DiffEntry(
                    ScheduleDiff.ChangeType.ADDED, Constants.WEEK_TYPE_ODD, 1, (i % 6) + 1,
                    "Предмет " + i, "", "ауд. " + i));
        }
        ScheduleDiff diff = new ScheduleDiff("group", "ИПБ-24-1", "2026-09-01T08:00:00", entries);

        ScheduleChangeFormatter.FormattedNotification notif =
                ScheduleChangeFormatter.format("ИПБ-24-1", diff, Collections.emptyList());

        assertNotNull(notif);
        assertEquals(11, notif.changeCount);
        assertTrue(notif.bigText.contains("Предмет 8"));
        assertFalse(notif.bigText.contains("Предмет 9"));
        assertTrue(notif.bigText.endsWith("… и ещё 3 изменения"));
    }

    @Test
    public void filterTransfersForSelection_matchesGroupSubgroupAndTeacher() {
        TransferItem parentGroup = makeTransfer(TransferItem.KIND_REMOVAL, "2026-02-12", 2,
                "ИПБ-24", "Физика", "Гагарина Алиса Игоревна", "", "1-204");
        TransferItem otherGroup = makeTransfer(TransferItem.KIND_ADDITION, "2026-02-12", 3,
                "ЭПБ-24-1", "История", "Иванов Иван Иванович", "", "2-101");

        List<TransferItem> forSubgroup = ScheduleChangeFormatter.filterTransfersForSelection(
                Arrays.asList(parentGroup, otherGroup), "group", "ИПБ-24-2");
        assertEquals(1, forSubgroup.size());
        assertEquals("Физика", forSubgroup.get(0).getSubjectName());

        List<TransferItem> forTeacher = ScheduleChangeFormatter.filterTransfersForSelection(
                Arrays.asList(parentGroup, otherGroup), "teacher", "Гагарина А.И.");
        assertEquals(1, forTeacher.size());
        assertEquals("Физика", forTeacher.get(0).getSubjectName());
    }

    @Test
    public void findNewTransfers_ignoresUnchangedAndDetectsNewOrModified() {
        TransferItem old1 = makeTransfer(TransferItem.KIND_REMOVAL, "2026-02-12", 2,
                "ИПБ-24-1", "Физика", "Иванов И.И.", "", "1-204");
        old1.setId(1L);

        TransferItem sameNewId = makeTransfer(TransferItem.KIND_REMOVAL, "2026-02-12", 2,
                "ИПБ-24-1", "Физика", "Иванов И.И.", "", "1-204");
        sameNewId.setId(99L);

        TransferItem brandNew = makeTransfer(TransferItem.KIND_ADDITION, "2026-02-14", 3,
                "ИПБ-24-1", "Физика", "Иванов И.И.", "", "2-301");
        brandNew.setId(100L);

        List<TransferItem> diff = ScheduleChangeFormatter.findNewTransfers(
                Collections.singletonList(old1),
                Arrays.asList(sameNewId, brandNew));

        assertEquals(1, diff.size());
        assertEquals("2026-02-14", diff.get(0).getDate());
    }

    @Test
    public void shouldRetry_onlyTransientNetworkErrorsWithinLimit() {
        AppError noNet = AppError.from(new UnknownHostException("rsatu.ru"));
        AppError timeout = AppError.from(new SocketTimeoutException("read timed out"));
        AppError serverDown = AppError.from(new ConnectException("Connection refused"));
        AppError http503 = AppError.from(new IOException("HTTP 503"));
        AppError badFormat = AppError.from(new IOException("not a valid ooxml file"));
        AppError tooLarge = AppError.from(new IOException("Файл слишком большой: 99999999 байт"));

        assertTrue(ScheduleChangeFormatter.shouldRetry(noNet, 0));
        assertTrue(ScheduleChangeFormatter.shouldRetry(timeout, 1));
        assertTrue(ScheduleChangeFormatter.shouldRetry(serverDown, 2));
        assertTrue(ScheduleChangeFormatter.shouldRetry(http503, 0));

        assertFalse(ScheduleChangeFormatter.shouldRetry(noNet, 3));
        assertFalse(ScheduleChangeFormatter.shouldRetry(badFormat, 0));
        assertFalse(ScheduleChangeFormatter.shouldRetry(tooLarge, 0));
        assertFalse(ScheduleChangeFormatter.shouldRetry(null, 0));
    }

    @Test
    public void schedulerConstants_areValidForWorkManager() {
        assertTrue(AutoSyncScheduler.REPEAT_INTERVAL_HOURS >= 1L);
        assertTrue(AutoSyncScheduler.FLEX_INTERVAL_HOURS >= 1L);
        assertTrue(AutoSyncScheduler.FLEX_INTERVAL_HOURS < AutoSyncScheduler.REPEAT_INTERVAL_HOURS);
        assertTrue(AutoSyncScheduler.INITIAL_BACKOFF_MINUTES >= 1L);
        assertFalse(AutoSyncScheduler.WORK_NAME.isEmpty());
    }

    private static TransferItem makeTransfer(String kind, String date, int lessonNum,
                                             String group, String subject,
                                             String teacher, String subTeacher, String room) {
        TransferItem item = new TransferItem();
        item.setKind(kind);
        item.setDate(date);
        item.setLessonNumber(lessonNum);
        item.setGroupName(group);
        item.setSubjectName(subject);
        item.setTeacherName(teacher);
        item.setSubstituteTeacher(subTeacher);
        item.setRoom(room);
        return item;
    }
}
