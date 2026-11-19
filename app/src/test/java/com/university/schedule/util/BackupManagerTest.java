package com.university.schedule.util;

import com.university.schedule.model.CustomEvent;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.LessonNote;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BackupManagerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void suggestedBackupFileName_formatsIsoDate() {
        String name = BackupManager.suggestedBackupFileName(LocalDate.of(2026, 11, 19));
        assertEquals("xRSATUx_backup_2026-11-19.zip", name);
    }

    @Test
    public void serializeAndDeserializeJson_fullRoundTrip() throws Exception {
        BackupManager.BackupData original = buildSampleBackupData();

        String json = BackupManager.serializeToJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"schemaVersion\""));

        BackupManager.BackupData restored = BackupManager.deserializeFromJson(json);

        assertEquals(BackupManager.SCHEMA_VERSION, restored.schemaVersion);
        assertEquals("3.3.1", restored.appVersion);
        assertEquals("2026-11-19T12:30:00", restored.exportedAt);

        // Notes
        assertEquals(2, restored.notes.size());
        LessonNote n1 = restored.notes.get(0);
        assertEquals("2026-10-29", n1.getDate());
        assertEquals(1, n1.getLessonNumber());
        assertEquals("Математика", n1.getSubjectName());
        assertEquals("Подготовить лабу №3", n1.getNoteText());
        assertEquals("photo_1001.jpg", n1.getImagePath());
        assertFalse(n1.isCompleted());
        assertEquals("2026-10-28T18:00:00", n1.getCreatedAt());

        LessonNote n2 = restored.notes.get(1);
        assertEquals("2026-10-30", n2.getDate());
        assertEquals(3, n2.getLessonNumber());
        assertNull(n2.getImagePath());
        assertTrue(n2.isCompleted());

        // Custom events
        assertEquals(1, restored.customEvents.size());
        CustomEvent ev = restored.customEvents.get(0);
        assertEquals("ИВТ-21", ev.getGroupName());
        assertTrue(ev.isGroup());
        assertEquals("2026-11-20", ev.getDateIso());
        assertEquals(5, ev.getDayOfWeek());
        assertEquals("odd", ev.getWeekType());
        assertEquals(4, ev.getLessonNumber());
        assertEquals("Консультация", ev.getSubjectName());
        assertEquals("Иванов И.И.", ev.getTeacherName());
        assertEquals("1-210", ev.getRoom());
        assertEquals("Принести зачётку", ev.getNote());

        // Attendance
        assertEquals(25, restored.skipLimitPercent);
        assertEquals(new HashSet<>(Arrays.asList("2026-10-10#1#Физика", "2026-10-12#2#Математика")),
                restored.skippedLessons);
        assertEquals(Collections.singleton("2026-10-11#3#История"), restored.attendedLessons);
        assertEquals(Integer.valueOf(2), restored.manualAttendanceAdjustments.get("ИВТ-21_Физика"));

        // Excluded subjects
        assertEquals(new HashSet<>(Arrays.asList("Физкультура", "БЖД")),
                restored.excludedSubjects.get("ИВТ-21"));

        // Favorites
        assertEquals(2, restored.favorites.size());
        assertEquals("ИВТ-21", restored.favorites.get(0).getName());
        assertEquals(GroupOrTeacher.TYPE_GROUP, restored.favorites.get(0).getType());
        assertEquals("Петров П.П.", restored.favorites.get(1).getName());
        assertEquals(GroupOrTeacher.TYPE_TEACHER, restored.favorites.get(1).getType());

        // Prefs
        assertNotNull(restored.prefs);
        assertEquals("group", restored.prefs.selectionType);
        assertEquals("ИВТ-21", restored.prefs.selectionName);
        assertEquals("fall", restored.prefs.activeSemester);
        assertEquals("2026-08-31", restored.prefs.semesterStartFall);
        assertEquals("2027-02-08", restored.prefs.semesterStartSpring);
        assertEquals(Integer.valueOf(2), restored.prefs.themeMode);
        assertEquals(Boolean.TRUE, restored.prefs.reducedTransparency);
        assertEquals(Boolean.FALSE, restored.prefs.remindersEnabled);
        assertEquals(Integer.valueOf(30), restored.prefs.reminderMinutesBefore);
        assertEquals(Boolean.FALSE, restored.prefs.morningDigestEnabled);
        assertEquals(Boolean.TRUE, restored.prefs.liveLessonNotifEnabled);
    }

    @Test
    public void writeAndReadZipBackup_packsAndRestoresPhotosExactly() throws Exception {
        BackupManager.BackupData data = buildSampleBackupData();

        byte[] photo1Bytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03, 0x04};
        byte[] photo2Bytes = "fake-png-photo-content-1234567890".getBytes(StandardCharsets.UTF_8);

        File srcPhoto1 = tempFolder.newFile("photo_1001.jpg");
        try (FileOutputStream fos = new FileOutputStream(srcPhoto1)) {
            fos.write(photo1Bytes);
        }
        File srcPhoto2 = tempFolder.newFile("extra_board.png");
        try (FileOutputStream fos = new FileOutputStream(srcPhoto2)) {
            fos.write(photo2Bytes);
        }

        Map<String, File> photos = new LinkedHashMap<>();
        photos.put("photo_1001.jpg", srcPhoto1);
        photos.put("extra_board.png", srcPhoto2);

        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        BackupManager.BackupSummary exportSummary = BackupManager.writeZipBackup(data, photos, zipOut);

        assertEquals(2, exportSummary.notesCount);
        assertEquals(2, exportSummary.photosCount);
        assertEquals(1, exportSummary.customEventsCount);
        assertEquals(2, exportSummary.favoritesCount);
        assertEquals(4, exportSummary.attendanceMarksCount); // 2 skipped + 1 attended + 1 manual
        assertEquals(2, exportSummary.excludedSubjectsCount);
        assertTrue(exportSummary.formatExportSummary().contains("фото: 2"));
        assertTrue(exportSummary.formatImportSummary().contains("Заметок: 2"));

        File stagingDir = tempFolder.newFolder("staging");
        BackupManager.ParsedBackup parsed = BackupManager.readBackupStream(
                new ByteArrayInputStream(zipOut.toByteArray()),
                stagingDir
        );

        assertEquals(2, parsed.data.notes.size());
        assertEquals(2, parsed.extractedPhotos.size());
        assertTrue(parsed.extractedPhotos.containsKey("photo_1001.jpg"));
        assertTrue(parsed.extractedPhotos.containsKey("extra_board.png"));

        byte[] restoredPhoto1 = Files.readAllBytes(parsed.extractedPhotos.get("photo_1001.jpg").toPath());
        byte[] restoredPhoto2 = Files.readAllBytes(parsed.extractedPhotos.get("extra_board.png").toPath());
        assertArrayEquals(photo1Bytes, restoredPhoto1);
        assertArrayEquals(photo2Bytes, restoredPhoto2);
    }

    @Test
    public void readBackupStream_acceptsRawJsonWithUtf8Bom() throws Exception {
        BackupManager.BackupData data = buildSampleBackupData();
        String json = "\uFEFF" + BackupManager.serializeToJson(data);

        File stagingDir = tempFolder.newFolder("staging_json");
        BackupManager.ParsedBackup parsed = BackupManager.readBackupStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                stagingDir
        );

        assertEquals(2, parsed.data.notes.size());
        assertEquals(1, parsed.data.customEvents.size());
        assertTrue(parsed.extractedPhotos.isEmpty());
    }

    @Test
    public void readBackupStream_rejectsZipSlipTraversalEntries() throws Exception {
        String[] maliciousNames = {
                "../evil.txt",
                "photos/../../evil.jpg",
                "photos/../backup.json",
                "photos\\..\\evil.jpg",
                "/etc/passwd",
                "photos/subdir/nested.jpg"
        };

        String validJson = BackupManager.serializeToJson(buildSampleBackupData());
        for (String badEntry : maliciousNames) {
            byte[] badZip = createRawZip(new String[]{BackupManager.BACKUP_JSON_ENTRY, badEntry},
                    new byte[][]{validJson.getBytes(StandardCharsets.UTF_8), "hack".getBytes(StandardCharsets.UTF_8)});
            File stagingDir = tempFolder.newFolder();
            try {
                BackupManager.readBackupStream(new ByteArrayInputStream(badZip), stagingDir);
                fail("Expected BackupException for malicious ZIP entry: " + badEntry);
            } catch (BackupManager.BackupException expected) {
                // Expected security rejection
            }
        }
    }

    @Test(expected = BackupManager.BackupException.class)
    public void readBackupStream_rejectsEmptyStream() throws Exception {
        BackupManager.readBackupStream(new ByteArrayInputStream(new byte[0]), tempFolder.newFolder());
    }

    @Test(expected = BackupManager.BackupException.class)
    public void readBackupStream_rejectsRandomBinaryGarbage() throws Exception {
        byte[] garbage = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        BackupManager.readBackupStream(new ByteArrayInputStream(garbage), tempFolder.newFolder());
    }

    @Test(expected = BackupManager.BackupException.class)
    public void readBackupStream_rejectsZipWithoutBackupJson() throws Exception {
        byte[] zipWithoutJson = createRawZip(
                new String[]{"photos/photo_1.jpg"},
                new byte[][]{"abc".getBytes(StandardCharsets.UTF_8)}
        );
        BackupManager.readBackupStream(new ByteArrayInputStream(zipWithoutJson), tempFolder.newFolder());
    }

    @Test(expected = BackupManager.BackupException.class)
    public void deserializeFromJson_rejectsMissingSchemaVersion() throws Exception {
        BackupManager.deserializeFromJson("{\"notes\":[]}");
    }

    @Test(expected = BackupManager.BackupException.class)
    public void deserializeFromJson_rejectsFutureSchemaVersion() throws Exception {
        BackupManager.deserializeFromJson("{\"schemaVersion\":999,\"notes\":[]}");
    }

    @Test(expected = BackupManager.BackupException.class)
    public void deserializeFromJson_rejectsMalformedJson() throws Exception {
        BackupManager.deserializeFromJson("{\"schemaVersion\":1, \"notes\": [");
    }

    @Test
    public void sanitizePhotoFileName_stripsPathsAndRejectsTraversal() {
        assertEquals("photo_1.jpg", BackupManager.sanitizePhotoFileName("/data/user/0/app/files/notes_photos/photo_1.jpg"));
        assertEquals("photo_2.png", BackupManager.sanitizePhotoFileName("C:\\temp\\photo_2.png"));
        assertNull(BackupManager.sanitizePhotoFileName(".."));
        assertNull(BackupManager.sanitizePhotoFileName("."));
        assertNull(BackupManager.sanitizePhotoFileName(""));
        assertNull(BackupManager.sanitizePhotoFileName(null));
    }

    private static BackupManager.BackupData buildSampleBackupData() {
        BackupManager.BackupData data = new BackupManager.BackupData();
        data.schemaVersion = BackupManager.SCHEMA_VERSION;
        data.appVersion = "3.3.1";
        data.exportedAt = "2026-11-19T12:30:00";

        data.notes.add(new LessonNote(
                1, "2026-10-29", 1, "Математика", "Подготовить лабу №3",
                "photo_1001.jpg", false, "2026-10-28T18:00:00"));
        data.notes.add(new LessonNote(
                2, "2026-10-30", 3, "История", "Сдать реферат",
                null, true, "2026-10-29T09:00:00"));

        CustomEvent ev = new CustomEvent();
        ev.setGroupName("ИВТ-21");
        ev.setGroup(true);
        ev.setDateIso("2026-11-20");
        ev.setDayOfWeek(5);
        ev.setWeekType("odd");
        ev.setLessonNumber(4);
        ev.setSubjectName("Консультация");
        ev.setTeacherName("Иванов И.И.");
        ev.setRoom("1-210");
        ev.setNote("Принести зачётку");
        data.customEvents.add(ev);

        data.skipLimitPercent = 25;
        data.skippedLessons = new HashSet<>(Arrays.asList("2026-10-10#1#Физика", "2026-10-12#2#Математика"));
        data.attendedLessons = new HashSet<>(Collections.singletonList("2026-10-11#3#История"));
        data.manualAttendanceAdjustments = new HashMap<>();
        data.manualAttendanceAdjustments.put("ИВТ-21_Физика", 2);

        data.excludedSubjects = new HashMap<>();
        data.excludedSubjects.put("ИВТ-21", new HashSet<>(Arrays.asList("Физкультура", "БЖД")));

        data.favorites.add(new GroupOrTeacher("ИВТ-21", GroupOrTeacher.TYPE_GROUP));
        data.favorites.add(new GroupOrTeacher("Петров П.П.", GroupOrTeacher.TYPE_TEACHER));

        BackupManager.UserPrefsData p = new BackupManager.UserPrefsData();
        p.selectionType = "group";
        p.selectionName = "ИВТ-21";
        p.activeSemester = "fall";
        p.semesterStartFall = "2026-08-31";
        p.semesterStartSpring = "2027-02-08";
        p.themeMode = 2;
        p.reducedTransparency = true;
        p.remindersEnabled = false;
        p.reminderMinutesBefore = 30;
        p.morningDigestEnabled = false;
        p.liveLessonNotifEnabled = true;
        data.prefs = p;

        return data;
    }

    private static byte[] createRawZip(String[] names, byte[][] payloads) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < names.length; i++) {
                ZipEntry entry = new ZipEntry(names[i]);
                zos.putNextEntry(entry);
                zos.write(payloads[i]);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
