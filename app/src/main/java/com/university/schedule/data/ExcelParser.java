package com.university.schedule.data;

import android.util.Log;
import com.university.schedule.data.db.ScheduleDao;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.util.Constants;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExcelParser {
    private static final String TAG = "ExcelParser";
    private static final Pattern WEEK_PATTERN =
            Pattern.compile("\\(Недели?\\s*([^)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_PATTERN =
            Pattern.compile("(?<![\\p{L}0-9])(оЛ|оП|ЛР|Экзамен|Л|П)(?![\\p{L}0-9])");
    private static final Pattern GROUP_TOKEN =
            Pattern.compile("^[A-Za-zА-Яа-яЁё][A-Za-zА-Яа-яЁё0-9()]*-\\d+[A-Za-zА-Яа-яЁё0-9().-]*$");
    private static final Pattern ROOM_TOKEN =
            Pattern.compile("^[А-ЯЁA-Za-z]?\\d+-\\d+([а-яА-ЯёЁ])?$|^[А-ЯЁA-Za-z]+-\\d+(-\\d+)*([а-яА-ЯёЁ])?$");
    private static final Pattern INITIALS =
            Pattern.compile("^[А-ЯЁA-Z]\\.([А-ЯЁA-Z]\\.)?$");
    private static final Pattern NAME_WORD =
            Pattern.compile("^[А-ЯЁA-Z][а-яёa-z]{1,}$");
    
    private static final Set<String> ROOM_TWO_WORDS = new HashSet<>(
            Arrays.asList("Большой спортзал", "Точка кипения"));
    // ВАЖНО: "saby" добавлено сюда для распознавания платформы Saby как аудитории.
    private static final Set<String> ROOM_ONE_WORD = new HashSet<>(
            Arrays.asList("онлайн", "предприятия", "saby"));
            
    private static final String[] DAY_NAMES = {
            "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"
    };

    private static final long MAX_XLSX_SIZE_BYTES = 50L * 1024 * 1024; // 50 МБ

    public List<ScheduleItem> parseGroups(InputStream in) throws Exception {
        return parseSheet(in, Constants.SHEET_INDEX_GROUPS, ScheduleDao.SOURCE_GROUP);
    }

    public List<ScheduleItem> parseTeachers(InputStream in) throws Exception {
        return parseSheet(in, Constants.SHEET_INDEX_TEACHERS, ScheduleDao.SOURCE_TEACHER);
    }

    private List<ScheduleItem> parseSheet(InputStream in, int sheetIndex, String source) throws Exception {
        List<ScheduleItem> result = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new BoundedInputStream(in, MAX_XLSX_SIZE_BYTES))) {
            if (sheetIndex >= wb.getNumberOfSheets()) {
                Log.w(TAG, "Лист #" + sheetIndex + " отсутствует (всего " + wb.getNumberOfSheets() + ")");
                return result;
            }
            Sheet sheet = wb.getSheetAt(sheetIndex);
            int headerRowIdx = findHeaderRow(sheet);
            if (headerRowIdx < 0) {
                Log.w(TAG, "Не найдена строка-шапка на листе " + sheet.getSheetName());
                return result;
            }
            Row headerRow = sheet.getRow(headerRowIdx);
            int headerLen = headerRow.getLastCellNum();
            String[] header = new String[headerLen];
            for (int c = 0; c < headerLen; c++) {
                header[c] = trim(getStr(headerRow.getCell(c)));
            }
            Log.d(TAG, "Лист \"" + sheet.getSheetName() + "\": шапка в строке " + headerRowIdx
                    + ", всего строк " + (sheet.getLastRowNum() + 1));

            String currentWeekType = null;
            int currentDay = -1;
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String wtRaw = trim(getStr(row.getCell(0)));
                if (!wtRaw.isEmpty()) {
                    String parsed = parseWeekType(wtRaw);
                    if (parsed != null) currentWeekType = parsed;
                }
                String dayRaw = trim(getStr(row.getCell(1)));
                if (!dayRaw.isEmpty()) {
                    int d = parseDayOfWeek(dayRaw);
                    if (d >= 0) currentDay = d;
                }
                String lessonRaw = trim(getStr(row.getCell(2)));
                if (lessonRaw.isEmpty()) continue;
                int lesson = parseLessonNumber(lessonRaw);
                if (lesson < 0) continue;
                if (currentWeekType == null || currentDay < 0) continue;

                int last = row.getLastCellNum();
                for (int c = 3; c < last && c < headerLen; c++) {
                    String colName = header[c];
                    if (colName == null || colName.isEmpty()) continue;
                    String cell = getStr(row.getCell(c));
                    if (cell == null) continue;
                    for (String line : cell.split("\\r?\n")) {
                        ScheduleItem item = parseLine(line, currentWeekType, currentDay, lesson, colName, source);
                        if (item != null) result.add(item);
                    }
                }
            }
        }
        Log.d(TAG, "Распознано (" + source + "): " + result.size());
        return result;
    }

    private int findHeaderRow(Sheet sheet) {
        int limit = Math.min(6, sheet.getLastRowNum() + 1);
        for (int r = 0; r < limit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String c1 = trim(getStr(row.getCell(1)));
            if ("Группа".equalsIgnoreCase(c1)) return r;
        }
        return -1;
    }

    ScheduleItem parseLine(String raw, String weekType, int day, int lesson,
                           String colName, String source) {
        if (raw == null) return null;
        String line = raw.trim();
        if (line.isEmpty()) return null;

        String weekSpec = "";
        Matcher wm = WEEK_PATTERN.matcher(line);
        if (wm.find()) {
            weekSpec = wm.group(1).replaceAll("\\s+", "");
            line = (line.substring(0, wm.start()) + " " + line.substring(wm.end())).replaceAll("\\s+", " ").trim();
        }

        Matcher tm = TYPE_PATTERN.matcher(line);
        int typeStart = -1, typeEnd = -1;
        String type = "";
        if (tm.find()) {
            typeStart = tm.start();
            typeEnd = tm.end();
            type = tm.group(1);
        }

        String subjectRaw;
        String tail;
        if (typeStart >= 0) {
            subjectRaw = line.substring(0, typeStart).trim();
            tail = line.substring(typeEnd).trim();
        } else {
            subjectRaw = null;
            tail = line;
        }

        List<String> tt = tokens(tail);
        String room = peelRoom(tt);
        String teacher;
        // Для листа преподавателей: первый токен может быть группой (например "ИВТ-401")
        String groupName = null;
        if (ScheduleDao.SOURCE_TEACHER.equals(source) && !tt.isEmpty()) {
            String firstToken = tt.get(0);
            if (GROUP_TOKEN.matcher(firstToken).matches()) {
                groupName = firstToken;
                tt.remove(0);
            }
        }
        if (typeStart >= 0) {
            teacher = join(tt);
        } else {
            teacher = peelTeacherSuffix(tt);
            subjectRaw = join(tt);
        }

        String subject = cleanSubject(subjectRaw);
        if (subject.isEmpty()) return null;

        ScheduleItem item = new ScheduleItem();
        item.setWeekType(weekType);
        item.setDayOfWeek(day);
        item.setLessonNumber(lesson);
        item.setSubjectName(subject);
        item.setLessonType(type);
        item.setTeacherName(teacher);
        item.setRoom(room);
        item.setWeekSpec(weekSpec);
        item.setSource(source);
        item.setGroupName(groupName);

        if (ScheduleDao.SOURCE_GROUP.equals(source)) {
            item.setGroupName(colName);
        } else {
            item.setTeacherName(colName);
        }
        return item;
    }

    private List<String> tokens(String s) {
        List<String> list = new ArrayList<>();
        if (s == null) return list;
        for (String t : s.split("\\s+")) {
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    private String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String t : list) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(t);
        }
        return sb.toString();
    }

    private String peelRoom(List<String> tt) {
        if (tt.isEmpty()) return "";
        
        // 1. Специфические трёхсловные названия (партнёры/платформы)
        if (tt.size() >= 3) {
            String three = tt.get(tt.size() - 3) + " " + tt.get(tt.size() - 2) + " " + tt.get(tt.size() - 1);
            if (three.equalsIgnoreCase("ООО НПО «Криста»") || three.equalsIgnoreCase("ООО НПО Криста")) {
                tt.remove(tt.size() - 1);
                tt.remove(tt.size() - 1);
                tt.remove(tt.size() - 1);
                return three;
            }
        }

        // 2. Двухсловные аудитории
        if (tt.size() >= 2) {
            String two = tt.get(tt.size() - 2) + " " + tt.get(tt.size() - 1);
            if (ROOM_TWO_WORDS.contains(two)) {
                tt.remove(tt.size() - 1);
                tt.remove(tt.size() - 1);
                return two;
            }
        }
        
        // 3. Однословные аудитории и стандартные токены (Г-417, 1-114 и т.д.)
        String last = tt.get(tt.size() - 1);
        if (ROOM_ONE_WORD.contains(last.toLowerCase()) || ROOM_TOKEN.matcher(last).matches()) {
            tt.remove(tt.size() - 1);
            return last;
        }
        return "";
    }

    private String peelTeacherSuffix(List<String> tt) {
        int i = tt.size();
        while (i > 0) {
            String t = tt.get(i - 1);
            if (INITIALS.matcher(t).matches() || NAME_WORD.matcher(t).matches()) {
                i--;
            } else {
                break;
            }
        }
        String teacher = join(tt.subList(i, tt.size()));
        while (tt.size() > i) tt.remove(tt.size() - 1);
        return teacher;
    }

    private String cleanSubject(String raw) {
        if (raw == null) return "";
        List<String> st = tokens(raw);
        while (!st.isEmpty()) {
            String t = st.get(0);
            if ("ИР".equals(t) || GROUP_TOKEN.matcher(t).matches()) {
                st.remove(0);
            } else {
                break;
            }
        }
        String s = join(st);
        if (s.isEmpty()) s = raw.trim();
        return s;
    }

    private String parseWeekType(String s) {
        if (s == null || s.isEmpty()) return null;
        String l = s.toLowerCase();
        if (l.contains("нечёт") || l.contains("нечет")) return Constants.WEEK_TYPE_ODD;
        if (l.contains("чёт") || l.contains("чет")) return Constants.WEEK_TYPE_EVEN;
        return null;
    }

    private int parseDayOfWeek(String s) {
        if (s == null) return -1;
        for (int i = 0; i < DAY_NAMES.length; i++) {
            if (DAY_NAMES[i].equalsIgnoreCase(s)) return i + 1;
        }
        return -1;
    }

    private int parseLessonNumber(String s) {
        if (s == null) return -1;
        String d = s.replaceAll("[^0-9]", "");
        if (!d.isEmpty()) {
            int n = d.charAt(0) - '0';
            if (n >= 1 && n <= 7) return n;
        }
        return -1;
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String getStr(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue();
            if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((int) cell.getNumericCellValue());
            if (cell.getCellType() == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
            if (cell.getCellType() == CellType.FORMULA) {
                try { return cell.getStringCellValue(); }
                catch (Exception e) {
                    try { return String.valueOf((int) cell.getNumericCellValue()); }
                    catch (Exception e2) { return null; }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private final long maxBytes;
        private long readSoFar;

        BoundedInputStream(InputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            checkLimit(1);
            int b = delegate.read();
            if (b != -1) readSoFar++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            checkLimit(len);
            int n = delegate.read(b, off, len);
            if (n > 0) readSoFar += n;
            return n;
        }

        private void checkLimit(int about) throws IOException {
            if (readSoFar + about > maxBytes) {
                throw new IOException("Файл расписания превышает допустимый размер ("
                        + maxBytes + " байт) — разбор остановлен");
            }
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}