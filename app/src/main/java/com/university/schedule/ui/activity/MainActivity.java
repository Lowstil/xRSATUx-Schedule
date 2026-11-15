package com.university.schedule.ui.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
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
import com.university.schedule.notification.LiveLessonService;
import com.university.schedule.notification.ReminderScheduler;
import com.university.schedule.ui.adapter.DayPagerAdapter;
import com.university.schedule.ui.dialog.AttendanceDialog;
import com.university.schedule.ui.dialog.BellScheduleDialog;
import com.university.schedule.ui.dialog.RoomTeacherFinderDialog;
import com.university.schedule.ui.dialog.ScheduleDiffDialog;
import com.university.schedule.ui.glass.AmbientBackdropView;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.AppError;
import com.university.schedule.util.AttendanceManager;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private AmbientBackdropView ambientBackdrop;
    private View headerContainer;
    private View toolbarTitleArea;
    private View weekSwitcherCard;
    private View btnSettings;
    private View btnMenu;
    private View btnPrev, btnNext;
    private View staleBanner;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private TextView tvWeekInfo, tvSelectionName, tvSelectionType, tvTodayDate, tvMonthLabel, tvStaleBannerText;
    private HorizontalScrollView hsvFavorites;
    private LinearLayout llFavorites;

    private DayPagerAdapter pagerAdapter;
    private ScheduleRepository repo;
    private WeekCalculator calc;
    private final SparseArray<WeekSchedule> weekCache = new SparseArray<>();
    private final Set<Integer> loadingWeeks = new HashSet<>();
    private int selectionGeneration = 0;
    private WeekSchedule currentWeek;
    private String selType, selName;
    private int weekNum, dayIndex;
    private boolean suppressTabCallback;
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
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        repo = ScheduleRepository.getInstance(this);
        calc = repo.getWeekCalculator();
        selType = repo.getSelectionType();
        selName = repo.getSelectionName();
        initWeekForDate(DateUtils.todayMoscow());

        ambientBackdrop = findViewById(R.id.ambientBackdrop);
        headerContainer = findViewById(R.id.headerContainer);
        toolbarTitleArea = findViewById(R.id.toolbarTitleArea);
        weekSwitcherCard = findViewById(R.id.weekSwitcherCard);
        btnSettings = findViewById(R.id.btnSettings);
        btnMenu = findViewById(R.id.btnMenu);
        btnPrev = findViewById(R.id.btnPrevWeek);
        btnNext = findViewById(R.id.btnNextWeek);
        staleBanner = findViewById(R.id.staleBanner);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        tvWeekInfo = findViewById(R.id.tvWeekInfo);
        tvSelectionName = findViewById(R.id.tvSelectionName);
        tvSelectionType = findViewById(R.id.tvSelectionType);
        tvTodayDate = findViewById(R.id.tvTodayDate);
        tvMonthLabel = findViewById(R.id.tvMonthLabel);
        tvStaleBannerText = findViewById(R.id.tvStaleBannerText);
        hsvFavorites = findViewById(R.id.hsvFavorites);
        llFavorites = findViewById(R.id.llFavorites);

        applyEdgeToEdgeInsets();
        applyGlassChrome();

        staleBanner.setOnClickListener(v -> {
            swipeRefresh.setRefreshing(true);
            refresh();
        });

        tvSelectionName.setText(selName != null ? selName : "—");
        tvSelectionType.setText(GroupOrTeacher.TYPE_GROUP.equals(selType) ? "Группа" : "Преподаватель");
        tvTodayDate.setText(DateUtils.formatDisplayDateShort(DateUtils.todayMoscow()));
        tvTodayDate.setOnClickListener(v -> {
            initWeekForDate(DateUtils.todayMoscow());
            currentWeek = weekCache.get(weekNum);
            updateWeekHeaderUi();
            updateDayTabsForWeek();
            selectPage(weekNum, dayIndex, true);
            loadWeek();
        });

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }
        if (btnMenu != null) {
            btnMenu.setOnClickListener(this::showOverflowMenu);
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
                        LiveLessonService.syncNow(this);
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

        pagerAdapter = new DayPagerAdapter();
        pagerAdapter.setExcludedSubjects(excludedSubjectsManager.getExcludedSubjects(selName));
        pagerAdapter.setSkippedLessonsManager(skippedLessonsManager);
        pagerAdapter.setAttendanceManager(attendanceManager);
        pagerAdapter.setOnLessonClickListener(this::showLessonNoteDialog);
        pagerAdapter.setOnLessonLongClickListener(this::showSkipLessonDialog);

        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(2);
        viewPager.setCurrentItem(DayPagerAdapter.positionOf(weekNum, dayIndex), false);

        buildDayTabs();
        selectPage(weekNum, dayIndex, false);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab t) {
                if (suppressTabCallback) return;
                dayIndex = t.getPosition();
                viewPager.setCurrentItem(DayPagerAdapter.positionOf(weekNum, dayIndex), true);
                updateTabSelectionVisuals();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab t) { }

            @Override
            public void onTabReselected(TabLayout.Tab t) { }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int newWeek = DayPagerAdapter.weekOf(position);
                int newDay = DayPagerAdapter.dayOf(position);
                dayIndex = newDay;

                TabLayout.Tab tab = tabLayout.getTabAt(newDay);
                if (tab != null && !tab.isSelected()) {
                    suppressTabCallback = true;
                    tab.select();
                    suppressTabCallback = false;
                    tabLayout.setScrollPosition(newDay, 0f, true);
                }
                updateTabSelectionVisuals();

                if (newWeek != weekNum) {
                    weekNum = newWeek;
                    currentWeek = weekCache.get(weekNum);
                    updateWeekHeaderUi();
                    updateDayTabsForWeek();
                    loadWeek();
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    swipeRefresh.setEnabled(false);
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    swipeRefresh.setEnabled(true);
                }
            }
        });

        swipeRefresh.setOnChildScrollUpCallback((parent, child) ->
                pagerAdapter.canPageScrollUp(viewPager.getCurrentItem()));
        swipeRefresh.setOnRefreshListener(this::refresh);

        btnPrev.setOnClickListener(v -> {
            if (weekNum > 1) {
                weekNum--;
                dayIndex = 0;
                currentWeek = weekCache.get(weekNum);
                updateWeekHeaderUi();
                updateDayTabsForWeek();
                selectPage(weekNum, 0, true);
                loadWeek();
            }
        });
        btnNext.setOnClickListener(v -> {
            if (weekNum < DayPagerAdapter.WEEK_COUNT) {
                weekNum++;
                dayIndex = 0;
                currentWeek = weekCache.get(weekNum);
                updateWeekHeaderUi();
                updateDayTabsForWeek();
                selectPage(weekNum, 0, true);
                loadWeek();
            }
        });

        updateLastUpdatedBanner();
        updateFavoritesBar();
        loadWeek();
    }

    private void applyEdgeToEdgeInsets() {
        final int baseLeft = headerContainer.getPaddingLeft();
        final int baseTop = headerContainer.getPaddingTop();
        final int baseRight = headerContainer.getPaddingRight();
        final int baseBottom = headerContainer.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(headerContainer, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    baseLeft + bars.left,
                    baseTop + bars.top,
                    baseRight + bars.right,
                    baseBottom
            );
            if (pagerAdapter != null) {
                pagerAdapter.setBottomInsetPx(bars.bottom);
            }
            return insets;
        });
    }

    private void applyGlassChrome() {
        GlassSurfaceHelper.applyGlassCard(toolbarTitleArea, 18f, false);
        GlassSurfaceHelper.applyGlassCard(tvTodayDate, 999f, false);
        GlassSurfaceHelper.applyGlassCard(btnSettings, 999f, false);
        GlassSurfaceHelper.applyGlassCard(btnMenu, 999f, false);
        GlassSurfaceHelper.applyGlassCard(weekSwitcherCard, 20f, false);
        GlassSurfaceHelper.applyGlassCard(staleBanner, 16f, true);
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_main, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> handleMenuAction(item.getItemId()));
        popup.show();
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
                .setNegativeButton("Позже", (dialog, which) -> prefs.setFirstLaunchCompleted(true))
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
            chip.setTextSize(12);
            chip.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd(dpToPx(8));
            chip.setLayoutParams(lp);

            GlassSurfaceHelper.applyGlassCard(chip, 999f, isSelected);
            chip.setTextColor(ContextCompat.getColor(this,
                    isSelected ? R.color.glass_accent : R.color.glass_text_primary));
            chip.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);

            chip.setOnClickListener(v -> {
                if (!fav.getName().equalsIgnoreCase(selName)) {
                    selType = fav.getType();
                    selName = fav.getName();
                    repo.saveSelection(selType, selName);
                    if (excludedSubjectsManager != null) {
                        pagerAdapter.setExcludedSubjects(excludedSubjectsManager.getExcludedSubjects(selName));
                    }
                    tvSelectionName.setText(selName);
                    tvSelectionType.setText(fav.isGroup() ? "Группа" : "Преподаватель");
                    clearWeekCache();
                    loadWeek();
                    updateFavoritesBar();
                    if (attendanceManager != null) {
                        attendanceManager.invalidateCache();
                    }
                    WidgetUpdateHelper.updateAllWidgets(this);
                    ReminderScheduler.rescheduleAll(this);
                    LiveLessonService.syncNow(this);
                }
            });

            chip.setOnLongClickListener(v -> {
                showRemoveFavoriteDialog(fav);
                return true;
            });

            llFavorites.addView(chip);
        }

        TextView btnAdd = new TextView(this);
        btnAdd.setText("＋");
        btnAdd.setTextSize(14);
        btnAdd.setTextColor(ContextCompat.getColor(this, R.color.glass_text_primary));
        btnAdd.setPadding(dpToPx(11), dpToPx(5), dpToPx(11), dpToPx(5));
        GlassSurfaceHelper.applyGlassCard(btnAdd, 999f, false);
        btnAdd.setOnClickListener(v -> startActivity(new Intent(this, SelectionActivity.class)));
        llFavorites.addView(btnAdd);
    }

    private void showRemoveFavoriteDialog(GroupOrTeacher fav) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(22);
        root.setPadding(pad, pad, pad, dpToPx(16));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Удалить из избранного?");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.glass_text_primary));
        root.addView(tvTitle);

        TextView tvMsg = new TextView(this);
        tvMsg.setText("Удалить «" + fav.getName() + "» из избранного?");
        tvMsg.setTextSize(14);
        tvMsg.setTextColor(ContextCompat.getColor(this, R.color.glass_text_secondary));
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        msgLp.topMargin = dpToPx(10);
        tvMsg.setLayoutParams(msgLp);
        root.addView(tvMsg);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actLp.topMargin = dpToPx(20);
        actions.setLayoutParams(actLp);

        TextView btnCancel = new TextView(this);
        btnCancel.setText("Отмена");
        btnCancel.setTextSize(14);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setTextColor(ContextCompat.getColor(this, R.color.glass_text_secondary));
        btnCancel.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        GlassSurfaceHelper.applyGlassCard(btnCancel, 999f, false);

        TextView btnDelete = new TextView(this);
        btnDelete.setText("Удалить");
        btnDelete.setTextSize(14);
        btnDelete.setTypeface(null, Typeface.BOLD);
        btnDelete.setTextColor(ContextCompat.getColor(this, R.color.glass_danger));
        btnDelete.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        delLp.setMarginStart(dpToPx(10));
        btnDelete.setLayoutParams(delLp);
        GlassSurfaceHelper.applyGlassCard(btnDelete, 999f, false);

        actions.addView(btnCancel);
        actions.addView(btnDelete);
        root.addView(actions);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDelete.setOnClickListener(v -> {
            FavoritesManager.removeFavorite(this, fav.getType(), fav.getName());
            updateFavoritesBar();
            dialog.dismiss();
            Toast.makeText(this, "Удалено из избранного", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, root);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
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
        MaterialButton btnAttachPhoto = dialogView.findViewById(R.id.btnAttachPhoto);
        View btnDelete = dialogView.findViewById(R.id.btnDeleteNote);
        View btnCancel = dialogView.findViewById(R.id.btnCancelNote);
        View btnSave = dialogView.findViewById(R.id.btnSaveNote);

        tvSubject.setText(item.getSubjectName());
        String det = item.getLessonNumber() + " пара · " + DateUtils.formatDisplayDate(date);
        if (item.getRoom() != null && !item.getRoom().isEmpty()) det += " · ауд. " + item.getRoom();
        tvDetails.setText(det);

        final String[] currentPhotoPath = new String[]{(existingNote != null) ? existingNote.getImagePath() : null};

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
            String dateIso = date.toString();
            repo.deleteNote(dateIso, item.getLessonNumber());
            pagerAdapter.updateNotesForDate(dateIso, repo.getNotesForDate(dateIso));
            dialog.dismiss();
            Toast.makeText(this, "Заметка удалена", Toast.LENGTH_SHORT).show();
        });

        btnSave.setOnClickListener(v -> {
            String text = etNote.getText().toString().trim();
            String dateIso = date.toString();
            if (text.isEmpty() && currentPhotoPath[0] == null) {
                repo.deleteNote(dateIso, item.getLessonNumber());
            } else {
                repo.saveNote(dateIso, item.getLessonNumber(), item.getSubjectName(), text, currentPhotoPath[0], cbCompleted.isChecked());
            }
            pagerAdapter.updateNotesForDate(dateIso, repo.getNotesForDate(dateIso));
            dialog.dismiss();
            Toast.makeText(this, "Заметка сохранена", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, dialogView);
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
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.glass_success));
            btnReset.setVisibility(View.VISIBLE);
        } else if (currentStatus == SkippedLessonsManager.AttendanceStatus.SKIPPED) {
            tvStatus.setText("🔴 Отмечено: Н-ка (пропуск)");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.glass_danger));
            btnReset.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setText("⚪ Не отмечено");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.glass_text_secondary));
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
            onAttendanceStateChanged();
            Toast.makeText(this, isFuture ? "Отмечено: Пойду на пару" : "Отмечено: Пара посещена", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        cardSkipped.setOnClickListener(v -> {
            skippedLessonsManager.markSkipped(date, item.getLessonNumber(), item.getSubjectName());
            onAttendanceStateChanged();
            Toast.makeText(this, "Отмечено: Н-ка (пропуск)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnReset.setOnClickListener(v -> {
            skippedLessonsManager.clearStatus(date, item.getLessonNumber(), item.getSubjectName());
            onAttendanceStateChanged();
            Toast.makeText(this, "Отметка сброшена", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, dialogView);

        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void onAttendanceStateChanged() {
        if (attendanceManager != null) attendanceManager.invalidateCache();
        pagerAdapter.notifyDataSetChanged();
        updateClock();
        WidgetUpdateHelper.updateAllWidgets(this);
        ReminderScheduler.rescheduleAll(this);
        LiveLessonService.syncNow(this);
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

    private void selectPage(int targetWeek, int targetDay, boolean smoothScroll) {
        if (targetWeek < 1) targetWeek = 1;
        if (targetWeek > DayPagerAdapter.WEEK_COUNT) targetWeek = DayPagerAdapter.WEEK_COUNT;
        if (targetDay < 0 || targetDay >= DayPagerAdapter.DAYS_PER_WEEK) targetDay = 0;
        final int targetPos = DayPagerAdapter.positionOf(targetWeek, targetDay);
        final int finalDay = targetDay;
        if (viewPager != null && viewPager.getCurrentItem() != targetPos) {
            viewPager.setCurrentItem(targetPos, smoothScroll);
        }
        tabLayout.post(() -> {
            TabLayout.Tab tab = tabLayout.getTabAt(finalDay);
            if (tab != null) {
                suppressTabCallback = true;
                tab.select();
                suppressTabCallback = false;
                tabLayout.setScrollPosition(finalDay, 0f, true);
            }
            updateTabSelectionVisuals();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyGlassChrome();
        updateTabSelectionVisuals();
        LocalDate today = DateUtils.todayMoscow();
        if (builtForDate == null || !builtForDate.equals(today)) {
            calc = repo.getWeekCalculator();
            initWeekForDate(today);
            tvTodayDate.setText(DateUtils.formatDisplayDateShort(today));
            clearWeekCache();
            selectPage(weekNum, dayIndex, false);
            loadWeek();
        }
        updateFavoritesBar();
        if (excludedSubjectsManager != null) {
            pagerAdapter.setExcludedSubjects(excludedSubjectsManager.getExcludedSubjects(selName));
        }
        if (attendanceManager != null) {
            attendanceManager.invalidateCache();
        }
        pagerAdapter.notifyDataSetChanged();
        WidgetUpdateHelper.updateAllWidgets(this);
        LiveLessonService.syncNow(this);
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
        updateTabSelectionVisuals();
    }

    private void updateTabSelectionVisuals() {
        int selected = tabLayout.getSelectedTabPosition();
        if (selected < 0) selected = dayIndex;
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab == null || tab.getCustomView() == null) continue;
            View custom = tab.getCustomView();
            View pillRoot = custom.findViewById(R.id.tabPillRoot);
            TextView tvName = custom.findViewById(R.id.tvTabDayName);
            TextView tvNum = custom.findViewById(R.id.tvTabDayNumber);
            boolean isSelected = (i == selected);
            if (pillRoot != null) {
                GlassSurfaceHelper.applyGlassCard(pillRoot, 16f, isSelected);
            }
            if (tvName != null) {
                tvName.setTextColor(ContextCompat.getColor(this,
                        isSelected ? R.color.glass_accent : R.color.glass_text_secondary));
            }
            if (tvNum != null) {
                tvNum.setTextColor(ContextCompat.getColor(this,
                        isSelected ? R.color.glass_text_primary : R.color.glass_text_secondary));
            }
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
                if ("Сегодня".equals(label)) {
                    tvBadge.getBackground().mutate().setTint(ContextCompat.getColor(this, R.color.badge_today_bg));
                    tvBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_today_text));
                } else if ("Завтра".equals(label)) {
                    tvBadge.getBackground().mutate().setTint(ContextCompat.getColor(this, R.color.badge_tomorrow_bg));
                    tvBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_tomorrow_text));
                } else {
                    int accent = ContextCompat.getColor(this, R.color.glass_accent);
                    tvBadge.getBackground().mutate().setTint(
                            androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 34));
                    tvBadge.setTextColor(accent);
                }
            }
        }
        updateTabSelectionVisuals();
    }

    private void updateWeekHeaderUi() {
        String label = (weekNum % 2 == 0) ? "Чётная" : "Нечётная";
        LocalDate mon = calc.getMondayOfWeek(weekNum);
        LocalDate sat = calc.getSaturdayOfWeek(weekNum);
        String range = (mon != null && sat != null) ? DateUtils.formatDisplayDate(mon) + " – " + DateUtils.formatDisplayDate(sat) : "";
        tvWeekInfo.setText("Неделя " + weekNum + " (" + label + ") · " + range);
        btnPrev.setAlpha(weekNum > 1 ? 1f : 0.3f);
        btnNext.setAlpha(weekNum < DayPagerAdapter.WEEK_COUNT ? 1f : 0.3f);
    }

    private void clearWeekCache() {
        selectionGeneration++;
        weekCache.clear();
        loadingWeeks.clear();
        currentWeek = null;
        if (pagerAdapter != null) {
            pagerAdapter.clearWeeks();
        }
    }

    private void loadWeek() {
        updateWeekHeaderUi();

        WeekSchedule cached = weekCache.get(weekNum);
        if (cached != null) {
            currentWeek = cached;
            progressBar.setVisibility(View.GONE);
            updateDayTabsForWeek();
            updateClock();
        } else {
            progressBar.setVisibility(View.VISIBLE);
            fetchWeekIfNeeded(weekNum);
        }

        if (weekNum > 1) {
            fetchWeekIfNeeded(weekNum - 1);
        }
        if (weekNum < DayPagerAdapter.WEEK_COUNT) {
            fetchWeekIfNeeded(weekNum + 1);
        }
    }

    private void fetchWeekIfNeeded(final int targetWeek) {
        if (targetWeek < 1 || targetWeek > DayPagerAdapter.WEEK_COUNT) return;
        if (weekCache.get(targetWeek) != null || loadingWeeks.contains(targetWeek)) return;

        loadingWeeks.add(targetWeek);
        final int gen = selectionGeneration;
        final String type = selType;
        final String name = selName;

        ex.execute(() -> {
            if (gen != selectionGeneration) return;
            WeekSchedule ws = GroupOrTeacher.TYPE_GROUP.equals(type)
                    ? repo.getWeekScheduleForGroup(name, targetWeek)
                    : repo.getWeekScheduleForTeacher(name, targetWeek);
            Map<String, Map<Integer, LessonNote>> notesMap = new HashMap<>();
            if (ws != null && ws.getDays() != null) {
                for (DaySchedule d : ws.getDays()) {
                    if (d.getDate() != null) {
                        String iso = d.getDate().toString();
                        notesMap.put(iso, repo.getNotesForDate(iso));
                    }
                }
            }
            runOnUiThread(() -> {
                if (gen != selectionGeneration) return;
                loadingWeeks.remove(targetWeek);
                if (ws != null) {
                    weekCache.put(targetWeek, ws);
                }
                pagerAdapter.setWeekSchedule(targetWeek, ws, notesMap);
                if (targetWeek == weekNum) {
                    currentWeek = ws;
                    progressBar.setVisibility(View.GONE);
                    updateDayTabsForWeek();
                    updateClock();
                    LiveLessonService.syncNow(this);
                }
            });
        });
    }

    private void updateClock() {
        ScheduleClock.State st = ScheduleClock.compute();
        if (ambientBackdrop != null) {
            ambientBackdrop.setLessonActive(st != null && st.current != null);
        }
        boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
        NextLesson next = repo.findNextLesson(selName, isGroup,
                DateUtils.todayMoscow(), DateUtils.nowTimeMoscow(), 8);
        ScheduleClock.LessonKey nextKey = null;
        if (next != null) {
            nextKey = new ScheduleClock.LessonKey(next.getDayOfWeek(), next.getLessonNumber(), next.getDate());
        }
        pagerAdapter.applyClock(st, nextKey, next);
        WidgetUpdateHelper.updateAllWidgets(this);
        LiveLessonService.syncNow(this);
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
                    clearWeekCache();
                    loadWeek();
                    updateLastUpdatedBanner();
                    updateFavoritesBar();
                    LiveLessonService.syncNow(MainActivity.this);
                    ScheduleDiffDialog.showIfHasChanges(MainActivity.this);
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
        if (handleMenuAction(it.getItemId())) {
            return true;
        }
        return super.onOptionsItemSelected(it);
    }

    private boolean handleMenuAction(int id) {
        if (id == R.id.action_bell_schedule) {
            BellScheduleDialog.show(this);
            return true;
        }
        if (id == R.id.action_attendance) {
            AttendanceDialog.show(this, selName, () -> {
                if (attendanceManager != null) {
                    attendanceManager.invalidateCache();
                    pagerAdapter.notifyDataSetChanged();
                }
                LiveLessonService.syncNow(this);
            });
            return true;
        }
        if (id == R.id.action_room_finder) {
            RoomTeacherFinderDialog.show(this);
            return true;
        }
        if (id == R.id.action_schedule_diff) {
            ScheduleDiffDialog.show(this);
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_refresh) {
            swipeRefresh.setRefreshing(true);
            refresh();
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockTick);
        ex.shutdown();
    }
}
