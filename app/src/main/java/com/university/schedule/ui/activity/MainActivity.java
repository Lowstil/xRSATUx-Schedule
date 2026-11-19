package com.university.schedule.ui.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
import androidx.annotation.NonNull;
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
import com.university.schedule.logic.SemesterManager;
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
import com.university.schedule.ui.dialog.CompareScheduleDialog;
import com.university.schedule.ui.dialog.CustomEventDialog;
import com.university.schedule.ui.dialog.HomeworkHubDialog;
import com.university.schedule.ui.dialog.LessonNoteDialog;
import com.university.schedule.ui.dialog.MarkAttendanceDialog;
import com.university.schedule.ui.dialog.RoomTeacherFinderDialog;
import com.university.schedule.ui.dialog.ScheduleDiffDialog;
import com.university.schedule.ui.glass.AmbientBackdropView;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.AppError;
import com.university.schedule.util.AppShortcutsHelper;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_PENDING_PHOTO_DATE = "pending_photo_date";
    private static final String KEY_PENDING_PHOTO_LESSON = "pending_photo_lesson";
    private static final String KEY_PENDING_PHOTO_SUBJECT = "pending_photo_subject";

    private LessonNoteDialog.PhotoCallback activePhotoCallback;
    private String pendingPhotoDateIso;
    private int pendingPhotoLessonNum = -1;
    private String pendingPhotoSubject;
    private final List<Dialog> activeDialogs = new ArrayList<>();
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
    private TextView semesterToastPill;
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
    private String activeSemesterId;
    private String selType, selName;
    private int weekNum, dayIndex;
    private boolean suppressTabCallback;
    private LocalDate builtForDate;

    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Handler semesterToastHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideSemesterToastRunnable = () -> {
        if (semesterToastPill == null || isFinishing() || isDestroyed()) return;
        semesterToastPill.animate()
                .alpha(0f)
                .translationY(-dpToPx(10))
                .setDuration(220)
                .withEndAction(() -> {
                    if (semesterToastPill != null) {
                        semesterToastPill.setVisibility(View.GONE);
                    }
                })
                .start();
    };
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

        if (s != null) {
            pendingPhotoDateIso = s.getString(KEY_PENDING_PHOTO_DATE);
            pendingPhotoLessonNum = s.getInt(KEY_PENDING_PHOTO_LESSON, -1);
            pendingPhotoSubject = s.getString(KEY_PENDING_PHOTO_SUBJECT);
        }

        repo = ScheduleRepository.getInstance(this);
        activeSemesterId = repo.getActiveSemesterId();
        if (activeSemesterId == null) {
            activeSemesterId = SemesterManager.SEMESTER_FALL;
        }
        calc = repo.getWeekCalculator(activeSemesterId);
        weekController = new WeekScheduleController(repo, ex);
        weekController.setSemesterId(activeSemesterId);
        noteDao = new NoteDao(this);
        selType = repo.getSelectionType();
        selName = repo.getSelectionName();
        initWeekForSemester(activeSemesterId, DateUtils.todayMoscow());

        ambientBackdrop = findViewById(R.id.ambientBackdrop);
        headerContainer = findViewById(R.id.headerContainer);
        toolbarTitleArea = findViewById(R.id.toolbarTitleArea);
        weekSwitcherCard = findViewById(R.id.weekSwitcherCard);
        semesterToastPill = findViewById(R.id.semesterToastPill);
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
                    if (uri == null) {
                        clearPendingPhotoTarget();
                        return;
                    }
                    if (activePhotoCallback != null) {
                        clearPendingPhotoTarget();
                        ex.execute(() -> {
                            String localPath = savePickedImageToInternalStorage(uri);
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                if (activePhotoCallback != null) {
                                    activePhotoCallback.onPhotoReady(localPath);
                                }
                            });
                        });
                    } else if (pendingPhotoDateIso != null && pendingPhotoLessonNum > 0) {
                        final String dateIso = pendingPhotoDateIso;
                        final int lessonNum = pendingPhotoLessonNum;
                        final String subject = pendingPhotoSubject;
                        clearPendingPhotoTarget();
                        ex.execute(() -> {
                            String localPath = savePickedImageToInternalStorage(uri);
                            Map<Integer, LessonNote> notesForDate = repo.getNotesForDate(dateIso);
                            LessonNote existing = notesForDate != null ? notesForDate.get(lessonNum) : null;
                            LessonNote noteToShow = existing != null ? existing : new LessonNote();
                            if (existing == null) {
                                noteToShow.setDate(dateIso);
                                noteToShow.setLessonNumber(lessonNum);
                                noteToShow.setSubjectName(subject != null ? subject : "");
                            }
                            noteToShow.setImagePath(localPath);

                            ScheduleItem item = new ScheduleItem();
                            item.setLessonNumber(lessonNum);
                            item.setSubjectName(subject != null ? subject : "");

                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                try {
                                    LocalDate parsedDate = LocalDate.parse(dateIso);
                                    showLessonNoteDialog(item, parsedDate, noteToShow);
                                } catch (Exception ignored) { }
                            });
                        });
                    }
                }
        );

        pagerAdapter = new DayPagerAdapter();
        pagerAdapter.setSemesterPublished(SemesterManager.SEMESTER_FALL, repo.isSemesterPublished(SemesterManager.SEMESTER_FALL));
        pagerAdapter.setSemesterPublished(SemesterManager.SEMESTER_SPRING, repo.isSemesterPublished(SemesterManager.SEMESTER_SPRING));
        pagerAdapter.setExcludedSubjects(excludedSubjectsManager.getExcludedSubjects(selName));
        pagerAdapter.setSkippedLessonsManager(skippedLessonsManager);
        pagerAdapter.setAttendanceManager(attendanceManager);
        pagerAdapter.setOnLessonClickListener(this::showLessonNoteDialog);
        pagerAdapter.setOnLessonLongClickListener(this::showSkipLessonDialog);
        pagerAdapter.setOnTeacherClickListener(teacherName ->
                RoomTeacherFinderDialog.showForTeacher(this, teacherName));
        pagerAdapter.setOnRoomClickListener(rawRoom ->
                BuildingInfoDialog.show(this, rawRoom));
        pagerAdapter.setOnSemesterCheckListener(semId -> {
            swipeRefresh.setRefreshing(true);
            refresh();
        });

        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(2);
        viewPager.setCurrentItem(DayPagerAdapter.positionOf(activeSemesterId, weekNum, dayIndex), false);

        buildDayTabs();
        selectPage(activeSemesterId, weekNum, dayIndex, false);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab t) {
                if (suppressTabCallback) return;
                dayIndex = t.getPosition();
                viewPager.setCurrentItem(DayPagerAdapter.positionOf(activeSemesterId, weekNum, dayIndex), true);
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
                String newSem = DayPagerAdapter.semesterOf(position);
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

                boolean semChanged = !newSem.equals(activeSemesterId);
                if (semChanged) {
                    switchActiveSemester(newSem, true);
                }

                if (semChanged || newWeek != weekNum) {
                    weekNum = newWeek;
                    currentWeek = weekController.getCachedWeek(weekNum, activeSemesterId);
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
                currentWeek = weekController.getCachedWeek(weekNum, activeSemesterId);
                updateWeekHeaderUi();
                updateDayTabsForWeek();
                selectPage(activeSemesterId, weekNum, 0, true);
                loadWeek();
            } else if (SemesterManager.SEMESTER_SPRING.equals(activeSemesterId)) {
                switchActiveSemester(SemesterManager.SEMESTER_FALL, true);
                weekNum = DayPagerAdapter.WEEK_COUNT;
                dayIndex = 0;
                currentWeek = weekController.getCachedWeek(weekNum, activeSemesterId);
                updateWeekHeaderUi();
                updateDayTabsForWeek();
                selectPage(activeSemesterId, weekNum, 0, true);
                loadWeek();
            }
        });
        btnNext.setOnClickListener(v -> {
            if (weekNum < DayPagerAdapter.WEEK_COUNT) {
                weekNum++;
                dayIndex = 0;
                currentWeek = weekController.getCachedWeek(weekNum, activeSemesterId);
                updateWeekHeaderUi();
                updateDayTabsForWeek();
                selectPage(activeSemesterId, weekNum, 0, true);
                loadWeek();
            } else if (SemesterManager.SEMESTER_FALL.equals(activeSemesterId)) {
                switchActiveSemester(SemesterManager.SEMESTER_SPRING, true);
                weekNum = 1;
                dayIndex = 0;
                currentWeek = weekController.getCachedWeek(weekNum, activeSemesterId);
                updateWeekHeaderUi();
                updateDayTabsForWeek();
                selectPage(activeSemesterId, weekNum, 0, true);
                loadWeek();
            }
        });

        updateLastUpdatedBanner();
        updateFavoritesBar();
        AppShortcutsHelper.syncDynamicShortcutsAsync(this);
        loadWeek();

        if (s == null && AppShortcutsHelper.hasShortcutRequest(getIntent())) {
            final Intent launchIntent = getIntent();
            viewPager.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    handleShortcutIntent(launchIntent);
                }
            });
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShortcutIntent(intent);
    }

    private void handleShortcutIntent(Intent intent) {
        String action = AppShortcutsHelper.extractShortcutAction(intent);
        if (action == null || isFinishing() || isDestroyed()) return;

        // Потребляем действие один раз, чтобы оно не повторялось при пересоздании Activity
        intent.removeExtra(AppShortcutsHelper.EXTRA_SHORTCUT_ACTION);
        if (AppShortcutsHelper.isShortcutAction(intent.getAction())) {
            intent.setAction(null);
        }

        switch (action) {
            case AppShortcutsHelper.ACTION_FREE_ROOMS:
                RoomTeacherFinderDialog.showFreeRooms(this);
                break;
            case AppShortcutsHelper.ACTION_FIND_TEACHER:
                RoomTeacherFinderDialog.show(this);
                break;
            case AppShortcutsHelper.ACTION_HOMEWORK:
                HomeworkHubDialog.show(this, () -> {
                    clearWeekCache();
                    loadWeek();
                    updateDockMoreBadge();
                }, this::navigateToDate);
                break;
            case AppShortcutsHelper.ACTION_OPEN_FAVORITE: {
                String favType = intent.getStringExtra(AppShortcutsHelper.EXTRA_FAVORITE_TYPE);
                String favName = intent.getStringExtra(AppShortcutsHelper.EXTRA_FAVORITE_NAME);
                if (favName != null && !favName.trim().isEmpty()) {
                    String resolvedType = (favType != null && !favType.isEmpty())
                            ? favType : GroupOrTeacher.TYPE_GROUP;
                    if (switchSelection(resolvedType, favName.trim())) {
                        Toast.makeText(this, "★ " + favName.trim(), Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    private boolean switchSelection(String newType, String newName) {
        if (newName == null || newName.trim().isEmpty()) return false;
        String trimmedName = newName.trim();
        String resolvedType = (newType != null && !newType.isEmpty())
                ? newType : GroupOrTeacher.TYPE_GROUP;
        if (trimmedName.equalsIgnoreCase(selName) && resolvedType.equals(selType)) {
            return false;
        }
        selType = resolvedType;
        selName = trimmedName;
        repo.saveSelection(selType, selName);
        if (excludedSubjectsManager != null) {
            pagerAdapter.setExcludedSubjects(excludedSubjectsManager.getExcludedSubjects(selName));
        }
        tvSelectionName.setText(selName);
        tvSelectionType.setText(GroupOrTeacher.TYPE_GROUP.equals(selType) ? "Группа" : "Преподаватель");
        clearWeekCache();
        loadWeek();
        updateFavoritesBar();
        if (attendanceManager != null) {
            attendanceManager.invalidateCache();
        }
        WidgetUpdateHelper.updateAllWidgets(this);
        ReminderScheduler.rescheduleAll(this);
        LiveLessonService.syncNow(this);
        return true;
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
        GlassSurfaceHelper.applyGlassCard(semesterToastPill, 999f, true);
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
        LocalDate today = DateUtils.todayMoscow();
        String windowSem = SemesterManager.getCurrentWindowSemester(today);
        boolean semChanged = !windowSem.equals(activeSemesterId);
        if (semChanged) {
            switchActiveSemester(windowSem, true);
        }
        initWeekForSemester(activeSemesterId, today);
        currentWeek = weekController.getCachedWeek(weekNum, activeSemesterId);
        updateWeekHeaderUi();
        updateDayTabsForWeek();
        selectPage(activeSemesterId, weekNum, dayIndex, !semChanged);
        loadWeek();
        updateClock();
    }

    private void navigateToDate(LocalDate targetDate) {
        if (targetDate == null) return;
        String targetSem = SemesterManager.getCurrentWindowSemester(targetDate);
        WeekCalculator targetCalc = repo.getWeekCalculator(targetSem);
        int computedWeek = targetCalc != null ? targetCalc.getWeekNumber(targetDate) : 1;
        if (computedWeek < 1) {
            String otherSem = SemesterManager.SEMESTER_FALL.equals(targetSem)
                    ? SemesterManager.SEMESTER_SPRING : SemesterManager.SEMESTER_FALL;
            WeekCalculator otherCalc = repo.getWeekCalculator(otherSem);
            int otherWeek = otherCalc != null ? otherCalc.getWeekNumber(targetDate) : 0;
            if (otherWeek >= 1 && otherWeek <= DayPagerAdapter.WEEK_COUNT) {
                targetSem = otherSem;
                computedWeek = otherWeek;
            } else {
                computedWeek = 1;
            }
        }
        if (computedWeek > DayPagerAdapter.WEEK_COUNT) {
            computedWeek = DayPagerAdapter.WEEK_COUNT;
        }
        int dow = DateUtils.toScheduleDayOfWeek(targetDate);
        int targetDay = (dow >= 1 && dow <= 6) ? dow - 1 : 0;

        boolean semChanged = !targetSem.equals(activeSemesterId);
        if (semChanged) {
            switchActiveSemester(targetSem, true);
        }
        weekNum = computedWeek;
        dayIndex = targetDay;
        currentWeek = weekController.getCachedWeek(weekNum, activeSemesterId);
        updateWeekHeaderUi();
        updateDayTabsForWeek();
        int targetPos = DayPagerAdapter.positionOf(activeSemesterId, weekNum, dayIndex);
        boolean smooth = viewPager != null && Math.abs(viewPager.getCurrentItem() - targetPos) <= 12;
        selectPage(activeSemesterId, weekNum, dayIndex, smooth);
        loadWeek();
        updateClock();
    }

    private void switchActiveSemester(@NonNull String semesterId, boolean showToast) {
        if (semesterId.equals(activeSemesterId)) return;
        activeSemesterId = semesterId;
        repo.setActiveSemesterId(activeSemesterId);
        calc = repo.getWeekCalculator(activeSemesterId);
        weekController.setSemesterId(activeSemesterId);
        if (showToast) {
            showSemesterToast(activeSemesterId);
        }
    }

    private void showSemesterToast(@NonNull String semesterId) {
        if (semesterToastPill == null || isFinishing() || isDestroyed()) return;
        semesterToastHandler.removeCallbacks(hideSemesterToastRunnable);
        semesterToastPill.animate().cancel();

        boolean isSpring = SemesterManager.SEMESTER_SPRING.equals(semesterId);
        String text = isSpring ? "🌸 Весенний семестр" : "🍂 Осенний семестр";
        if (!repo.isSemesterPublished(semesterId)) {
            text += " · нет расписания";
        }
        semesterToastPill.setText(text);
        GlassSurfaceHelper.applyGlassCard(semesterToastPill, 999f, true);

        semesterToastPill.setAlpha(0f);
        semesterToastPill.setTranslationY(-dpToPx(12));
        semesterToastPill.setVisibility(View.VISIBLE);
        semesterToastPill.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .start();
        semesterToastHandler.postDelayed(hideSemesterToastRunnable, 2500);
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
        if (isFinishing() || isDestroyed()) return;
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
        trackDialog(dialog);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        addMoreMenuItem(root, "➕", "Личное событие", null, () -> {
            dialog.dismiss();
            LocalDate currentPageDate = getCurrentPageDate();
            boolean isGroup = GroupOrTeacher.TYPE_GROUP.equals(selType);
            AlertDialog customDlg = CustomEventDialog.show(this, currentPageDate, selName, isGroup, () -> {
                clearWeekCache();
                loadWeek();
                WidgetUpdateHelper.updateAllWidgets(this);
                LiveLessonService.syncNow(this);
            });
            trackDialog(customDlg);
        });
        addMoreMenuItem(root, "⚖️", "Сравнить группы (Общие окна)", null, () -> {
            dialog.dismiss();
            CompareScheduleDialog.show(this, selName, null);
        });
        int pendingCount = (noteDao != null) ? noteDao.getPendingNotesCount() : 0;
        addMoreMenuItem(root, "📝", "Домашки и заметки", pendingCount > 0 ? String.valueOf(pendingCount) : null, () -> {
            dialog.dismiss();
            HomeworkHubDialog.show(this, () -> {
                clearWeekCache();
                loadWeek();
                updateDockMoreBadge();
            }, this::navigateToDate);
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
        trackDialog(dialog);

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
        trackDialog(dialog);

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

            chip.setOnClickListener(v -> switchSelection(fav.getType(), fav.getName()));

            chip.setOnLongClickListener(v -> {
                showFavoriteActionsDialog(fav);
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

    private void showFavoriteActionsDialog(GroupOrTeacher fav) {
        if (isFinishing() || isDestroyed()) return;
        LinearLayout root = createGlassMessageDialogRoot(
                fav.getName(),
                "Выберите действие для «" + fav.getName() + "»"
        );

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();
        trackDialog(dialog);

        if (!fav.getName().equalsIgnoreCase(selName)) {
            addMoreMenuItem(root, "⚖️", "Сравнить с текущей", null, () -> {
                dialog.dismiss();
                CompareScheduleDialog.show(this, selName, fav.getName());
            });
        }
        addMoreMenuItem(root, "🗑", "Удалить из избранного", null, () -> {
            FavoritesManager.removeFavorite(this, fav.getType(), fav.getName());
            updateFavoritesBar();
            dialog.dismiss();
            Toast.makeText(this, "Удалено из избранного", Toast.LENGTH_SHORT).show();
        });

        LinearLayout actions = createGlassDialogActionsRow();
        TextView btnCancel = createGlassDialogButton("Отмена", R.color.glass_text_secondary, false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(btnCancel);
        root.addView(actions);

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
                    pendingPhotoDateIso = date.toString();
                    pendingPhotoLessonNum = item.getLessonNumber();
                    pendingPhotoSubject = item.getSubjectName();
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
        if (item.isCustom()) {
            showDeleteCustomEventDialog(item);
            return;
        }
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

    private void showDeleteCustomEventDialog(ScheduleItem item) {
        if (isFinishing() || isDestroyed()) return;
        LinearLayout root = createGlassMessageDialogRoot(
                "Удалить личное событие?",
                "Удалить «" + (item.getSubjectName() != null ? item.getSubjectName() : "") + "»?"
        );
        LinearLayout actions = createGlassDialogActionsRow();
        TextView btnCancel = createGlassDialogButton("Отмена", R.color.glass_text_secondary, false);
        TextView btnDelete = createGlassDialogButton("Удалить", R.color.glass_danger, false);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        delLp.setMarginStart(dpToPx(10));
        btnDelete.setLayoutParams(delLp);
        actions.addView(btnCancel);
        actions.addView(btnDelete);
        root.addView(actions);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();
        trackDialog(dialog);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            long customId = item.getCustomId();
            ex.execute(() -> {
                repo.deleteCustomEvent(customId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    clearWeekCache();
                    loadWeek();
                    WidgetUpdateHelper.updateAllWidgets(this);
                    LiveLessonService.syncNow(this);
                    Toast.makeText(this, "Событие удалено", Toast.LENGTH_SHORT).show();
                });
            });
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, root);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
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

    private void initWeekForSemester(@NonNull String semesterId, LocalDate today) {
        String windowSem = SemesterManager.getCurrentWindowSemester(today);
        if (semesterId.equals(windowSem)) {
            initWeekForDate(today);
        } else {
            weekNum = 1;
            dayIndex = 0;
            builtForDate = today;
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

    private void selectPage(@NonNull String semesterId, int targetWeek, int targetDay, boolean smoothScroll) {
        if (targetWeek < 1) targetWeek = 1;
        if (targetWeek > DayPagerAdapter.WEEK_COUNT) targetWeek = DayPagerAdapter.WEEK_COUNT;
        if (targetDay < 0 || targetDay >= DayPagerAdapter.DAYS_PER_WEEK) targetDay = 0;
        final int targetPos = DayPagerAdapter.positionOf(semesterId, targetWeek, targetDay);
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
        String repoSem = repo.getActiveSemesterId();
        if (repoSem == null) repoSem = SemesterManager.SEMESTER_FALL;
        boolean semChanged = !repoSem.equals(activeSemesterId);
        activeSemesterId = repoSem;
        calc = repo.getWeekCalculator(activeSemesterId);
        weekController.setSemesterId(activeSemesterId);
        pagerAdapter.setSemesterPublished(SemesterManager.SEMESTER_FALL, repo.isSemesterPublished(SemesterManager.SEMESTER_FALL));
        pagerAdapter.setSemesterPublished(SemesterManager.SEMESTER_SPRING, repo.isSemesterPublished(SemesterManager.SEMESTER_SPRING));
        if (builtForDate == null || !builtForDate.equals(today) || semChanged) {
            initWeekForSemester(activeSemesterId, today);
            tvTodayDate.setText(DateUtils.formatDisplayDateShort(today));
            clearWeekCache();
            selectPage(activeSemesterId, weekNum, dayIndex, false);
            loadWeek();
        } else {
            updateWeekHeaderUi();
            updateDayTabsForWeek();
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
        LocalDate mon = (days != null && !days.isEmpty()) ? days.get(0).getDate() : calc.getMondayOfWeek(weekNum);
        LocalDate sat = (days != null && days.size() >= 6) ? days.get(5).getDate() : calc.getSaturdayOfWeek(weekNum);
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
                Drawable badgeBg = tvBadge.getBackground();
                if ("Сегодня".equals(label)) {
                    if (badgeBg != null) badgeBg.mutate().setTint(ContextCompat.getColor(this, R.color.badge_today_bg));
                    tvBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_today_text));
                } else if ("Завтра".equals(label)) {
                    if (badgeBg != null) badgeBg.mutate().setTint(ContextCompat.getColor(this, R.color.badge_tomorrow_bg));
                    tvBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_tomorrow_text));
                } else {
                    int accent = ContextCompat.getColor(this, R.color.glass_accent);
                    if (badgeBg != null) {
                        badgeBg.mutate().setTint(
                                androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 34));
                    }
                    tvBadge.setTextColor(accent);
                }
            }
        }
        updateTabSelectionVisuals();
    }

    private void updateWeekHeaderUi() {
        String semLabel = SemesterManager.SEMESTER_SPRING.equals(activeSemesterId) ? "Весна" : "Осень";
        String label = (weekNum % 2 == 0) ? "Чётная" : "Нечётная";
        LocalDate mon = calc.getMondayOfWeek(weekNum);
        LocalDate sat = calc.getSaturdayOfWeek(weekNum);
        String range = (mon != null && sat != null) ? DateUtils.formatDisplayDate(mon) + " – " + DateUtils.formatDisplayDate(sat) : "";
        tvWeekInfo.setText(semLabel + " · Неделя " + weekNum + " (" + label + ")  " + range);
        boolean isFall = SemesterManager.SEMESTER_FALL.equals(activeSemesterId);
        boolean isSpring = SemesterManager.SEMESTER_SPRING.equals(activeSemesterId);
        btnPrev.setAlpha(!(isFall && weekNum == 1) ? 1f : 0.3f);
        btnNext.setAlpha(!(isSpring && weekNum == DayPagerAdapter.WEEK_COUNT) ? 1f : 0.3f);
    }

    private void clearWeekCache() {
        weekController.clearCache();
        currentWeek = null;
        if (pagerAdapter != null) {
            pagerAdapter.clearWeeks();
        }
    }

    private void loadWeek() {
        boolean fallPub = repo.isSemesterPublished(SemesterManager.SEMESTER_FALL);
        boolean springPub = repo.isSemesterPublished(SemesterManager.SEMESTER_SPRING);
        pagerAdapter.setSemesterPublished(SemesterManager.SEMESTER_FALL, fallPub);
        pagerAdapter.setSemesterPublished(SemesterManager.SEMESTER_SPRING, springPub);
        updateWeekHeaderUi();

        if (!repo.isSemesterPublished(activeSemesterId)) {
            progressBar.setVisibility(View.GONE);
            currentWeek = null;
            updateDayTabsForWeek();
            prefetchCrossSemesterBoundary();
            return;
        }

        final String targetSem = activeSemesterId;
        boolean cachedHit = weekController.loadWeek(weekNum, targetSem, selType, selName, (loadedWeek, ws, notesMap, isTarget) -> {
            pagerAdapter.setWeekSchedule(targetSem, loadedWeek, ws, notesMap);
            if (loadedWeek == weekNum && targetSem.equals(activeSemesterId)) {
                currentWeek = ws;
                progressBar.setVisibility(View.GONE);
                updateDayTabsForWeek();
                updateClock();
                LiveLessonService.syncNow(this);
            }
        });

        if (cachedHit) {
            currentWeek = weekController.getCachedWeek(weekNum, targetSem);
            progressBar.setVisibility(View.GONE);
            updateDayTabsForWeek();
            updateClock();
        } else {
            progressBar.setVisibility(View.VISIBLE);
        }
        prefetchCrossSemesterBoundary();
    }

    private void prefetchCrossSemesterBoundary() {
        final String boundarySem;
        final int boundaryWeek;
        if (SemesterManager.SEMESTER_SPRING.equals(activeSemesterId) && weekNum == 1) {
            boundarySem = SemesterManager.SEMESTER_FALL;
            boundaryWeek = DayPagerAdapter.WEEK_COUNT;
        } else if (SemesterManager.SEMESTER_FALL.equals(activeSemesterId) && weekNum == DayPagerAdapter.WEEK_COUNT) {
            boundarySem = SemesterManager.SEMESTER_SPRING;
            boundaryWeek = 1;
        } else {
            return;
        }
        if (!repo.isSemesterPublished(boundarySem)) return;
        if (weekController.getCachedWeek(boundaryWeek, boundarySem) != null) return;

        final String targetType = selType;
        final String targetName = selName;
        ex.execute(() -> {
            WeekSchedule ws = GroupOrTeacher.TYPE_GROUP.equals(targetType)
                    ? repo.getWeekScheduleForGroup(targetName, boundaryWeek, boundarySem)
                    : repo.getWeekScheduleForTeacher(targetName, boundaryWeek, boundarySem);
            if (ws == null) return;
            java.util.Map<String, java.util.Map<Integer, LessonNote>> notesMap = new java.util.HashMap<>();
            if (ws.getDays() != null) {
                for (DaySchedule d : ws.getDays()) {
                    if (d.getDate() != null) {
                        String iso = d.getDate().toString();
                        notesMap.put(iso, repo.getNotesForDate(iso));
                    }
                }
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (targetName != null && !targetName.equals(selName)) return;
                if (targetType != null && !targetType.equals(selType)) return;
                pagerAdapter.setWeekSchedule(boundarySem, boundaryWeek, ws, notesMap);
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
        pagerAdapter.updateClockOnly(st, nextKey, next);
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
                    if (isFinishing() || isDestroyed()) return;
                    swipeRefresh.setRefreshing(false);
                    repo.refreshLogic();
                    activeSemesterId = repo.getActiveSemesterId();
                    if (activeSemesterId == null) {
                        activeSemesterId = SemesterManager.SEMESTER_FALL;
                    }
                    calc = repo.getWeekCalculator(activeSemesterId);
                    weekController.setSemesterId(activeSemesterId);
                    pagerAdapter.setSemesterPublished(SemesterManager.SEMESTER_FALL, repo.isSemesterPublished(SemesterManager.SEMESTER_FALL));
                    pagerAdapter.setSemesterPublished(SemesterManager.SEMESTER_SPRING, repo.isSemesterPublished(SemesterManager.SEMESTER_SPRING));
                    clearWeekCache();
                    if (repo.isSemesterPublished(activeSemesterId)) {
                        initWeekForSemester(activeSemesterId, DateUtils.todayMoscow());
                        selectPage(activeSemesterId, weekNum, dayIndex, false);
                    }
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
                    if (isFinishing() || isDestroyed()) return;
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

    private void trackDialog(Dialog dialog) {
        if (dialog == null) return;
        activeDialogs.add(dialog);
        dialog.setOnDismissListener(d -> activeDialogs.remove(dialog));
    }

    private void clearPendingPhotoTarget() {
        pendingPhotoDateIso = null;
        pendingPhotoLessonNum = -1;
        pendingPhotoSubject = null;
    }

    private LocalDate getCurrentPageDate() {
        if (currentWeek != null && currentWeek.getDays() != null
                && dayIndex >= 0 && dayIndex < currentWeek.getDays().size()) {
            LocalDate d = currentWeek.getDays().get(dayIndex).getDate();
            if (d != null) return d;
        }
        return DateUtils.todayMoscow();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingPhotoDateIso != null && pendingPhotoLessonNum > 0) {
            outState.putString(KEY_PENDING_PHOTO_DATE, pendingPhotoDateIso);
            outState.putInt(KEY_PENDING_PHOTO_LESSON, pendingPhotoLessonNum);
            outState.putString(KEY_PENDING_PHOTO_SUBJECT, pendingPhotoSubject);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockTick);
        semesterToastHandler.removeCallbacks(hideSemesterToastRunnable);
        if (semesterToastPill != null) {
            semesterToastPill.animate().cancel();
        }
        if (weekController != null) {
            weekController.destroy();
        }
        for (Dialog d : new ArrayList<>(activeDialogs)) {
            if (d != null && d.isShowing()) {
                try {
                    d.dismiss();
                } catch (Exception ignored) { }
            }
        }
        activeDialogs.clear();
        ex.shutdown();
    }
}
