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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер .xlsx расписания, написанный под конкретный формат файла университета.
 *
 * Структура листа (группы и преподаватели одинаковы):
 *   - где-то в первых строках идёт строка-шапка, у которой в колонке 1 стоит "Группа";
 *     в этой строке начиная с колонки 3 идут имена групп (лист групп) или ФИО
 *     преподавателей (лист преподавателей);
 *   - ниже — строки данных: col0 = тип недели ("Нечётная неделя"/"Чётная неделя"),
 *     col1 = день недели, col2 = "N пара", col3+ = ячейки с занятиями.
 *
 * Группа/преподаватель для строки берутся ИЗ ЗАГОЛОВКА колонки (source + имя),
 * а из текста ячейки извлекаются только предмет, тип, преподаватель, аудитория
 * и диапазон недель. Поэтому выбор группы показывает ровно её строки, даже если
 * в ячейке перечислены несколько групп совместной пары.
 *
 * Ячейка может содержать несколько занятий через перенос строки — парсим построчно.
 */
public class ExcelParser {

    private static final String TAG = "ExcelParser";

    /** Диапазон/список недель в скобках: (Недели 1-16), (Неделя 15), (Недели 1, 18). */
    private static final Pattern WEEK_PATTERN =
            Pattern.compile("\\(Недели?\\s*([^)]+)\\)", Pattern.CASE_INSENSITIVE);

    /** Тип занятия как отдельный токен. Ищем все вхождения, берём последнее. */
    private static final Pattern TYPE_PATTERN =
            Pattern.compile("(?<![\\p{L}0-9])(оЛ|оП|ЛР|Экзамен|Л|П)(?![\\p{L}0-9])");

    /** Токен-группа в начале текста (ИВБ-24, ИВБк-24-1, ИВБ(КР)-25, CТБ-25 ...). */
    private static final Pattern GROUP_TOKEN =
            Pattern.compile("^[A-Za-zА-ЯЁ][A-Za-zА-ЯЁ0-9()]*-\\d+[A-Za-zА-ЯЁ0-9().-]*$");

    /** Аудитория: Г-519, 3-201, 1-101г, 1-100б, Г-200-1, Г-321а и т.п. */
    private static final Pattern ROOM_TOKEN =
            Pattern.compile("^[А-ЯЁA-Za-z]?\\d+-\\d+([а-яА-ЯёЁ])?$|^[А-ЯЁA-Za-z]+-\\d+(-\\d+)*([а-яА-ЯёЁ])?$");

    /** Инициалы: "П." или "П.В.". */
    private static final Pattern INITIALS =
            Pattern.compile("^[А-ЯЁA-Z]\\.([А-ЯЁA-Z]\\.)?$");

    /** Слово имени/фамилии: заглавная + строчные (>=2 букв). */
    private static final Pattern NAME_WORD =
            Pattern.compile("^[А-ЯЁA-Z][а-яёa-z]{1,}$");

    /** Многословные маркеры аудиторий. */
    private static final Set<String> ROOM_TWO_WORDS = new HashSet<>(
            Arrays.asList("Большой спортзал", "Точка кипения"));

    /** Однословные маркеры аудиторий (нижний регистр для сравнения). */
    private static final Set<String> ROOM_ONE_WORD = new HashSet<>(
            Arrays.asList("онлайн", "предприятия"));

    private static final String[] DAY_NAMES = {
            "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"
    };

    /** Парсит лист групп (source = "group"). */
    public List<ScheduleItem> parseGroups(InputStream in) throws Exception {
        return parseSheet(in, Constants.SHEET_INDEX_GROUPS, ScheduleDao.SOURCE_GROUP);
    }

    /** Парсит лист преподавателей (source = "teacher"). */
    public List<ScheduleItem> parseTeachers(InputStream in) throws Exception {
        return parseSheet(in, Constants.SHEET_INDEX_TEACHERS, ScheduleDao.SOURCE_TEACHER);
    }

    private List<ScheduleItem> parseSheet(InputStream in, int sheetIndex, String source) throws Exception {
        List<ScheduleItem> result = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(in)) {
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
                    + ", данных строк ~ " + (sheet.getLastRowNum() - headerRowIdx));

            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String weekType = parseWeekType(trim(getStr(row.getCell(0))));
                if (weekType == null) continue;
                int day = parseDayOfWeek(trim(getStr(row.getCell(1))));
                if (day < 0) continue;
                int lesson = parseLessonNumber(trim(getStr(row.getCell(2))));
                if (lesson < 0) continue;

                int last = row.getLastCellNum();
                for (int c = 3; c < last && c < headerLen; c++) {
                    String colName = header[c];
                    if (colName == null || colName.isEmpty()) continue;
                    String cell = getStr(row.getCell(c));
                    if (cell == null) continue;
                    for (String line : cell.split("\\r?\\n")) {
                        ScheduleItem item = parseLine(line, weekType, day, lesson, colName, source);
                        if (item != null) result.add(item);
                    }
                }
            }
        }
        Log.d(TAG, "Распознано (" + source + "): " + result.size());
        return result;
    }

    /** Ищем строку-шапку по маркеру "Группа" в колонке 1. */
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

    /**
     * Разбор одной строки ячейки. Алгоритм:
     *  1) вырезаем (Недели ...);
     *  2) ищем последнее вхождение токена-типа;
     *     - если тип найден: subject = всё до типа; хвост после типа = преподаватель+аудитория;
     *     - если тип не найден: хвост = вся строка, из него эвристикой откусываем
     *       аудиторию и преподавателя, остаток = subject;
     *  3) чистим ведущие токены-группы и маркер "ИР" из subject.
     */
    private ScheduleItem parseLine(String raw, String weekType, int day, int lesson,
                                   String colName, String source) {
        if (raw == null) return null;
        String line = raw.trim();
        if (line.isEmpty()) return null;

        // 1) недели
        String weekSpec = "";
        Matcher wm = WEEK_PATTERN.matcher(line);
        if (wm.find()) {
            weekSpec = wm.group(1).replaceAll("\\s+", "");
            line = (line.substring(0, wm.start()) + " " + line.substring(wm.end())).replaceAll("\\s+", " ").trim();
        }

        // 2) тип занятия (последнее вхождение)
        Matcher tm = TYPE_PATTERN.matcher(line);
        int typeStart = -1, typeEnd = -1;
        String type = "";
        while (tm.find()) {
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

    /** Откусывает аудиторию с конца списка (1 или 2 токена). */
    private String peelRoom(List<String> tt) {
        if (tt.isEmpty()) return "";
        if (tt.size() >= 2) {
            String two = tt.get(tt.size() - 2) + " " + tt.get(tt.size() - 1);
            if (ROOM_TWO_WORDS.contains(two)) {
                tt.remove(tt.size() - 1);
                tt.remove(tt.size() - 1);
                return two;
            }
        }
        String last = tt.get(tt.size() - 1);
        if (ROOM_ONE_WORD.contains(last.toLowerCase()) || ROOM_TOKEN.matcher(last).matches()) {
            tt.remove(tt.size() - 1);
            return last;
        }
        return "";
    }

    /** Откусывает ФИО с конца списка (инициалы / слова имени). */
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

    /** Убирает ведущие токены-группы и маркер "ИР". */
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
}