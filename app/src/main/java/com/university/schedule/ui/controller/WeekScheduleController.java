package com.university.schedule.ui.controller;

import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.model.LessonNote;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.ui.adapter.DayPagerAdapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Manages the 18-week schedule cache, in-flight deduplication,
 * generation-guarded async loading, and week ± 1 prefetching.
 */
public class WeekScheduleController {

    public interface Listener {
        void onWeekReady(
                int weekNumber,
                @Nullable WeekSchedule weekSchedule,
                @NonNull Map<String, Map<Integer, LessonNote>> notesMap,
                boolean isTargetWeek
        );
    }

    private final ScheduleRepository repo;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SparseArray<WeekSchedule> weekCache = new SparseArray<>();
    private final Set<Integer> loadingWeeks = new HashSet<>();
    private int selectionGeneration = 0;

    public WeekScheduleController(@NonNull ScheduleRepository repo, @NonNull ExecutorService executor) {
        this.repo = repo;
        this.executor = executor;
    }

    @Nullable
    public WeekSchedule getCachedWeek(int weekNumber) {
        return weekCache.get(weekNumber);
    }

    public void clearCache() {
        selectionGeneration++;
        weekCache.clear();
        loadingWeeks.clear();
    }

    /**
     * Loads {@code targetWeek} (or returns cached data immediately via {@code listener})
     * and prefetches {@code targetWeek - 1} and {@code targetWeek + 1}.
     *
     * @return {@code true} if {@code targetWeek} was already cached, {@code false} if an async load was started.
     */
    public boolean loadWeek(
            int targetWeek,
            @Nullable String selType,
            @Nullable String selName,
            @NonNull Listener listener
    ) {
        boolean cachedHit = false;
        WeekSchedule cached = weekCache.get(targetWeek);
        if (cached != null) {
            cachedHit = true;
        } else {
            fetchWeekIfNeeded(targetWeek, targetWeek, selType, selName, listener);
        }

        if (targetWeek > 1) {
            fetchWeekIfNeeded(targetWeek - 1, targetWeek, selType, selName, listener);
        }
        if (targetWeek < DayPagerAdapter.WEEK_COUNT) {
            fetchWeekIfNeeded(targetWeek + 1, targetWeek, selType, selName, listener);
        }
        return cachedHit;
    }

    private void fetchWeekIfNeeded(
            final int weekToFetch,
            final int activeTargetWeek,
            @Nullable final String selType,
            @Nullable final String selName,
            @NonNull final Listener listener
    ) {
        if (weekToFetch < 1 || weekToFetch > DayPagerAdapter.WEEK_COUNT) return;
        if (weekCache.get(weekToFetch) != null || loadingWeeks.contains(weekToFetch)) return;

        loadingWeeks.add(weekToFetch);
        final int gen = selectionGeneration;

        executor.execute(() -> {
            if (gen != selectionGeneration) return;
            WeekSchedule ws = GroupOrTeacher.TYPE_GROUP.equals(selType)
                    ? repo.getWeekScheduleForGroup(selName, weekToFetch)
                    : repo.getWeekScheduleForTeacher(selName, weekToFetch);

            Map<String, Map<Integer, LessonNote>> notesMap = new HashMap<>();
            if (ws != null && ws.getDays() != null) {
                for (DaySchedule d : ws.getDays()) {
                    if (d.getDate() != null) {
                        String iso = d.getDate().toString();
                        notesMap.put(iso, repo.getNotesForDate(iso));
                    }
                }
            }

            mainHandler.post(() -> {
                if (gen != selectionGeneration) return;
                loadingWeeks.remove(weekToFetch);
                if (ws != null) {
                    weekCache.put(weekToFetch, ws);
                }
                listener.onWeekReady(weekToFetch, ws, notesMap, weekToFetch == activeTargetWeek);
            });
        });
    }
}
