package com.university.schedule.ui.activity;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.SemesterManager;
import com.university.schedule.notification.ReminderScheduler;
import com.university.schedule.ui.dialog.BellScheduleDialog;
import com.university.schedule.util.AppError;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.EdgeToEdgeCompat;
import com.university.schedule.util.PrefsManager;
import com.university.schedule.util.ThemeManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {
    private static final int RC_NOTIFICATION_PERMISSION = 3001;

    private ScheduleRepository repo;
    private PrefsManager prefs;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private TextView tvSel, tvSem, tvUpd, tvTransfersUpd;
    private MaterialButton btnRefresh;
    private MaterialButtonToggleGroup toggleTheme;
    private SwitchMaterial switchLessonReminder, switchMorningDigest;
    private MaterialButtonToggleGroup toggleReminderMinutes;
    private boolean suppressThemeCallback;
    private boolean suppressReminderCallback;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_settings);
        EdgeToEdgeCompat.applyEdgeToEdgeInsets(this);
        repo = ScheduleRepository.getInstance(this);
        prefs = new PrefsManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.action_settings);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSel = findViewById(R.id.tvCurrentSelection);
        tvSem = findViewById(R.id.tvSemesterInfo);
        tvUpd = findViewById(R.id.tvLastUpdated);
        tvTransfersUpd = findViewById(R.id.tvTransfersLastUpdated);
        btnRefresh = findViewById(R.id.btnRefreshSchedule);
        toggleTheme = findViewById(R.id.toggleTheme);

        switchLessonReminder = findViewById(R.id.switchLessonReminder);
        toggleReminderMinutes = findViewById(R.id.toggleReminderMinutes);
        switchMorningDigest = findViewById(R.id.switchMorningDigest);

        findViewById(R.id.btnChangeSelection).setOnClickListener(v -> {
            Intent i = new Intent(this, SelectionActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        findViewById(R.id.btnBellSchedule).setOnClickListener(v -> BellScheduleDialog.show(this));
        findViewById(R.id.btnChangeSemester).setOnClickListener(v -> pickDate());
        btnRefresh.setOnClickListener(v -> doRefresh());

        findViewById(R.id.btnClearData).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Очистить данные")
                .setMessage("Удалить расписание и настройки?")
                .setPositiveButton("Удалить", (d, w) -> ex.execute(() -> {
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

        setupThemeToggle();
        setupNotificationSettings();
        updateInfo();
    }

    private void setupNotificationSettings() {
        suppressReminderCallback = true;
        switchLessonReminder.setChecked(prefs.isRemindersEnabled());
        int mins = prefs.getReminderMinutesBefore();
        if (mins == 10) toggleReminderMinutes.check(R.id.btnReminder10);
        else if (mins == 30) toggleReminderMinutes.check(R.id.btnReminder30);
        else toggleReminderMinutes.check(R.id.btnReminder15);

        switchMorningDigest.setChecked(prefs.isMorningDigestEnabled());
        suppressReminderCallback = false;

        switchLessonReminder.setOnCheckedChangeListener((btn, isChecked) -> {
            if (suppressReminderCallback) return;
            prefs.setRemindersEnabled(isChecked);
            if (isChecked) checkNotificationPermission();
            ReminderScheduler.scheduleNextReminder(this);
        });

        toggleReminderMinutes.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppressReminderCallback) return;
            int m = 15;
            if (checkedId == R.id.btnReminder10) m = 10;
            else if (checkedId == R.id.btnReminder30) m = 30;
            prefs.setReminderMinutesBefore(m);
            ReminderScheduler.scheduleNextReminder(this);
        });

        switchMorningDigest.setOnCheckedChangeListener((btn, isChecked) -> {
            if (suppressReminderCallback) return;
            prefs.setMorningDigestEnabled(isChecked);
            if (isChecked) checkNotificationPermission();
            ReminderScheduler.scheduleMorningDigest(this);
        });
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        RC_NOTIFICATION_PERMISSION
                );
            }
        }
    }

    private void setupThemeToggle() {
        int saved = ThemeManager.getSavedMode(this);
        int checkedId;
        if (saved == ThemeManager.MODE_LIGHT) checkedId = R.id.btnThemeLight;
        else if (saved == ThemeManager.MODE_DARK) checkedId = R.id.btnThemeDark;
        else checkedId = R.id.btnThemeSystem;

        suppressThemeCallback = true;
        toggleTheme.check(checkedId);
        suppressThemeCallback = false;

        toggleTheme.addOnButtonCheckedListener((group, checkedButtonId, isChecked) -> {
            if (!isChecked || suppressThemeCallback) return;
            int mode;
            if (checkedButtonId == R.id.btnThemeLight) mode = ThemeManager.MODE_LIGHT;
            else if (checkedButtonId == R.id.btnThemeDark) mode = ThemeManager.MODE_DARK;
            else mode = ThemeManager.MODE_SYSTEM;
            ThemeManager.setMode(this, mode);
        });
    }

    private void updateInfo() {
        String t = repo.getSelectionType();
        String n = repo.getSelectionName();
        tvSel.setText(("group".equals(t) ? "Группа" : "Преподаватель") + ": " + (n != null ? n : "—"));
        tvSem.setText(SemesterManager.describeSemester(repo.getSemesterInfo()));
        LocalDateTime ldt = DateUtils.parseIsoDateTime(repo.getLastUpdated());
        tvUpd.setText(ldt != null ? "Расписание обновлено: " + ldt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) : "Расписание ещё не обновлялось");
        LocalDateTime tldt = DateUtils.parseIsoDateTime(repo.getTransfersLastUpdated());
        tvTransfersUpd.setText(tldt != null ? "Переносы обновлены: " + tldt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) : "Переносы ещё не загружались");
    }

    private void pickDate() {
        LocalDate cur = repo.getSemesterStart();
        if (cur == null) cur = SemesterManager.getDefaultSemesterStart();
        new DatePickerDialog(this, R.style.ThemeOverlay_UniSchedule_DatePicker, (v, y, m, d) -> {
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
        btnRefresh.setText(R.string.settings_refresh);
        ex.execute(() -> repo.loadScheduleFromNetwork(new ScheduleRepository.LoadCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnRefresh.setEnabled(true);
                    btnRefresh.setText(R.string.settings_refresh);
                    updateInfo();
                    Toast.makeText(SettingsActivity.this, "Расписание обновлено", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(AppError error) {
                runOnUiThread(() -> {
                    btnRefresh.setEnabled(true);
                    btnRefresh.setText(R.string.settings_refresh);
                    Toast.makeText(SettingsActivity.this, error.userMessage, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onProgress(String m) { }
        }));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ex.shutdown();
    }
}
