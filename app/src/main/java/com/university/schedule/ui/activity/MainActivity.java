package com.university.schedule.ui.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ImageButton;
import com.google.android.material.button.MaterialButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.LessonNote;
import com.university.schedule.model.NextLesson;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.notification.ReminderScheduler;
import com.university.schedule.ui.adapter.DayScheduleAdapter;
import com.university.schedule.ui.dialog.AttendanceDialog;
import com.university.schedule.ui.dialog.BellScheduleDialog;
import com.university.schedule.util.AppError;
import com.university.schedule.util.AttendanceManager;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.EdgeToEdgeCompat;
import com.university.schedule.util.ExcludedSubjectsManager;
import com.university.schedule.util.FavoritesManager;
import com.university.schedule.util.PrefsManager;
import com.university.schedule.util.ScheduleClock;
import com.university.schedule.util.SkippedLessonsManager;
import com.university.schedule.widget.WidgetUpdateHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private interface PhotoPickCallback {
        void onPhotoReady(String path);
    }

    private PhotoPickCallback activePhotoCallback;
    private ActivityResultLauncher<String> pickPhotoLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private ExcludedSubjectsManager excludedSubjectsManager;
    private SkippedLessonsManager skippedLessonsManager;
    private AttendanceManager attendanceManager;

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private TextView tvWeekInfo, tvEmpty, tvSelectionName, tvSelectionType, tvTodayDate, tvMonthLabel, tvStaleBannerText;
    private View toolbarTitleArea;
    private View btnSettings;
    private View btnPrev, btnNext;
    private View staleBanner;
    private View nextLessonPanel;
    private TextView tvNextWhen, tvNextSubject, tvNextTeacherRoom;
    private HorizontalScrollView hsvFavorites;
    private LinearLayout llFavorites;

    private DayScheduleAdapter adapter;
    private ScheduleRepository repo;
    private WeekCalculator calc;
    private WeekSchedule currentWeek;
    private String selType, selName;
    private int weekNum, dayIndex;
    private boolean suppressTabCallback;
    private boolean currentDayEmpty;
    private LocalDate selectedDayDate;
    private LocalDate builtForDate;

    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            clockHandler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        EdgeToEdgeCompat.applyEdgeToEdgeInsets(this);

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
        btnSettings = findViewById(R.id.btnSettings);
        btnPrev = findViewById(R.id.btnPrevWeek);
        btnNext = findViewById(R.id.btnNextWeek);
        staleBanner = findViewById(R.id.staleBanner);
        tvStaleBannerText = findViewById(R.id.tvStaleBannerText);
        nextLessonPanel = findViewById(R.id.nextLessonPanel);
        tvNextWhen = findViewById(R.id.tvNextWhen);
        tvNextSubject = findViewById(R.id.tvNextSubject);
        tvNextTeacherRoom = findViewById(R.id.tvNextTeacherRoom);
        hsvFavorites = findViewById(R.id.hsvFavorites);
        llFavorites = findViewById(R.id.llFavorites);

        staleBanner.setOnClickListener(v -> {
            swipeRefresh.setRefreshing(true);
            refresh();
        });

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        tvSelectionName.setText(selName != null ? selName : "—");
        tvSelectionType.setText(GroupOrTeacher.TYPE_GROUP.equals(selType) ? "Группа" : "Преподаватель");
        tvTodayDate.setText(DateUtils.formatDisplayDateShort(DateUtils.todayMoscow()));
        tvTodayDate.setOnClickListener(v -> {
            initWeekForDate(DateUtils.todayMoscow());
            loadWeek();
            selectTodayTab();
        });

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }

        toolbarTitleArea.setOnClickListener(v -> showSelectionDialog());

        excludedSubjectsManager = new ExcludedSubjectsManager(this);
        skippedLessonsManager = new SkippedLessonsManager(this);
        attendanceManager = new AttendanceManager(this);

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        ReminderScheduler.rescheduleAll(this);
                    }
                }
        );

        PrefsManager prefs = new PrefsManager(this);
        if (prefs.isFirstLaunch()) {
            showWelcomePermissionsDialog(prefs);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        pickPhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && activePhotoCallback != null) {
                        ex.execute(() -> {
                            String localPath = savePickedImageToInternalStorage(uri);
                            runOnUiThread(() -> {
                                if (activePhotoCallback != null) {
                                    activePhotoCallback.onPhotoReady(localPath);
                                }
                            });
                        });
                    }
                }
        );

        adapter = new DayScheduleAdapter(new ArrayList<>());
        adapter.setExcludedSubjects(excludedSubjectsManager.getExcludedSubjects(selName));
        adapter.setSkippedLessonsManager(skippedLessonsManager);
        adapter.setAttendanceManager(attendanceManager);
        adapter.setOnLessonClickListener(this::showLessonNoteDialog);
        adapter.setOnLessonLongClickListener(this::showSkipLessonDialog);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        buildDayTabs();
        selectTodayTab();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab t) {
                if (suppressTabCallback) return;
                dayIndex = t.getPosition();
                displayDay();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab t) { }

            @Override
            public void onTabReselected(TabLayout.Tab t) { }
        });

        swipeRefresh.setOnRefreshListener(this::refresh);
        btnPrev.setOnClickListener(v -> { if (weekNum > 1) { weekNum--; loadWeek(); } });
        btnNext.setOnClickListener(v -> { if (weekNum < 18) { weekNum++; loadWeek(); } });

        updateLastUpdatedBanner();
        updateFavoritesBar();
        loadWeek();
    }

    private void showSelectionDialog() {
        boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
        String title = isGroup ? "Выбрать другую группу?" : "Выбрать другого преподавателя?";
        String message = isGroup
                ? "Текущая группа: " + (selName != null ? selName : "—") + "\nХотите выбрать другую группу?"
                : "Текущий преподаватель: " + (selName != null ? selName : "—") + "\nХотите выбрать другого преподавателя?";

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Выбрать", (dialog, which) -> {
                    Intent i = new Intent(this, SelectionActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showWelcomePermissionsDialog(PrefsManager prefs) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Добро пожаловать в РГАТУ!")
                .setMessage("Для работы напоминаний о парах приложению требуется доступ к уведомлениям:\n\n"
                        + "• 🔔 Напоминание за 10–15 мин до звонка (с кабинетом и предметом)\n"
                        + "• 🌅 Утреннее расписание в 07:30\n"
                        + "• ⚠️ Оповещения об отменах и переносах пар\n\n"
                        + "Разрешить отправку уведомлений?")
                .setPositiveButton("Разрешить", (dialog, which) -> {
                    prefs.setFirstLaunchCompleted(true);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    }
                })
                .setNegativeButton("Позже", (dialog, which) -> {
                    prefs.setFirstLaunchCompleted(true);
                })
                .setCancelable(false)
                .show();
    }

    private void updateFavoritesBar() {
        if (llFavorites == null || hsvFavorites == null) return;
        List<GroupOrTeacher> favs = FavoritesManager.getFavorites(this);
        if (favs.isEmpty()) {
            hsvFavorites.setVisibility(View.GONE);
            return;
        }

        hsvFavorites.setVisibility(View.VISIBLE);
        llFavorites.removeAllViews();

        for (GroupOrTeacher fav : favs) {
            TextView chip = new TextView(this);
            boolean isSelected = fav.getName().equalsIgnoreCase(selName);

            chip.setText("★ " + fav.getName());
            chip.setTextSize(13);
            chip.setPadding(dpToPx(12), dpToPx(5), dpToPx(12), dpToPx(5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd(dpToPx(8));
            chip.setLayoutParams(lp);

            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(dpToPx(16));
            if (isSelected) {
                gd.setColor(Color.WHITE);
                chip.setTextColor(ContextCompat.getColor(this, R.color.primary_dark));
                chip.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                gd.setColor(0x33FFFFFF);
                chip.setTextColor(Color.WHITE);
                chip.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
            chip.setBackground(gd);

            chip.setOnClickListener(v -> {
                if (!fav.getName().equalsIgnoreCase(selName)) {
                    selType = fav.getType();
                    selName = fav.getName();
                    repo.saveSelection(selType, selName);
                    if (excludedSubjectsManager != null) {
                        adapter.setExcludedSubjects(excludedSubjectsManager.getExcludedSubjects(selName));
                    }
                    tvSelectionName.setText(selName);
                    tvSelectionType.setText(fav.isGroup() ? "Группа" : "Преподаватель");
                    loadWeek();
                    updateFavoritesBar();
                    if (attendanceManager != null) {
                        attendanceManager.invalidateCache();
                    }
                    WidgetUpdateHelper.updateAllWidgets(this);
                    ReminderScheduler.rescheduleAll(this);
                }
            });

            llFavorites.addView(chip);
        }

        TextView btnAdd = new TextView(this);
        btnAdd.setText("＋");
        btnAdd.setTextSize(15);
        btnAdd.setTextColor(Color.WHITE);
        btnAdd.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
        GradientDrawable addGd = new GradientDrawable();
        addGd.setCornerRadius(dpToPx(16));
        addGd.setColor(0x22FFFFFF);
        btnAdd.setBackground(addGd);
        btnAdd.setOnClickListener(v -> {
            Intent i = new Intent(this, SelectionActivity.class);
            startActivity(i);
        });
        llFavorites.addView(btnAdd);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showLessonNoteDialog(ScheduleItem item, LocalDate date, LessonNote existingNote) {
        if (date == null) return;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_lesson_note, null);
        TextView tvSubject = dialogView.findViewById(R.id.tvNoteDialogSubject);
        TextView tvDetails = dialogView.findViewById(R.id.tvNoteDialogDetails);
        EditText etNote = dialogView.findViewById(R.id.etNoteText);
        CheckBox cbCompleted = dialogView.findViewById(R.id.cbNoteCompleted);
        View cardNotePhoto = dialogView.findViewById(R.id.cardNotePhoto);
        ImageView ivNotePhotoPreview = dialogView.findViewById(R.id.ivNotePhotoPreview);
        View btnRemovePhoto = dialogView.findViewById(R.id.btnRemovePhoto);
        com.google.android.material.button.MaterialButton btnAttachPhoto = dialogView.findViewById(R.id.btnAttachPhoto);
        View btnDelete = dialogView.findViewById(R.id.btnDeleteNote);
        View btnCancel = dialogView.findViewById(R.id.btnCancelNote);
        View btnSave = dialogView.findViewById(R.id.btnSaveNote);

        tvSubject.setText(item.getSubjectName());
        String det = item.getLessonNumber() + " пара · " + DateUtils.formatDisplayDate(date);
        if (item.getRoom() != null && !item.getRoom().isEmpty()) det += " · ауд. " + item.getRoom();
        tvDetails.setText(det);

        final String[] currentPhotoPath = new String[]{ (existingNote != null) ? existingNote.getImagePath() : null };

        Runnable updatePhotoUi = () -> {
            if (currentPhotoPath[0] != null && new File(currentPhotoPath[0]).exists()) {
                cardNotePhoto.setVisibility(View.VISIBLE);
                displayImageThumb(ivNotePhotoPreview, currentPhotoPath[0]);
                btnAttachPhoto.setText("Сменить фото");
            } else {
                cardNotePhoto.setVisibility(View.GONE);
                btnAttachPhoto.setText("Прикрепить фото");
            }
        };

        boolean hasContent = existingNote != null && ((existingNote.getNoteText() != null && !existingNote.getNoteText().isEmpty())
                || (existingNote.getImagePath() != null && !existingNote.getImagePath().isEmpty()));
        if (hasContent) {
            if (existingNote.getNoteText() != null) etNote.setText(existingNote.getNoteText());
            cbCompleted.setChecked(existingNote.isCompleted());
            cbCompleted.setVisibility(View.VISIBLE);
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            cbCompleted.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);
        }

        updatePhotoUi.run();

        activePhotoCallback = path -> {
            currentPhotoPath[0] = path;
            updatePhotoUi.run();
        };

        btnAttachPhoto.setOnClickListener(v -> pickPhotoLauncher.launch("image/*"));
        btnRemovePhoto.setOnClickListener(v -> {
            currentPhotoPath[0] = null;
            updatePhotoUi.run();
        });
        ivNotePhotoPreview.setOnClickListener(v -> {
            if (currentPhotoPath[0] != null) showFullScreenPhoto(currentPhotoPath[0]);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        dialog.setOnDismissListener(d -> activePhotoCallback = null);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDelete.setOnClickListener(v -> {
            repo.deleteNote(date.toString(), item.getLessonNumber());
            adapter.setNotes(repo.getNotesForDate(date.toString()));
            dialog.dismiss();
            Toast.makeText(this, "Заметка удалена", Toast.LENGTH_SHORT).show();
        });

        btnSave.setOnClickListener(v -> {
            String text = etNote.getText().toString().trim();
            if (text.isEmpty() && currentPhotoPath[0] == null) {
                repo.deleteNote(date.toString(), item.getLessonNumber());
            } else {
                repo.saveNote(date.toString(), item.getLessonNumber(), item.getSubjectName(), text, currentPhotoPath[0], cbCompleted.isChecked());
            }
            adapter.setNotes(repo.getNotesForDate(date.toString()));
            dialog.dismiss();
            Toast.makeText(this, "Заметка сохранена", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private String savePickedImageToInternalStorage(Uri uri) {
        try {
            File dir = new File(getFilesDir(), "notes_photos");
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(dest)) {
                if (in == null) return null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void displayImageThumb(ImageView iv, String path) {
        if (path == null || !new File(path).exists()) {
            iv.setImageDrawable(null);
            return;
        }
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            opts.inSampleSize = calculateInSampleSize(opts, 480, 360);
            opts.inJustDecodeBounds = false;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            iv.setImageBitmap(bmp);
        } catch (Throwable t) {
            iv.setImageDrawable(null);
        }
    }

    private void showFullScreenPhoto(String path) {
        if (path == null || !new File(path).exists()) return;
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp != null) {
                iv.setImageBitmap(bmp);
            } else {
                return;
            }
        } catch (Throwable t) {
            return;
        }
        iv.setOnClickListener(v -> d.dismiss());
        d.setContentView(iv);
        d.show();
    }

    private void showSkipLessonDialog(ScheduleItem item, LocalDate date) {
        showAttendanceMarkDialog(item, date);
    }

    private void showAttendanceMarkDialog(ScheduleItem item, LocalDate date) {
        if (date == null || item == null) return;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_mark_lesson_attendance, null);

        TextView tvSubject = dialogView.findViewById(R.id.tvMarkLessonSubject);
        TextView tvDetails = dialogView.findViewById(R.id.tvMarkLessonDetails);
        TextView tvDate = dialogView.findViewById(R.id.tvMarkLessonDate);
        TextView tvStatus = dialogView.findViewById(R.id.tvCurrentStatusBadge);
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseMarkDialog);
        View cardAttended = dialogView.findViewById(R.id.cardMarkAttended);
        TextView tvBtnAttendedTitle = dialogView.findViewById(R.id.tvBtnAttendedTitle);
        View cardSkipped = dialogView.findViewById(R.id.cardMarkSkipped);
        MaterialButton btnReset = dialogView.findViewById(R.id.btnResetMark);

        tvSubject.setText(item.getSubjectName());
        int dow = DateUtils.toScheduleDayOfWeek(date);
        String timeStr = Constants.getLessonTimeLabel(dow, item.getLessonNumber());
        String roomStr = (item.getRoom() != null && !item.getRoom().isEmpty()) ? " · ауд. " + item.getRoom() : "";
        tvDetails.setText(item.getLessonNumber() + " пара · " + timeStr + roomStr);
        tvDate.setText(DateUtils.formatDisplayDate(date));

        boolean isFuture = date.isAfter(DateUtils.todayMoscow());
        if (tvBtnAttendedTitle != null) {
            tvBtnAttendedTitle.setText(isFuture ? "Пойду на пару" : "Посетил пару");
        }

        SkippedLessonsManager.AttendanceStatus currentStatus = skippedLessonsManager.getStatus(date, item.getLessonNumber(), item.getSubjectName());
        if (currentStatus == SkippedLessonsManager.AttendanceStatus.ATTENDED) {
            tvStatus.setText(isFuture ? "🟢 Отмечено: Пойду" : "🟢 Отмечено: Посещено");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_attended_text));
            btnReset.setVisibility(View.VISIBLE);
        } else if (currentStatus == SkippedLessonsManager.AttendanceStatus.SKIPPED) {
            tvStatus.setText("🔴 Отмечено: Н-ка (пропуск)");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_nka_text));
            btnReset.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setText("⚪ Не отмечено");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            btnReset.setVisibility(View.GONE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        cardAttended.setOnClickListener(v -> {
            skippedLessonsManager.markAttended(date, item.getLessonNumber(), item.getSubjectName());
            if (attendanceManager != null) attendanceManager.invalidateCache();
            adapter.notifyDataSetChanged();
            updateClock();
            WidgetUpdateHelper.updateAllWidgets(this);
            ReminderScheduler.rescheduleAll(this);
            Toast.makeText(this, isFuture ? "Отмечено: Пойду на пару" : "Отмечено: Пара посещена", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        cardSkipped.setOnClickListener(v -> {
            skippedLessonsManager.markSkipped(date, item.getLessonNumber(), item.getSubjectName());
            if (attendanceManager != null) attendanceManager.invalidateCache();
            adapter.notifyDataSetChanged();
            updateClock();
            WidgetUpdateHelper.updateAllWidgets(this);
            ReminderScheduler.rescheduleAll(this);
            Toast.makeText(this, "Отмечено: Н-ка (пропуск)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnReset.setOnClickListener(v -> {
            skippedLessonsManager.clearStatus(date, item.getLessonNumber(), item.getSubjectName());
            if (attendanceManager != null) attendanceManager.invalidateCache();
            adapter.notifyDataSetChanged();
            updateClock();
            WidgetUpdateHelper.updateAllWidgets(this);
            ReminderScheduler.rescheduleAll(this);
            Toast.makeText(this, "Отметка сброшена", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
        }
    }

    private void initWeekForDate(LocalDate today) {
        LocalDate target = DateUtils.initialScheduleDate(today);
        weekNum = calc.getWeekNumber(target);
        if (weekNum < 1) {
            weekNum = calc.getCurrentWeekNumber();
            if (weekNum < 1) weekNum = 1;
        }
        if (weekNum > 18) weekNum = 18;
        int dow = DateUtils.toScheduleDayOfWeek(target);
        dayIndex = (dow >= 1 && dow <= 6) ? dow - 1 : 0;
        builtForDate = today;
    }

    private void selectTodayTab() {
        tabLayout.post(() -> {
            TabLayout.Tab tab = tabLayout.getTabAt(dayIndex);
            if (tab != null) {
                suppressTabCallback = true;
                tab.select();
                suppressTabCallback = false;
                tabLayout.setScrollPosition(dayIndex, 0f, true);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalDate today = DateUtils.todayMoscow();
        if (builtForDate == null || !builtForDate.equals(today)) {
            calc = repo.getWeekCalculator();
            initWeekForDate(today);
            tvTodayDate.setText(DateUtils.formatDisplayDateShort(today));
            selectTodayTab();
            loadWeek();
        }
        updateFavoritesBar();
        if (excludedSubjectsManager != null) {
            adapter.setExcludedSubjects(excludedSubjectsManager.getExcludedSubjects(selName));
        }
        if (attendanceManager != null) {
            attendanceManager.invalidateCache();
        }
        adapter.notifyDataSetChanged();
        WidgetUpdateHelper.updateAllWidgets(this);
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
            View cv = tab.getCustomView();
            if (cv != null) {
                TextView tvName = cv.findViewById(R.id.tvTabDayName);
                if (tvName != null) tvName.setText(Constants.DAY_NAMES_SHORT[i]);
            }
            tabLayout.addTab(tab);
        }
    }

    private void updateDayTabsForWeek() {
        LocalDate today = DateUtils.todayMoscow();
        List<DaySchedule> days = (currentWeek != null) ? currentWeek.getDays() : null;
        LocalDate mon = (days != null && !days.isEmpty()) ? days.get(0).getDate() : null;
        LocalDate sat = (days != null && days.size() >= 6) ? days.get(5).getDate() : null;
        tvMonthLabel.setText(DateUtils.formatMonthRangeRu(mon, sat));

        for (int i = 0; i < 6; i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab == null || tab.getCustomView() == null) continue;
            View custom = tab.getCustomView();
            TextView tvName = custom.findViewById(R.id.tvTabDayName);
            TextView tvNumber = custom.findViewById(R.id.tvTabDayNumber);
            TextView tvBadge = custom.findViewById(R.id.tvTabBadge);

            if (tvName != null) {
                tvName.setText(Constants.DAY_NAMES_SHORT[i]);
            }

            LocalDate date = (days != null && i < days.size()) ? days.get(i).getDate() : null;
            if (date == null) {
                if (tvNumber != null) tvNumber.setText("");
                if (tvBadge != null) tvBadge.setVisibility(View.GONE);
                continue;
            }

            if (tvNumber != null) {
                tvNumber.setText(String.valueOf(date.getDayOfMonth()));
            }

            String label = DateUtils.relativeDayLabel(date, today);
            if (label == null || tvBadge == null) {
                if (tvBadge != null) tvBadge.setVisibility(View.GONE);
            } else {
                tvBadge.setVisibility(View.VISIBLE);
                tvBadge.setText(label);
                int bgColor, textColor;
                if ("Сегодня".equals(label)) {
                    bgColor = R.color.badge_today_bg;
                    textColor = R.color.badge_today_text;
                } else if ("Завтра".equals(label)) {
                    bgColor = R.color.badge_tomorrow_bg;
                    textColor = R.color.badge_tomorrow_text;
                } else {
                    bgColor = R.color.badge_future_bg;
                    textColor = R.color.badge_future_text;
                }
                tvBadge.getBackground().mutate().setTint(ContextCompat.getColor(this, bgColor));
                tvBadge.setTextColor(ContextCompat.getColor(this, textColor));
            }
        }
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
            adapter.updateData(new ArrayList<>());
            adapter.setNotes(new HashMap<>());
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Нет данных");
            selectedDayDate = null;
            currentDayEmpty = true;
            adapter.applyClock(null, null, null);
            return;
        }

        DaySchedule day = currentWeek.getDays().get(dayIndex);
        selectedDayDate = day.getDate();
        currentDayEmpty = day.isDayOff() || !day.hasLessons();

        if (selectedDayDate != null) {
            adapter.setNotes(repo.getNotesForDate(selectedDayDate.toString()));
        } else {
            adapter.setNotes(new HashMap<>());
        }

        if (day.isDayOff()) {
            adapter.updateData(new ArrayList<>());
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(day.getHolidayName() != null ? day.getHolidayName() : "Выходной день");
            adapter.applyClock(selectedDayDate, null, null);
        } else if (!day.hasLessons()) {
            adapter.updateData(new ArrayList<>());
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Нет занятий");
            adapter.applyClock(selectedDayDate, null, null);
        } else {
            tvEmpty.setVisibility(View.GONE);
            adapter.updateData(day.getLessons());
        }

        updateClock();
    }

    private void updateClock() {
        ScheduleClock.State st = ScheduleClock.compute();
        boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
        NextLesson next = repo.findNextLesson(selName, isGroup,
                DateUtils.todayMoscow(), DateUtils.nowTimeMoscow(), 8);
        ScheduleClock.LessonKey nextKey = null;
        if (next != null) {
            nextKey = new ScheduleClock.LessonKey(next.getDayOfWeek(), next.getLessonNumber(), next.getDate());
        }
        adapter.applyClock(selectedDayDate, st, nextKey);
        updateNextLessonPanel(currentDayEmpty ? next : null);
        WidgetUpdateHelper.updateAllWidgets(this);
    }

    private void updateNextLessonPanel(NextLesson next) {
        if (!currentDayEmpty) {
            nextLessonPanel.setVisibility(View.GONE);
            return;
        }
        if (next == null) {
            nextLessonPanel.setVisibility(View.GONE);
            return;
        }
        String dowName = (next.getDayOfWeek() >= 1 && next.getDayOfWeek() <= 6)
                ? Constants.DAY_NAMES_SHORT[next.getDayOfWeek() - 1] : "";
        String when = dowName + ", " + DateUtils.formatDisplayDateShort(next.getDate())
                + " · " + next.getLessonNumber() + " пара · "
                + next.getTimeStart() + "–" + next.getTimeEnd();
        tvNextWhen.setText(when);
        tvNextSubject.setText(next.getSubjectName());
        String tr = next.getTeacherName() != null ? next.getTeacherName() : "";
        if (next.getRoom() != null && !next.getRoom().isEmpty()) {
            tr += (tr.isEmpty() ? "" : " · ") + next.getRoom();
        }
        tvNextTeacherRoom.setText(tr);
        tvNextTeacherRoom.setVisibility(tr.isEmpty() ? View.GONE : View.VISIBLE);
        nextLessonPanel.setVisibility(View.VISIBLE);
    }

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
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    repo.refreshLogic();
                    calc = repo.getWeekCalculator();
                    loadWeek();
                    updateLastUpdatedBanner();
                    updateFavoritesBar();
                });
            }

            @Override
            public void onError(AppError error) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    Snackbar.make(swipeRefresh, error.userMessage, Snackbar.LENGTH_LONG)
                            .setAction("Повторить", v -> refresh())
                            .show();
                });
            }

            @Override
            public void onProgress(String m) { }
        }));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        getMenuInflater().inflate(R.menu.menu_main, m);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        if (it.getItemId() == R.id.action_bell_schedule) {
            BellScheduleDialog.show(this);
            return true;
        }
        if (it.getItemId() == R.id.action_attendance) {
            AttendanceDialog.show(this, () -> {
                if (attendanceManager != null) {
                    attendanceManager.invalidateCache();
                    adapter.notifyDataSetChanged();
                }
            });
            return true;
        }
        if (it.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (it.getItemId() == R.id.action_refresh) {
            swipeRefresh.setRefreshing(true);
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(it);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockTick);
        ex.shutdown();
    }
}
