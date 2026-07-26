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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.ui.adapter.DayScheduleAdapter;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.ScheduleClock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private TextView tvWeekInfo, tvEmpty;
    private View btnPrev, btnNext;

    private DayScheduleAdapter adapter;
    private ScheduleRepository repo;
    private WeekCalculator calc;
    private WeekSchedule currentWeek;
    private LocalDate selectedDayDate;

    private String selType, selName;
    private int weekNum, dayIndex;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            clockHandler.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        repo = ScheduleRepository.getInstance(this);
        calc = repo.getWeekCalculator();
        selType = repo.getSelectionType();
        selName = repo.getSelectionName();

        weekNum = calc.getCurrentWeekNumber();
        if (weekNum < 1) weekNum = 1;
        int dow = DateUtils.toScheduleDayOfWeek(DateUtils.todayMoscow());
        dayIndex = (dow >= 1 && dow <= 6) ? dow - 1 : 0;

        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tabLayout);
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        tvWeekInfo = findViewById(R.id.tvWeekInfo);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnPrev = findViewById(R.id.btnPrevWeek);
        btnNext = findViewById(R.id.btnNextWeek);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(selName);
            getSupportActionBar().setSubtitle(
                    GroupOrTeacher.TYPE_GROUP.equals(selType) ? "Группа" : "Преподаватель");
        }

        adapter = new DayScheduleAdapter(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        for (int i = 0; i < 6; i++) tabLayout.addTab(tabLayout.newTab().setText(Constants.DAY_NAMES_SHORT[i]));
        tabLayout.selectTab(tabLayout.getTabAt(dayIndex));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) { dayIndex = t.getPosition(); displayDay(); }
            @Override public void onTabUnselected(TabLayout.Tab t) { }
            @Override public void onTabReselected(TabLayout.Tab t) { }
        });

        swipeRefresh.setOnRefreshListener(this::refresh);
        btnPrev.setOnClickListener(v -> { if (weekNum > 1) { weekNum--; loadWeek(); } });
        btnNext.setOnClickListener(v -> { if (weekNum < 18) { weekNum++; loadWeek(); } });

        loadWeek();
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockHandler.removeCallbacks(clockTick);
        clockHandler.post(clockTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockTick);
    }

    private void loadWeek() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        String label = (weekNum % 2 == 0) ? "Чётная" : "Нечётная";
        LocalDate mon = calc.getMondayOfWeek(weekNum);
        LocalDate sat = calc.getSaturdayOfWeek(weekNum);
        String range = (mon != null && sat != null)
                ? DateUtils.formatDisplayDate(mon) + " – " + DateUtils.formatDisplayDate(sat) : "";
        tvWeekInfo.setText("Неделя " + weekNum + " (" + label + ")  " + range);
        btnPrev.setAlpha(weekNum > 1 ? 1f : 0.3f);
        btnNext.setAlpha(weekNum < 18 ? 1f : 0.3f);

        executor.execute(() -> {
            WeekSchedule ws = GroupOrTeacher.TYPE_GROUP.equals(selType)
                    ? repo.getWeekScheduleForGroup(selName, weekNum)
                    : repo.getWeekScheduleForTeacher(selName, weekNum);
            runOnUiThread(() -> { currentWeek = ws; progressBar.setVisibility(View.GONE); displayDay(); });
        });
    }

    private void displayDay() {
        if (currentWeek == null || currentWeek.getDays().isEmpty()) {
            adapter.updateData(new ArrayList<>());
            selectedDayDate = null;
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Нет данных");
            return;
        }
        DaySchedule day = currentWeek.getDays().get(dayIndex);
        selectedDayDate = day.getDate();

        if (day.isDayOff()) {
            adapter.updateData(new ArrayList<>());
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(day.getHolidayName() != null ? day.getHolidayName() : "Выходной день");
        } else if (!day.hasLessons()) {
            adapter.updateData(new ArrayList<>());
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Нет занятий");
        } else {
            tvEmpty.setVisibility(View.GONE);
            adapter.updateData(day.getLessons());
        }
        updateClock();
    }

    /** Пересчитать подсветку текущей/следующей пары для выбранного таба. */
    private void updateClock() {
        ScheduleClock.State st = ScheduleClock.compute();
        adapter.applyClock(selectedDayDate, st.current, st.next);
    }

    private void refresh() {
        executor.execute(() -> repo.loadScheduleFromNetwork(new ScheduleRepository.LoadCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> { swipeRefresh.setRefreshing(false); repo.refreshLogic(); calc = repo.getWeekCalculator(); loadWeek(); });
            }
            @Override public void onError(String m) {
                runOnUiThread(() -> swipeRefresh.setRefreshing(false));
            }
            @Override public void onProgress(String m) { }
        }));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_refresh) {
            swipeRefresh.setRefreshing(true);
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockTick);
        executor.shutdown();
    }
}