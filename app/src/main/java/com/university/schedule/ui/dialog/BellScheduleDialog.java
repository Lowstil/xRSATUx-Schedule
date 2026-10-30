package com.university.schedule.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.university.schedule.R;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class BellScheduleDialog {
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");

    private BellScheduleDialog() {
    }

    public static void show(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_bell_schedule, null);
        TabLayout tabLayout = view.findViewById(R.id.tabBellSchedule);
        LinearLayout llRows = view.findViewById(R.id.llBellRows);
        View btnClose = view.findViewById(R.id.btnCloseBell);
        View btnCloseHeader = view.findViewById(R.id.btnCloseBellHeader);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .create();

        if (btnCloseHeader != null) {
            btnCloseHeader.setOnClickListener(v -> dialog.dismiss());
        }

        int todayDow = DateUtils.toScheduleDayOfWeek(DateUtils.todayMoscow());
        boolean isTodaySaturday = (todayDow == 6);

        renderRows(context, llRows, isTodaySaturday);
        if (isTodaySaturday) {
            TabLayout.Tab tab = tabLayout.getTabAt(1);
            if (tab != null) tab.select();
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                renderRows(context, llRows, tab.getPosition() == 1);
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        if (dialog.getWindow() != null) {
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int h = (int) (dm.heightPixels * 0.82);
            int w = (int) (dm.widthPixels * 0.92);
            dialog.getWindow().setLayout(w, h);
        }
    }

    private static void renderRows(Context context, LinearLayout container, boolean isSaturday) {
        container.removeAllViews();
        int dow = isSaturday ? 6 : 1;
        String[][] times = Constants.getLessonTimes(dow);
        LocalDate today = DateUtils.todayMoscow();
        int todayDow = DateUtils.toScheduleDayOfWeek(today);
        LocalTime now = DateUtils.nowTimeMoscow();
        boolean checkCurrent = (isSaturday && todayDow == 6) || (!isSaturday && todayDow >= 1 && todayDow <= 5);

        for (int i = 0; i < times.length; i++) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_bell_row, container, false);
            TextView tvNum = row.findViewById(R.id.tvBellNumber);
            TextView tvTime = row.findViewById(R.id.tvBellTime);
            TextView tvBreak = row.findViewById(R.id.tvBellBreak);
            TextView tvBadge = row.findViewById(R.id.tvBellCurrentBadge);

            int n = i + 1;
            tvNum.setText(String.valueOf(n));
            tvNum.setBackground(createCircleDrawable(ContextCompat.getColor(context, R.color.primary), 0.18f));

            String startStr = times[i][0];
            String endStr = times[i][1];
            tvTime.setText(startStr + " – " + endStr);

            String breakText;
            if (i < times.length - 1) {
                String nextStart = times[i + 1][0];
                long breakMins = java.time.Duration.between(
                        LocalTime.parse(endStr, TF), LocalTime.parse(nextStart, TF)).toMinutes();
                if (breakMins >= 40) {
                    breakText = "Большая перемена: " + breakMins + " мин (" + endStr + " – " + nextStart + ")";
                } else {
                    breakText = "Перемена: " + breakMins + " мин (" + endStr + " – " + nextStart + ")";
                }
            } else {
                breakText = "Окончание занятий дня";
            }
            tvBreak.setText(breakText);

            if (checkCurrent) {
                LocalTime start = LocalTime.parse(startStr, TF);
                LocalTime end = LocalTime.parse(endStr, TF);
                if (!now.isBefore(start) && !now.isAfter(end)) {
                    tvBadge.setVisibility(View.VISIBLE);
                    row.setBackground(createRoundDrawable(ContextCompat.getColor(context, R.color.state_current_bg), 1f, 8));
                }
            }

            container.addView(row);
        }
    }

    private static GradientDrawable createCircleDrawable(int color, float alpha) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.argb((int) (alpha * 255), Color.red(color), Color.green(color), Color.blue(color)));
        return gd;
    }

    private static GradientDrawable createRoundDrawable(int color, float alpha, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(Color.argb((int) (alpha * 255), Color.red(color), Color.green(color), Color.blue(color)));
        gd.setCornerRadius(radiusDp * 3);
        return gd;
    }
}
