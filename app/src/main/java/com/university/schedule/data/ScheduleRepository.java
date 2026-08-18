package com.university.schedule.data;
import android.content.Context;
import android.util.Log;
import com.university.schedule.data.db.HolidayDao;
import com.university.schedule.data.db.ScheduleDao;
import com.university.schedule.data.db.TransferDao;
import com.university.schedule.data.db.UserDao;
import com.university.schedule.logic.HolidayChecker;
import com.university.schedule.logic.ScheduleFilter;
import com.university.schedule.logic.SemesterManager;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.Holiday;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.SemesterInfo;
import com.university.schedule.model.TransferItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.PrefsManager;
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ScheduleRepository {
    private static final String TAG = "ScheduleRepository";
    private static volatile ScheduleRepository instance;

    public interface LoadCallback {
        void onSuccess();
        void onError(String message);
        void onProgress(String message);
    }

    private final Context context;
    private final ScheduleDao scheduleDao;
    private final UserDao userDao;
    private final HolidayDao holidayDao;
    private final TransferDao transferDao;
    private final NetworkClient networkClient;
    private final ExcelParser excelParser;
    private final TransferParser transferParser;
    private final PrefsManager prefsManager;
    private HolidayChecker holidayChecker;
    private WeekCalculator weekCalculator;
    private ScheduleFilter scheduleFilter;

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
        this.networkClient = new NetworkClient(this.context);
        this.excelParser = new ExcelParser();
        this.transferParser = new TransferParser();
        this.prefsManager = new PrefsManager(this.context);
        initLogic();
    }

    private void initLogic() {
        List<Holiday> extra = holidayDao.getAll();
        this.holidayChecker = new HolidayChecker(extra);
        LocalDate start = prefsManager.getSemesterStart();
        LocalDate today = DateUtils.todayMoscow();
        if (start == null || !isWithinSemester(start, today)) {
            LocalDate rolled = SemesterManager.getDefaultSemesterStart();
            if (rolled != null && (start == null || isWithinSemester(rolled, today) || !isWithinSemester(start, today))) {
                start = rolled;
                prefsManager.saveSemesterStart(start);
                Log.d(TAG, "Начало семестра актуализировано: " + start);
            }
        }
        this.weekCalculator = new WeekCalculator(new SemesterInfo(start));
        this.scheduleFilter = new ScheduleFilter(holidayChecker);
    }

    private static boolean isWithinSemester(LocalDate start, LocalDate date) {
        if (start == null || date == null) return false;
        LocalDate end = start.plusWeeks(SemesterInfo.TOTAL_WEEKS);
        return !date.isBefore(start) && date.isBefore(end);
    }

    public void refreshLogic() {
        initLogic();
    }

    public void loadScheduleFromNetwork(LoadCallback cb) {
        try {
            cb.onProgress("Загрузка файла расписания...");
            File file = networkClient.downloadSync(Constants.SCHEDULE_URL);
            cb.onProgress("Обработка файла...");
            parseAndSave(file, cb);
            prefsManager.saveLastUpdated(DateUtils.nowIsoDateTime());
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
            cb.onSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка загрузки", e);
            cb.onError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    public void parseAndSave(File xlsx, LoadCallback cb) throws Exception {
        List<ScheduleItem> all = new ArrayList<>();
        try (FileInputStream is = new FileInputStream(xlsx)) {
            all.addAll(excelParser.parseGroups(is));
        }
        try (FileInputStream is = new FileInputStream(xlsx)) {
            all.addAll(excelParser.parseTeachers(is));
        } catch (Exception e) {
            Log.w(TAG, "Лист преподавателей не распознан: " + e.getMessage());
        }
        cb.onProgress("Сохранение в БД (" + all.size() + " записей)...");
        scheduleDao.replaceAll(all);
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
        String wt = (week % 2 == 0) ? Constants.WEEK_TYPE_EVEN : Constants.WEEK_TYPE_ODD;
        List<ScheduleItem> items = scheduleDao.getScheduleForGroup(group, wt);
        LocalDate monday = weekCalculator.getMondayOfWeek(week);
        LocalDate saturday = monday.plusDays(5);
        List<TransferItem> transfers = transferDao.getForDateRange(monday.toString(), saturday.toString());
        return scheduleFilter.buildWeekSchedule(items, week, wt, monday, transfers, group, true);
    }

    public WeekSchedule getWeekScheduleForTeacher(String teacher, int week) {
        String wt = (week % 2 == 0) ? Constants.WEEK_TYPE_EVEN : Constants.WEEK_TYPE_ODD;
        List<ScheduleItem> items = scheduleDao.getScheduleForTeacher(teacher, wt);
        LocalDate monday = weekCalculator.getMondayOfWeek(week);
        LocalDate saturday = monday.plusDays(5);
        List<TransferItem> transfers = transferDao.getForDateRange(monday.toString(), saturday.toString());
        return scheduleFilter.buildWeekSchedule(items, week, wt, monday, transfers, teacher, false);
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
        List<ScheduleItem> items = group
                ? scheduleDao.getScheduleForGroup(name, wt)
                : scheduleDao.getScheduleForTeacher(name, wt);
        List<TransferItem> transfers = transferDao.getForDate(today.toString());
        return scheduleFilter.buildDaySchedule(items, dow, week, wt, today, transfers, name, group);
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
        prefsManager.saveSemesterStart(start);
        userDao.updateSemesterStart(start);
        refreshLogic();
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

    public String getLastUpdated() {
        return prefsManager.getLastUpdated();
    }

    public String getTransfersLastUpdated() {
        return prefsManager.getTransfersLastUpdated();
    }

    public void clearAllData() {
        scheduleDao.clearAll();
        userDao.clear();
        holidayDao.clear();
        transferDao.clearAll();
        networkClient.clearCache();
        prefsManager.clearAll();
        refreshLogic();
    }

    private static final LoadCallback NOOP = new LoadCallback() {
        @Override public void onSuccess() { }
        @Override public void onError(String message) { }
        @Override public void onProgress(String message) { }
    };
}
