package com.university.schedule.ui.activity;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.SemesterManager;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.notification.LiveLessonService;
import com.university.schedule.notification.ReminderScheduler;
import com.university.schedule.ui.dialog.BellScheduleDialog;
import com.university.schedule.ui.dialog.RoomTeacherFinderDialog;
import com.university.schedule.ui.dialog.ScheduleDiffDialog;
import com.university.schedule.ui.glass.AmbientBackdropView;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.AppError;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.EdgeToEdgeCompat;
import com.university.schedule.util.ExcludedSubjectsManager;
import com.university.schedule.util.PrefsManager;
import com.university.schedule.util.ScheduleExporter;
import com.university.schedule.util.ThemeManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {
    private static final int RC_NOTIFICATION_PERMISSION = 3001;

    private ScheduleRepository repo;
    private PrefsManager prefs;
    private ExcludedSubjectsManager excludedManager;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private TextView tvSel, tvSelectedSemester, tvSem, tvUpd, tvTransfersUpd;
    private MaterialButton btnRefresh;
    private MaterialButtonToggleGroup toggleTheme;
    private SwitchMaterial switchLessonReminder, switchMorningDigest, switchLiveLessonNotif, switchReducedTransparency;
    private MaterialButtonToggleGroup toggleReminderMinutes;
    private View cardExcludedSubjects;
    private AmbientBackdropView ambientBackdrop;
    private TextView tvExcludedSubjectsSummary;
    private MaterialButton btnSelectExcludedSubjects;
    private boolean suppressThemeCallback;
    private boolean suppressReminderCallback;
    private boolean suppressGlassCallback;
    private final List<Dialog> activeDialogs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        EdgeToEdgeCompat.enableEdgeToEdge(this);
        setContentView(R.layout.activity_settings);
        EdgeToEdgeCompat.applySystemBarInsets(findViewById(R.id.settingsContentContainer), true, false);
        EdgeToEdgeCompat.applySystemBarInsets(findViewById(R.id.settingsScrollView), false, true);
        repo = ScheduleRepository.getInstance(this);
        prefs = new PrefsManager(this);
        excludedManager = new ExcludedSubjectsManager(this);

        ambientBackdrop = findViewById(R.id.ambientBackdrop);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.action_settings);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSel = findViewById(R.id.tvCurrentSelection);
        tvSelectedSemester = findViewById(R.id.tvSelectedSemester);
        tvSem = findViewById(R.id.tvSemesterInfo);
        tvUpd = findViewById(R.id.tvLastUpdated);
        tvTransfersUpd = findViewById(R.id.tvTransfersLastUpdated);
        btnRefresh = findViewById(R.id.btnRefreshSchedule);
        toggleTheme = findViewById(R.id.toggleTheme);

        switchLiveLessonNotif = findViewById(R.id.switchLiveLessonNotif);
        switchLessonReminder = findViewById(R.id.switchLessonReminder);
        toggleReminderMinutes = findViewById(R.id.toggleReminderMinutes);
        switchMorningDigest = findViewById(R.id.switchMorningDigest);
        switchReducedTransparency = findViewById(R.id.switchReducedTransparency);

        cardExcludedSubjects = findViewById(R.id.cardExcludedSubjects);
        tvExcludedSubjectsSummary = findViewById(R.id.tvExcludedSubjectsSummary);
        btnSelectExcludedSubjects = findViewById(R.id.btnSelectExcludedSubjects);

        findViewById(R.id.btnChangeSelection).setOnClickListener(v -> {
            Intent i = new Intent(this, SelectionActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        findViewById(R.id.btnRoomTeacherFinder).setOnClickListener(v -> RoomTeacherFinderDialog.show(this));
        findViewById(R.id.btnBellSchedule).setOnClickListener(v -> BellScheduleDialog.show(this));
        findViewById(R.id.btnShowDiff).setOnClickListener(v -> ScheduleDiffDialog.show(this));
        findViewById(R.id.btnShareSchedule).setOnClickListener(v -> ScheduleExporter.showExportDialog(this));
        findViewById(R.id.btnChangeSemester).setOnClickListener(v -> pickDate());
        btnRefresh.setOnClickListener(v -> doRefresh());

        findViewById(R.id.btnClearData).setOnClickListener(v -> showClearDataDialog());

        setupThemeToggle();
        setupGlassSettings();
        setupNotificationSettings();
        setupExcludedSubjects();
        applyGlassCards();
        updateInfo();
    }

    private void applyGlassCards() {
        int[] cardIds = {
                R.id.cardSelection,
                R.id.cardTools,
                R.id.cardShare,
                R.id.cardExcludedSubjects,
                R.id.cardNotifications,
                R.id.cardSemester,
                R.id.cardAppearance,
                R.id.cardData
        };
        for (int id : cardIds) {
            View card = findViewById(id);
            if (card != null) {
                GlassSurfaceHelper.applyGlassCard(card, 22f, false);
            }
        }
    }

    private void setupGlassSettings() {
        suppressGlassCallback = true;
        switchReducedTransparency.setChecked(prefs.isReducedTransparency());
        suppressGlassCallback = false;

        switchReducedTransparency.setOnCheckedChangeListener((btn, isChecked) -> {
            if (suppressGlassCallback) return;
            prefs.setReducedTransparency(isChecked);
            applyGlassCards();
            if (ambientBackdrop != null) {
                ambientBackdrop.reloadPreferences();
            }
        });
    }

    private void setupNotificationSettings() {
        suppressReminderCallback = true;
        switchLiveLessonNotif.setChecked(prefs.isLiveLessonNotificationEnabled());
        switchLessonReminder.setChecked(prefs.isRemindersEnabled());
        int mins = prefs.getReminderMinutesBefore();
        if (mins == 10) toggleReminderMinutes.check(R.id.btnReminder10);
        else if (mins == 30) toggleReminderMinutes.check(R.id.btnReminder30);
        else toggleReminderMinutes.check(R.id.btnReminder15);

        switchMorningDigest.setChecked(prefs.isMorningDigestEnabled());
        suppressReminderCallback = false;

        switchLiveLessonNotif.setOnCheckedChangeListener((btn, isChecked) -> {
            if (suppressReminderCallback) return;
            prefs.setLiveLessonNotificationEnabled(isChecked);
            if (isChecked) checkNotificationPermission();
            LiveLessonService.syncNow(this);
        });

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
            if (ambientBackdrop != null) {
                ambientBackdrop.reloadPreferences();
            }
            applyGlassCards();
        });
    }

    private void updateInfo() {
        String t = repo.getSelectionType();
        String n = repo.getSelectionName();
        tvSel.setText(("group".equals(t) ? "Группа" : "Преподаватель") + ": " + (n != null ? n : "—"));
        String activeSemId = repo.getActiveSemesterId();
        if (activeSemId == null) activeSemId = SemesterManager.SEMESTER_FALL;
        boolean isSpring = SemesterManager.SEMESTER_SPRING.equals(activeSemId);
        if (tvSelectedSemester != null) {
            tvSelectedSemester.setText("Выбранный семестр: " + (isSpring ? "Весна" : "Осень"));
        }
        WeekCalculator semCalc = repo.getWeekCalculator(activeSemId);
        LocalDate semStart = semCalc != null ? semCalc.getMondayOfWeek(1) : null;
        if (semStart == null) {
            semStart = SemesterManager.getDefaultStartForSemester(activeSemId, DateUtils.todayMoscow());
        }
        tvSem.setText(SemesterManager.describeSemester(activeSemId, semStart));
        LocalDateTime ldt = DateUtils.parseIsoDateTime(repo.getLastUpdated());
        tvUpd.setText(ldt != null ? "Расписание обновлено: " + ldt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) : "Расписание ещё не обновлялось");
        LocalDateTime tldt = DateUtils.parseIsoDateTime(repo.getTransfersLastUpdated());
        tvTransfersUpd.setText(tldt != null ? "Переносы обновлены: " + tldt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) : "Переносы ещё не загружались");
    }

    private void pickDate() {
        if (isFinishing() || isDestroyed()) return;
        final String activeSemId = repo.getActiveSemesterId() != null
                ? repo.getActiveSemesterId()
                : SemesterManager.SEMESTER_FALL;
        WeekCalculator semCalc = repo.getWeekCalculator(activeSemId);
        LocalDate cur = semCalc != null ? semCalc.getMondayOfWeek(1) : null;
        if (cur == null) {
            cur = SemesterManager.getDefaultStartForSemester(activeSemId, DateUtils.todayMoscow());
        }
        DatePickerDialog dpd = new DatePickerDialog(this, R.style.ThemeOverlay_UniSchedule_DatePicker, (v, y, m, d) -> {
            LocalDate sel = LocalDate.of(y, m + 1, d);
            LocalDate mon = DateUtils.mondayOfWeek(sel);
            if (mon.isAfter(sel)) mon = mon.minusWeeks(1);
            repo.updateSemesterStart(activeSemId, mon);
            updateInfo();
            Toast.makeText(this, "Начало семестра: " + DateUtils.formatDisplayDate(mon), Toast.LENGTH_SHORT).show();
        }, cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth());
        trackDialog(dpd);
        dpd.show();
    }

    private void doRefresh() {
        btnRefresh.setEnabled(false);
        btnRefresh.setText(R.string.settings_refresh);
        ex.execute(() -> repo.loadScheduleFromNetwork(new ScheduleRepository.LoadCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnRefresh.setEnabled(true);
                    btnRefresh.setText(R.string.settings_refresh);
                    updateInfo();
                    Toast.makeText(SettingsActivity.this, "Расписание обновлено", Toast.LENGTH_SHORT).show();
                    ScheduleDiffDialog.showIfHasChanges(SettingsActivity.this);
                });
            }

            @Override
            public void onError(AppError error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnRefresh.setEnabled(true);
                    btnRefresh.setText(R.string.settings_refresh);
                    Toast.makeText(SettingsActivity.this, error.userMessage, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onProgress(String m) { }
        }));
    }

    private void setupExcludedSubjects() {
        String type = repo.getSelectionType();
        String groupName = repo.getSelectionName();
        boolean isGroup = "group".equals(type);
        if (!isGroup || groupName == null || groupName.isEmpty()) {
            if (cardExcludedSubjects != null) cardExcludedSubjects.setVisibility(android.view.View.GONE);
            return;
        }
        if (cardExcludedSubjects != null) cardExcludedSubjects.setVisibility(android.view.View.VISIBLE);
        updateExcludedSummary(groupName);

        if (btnSelectExcludedSubjects != null) {
            btnSelectExcludedSubjects.setOnClickListener(v -> showExcludedSubjectsDialog(groupName));
        }
    }

    private void updateExcludedSummary(String groupName) {
        if (tvExcludedSubjectsSummary == null) return;
        Set<String> excluded = excludedManager.getExcludedSubjects(groupName);
        if (excluded.isEmpty()) {
            tvExcludedSubjectsSummary.setText("Нет исключённых предметов");
        } else {
            tvExcludedSubjectsSummary.setText("Исключено предметов: " + excluded.size() + "\n(будут вычеркнуты в расписании)");
        }
    }

    private void showClearDataDialog() {
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(20f * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Очистить данные");
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.glass_text_primary));
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(tvTitle);

        TextView tvMessage = new TextView(this);
        tvMessage.setText("Удалить расписание и настройки?");
        tvMessage.setTextColor(ContextCompat.getColor(this, R.color.glass_text_secondary));
        tvMessage.setTextSize(14f);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        msgLp.topMargin = Math.round(8f * density);
        root.addView(tvMessage, msgLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionsLp.topMargin = Math.round(18f * density);
        root.addView(actions, actionsLp);

        MaterialButton btnCancel = new MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
        );
        btnCancel.setText("Отмена");
        btnCancel.setCornerRadius(Math.round(23f * density));
        btnCancel.setTextColor(ContextCompat.getColor(this, R.color.glass_text_primary));
        btnCancel.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.glass_stroke_start)));
        btnCancel.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.glass_capsule)));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, Math.round(46f * density), 1f);
        cancelLp.setMarginEnd(Math.round(8f * density));
        actions.addView(btnCancel, cancelLp);

        MaterialButton btnDelete = new MaterialButton(this);
        btnDelete.setText("Удалить");
        btnDelete.setCornerRadius(Math.round(23f * density));
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.glass_danger)));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, Math.round(46f * density), 1f);
        actions.addView(btnDelete, deleteLp);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            ex.execute(() -> {
                repo.clearAllData();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Данные очищены", Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(this, SelectionActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                });
            });
        });

        trackDialog(dialog);
        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, root);
    }

    private void showExcludedSubjectsDialog(String groupName) {
        ex.execute(() -> {
            List<String> subjects = repo.getDistinctSubjectsForGroup(groupName);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (subjects == null || subjects.isEmpty()) {
                    Toast.makeText(this, "Предметы группы не найдены. Обновите расписание.", Toast.LENGTH_SHORT).show();
                    return;
                }

                View view = LayoutInflater.from(this).inflate(R.layout.dialog_excluded_subjects, null);
                View btnClose = view.findViewById(R.id.btnCloseExcludedDialog);
                TextInputEditText etSearch = view.findViewById(R.id.etSearchExcludedSubjects);
                TextView tvCount = view.findViewById(R.id.tvExcludedDialogCount);
                LinearLayout llList = view.findViewById(R.id.llExcludedSubjectsList);
                TextView tvEmpty = view.findViewById(R.id.tvExcludedEmptySearch);
                MaterialButton btnCancel = view.findViewById(R.id.btnCancelExcludedSubjects);
                MaterialButton btnSave = view.findViewById(R.id.btnSaveExcludedSubjects);

                Set<String> selectedSet = new HashSet<>(excludedManager.getExcludedSubjects(groupName));
                updateExcludedDialogCount(tvCount, selectedSet.size(), subjects.size());
                renderExcludedSubjectCheckboxes(llList, tvEmpty, tvCount, subjects, selectedSet, "");

                if (etSearch != null) {
                    etSearch.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            String query = s != null ? s.toString() : "";
                            renderExcludedSubjectCheckboxes(llList, tvEmpty, tvCount, subjects, selectedSet, query);
                        }

                        @Override
                        public void afterTextChanged(Editable s) { }
                    });
                }

                AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                        .setView(view)
                        .create();

                if (btnClose != null) {
                    btnClose.setOnClickListener(v -> dialog.dismiss());
                }
                if (btnCancel != null) {
                    btnCancel.setOnClickListener(v -> dialog.dismiss());
                }
                if (btnSave != null) {
                    btnSave.setOnClickListener(v -> {
                        excludedManager.setExcludedSubjects(groupName, selectedSet);
                        updateExcludedSummary(groupName);
                        ReminderScheduler.rescheduleAll(this);
                        Toast.makeText(this, "Настройки перезачёта сохранены", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
                }

                trackDialog(dialog);
                dialog.show();
                GlassSurfaceHelper.applyGlassDialog(dialog, view);

                if (dialog.getWindow() != null) {
                    DisplayMetrics dm = getResources().getDisplayMetrics();
                    int w = (int) (dm.widthPixels * 0.92f);
                    int h = (int) (dm.heightPixels * 0.78f);
                    dialog.getWindow().setLayout(w, h);
                }
            });
        });
    }

    private void renderExcludedSubjectCheckboxes(
            LinearLayout container,
            TextView tvEmpty,
            TextView tvCount,
            List<String> allSubjects,
            Set<String> selectedSet,
            String rawQuery) {
        container.removeAllViews();
        String query = rawQuery != null ? rawQuery.trim().toLowerCase(Locale.ROOT) : "";
        int accent = ContextCompat.getColor(this, R.color.glass_accent);
        int textPrimary = ContextCompat.getColor(this, R.color.glass_text_primary);
        int strokeColor = ContextCompat.getColor(this, R.color.glass_stroke_mid);
        ColorStateList tint = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{accent, ContextCompat.getColor(this, R.color.glass_text_secondary)}
        );

        int shown = 0;
        float density = getResources().getDisplayMetrics().density;
        for (String subject : allSubjects) {
            if (!query.isEmpty() && !subject.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            shown++;
            MaterialCheckBox cb = new MaterialCheckBox(this);
            cb.setText(subject);
            cb.setTextColor(textPrimary);
            cb.setTextSize(14f);
            cb.setButtonTintList(tint);
            cb.setPadding(Math.round(6f * density), Math.round(10f * density),
                    Math.round(6f * density), Math.round(10f * density));
            cb.setChecked(selectedSet.contains(subject));
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedSet.add(subject);
                } else {
                    selectedSet.remove(subject);
                }
                updateExcludedDialogCount(tvCount, selectedSet.size(), allSubjects.size());
            });
            container.addView(cb, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            View divider = new View(this);
            divider.setBackgroundColor(ColorUtils.setAlphaComponent(strokeColor, 64));
            container.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.max(1, Math.round(0.75f * density))
            ));
        }

        if (tvEmpty != null) {
            tvEmpty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void updateExcludedDialogCount(TextView tvCount, int selectedCount, int totalCount) {
        if (tvCount == null) return;
        tvCount.setText("Выбрано: " + selectedCount + " из " + totalCount);
    }

    private void trackDialog(Dialog dialog) {
        if (dialog == null) return;
        activeDialogs.add(dialog);
        dialog.setOnDismissListener(d -> activeDialogs.remove(dialog));
    }

    @Override
    protected void onDestroy() {
        for (Dialog d : new ArrayList<>(activeDialogs)) {
            if (d != null && d.isShowing()) {
                try {
                    d.dismiss();
                } catch (Exception ignored) {
                }
            }
        }
        activeDialogs.clear();
        super.onDestroy();
        ex.shutdown();
    }
}
