package com.university.schedule.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.university.schedule.R;
import com.university.schedule.ui.activity.MainActivity;

import java.util.List;

public final class NotificationHelper {

    public static final String CHANNEL_REMINDERS = "channel_lesson_reminders";
    public static final String CHANNEL_DIGEST = "channel_morning_digest";
    public static final String CHANNEL_TRANSFERS = "channel_transfers";

    private static final int ID_REMINDER = 1001;
    private static final int ID_DIGEST = 1002;
    private static final int ID_TRANSFER = 1003;

    private NotificationHelper() {
    }

    public static void initChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;

            NotificationChannel cReminders = new NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Напоминания о парах",
                    NotificationManager.IMPORTANCE_HIGH
            );
            cReminders.setDescription("Уведомления за 10–15 минут до начала занятия");
            cReminders.enableVibration(true);

            NotificationChannel cDigest = new NotificationChannel(
                    CHANNEL_DIGEST,
                    "Утреннее расписание",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            cDigest.setDescription("Ежедневный дайджест расписания на текущий день");

            NotificationChannel cTransfers = new NotificationChannel(
                    CHANNEL_TRANSFERS,
                    "Изменения в расписании",
                    NotificationManager.IMPORTANCE_HIGH
            );
            cTransfers.setDescription("Оповещения об отменах и переносах занятий");
            cTransfers.enableVibration(true);

            nm.createNotificationChannel(cReminders);
            nm.createNotificationChannel(cDigest);
            nm.createNotificationChannel(cTransfers);
        }
    }

    public static void sendLessonReminder(Context context, String subject, String type,
                                           String room, String teacherOrGroup,
                                           int lessonNum, int minutesBefore) {
        initChannels(context);
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = (minutesBefore > 0)
                ? "Через " + minutesBefore + " мин: " + subject
                : "Пара начинается: " + subject;

        StringBuilder body = new StringBuilder();
        if (type != null && !type.isEmpty()) body.append(type).append(" · ");
        body.append(lessonNum).append(" пара");
        if (room != null && !room.isEmpty()) body.append(" · ауд. ").append(room);
        if (teacherOrGroup != null && !teacherOrGroup.isEmpty()) body.append(" · ").append(teacherOrGroup);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body.toString())
                .setColor(ContextCompat.getColor(context, R.color.primary))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(ID_REMINDER, b.build());
        }
    }

    public static void sendMorningDigest(Context context, String selName,
                                          List<String> lessonLines, String startTime, String endTime) {
        initChannels(context);
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int count = lessonLines != null ? lessonLines.size() : 0;
        String title = "Сегодня " + count + " " + pluralLessons(count) + " (" + selName + ")";
        String summary = "Начало в " + startTime + ", окончание в " + endTime;

        NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle();
        inbox.setBigContentTitle(title);
        inbox.setSummaryText(summary);
        if (lessonLines != null) {
            for (String l : lessonLines) {
                inbox.addLine(l);
            }
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_DIGEST)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(inbox)
                .setColor(ContextCompat.getColor(context, R.color.primary))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(ID_DIGEST, b.build());
        }
    }

    public static void sendTransferAlert(Context context, String title, String body) {
        initChannels(context);
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setColor(ContextCompat.getColor(context, R.color.accent))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(ID_TRANSFER, b.build());
        }
    }

    private static String pluralLessons(int n) {
        int rem100 = n % 100;
        int rem10 = n % 10;
        if (rem100 >= 11 && rem100 <= 14) return "пар";
        if (rem10 == 1) return "пара";
        if (rem10 >= 2 && rem10 <= 4) return "пары";
        return "пар";
    }
}
