package com.university.schedule.ui.dialog;

import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.university.schedule.R;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.AttendanceManager;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;
import com.university.schedule.util.SkippedLessonsManager;

import java.time.LocalDate;

/**
 * Liquid Glass диалог отметки посещаемости конкретной пары ("Посетил" / "Н-ка").
 */
public final class MarkAttendanceDialog {

    private MarkAttendanceDialog() {
    }

    public static void show(@NonNull AppCompatActivity activity,
                            @Nullable ScheduleItem item,
                            @Nullable LocalDate date,
                            boolean isGroup,
                            @Nullable AttendanceManager mgr,
                            @Nullable SkippedLessonsManager skippedMgr,
                            @Nullable Runnable onChanged) {
        if (activity.isFinishing() || date == null || item == null || skippedMgr == null) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_mark_lesson_attendance, null);

        TextView tvSubject = dialogView.findViewById(R.id.tvMarkLessonSubject);
        TextView tvDetails = dialogView.findViewById(R.id.tvMarkLessonDetails);
        TextView tvDate = dialogView.findViewById(R.id.tvMarkLessonDate);
        TextView tvStatus = dialogView.findViewById(R.id.tvCurrentStatusBadge);
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseMarkDialog);
        View cardAttended = dialogView.findViewById(R.id.cardMarkAttended);
        TextView tvBtnAttendedTitle = dialogView.findViewById(R.id.tvBtnAttendedTitle);
        View cardSkipped = dialogView.findViewById(R.id.cardMarkSkipped);
        MaterialButton btnReset = dialogView.findViewById(R.id.btnResetMark);

        tvSubject.setText(item.getSubjectName());
        int dow = DateUtils.toScheduleDayOfWeek(date);
        String timeStr = Constants.getLessonTimeLabel(dow, item.getLessonNumber());
        String roomStr = (item.getRoom() != null && !item.getRoom().isEmpty()) ? " · ауд. " + item.getRoom() : "";
        tvDetails.setText(item.getLessonNumber() + " пара · " + timeStr + roomStr);
        tvDate.setText(DateUtils.formatDisplayDate(date));

        boolean isFuture = date.isAfter(DateUtils.todayMoscow());
        if (tvBtnAttendedTitle != null) {
            tvBtnAttendedTitle.setText(isFuture ? "Пойду на пару" : "Посетил пару");
        }

        SkippedLessonsManager.AttendanceStatus currentStatus =
                skippedMgr.getStatus(date, item.getLessonNumber(), item.getSubjectName());
        if (currentStatus == SkippedLessonsManager.AttendanceStatus.ATTENDED) {
            tvStatus.setText(isFuture ? "🟢 Отмечено: Пойду" : "🟢 Отмечено: Посещено");
            tvStatus.setTextColor(ContextCompat.getColor(activity, R.color.glass_success));
            btnReset.setVisibility(View.VISIBLE);
        } else if (currentStatus == SkippedLessonsManager.AttendanceStatus.SKIPPED) {
            tvStatus.setText("🔴 Отмечено: Н-ка (пропуск)");
            tvStatus.setTextColor(ContextCompat.getColor(activity, R.color.glass_danger));
            btnReset.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setText("⚪ Не отмечено");
            tvStatus.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_secondary));
            btnReset.setVisibility(View.GONE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .create();

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        cardAttended.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            skippedMgr.markAttended(date, item.getLessonNumber(), item.getSubjectName());
            if (mgr != null) mgr.invalidateCache();
            if (onChanged != null) onChanged.run();
            Toast.makeText(activity, isFuture ? "Отмечено: Пойду на пару" : "Отмечено: Пара посещена", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        cardSkipped.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            skippedMgr.markSkipped(date, item.getLessonNumber(), item.getSubjectName());
            if (mgr != null) mgr.invalidateCache();
            if (onChanged != null) onChanged.run();
            Toast.makeText(activity, "Отмечено: Н-ка (пропуск)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnReset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            skippedMgr.clearStatus(date, item.getLessonNumber(), item.getSubjectName());
            if (mgr != null) mgr.invalidateCache();
            if (onChanged != null) onChanged.run();
            Toast.makeText(activity, "Отметка сброшена", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, dialogView);

        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
