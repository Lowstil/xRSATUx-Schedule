package com.university.schedule.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.ExcludedSubjectsManager;
import com.university.schedule.util.SkippedLessonsManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MorningDigestReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        final Context appCtx = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                ScheduleRepository repo = ScheduleRepository.getInstance(appCtx);
                String selType = repo.getSelectionType();
                String selName = repo.getSelectionName();
                if (selName == null || selName.isEmpty()) return;

                boolean isGroup = "group".equals(selType);
                DaySchedule today = isGroup
                        ? repo.getTodayScheduleForGroup(selName)
                        : repo.getTodayScheduleForTeacher(selName);

                if (today != null && today.hasLessons() && !today.isDayOff()) {
                    LocalDate todayDate = DateUtils.todayMoscow();
                    ExcludedSubjectsManager excludedMgr = new ExcludedSubjectsManager(appCtx);
                    SkippedLessonsManager skippedMgr = new SkippedLessonsManager(appCtx);

                    List<ScheduleItem> lessons = new ArrayList<>();
                    for (ScheduleItem it : today.getLessons()) {
                        if (it.isCancelled()) continue;
                        if (isGroup && excludedMgr.isExcluded(selName, it.getSubjectName())) continue;
                        if (skippedMgr.isSkipped(todayDate, it.getLessonNumber(), it.getSubjectName())) continue;
                        lessons.add(it);
                    }

                    if (!lessons.isEmpty()) {
                        Collections.sort(lessons, (a, b) -> Integer.compare(a.getLessonNumber(), b.getLessonNumber()));
                        List<String> lines = new ArrayList<>();
                        String startTime = "08:30";
                        String endTime = "17:45";

                        for (ScheduleItem it : lessons) {
                            String t = it.getLessonNumber() + ". " + it.getSubjectName();
                            if (it.getRoom() != null && !it.getRoom().isEmpty()) {
                                t += " (" + it.getRoom() + ")";
                            }
                            lines.add(t);
                        }

                        int dow = DateUtils.toScheduleDayOfWeek(todayDate);
                        String[][] times = Constants.getLessonTimes(dow);
                        int firstIdx = lessons.get(0).getLessonNumber() - 1;
                        int lastIdx = lessons.get(lessons.size() - 1).getLessonNumber() - 1;
                        if (firstIdx >= 0 && firstIdx < times.length) startTime = times[firstIdx][0];
                        if (lastIdx >= 0 && lastIdx < times.length) endTime = times[lastIdx][1];

                        NotificationHelper.sendMorningDigest(appCtx, selName, lines, startTime, endTime);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                ReminderScheduler.scheduleMorningDigest(appCtx);
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        });
    }
}
