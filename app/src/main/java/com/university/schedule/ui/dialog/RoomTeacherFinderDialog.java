package com.university.schedule.ui.dialog;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.data.db.ScheduleDao;
import com.university.schedule.logic.SemesterManager;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.RoomFormatter;
import com.university.schedule.util.ScheduleClock;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Офлайн-поиск преподавателей ("Где преподаватель?") и свободных аудиторий (Feature #3).
 */
public final class RoomTeacherFinderDialog {

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");

    private RoomTeacherFinderDialog() {
    }

    public static void show(AppCompatActivity activity) {
        showInternal(activity, null, null, false);
    }

    public static void showFreeRooms(AppCompatActivity activity) {
        showInternal(activity, null, null, true);
    }

    public static void showForTeacher(AppCompatActivity activity, @Nullable String teacherName) {
        showInternal(activity, teacherName, null, false);
    }

    public static void showForBuilding(AppCompatActivity activity, @Nullable String buildingName) {
        showInternal(activity, null, buildingName, true);
    }

    private static void showInternal(AppCompatActivity activity,
                                     @Nullable String initialTeacher,
                                     @Nullable String initialBuilding,
                                     boolean startOnFreeRooms) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_room_teacher_finder, null);
        View rootCard = view.findViewById(R.id.rootGlassCard);

        TabLayout tabFinder = view.findViewById(R.id.tabFinder);
        View panelTeacher = view.findViewById(R.id.panelTeacherFinder);
        View panelRooms = view.findViewById(R.id.panelFreeRooms);

        EditText etTeacherSearch = view.findViewById(R.id.etTeacherSearch);
        LinearLayout llTeacherSuggestions = view.findViewById(R.id.llTeacherSuggestions);
        LinearLayout llTeacherResult = view.findViewById(R.id.llTeacherResult);
        TextView tvSelectedTeacherName = view.findViewById(R.id.tvSelectedTeacherName);
        TextView tvTeacherNowStatus = view.findViewById(R.id.tvTeacherNowStatus);
        LinearLayout llTeacherDayRows = view.findViewById(R.id.llTeacherDayRows);

        MaterialButtonToggleGroup toggleRoomWeekType = view.findViewById(R.id.toggleRoomWeekType);
        MaterialButtonToggleGroup toggleRoomDay = view.findViewById(R.id.toggleRoomDay);
        ChipGroup chipGroupBuilding = view.findViewById(R.id.chipGroupBuilding);
        MaterialButtonToggleGroup toggleRoomSlot = view.findViewById(R.id.toggleRoomSlot);
        TextView tvFreeRoomsSummary = view.findViewById(R.id.tvFreeRoomsSummary);
        LinearLayout llFreeRoomsList = view.findViewById(R.id.llFreeRoomsList);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .create();

        view.findViewById(R.id.btnCloseFinder).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnCloseFinderBottom).setOnClickListener(v -> dialog.dismiss());

        ExecutorService ex = Executors.newSingleThreadExecutor();
        dialog.setOnDismissListener(d -> ex.shutdown());

        ScheduleDao dao = new ScheduleDao(activity);
        ScheduleRepository repo = ScheduleRepository.getInstance(activity);
        final String activeSemId = repo.getActiveSemesterId() != null
                ? repo.getActiveSemesterId()
                : SemesterManager.SEMESTER_FALL;

        LocalDate today = DateUtils.todayMoscow();
        int rawDow = DateUtils.toScheduleDayOfWeek(today);
        final int queryDow = (rawDow >= 1 && rawDow <= 6) ? rawDow : 1;
        WeekCalculator wc = repo.getWeekCalculator(activeSemId);
        String wt = wc != null ? wc.getWeekTypeForDate(today) : Constants.WEEK_TYPE_ODD;
        final String queryWeekType = (wt != null) ? wt : Constants.WEEK_TYPE_ODD;

        toggleRoomWeekType.check(Constants.WEEK_TYPE_EVEN.equals(queryWeekType)
                ? R.id.btnWeekEven : R.id.btnWeekOdd);
        toggleRoomDay.check(dayButtonId(queryDow));

        final List<String> allTeachers = new ArrayList<>();
        final List<String> allRooms = new ArrayList<>();

        tabFinder.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean isTeacherTab = tab.getPosition() == 0;
                panelTeacher.setVisibility(isTeacherTab ? View.VISIBLE : View.GONE);
                panelRooms.setVisibility(isTeacherTab ? View.GONE : View.VISIBLE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        final String preselectedTeacher = initialTeacher != null ? initialTeacher.trim() : "";
        final boolean hasInitialTeacher = !preselectedTeacher.isEmpty();
        final boolean hasInitialBuilding = initialBuilding != null && !initialBuilding.trim().isEmpty();

        if (startOnFreeRooms || hasInitialBuilding) {
            TabLayout.Tab roomsTab = tabFinder.getTabAt(1);
            if (roomsTab != null) roomsTab.select();
            panelTeacher.setVisibility(View.GONE);
            panelRooms.setVisibility(View.VISIBLE);
            if (hasInitialBuilding) {
                chipGroupBuilding.check(chipIdForBuilding(initialBuilding));
            }
        }

        // Initial load of teachers & rooms
        ex.execute(() -> {
            List<String> teachers = dao.extractDistinctTeachers();
            List<String> rooms = dao.extractDistinctRooms(activeSemId);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;
                allTeachers.clear();
                allTeachers.addAll(teachers);
                allRooms.clear();
                allRooms.addAll(rooms);
                if (hasInitialTeacher) {
                    String resolved = resolveTeacherName(allTeachers, preselectedTeacher);
                    etTeacherSearch.setText(resolved);
                    etTeacherSearch.setSelection(resolved.length());
                    loadTeacherSchedule(activity, ex, dao, activeSemId, resolved, queryWeekType, queryDow, rawDow == 0,
                            llTeacherSuggestions, llTeacherResult, tvSelectedTeacherName,
                            tvTeacherNowStatus, llTeacherDayRows);
                } else {
                    renderTeacherSuggestions(activity, allTeachers, "", llTeacherSuggestions, teacher -> {
                        etTeacherSearch.setText(teacher);
                        loadTeacherSchedule(activity, ex, dao, activeSemId, teacher, queryWeekType, queryDow, rawDow == 0,
                                llTeacherSuggestions, llTeacherResult, tvSelectedTeacherName,
                                tvTeacherNowStatus, llTeacherDayRows);
                    });
                }
                refreshFreeRooms(activity, ex, dao, activeSemId, allRooms, toggleRoomWeekType, toggleRoomDay,
                        chipGroupBuilding, toggleRoomSlot, queryWeekType, queryDow, rawDow == 0,
                        tvFreeRoomsSummary, llFreeRoomsList);
            });
        });

        etTeacherSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(Editable s) {
                String q = s != null ? s.toString().trim() : "";
                llTeacherResult.setVisibility(View.GONE);
                llTeacherSuggestions.setVisibility(View.VISIBLE);
                renderTeacherSuggestions(activity, allTeachers, q, llTeacherSuggestions, teacher -> {
                    etTeacherSearch.setText(teacher);
                    loadTeacherSchedule(activity, ex, dao, activeSemId, teacher, queryWeekType, queryDow, rawDow == 0,
                            llTeacherSuggestions, llTeacherResult, tvSelectedTeacherName,
                            tvTeacherNowStatus, llTeacherDayRows);
                });
            }
        });

        // Default slot to current or next lesson (1..7)
        int defaultSlot = detectDefaultSlot();
        toggleRoomSlot.check(slotButtonId(defaultSlot));

        Runnable triggerRoomsRefresh = () -> refreshFreeRooms(
                activity, ex, dao, activeSemId, allRooms, toggleRoomWeekType, toggleRoomDay,
                chipGroupBuilding, toggleRoomSlot, queryWeekType, queryDow, rawDow == 0,
                tvFreeRoomsSummary, llFreeRoomsList);

        toggleRoomWeekType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) triggerRoomsRefresh.run();
        });

        toggleRoomDay.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) triggerRoomsRefresh.run();
        });

        chipGroupBuilding.setOnCheckedStateChangeListener((group, checkedIds) -> triggerRoomsRefresh.run());

        toggleRoomSlot.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) triggerRoomsRefresh.run();
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, rootCard);
        if (dialog.getWindow() != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            dialog.getWindow().setLayout((int) (dm.widthPixels * 0.92), (int) (dm.heightPixels * 0.84));
        }
    }

    private interface OnTeacherClickListener {
        void onTeacherClick(String teacher);
    }

    private static void renderTeacherSuggestions(AppCompatActivity activity, List<String> allTeachers,
                                                 String query, LinearLayout container,
                                                 OnTeacherClickListener listener) {
        container.removeAllViews();
        String low = query.toLowerCase(Locale.ROOT);
        int shown = 0;
        for (String t : allTeachers) {
            if (!low.isEmpty() && !t.toLowerCase(Locale.ROOT).contains(low)) continue;
            TextView tv = new TextView(activity);
            tv.setText(t);
            tv.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_primary));
            tv.setTextSize(14f);
            tv.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setOnClickListener(v -> listener.onTeacherClick(t));
            container.addView(tv);
            shown++;
            if (shown >= 40) break;
        }
        if (shown == 0) {
            TextView empty = new TextView(activity);
            empty.setText(allTeachers.isEmpty()
                    ? "Нет данных о преподавателях в локальной БД"
                    : "Преподаватель не найден");
            empty.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_secondary));
            empty.setTextSize(13f);
            empty.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
            container.addView(empty);
        }
    }

    private static void loadTeacherSchedule(AppCompatActivity activity, ExecutorService ex, ScheduleDao dao,
                                            String semId, String teacher, String weekType, int dow, boolean isSunday,
                                            LinearLayout llSuggestions, LinearLayout llResult,
                                            TextView tvName, TextView tvNowStatus, LinearLayout llDayRows) {
        llSuggestions.setVisibility(View.GONE);
        llResult.setVisibility(View.VISIBLE);
        tvName.setText(teacher);
        tvNowStatus.setText("Загрузка...");
        llDayRows.removeAllViews();

        ex.execute(() -> {
            List<ScheduleItem> items = dao.getTeacherScheduleForDay(teacher, weekType, dow, semId);
            LocalTime now = DateUtils.nowTimeMoscow();
            String[][] times = Constants.getLessonTimes(dow);

            String nowStatusText;
            if (isSunday) {
                nowStatusText = "Сегодня воскресенье — занятий нет (показан ПН)";
            } else {
                ScheduleItem current = null;
                ScheduleItem next = null;
                for (ScheduleItem it : items) {
                    int n = it.getLessonNumber();
                    if (n < 1 || n > times.length) continue;
                    LocalTime start = LocalTime.parse(times[n - 1][0], TF);
                    LocalTime end = LocalTime.parse(times[n - 1][1], TF);
                    if (!now.isBefore(start) && now.isBefore(end)) {
                        current = it;
                        break;
                    } else if (now.isBefore(start) && next == null) {
                        next = it;
                    }
                }
                if (current != null) {
                    String room = safeRoom(current.getRoom());
                    nowStatusText = "Сейчас (" + current.getLessonNumber() + " пара): "
                            + room + " · " + safe(current.getSubjectName())
                            + (safe(current.getGroupName()).isEmpty() ? "" : " (" + current.getGroupName() + ")");
                } else if (next != null) {
                    String room = safeRoom(next.getRoom());
                    nowStatusText = "Сейчас пары нет. Следующая (" + next.getLessonNumber() + " пара в "
                            + times[next.getLessonNumber() - 1][0] + "): " + room
                            + " · " + safe(next.getSubjectName());
                } else {
                    nowStatusText = items.isEmpty()
                            ? "Сегодня у преподавателя пар нет"
                            : "Пары на сегодня уже закончились";
                }
            }

            final String finalStatus = nowStatusText;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                tvNowStatus.setText(finalStatus);
                GlassSurfaceHelper.applyGlassCard(tvNowStatus, 14f, true);
                llDayRows.removeAllViews();
                if (items.isEmpty()) {
                    TextView empty = new TextView(activity);
                    empty.setText("Нет запланированных пар на этот день");
                    empty.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_secondary));
                    empty.setTextSize(13f);
                    empty.setPadding(0, dp(activity, 8), 0, dp(activity, 8));
                    llDayRows.addView(empty);
                } else {
                    for (ScheduleItem it : items) {
                        TextView row = new TextView(activity);
                        String time = Constants.getLessonTimeLabel(dow, it.getLessonNumber());
                        String text = it.getLessonNumber() + " пара (" + time + ") · "
                                + safeRoom(it.getRoom()) + "\n"
                                + safe(it.getSubjectName())
                                + (safe(it.getGroupName()).isEmpty() ? "" : " · гр. " + it.getGroupName());
                        row.setText(text);
                        row.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_primary));
                        row.setTextSize(13f);
                        row.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.bottomMargin = dp(activity, 6);
                        row.setLayoutParams(lp);
                        GlassSurfaceHelper.applyGlassCard(row, 12f, false);
                        llDayRows.addView(row);
                    }
                }
            });
        });
    }

    private static void refreshFreeRooms(AppCompatActivity activity, ExecutorService ex, ScheduleDao dao,
                                         String semId, List<String> allRooms,
                                         MaterialButtonToggleGroup toggleWeekType,
                                         MaterialButtonToggleGroup toggleDay,
                                         ChipGroup chipBuilding,
                                         MaterialButtonToggleGroup toggleSlot,
                                         String defaultWeekType, int defaultDow, boolean isSunday,
                                         TextView tvSummary, LinearLayout llList) {
        String weekType = weekTypeFromButtonId(toggleWeekType.getCheckedButtonId(), defaultWeekType);
        int dow = dayFromButtonId(toggleDay.getCheckedButtonId(), defaultDow);
        int slot = slotFromButtonId(toggleSlot.getCheckedButtonId());
        int bldId = chipBuilding.getCheckedChipId();
        String dayName = Constants.DAY_NAMES_SHORT[Math.max(0, Math.min(5, dow - 1))];
        String wtLabel = Constants.WEEK_TYPE_EVEN.equals(weekType) ? "чётная" : "нечётная";
        String timeLabel = Constants.getLessonTimeLabel(dow, slot);

        ex.execute(() -> {
            Set<String> occupied = dao.getOccupiedRooms(weekType, dow, slot, semId);
            List<String> free = new ArrayList<>();
            for (String room : allRooms) {
                if (occupied.contains(room)) continue;
                if (matchesBuilding(room, bldId)) {
                    free.add(room);
                }
            }
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                boolean matchesToday = (dow == defaultDow && weekType.equals(defaultWeekType));
                String prefix;
                if (matchesToday && isSunday) {
                    prefix = "Воскресенье (показан ПН, " + wtLabel + ")";
                } else if (matchesToday) {
                    prefix = "Сегодня (" + dayName + ", " + wtLabel + ")";
                } else {
                    prefix = dayName + ", " + wtLabel + " нед.";
                }
                String summary = prefix + " · " + slot + " пара (" + timeLabel + ") · свободно: " + free.size();
                tvSummary.setText(summary);
                llList.removeAllViews();

                if (allRooms.isEmpty()) {
                    TextView empty = new TextView(activity);
                    empty.setText("Нет данных об аудиториях. Обновите расписание.");
                    empty.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_secondary));
                    empty.setPadding(0, dp(activity, 8), 0, dp(activity, 8));
                    llList.addView(empty);
                    return;
                }
                if (free.isEmpty()) {
                    TextView empty = new TextView(activity);
                    empty.setText("Свободных аудиторий по выбранным параметрам не найдено");
                    empty.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_secondary));
                    empty.setPadding(0, dp(activity, 8), 0, dp(activity, 8));
                    llList.addView(empty);
                    return;
                }

                for (String r : free) {
                    TextView tv = new TextView(activity);
                    RoomFormatter.RoomInfo info = RoomFormatter.parse(r);
                    String label = r;
                    if (info != null && info.room != null) {
                        label = r + "  (" + info.building + ")";
                    }
                    tv.setText(label);
                    tv.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_primary));
                    tv.setTextSize(14f);
                    tv.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = dp(activity, 6);
                    tv.setLayoutParams(lp);
                    GlassSurfaceHelper.applyGlassCard(tv, 12f, false);
                    llList.addView(tv);
                }
            });
        });
    }

    private static int chipIdForBuilding(String buildingName) {
        if (buildingName == null) return R.id.chipBldAll;
        String b = buildingName.trim().toLowerCase(Locale.ROOT);
        if (b.contains("главн")) return R.id.chipBldMain;
        if (b.contains("перв") || b.equals("1") || b.startsWith("1 ")) return R.id.chipBld1;
        if (b.contains("втор") || b.equals("2") || b.startsWith("2 ")) return R.id.chipBld2;
        if (b.contains("трет") || b.equals("3") || b.startsWith("3 ")) return R.id.chipBld3;
        return R.id.chipBldAll;
    }

    private static String resolveTeacherName(List<String> allTeachers, String query) {
        for (String t : allTeachers) {
            if (t.equalsIgnoreCase(query)) return t;
        }
        String low = query.toLowerCase(Locale.ROOT);
        for (String t : allTeachers) {
            if (t.toLowerCase(Locale.ROOT).contains(low)) return t;
        }
        return query;
    }

    private static boolean matchesBuilding(String room, int chipId) {
        if (chipId == R.id.chipBldAll || chipId == View.NO_ID) {
            return true;
        }
        String r = room.trim().toUpperCase(Locale.ROOT);
        if (chipId == R.id.chipBldMain) return r.startsWith("Г-") || r.startsWith("Г ");
        if (chipId == R.id.chipBld1) return r.startsWith("1-");
        if (chipId == R.id.chipBld2) return r.startsWith("2-");
        if (chipId == R.id.chipBld3) return r.startsWith("3-");
        return true;
    }

    private static int detectDefaultSlot() {
        ScheduleClock.State state = ScheduleClock.compute();
        if (state.current != null) {
            return Math.max(1, Math.min(7, state.current.lesson));
        }
        if (state.next != null && state.next.date != null
                && state.next.date.equals(DateUtils.todayMoscow())) {
            return Math.max(1, Math.min(7, state.next.lesson));
        }
        return 1;
    }

    private static int slotButtonId(int slot) {
        switch (slot) {
            case 2: return R.id.btnSlot2;
            case 3: return R.id.btnSlot3;
            case 4: return R.id.btnSlot4;
            case 5: return R.id.btnSlot5;
            case 6: return R.id.btnSlot6;
            case 7: return R.id.btnSlot7;
            default: return R.id.btnSlot1;
        }
    }

    private static int slotFromButtonId(int id) {
        if (id == R.id.btnSlot2) return 2;
        if (id == R.id.btnSlot3) return 3;
        if (id == R.id.btnSlot4) return 4;
        if (id == R.id.btnSlot5) return 5;
        if (id == R.id.btnSlot6) return 6;
        if (id == R.id.btnSlot7) return 7;
        return 1;
    }

    private static int dayButtonId(int dow) {
        switch (dow) {
            case 2: return R.id.btnDayTue;
            case 3: return R.id.btnDayWed;
            case 4: return R.id.btnDayThu;
            case 5: return R.id.btnDayFri;
            case 6: return R.id.btnDaySat;
            default: return R.id.btnDayMon;
        }
    }

    private static int dayFromButtonId(int id, int fallback) {
        if (id == R.id.btnDayMon) return 1;
        if (id == R.id.btnDayTue) return 2;
        if (id == R.id.btnDayWed) return 3;
        if (id == R.id.btnDayThu) return 4;
        if (id == R.id.btnDayFri) return 5;
        if (id == R.id.btnDaySat) return 6;
        return fallback;
    }

    private static String weekTypeFromButtonId(int id, String fallback) {
        if (id == R.id.btnWeekOdd) return Constants.WEEK_TYPE_ODD;
        if (id == R.id.btnWeekEven) return Constants.WEEK_TYPE_EVEN;
        return fallback;
    }

    private static String safeRoom(String r) {
        return (r == null || r.trim().isEmpty()) ? "ауд. не указана" : "ауд. " + r.trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static int dp(AppCompatActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
