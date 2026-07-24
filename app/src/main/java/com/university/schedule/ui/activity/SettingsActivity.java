package com.university.schedule.ui.activity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.SemesterManager;
import com.university.schedule.util.DateUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private ScheduleRepository repo;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView tvSel, tvSem, tvUpd;
    private MaterialButton btnRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        repo = ScheduleRepository.getInstance(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Настройки");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSel = findViewById(R.id.tvCurrentSelection);
        tvSem = findViewById(R.id.tvSemesterInfo);
        tvUpd = findViewById(R.id.tvLastUpdated);
        btnRefresh = findViewById(R.id.btnRefreshSchedule);

        findViewById(R.id.btnChangeSelection).setOnClickListener(v -> {
            Intent i = new Intent(this, SelectionActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
        findViewById(R.id.btnChangeSemester).setOnClickListener(v -> pickDate());
        btnRefresh.setOnClickListener(v -> doRefresh());
        findViewById(R.id.btnClearData).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Очистить данные")
                .setMessage("Удалить расписание и настройки?")
                .setPositiveButton("Удалить", (d, w) -> executor.execute(() -> {
                    repo.clearAllData();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Данные очищены", Toast.LENGTH_SHORT).show();
                        Intent i = new Intent(this, SelectionActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    });
                }))
                .setNegativeButton("Отмена", null)
                .show());

        updateInfo();
    }

    private void updateInfo() {
        String type = repo.getSelectionType();
        String name = repo.getSelectionName();
        tvSel.setText((GroupOrTeacher_TYPE_GROUP(type) ? "Группа" : "Преподаватель") + ": " + (name != null ? name : "—"));
        tvSem.setText(SemesterManager.describeSemester(repo.getSemesterInfo()));
        String lu = repo.getLastUpdated();
        LocalDateTime ldt = DateUtils.parseIsoDateTime(lu);
        tvUpd.setText(ldt != null
                ? "Обновлено: " + ldt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                : "Ещё не обновлялось");
    }

    private boolean GroupOrTeacher_TYPE_GROUP(String t) {
        return "group".equals(t);
    }

    private void pickDate() {
        LocalDate cur = repo.getSemesterStart();
        if (cur == null) cur = SemesterManager.getDefaultSemesterStart();
        new DatePickerDialog(this, (v, y, m, d) -> {
            LocalDate sel = LocalDate.of(y, m + 1, d);
            LocalDate mon = DateUtils.mondayOfWeek(sel);
            if (mon.isAfter(sel)) mon = mon.minusWeeks(1);
            repo.updateSemesterStart(mon);
            updateInfo();
            Toast.makeText(this, "Начало семестра: " + DateUtils.formatDisplayDate(mon), Toast.LENGTH_SHORT).show();
        }, cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth()).show();
    }

    private void doRefresh() {
        btnRefresh.setEnabled(false);
        btnRefresh.setText("Обновление...");
        executor.execute(() -> repo.loadScheduleFromNetwork(new ScheduleRepository.LoadCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> { btnRefresh.setEnabled(true); btnRefresh.setText(R.string.settings_refresh); updateInfo(); Toast.makeText(SettingsActivity.this, "Расписание обновлено", Toast.LENGTH_SHORT).show(); });
            }
            @Override public void onError(String m) {
                runOnUiThread(() -> { btnRefresh.setEnabled(true); btnRefresh.setText(R.string.settings_refresh); Toast.makeText(SettingsActivity.this, "Ошибка: " + m, Toast.LENGTH_LONG).show(); });
            }
            @Override public void onProgress(String m) {}
        }));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}