package com.university.schedule.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MorningDigestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ScheduleRepository repo = ScheduleRepository.getInstance(context);
                String selType = repo.getSelectionType();
                String selName = repo.getSelectionName();
                if (selName == null || selName.isEmpty()) return;

                DaySchedule today = "group".equals(selType)
                        ? repo.getTodayScheduleForGroup(selName)
                        : repo.getTodayScheduleForTeacher(selName);

                if (today != null && today.hasLessons() && !today.isDayOff()) {
                    List<ScheduleItem> lessons = today.getLessons();
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

                    if (!lessons.isEmpty()) {
                        int dow = DateUtils.toScheduleDayOfWeek(DateUtils.todayMoscow());
                        String[][] times = Constants.getLessonTimes(dow);
                        int firstIdx = lessons.get(0).getLessonNumber() - 1;
                        int lastIdx = lessons.get(lessons.size() - 1).getLessonNumber() - 1;
                        if (firstIdx >= 0 && firstIdx < times.length) startTime = times[firstIdx][0];
                        if (lastIdx >= 0 && lastIdx < times.length) endTime = times[lastIdx][1];
                    }

                    NotificationHelper.sendMorningDigest(context, selName, lines, startTime, endTime);
                }
            } catch (Exception ignored) {
            } finally {
                ReminderScheduler.scheduleMorningDigest(context);
            }
        });
    }
}
