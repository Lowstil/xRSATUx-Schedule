package com.university.schedule.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.logic.WeekCalculator;
import com.university.schedule.model.DaySchedule;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.model.WeekSchedule;
import com.university.schedule.ui.glass.GlassSurfaceHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экспорт и шаринг расписания (День / Неделя — текст или Liquid Glass картинка, Feature #10).
 */
public final class ScheduleExporter {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private ScheduleExporter() {
    }

    public static void showExportDialog(AppCompatActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        ScheduleRepository repo = ScheduleRepository.getInstance(activity);
        if (!repo.hasUserSelection()) {
            Toast.makeText(activity, "Сначала выберите группу или преподавателя", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = new String[]{
                "Сегодня — текст",
                "Сегодня — картинка (Liquid Glass)",
                "Текущая неделя — текст",
                "Текущая неделя — картинка (Liquid Glass)"
        };

        float density = activity.getResources().getDisplayMetrics().density;
        int pad20 = Math.round(20 * density);
        int pad14 = Math.round(14 * density);
        int pad12 = Math.round(12 * density);
        int gap8 = Math.round(8 * density);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad20, pad20, pad20, Math.round(16 * density));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("Поделиться расписанием");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_primary));
        root.addView(tvTitle);

        TextView tvSubtitle = new TextView(activity);
        tvSubtitle.setText("Выберите формат экспорта");
        tvSubtitle.setTextSize(13f);
        tvSubtitle.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_secondary));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Math.round(2 * density);
        subLp.bottomMargin = pad14;
        tvSubtitle.setLayoutParams(subLp);
        root.addView(tvSubtitle);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(root)
                .create();

        int primaryText = ContextCompat.getColor(activity, R.color.glass_text_primary);
        for (int i = 0; i < options.length; i++) {
            final int which = i;
            TextView row = new TextView(activity);
            row.setText(options[i]);
            row.setTextSize(14f);
            row.setTypeface(null, Typeface.BOLD);
            row.setTextColor(primaryText);
            row.setPadding(pad14, pad12, pad14, pad12);
            row.setClickable(true);
            row.setFocusable(true);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = gap8;
            row.setLayoutParams(rowLp);
            GlassSurfaceHelper.applyGlassCard(row, 14f, false);
            row.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                dialog.dismiss();
                boolean fullWeek = (which == 2 || which == 3);
                boolean asImage = (which == 1 || which == 3);
                exportAndShare(activity, fullWeek, asImage);
            });
            root.addView(row);
        }

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = Math.round(6 * density);
        footer.setLayoutParams(footerLp);

        TextView btnCancel = new TextView(activity);
        btnCancel.setText("Отмена");
        btnCancel.setTextSize(14f);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setTextColor(ContextCompat.getColor(activity, R.color.glass_text_secondary));
        btnCancel.setPadding(Math.round(18 * density), Math.round(10 * density),
                Math.round(18 * density), Math.round(10 * density));
        btnCancel.setClickable(true);
        btnCancel.setFocusable(true);
        GlassSurfaceHelper.applyGlassCard(btnCancel, 999f, false);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        footer.addView(btnCancel);
        root.addView(footer);

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, root);
        Window window = dialog.getWindow();
        if (window != null) {
            int maxW = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 420f, activity.getResources().getDisplayMetrics());
            int width = Math.min((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90f), maxW);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public static void exportAndShare(AppCompatActivity activity, boolean fullWeek, boolean asImage) {
        EXECUTOR.execute(() -> {
            try {
                ScheduleRepository repo = ScheduleRepository.getInstance(activity);
                String selType = repo.getSelectionType();
                String selName = repo.getSelectionName();
                boolean isGroup = "group".equals(selType);
                LocalDate today = DateUtils.todayMoscow();
                WeekCalculator wc = repo.getWeekCalculator();
                int weekNum = wc != null ? wc.getWeekNumber(today) : -1;

                if (fullWeek) {
                    if (weekNum < 1) {
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            Toast.makeText(activity, "Сейчас вне учебного семестра", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    WeekSchedule week = isGroup
                            ? repo.getWeekScheduleForGroup(selName, weekNum)
                            : repo.getWeekScheduleForTeacher(selName, weekNum);
                    if (asImage) {
                        Bitmap bmp = renderWeekBitmap(selName, week);
                        Uri uri = saveBitmapToCache(activity, bmp, "schedule_week.png");
                        bmp.recycle();
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            shareImage(activity, uri, "Расписание на неделю: " + selName);
                        });
                    } else {
                        String text = formatWeekText(selName, week, isGroup);
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            shareText(activity, text, "Расписание на неделю");
                        });
                    }
                } else {
                    DaySchedule day = isGroup
                            ? repo.getTodayScheduleForGroup(selName)
                            : repo.getTodayScheduleForTeacher(selName);
                    if (asImage) {
                        Bitmap bmp = renderDayBitmap(selName, day);
                        Uri uri = saveBitmapToCache(activity, bmp, "schedule_day.png");
                        bmp.recycle();
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            shareImage(activity, uri, "Расписание на сегодня: " + selName);
                        });
                    } else {
                        String text = formatDayText(selName, day, isGroup);
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            shareText(activity, text, "Расписание на сегодня");
                        });
                    }
                }
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Toast.makeText(activity, "Ошибка экспорта: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    public static String formatDayText(String selName, DaySchedule day, boolean isGroup) {
        StringBuilder sb = new StringBuilder();
        String dateStr = day != null && day.getDate() != null ? DateUtils.formatDisplayDate(day.getDate()) : "";
        String dayName = (day != null && day.getDayOfWeek() >= 1 && day.getDayOfWeek() <= 6)
                ? Constants.DAY_NAMES_SHORT[day.getDayOfWeek() - 1]
                : "Сегодня";
        sb.append("📅 ").append(selName).append(" · ").append(dayName);
        if (!dateStr.isEmpty()) sb.append(" (").append(dateStr).append(")");
        sb.append("\n\n");

        if (day == null || day.isDayOff()) {
            String h = (day != null && day.getHolidayName() != null) ? day.getHolidayName() : "Выходной";
            sb.append("🎉 ").append(h);
            return sb.toString();
        }
        if (!day.hasLessons()) {
            sb.append("Занятий нет");
            return sb.toString();
        }

        for (ScheduleItem it : day.getLessons()) {
            if (it.isCancelled()) continue;
            String time = Constants.getLessonTimeLabel(day.getDayOfWeek(), it.getLessonNumber());
            sb.append(it.getLessonNumber()).append(". [").append(time).append("] ");
            if (it.getLessonType() != null && !it.getLessonType().isEmpty()) {
                sb.append("(").append(it.getLessonType()).append(") ");
            }
            sb.append(safe(it.getSubjectName()));
            if (it.getRoom() != null && !it.getRoom().isEmpty()) {
                sb.append(" — ауд. ").append(it.getRoom());
            }
            String party = isGroup ? it.getTeacherName() : it.getGroupName();
            if (party != null && !party.isEmpty()) {
                sb.append(" (").append(party).append(")");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public static String formatWeekText(String selName, WeekSchedule week, boolean isGroup) {
        StringBuilder sb = new StringBuilder();
        String parity = (week != null && week.isEven()) ? "чётная" : "нечётная";
        int num = week != null ? week.getWeekNumber() : 1;
        sb.append("📆 ").append(selName).append(" · Неделя ").append(num).append(" (").append(parity).append(")\n");

        if (week == null || week.getDays() == null || week.getDays().isEmpty()) {
            sb.append("\nНет данных");
            return sb.toString();
        }

        for (DaySchedule day : week.getDays()) {
            String dayName = (day.getDayOfWeek() >= 1 && day.getDayOfWeek() <= 6)
                    ? Constants.DAY_NAMES_SHORT[day.getDayOfWeek() - 1]
                    : "";
            String dateStr = day.getDate() != null ? DateUtils.formatDisplayDateShort(day.getDate()) : "";
            sb.append("\n— ").append(dayName).append(" ").append(dateStr).append(" —\n");
            if (day.isDayOff()) {
                sb.append("Выходной").append(day.getHolidayName() != null ? " (" + day.getHolidayName() + ")" : "").append("\n");
                continue;
            }
            if (!day.hasLessons()) {
                sb.append("Нет пар\n");
                continue;
            }
            boolean any = false;
            for (ScheduleItem it : day.getLessons()) {
                if (it.isCancelled()) continue;
                any = true;
                String time = Constants.getLessonTimeLabel(day.getDayOfWeek(), it.getLessonNumber());
                sb.append(it.getLessonNumber()).append(". ").append(time).append(" · ")
                        .append(safe(it.getSubjectName()));
                if (it.getRoom() != null && !it.getRoom().isEmpty()) {
                    sb.append(" · ауд. ").append(it.getRoom());
                }
                String party = isGroup ? it.getTeacherName() : it.getGroupName();
                if (party != null && !party.isEmpty()) {
                    sb.append(" · ").append(party);
                }
                sb.append("\n");
            }
            if (!any) sb.append("Нет пар\n");
        }
        return sb.toString().trim();
    }

    private static Bitmap renderDayBitmap(String selName, DaySchedule day) {
        List<ScheduleItem> lessons = new ArrayList<>();
        if (day != null && !day.isDayOff() && day.hasLessons()) {
            for (ScheduleItem it : day.getLessons()) {
                if (!it.isCancelled()) lessons.add(it);
            }
        }

        int width = 1080;
        int rowHeight = 148;
        int headerHeight = 220;
        int footerHeight = 90;
        int contentHeight = lessons.isEmpty() ? 180 : lessons.size() * rowHeight + 40;
        int height = headerHeight + contentHeight + footerHeight;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawBackdrop(canvas, width, height);

        TextPaint titlePaint = makePaint(Color.WHITE, 46f, true);
        TextPaint subPaint = makePaint(0xCCFFFFFF, 28f, false);
        TextPaint itemTitlePaint = makePaint(0xFFF3F6FC, 32f, true);
        TextPaint itemSubPaint = makePaint(0xFF9AA6BF, 26f, false);
        TextPaint accentPaint = makePaint(0xFF6C8CFF, 28f, true);

        String dayName = (day != null && day.getDayOfWeek() >= 1 && day.getDayOfWeek() <= 6)
                ? Constants.DAY_NAMES_SHORT[day.getDayOfWeek() - 1]
                : "Сегодня";
        String dateStr = (day != null && day.getDate() != null) ? DateUtils.formatDisplayDate(day.getDate()) : "";

        canvas.drawText(selName, 64f, 108f, titlePaint);
        canvas.drawText(dayName + (dateStr.isEmpty() ? "" : " · " + dateStr), 64f, 158f, subPaint);

        int y = headerHeight;
        if (lessons.isEmpty()) {
            RectF card = new RectF(52f, y, width - 52f, y + 130f);
            drawGlassCard(canvas, card);
            String msg = (day != null && day.isDayOff()) ? "Выходной день" : "Занятий нет";
            canvas.drawText(msg, 88f, y + 76f, itemTitlePaint);
        } else {
            for (ScheduleItem it : lessons) {
                RectF card = new RectF(52f, y, width - 52f, y + rowHeight - 20f);
                drawGlassCard(canvas, card);

                String time = Constants.getLessonTimeLabel(day.getDayOfWeek(), it.getLessonNumber());
                canvas.drawText(it.getLessonNumber() + " пара · " + time, 88f, y + 48f, accentPaint);
                canvas.drawText(ellipsize(safe(it.getSubjectName()), 38), 88f, y + 92f, itemTitlePaint);

                String details = (it.getRoom() != null && !it.getRoom().isEmpty() ? "ауд. " + it.getRoom() : "")
                        + (it.getTeacherName() != null && !it.getTeacherName().isEmpty() ? " · " + it.getTeacherName() : "");
                if (!details.isEmpty()) {
                    canvas.drawText(ellipsize(details, 46), 88f, y + 124f, itemSubPaint);
                }
                y += rowHeight;
            }
        }

        canvas.drawText("Расписание РГАТУ", 64f, height - 38f, itemSubPaint);
        return bmp;
    }

    private static Bitmap renderWeekBitmap(String selName, WeekSchedule week) {
        int width = 1080;
        List<DaySchedule> days = (week != null && week.getDays() != null) ? week.getDays() : new ArrayList<>();
        int lineCount = 0;
        for (DaySchedule d : days) {
            lineCount += 1;
            if (!d.isDayOff() && d.hasLessons()) {
                for (ScheduleItem it : d.getLessons()) {
                    if (!it.isCancelled()) lineCount++;
                }
            } else {
                lineCount += 1;
            }
        }
        int height = Math.max(640, 240 + lineCount * 52 + 80);
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawBackdrop(canvas, width, height);

        TextPaint titlePaint = makePaint(Color.WHITE, 44f, true);
        TextPaint subPaint = makePaint(0xCCFFFFFF, 26f, false);
        TextPaint dayPaint = makePaint(0xFF6C8CFF, 28f, true);
        TextPaint rowPaint = makePaint(0xFFF3F6FC, 24f, false);

        String parity = (week != null && week.isEven()) ? "чётная" : "нечётная";
        int num = week != null ? week.getWeekNumber() : 1;
        canvas.drawText(selName, 56f, 98f, titlePaint);
        canvas.drawText("Неделя " + num + " (" + parity + ")", 56f, 144f, subPaint);

        RectF mainCard = new RectF(44f, 178f, width - 44f, height - 64f);
        drawGlassCard(canvas, mainCard);

        float y = 230f;
        for (DaySchedule d : days) {
            String dayName = (d.getDayOfWeek() >= 1 && d.getDayOfWeek() <= 6)
                    ? Constants.DAY_NAMES_SHORT[d.getDayOfWeek() - 1]
                    : "";
            String dateStr = d.getDate() != null ? DateUtils.formatDisplayDateShort(d.getDate()) : "";
            canvas.drawText(dayName + " " + dateStr, 76f, y, dayPaint);
            y += 42f;

            if (d.isDayOff() || !d.hasLessons()) {
                canvas.drawText(d.isDayOff() ? "Выходной" : "Нет пар", 96f, y, subPaint);
                y += 46f;
                continue;
            }
            boolean any = false;
            for (ScheduleItem it : d.getLessons()) {
                if (it.isCancelled()) continue;
                any = true;
                String line = it.getLessonNumber() + ". " + safe(it.getSubjectName())
                        + (it.getRoom() != null && !it.getRoom().isEmpty() ? " (" + it.getRoom() + ")" : "");
                canvas.drawText(ellipsize(line, 52), 96f, y, rowPaint);
                y += 42f;
            }
            if (!any) {
                canvas.drawText("Нет пар", 96f, y, subPaint);
                y += 42f;
            }
            y += 12f;
        }

        return bmp;
    }

    private static void drawBackdrop(Canvas canvas, int w, int h) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0xFF0B0F1A, 0xFF1E2B58, 0xFF2B1B4D, 0xFF0F3847},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bg);
    }

    private static void drawGlassCard(Canvas canvas, RectF rect) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0x99182032);
        canvas.drawRoundRect(rect, 32f, 32f, fill);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.5f);
        stroke.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{0x52FFFFFF, 0x14FFFFFF, 0x26FFFFFF},
                null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, 32f, 32f, stroke);
    }

    private static TextPaint makePaint(int color, float size, boolean bold) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        if (bold) p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return p;
    }

    private static Uri saveBitmapToCache(Context context, Bitmap bmp, String fileName) throws Exception {
        File dir = new File(context.getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Не удалось создать каталог exports");
        }
        File out = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 95, fos);
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", out);
    }

    private static void shareText(Context context, String text, String title) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(intent, title));
    }

    private static void shareImage(Context context, Uri uri, String title) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(intent, title);
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(chooser);
    }

    private static String ellipsize(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
