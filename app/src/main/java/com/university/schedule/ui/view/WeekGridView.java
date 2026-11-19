package com.university.schedule.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.university.schedule.R;
import com.university.schedule.logic.SemesterManager;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.logic.WeekGridHelper;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.LessonNote;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.AttendanceManager;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.RoomFormatter;
import com.university.schedule.util.ScheduleClock;
import com.university.schedule.util.SkippedLessonsManager;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compact Liquid Glass 6-day (ПН–СБ) weekly overview grid.
 * Displays sticky day headers, a left time rail (slots 1..6/7),
 * color-coded translucent lesson tiles with LIVE/skipped/note indicators,
 * clean transparent free windows, and a bottom weekly summary strip.
 */
public class WeekGridView extends FrameLayout {

    public interface OnDayHeaderClickListener {
        void onDayHeaderClicked(int dayIndex);
    }

    public interface OnLessonCellClickListener {
        void onLessonCellClicked(@NonNull ScheduleItem item, @Nullable LocalDate date, @Nullable LessonNote note, int dayIndex);
    }

    public interface OnLessonCellLongClickListener {
        void onLessonCellLongClicked(@NonNull ScheduleItem item, @Nullable LocalDate date);
    }

    public interface OnUnpublishedCheckListener {
        void onCheckSemesterNow(@NonNull String semesterId);
    }

    private static final int DAYS_COUNT = 6;
    private static final int RAIL_WIDTH_DP = 34;

    private LinearLayout contentContainer;
    private LinearLayout headerRow;
    private ScrollView gridScrollView;
    private LinearLayout scrollBody;
    private LinearLayout gridRowsContainer;
    private LinearLayout summaryCard;
    private TextView tvSummaryMain;
    private TextView tvSummarySecondary;

    private FrameLayout unpublishedOverlay;
    private LinearLayout unpublishedCard;
    private TextView tvUnpublishedTitle;
    private TextView tvUnpublishedSubtitle;
    private TextView btnCheckSemesterNow;

    private OnDayHeaderClickListener dayHeaderClickListener;
    private OnLessonCellClickListener lessonCellClickListener;
    private OnLessonCellLongClickListener lessonCellLongClickListener;
    private OnUnpublishedCheckListener unpublishedCheckListener;

    private Set<String> excludedSubjects = new HashSet<>();
    private SkippedLessonsManager skippedLessonsManager;
    private boolean fallPublished = true;
    private boolean springPublished = true;
    private int bottomInsetPx = 0;

    private WeekSchedule currentWeek;
    private Map<String, Map<Integer, LessonNote>> notesByDate = new HashMap<>();
    private ScheduleClock.State clockState;
    private String currentSemesterId = SemesterManager.SEMESTER_FALL;
    private int currentWeekNumber = 1;
    private WeekCalculator currentCalc;

    private final List<ValueAnimator> activeAnimators = new ArrayList<>();

    public WeekGridView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public WeekGridView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WeekGridView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context ctx) {
        contentContainer = new LinearLayout(ctx);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Sticky header row (time corner + ПН..СБ)
        headerRow = new LinearLayout(ctx);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        int hPad = dp(6);
        headerRow.setPadding(hPad, dp(4), hPad, dp(4));
        contentContainer.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Scrollable grid + summary
        gridScrollView = new ScrollView(ctx);
        gridScrollView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        gridScrollView.setVerticalScrollBarEnabled(false);
        gridScrollView.setClipToPadding(false);

        scrollBody = new LinearLayout(ctx);
        scrollBody.setOrientation(LinearLayout.VERTICAL);
        scrollBody.setPadding(hPad, dp(2), hPad, dp(16));

        gridRowsContainer = new LinearLayout(ctx);
        gridRowsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollBody.addView(gridRowsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Compact bottom summary strip
        summaryCard = new LinearLayout(ctx);
        summaryCard.setOrientation(LinearLayout.HORIZONTAL);
        summaryCard.setGravity(Gravity.CENTER_VERTICAL);
        summaryCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sumLp.topMargin = dp(8);
        sumLp.leftMargin = dp(2);
        sumLp.rightMargin = dp(2);
        summaryCard.setLayoutParams(sumLp);

        tvSummaryMain = new TextView(ctx);
        tvSummaryMain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tvSummaryMain.setTypeface(null, Typeface.BOLD);
        tvSummaryMain.setMaxLines(1);
        tvSummaryMain.setEllipsize(TextUtils.TruncateAt.END);
        summaryCard.addView(tvSummaryMain, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        tvSummarySecondary = new TextView(ctx);
        tvSummarySecondary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tvSummarySecondary.setTypeface(null, Typeface.BOLD);
        tvSummarySecondary.setMaxLines(1);
        tvSummarySecondary.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        secLp.setMarginStart(dp(8));
        summaryCard.addView(tvSummarySecondary, secLp);

        scrollBody.addView(summaryCard);
        gridScrollView.addView(scrollBody, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        contentContainer.addView(gridScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        addView(contentContainer);

        // Unpublished semester overlay
        unpublishedOverlay = new FrameLayout(ctx);
        unpublishedOverlay.setVisibility(GONE);
        unpublishedOverlay.setPadding(dp(20), dp(24), dp(20), dp(24));

        unpublishedCard = new LinearLayout(ctx);
        unpublishedCard.setOrientation(LinearLayout.VERTICAL);
        unpublishedCard.setGravity(Gravity.CENTER_HORIZONTAL);
        unpublishedCard.setPadding(dp(22), dp(24), dp(22), dp(24));

        tvUnpublishedTitle = new TextView(ctx);
        tvUnpublishedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        tvUnpublishedTitle.setTypeface(null, Typeface.BOLD);
        tvUnpublishedTitle.setGravity(Gravity.CENTER);
        unpublishedCard.addView(tvUnpublishedTitle);

        tvUnpublishedSubtitle = new TextView(ctx);
        tvUnpublishedSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tvUnpublishedSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subLp.topMargin = dp(8);
        unpublishedCard.addView(tvUnpublishedSubtitle, subLp);

        btnCheckSemesterNow = new TextView(ctx);
        btnCheckSemesterNow.setText(R.string.week_grid_check_now);
        btnCheckSemesterNow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btnCheckSemesterNow.setTypeface(null, Typeface.BOLD);
        btnCheckSemesterNow.setPadding(dp(18), dp(9), dp(18), dp(9));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnLp.topMargin = dp(16);
        unpublishedCard.addView(btnCheckSemesterNow, btnLp);

        LayoutParams cardLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        unpublishedOverlay.addView(unpublishedCard, cardLp);
        addView(unpublishedOverlay, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    public void setOnDayHeaderClickListener(@Nullable OnDayHeaderClickListener listener) {
        this.dayHeaderClickListener = listener;
    }

    public void setOnLessonCellClickListener(@Nullable OnLessonCellClickListener listener) {
        this.lessonCellClickListener = listener;
    }

    public void setOnLessonCellLongClickListener(@Nullable OnLessonCellLongClickListener listener) {
        this.lessonCellLongClickListener = listener;
    }

    public void setOnUnpublishedCheckListener(@Nullable OnUnpublishedCheckListener listener) {
        this.unpublishedCheckListener = listener;
    }

    public void setExcludedSubjects(@Nullable Set<String> subjects) {
        this.excludedSubjects = subjects != null ? subjects : new HashSet<>();
    }

    public void setSkippedLessonsManager(@Nullable SkippedLessonsManager manager) {
        this.skippedLessonsManager = manager;
    }

    public void setSemesterPublished(@NonNull String semesterId, boolean published) {
        if (SemesterManager.SEMESTER_SPRING.equals(semesterId)) {
            springPublished = published;
        } else {
            fallPublished = published;
        }
    }

    private boolean isSemesterPublished(@NonNull String semesterId) {
        return SemesterManager.SEMESTER_SPRING.equals(semesterId) ? springPublished : fallPublished;
    }

    public void setBottomInsetPx(int bottomInsetPx) {
        this.bottomInsetPx = Math.max(0, bottomInsetPx);
        if (scrollBody != null) {
            scrollBody.setPadding(
                    scrollBody.getPaddingLeft(),
                    scrollBody.getPaddingTop(),
                    scrollBody.getPaddingRight(),
                    dp(16) + this.bottomInsetPx
            );
        }
    }

    public boolean canScrollUp() {
        return gridScrollView != null
                && contentContainer.getVisibility() == VISIBLE
                && gridScrollView.canScrollVertically(-1);
    }

    public void bindWeek(
            @Nullable WeekSchedule week,
            @Nullable Map<String, Map<Integer, LessonNote>> notesByDate,
            @Nullable ScheduleClock.State clockState,
            @NonNull String semesterId,
            int weekNumber,
            @Nullable WeekCalculator calc
    ) {
        this.currentWeek = week;
        this.notesByDate = notesByDate != null ? notesByDate : new HashMap<>();
        this.clockState = clockState;
        this.currentSemesterId = semesterId;
        this.currentWeekNumber = weekNumber;
        this.currentCalc = calc;
        render();
    }

    public void updateClock(@Nullable ScheduleClock.State clockState) {
        this.clockState = clockState;
        if (getVisibility() == VISIBLE) {
            render();
        }
    }

    public void refreshStyle() {
        if (getVisibility() == VISIBLE) {
            render();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAllAnimators();
        super.onDetachedFromWindow();
    }

    private void cancelAllAnimators() {
        for (ValueAnimator va : activeAnimators) {
            va.cancel();
        }
        activeAnimators.clear();
    }

    private void render() {
        cancelAllAnimators();
        Context ctx = getContext();
        boolean dark = isNightMode(ctx);
        boolean reduced = isReducedTransparency(ctx);

        if (!isSemesterPublished(currentSemesterId)) {
            contentContainer.setVisibility(GONE);
            unpublishedOverlay.setVisibility(VISIBLE);
            GlassSurfaceHelper.applyGlassCard(unpublishedCard, 24f, false);
            GlassSurfaceHelper.applyGlassCard(btnCheckSemesterNow, 999f, true);
            boolean isSpring = SemesterManager.SEMESTER_SPRING.equals(currentSemesterId);
            tvUnpublishedTitle.setTextColor(ContextCompat.getColor(ctx, R.color.glass_text_primary));
            tvUnpublishedSubtitle.setTextColor(ContextCompat.getColor(ctx, R.color.glass_text_secondary));
            btnCheckSemesterNow.setTextColor(ContextCompat.getColor(ctx, R.color.glass_accent));
            tvUnpublishedTitle.setText(isSpring
                    ? "Расписание на весенний семестр ещё не опубликовано"
                    : "Расписание на осенний семестр ещё не опубликовано");
            tvUnpublishedSubtitle.setText(isSpring
                    ? "Оно появится автоматически после 30 января, как только вуз выложит новый файл на сайте."
                    : "Оно появится автоматически после 25 августа, как только вуз выложит новый файл на сайте.");
            btnCheckSemesterNow.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (unpublishedCheckListener != null) {
                    unpublishedCheckListener.onCheckSemesterNow(currentSemesterId);
                }
            });
            return;
        }

        unpublishedOverlay.setVisibility(GONE);
        contentContainer.setVisibility(VISIBLE);

        LocalDate today = DateUtils.todayMoscow();
        List<DaySchedule> days = (currentWeek != null && currentWeek.getDays() != null)
                ? currentWeek.getDays() : null;
        LocalDate fallbackMonday = (currentCalc != null) ? currentCalc.getMondayOfWeek(currentWeekNumber) : null;

        LocalDate[] dayDates = new LocalDate[DAYS_COUNT];
        DaySchedule[] daySchedules = new DaySchedule[DAYS_COUNT];
        int todayCol = -1;

        for (int i = 0; i < DAYS_COUNT; i++) {
            DaySchedule ds = (days != null && i < days.size()) ? days.get(i) : null;
            daySchedules[i] = ds;
            LocalDate d = (ds != null && ds.getDate() != null)
                    ? ds.getDate()
                    : (fallbackMonday != null ? fallbackMonday.plusDays(i) : null);
            dayDates[i] = d;
            if (d != null && d.equals(today)) {
                todayCol = i;
            }
        }

        WeekGridHelper.WeekSummary summary = WeekGridHelper.computeSummary(
                currentWeek,
                excludedSubjects,
                (date, lessonNumber, subjectName) ->
                        skippedLessonsManager != null && skippedLessonsManager.isSkipped(date, lessonNumber, subjectName)
        );
        int maxSlots = summary.maxLessonSlot;

        renderHeaderRow(ctx, daySchedules, dayDates, todayCol);
        renderGridRows(ctx, daySchedules, dayDates, todayCol, maxSlots, dark, reduced);
        renderSummaryStrip(ctx, summary);
    }

    private void renderHeaderRow(
            @NonNull Context ctx,
            @NonNull DaySchedule[] daySchedules,
            @NonNull LocalDate[] dayDates,
            int todayCol
    ) {
        headerRow.removeAllViews();

        // Corner spacer above time rail
        TextView corner = new TextView(ctx);
        corner.setText("№");
        corner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        corner.setTypeface(null, Typeface.BOLD);
        corner.setGravity(Gravity.CENTER);
        corner.setTextColor(ContextCompat.getColor(ctx, R.color.glass_text_secondary));
        headerRow.addView(corner, new LinearLayout.LayoutParams(dp(RAIL_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT));

        int primaryText = ContextCompat.getColor(ctx, R.color.glass_text_primary);
        int secondaryText = ContextCompat.getColor(ctx, R.color.glass_text_secondary);
        int accentColor = ContextCompat.getColor(ctx, R.color.glass_accent);
        int warningColor = ContextCompat.getColor(ctx, R.color.glass_warning);

        for (int col = 0; col < DAYS_COUNT; col++) {
            final int dayIdx = col;
            DaySchedule ds = daySchedules[col];
            LocalDate date = dayDates[col];
            boolean isToday = (col == todayCol);
            boolean isHoliday = ds != null && ds.isDayOff();

            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(2), dp(5), dp(2), dp(5));
            cell.setClickable(true);
            cell.setFocusable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = dp(2);
            lp.rightMargin = dp(2);
            cell.setLayoutParams(lp);

            GlassSurfaceHelper.applyGlassCard(cell, 10f, isToday);

            TextView tvDay = new TextView(ctx);
            tvDay.setText(Constants.DAY_NAMES_SHORT[col]);
            tvDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            tvDay.setTypeface(null, Typeface.BOLD);
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setTextColor(isToday ? accentColor : (isHoliday ? warningColor : primaryText));
            cell.addView(tvDay);

            TextView tvDate = new TextView(ctx);
            String dateLabel = date != null ? String.valueOf(date.getDayOfMonth()) : "—";
            if (isHoliday) {
                dateLabel = dateLabel + " 🎉";
            }
            tvDate.setText(dateLabel);
            tvDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            tvDate.setFontFeatureSettings("tnum");
            tvDate.setGravity(Gravity.CENTER);
            tvDate.setMaxLines(1);
            tvDate.setTextColor(isToday ? primaryText : secondaryText);
            cell.addView(tvDate);

            cell.setContentDescription(Constants.DAY_NAMES_SHORT[col] + (date != null ? " " + DateUtils.formatDisplayDateShort(date) : ""));
            cell.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (dayHeaderClickListener != null) {
                    dayHeaderClickListener.onDayHeaderClicked(dayIdx);
                }
            });

            headerRow.addView(cell);
        }
    }

    private void renderGridRows(
            @NonNull Context ctx,
            @NonNull DaySchedule[] daySchedules,
            @NonNull LocalDate[] dayDates,
            int todayCol,
            int maxSlots,
            boolean dark,
            boolean reduced
    ) {
        gridRowsContainer.removeAllViews();

        String[][] weekdayTimes = Constants.getLessonTimes(1);
        int primaryText = ContextCompat.getColor(ctx, R.color.glass_text_primary);
        int secondaryText = ContextCompat.getColor(ctx, R.color.glass_text_secondary);
        int accentColor = ContextCompat.getColor(ctx, R.color.glass_accent);
        int warningColor = ContextCompat.getColor(ctx, R.color.glass_warning);
        int dangerColor = ContextCompat.getColor(ctx, R.color.glass_danger);
        int successColor = ContextCompat.getColor(ctx, R.color.glass_success);

        ScheduleClock.LessonKey currentKey = (clockState != null) ? clockState.current : null;
        int cellMinHeight = dp(maxSlots >= 7 ? 68 : 76);

        for (int slot = 1; slot <= maxSlots; slot++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.FILL_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowLp.bottomMargin = dp(4);
            row.setLayoutParams(rowLp);

            // Left time rail
            boolean isCurrentSlotToday = currentKey != null && todayCol >= 0 && currentKey.lesson == slot;
            LinearLayout rail = new LinearLayout(ctx);
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setGravity(Gravity.CENTER);
            rail.setMinimumHeight(cellMinHeight);
            rail.setPadding(dp(1), dp(4), dp(2), dp(4));

            TextView tvSlotNum = new TextView(ctx);
            tvSlotNum.setText(String.valueOf(slot));
            tvSlotNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            tvSlotNum.setTypeface(null, Typeface.BOLD);
            tvSlotNum.setGravity(Gravity.CENTER);
            tvSlotNum.setTextColor(isCurrentSlotToday ? accentColor : primaryText);
            rail.addView(tvSlotNum);

            TextView tvStart = new TextView(ctx);
            String startStr = (slot - 1 < weekdayTimes.length) ? weekdayTimes[slot - 1][0] : "";
            tvStart.setText(startStr);
            tvStart.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
            tvStart.setFontFeatureSettings("tnum");
            tvStart.setGravity(Gravity.CENTER);
            tvStart.setTextColor(isCurrentSlotToday ? accentColor : secondaryText);
            rail.addView(tvStart);

            row.addView(rail, new LinearLayout.LayoutParams(dp(RAIL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT));

            // 6 Day columns
            for (int col = 0; col < DAYS_COUNT; col++) {
                final int dayIdx = col;
                DaySchedule ds = daySchedules[col];
                LocalDate date = dayDates[col];
                boolean isToday = (col == todayCol);
                boolean isHoliday = ds != null && ds.isDayOff();

                FrameLayout slotWrapper = new FrameLayout(ctx);
                slotWrapper.setMinimumHeight(cellMinHeight);
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
                );
                cellLp.leftMargin = dp(2);
                cellLp.rightMargin = dp(2);
                slotWrapper.setLayoutParams(cellLp);

                if (isHoliday) {
                    if (slot == 1) {
                        slotWrapper.addView(createHolidayPill(ctx, ds, warningColor, dark, reduced));
                    } else if (isToday) {
                        slotWrapper.setBackground(createTodayColumnSlotBg(accentColor, dark));
                    }
                    row.addView(slotWrapper);
                    continue;
                }

                List<ScheduleItem> slotLessons = WeekGridHelper.findLessonsForSlot(ds, slot);
                ScheduleItem primary = WeekGridHelper.pickPrimaryLesson(slotLessons);

                if (primary == null) {
                    // Clean transparent free window (with very subtle today tint if today)
                    if (isToday) {
                        slotWrapper.setBackground(createTodayColumnSlotBg(accentColor, dark));
                    }
                    row.addView(slotWrapper);
                    continue;
                }

                // Populated lesson tile
                String subject = primary.getSubjectName() != null ? primary.getSubjectName().trim() : "";
                boolean isExempt = excludedSubjects != null && excludedSubjects.contains(subject);
                boolean isSkipped = skippedLessonsManager != null && date != null
                        && skippedLessonsManager.isSkipped(date, slot, subject);
                boolean isDimmed = primary.isCancelled() || isExempt || isSkipped;
                boolean isLive = !isDimmed
                        && currentKey != null
                        && currentKey.date != null
                        && date != null
                        && currentKey.date.equals(date)
                        && currentKey.lesson == slot;

                LessonNote note = null;
                if (date != null) {
                    Map<Integer, LessonNote> dayNotes = notesByDate.get(date.toString());
                    if (dayNotes != null) {
                        note = dayNotes.get(slot);
                    }
                }
                boolean hasNote = hasNoteContent(note);

                View tile = createLessonTile(
                        ctx, primary, slotLessons.size(), date, note, hasNote,
                        dayIdx, isLive, isSkipped, isDimmed,
                        dark, reduced, primaryText, accentColor, successColor, dangerColor
                );
                slotWrapper.addView(tile, new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                row.addView(slotWrapper);
            }

            gridRowsContainer.addView(row);
        }
    }

    private View createHolidayPill(
            @NonNull Context ctx,
            @NonNull DaySchedule ds,
            int warningColor,
            boolean dark,
            boolean reduced
    ) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(3), dp(6), dp(3), dp(6));

        int baseFill = ContextCompat.getColor(ctx,
                reduced ? R.color.glass_surface_fallback : R.color.glass_surface);
        int fill = ColorUtils.compositeColors(
                ColorUtils.setAlphaComponent(warningColor, dark ? 34 : 24), baseFill);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(10));
        bg.setColor(fill);
        bg.setStroke(dp(1), ColorUtils.setAlphaComponent(warningColor, dark ? 95 : 80));
        box.setBackground(bg);

        TextView tv = new TextView(ctx);
        String name = ds.getHolidayName() != null && !ds.getHolidayName().trim().isEmpty()
                ? ds.getHolidayName().trim() : "Выходной";
        tv.setText(name);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(3);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setTextColor(warningColor);
        box.addView(tv);

        return box;
    }

    private View createLessonTile(
            @NonNull Context ctx,
            @NonNull ScheduleItem item,
            int overlapCount,
            @Nullable LocalDate date,
            @Nullable LessonNote note,
            boolean hasNote,
            int dayIdx,
            boolean isLive,
            boolean isSkipped,
            boolean isDimmed,
            boolean dark,
            boolean reduced,
            int primaryText,
            int accentColor,
            int successColor,
            int dangerColor
    ) {
        int typeColor = resolveTypeColor(item, dark);

        FrameLayout tileRoot = new FrameLayout(ctx);
        tileRoot.setClickable(true);
        tileRoot.setFocusable(true);

        if (isLive) {
            GlassSurfaceHelper.applyLessonStateGlass(tileRoot, 10f, 1);
        } else {
            tileRoot.setBackground(createTintedTileBackground(ctx, typeColor, dark, reduced));
            tileRoot.setClipToOutline(true);
        }

        if (isDimmed) {
            tileRoot.setAlpha(0.48f);
        }

        // Top color accent strip (2.5dp) for immediate lesson-type scan
        View topStrip = new View(ctx);
        GradientDrawable stripBg = new GradientDrawable();
        stripBg.setShape(GradientDrawable.RECTANGLE);
        stripBg.setColor(isLive ? successColor : ColorUtils.setAlphaComponent(typeColor, dark ? 225 : 210));
        topStrip.setBackground(stripBg);
        tileRoot.addView(topStrip, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(2, Math.round(2.5f * getResources().getDisplayMetrics().density)),
                Gravity.TOP
        ));

        // Inner vertical content
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(4), dp(5), dp(4), dp(4));

        TextView tvSubject = new TextView(ctx);
        tvSubject.setText(item.getSubjectName() != null ? item.getSubjectName().trim() : "");
        tvSubject.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        tvSubject.setTypeface(null, Typeface.BOLD);
        tvSubject.setTextColor(primaryText);
        tvSubject.setMaxLines(3);
        tvSubject.setEllipsize(TextUtils.TruncateAt.END);
        tvSubject.setLineSpacing(0f, 0.94f);
        tvSubject.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        if (isDimmed) {
            tvSubject.setPaintFlags(tvSubject.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        LinearLayout.LayoutParams subjLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        body.addView(tvSubject, subjLp);

        // Bottom metadata row: room badge + optional +N overlap indicator
        String compactRoom = WeekGridHelper.formatCompactRoom(item.getRoom());
        if (!compactRoom.isEmpty() || overlapCount > 1 || isSkipped) {
            LinearLayout metaRow = new LinearLayout(ctx);
            metaRow.setOrientation(LinearLayout.HORIZONTAL);
            metaRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            metaLp.topMargin = dp(3);
            metaRow.setLayoutParams(metaLp);

            if (!compactRoom.isEmpty()) {
                RoomFormatter.RoomInfo ri = RoomFormatter.parse(item.getRoom(), dark);
                int roomColor = ri != null ? ri.color : typeColor;

                TextView tvRoom = new TextView(ctx);
                tvRoom.setText(compactRoom);
                tvRoom.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f);
                tvRoom.setTypeface(null, Typeface.BOLD);
                tvRoom.setMaxLines(1);
                tvRoom.setEllipsize(TextUtils.TruncateAt.END);
                tvRoom.setTextColor(roomColor);
                tvRoom.setPadding(dp(3), dp(1), dp(3), dp(1));

                GradientDrawable roomBg = new GradientDrawable();
                roomBg.setShape(GradientDrawable.RECTANGLE);
                roomBg.setCornerRadius(dp(5));
                roomBg.setColor(ColorUtils.setAlphaComponent(roomColor, dark ? 52 : 34));
                tvRoom.setBackground(roomBg);

                metaRow.addView(tvRoom, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ));
            } else {
                metaRow.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
            }

            if (isSkipped) {
                TextView tvNka = new TextView(ctx);
                tvNka.setText("Н");
                tvNka.setTextSize(TypedValue.COMPLEX_UNIT_SP, 7.5f);
                tvNka.setTypeface(null, Typeface.BOLD);
                tvNka.setTextColor(dangerColor);
                LinearLayout.LayoutParams nkaLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                nkaLp.setMarginStart(dp(2));
                metaRow.addView(tvNka, nkaLp);
            } else if (overlapCount > 1) {
                TextView tvExtra = new TextView(ctx);
                tvExtra.setText("+" + (overlapCount - 1));
                tvExtra.setTextSize(TypedValue.COMPLEX_UNIT_SP, 7.5f);
                tvExtra.setTypeface(null, Typeface.BOLD);
                tvExtra.setTextColor(typeColor);
                LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                exLp.setMarginStart(dp(2));
                metaRow.addView(tvExtra, exLp);
            }

            body.addView(metaRow);
        }

        tileRoot.addView(body, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Top-right micro indicators: LIVE pulsing dot and/or Note dot
        if (isLive || hasNote) {
            LinearLayout dots = new LinearLayout(ctx);
            dots.setOrientation(LinearLayout.HORIZONTAL);
            dots.setGravity(Gravity.CENTER_VERTICAL);

            if (hasNote) {
                View noteDot = new View(ctx);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor((note != null && note.isCompleted()) ? successColor : accentColor);
                noteDot.setBackground(dotBg);
                LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(5), dp(5));
                if (isLive) dLp.setMarginEnd(dp(2));
                dots.addView(noteDot, dLp);
            }

            if (isLive) {
                View liveDot = new View(ctx);
                GradientDrawable liveBg = new GradientDrawable();
                liveBg.setShape(GradientDrawable.OVAL);
                liveBg.setColor(successColor);
                liveDot.setBackground(liveBg);
                dots.addView(liveDot, new LinearLayout.LayoutParams(dp(6), dp(6)));

                ValueAnimator pulse = ValueAnimator.ofFloat(1f, 0.25f);
                pulse.setDuration(650);
                pulse.setRepeatMode(ValueAnimator.REVERSE);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.addUpdateListener(anim -> liveDot.setAlpha((float) anim.getAnimatedValue()));
                pulse.start();
                activeAnimators.add(pulse);
            }

            LayoutParams dotsLp = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END
            );
            dotsLp.topMargin = dp(4);
            dotsLp.rightMargin = dp(3);
            tileRoot.addView(dots, dotsLp);
        }

        tileRoot.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (lessonCellClickListener != null) {
                lessonCellClickListener.onLessonCellClicked(item, date, note, dayIdx);
            }
        });

        tileRoot.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (lessonCellLongClickListener != null) {
                lessonCellLongClickListener.onLessonCellLongClicked(item, date);
                return true;
            }
            return false;
        });

        return tileRoot;
    }

    private void renderSummaryStrip(@NonNull Context ctx, @NonNull WeekGridHelper.WeekSummary summary) {
        GlassSurfaceHelper.applyGlassCard(summaryCard, 16f, false);
        int primaryText = ContextCompat.getColor(ctx, R.color.glass_text_primary);
        int secondaryText = ContextCompat.getColor(ctx, R.color.glass_text_secondary);
        int accentColor = ContextCompat.getColor(ctx, R.color.glass_accent);

        tvSummaryMain.setTextColor(primaryText);
        tvSummarySecondary.setTextColor(secondaryText);

        if (summary.totalActiveLessons == 0) {
            tvSummaryMain.setText(R.string.week_grid_empty_week);
            tvSummarySecondary.setText(summary.holidayDaysCount > 0 ? "🎉 Выходные" : "");
            tvSummarySecondary.setVisibility(summary.holidayDaysCount > 0 ? VISIBLE : GONE);
            return;
        }

        tvSummaryMain.setText("📅 " + AttendanceManager.formatLessonsCount(summary.totalActiveLessons) + " за неделю");

        StringBuilder sec = new StringBuilder();
        if (summary.busiestDayIndex >= 0 && summary.busiestDayIndex < Constants.DAY_NAMES_SHORT.length) {
            sec.append("Пик: ")
                    .append(Constants.DAY_NAMES_SHORT[summary.busiestDayIndex])
                    .append(" (")
                    .append(summary.busiestDayCount)
                    .append(")");
        }
        if (summary.freeWindowsCount > 0) {
            if (sec.length() > 0) sec.append(" · ");
            sec.append("☕ ").append(summary.freeWindowsCount);
        }
        tvSummarySecondary.setText(sec.toString());
        tvSummarySecondary.setTextColor(accentColor);
        tvSummarySecondary.setVisibility(sec.length() > 0 ? VISIBLE : GONE);
    }

    private GradientDrawable createTintedTileBackground(
            @NonNull Context ctx,
            int typeColor,
            boolean dark,
            boolean reduced
    ) {
        int baseFill = ContextCompat.getColor(ctx,
                reduced ? R.color.glass_surface_fallback : R.color.glass_surface);
        int tintAlpha = dark ? (reduced ? 34 : 44) : (reduced ? 22 : 28);
        int fill = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(typeColor, tintAlpha), baseFill);
        int stroke = ColorUtils.setAlphaComponent(typeColor, dark ? 115 : 88);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(10));
        gd.setColor(fill);
        gd.setStroke(dp(1), stroke);
        return gd;
    }

    private GradientDrawable createTodayColumnSlotBg(int accentColor, boolean dark) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(10));
        gd.setColor(ColorUtils.setAlphaComponent(accentColor, dark ? 20 : 14));
        return gd;
    }

    private static int resolveTypeColor(@NonNull ScheduleItem item, boolean dark) {
        if (item.isCustom()) {
            return dark ? 0xFFA78BFA : 0xFF7C3AED;
        }
        String type = item.getLessonType();
        if (type == null) return dark ? 0xFF8A8E9E : 0xFF616161;
        String t = type.trim();
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_LECTURE)
                || t.equalsIgnoreCase(Constants.LESSON_TYPE_ONLINE_LECTURE)) {
            return dark ? 0xFF6C8CFF : 0xFF4361EE;
        }
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_PRACTICE)
                || t.equalsIgnoreCase(Constants.LESSON_TYPE_ONLINE_PRACTICE)) {
            return dark ? 0xFF34D399 : 0xFF059669;
        }
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_LAB)) {
            return dark ? 0xFFFBBF24 : 0xFFD97706;
        }
        if (t.equalsIgnoreCase(Constants.LESSON_TYPE_EXAM)) {
            return dark ? 0xFFF87171 : 0xFFDC2626;
        }
        return dark ? 0xFF8A8E9E : 0xFF616161;
    }

    private static boolean hasNoteContent(@Nullable LessonNote note) {
        if (note == null) return false;
        if (note.getNoteText() != null && !note.getNoteText().trim().isEmpty()) {
            return true;
        }
        return note.getImagePath() != null
                && !note.getImagePath().isEmpty()
                && new File(note.getImagePath()).exists();
    }

    private static boolean isNightMode(@NonNull Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static boolean isReducedTransparency(@NonNull Context ctx) {
        SharedPreferences glassPrefs = ctx.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE);
        if (glassPrefs.getBoolean("reduced_transparency", false)) return true;
        SharedPreferences appPrefs = ctx.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return appPrefs.getBoolean("reduced_transparency", false);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
