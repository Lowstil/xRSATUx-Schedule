package com.university.schedule.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.data.db.NoteDao;
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
import com.university.schedule.ui.controller.WeekScheduleController;
import com.university.schedule.ui.dialog.AttendanceDialog;
import com.university.schedule.ui.dialog.BellScheduleDialog;
import com.university.schedule.ui.dialog.BuildingInfoDialog;
import com.university.schedule.ui.dialog.HomeworkHubDialog;
import com.university.schedule.ui.dialog.LessonNoteDialog;
import com.university.schedule.ui.dialog.MarkAttendanceDialog;
import com.university.schedule.ui.dialog.RoomTeacherFinderDialog;
import com.university.schedule.ui.dialog.ScheduleDiffDialog;
import com.university.schedule.ui.glass.AmbientBackdropView;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private LessonNoteDialog.PhotoCallback activePhotoCallback;
    private ActivityResultLauncher<String> pickPhotoLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private ExcludedSubjectsManager excludedSubjectsManager;
    private SkippedLessonsManager skippedLessonsManager;
    private AttendanceManager attendanceManager;
    private NoteDao noteDao;

    private AmbientBackdropView ambientBackdrop;
    private View headerContainer;
    private View toolbarTitleArea;
    private View weekSwitcherCard;
    private View bottomDock;
    private View dockPairs;
    private View dockAttendance;
    private View dockRooms;
    private View dockMore;
    private TextView tvDockMoreBadge;
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
    private WeekScheduleController weekController;
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
        EdgeToEdgeCompat.enableEdgeToEdge(this);
        setContentView(R.layout.activity_main);

        repo = ScheduleRepository.getInstance(this);
        calc = repo.getWeekCalculator();
        weekController = new WeekScheduleController(repo, ex);
        noteDao = new NoteDao(this);
        selType = repo.getSelectionType();
        selName = repo.getSelectionName();
        initWeekForDate(DateUtils.todayMoscow());

        ambientBackdrop = findViewById(R.id.ambientBackdrop);
        headerContainer = findViewById(R.id.headerContainer);
        toolbarTitleArea = findViewById(R.id.toolbarTitleArea);
        weekSwitcherCard = findViewById(R.id.weekSwitcherCard);
        bottomDock = findViewById(R.id.bottomDock);
        dockPairs = findViewById(R.id.dockPairs);
        dockAttendance = findViewById(R.id.dockAttendance);
        dockRooms = findViewById(R.id.dockRooms);
        dockMore = findViewById(R.id.dockMore);
        tvDockMoreBadge = findViewById(R.id.tvDockMoreBadge);
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
        tvTodayDate.setOnClickListener(v -> jumpToToday());

        setupBottomDock();

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
        pagerAdapter.setOnTeacherClickListener(teacherName ->
                RoomTeacherFinderDialog.showForTeacher(this, teacherName));
        pagerAdapter.setOnRoomClickListener(rawRoom ->
                BuildingInfoDialog.show(this, rawRoom));

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
                viewPager.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
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
                    currentWeek = weekController.getCachedWeek(weekNum);
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
                currentWeek = weekController.getCachedWeek(weekNum);
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
                currentWeek = weekController.getCachedWeek(weekNum);
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
                pagerAdapter.setBottomInsetPx(bars.bottom + dpToPx(88));
            }
            return insets;
        });

        if (bottomDock != null) {
            EdgeToEdgeCompat.applyBottomSystemBarMargin(bottomDock);
        }
    }

    private void applyGlassChrome() {
        GlassSurfaceHelper.applyGlassCard(toolbarTitleArea, 18f, false);
        GlassSurfaceHelper.applyGlassCard(tvTodayDate, 999f, false);
        GlassSurfaceHelper.applyGlassCard(weekSwitcherCard, 20f, false);
        GlassSurfaceHelper.applyGlassCard(staleBanner, 16f, true);
        GlassSurfaceHelper.applyGlassCard(bottomDock, 999f, false);
        GlassSurfaceHelper.applyGlassCard(dockPairs, 999f, true);
    }

    private void setupBottomDock() {
        if (dockPairs != null) {
            dockPairs.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                jumpToToday();
            });
        }
        if (dockAttendance != null) {
            dockAttendance.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                openAttendanceDialog();
            });
        }
        if (dockRooms != null) {
            dockRooms.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                RoomTeacherFinderDialog.show(this);
            });
        }
        if (dockMore != null) {
            dockMore.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                showMoreMenuDialog();
            });
        }
        updateDockMoreBadge();
    }

    private void jumpToToday() {
        initWeekForDate(DateUtils.todayMoscow());
        currentWeek = weekController.getCachedWeek(weekNum);
        updateWeekHeaderUi();
        updateDayTabsForWeek();
        selectPage(weekNum, dayIndex, true);
        loadWeek();
        updateClock();
    }

    private void openAttendanceDialog() {
        AttendanceDialog.show(this, selName, () -> {
            if (attendanceManager != null) {
                attendanceManager.invalidateCache();
                pagerAdapter.notifyDataSetChanged();
            }
            LiveLessonService.syncNow(this);
        });
    }

    private void updateDockMoreBadge() {
        if (tvDockMoreBadge == null || noteDao == null) return;
        int count = noteDao.getPendingNotesCount();
        if (count > 0) {
            tvDockMoreBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(dpToPx(999));
            badgeBg.setColor(ContextCompat.getColor(this, R.color.glass_accent));
            tvDockMoreBadge.setBackground(badgeBg);
            tvDockMoreBadge.setVisibility(View.VISIBLE);
        } else {
            tvDockMoreBadge.setVisibility(View.GONE);
        }
    }

    private void showMoreMenuDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = dpToPx(18);
        root.setPadding(padH, dpToPx(18), padH, dpToPx(18));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Ещё");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.glass_text_primary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleLp);
        header.addView(tvTitle);

        TextView btnClose = new TextView(this);
        btnClose.setText("✕");
        btnClose.setTextSize(15);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setTextColor(ContextCompat.getColor(this, R.color.glass_text_secondary));
        btnClose.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        GlassSurfaceHelper.applyGlassCard(btnClose, 999f, false);
        header.addView(btnClose);
        root.addView(header);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        int pendingCount = (noteDao != null) ? noteDao.getPendingNotesCount() : 0;
        addMoreMenuItem(root, "📝", "Домашки и заметки", pendingCount > 0 ? String.valueOf(pendingCount) : null, () -> {
            dialog.dismiss();
            HomeworkHubDialog.show(this, () -> {
                clearWeekCache();
                loadWeek();
                updateDockMoreBadge();
            });
        });
        addMoreMenuItem(root, "🔔", "Расписание звонков", null, () -> {
            dialog.dismiss();
            BellScheduleDialog.show(this);
        });
        addMoreMenuItem(root, "🔄", "Что изменилось", null, () -> {
            dialog.dismiss();
            ScheduleDiffDialog.show(this);
        });
        addMoreMenuItem(root, "↻", "Обновить расписание", null, () -> {
            dialog.dismiss();
            swipeRefresh.setRefreshing(true);
            refresh();
        });
        addMoreMenuItem(root, "⚙", getString(R.string.action_settings), null, () -> {
            dialog.dismiss();
            startActivity(new Intent(this, SettingsActivity.class));
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(getWindow().getDecorView());
            int navBottom = rootInsets != null ? rootInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom : 0;
            window.getAttributes().y = navBottom + dpToPx(16);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.94);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void addMoreMenuItem(LinearLayout parent, String icon, String title, String badgeText, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dpToPx(10);
        row.setLayoutParams(lp);
        GlassSurfaceHelper.applyGlassCard(row, 16f, false);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon);
        tvIcon.setTextSize(16);
        tvIcon.setTextColor(ContextCompat.getColor(this, R.color.glass_text_primary));
        row.addView(tvIcon);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.glass_text_primary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dpToPx(12));
        tvTitle.setLayoutParams(titleLp);
        row.addView(tvTitle);

        if (badgeText != null && !badgeText.isEmpty()) {
            TextView tvBadge = new TextView(this);
            tvBadge.setText(badgeText);
            tvBadge.setTextSize(11);
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setTextColor(Color.WHITE);
            tvBadge.setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(dpToPx(999));
            badgeBg.setColor(ContextCompat.getColor(this, R.color.glass_accent));
            tvBadge.setBackground(badgeBg);
            row.addView(tvBadge);
        }

        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            onClick.run();
        });
        parent.addView(row);
    }

    private void showSelectionDialog() {
        boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
        String title = isGroup ? "Выбрать другую группу?" : "Выбрать другого преподавателя?";
        String message = isGroup
                ? "Текущая группа: " + (selName != null ? selName : "—") + "\nХотите выбрать другую группу?"
                : "Текущий преподаватель: " + (selName != null ? selName : "—") + "\nХотите выбрать другого преподавателя?";

        LinearLayout root = createGlassMessageDialogRoot(title, message);
        LinearLayout actions = createGlassDialogActionsRow();

        TextView btnCancel = createGlassDialogButton("Отмена", R.color.glass_text_secondary, false);
        TextView btnSelect = createGlassDialogButton("Выбрать", R.color.glass_accent, true);
        LinearLayout.LayoutParams selLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        selLp.setMarginStart(dpToPx(10));
        btnSelect.setLayoutParams(selLp);

        actions.addView(btnCancel);
        actions.addView(btnSelect);
        root.addView(actions);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSelect.setOnClickListener(v -> {
            dialog.dismiss();
            Intent i = new Intent(this, SelectionActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, root);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showWelcomePermissionsDialog(PrefsManager prefs) {
        String title = "Добро пожаловать в РГАТУ!";
        String message = "Для работы напоминаний о парах приложению требуется доступ к уведомлениям:\n\n"
                + "• 🔔 Напоминание за 10–15 мин до звонка (с кабинетом и предметом)\n"
                + "• 🌅 Утреннее расписание в 07:30\n"
                + "• ⚠️ Оповещения об отменах и переносах пар\n\n"
                + "Разрешить отправку уведомлений?";

        LinearLayout root = createGlassMessageDialogRoot(title, message);
        LinearLayout actions = createGlassDialogActionsRow();

        TextView btnLater = createGlassDialogButton("Позже", R.color.glass_text_secondary, false);
        TextView btnAllow = createGlassDialogButton("Разрешить", R.color.glass_accent, true);
        LinearLayout.LayoutParams allowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        allowLp.setMarginStart(dpToPx(10));
        btnAllow.setLayoutParams(allowLp);

        actions.addView(btnLater);
        actions.addView(btnAllow);
        root.addView(actions);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .setCancelable(false)
                .create();

        btnLater.setOnClickListener(v -> {
            prefs.setFirstLaunchCompleted(true);
            dialog.dismiss();
        });
        btnAllow.setOnClickListener(v -> {
            prefs.setFirstLaunchCompleted(true);
            dialog.dismiss();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, root);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private LinearLayout createGlassMessageDialogRoot(String title, String message) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(22);
        root.setPadding(pad, pad, pad, dpToPx(16));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.glass_text_primary));
        root.addView(tvTitle);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(message);
        tvMsg.setTextSize(14);
        tvMsg.setLineSpacing(0f, 1.15f);
        tvMsg.setTextColor(ContextCompat.getColor(this, R.color.glass_text_secondary));
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        msgLp.topMargin = dpToPx(10);
        tvMsg.setLayoutParams(msgLp);
        root.addView(tvMsg);
        return root;
    }

    private LinearLayout createGlassDialogActionsRow() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actLp.topMargin = dpToPx(20);
        actions.setLayoutParams(actLp);
        return actions;
    }

    private TextView createGlassDialogButton(String label, int colorRes, boolean highlight) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(14);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextColor(ContextCompat.getColor(this, colorRes));
        btn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        GlassSurfaceHelper.applyGlassCard(btn, 999f, highlight);
        return btn;
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
        LessonNoteDialog.show(
                this,
                item,
                date,
                existingNote,
                repo,
                cb -> {
                    activePhotoCallback = cb;
                    pickPhotoLauncher.launch("image/*");
                },
                () -> {
                    String dateIso = date.toString();
                    pagerAdapter.updateNotesForDate(dateIso, repo.getNotesForDate(dateIso));
                    updateDockMoreBadge();
                }
        );
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

    private void showSkipLessonDialog(ScheduleItem item, LocalDate date) {
        if (date == null || item == null) return;
        boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
        MarkAttendanceDialog.show(
                this,
                item,
                date,
                isGroup,
                attendanceManager,
                skippedLessonsManager,
                this::onAttendanceStateChanged
        );
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
        if (ambientBackdrop != null) {
            ambientBackdrop.reloadPreferences();
        }
        applyGlassChrome();
        updateTabSelectionVisuals();
        updateDockMoreBadge();
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
        weekController.clearCache();
        currentWeek = null;
        if (pagerAdapter != null) {
            pagerAdapter.clearWeeks();
        }
    }

    private void loadWeek() {
        updateWeekHeaderUi();

        boolean cachedHit = weekController.loadWeek(weekNum, selType, selName, (loadedWeek, ws, notesMap, isTarget) -> {
            pagerAdapter.setWeekSchedule(loadedWeek, ws, notesMap);
            if (loadedWeek == weekNum) {
                currentWeek = ws;
                progressBar.setVisibility(View.GONE);
                updateDayTabsForWeek();
                updateClock();
                LiveLessonService.syncNow(this);
            }
        });

        if (cachedHit) {
            currentWeek = weekController.getCachedWeek(weekNum);
            progressBar.setVisibility(View.GONE);
            updateDayTabsForWeek();
            updateClock();
        } else {
            progressBar.setVisibility(View.VISIBLE);
        }
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
