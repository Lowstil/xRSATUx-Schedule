package com.university.schedule.ui.controller;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.SemesterManager;
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
    private final Map<String, WeekSchedule> weekCache = new HashMap<>();
    private final Set<String> loadingWeeks = new HashSet<>();
    private String currentSemesterId;
    private int selectionGeneration = 0;

    public WeekScheduleController(@NonNull ScheduleRepository repo, @NonNull ExecutorService executor) {
        this.repo = repo;
        this.executor = executor;
        String active = repo.getActiveSemesterId();
        this.currentSemesterId = active != null ? active : SemesterManager.SEMESTER_FALL;
    }

    @NonNull
    public String getSemesterId() {
        return currentSemesterId;
    }

    public void setSemesterId(@NonNull String semesterId) {
        if (!semesterId.equals(this.currentSemesterId)) {
            this.currentSemesterId = semesterId;
            selectionGeneration++;
            loadingWeeks.clear();
        }
    }

    private static String cacheKey(@NonNull String semesterId, int weekNumber) {
        return semesterId + ":" + weekNumber;
    }

    @Nullable
    public WeekSchedule getCachedWeek(int weekNumber) {
        return weekCache.get(cacheKey(currentSemesterId, weekNumber));
    }

    @Nullable
    public WeekSchedule getCachedWeek(int weekNumber, @NonNull String semesterId) {
        return weekCache.get(cacheKey(semesterId, weekNumber));
    }

    public void clearCache() {
        selectionGeneration++;
        weekCache.clear();
        loadingWeeks.clear();
    }

    public void destroy() {
        selectionGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        weekCache.clear();
        loadingWeeks.clear();
    }

    /**
     * Loads {@code targetWeek} for the current semester (or returns cached data immediately via {@code listener})
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
        return loadWeek(targetWeek, currentSemesterId, selType, selName, listener);
    }

    /**
     * Loads {@code targetWeek} for {@code semesterId} (or returns cached data immediately via {@code listener})
     * and prefetches {@code targetWeek - 1} and {@code targetWeek + 1}.
     *
     * @return {@code true} if {@code targetWeek} was already cached, {@code false} if an async load was started.
     */
    public boolean loadWeek(
            int targetWeek,
            @NonNull String semesterId,
            @Nullable String selType,
            @Nullable String selName,
            @NonNull Listener listener
    ) {
        setSemesterId(semesterId);
        boolean cachedHit = false;
        WeekSchedule cached = weekCache.get(cacheKey(semesterId, targetWeek));
        if (cached != null) {
            cachedHit = true;
        } else {
            fetchWeekIfNeeded(targetWeek, targetWeek, semesterId, selType, selName, listener);
        }

        if (targetWeek > 1) {
            fetchWeekIfNeeded(targetWeek - 1, targetWeek, semesterId, selType, selName, listener);
        }
        if (targetWeek < DayPagerAdapter.WEEK_COUNT) {
            fetchWeekIfNeeded(targetWeek + 1, targetWeek, semesterId, selType, selName, listener);
        }
        return cachedHit;
    }

    private void fetchWeekIfNeeded(
            final int weekToFetch,
            final int activeTargetWeek,
            @NonNull final String semesterId,
            @Nullable final String selType,
            @Nullable final String selName,
            @NonNull final Listener listener
    ) {
        if (weekToFetch < 1 || weekToFetch > DayPagerAdapter.WEEK_COUNT) return;
        final String key = cacheKey(semesterId, weekToFetch);
        if (weekCache.get(key) != null || loadingWeeks.contains(key)) return;

        loadingWeeks.add(key);
        final int gen = selectionGeneration;

        executor.execute(() -> {
            if (gen != selectionGeneration) return;
            WeekSchedule ws = GroupOrTeacher.TYPE_GROUP.equals(selType)
                    ? repo.getWeekScheduleForGroup(selName, weekToFetch, semesterId)
                    : repo.getWeekScheduleForTeacher(selName, weekToFetch, semesterId);

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
                loadingWeeks.remove(key);
                if (ws != null) {
                    weekCache.put(key, ws);
                }
                listener.onWeekReady(weekToFetch, ws, notesMap, weekToFetch == activeTargetWeek);
            });
        });
    }
}
