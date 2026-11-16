package com.university.schedule.ui.dialog;

import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.FavoritesManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Liquid Glass диалог сравнения расписания двух групп и поиска общих окон (N5).
 */
public final class CompareScheduleDialog {

    private CompareScheduleDialog() {
    }

    @Nullable
    public static AlertDialog show(@NonNull AppCompatActivity activity,
                                   @Nullable String currentGroup,
                                   @Nullable String otherGroupOrNull) {
        if (activity.isFinishing() || activity.isDestroyed()) return null;

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_compare_schedule, null);
        View rootCard = view.findViewById(R.id.rootCompareGlassCard);

        TextView tvSubtitle = view.findViewById(R.id.tvCompareSubtitle);
        ImageButton btnCloseTop = view.findViewById(R.id.btnCloseCompareHeader);
        MaterialButton btnCloseBottom = view.findViewById(R.id.btnCloseCompareBottom);
        HorizontalScrollView scrollFavs = view.findViewById(R.id.scrollCompareFavChips);
        ChipGroup chipGroupFavs = view.findViewById(R.id.chipGroupCompareFavs);
        AutoCompleteTextView actvOtherGroup = view.findViewById(R.id.actvCompareOtherGroup);
        MaterialButton btnPrevWeek = view.findViewById(R.id.btnComparePrevWeek);
        MaterialButton btnNextWeek = view.findViewById(R.id.btnCompareNextWeek);
        TextView tvWeekLabel = view.findViewById(R.id.tvCompareWeekLabel);
        MaterialButtonToggleGroup toggleDow = view.findViewById(R.id.toggleCompareDow);
        TextView tvSummary = view.findViewById(R.id.tvCompareSummary);
        LinearLayout llRows = view.findViewById(R.id.llCompareRows);

        ScheduleRepository repo = ScheduleRepository.getInstance(activity);
        String resolvedCurrent = (currentGroup != null && !currentGroup.trim().isEmpty())
                ? currentGroup.trim()
                : repo.getSelectionName();
        if (resolvedCurrent == null) resolvedCurrent = "";
        final String baseGroup = resolvedCurrent;

        WeekCalculator wc = repo.getWeekCalculator();
        int initialWeek = wc != null ? wc.getCurrentWeekNumber() : 1;
        if (initialWeek < 1) initialWeek = 1;
        final int[] selectedWeek = new int[]{initialWeek};

        LocalDate today = DateUtils.todayMoscow();
        int initDow = DateUtils.toScheduleDayOfWeek(today);
        if (initDow < 1 || initDow > 6) initDow = 1;
        final int[] selectedDow = new int[]{initDow};
        final String[] selectedOther = new String[]{
                otherGroupOrNull != null ? otherGroupOrNull.trim() : ""
        };

        toggleDow.check(dowButtonId(selectedDow[0]));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .create();

        ExecutorService ex = Executors.newSingleThreadExecutor();
        dialog.setOnDismissListener(d -> ex.shutdown());

        btnCloseTop.setOnClickListener(v -> dialog.dismiss());
        btnCloseBottom.setOnClickListener(v -> dialog.dismiss());

        Runnable refreshComparison = () -> {
            int wk = selectedWeek[0];
            int dow = selectedDow[0];
            String other = selectedOther[0];

            boolean isEven = wc != null ? wc.isEvenWeek(wk) : (wk % 2 == 0);
            tvWeekLabel.setText("Неделя " + wk + " (" + (isEven ? "чётная" : "нечётная") + ")");

            if (baseGroup.isEmpty() || other.isEmpty()) {
                tvSubtitle.setText(baseGroup.isEmpty() ? "Выберите группы" : baseGroup + " ↔ выберите группу");
                tvSummary.setText("Выберите вторую группу для сравнения");
                llRows.removeAllViews();
                return;
            }

            tvSubtitle.setText(baseGroup + " ↔ " + other);
            tvSummary.setText("Загрузка...");

            ex.execute(() -> {
                WeekSchedule w1 = repo.getWeekScheduleForGroup(baseGroup, wk);
                WeekSchedule w2 = repo.getWeekScheduleForGroup(other, wk);
                DaySchedule d1 = findDay(w1, dow);
                DaySchedule d2 = findDay(w2, dow);

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;
                    renderComparison(activity, baseGroup, other, dow, d1, d2, tvSummary, llRows);
                });
            });
        };

        // Load groups & favorites
        ex.execute(() -> {
            List<String> allGroupsRaw = repo.getAllGroups();
            List<String> otherGroups = new ArrayList<>();
            for (String g : allGroupsRaw) {
                if (g != null && !g.equalsIgnoreCase(baseGroup)) {
                    otherGroups.add(g);
                }
            }
            List<GroupOrTeacher> favs = FavoritesManager.getFavorites(activity);
            List<String> favGroups = new ArrayList<>();
            for (GroupOrTeacher item : favs) {
                if (item.isGroup() && !item.getName().equalsIgnoreCase(baseGroup)) {
                    favGroups.add(item.getName());
                }
            }

            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        activity, android.R.layout.simple_dropdown_item_1line, otherGroups);
                actvOtherGroup.setAdapter(adapter);

                if (!favGroups.isEmpty()) {
                    scrollFavs.setVisibility(View.VISIBLE);
                    chipGroupFavs.removeAllViews();
                    for (String fg : favGroups) {
                        Chip chip = new Chip(activity);
                        chip.setText(fg);
                        chip.setCheckable(true);
                        chip.setChecked(fg.equalsIgnoreCase(selectedOther[0]));
                        chip.setOnClickListener(v -> {
                            selectedOther[0] = fg;
                            actvOtherGroup.setText(fg, false);
                            refreshComparison.run();
                        });
                        chipGroupFavs.addView(chip);
                    }
                }

                if (selectedOther[0].isEmpty()) {
                    if (!favGroups.isEmpty()) {
                        selectedOther[0] = favGroups.get(0);
                    } else if (!otherGroups.isEmpty()) {
                        selectedOther[0] = otherGroups.get(0);
                    }
                }
                if (!selectedOther[0].isEmpty()) {
                    actvOtherGroup.setText(selectedOther[0], false);
                }
                refreshComparison.run();
            });
        });

        actvOtherGroup.setOnItemClickListener((parent, v, pos, id) -> {
            Object item = parent.getItemAtPosition(pos);
            if (item != null) {
                selectedOther[0] = item.toString();
                refreshComparison.run();
            }
        });

        btnPrevWeek.setOnClickListener(v -> {
            if (selectedWeek[0] > 1) {
                selectedWeek[0]--;
                refreshComparison.run();
            }
        });

        btnNextWeek.setOnClickListener(v -> {
            selectedWeek[0]++;
            refreshComparison.run();
        });

        toggleDow.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            selectedDow[0] = dowFromButtonId(checkedId);
            refreshComparison.run();
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, rootCard);

        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            window.setLayout((int) (dm.widthPixels * 0.94f), (int) (dm.heightPixels * 0.86f));
        }

        return dialog;
    }

    @Nullable
    private static DaySchedule findDay(@Nullable WeekSchedule ws, int dow) {
        if (ws == null || ws.getDays() == null) return null;
        for (DaySchedule d : ws.getDays()) {
            if (d.getDayOfWeek() == dow) return d;
        }
        return null;
    }

    private static void renderComparison(AppCompatActivity activity,
                                         String group1,
                                         String group2,
                                         int dow,
                                         @Nullable DaySchedule d1,
                                         @Nullable DaySchedule d2,
                                         TextView tvSummary,
                                         LinearLayout llRows) {
        llRows.removeAllViews();

        Map<Integer, String> map1 = mapLessonsBySlot(d1);
        Map<Integer, String> map2 = mapLessonsBySlot(d2);

        int commonWindows = 0;
        int bothBusy = 0;
        int oneBusy = 0;

        for (int slot = 1; slot <= 7; slot++) {
            String s1 = map1.get(slot);
            String s2 = map2.get(slot);
            boolean busy1 = s1 != null && !s1.isEmpty();
            boolean busy2 = s2 != null && !s2.isEmpty();

            if (!busy1 && !busy2) {
                commonWindows++;
            } else if (busy1 && busy2) {
                bothBusy++;
            } else {
                oneBusy++;
            }

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(activity, 8);
            row.setLayoutParams(lp);
            GlassSurfaceHelper.applyGlassCard(row, 14f, !busy1 && !busy2);

            String timeLabel = Constants.getLessonTimeLabel(dow, slot);

            TextView tvHeader = new TextView(activity);
            tvHeader.setTextSize(13f);
            tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);

            if (!busy1 && !busy2) {
                tvHeader.setText("🟢 Общее окно · " + slot + " пара (" + timeLabel + ")");
                tvHeader.setTextColor(ContextCompat.getColor(activity, R.color.glass_success));
            } else if (busy1 && busy2) {
                tvHeader.setText("🔵 Заняты оба · " + slot + " пара (" + timeLabel + ")");
                tvHeader.setTextColor(ContextCompat.getColor(activity, R.color.glass_accent));
            } else {
                tvHeader.setText("🟡 Занята одна группа · " + slot + " пара (" + timeLabel + ")");
                tvHeader.setTextColor(ContextCompat.getColor(activity, R.color.glass_warning));
            }
            row.addView(tvHeader);

            if (busy1 || busy2) {
                TextView tvDetails = new TextView(activity);
                tvDetails.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_primary));
                tvDetails.setTextSize(12.5f);
                tvDetails.setPadding(0, dp(activity, 4), 0, 0);

                StringBuilder sb = new StringBuilder();
                if (busy1) {
                    sb.append(group1).append(": ").append(s1);
                } else {
                    sb.append(group1).append(": свободно");
                }
                sb.append("\n");
                if (busy2) {
                    sb.append(group2).append(": ").append(s2);
                } else {
                    sb.append(group2).append(": свободно");
                }
                tvDetails.setText(sb.toString());
                row.addView(tvDetails);
            }

            llRows.addView(row);
        }

        String dayName = (dow >= 1 && dow <= 6) ? Constants.DAY_NAMES_SHORT[dow - 1] : "";
        tvSummary.setText(dayName + " · общих окон: " + commonWindows
                + " · заняты оба: " + bothBusy
                + " · одна группа: " + oneBusy);
    }

    private static Map<Integer, String> mapLessonsBySlot(@Nullable DaySchedule day) {
        Map<Integer, String> map = new HashMap<>();
        if (day == null || day.isDayOff() || day.getLessons() == null) {
            return map;
        }
        for (ScheduleItem item : day.getLessons()) {
            if (item == null || item.isCancelled()) continue;
            int slot = item.getLessonNumber();
            if (slot < 1 || slot > 7) continue;
            String subj = item.getSubjectName() != null ? item.getSubjectName().trim() : "";
            if (subj.isEmpty()) continue;
            String room = item.getRoom() != null && !item.getRoom().trim().isEmpty()
                    ? " (" + item.getRoom().trim() + ")"
                    : "";
            String entry = subj + room;
            String existing = map.get(slot);
            map.put(slot, existing == null ? entry : existing + "; " + entry);
        }
        return map;
    }

    private static int dowButtonId(int dow) {
        switch (dow) {
            case 2: return R.id.btnCmpDow2;
            case 3: return R.id.btnCmpDow3;
            case 4: return R.id.btnCmpDow4;
            case 5: return R.id.btnCmpDow5;
            case 6: return R.id.btnCmpDow6;
            default: return R.id.btnCmpDow1;
        }
    }

    private static int dowFromButtonId(int id) {
        if (id == R.id.btnCmpDow2) return 2;
        if (id == R.id.btnCmpDow3) return 3;
        if (id == R.id.btnCmpDow4) return 4;
        if (id == R.id.btnCmpDow5) return 5;
        if (id == R.id.btnCmpDow6) return 6;
        return 1;
    }

    private static int dp(AppCompatActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
