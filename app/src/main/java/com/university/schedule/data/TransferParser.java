package com.university.schedule.data;

import android.util.Log;

import com.university.schedule.model.TransferItem;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Парсер файла "Журнал переносов" (отдельный .xlsx, отдельный от основного
 * расписания). Содержит два листа:
 *
 * "Переносы" — столбцы фиксированы по ПОЗИЦИИ (не по названию, т.к. в файле
 * встречаются опечатки в заголовках вроде "пропущеного"):
 *   0=ФИО преподавателя, 1=Дисциплина, 2=Группа/поток,
 *   3=Дата пропущенного занятия, 4=Пара пропущенного занятия,
 *   5=Дата проведения занятия, 6=Пара проведения занятия,
 *   7=Заменяющий преподаватель, 8=Аудитория.
 * Одна строка листа порождает:
 *   - TransferItem(REMOVAL) на старую дату/пару, если она указана
 *     (бывают строки без неё — устоявшийся перенос без явной старой пары);
 *   - TransferItem(ADDITION) на новую дату/пару, если она указана
 *     (бывают строки-отмены без новой пары — занятие просто снимается).
 *   REMOVAL и ADDITION одной строки связаны linkId, чтобы UI мог показать
 *   их как одну логическую замену ("перенесено на ...").
 *
 * "Аудитории" — столбцы фиксированы по позиции:
 *   0=Дата проведения, 1=Время проведения, 2=ФИО преподавателя, 3=Кафедра,
 *   4=Дисциплина, 5=Группа/поток, 6=Аудитория, 7=Примечания.
 * Каждая строка — TransferItem(ROOM_CHANGE): день/пара из обычного
 * расписания сохраняются, но аудитория меняется.
 */
public class TransferParser {

    private static final String TAG = "TransferParser";
    private static final long MAX_XLSX_SIZE_BYTES = 50L * 1024 * 1024; // 50 МБ
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    public List<TransferItem> parse(InputStream in) throws Exception {
        List<TransferItem> result = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new BoundedInputStream(in, MAX_XLSX_SIZE_BYTES))) {
            Sheet transfers = findSheet(wb, "Перенос");
            if (transfers != null) {
                result.addAll(parseTransfersSheet(transfers));
            } else {
                Log.w(TAG, "Лист \"Переносы\" не найден");
            }
            Sheet rooms = findSheet(wb, "Аудитор");
            if (rooms != null) {
                result.addAll(parseRoomsSheet(rooms));
            } else {
                Log.w(TAG, "Лист \"Аудитории\" не найден (необязательный)");
            }
        }
        Log.d(TAG, "Распознано переносов/замен: " + result.size());
        return result;
    }

    /** Ищет лист, чьё имя содержит фрагмент (без учёта регистра) — устойчиво к вариациям названия. */
    private Sheet findSheet(Workbook wb, String nameContains) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String name = wb.getSheetName(i);
            if (name != null && name.toLowerCase().contains(nameContains.toLowerCase())) {
                return wb.getSheetAt(i);
            }
        }
        return null;
    }

    private List<TransferItem> parseTransfersSheet(Sheet sheet) {
        List<TransferItem> out = new ArrayList<>();
        int headerRow = findHeaderRow(sheet, 0, "ФИО преподавателя");
        if (headerRow < 0) {
            Log.w(TAG, "Не найдена шапка на листе \"Переносы\"");
            return out;
        }
        long nextLinkId = 1;
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String teacher = trim(getStr(row.getCell(0)));
            String subject = trim(getStr(row.getCell(1)));
            String group = trim(getStr(row.getCell(2)));
            if (teacher.isEmpty() && subject.isEmpty() && group.isEmpty()) continue; // пустая строка

            LocalDate removalDate = getDate(row.getCell(3));
            int removalLesson = parseLessonNumber(getStr(row.getCell(4)));
            LocalDate additionDate = getDate(row.getCell(5));
            int additionLesson = parseLessonNumber(getStr(row.getCell(6)));
            String substitute = trim(getStr(row.getCell(7)));
            String room = trim(getStr(row.getCell(8)));

            long linkId = nextLinkId++;

            if (removalDate != null && removalLesson > 0) {
                TransferItem removal = new TransferItem();
                removal.setKind(TransferItem.KIND_REMOVAL);
                removal.setDate(removalDate.toString());
                removal.setLessonNumber(removalLesson);
                removal.setGroupName(group);
                removal.setSubjectName(subject);
                removal.setTeacherName(teacher);
                removal.setSubstituteTeacher(substitute.isEmpty() ? null : substitute);
                removal.setLinkId(linkId);
                out.add(removal);
            }
            if (additionDate != null && additionLesson > 0) {
                TransferItem addition = new TransferItem();
                addition.setKind(TransferItem.KIND_ADDITION);
                addition.setDate(additionDate.toString());
                addition.setLessonNumber(additionLesson);
                addition.setGroupName(group);
                addition.setSubjectName(subject);
                addition.setTeacherName(teacher);
                addition.setSubstituteTeacher(substitute.isEmpty() ? null : substitute);
                addition.setRoom(room.isEmpty() ? null : room);
                addition.setLinkId(linkId);
                if (removalDate != null) {
                    addition.setNote("Перенесено с " + formatRu(removalDate) + ", " + removalLesson + " пара");
                }
                out.add(addition);
            }
        }
        return out;
    }

    private List<TransferItem> parseRoomsSheet(Sheet sheet) {
        List<TransferItem> out = new ArrayList<>();
        int headerRow = findHeaderRow(sheet, 0, "Дата проведения");
        if (headerRow < 0) {
            Log.w(TAG, "Не найдена шапка на листе \"Аудитории\"");
            return out;
        }
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            LocalDate date = getDate(row.getCell(0));
            int lesson = parseLessonNumber(getStr(row.getCell(1)));
            String teacher = trim(getStr(row.getCell(2)));
            String subject = trim(getStr(row.getCell(4)));
            String group = trim(getStr(row.getCell(5)));
            String room = trim(getStr(row.getCell(6)));
            String note = trim(getStr(row.getCell(7)));
            if (date == null || lesson <= 0) continue;
            if (teacher.isEmpty() && subject.isEmpty() && group.isEmpty()) continue;

            TransferItem item = new TransferItem();
            item.setKind(TransferItem.KIND_ROOM_CHANGE);
            item.setDate(date.toString());
            item.setLessonNumber(lesson);
            item.setGroupName(group);
            item.setSubjectName(subject);
            item.setTeacherName(teacher);
            item.setRoom(room.isEmpty() ? null : room);
            item.setNote(note.isEmpty() ? null : note);
            out.add(item);
        }
        return out;
    }

    private int findHeaderRow(Sheet sheet, int col, String expectedContains) {
        int limit = Math.min(4, sheet.getLastRowNum() + 1);
        for (int r = 0; r < limit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String v = trim(getStr(row.getCell(col)));
            if (v.toLowerCase().contains(expectedContains.toLowerCase())) return r;
        }
        return -1;
    }

    /** Лениво извлекает номер пары ("1 пара", "3пара ", "5  пара", "–" -> -1). */
    private int parseLessonNumber(String s) {
        if (s == null) return -1;
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1;
        try {
            int n = Integer.parseInt(digits.substring(0, 1));
            return (n >= 1 && n <= 7) ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private LocalDate getDate(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date d = cell.getDateCellValue();
                return d.toInstant().atZone(MOSCOW).toLocalDate();
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue();
                return s == null || s.trim().isEmpty() ? null : LocalDate.parse(s.trim());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private String formatRu(LocalDate d) {
        return String.format("%02d.%02d.%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String getStr(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue();
            if (cell.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) return null; // даты читаются через getDate()
                return String.valueOf((int) cell.getNumericCellValue());
            }
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

    /** Тот же предохранитель от аномально больших файлов, что и в ExcelParser. */
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
                throw new IOException("Файл переносов превышает допустимый размер (" + maxBytes + " байт)");
            }
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
