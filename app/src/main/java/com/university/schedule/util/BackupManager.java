package com.university.schedule.util;

import android.content.Context;

import com.university.schedule.BuildConfig;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.data.db.CustomEventDao;
import com.university.schedule.data.db.NoteDao;
import com.university.schedule.logic.SemesterManager;
import com.university.schedule.model.CustomEvent;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.LessonNote;
import com.university.schedule.notification.LiveLessonService;
import com.university.schedule.notification.ReminderScheduler;
import com.university.schedule.widget.WidgetUpdateHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Локальный экспорт и восстановление пользовательских данных приложения (.zip / .json):
 * заметки с фотографиями, личные события, посещаемость/пропуски, перезачёты,
 * избранное и ключевые настройки.
 */
public final class BackupManager {

    public static final int SCHEMA_VERSION = 1;
    public static final String BACKUP_JSON_ENTRY = "backup.json";
    public static final String PHOTOS_DIR_PREFIX = "photos/";
    public static final String NOTES_PHOTOS_DIR_NAME = "notes_photos";

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_JSON_BYTES = 16 * 1024 * 1024;
    private static final int MAX_PHOTO_BYTES = 25 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 2000;

    private BackupManager() {
    }

    public static class BackupException extends Exception {
        public BackupException(String message) {
            super(message);
        }

        public BackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class UserPrefsData {
        public String selectionType;
        public String selectionName;
        public String activeSemester;
        public String semesterStartFall;
        public String semesterStartSpring;
        public Integer themeMode;
        public Boolean reducedTransparency;
        public Boolean remindersEnabled;
        public Integer reminderMinutesBefore;
        public Boolean morningDigestEnabled;
        public Boolean liveLessonNotifEnabled;
    }

    public static class BackupData {
        public int schemaVersion = SCHEMA_VERSION;
        public String appVersion = "";
        public String exportedAt = "";
        public List<LessonNote> notes = new ArrayList<>();
        public List<CustomEvent> customEvents = new ArrayList<>();
        public int skipLimitPercent = AttendanceManager.DEFAULT_SKIP_LIMIT_PCT;
        public Set<String> skippedLessons = new HashSet<>();
        public Set<String> attendedLessons = new HashSet<>();
        public Map<String, Integer> manualAttendanceAdjustments = new HashMap<>();
        public Map<String, Set<String>> excludedSubjects = new HashMap<>();
        public List<GroupOrTeacher> favorites = new ArrayList<>();
        public UserPrefsData prefs = new UserPrefsData();
    }

    public static class ParsedBackup {
        public final BackupData data;
        public final Map<String, File> extractedPhotos;

        public ParsedBackup(BackupData data, Map<String, File> extractedPhotos) {
            this.data = data;
            this.extractedPhotos = extractedPhotos != null ? extractedPhotos : Collections.emptyMap();
        }
    }

    public static class BackupSummary {
        public int notesCount;
        public int photosCount;
        public int customEventsCount;
        public int favoritesCount;
        public int attendanceMarksCount;
        public int excludedSubjectsCount;
        public boolean prefsRestored;
        public String exportedAt;
        public String appVersion;

        public String formatExportSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Бэкап сохранён:\n");
            sb.append("• Заметок: ").append(notesCount);
            if (photosCount > 0) {
                sb.append(" (фото: ").append(photosCount).append(")");
            }
            sb.append("\n• Личных событий: ").append(customEventsCount);
            sb.append("\n• Отметок посещаемости: ").append(attendanceMarksCount);
            sb.append("\n• Перезачётов: ").append(excludedSubjectsCount);
            sb.append("\n• Избранного: ").append(favoritesCount);
            return sb.toString();
        }

        public String formatImportSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Восстановлено из резервной копии:\n");
            sb.append("• Заметок: ").append(notesCount);
            if (photosCount > 0) {
                sb.append(" (фото: ").append(photosCount).append(")");
            }
            sb.append("\n• Личных событий: ").append(customEventsCount);
            sb.append("\n• Отметок посещаемости: ").append(attendanceMarksCount);
            sb.append("\n• Перезачётов: ").append(excludedSubjectsCount);
            sb.append("\n• Избранного: ").append(favoritesCount);
            if (prefsRestored) {
                sb.append("\n• Настройки приложения обновлены");
            }
            return sb.toString();
        }
    }

    public static String suggestedBackupFileName() {
        return suggestedBackupFileName(DateUtils.todayMoscow());
    }

    public static String suggestedBackupFileName(LocalDate date) {
        LocalDate effective = date != null ? date : LocalDate.now();
        return "xRSATUx_backup_" + effective + ".zip";
    }

    /**
     * Экспортирует все пользовательские данные и прикреплённые фото заметок в ZIP-архив.
     */
    public static BackupSummary exportToZip(Context context, OutputStream outputStream) throws BackupException {
        if (context == null) {
            throw new BackupException("Контекст приложения недоступен");
        }
        if (outputStream == null) {
            throw new BackupException("Не удалось открыть файл для записи");
        }
        Context appCtx = context.getApplicationContext();

        BackupData data = new BackupData();
        data.schemaVersion = SCHEMA_VERSION;
        data.appVersion = BuildConfig.VERSION_NAME;
        data.exportedAt = DateUtils.nowIsoDateTime();

        // 1. Заметки + фото
        NoteDao noteDao = new NoteDao(appCtx);
        List<LessonNote> rawNotes = noteDao.getAllNotes();
        Map<String, File> photoFilesByName = new LinkedHashMap<>();
        List<LessonNote> exportNotes = new ArrayList<>();
        for (LessonNote n : rawNotes) {
            LessonNote copy = new LessonNote(
                    n.getId(),
                    n.getDate(),
                    n.getLessonNumber(),
                    n.getSubjectName(),
                    n.getNoteText(),
                    null,
                    n.isCompleted(),
                    n.getCreatedAt()
            );
            String imgPath = n.getImagePath();
            if (imgPath != null && !imgPath.trim().isEmpty()) {
                File imgFile = new File(imgPath);
                if (imgFile.isFile() && imgFile.canRead()) {
                    String uniqueName = registerUniquePhotoName(photoFilesByName, imgFile);
                    copy.setImagePath(uniqueName);
                }
            }
            exportNotes.add(copy);
        }
        data.notes = exportNotes;

        // 2. Личные события
        CustomEventDao eventDao = new CustomEventDao(appCtx);
        data.customEvents = eventDao.getAll();

        // 3. Посещаемость и пропуски
        SkippedLessonsManager skippedMgr = new SkippedLessonsManager(appCtx);
        AttendanceManager attendanceMgr = new AttendanceManager(appCtx);
        data.skipLimitPercent = attendanceMgr.getSkipLimitPercent();
        data.skippedLessons = skippedMgr.getAllSkippedKeys();
        data.attendedLessons = skippedMgr.getAllAttendedKeys();
        data.manualAttendanceAdjustments = attendanceMgr.getAllManualAdjustments();

        // 4. Исключённые предметы (перезачёты)
        ExcludedSubjectsManager excludedMgr = new ExcludedSubjectsManager(appCtx);
        data.excludedSubjects = excludedMgr.getAllExcludedSubjects();

        // 5. Избранное
        data.favorites = FavoritesManager.getFavorites(appCtx);

        // 6. Настройки пользователя
        PrefsManager prefsMgr = new PrefsManager(appCtx);
        UserPrefsData p = new UserPrefsData();
        p.selectionType = prefsMgr.getSelectionType();
        p.selectionName = prefsMgr.getSelectionName();
        p.activeSemester = prefsMgr.getActiveSemester();
        LocalDate fallStart = prefsMgr.getSemesterStart(SemesterManager.SEMESTER_FALL);
        LocalDate springStart = prefsMgr.getSemesterStart(SemesterManager.SEMESTER_SPRING);
        p.semesterStartFall = fallStart != null ? DateUtils.toIsoString(fallStart) : null;
        p.semesterStartSpring = springStart != null ? DateUtils.toIsoString(springStart) : null;
        p.themeMode = prefsMgr.getThemeMode(ThemeManager.MODE_SYSTEM);
        p.reducedTransparency = prefsMgr.isReducedTransparency();
        p.remindersEnabled = prefsMgr.isRemindersEnabled();
        p.reminderMinutesBefore = prefsMgr.getReminderMinutesBefore();
        p.morningDigestEnabled = prefsMgr.isMorningDigestEnabled();
        p.liveLessonNotifEnabled = prefsMgr.isLiveLessonNotificationEnabled();
        data.prefs = p;

        return writeZipBackup(data, photoFilesByName, outputStream);
    }

    /**
     * Восстанавливает пользовательские данные из потока (.zip или .json).
     * Сначала полностью валидирует и распаковывает архив во временный каталог,
     * и только при успехе применяет изменения в БД и SharedPreferences.
     */
    public static BackupSummary importFromStream(Context context, InputStream inputStream) throws BackupException {
        if (context == null) {
            throw new BackupException("Контекст приложения недоступен");
        }
        if (inputStream == null) {
            throw new BackupException("Не удалось открыть выбранный файл");
        }
        Context appCtx = context.getApplicationContext();

        File stagingDir = new File(appCtx.getCacheDir(), "backup_import_" + System.currentTimeMillis());
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            throw new BackupException("Не удалось создать временный каталог для распаковки");
        }

        try {
            ParsedBackup parsed = readBackupStream(inputStream, stagingDir);
            BackupData data = parsed.data;

            // 1. Переносим валидированные фото в постоянный каталог заметок
            File photosDir = new File(appCtx.getFilesDir(), NOTES_PHOTOS_DIR_NAME);
            if (!photosDir.exists() && !photosDir.mkdirs()) {
                throw new BackupException("Не удалось создать каталог для фотографий заметок");
            }

            Map<String, String> restoredPhotoPaths = new HashMap<>();
            int restoredPhotosCount = 0;
            for (Map.Entry<String, File> entry : parsed.extractedPhotos.entrySet()) {
                String safeName = sanitizePhotoFileName(entry.getKey());
                File src = entry.getValue();
                if (safeName == null || src == null || !src.isFile()) continue;
                File dest = resolveSafeChildFile(photosDir, safeName);
                copyFile(src, dest);
                restoredPhotoPaths.put(safeName, dest.getAbsolutePath());
                restoredPhotosCount++;
            }

            // 2. Перепривязываем пути к фото в заметках под текущее устройство
            List<LessonNote> notesToSave = new ArrayList<>();
            for (LessonNote n : data.notes) {
                String relPhoto = sanitizePhotoFileName(n.getImagePath());
                String resolvedPath = null;
                if (relPhoto != null) {
                    if (restoredPhotoPaths.containsKey(relPhoto)) {
                        resolvedPath = restoredPhotoPaths.get(relPhoto);
                    } else {
                        File existing = resolveSafeChildFile(photosDir, relPhoto);
                        if (existing.isFile()) {
                            resolvedPath = existing.getAbsolutePath();
                        }
                    }
                }
                notesToSave.add(new LessonNote(
                        0,
                        n.getDate(),
                        n.getLessonNumber(),
                        n.getSubjectName(),
                        n.getNoteText(),
                        resolvedPath,
                        n.isCompleted(),
                        n.getCreatedAt()
                ));
            }

            // 3. Транзакционно сохраняем заметки и личные события
            new NoteDao(appCtx).replaceAll(notesToSave);
            new CustomEventDao(appCtx).replaceAll(data.customEvents);

            // 4. Восстанавливаем посещаемость и пропуски
            new SkippedLessonsManager(appCtx).replaceAll(data.skippedLessons, data.attendedLessons);
            AttendanceManager attendanceMgr = new AttendanceManager(appCtx);
            attendanceMgr.setSkipLimitPercent(data.skipLimitPercent);
            attendanceMgr.replaceAllManualAdjustments(data.manualAttendanceAdjustments);

            // 5. Восстанавливаем перезачёты
            new ExcludedSubjectsManager(appCtx).replaceAllExcludedSubjects(data.excludedSubjects);

            // 6. Восстанавливаем избранное
            FavoritesManager.replaceFavorites(appCtx, data.favorites);

            // 7. Восстанавливаем настройки
            boolean prefsRestored = applyUserPrefs(appCtx, data.prefs);

            // 8. Обновляем логику, виджеты и уведомления
            ScheduleRepository.getInstance(appCtx).refreshLogic();
            WidgetUpdateHelper.updateAllWidgets(appCtx);
            try {
                ReminderScheduler.rescheduleAll(appCtx);
                LiveLessonService.syncNow(appCtx);
            } catch (Exception ignored) {
                // Уведомления могут быть недоступны при отсутствии разрешений
            }

            BackupSummary summary = buildSummary(data, restoredPhotosCount, prefsRestored);
            return summary;
        } catch (IOException e) {
            throw new BackupException("Ошибка чтения или записи файлов при восстановлении: " + e.getMessage(), e);
        } finally {
            deleteRecursively(stagingDir);
        }
    }

    private static boolean applyUserPrefs(Context appCtx, UserPrefsData p) {
        if (p == null) return false;
        PrefsManager prefsMgr = new PrefsManager(appCtx);
        boolean any = false;

        if (p.activeSemester != null && !p.activeSemester.trim().isEmpty()) {
            prefsMgr.setActiveSemester(p.activeSemester.trim());
            any = true;
        }
        if (p.semesterStartFall != null && !p.semesterStartFall.trim().isEmpty()) {
            LocalDate d = DateUtils.parseIsoDate(p.semesterStartFall.trim());
            if (d != null) {
                prefsMgr.saveSemesterStart(SemesterManager.SEMESTER_FALL, d);
                any = true;
            }
        }
        if (p.semesterStartSpring != null && !p.semesterStartSpring.trim().isEmpty()) {
            LocalDate d = DateUtils.parseIsoDate(p.semesterStartSpring.trim());
            if (d != null) {
                prefsMgr.saveSemesterStart(SemesterManager.SEMESTER_SPRING, d);
                any = true;
            }
        }
        if (p.selectionType != null && !p.selectionType.trim().isEmpty()
                && p.selectionName != null && !p.selectionName.trim().isEmpty()) {
            ScheduleRepository.getInstance(appCtx)
                    .saveSelection(p.selectionType.trim(), p.selectionName.trim());
            any = true;
        }
        if (p.themeMode != null) {
            prefsMgr.saveThemeMode(p.themeMode);
            any = true;
        }
        if (p.reducedTransparency != null) {
            prefsMgr.setReducedTransparency(p.reducedTransparency);
            any = true;
        }
        if (p.remindersEnabled != null) {
            prefsMgr.setRemindersEnabled(p.remindersEnabled);
            any = true;
        }
        if (p.reminderMinutesBefore != null) {
            int mins = p.reminderMinutesBefore;
            if (mins == 10 || mins == 15 || mins == 30) {
                prefsMgr.setReminderMinutesBefore(mins);
                any = true;
            }
        }
        if (p.morningDigestEnabled != null) {
            prefsMgr.setMorningDigestEnabled(p.morningDigestEnabled);
            any = true;
        }
        if (p.liveLessonNotifEnabled != null) {
            prefsMgr.setLiveLessonNotificationEnabled(p.liveLessonNotifEnabled);
            any = true;
        }
        return any;
    }

    /**
     * Записывает BackupData и карту фото-файлов в ZIP-поток.
     */
    public static BackupSummary writeZipBackup(
            BackupData data,
            Map<String, File> photoFilesByName,
            OutputStream outputStream) throws BackupException {
        if (data == null) {
            throw new BackupException("Нет данных для резервного копирования");
        }
        if (outputStream == null) {
            throw new BackupException("Поток вывода не задан");
        }
        String json = serializeToJson(data);
        int packedPhotos = 0;

        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            ZipEntry jsonEntry = new ZipEntry(BACKUP_JSON_ENTRY);
            zos.putNextEntry(jsonEntry);
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            if (photoFilesByName != null && !photoFilesByName.isEmpty()) {
                Set<String> writtenNames = new HashSet<>();
                for (Map.Entry<String, File> entry : photoFilesByName.entrySet()) {
                    String safeName = sanitizePhotoFileName(entry.getKey());
                    File file = entry.getValue();
                    if (safeName == null || file == null || !file.isFile() || !file.canRead()) {
                        continue;
                    }
                    if (!writtenNames.add(safeName)) {
                        continue;
                    }
                    ZipEntry photoEntry = new ZipEntry(PHOTOS_DIR_PREFIX + safeName);
                    zos.putNextEntry(photoEntry);
                    try (InputStream fis = new FileInputStream(file)) {
                        copyStreamBounded(fis, zos, MAX_PHOTO_BYTES);
                    }
                    zos.closeEntry();
                    packedPhotos++;
                }
            }
            zos.finish();
        } catch (IOException e) {
            throw new BackupException("Не удалось записать ZIP-архив: " + e.getMessage(), e);
        }

        return buildSummary(data, packedPhotos, data.prefs != null);
    }

    /**
     * Определяет формат входного потока (ZIP или чистый JSON), валидирует структуру,
     * проверяет защиту от Zip Slip и распаковывает фотографии в photosStagingDir.
     */
    public static ParsedBackup readBackupStream(InputStream inputStream, File photosStagingDir) throws BackupException {
        if (inputStream == null) {
            throw new BackupException("Пустой входной поток");
        }

        try {
            PushbackInputStream pbis = new PushbackInputStream(inputStream, 4);
            byte[] header = new byte[4];
            int read = readFullyUpTo(pbis, header);
            if (read <= 0) {
                throw new BackupException("Выбранный файл пуст");
            }
            pbis.unread(header, 0, read);

            if (isZipHeader(header, read)) {
                return readZipStream(pbis, photosStagingDir);
            } else {
                String jsonText = readStreamAsUtf8(pbis, MAX_JSON_BYTES);
                BackupData data = deserializeFromJson(jsonText);
                return new ParsedBackup(data, Collections.emptyMap());
            }
        } catch (BackupException e) {
            throw e;
        } catch (IOException e) {
            throw new BackupException("Ошибка чтения файла бэкапа: " + e.getMessage(), e);
        }
    }

    private static ParsedBackup readZipStream(InputStream inputStream, File photosStagingDir) throws BackupException {
        String backupJson = null;
        Map<String, File> extractedPhotos = new LinkedHashMap<>();
        int entryCount = 0;

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new BackupException("Архив содержит слишком много файлов");
                }
                String name = entry.getName();
                validateZipEntryName(name);

                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                if (BACKUP_JSON_ENTRY.equals(name)) {
                    backupJson = readStreamAsUtf8(zis, MAX_JSON_BYTES);
                } else if (name.startsWith(PHOTOS_DIR_PREFIX)) {
                    String relativeName = name.substring(PHOTOS_DIR_PREFIX.length());
                    if (relativeName.isEmpty() || relativeName.contains("/") || relativeName.contains("\\")) {
                        throw new BackupException("Некорректный путь к фото в архиве: " + name);
                    }
                    String safeName = sanitizePhotoFileName(relativeName);
                    if (safeName == null) {
                        throw new BackupException("Некорректное имя фото в архиве: " + name);
                    }
                    if (photosStagingDir != null) {
                        if (!photosStagingDir.exists() && !photosStagingDir.mkdirs()) {
                            throw new BackupException("Не удалось создать каталог для фото");
                        }
                        File destFile = resolveSafeChildFile(photosStagingDir, safeName);
                        try (OutputStream fos = new FileOutputStream(destFile)) {
                            copyStreamBounded(zis, fos, MAX_PHOTO_BYTES);
                        }
                        extractedPhotos.put(safeName, destFile);
                    }
                }
                zis.closeEntry();
            }
        } catch (BackupException e) {
            throw e;
        } catch (IOException e) {
            throw new BackupException("Повреждённый ZIP-архив: " + e.getMessage(), e);
        }

        if (backupJson == null || backupJson.trim().isEmpty()) {
            throw new BackupException("В архиве не найден файл " + BACKUP_JSON_ENTRY);
        }

        BackupData data = deserializeFromJson(backupJson);
        return new ParsedBackup(data, extractedPhotos);
    }

    /**
     * Защита от Zip Slip: отклоняет любые пути с "..", обратными слешами, абсолютными путями и NUL-байтами.
     */
    static void validateZipEntryName(String entryName) throws BackupException {
        if (entryName == null || entryName.trim().isEmpty()) {
            throw new BackupException("Обнаружена запись с пустым именем в архиве");
        }
        if (entryName.indexOf('\0') >= 0
                || entryName.contains("\\")
                || entryName.startsWith("/")
                || entryName.contains(":")) {
            throw new BackupException("Обнаружен небезопасный путь в архиве: " + entryName);
        }
        String[] segments = entryName.split("/");
        for (String seg : segments) {
            if ("..".equals(seg) || ".".equals(seg)) {
                throw new BackupException("Обнаружена попытка выхода за пределы каталога (Zip Slip): " + entryName);
            }
        }
    }

    static File resolveSafeChildFile(File parentDir, String fileName) throws BackupException, IOException {
        if (parentDir == null || fileName == null || fileName.isEmpty()) {
            throw new BackupException("Некорректный путь к файлу");
        }
        String canonicalParent = parentDir.getCanonicalPath();
        File candidate = new File(parentDir, fileName);
        String canonicalCandidate = candidate.getCanonicalPath();
        String expectedPrefix = canonicalParent.endsWith(File.separator)
                ? canonicalParent
                : canonicalParent + File.separator;
        if (!canonicalCandidate.startsWith(expectedPrefix)) {
            throw new BackupException("Обнаружена попытка выхода за пределы каталога (Zip Slip): " + fileName);
        }
        return candidate;
    }

    public static String serializeToJson(BackupData data) throws BackupException {
        if (data == null) {
            throw new BackupException("Данные бэкапа отсутствуют");
        }
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", data.schemaVersion > 0 ? data.schemaVersion : SCHEMA_VERSION);
            root.put("appVersion", data.appVersion != null ? data.appVersion : "");
            root.put("exportedAt", data.exportedAt != null ? data.exportedAt : "");

            // notes
            JSONArray notesArr = new JSONArray();
            if (data.notes != null) {
                for (LessonNote n : data.notes) {
                    if (n == null || n.getDate() == null || n.getDate().trim().isEmpty()) continue;
                    JSONObject obj = new JSONObject();
                    obj.put("date", n.getDate().trim());
                    obj.put("lessonNumber", n.getLessonNumber());
                    obj.put("subjectName", n.getSubjectName() != null ? n.getSubjectName() : "");
                    obj.put("noteText", n.getNoteText() != null ? n.getNoteText() : "");
                    String photoFileName = sanitizePhotoFileName(n.getImagePath());
                    if (photoFileName != null) {
                        obj.put("photoFileName", photoFileName);
                    }
                    obj.put("isCompleted", n.isCompleted());
                    if (n.getCreatedAt() != null && !n.getCreatedAt().isEmpty()) {
                        obj.put("createdAt", n.getCreatedAt());
                    }
                    notesArr.put(obj);
                }
            }
            root.put("notes", notesArr);

            // customEvents
            JSONArray eventsArr = new JSONArray();
            if (data.customEvents != null) {
                for (CustomEvent e : data.customEvents) {
                    if (e == null || e.getGroupName() == null || e.getGroupName().trim().isEmpty()) continue;
                    JSONObject obj = new JSONObject();
                    obj.put("groupName", e.getGroupName().trim());
                    obj.put("isGroup", e.isGroup());
                    if (e.getDateIso() != null) obj.put("dateIso", e.getDateIso());
                    obj.put("dayOfWeek", e.getDayOfWeek());
                    if (e.getWeekType() != null) obj.put("weekType", e.getWeekType());
                    obj.put("lessonNumber", e.getLessonNumber());
                    obj.put("subjectName", e.getSubjectName() != null ? e.getSubjectName() : "");
                    if (e.getTeacherName() != null) obj.put("teacherName", e.getTeacherName());
                    if (e.getRoom() != null) obj.put("room", e.getRoom());
                    if (e.getNote() != null) obj.put("note", e.getNote());
                    eventsArr.put(obj);
                }
            }
            root.put("customEvents", eventsArr);

            // attendance
            JSONObject attendanceObj = new JSONObject();
            attendanceObj.put("skipLimitPercent", data.skipLimitPercent);
            JSONArray skippedArr = new JSONArray();
            if (data.skippedLessons != null) {
                List<String> sorted = new ArrayList<>(data.skippedLessons);
                Collections.sort(sorted);
                for (String k : sorted) {
                    if (k != null && !k.isEmpty()) skippedArr.put(k);
                }
            }
            attendanceObj.put("skippedLessons", skippedArr);

            JSONArray attendedArr = new JSONArray();
            if (data.attendedLessons != null) {
                List<String> sorted = new ArrayList<>(data.attendedLessons);
                Collections.sort(sorted);
                for (String k : sorted) {
                    if (k != null && !k.isEmpty()) attendedArr.put(k);
                }
            }
            attendanceObj.put("attendedLessons", attendedArr);

            JSONObject manualObj = new JSONObject();
            if (data.manualAttendanceAdjustments != null) {
                for (Map.Entry<String, Integer> entry : data.manualAttendanceAdjustments.entrySet()) {
                    if (entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null) {
                        manualObj.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            attendanceObj.put("manualAdjustments", manualObj);
            root.put("attendance", attendanceObj);

            // excludedSubjects
            JSONObject excludedObj = new JSONObject();
            if (data.excludedSubjects != null) {
                for (Map.Entry<String, Set<String>> entry : data.excludedSubjects.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().trim().isEmpty() || entry.getValue() == null) {
                        continue;
                    }
                    JSONArray subArr = new JSONArray();
                    List<String> sorted = new ArrayList<>(entry.getValue());
                    Collections.sort(sorted);
                    for (String s : sorted) {
                        if (s != null && !s.trim().isEmpty()) subArr.put(s.trim());
                    }
                    if (subArr.length() > 0) {
                        excludedObj.put(entry.getKey().trim(), subArr);
                    }
                }
            }
            root.put("excludedSubjects", excludedObj);

            // favorites
            JSONArray favArr = new JSONArray();
            if (data.favorites != null) {
                for (GroupOrTeacher item : data.favorites) {
                    if (item == null || item.getName() == null || item.getName().trim().isEmpty()) continue;
                    JSONObject obj = new JSONObject();
                    obj.put("type", item.getType() != null ? item.getType() : GroupOrTeacher.TYPE_GROUP);
                    obj.put("name", item.getName().trim());
                    favArr.put(obj);
                }
            }
            root.put("favorites", favArr);

            // prefs
            JSONObject prefsObj = new JSONObject();
            if (data.prefs != null) {
                if (data.prefs.selectionType != null) prefsObj.put("selectionType", data.prefs.selectionType);
                if (data.prefs.selectionName != null) prefsObj.put("selectionName", data.prefs.selectionName);
                if (data.prefs.activeSemester != null) prefsObj.put("activeSemester", data.prefs.activeSemester);
                if (data.prefs.semesterStartFall != null) prefsObj.put("semesterStartFall", data.prefs.semesterStartFall);
                if (data.prefs.semesterStartSpring != null) prefsObj.put("semesterStartSpring", data.prefs.semesterStartSpring);
                if (data.prefs.themeMode != null) prefsObj.put("themeMode", data.prefs.themeMode);
                if (data.prefs.reducedTransparency != null) prefsObj.put("reducedTransparency", data.prefs.reducedTransparency);
                if (data.prefs.remindersEnabled != null) prefsObj.put("remindersEnabled", data.prefs.remindersEnabled);
                if (data.prefs.reminderMinutesBefore != null) prefsObj.put("reminderMinutesBefore", data.prefs.reminderMinutesBefore);
                if (data.prefs.morningDigestEnabled != null) prefsObj.put("morningDigestEnabled", data.prefs.morningDigestEnabled);
                if (data.prefs.liveLessonNotifEnabled != null) prefsObj.put("liveLessonNotifEnabled", data.prefs.liveLessonNotifEnabled);
            }
            root.put("prefs", prefsObj);

            return root.toString(2);
        } catch (JSONException e) {
            throw new BackupException("Ошибка сериализации данных в JSON: " + e.getMessage(), e);
        }
    }

    public static BackupData deserializeFromJson(String rawJson) throws BackupException {
        if (rawJson == null) {
            throw new BackupException("Файл бэкапа пуст");
        }
        String trimmed = stripUtf8Bom(rawJson).trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            throw new BackupException("Неподдерживаемый формат файла: ожидается .zip или .json бэкап");
        }

        try {
            JSONObject root = new JSONObject(trimmed);
            if (!root.has("schemaVersion")) {
                throw new BackupException("Файл не является бэкапом xRSATUx Schedule (отсутствует schemaVersion)");
            }
            int schemaVersion = root.optInt("schemaVersion", -1);
            if (schemaVersion < 1 || schemaVersion > SCHEMA_VERSION) {
                throw new BackupException("Неподдерживаемая версия формата бэкапа: " + schemaVersion);
            }

            BackupData data = new BackupData();
            data.schemaVersion = schemaVersion;
            data.appVersion = root.optString("appVersion", "");
            data.exportedAt = root.optString("exportedAt", "");

            // notes
            JSONArray notesArr = root.optJSONArray("notes");
            if (notesArr != null) {
                for (int i = 0; i < notesArr.length(); i++) {
                    JSONObject obj = notesArr.optJSONObject(i);
                    if (obj == null) continue;
                    String date = obj.optString("date", "").trim();
                    int lessonNumber = obj.optInt("lessonNumber", 0);
                    if (date.isEmpty() || lessonNumber <= 0) continue;
                    String subject = obj.optString("subjectName", "");
                    String text = obj.optString("noteText", "");
                    String photoFileName = optNullableString(obj, "photoFileName");
                    if (photoFileName != null) {
                        photoFileName = sanitizePhotoFileName(photoFileName);
                    }
                    boolean completed = obj.optBoolean("isCompleted", false);
                    String createdAt = optNullableString(obj, "createdAt");
                    data.notes.add(new LessonNote(0, date, lessonNumber, subject, text, photoFileName, completed, createdAt));
                }
            }

            // customEvents
            JSONArray eventsArr = root.optJSONArray("customEvents");
            if (eventsArr != null) {
                for (int i = 0; i < eventsArr.length(); i++) {
                    JSONObject obj = eventsArr.optJSONObject(i);
                    if (obj == null) continue;
                    String groupName = obj.optString("groupName", "").trim();
                    if (groupName.isEmpty()) continue;
                    CustomEvent e = new CustomEvent();
                    e.setGroupName(groupName);
                    e.setGroup(obj.optBoolean("isGroup", true));
                    e.setDateIso(optNullableString(obj, "dateIso"));
                    e.setDayOfWeek(obj.optInt("dayOfWeek", 0));
                    e.setWeekType(optNullableString(obj, "weekType"));
                    e.setLessonNumber(obj.optInt("lessonNumber", 1));
                    e.setSubjectName(obj.optString("subjectName", ""));
                    e.setTeacherName(optNullableString(obj, "teacherName"));
                    e.setRoom(optNullableString(obj, "room"));
                    e.setNote(optNullableString(obj, "note"));
                    data.customEvents.add(e);
                }
            }

            // attendance
            JSONObject attObj = root.optJSONObject("attendance");
            if (attObj != null) {
                data.skipLimitPercent = attObj.optInt("skipLimitPercent", AttendanceManager.DEFAULT_SKIP_LIMIT_PCT);
                JSONArray skippedArr = attObj.optJSONArray("skippedLessons");
                if (skippedArr != null) {
                    for (int i = 0; i < skippedArr.length(); i++) {
                        String k = skippedArr.optString(i, "");
                        if (!k.trim().isEmpty()) data.skippedLessons.add(k);
                    }
                }
                JSONArray attendedArr = attObj.optJSONArray("attendedLessons");
                if (attendedArr != null) {
                    for (int i = 0; i < attendedArr.length(); i++) {
                        String k = attendedArr.optString(i, "");
                        if (!k.trim().isEmpty()) data.attendedLessons.add(k);
                    }
                }
                JSONObject manualObj = attObj.optJSONObject("manualAdjustments");
                if (manualObj != null) {
                    Iterator<String> keys = manualObj.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        if (k != null && !k.isEmpty()) {
                            int v = manualObj.optInt(k, 0);
                            if (v != 0) {
                                data.manualAttendanceAdjustments.put(k, v);
                            }
                        }
                    }
                }
            }

            // excludedSubjects
            JSONObject excludedObj = root.optJSONObject("excludedSubjects");
            if (excludedObj != null) {
                Iterator<String> keys = excludedObj.keys();
                while (keys.hasNext()) {
                    String group = keys.next();
                    if (group == null || group.trim().isEmpty()) continue;
                    JSONArray arr = excludedObj.optJSONArray(group);
                    if (arr == null) continue;
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String s = arr.optString(i, "").trim();
                        if (!s.isEmpty()) set.add(s);
                    }
                    if (!set.isEmpty()) {
                        data.excludedSubjects.put(group.trim(), set);
                    }
                }
            }

            // favorites
            JSONArray favArr = root.optJSONArray("favorites");
            if (favArr != null) {
                for (int i = 0; i < favArr.length(); i++) {
                    JSONObject obj = favArr.optJSONObject(i);
                    if (obj == null) continue;
                    String name = obj.optString("name", "").trim();
                    if (name.isEmpty()) continue;
                    String type = obj.optString("type", GroupOrTeacher.TYPE_GROUP).trim();
                    if (type.isEmpty()) type = GroupOrTeacher.TYPE_GROUP;
                    data.favorites.add(new GroupOrTeacher(name, type));
                }
            }

            // prefs
            JSONObject prefsObj = root.optJSONObject("prefs");
            if (prefsObj != null) {
                UserPrefsData p = new UserPrefsData();
                p.selectionType = optNullableString(prefsObj, "selectionType");
                p.selectionName = optNullableString(prefsObj, "selectionName");
                p.activeSemester = optNullableString(prefsObj, "activeSemester");
                p.semesterStartFall = optNullableString(prefsObj, "semesterStartFall");
                p.semesterStartSpring = optNullableString(prefsObj, "semesterStartSpring");
                if (prefsObj.has("themeMode") && !prefsObj.isNull("themeMode")) {
                    p.themeMode = prefsObj.optInt("themeMode");
                }
                if (prefsObj.has("reducedTransparency") && !prefsObj.isNull("reducedTransparency")) {
                    p.reducedTransparency = prefsObj.optBoolean("reducedTransparency");
                }
                if (prefsObj.has("remindersEnabled") && !prefsObj.isNull("remindersEnabled")) {
                    p.remindersEnabled = prefsObj.optBoolean("remindersEnabled");
                }
                if (prefsObj.has("reminderMinutesBefore") && !prefsObj.isNull("reminderMinutesBefore")) {
                    p.reminderMinutesBefore = prefsObj.optInt("reminderMinutesBefore");
                }
                if (prefsObj.has("morningDigestEnabled") && !prefsObj.isNull("morningDigestEnabled")) {
                    p.morningDigestEnabled = prefsObj.optBoolean("morningDigestEnabled");
                }
                if (prefsObj.has("liveLessonNotifEnabled") && !prefsObj.isNull("liveLessonNotifEnabled")) {
                    p.liveLessonNotifEnabled = prefsObj.optBoolean("liveLessonNotifEnabled");
                }
                data.prefs = p;
            }

            return data;
        } catch (JSONException e) {
            throw new BackupException("Повреждённый JSON-файл бэкапа: " + e.getMessage(), e);
        }
    }

    private static BackupSummary buildSummary(BackupData data, int photosCount, boolean prefsRestored) {
        BackupSummary summary = new BackupSummary();
        summary.notesCount = data.notes != null ? data.notes.size() : 0;
        summary.photosCount = photosCount;
        summary.customEventsCount = data.customEvents != null ? data.customEvents.size() : 0;
        summary.favoritesCount = data.favorites != null ? data.favorites.size() : 0;
        int attCount = (data.skippedLessons != null ? data.skippedLessons.size() : 0)
                + (data.attendedLessons != null ? data.attendedLessons.size() : 0)
                + (data.manualAttendanceAdjustments != null ? data.manualAttendanceAdjustments.size() : 0);
        summary.attendanceMarksCount = attCount;
        int excludedTotal = 0;
        if (data.excludedSubjects != null) {
            for (Set<String> set : data.excludedSubjects.values()) {
                if (set != null) excludedTotal += set.size();
            }
        }
        summary.excludedSubjectsCount = excludedTotal;
        summary.prefsRestored = prefsRestored;
        summary.exportedAt = data.exportedAt;
        summary.appVersion = data.appVersion;
        return summary;
    }

    private static String registerUniquePhotoName(Map<String, File> map, File file) {
        String baseName = sanitizePhotoFileName(file.getName());
        if (baseName == null) {
            baseName = "photo_" + System.currentTimeMillis() + ".jpg";
        }
        File existing = map.get(baseName);
        if (existing == null || existing.getAbsolutePath().equals(file.getAbsolutePath())) {
            map.put(baseName, file);
            return baseName;
        }
        int dot = baseName.lastIndexOf('.');
        String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
        String ext = dot > 0 ? baseName.substring(dot) : "";
        int counter = 1;
        String candidate;
        do {
            candidate = stem + "_" + counter + ext;
            counter++;
        } while (map.containsKey(candidate));
        map.put(candidate, file);
        return candidate;
    }

    static String sanitizePhotoFileName(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        // Извлекаем только имя файла без директорий
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        String name = slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)
                || name.indexOf('\0') >= 0 || name.contains(":")) {
            return null;
        }
        return name;
    }

    private static boolean isZipHeader(byte[] header, int len) {
        if (len < 4) return false;
        return header[0] == 0x50 && header[1] == 0x4B
                && ((header[2] == 0x03 && header[3] == 0x04)
                || (header[2] == 0x05 && header[3] == 0x06)
                || (header[2] == 0x07 && header[3] == 0x08));
    }

    private static int readFullyUpTo(InputStream is, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = is.read(buf, total, buf.length - total);
            if (n == -1) break;
            total += n;
        }
        return total;
    }

    private static String readStreamAsUtf8(InputStream is, int maxBytes) throws IOException, BackupException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copyStreamBounded(is, baos, maxBytes);
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private static void copyStreamBounded(InputStream is, OutputStream os, int maxBytes)
            throws IOException, BackupException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = is.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new BackupException(String.format(Locale.US,
                        "Размер файла превышает допустимый лимит (%d МБ)", maxBytes / (1024 * 1024)));
            }
            os.write(buffer, 0, read);
        }
    }

    private static void copyFile(File src, File dest) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static String stripUtf8Bom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private static String optNullableString(JSONObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) {
            return null;
        }
        String val = obj.optString(key, null);
        return (val != null && !val.isEmpty()) ? val : null;
    }
}
