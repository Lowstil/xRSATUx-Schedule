package com.university.schedule.data;
import android.content.Context;
import android.util.Log;
import com.university.schedule.data.db.CustomEventDao;
import com.university.schedule.data.db.HolidayDao;
import com.university.schedule.data.db.NoteDao;
import com.university.schedule.model.CustomEvent;
import com.university.schedule.model.LessonNote;
import com.university.schedule.data.db.ScheduleDao;
import com.university.schedule.data.db.TransferDao;
import com.university.schedule.data.db.UserDao;
import com.university.schedule.logic.HolidayChecker;
import com.university.schedule.logic.ScheduleFilter;
import com.university.schedule.logic.SemesterManager;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.Holiday;
import com.university.schedule.model.NextLesson;
import com.university.schedule.model.ScheduleDiff;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.SemesterInfo;
import com.university.schedule.model.TransferItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.util.AppError;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.PrefsManager;
import com.university.schedule.util.ExcludedSubjectsManager;
import com.university.schedule.util.SkippedLessonsManager;
import com.university.schedule.widget.TodayScheduleWidgetProvider;
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public class ScheduleRepository {
private static final String TAG = "ScheduleRepository";
private static volatile ScheduleRepository instance;
public interface LoadCallback {
void onSuccess();
void onError(AppError error);
void onProgress(String message);
}
private final Context context;
private final ScheduleDao scheduleDao;
private final UserDao userDao;
private final HolidayDao holidayDao;
private final TransferDao transferDao;
private final NoteDao noteDao;
private final CustomEventDao customEventDao;
private final NetworkClient networkClient;
private final ExcelParser excelParser;
private final TransferParser transferParser;
private final ExcludedSubjectsManager excludedSubjectsManager;
private final SkippedLessonsManager skippedLessonsManager;
private final PrefsManager prefsManager;
private volatile HolidayChecker holidayChecker;
private volatile WeekCalculator weekCalculator;
private volatile ScheduleFilter scheduleFilter;
public static ScheduleRepository getInstance(Context context) {
if (instance == null) {
synchronized (ScheduleRepository.class) {
if (instance == null) instance = new ScheduleRepository(context);
}
}
return instance;
}
private ScheduleRepository(Context context) {
this.context = context.getApplicationContext();
this.scheduleDao = new ScheduleDao(this.context);
this.userDao = new UserDao(this.context);
this.holidayDao = new HolidayDao(this.context);
this.transferDao = new TransferDao(this.context);
this.noteDao = new NoteDao(this.context);
this.customEventDao = new CustomEventDao(this.context);
this.networkClient = new NetworkClient(this.context);
this.excelParser = new ExcelParser();
this.transferParser = new TransferParser();
this.prefsManager = new PrefsManager(this.context);
this.excludedSubjectsManager = new ExcludedSubjectsManager(this.context);
this.skippedLessonsManager = new SkippedLessonsManager(this.context);
initLogic();
}
private void initLogic() {
List<Holiday> extra = holidayDao.getAll();
this.holidayChecker = new HolidayChecker(extra);
String activeSem = getActiveSemesterId();
LocalDate start = prefsManager.getSemesterStart(activeSem);
LocalDate today = DateUtils.todayMoscow();
if (start == null) {
start = SemesterManager.getDefaultStartForSemester(activeSem, today);
prefsManager.saveSemesterStart(activeSem, start);
Log.d(TAG, "Начало семестра " + activeSem + " инициализировано: " + start);
}
if (scheduleDao.hasDataForSemester(SemesterManager.SEMESTER_FALL)
&& !prefsManager.isSemesterPublished(SemesterManager.SEMESTER_FALL)) {
prefsManager.setSemesterPublished(SemesterManager.SEMESTER_FALL, true);
}
this.weekCalculator = new WeekCalculator(new SemesterInfo(start));
this.scheduleFilter = new ScheduleFilter(holidayChecker);
}

public String getActiveSemesterId() {
return prefsManager.getActiveSemester();
}

public void setActiveSemesterId(String semesterId) {
String sem = (SemesterManager.SEMESTER_SPRING.equals(semesterId))
? SemesterManager.SEMESTER_SPRING
: SemesterManager.SEMESTER_FALL;
prefsManager.setActiveSemester(sem);
LocalDate start = prefsManager.getSemesterStart(sem);
if (start == null) {
start = SemesterManager.getDefaultStartForSemester(sem, DateUtils.todayMoscow());
prefsManager.saveSemesterStart(sem, start);
}
this.weekCalculator = new WeekCalculator(new SemesterInfo(start));
userDao.updateSemesterStart(start);
com.university.schedule.widget.WidgetUpdateHelper.updateAllWidgets(context);
}

public boolean isSemesterPublished(String semesterId) {
if (semesterId == null) return false;
return prefsManager.isSemesterPublished(semesterId) || scheduleDao.hasDataForSemester(semesterId);
}

public WeekCalculator getWeekCalculator(String semesterId) {
String sem = (SemesterManager.SEMESTER_SPRING.equals(semesterId))
? SemesterManager.SEMESTER_SPRING
: SemesterManager.SEMESTER_FALL;
LocalDate start = prefsManager.getSemesterStart(sem);
if (start == null) {
start = SemesterManager.getDefaultStartForSemester(sem, DateUtils.todayMoscow());
prefsManager.saveSemesterStart(sem, start);
}
return new WeekCalculator(new SemesterInfo(start));
}
private static boolean isWithinSemester(LocalDate start, LocalDate date) {
if (start == null || date == null) return false;
LocalDate end = start.plusWeeks(SemesterInfo.TOTAL_WEEKS);
return !date.isBefore(start) && date.isBefore(end);
}
public void refreshLogic() {
initLogic();
com.university.schedule.widget.WidgetUpdateHelper.updateAllWidgets(context);
}
public void loadScheduleFromNetwork(LoadCallback cb) {
try {
cb.onProgress("Загрузка файла расписания...");
File file = networkClient.downloadScheduleSync();
String newHash = networkClient.getLastDownloadedHash();
cb.onProgress("Обработка файла...");
parseAndSave(file, cb, newHash);
if (!networkClient.lastFetchFromAssets()) prefsManager.saveLastUpdated(DateUtils.nowIsoDateTime());
userDao.touchLastUpdated();
try {
cb.onProgress("Загрузка переносов занятий...");
File transfersFile = networkClient.downloadTransfersSync();
cb.onProgress("Обработка переносов...");
parseAndSaveTransfers(transfersFile, cb);
prefsManager.saveTransfersLastUpdated(DateUtils.nowIsoDateTime());
} catch (Exception e) {
Log.w(TAG, "Не удалось обновить переносы (расписание всё равно обновлено): " + e.getMessage());
}
TodayScheduleWidgetProvider.updateAllWidgets(context);
com.university.schedule.notification.ReminderScheduler.rescheduleAll(context);
cb.onSuccess();
} catch (Exception e) {
Log.e(TAG, "Ошибка загрузки", e);
cb.onError(AppError.from(e));
}
}
public boolean shouldAutoRefresh() {
LocalDateTime lastSuccess = DateUtils.parseIsoDateTime(prefsManager.getLastUpdated());
LocalDateTime lastAttempt = DateUtils.parseIsoDateTime(prefsManager.getLastAutoRefreshAttempt());
LocalDateTime mostRecent = latest(lastSuccess, lastAttempt);
return DateUtils.isAutoRefreshDue(mostRecent, LocalDateTime.now());
}
private static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
if (a == null) return b;
if (b == null) return a;
return a.isAfter(b) ? a : b;
}
public void maybeAutoRefresh(LoadCallback cb) {
if (!shouldAutoRefresh()) {
cb.onSuccess();
return;
}
prefsManager.saveLastAutoRefreshAttempt(DateUtils.nowIsoDateTime());
loadScheduleFromNetwork(cb);
}
public void parseAndSave(File xlsx, LoadCallback cb) throws Exception {
parseAndSave(xlsx, cb, networkClient.getLastDownloadedHash());
}

public void parseAndSave(File xlsx, LoadCallback cb, String newHash) throws Exception {
List<ScheduleItem> all = new ArrayList<>();
try (FileInputStream is = new FileInputStream(xlsx)) {
all.addAll(excelParser.parseGroups(is));
}
try (FileInputStream is = new FileInputStream(xlsx)) {
all.addAll(excelParser.parseTeachers(is));
} catch (Exception e) {
Log.w(TAG, "Лист преподавателей не распознан: " + e.getMessage());
}
if (all.isEmpty()) {
throw new IllegalStateException("Файл расписания распознан пустым (0 записей) — существующие данные не затёрты");
}
LocalDate today = DateUtils.todayMoscow();
String windowSem = SemesterManager.getCurrentWindowSemester(today);
String targetSem = resolveTargetSemesterForDownload(windowSem, today, newHash);
boolean wasTargetPublished = isSemesterPublished(targetSem);

cb.onProgress("Сохранение в БД (" + all.size() + " записей)...");
List<ScheduleItem> oldSnapshot = null;
String selType = prefsManager.getSelectionType();
String selName = prefsManager.getSelectionName();
boolean trackDiff = prefsManager.hasSelection()
&& scheduleDao.hasDataForSemester(targetSem)
&& selName != null && !selName.isEmpty();
if (trackDiff) {
oldSnapshot = loadSelectionSnapshot(selType, selName, targetSem);
}
scheduleDao.replaceForSemester(targetSem, all);
if (newHash != null) {
prefsManager.saveSemesterHash(targetSem, newHash);
}
prefsManager.setSemesterPublished(targetSem, true);
if (targetSem.equals(windowSem) && !wasTargetPublished) {
setActiveSemesterId(targetSem);
}
if (trackDiff && oldSnapshot != null && !oldSnapshot.isEmpty()) {
List<ScheduleItem> newSnapshot = loadSelectionSnapshot(selType, selName, targetSem);
ScheduleDiff diff = ScheduleDiff.compute(selType, selName, oldSnapshot, newSnapshot);
if (diff.hasChanges()) {
String json = diff.toJson();
if (json != null) {
prefsManager.saveScheduleDiffJson(json);
}
}
}
}

private String resolveTargetSemesterForDownload(String windowSem, LocalDate today, String newHash) {
if (isSemesterPublished(windowSem)) {
return windowSem;
}
String otherSem = SemesterManager.SEMESTER_SPRING.equals(windowSem)
? SemesterManager.SEMESTER_FALL
: SemesterManager.SEMESTER_SPRING;
String otherHash = prefsManager.getSemesterHash(otherSem);
if (otherHash != null) {
if (newHash != null && !newHash.equals(otherHash)) {
return windowSem;
}
// На rsatu.ru всё ещё лежит файл предыдущего семестра — не загрязняем новый семестр
return otherSem;
}
// Хэша предыдущего семестра ещё нет (первая установка или апгрейд с v5)
if (SemesterManager.SEMESTER_SPRING.equals(windowSem)) {
return (today.getMonthValue() >= 2) ? SemesterManager.SEMESTER_SPRING : SemesterManager.SEMESTER_FALL;
} else {
return (today.getMonthValue() >= 9 || today.getMonthValue() == 1)
? SemesterManager.SEMESTER_FALL
: SemesterManager.SEMESTER_SPRING;
}
}

private List<ScheduleItem> loadSelectionSnapshot(String selType, String selName, String semesterId) {
List<ScheduleItem> snapshot = new ArrayList<>();
boolean isGroup = "group".equals(selType);
if (isGroup) {
snapshot.addAll(scheduleDao.getScheduleForGroup(selName, Constants.WEEK_TYPE_ODD, semesterId));
snapshot.addAll(scheduleDao.getScheduleForGroup(selName, Constants.WEEK_TYPE_EVEN, semesterId));
} else {
snapshot.addAll(scheduleDao.getScheduleForTeacher(selName, Constants.WEEK_TYPE_ODD, semesterId));
snapshot.addAll(scheduleDao.getScheduleForTeacher(selName, Constants.WEEK_TYPE_EVEN, semesterId));
}
return snapshot;
}
public void parseAndSaveTransfers(File xlsx, LoadCallback cb) throws Exception {
List<TransferItem> items;
try (FileInputStream is = new FileInputStream(xlsx)) {
items = transferParser.parse(is);
}
cb.onProgress("Сохранение переносов в БД (" + items.size() + " записей)...");
transferDao.replaceAll(items);
}
public boolean loadFromCache() {
File f = networkClient.getCachedFile();
if (f == null) return false;
try {
parseAndSave(f, NOOP);
} catch (Exception e) {
Log.e(TAG, "Ошибка чтения кэша расписания", e);
return false;
}
File tf = networkClient.getCachedTransfersFile();
if (tf != null) {
try {
parseAndSaveTransfers(tf, NOOP);
} catch (Exception e) {
Log.w(TAG, "Ошибка чтения кэша переносов (не критично): " + e.getMessage());
}
}
return true;
}
public WeekSchedule getWeekScheduleForGroup(String group, int week) {
return getWeekScheduleForGroup(group, week, getActiveSemesterId());
}

public WeekSchedule getWeekScheduleForGroup(String group, int week, String semesterId) {
String wt = (week % 2 == 0) ? Constants.WEEK_TYPE_EVEN : Constants.WEEK_TYPE_ODD;
WeekCalculator calc = getWeekCalculator(semesterId);
LocalDate monday = calc != null ? calc.getMondayOfWeek(week) : null;
if (monday == null) {
return new WeekSchedule(week, week % 2 == 0);
}
List<ScheduleItem> items = scheduleDao.getScheduleForGroup(group, wt, semesterId);
LocalDate saturday = monday.plusDays(5);
List<TransferItem> transfers = transferDao.getForDateRange(monday.toString(), saturday.toString());
WeekSchedule ws = scheduleFilter.buildWeekSchedule(items, week, wt, monday, transfers, group, true);
mergeCustomEventsIntoWeek(ws, group, true, wt);
return ws;
}

public WeekSchedule getWeekScheduleForTeacher(String teacher, int week) {
return getWeekScheduleForTeacher(teacher, week, getActiveSemesterId());
}

public WeekSchedule getWeekScheduleForTeacher(String teacher, int week, String semesterId) {
String wt = (week % 2 == 0) ? Constants.WEEK_TYPE_EVEN : Constants.WEEK_TYPE_ODD;
WeekCalculator calc = getWeekCalculator(semesterId);
LocalDate monday = calc != null ? calc.getMondayOfWeek(week) : null;
if (monday == null) {
return new WeekSchedule(week, week % 2 == 0);
}
List<ScheduleItem> items = scheduleDao.getScheduleForTeacher(teacher, wt, semesterId);
LocalDate saturday = monday.plusDays(5);
List<TransferItem> transfers = transferDao.getForDateRange(monday.toString(), saturday.toString());
WeekSchedule ws = scheduleFilter.buildWeekSchedule(items, week, wt, monday, transfers, teacher, false);
mergeCustomEventsIntoWeek(ws, teacher, false, wt);
return ws;
}
public DaySchedule getTodayScheduleForGroup(String group) {
return todayFor(group, true);
}
public DaySchedule getTodayScheduleForTeacher(String teacher) {
return todayFor(teacher, false);
}
private DaySchedule todayFor(String name, boolean group) {
// ВАЖНО: раньше здесь стояло LocalDate.now(ZoneId.of("Europe/Moscow")) —
// жёстко зашитая московская зона в обход DateUtils.todayMoscow(),
// который в одном из патчей был переведён на часы устройства
// (ZoneId.systemDefault()) специально для того, чтобы подсветка
// "сейчас/следующая" совпадала с реальным временем на телефоне при
// тестировании через ручную смену даты. Из-за этого расхождения
// initLogic()/ScheduleClock видели одно "сегодня" (по часам
// устройства), а todayFor() — другое (всегда по Москве), и в
// зависимости от того, какой путь кода отработал первым, поведение
// могло отличаться. Теперь оба места используют один и тот же
// источник правды.
LocalDate today = DateUtils.todayMoscow();
int week = weekCalculator.getWeekNumber(today);
int dow = DateUtils.toScheduleDayOfWeek(today);
if (week < 0) {
DaySchedule d = new DaySchedule(dow, today);
d.setDayOff(true);
d.setHolidayName("Вне учебного семестра");
return d;
}
if (dow == 0) {
DaySchedule d = new DaySchedule(0, today);
d.setDayOff(true);
return d;
}
String wt = weekCalculator.getWeekTypeForDate(today);
String activeSem = getActiveSemesterId();
List<ScheduleItem> items = group
? scheduleDao.getScheduleForGroup(name, wt, activeSem)
: scheduleDao.getScheduleForTeacher(name, wt, activeSem);
List<TransferItem> transfers = transferDao.getForDate(today.toString());
DaySchedule day = scheduleFilter.buildDaySchedule(items, dow, week, wt, today, transfers, name, group);
mergeCustomEventsIntoDay(day, customEventDao.getForTarget(name, group), wt);
return day;
}
/**
Задача: «Следующая пара» по реальному расписанию.
Ищет ближайшее будущее занятие для группы/преподавателя, начиная с
текущего момента, просматривая до maxDays дней вперёд.
В отличие от ScheduleClock (который считает по сетке звонков и всегда
подставляет 1-ю пару дня), здесь ищем по реальному расписанию:
Если сегодня суббота без пар, завтра воскресенье, а в понедельник
первая пара не 1-я, а 2-я — найдём именно 2-ю пару понедельника,
а не несуществующую 1-ю.
Пропускаем: воскресенья, праздники, дни вне семестра, отменённые пары.
Вызывать из фонового потока (читает БД).
*/
public NextLesson findNextLesson(String name, boolean group, LocalDate fromDate,
LocalTime afterTime, int maxDays) {
if (name == null || fromDate == null) return null;
DateTimeFormatter tf = DateTimeFormatter.ofPattern("H:mm");
List<CustomEvent> customEvents = customEventDao.getForTarget(name, group);
// Начинаем поиск с текущего дня, но если сегодня выходной или нет пар,
// то ищем в последующих днях
for (int k = 0; k < maxDays; k++) {
LocalDate date = fromDate.plusDays(k);
int dow = DateUtils.toScheduleDayOfWeek(date);
if (dow == 0) continue; // воскресенье — всегда выходной
int week = weekCalculator.getWeekNumber(date);
if (week < 1) continue; // вне семестра
String wt = weekCalculator.getWeekTypeForDate(date);
String activeSem = getActiveSemesterId();
List<ScheduleItem> items = group
? scheduleDao.getScheduleForGroup(name, wt, activeSem)
: scheduleDao.getScheduleForTeacher(name, wt, activeSem);
List<TransferItem> transfers = transferDao.getForDate(date.toString());
DaySchedule day = scheduleFilter.buildDaySchedule(items, dow, week, wt, date, transfers, name, group);
if (day.isDayOff()) continue; // праздничный выходной
mergeCustomEventsIntoDay(day, customEvents, wt);
// Проверяем наличие занятий: учитываем как обычные пары, так и добавленные переносом
List<ScheduleItem> lessons = day.getLessons();
if (lessons == null || lessons.isEmpty()) continue;
if (lessons.size() > 1) {
Collections.sort(lessons, (a, b) -> Integer.compare(a.getLessonNumber(), b.getLessonNumber()));
}
String[][] times = Constants.getLessonTimes(dow);
for (ScheduleItem it : lessons) {
if (it.isCancelled()) continue; // отменено переносом
if (group && excludedSubjectsManager.isExcluded(name, it.getSubjectName())) continue;
if (skippedLessonsManager.isSkipped(date.toString(), it.getLessonNumber(), it.getSubjectName())) continue;
int n = it.getLessonNumber();
if (n < 1 || n > times.length) continue;
LocalTime start = LocalTime.parse(times[n - 1][0], tf);
// Для сегодняшнего дня (k == 0): пропускаем пары, которые уже начались
// Для будущих дней (k > 0): берём все пары без проверки времени
if (k == 0 && afterTime != null && !afterTime.isBefore(start)) continue;
NextLesson nl = new NextLesson();
nl.setDate(date);
nl.setDayOfWeek(dow);
nl.setLessonNumber(n);
nl.setTimeStart(times[n - 1][0]);
nl.setTimeEnd(times[n - 1][1]);
nl.setSubjectName(it.getSubjectName());
nl.setTeacherName(it.getTeacherName());
nl.setRoom(it.getRoom());
nl.setLessonType(it.getLessonType());
return nl;
}
}
return null;
}
public List<String> getAllGroups() {
return scheduleDao.extractDistinctGroups();
}
public List<String> getAllTeachers() {
return scheduleDao.extractDistinctTeachers();
}
public boolean hasScheduleData() {
return !scheduleDao.isEmpty();
}
public void saveUserSelection(String type, String name) {
LocalDate start = prefsManager.getSemesterStart();
if (start == null) start = SemesterManager.getDefaultSemesterStart();
userDao.saveSelection(type, name, start);
prefsManager.saveSelection(type, name);
prefsManager.saveSemesterStart(start);
}
public void saveSelection(String type, String name) {
saveUserSelection(type, name);
}

public UserDao.UserSettings getUserSettings() {
return userDao.getSettings();
}
public boolean hasUserSelection() {
return prefsManager.hasSelection();
}
public String getSelectionType() {
return prefsManager.getSelectionType();
}
public String getSelectionName() {
return prefsManager.getSelectionName();
}
public void updateSemesterStart(LocalDate start) {
updateSemesterStart(getActiveSemesterId(), start);
}

public void updateSemesterStart(String semesterId, LocalDate start) {
String sem = (SemesterManager.SEMESTER_SPRING.equals(semesterId))
? SemesterManager.SEMESTER_SPRING
: SemesterManager.SEMESTER_FALL;
if (start != null) {
prefsManager.saveSemesterStart(sem, start);
}
if (sem.equals(getActiveSemesterId())) {
userDao.updateSemesterStart(start);
refreshLogic();
}
}
public LocalDate getSemesterStart() {
return prefsManager.getSemesterStart();
}
public SemesterInfo getSemesterInfo() {
LocalDate s = prefsManager.getSemesterStart();
return s != null ? new SemesterInfo(s) : null;
}
public WeekCalculator getWeekCalculator() {
return weekCalculator;
}
public HolidayChecker getHolidayChecker() {
return holidayChecker;
}
public String getLastUpdated() {
return prefsManager.getLastUpdated();
}
public String getTransfersLastUpdated() {
return prefsManager.getTransfersLastUpdated();
}
public java.util.Map<Integer, LessonNote> getNotesForDate(String date) {
return noteDao.getNotesForDate(date);
}
public void saveNote(String date, int lessonNum, String subject, String text, boolean completed) {
noteDao.saveOrUpdate(date, lessonNum, subject, text, completed);
}
public void saveNote(String date, int lessonNum, String subject, String text, String imagePath, boolean completed) {
noteDao.saveOrUpdate(date, lessonNum, subject, text, imagePath, completed);
}
public List<String> getDistinctSubjectsForGroup(String groupName) {
return scheduleDao.extractDistinctSubjectsForGroup(groupName);
}
public List<ScheduleItem> getScheduleForGroupAndWeekType(String group, String weekType) {
return scheduleDao.getScheduleForGroup(group, weekType);
}
public List<TransferItem> getTransfersForDate(String date) {
return transferDao.getForDate(date);
}
public List<TransferItem> getTransfersForDateRange(String fromIso, String toIso) {
return transferDao.getForDateRange(fromIso, toIso);
}
public List<CustomEvent> getCustomEvents() {
return customEventDao.getAll();
}
public long addCustomEvent(CustomEvent e) {
if (e == null) return -1;
return customEventDao.insert(e);
}
public void deleteCustomEvent(long id) {
customEventDao.delete(id);
}
private void mergeCustomEventsIntoWeek(WeekSchedule ws, String name, boolean isGroup, String weekType) {
if (ws == null || ws.getDays() == null || name == null) return;
List<CustomEvent> events = customEventDao.getForTarget(name, isGroup);
if (events == null || events.isEmpty()) return;
for (DaySchedule day : ws.getDays()) {
mergeCustomEventsIntoDay(day, events, weekType);
}
}
private void mergeCustomEventsIntoDay(DaySchedule day, List<CustomEvent> events, String weekType) {
if (day == null || events == null || events.isEmpty()) return;
List<ScheduleItem> lessons = day.getLessons();
if (lessons == null) {
lessons = new ArrayList<>();
day.setLessons(lessons);
}
boolean added = false;
for (CustomEvent e : events) {
if (e.matchesDate(day.getDate(), day.getDayOfWeek(), weekType)) {
lessons.add(e.toScheduleItem(day.getDayOfWeek()));
added = true;
}
}
if (added && lessons.size() > 1) {
Collections.sort(lessons, (a, b) -> Integer.compare(a.getLessonNumber(), b.getLessonNumber()));
}
}
public void deleteNote(String date, int lessonNum) {
noteDao.delete(date, lessonNum);
}
public void clearAllData() {
scheduleDao.clearAll();
userDao.clear();
holidayDao.clear();
transferDao.clearAll();
customEventDao.clearAll();
networkClient.clearCache();
prefsManager.clearAll();
refreshLogic();
}
private static final LoadCallback NOOP = new LoadCallback() {
@Override public void onSuccess() { }
@Override public void onError(AppError error) { }
@Override public void onProgress(String message) { }
};
}