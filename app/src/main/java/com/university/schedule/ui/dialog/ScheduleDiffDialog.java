package com.university.schedule.ui.dialog;

import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.university.schedule.R;
import com.university.schedule.model.ScheduleDiff;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.Constants;
import com.university.schedule.util.PrefsManager;

/**
 * Диалог отображения изменений расписания ("Что изменилось?", Feature #5).
 */
public final class ScheduleDiffDialog {

    private ScheduleDiffDialog() {
    }

    public static void showIfHasChanges(AppCompatActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        PrefsManager prefs = new PrefsManager(activity);
        String json = prefs.getScheduleDiffJson();
        if (json == null || json.trim().isEmpty()) return;
        ScheduleDiff diff = ScheduleDiff.fromJson(json);
        if (diff == null || !diff.hasChanges()) {
            prefs.clearScheduleDiff();
            return;
        }
        showInternal(activity, diff, prefs);
    }

    public static void show(AppCompatActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        PrefsManager prefs = new PrefsManager(activity);
        ScheduleDiff diff = ScheduleDiff.fromJson(prefs.getScheduleDiffJson());
        showInternal(activity, diff, prefs);
    }

    private static void showInternal(AppCompatActivity activity, ScheduleDiff diff, PrefsManager prefs) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_schedule_diff, null);
        View rootCard = view.findViewById(R.id.rootDiffGlassCard);
        TextView tvSubtitle = view.findViewById(R.id.tvDiffSubtitle);
        LinearLayout llEntries = view.findViewById(R.id.llDiffEntries);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .create();

        Runnable closeAndClear = () -> {
            prefs.clearScheduleDiff();
            dialog.dismiss();
        };

        view.findViewById(R.id.btnCloseDiffHeader).setOnClickListener(v -> closeAndClear.run());
        view.findViewById(R.id.btnDiffOk).setOnClickListener(v -> closeAndClear.run());
        dialog.setOnCancelListener(d -> prefs.clearScheduleDiff());

        if (diff == null || !diff.hasChanges()) {
            tvSubtitle.setText("После последнего обновления изменений не найдено.");
            TextView empty = new TextView(activity);
            empty.setText("Расписание полностью совпадает с предыдущей версией.");
            empty.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_secondary));
            empty.setTextSize(14f);
            empty.setPadding(0, dp(activity, 12), 0, dp(activity, 12));
            llEntries.addView(empty);
        } else {
            tvSubtitle.setText(diff.getSelectionName() + " · найдено изменений: " + diff.getEntries().size());
            for (ScheduleDiff.DiffEntry entry : diff.getEntries()) {
                llEntries.addView(buildEntryRow(activity, entry));
            }
        }

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, rootCard);
        if (dialog.getWindow() != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            dialog.getWindow().setLayout((int) (dm.widthPixels * 0.92),
                    (int) (dm.heightPixels * (diff != null && diff.hasChanges() ? 0.78 : 0.45)));
        }
    }

    private static View buildEntryRow(AppCompatActivity activity, ScheduleDiff.DiffEntry entry) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(activity, 8);
        box.setLayoutParams(lp);
        GlassSurfaceHelper.applyGlassCard(box, 14f, false);

        TextView tvBadge = new TextView(activity);
        tvBadge.setTextSize(12f);
        tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);

        int colorRes;
        String badgeLabel;
        String detail;
        switch (entry.type) {
            case ADDED:
                colorRes = R.color.glass_success;
                badgeLabel = "+ Добавлена пара";
                detail = entry.newValue;
                break;
            case REMOVED:
                colorRes = R.color.glass_danger;
                badgeLabel = "− Удалена пара";
                detail = entry.oldValue;
                break;
            case ROOM_CHANGED:
                colorRes = R.color.glass_warning;
                badgeLabel = "⇄ Изменена аудитория";
                detail = safeVal(entry.oldValue) + " → " + safeVal(entry.newValue);
                break;
            case TEACHER_CHANGED:
            default:
                colorRes = R.color.glass_accent;
                badgeLabel = "⇄ Замена преподавателя/группы";
                detail = safeVal(entry.oldValue) + " → " + safeVal(entry.newValue);
                break;
        }

        tvBadge.setText(badgeLabel);
        tvBadge.setTextColor(ContextCompat.getColor(activity, colorRes));
        box.addView(tvBadge);

        String dayName = (entry.dayOfWeek >= 1 && entry.dayOfWeek <= 6)
                ? Constants.DAY_NAMES_SHORT[entry.dayOfWeek - 1]
                : "";
        String wt = Constants.WEEK_TYPE_EVEN.equals(entry.weekType) ? "чётная" : "нечётная";

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(dayName + " (" + wt + "), " + entry.lessonNumber + " пара · " + entry.subjectName);
        tvTitle.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_primary));
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, dp(activity, 3), 0, 0);
        box.addView(tvTitle);

        if (!detail.isEmpty()) {
            TextView tvDetail = new TextView(activity);
            tvDetail.setText(detail);
            tvDetail.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_secondary));
            tvDetail.setTextSize(13f);
            tvDetail.setPadding(0, dp(activity, 2), 0, 0);
            box.addView(tvDetail);
        }

        return box;
    }

    private static String safeVal(String s) {
        return (s == null || s.trim().isEmpty()) ? "—" : s.trim();
    }

    private static int dp(AppCompatActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
