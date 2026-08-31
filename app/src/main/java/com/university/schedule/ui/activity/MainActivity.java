package com.university.schedule.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.ui.adapter.DayScheduleAdapter;
import com.university.schedule.util.AppError;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.ScheduleClock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private Toolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private TextView tvWeekInfo, tvEmpty, tvSelectionName, tvSelectionType, tvTodayDate, tvMonthLabel, tvStaleBannerText;
    private View toolbarTitleArea;
    private View btnPrev, btnNext;
    private View staleBanner;
    private DayScheduleAdapter adapter;
    private ScheduleRepository repo;
    private WeekCalculator calc;
    private WeekSchedule currentWeek;
    private String selType, selName;
    private int weekNum, dayIndex;
    private boolean suppressTabCallback;
    private LocalDate selectedDayDate;
    // Дата, при которой была построена текущая неделя. Нужна, чтобы отследить
    // смену системной даты "на лету" (пользователь поменял дату в настройках
    // телефона и вернулся в приложение) и пересчитать неделю/вкладки/часы.
    private LocalDate builtForDate;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            clockHandler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        repo = ScheduleRepository.getInstance(this);
        calc = repo.getWeekCalculator();
        selType = repo.getSelectionType();
        selName = repo.getSelectionName();
        initWeekForDate(DateUtils.todayMoscow());

        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tabLayout);
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        tvWeekInfo = findViewById(R.id.tvWeekInfo);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvSelectionName = findViewById(R.id.tvSelectionName);
        tvSelectionType = findViewById(R.id.tvSelectionType);
        tvTodayDate = findViewById(R.id.tvTodayDate);
        tvMonthLabel = findViewById(R.id.tvMonthLabel);
        toolbarTitleArea = findViewById(R.id.toolbarTitleArea);
        btnPrev = findViewById(R.id.btnPrevWeek);
        btnNext = findViewById(R.id.btnNextWeek);
        staleBanner = findViewById(R.id.staleBanner);
        tvStaleBannerText = findViewById(R.id.tvStaleBannerText);
        staleBanner.setOnClickListener(v -> { swipeRefresh.setRefreshing(true); refresh(); });

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        tvSelectionName.setText(selName != null ? selName : "—");
        tvSelectionType.setText(GroupOrTeacher.TYPE_GROUP.equals(selType) ? "Группа" : "Преподаватель");
        tvTodayDate.setText(DateUtils.formatDisplayDateShort(DateUtils.todayMoscow()));

        toolbarTitleArea.setOnClickListener(v -> {
            Intent i = new Intent(this, SelectionActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        adapter = new DayScheduleAdapter(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        buildDayTabs();
        suppressTabCallback = true;
        tabLayout.selectTab(tabLayout.getTabAt(dayIndex));
        suppressTabCallback = false;
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                if (suppressTabCallback) return;
                dayIndex = t.getPosition();
                displayDay();
            }
            @Override public void onTabUnselected(TabLayout.Tab t) { }
            @Override public void onTabReselected(TabLayout.Tab t) { }
        });
        swipeRefresh.setOnRefreshListener(this::refresh);
        btnPrev.setOnClickListener(v -> { if (weekNum > 1) { weekNum--; loadWeek(); } });
        btnNext.setOnClickListener(v -> { if (weekNum < 18) { weekNum++; loadWeek(); } });
        updateLastUpdatedBanner();
        loadWeek();
    }

    /** Выставляет weekNum/dayIndex под дату и запоминает, для какой даты строили. */
    private void initWeekForDate(LocalDate today) {
        weekNum = calc.getCurrentWeekNumber();
        if (weekNum < 1) weekNum = 1;
        int dow = DateUtils.toScheduleDayOfWeek(today);
        dayIndex = (dow >= 1 && dow <= 6) ? dow - 1 : 0;
        builtForDate = today;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Если системную дату поменяли, пока приложение было в фоне/закрыто —
        // пересобираем неделю под новое "сегодня", иначе открытая неделя будет
        // показывать чужие даты и подсветка "сейчас/следующая" не совпадёт.
        LocalDate today = DateUtils.todayMoscow();
        if (builtForDate == null || !builtForDate.equals(today)) {
            calc = repo.getWeekCalculator();
            initWeekForDate(today);
            tvTodayDate.setText(DateUtils.formatDisplayDateShort(today));
            suppressTabCallback = true;
            tabLayout.selectTab(tabLayout.getTabAt(dayIndex));
            suppressTabCallback = false;
            loadWeek();
        }
        clockHandler.removeCallbacks(clockTick);
        clockHandler.post(clockTick);
        updateLastUpdatedBanner();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockTick);
    }

    private void buildDayTabs() {
        tabLayout.removeAllTabs();
        for (int i = 0; i < 6; i++) {
            TabLayout.Tab tab = tabLayout.newTab();
            tab.setCustomView(R.layout.item_day_tab);
            TextView tvName = tab.getCustomView().findViewById(R.id.tvTabDayName);
            tvName.setText(Constants.DAY_NAMES_SHORT[i]);
            tabLayout.addTab(tab);
        }
    }

    private void updateDayTabsForWeek() {
        LocalDate today = DateUtils.todayMoscow();
        List<DaySchedule> days = (currentWeek != null) ? currentWeek.getDays() : null;
        String monthName = null;

        for (int i = 0; i < 6; i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab == null || tab.getCustomView() == null) continue;
            View custom = tab.getCustomView();
            TextView tvNumber = custom.findViewById(R.id.tvTabDayNumber);
            TextView tvBadge = custom.findViewById(R.id.tvTabBadge);

            LocalDate date = (days != null && i < days.size()) ? days.get(i).getDate() : null;
            if (date == null) {
                tvNumber.setText("");
                tvBadge.setVisibility(View.GONE);
                continue;
            }
            if (monthName == null) monthName = DateUtils.monthNameRu(date);
            tvNumber.setText(String.valueOf(date.getDayOfMonth()));

            String label = DateUtils.relativeDayLabel(date, today);
            if (label == null) {
                tvBadge.setVisibility(View.GONE);
            } else {
                tvBadge.setVisibility(View.VISIBLE);
                tvBadge.setText(label);
                int bgColor, textColor;
                if (label.equals("Сегодня")) {
                    bgColor = R.color.badge_today_bg; textColor = R.color.badge_today_text;
                } else if (label.equals("Завтра")) {
                    bgColor = R.color.badge_tomorrow_bg; textColor = R.color.badge_tomorrow_text;
                } else {
                    bgColor = R.color.badge_future_bg; textColor = R.color.badge_future_text;
                }
                tvBadge.getBackground().mutate().setTint(ContextCompat.getColor(this, bgColor));
                tvBadge.setTextColor(ContextCompat.getColor(this, textColor));
            }
        }
        tvMonthLabel.setText(monthName != null ? monthName : "");
    }

    private void loadWeek() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        String label = (weekNum % 2 == 0) ? "Чётная" : "Нечётная";
        LocalDate mon = calc.getMondayOfWeek(weekNum);
        LocalDate sat = calc.getSaturdayOfWeek(weekNum);
        String range = (mon != null && sat != null) ? DateUtils.formatDisplayDate(mon) + " – " + DateUtils.formatDisplayDate(sat) : "";
        tvWeekInfo.setText("Неделя " + weekNum + " (" + label + ")  " + range);
        btnPrev.setAlpha(weekNum > 1 ? 1f : 0.3f);
        btnNext.setAlpha(weekNum < 18 ? 1f : 0.3f);
        ex.execute(() -> {
            WeekSchedule ws = GroupOrTeacher.TYPE_GROUP.equals(selType)
                    ? repo.getWeekScheduleForGroup(selName, weekNum)
                    : repo.getWeekScheduleForTeacher(selName, weekNum);
            runOnUiThread(() -> {
                currentWeek = ws;
                progressBar.setVisibility(View.GONE);
                updateDayTabsForWeek();
                displayDay();
            });
        });
    }

    private void displayDay() {
        if (currentWeek == null || currentWeek.getDays().isEmpty()) {
            adapter.updateData(new ArrayList<>()); tvEmpty.setVisibility(View.VISIBLE); tvEmpty.setText("Нет данных");
            selectedDayDate = null;
            updateClock();
            return;
        }
        DaySchedule day = currentWeek.getDays().get(dayIndex);
        selectedDayDate = day.getDate();
        if (day.isDayOff()) { adapter.updateData(new ArrayList<>()); tvEmpty.setVisibility(View.VISIBLE); tvEmpty.setText(day.getHolidayName() != null ? day.getHolidayName() : "Выходной день"); }
        else if (!day.hasLessons()) { adapter.updateData(new ArrayList<>()); tvEmpty.setVisibility(View.VISIBLE); tvEmpty.setText("Нет занятий"); }
        else { tvEmpty.setVisibility(View.GONE); adapter.updateData(day.getLessons()); }
        updateClock();
    }

    private void updateClock() {
        ScheduleClock.State st = ScheduleClock.compute();
        adapter.applyClock(selectedDayDate, st.current, st.next);
    }

    /**
     * Задача 4: показывает заметный баннер на главном экране, если
     * расписание не обновлялось дольше DateUtils.STALE_DATA_THRESHOLD_DAYS
     * дней — раньше эта информация была видна только в Настройках.
     */
    private void updateLastUpdatedBanner() {
        String iso = repo.getLastUpdated();
        LocalDateTime updated = DateUtils.parseIsoDateTime(iso);
        String msg = DateUtils.staleDataMessage(updated, LocalDateTime.now());
        if (msg == null) {
            staleBanner.setVisibility(View.GONE);
        } else {
            tvStaleBannerText.setText(msg);
            staleBanner.setVisibility(View.VISIBLE);
        }
    }

    private void refresh() {
        ex.execute(() -> repo.loadScheduleFromNetwork(new ScheduleRepository.LoadCallback() {
            @Override public void onSuccess() { runOnUiThread(() -> { swipeRefresh.setRefreshing(false); repo.refreshLogic(); calc = repo.getWeekCalculator(); loadWeek(); updateLastUpdatedBanner(); }); }
            @Override public void onError(AppError error) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    Snackbar.make(swipeRefresh, error.userMessage, Snackbar.LENGTH_LONG)
                            .setAction("Повторить", v -> refresh())
                            .show();
                });
            }
            @Override public void onProgress(String m) { }
        }));
    }

    @Override public boolean onCreateOptionsMenu(Menu m) { getMenuInflater().inflate(R.menu.menu_main, m); return true; }
    @Override public boolean onOptionsItemSelected(MenuItem it) {
        if (it.getItemId() == R.id.action_settings) { startActivity(new Intent(this, SettingsActivity.class)); return true; }
        if (it.getItemId() == R.id.action_refresh) { swipeRefresh.setRefreshing(true); refresh(); return true; }
        return super.onOptionsItemSelected(it);
    }
    @Override protected void onDestroy() { super.onDestroy(); clockHandler.removeCallbacks(clockTick); ex.shutdown(); }
}
