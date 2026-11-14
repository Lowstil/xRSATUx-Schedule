package com.university.schedule.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.ui.view.CircularProgressRingView;
import com.university.schedule.util.AttendanceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Liquid Glass диалог статистики посещаемости и бюджета пропусков ("Н-ок").
 */
public class AttendanceDialog {

    public interface OnAttendanceChangedListener {
        void onAttendanceChanged();
    }

    public static void show(@NonNull AppCompatActivity activity,
                            @Nullable String groupName,
                            @Nullable Runnable onChanged) {
        String resolvedGroup = groupName;
        if (resolvedGroup == null || resolvedGroup.trim().isEmpty()) {
            resolvedGroup = ScheduleRepository.getInstance(activity).getSelectionName();
        }
        if (resolvedGroup == null || resolvedGroup.trim().isEmpty()) {
            return;
        }
        showInternal(activity, resolvedGroup.trim(), onChanged);
    }

    public static void show(@NonNull Context context,
                            @Nullable OnAttendanceChangedListener listener) {
        String groupName = ScheduleRepository.getInstance(context).getSelectionName();
        if (groupName == null || groupName.trim().isEmpty()) {
            return;
        }
        Runnable callback = listener != null ? listener::onAttendanceChanged : null;
        showInternal(context, groupName.trim(), callback);
    }

    private static void showInternal(@NonNull Context context,
                                     @NonNull String groupName,
                                     @Nullable Runnable onChanged) {
        AttendanceManager mgr = new AttendanceManager(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_attendance, null);

        View rootCard = dialogView.findViewById(R.id.cardAttendanceRoot);
        View heroCard = dialogView.findViewById(R.id.cardAttendanceHero);
        View capsuleLimit = dialogView.findViewById(R.id.capsuleSkipLimit);

        GlassSurfaceHelper.applyGlassCard(heroCard, 22f, true);
        GlassSurfaceHelper.applyGlassCard(capsuleLimit, 999f, false);

        TextView tvSubtitle = dialogView.findViewById(R.id.tvAttendanceGroupSubtitle);
        tvSubtitle.setText("Группа " + groupName + " · бюджет пропусков");

        ImageButton btnCloseHeader = dialogView.findViewById(R.id.btnCloseAttendanceHeader);
        MaterialButton btnClose = dialogView.findViewById(R.id.btnCloseAttendance);

        CircularProgressRingView ringOverall = dialogView.findViewById(R.id.ringOverallAttendance);
        ringOverall.setStrokeWidthDp(9.5f);
        ringOverall.setCenterTextSizeSp(18f);

        TextView tvHealthBadge = dialogView.findViewById(R.id.tvOverallHealthBadge);
        TextView tvOverallSummary = dialogView.findViewById(R.id.tvOverallSummaryText);
        TextView tvOverallBudget = dialogView.findViewById(R.id.tvOverallBudgetText);
        TextView tvSubjectsCount = dialogView.findViewById(R.id.tvSubjectsCountBadge);

        TextView btnLimit20 = dialogView.findViewById(R.id.btnSkipLimit20);
        TextView btnLimit25 = dialogView.findViewById(R.id.btnSkipLimit25);
        TextView btnLimit30 = dialogView.findViewById(R.id.btnSkipLimit30);

        RecyclerView rvSubjects = dialogView.findViewById(R.id.rvAttendanceSubjects);
        LinearLayout layoutEmpty = dialogView.findViewById(R.id.layoutAttendanceEmpty);

        rvSubjects.setLayoutManager(new LinearLayoutManager(context));

        final AttendanceSubjectAdapter[] adapterRef = new AttendanceSubjectAdapter[1];
        Runnable refreshUi = () -> updateUi(
                context, mgr, groupName,
                ringOverall, tvHealthBadge, tvOverallSummary, tvOverallBudget, tvSubjectsCount,
                btnLimit20, btnLimit25, btnLimit30,
                rvSubjects, layoutEmpty, adapterRef[0]
        );

        adapterRef[0] = new AttendanceSubjectAdapter(context, new ArrayList<>(), (subject, delta) -> {
            if (delta < 0 && subject.missedCount <= 0) return;
            mgr.adjustManualSkips(groupName, subject.subjectName, delta);
            refreshUi.run();
            if (onChanged != null) onChanged.run();
        });
        rvSubjects.setAdapter(adapterRef[0]);

        View.OnClickListener limitClickListener = v -> {
            int pct = 30;
            if (v.getId() == R.id.btnSkipLimit20) pct = 20;
            else if (v.getId() == R.id.btnSkipLimit25) pct = 25;
            mgr.setSkipLimitPercent(pct);
            refreshUi.run();
        };
        btnLimit20.setOnClickListener(limitClickListener);
        btnLimit25.setOnClickListener(limitClickListener);
        btnLimit30.setOnClickListener(limitClickListener);

        refreshUi.run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .create();

        btnCloseHeader.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        GlassSurfaceHelper.applyGlassDialog(dialog, rootCard);

        Window window = dialog.getWindow();
        if (window != null) {
            int maxWidth = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 460f, context.getResources().getDisplayMetrics());
            int width = Math.min((int) (context.getResources().getDisplayMetrics().widthPixels * 0.94f), maxWidth);
            int height = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.86f);
            window.setLayout(width, height);
        }
    }

    private static void updateUi(Context context, AttendanceManager mgr, String groupName,
                                 CircularProgressRingView ringOverall,
                                 TextView tvHealthBadge,
                                 TextView tvOverallSummary,
                                 TextView tvOverallBudget,
                                 TextView tvSubjectsCount,
                                 TextView btnLimit20, TextView btnLimit25, TextView btnLimit30,
                                 RecyclerView rvSubjects,
                                 LinearLayout layoutEmpty,
                                 AttendanceSubjectAdapter adapter) {
        int limitPct = mgr.getSkipLimitPercent();
        updateLimitSegments(context, limitPct, btnLimit20, btnLimit25, btnLimit30);

        AttendanceManager.OverallAttendance overall = mgr.getOverallStats(groupName);
        List<AttendanceManager.SubjectAttendance> subjects = mgr.getSubjectStatsList(groupName);

        AttendanceManager.BudgetHealth health = AttendanceManager.evaluateBudgetHealth(
                overall.missedCount, overall.totalPlanned, limitPct);
        int healthColor = resolveHealthColor(context, health);

        ringOverall.setRingColor(healthColor);
        ringOverall.setProgress(overall.attendancePercent, true);

        String healthLabel;
        if (health == AttendanceManager.BudgetHealth.EXCEEDED) {
            healthLabel = "Лимит превышен";
        } else if (health == AttendanceManager.BudgetHealth.WARNING) {
            healthLabel = "Близко к лимиту";
        } else {
            healthLabel = "В норме";
        }
        tvHealthBadge.setText(healthLabel);
        tvHealthBadge.setTextColor(healthColor);
        tvHealthBadge.setBackground(createPillBackground(context, withAlpha(healthColor, 36), 999f));

        tvOverallSummary.setText("Посещено: " + overall.attendedCount
                + " · Н-ок: " + overall.missedCount
                + " · Всего: " + overall.totalPlanned);

        tvOverallBudget.setText(AttendanceManager.formatBudgetStatus(
                overall.missedCount, overall.totalPlanned, limitPct));
        tvOverallBudget.setTextColor(healthColor);

        tvSubjectsCount.setText(String.valueOf(subjects.size()));
        tvSubjectsCount.setBackground(createPillBackground(
                context, ContextCompat.getColor(context, R.color.glass_capsule), 999f));

        if (subjects.isEmpty()) {
            rvSubjects.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            rvSubjects.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            adapter.setSkipLimitPercent(limitPct);
            adapter.setData(subjects);
        }
    }

    private static void updateLimitSegments(Context context, int activePct,
                                            TextView btn20, TextView btn25, TextView btn30) {
        int accent = ContextCompat.getColor(context, R.color.glass_accent);
        int inactiveText = ContextCompat.getColor(context, R.color.glass_text_secondary);

        applySegmentStyle(context, btn20, activePct == 20, accent, inactiveText);
        applySegmentStyle(context, btn25, activePct == 25, accent, inactiveText);
        applySegmentStyle(context, btn30, activePct == 30, accent, inactiveText);
    }

    private static void applySegmentStyle(Context context, TextView pill, boolean selected,
                                          @ColorInt int accentColor, @ColorInt int inactiveTextColor) {
        if (selected) {
            pill.setBackground(createPillBackground(context, accentColor, 999f));
            pill.setTextColor(Color.WHITE);
        } else {
            pill.setBackground(null);
            pill.setTextColor(inactiveTextColor);
        }
    }

    @ColorInt
    private static int resolveHealthColor(Context context, AttendanceManager.BudgetHealth health) {
        switch (health) {
            case EXCEEDED:
                return ContextCompat.getColor(context, R.color.glass_danger);
            case WARNING:
                return ContextCompat.getColor(context, R.color.glass_warning);
            case SAFE:
            default:
                return ContextCompat.getColor(context, R.color.glass_success);
        }
    }

    private static GradientDrawable createPillBackground(Context context, @ColorInt int fillColor, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, radiusDp, context.getResources().getDisplayMetrics()));
        return gd;
    }

    @ColorInt
    private static int withAlpha(@ColorInt int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private interface OnAdjustListener {
        void onAdjust(AttendanceManager.SubjectAttendance subject, int delta);
    }

    private static class AttendanceSubjectAdapter extends RecyclerView.Adapter<AttendanceSubjectAdapter.ViewHolder> {
        private final Context context;
        private final List<AttendanceManager.SubjectAttendance> items;
        private final OnAdjustListener adjustListener;
        private int skipLimitPercent = AttendanceManager.DEFAULT_SKIP_LIMIT_PCT;

        AttendanceSubjectAdapter(Context context,
                                 List<AttendanceManager.SubjectAttendance> items,
                                 OnAdjustListener adjustListener) {
            this.context = context;
            this.items = items;
            this.adjustListener = adjustListener;
        }

        void setSkipLimitPercent(int skipLimitPercent) {
            this.skipLimitPercent = skipLimitPercent;
        }

        void setData(List<AttendanceManager.SubjectAttendance> newItems) {
            this.items.clear();
            this.items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_attendance_subject, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AttendanceManager.SubjectAttendance sa = items.get(position);
            AttendanceManager.BudgetHealth health = AttendanceManager.evaluateBudgetHealth(
                    sa.missedCount, sa.totalPlanned, skipLimitPercent);
            int healthColor = resolveHealthColor(context, health);

            GlassSurfaceHelper.applyGlassCard(
                    holder.cardRoot, 20f, health == AttendanceManager.BudgetHealth.EXCEEDED);
            GlassSurfaceHelper.applyGlassCard(holder.capsuleStepper, 999f, false);

            holder.ringSubject.setRingColor(healthColor);
            holder.ringSubject.setProgress(sa.attendancePercent, false);

            holder.tvSubjectName.setText(sa.subjectName);
            holder.tvDetails.setText("Посещено: " + sa.attendedCount
                    + " · Н-ок: " + sa.missedCount
                    + " · Всего: " + sa.totalPlanned);

            holder.tvSkipCount.setText(String.valueOf(sa.missedCount));

            boolean canDecrement = sa.missedCount > 0;
            holder.btnMinus.setEnabled(canDecrement);
            holder.btnMinus.setAlpha(canDecrement ? 1f : 0.35f);

            int skipPct = AttendanceManager.computeSkipPercent(sa.missedCount, sa.totalPlanned);
            holder.tvSkipPctBadge.setText("Н-ки: " + skipPct + "%");
            holder.tvSkipPctBadge.setTextColor(health == AttendanceManager.BudgetHealth.EXCEEDED
                    ? healthColor
                    : ContextCompat.getColor(context, R.color.glass_text_secondary));

            holder.tvBudgetStatus.setText(AttendanceManager.formatBudgetStatus(
                    sa.missedCount, sa.totalPlanned, skipLimitPercent));
            holder.tvBudgetStatus.setTextColor(healthColor);
            holder.tvBudgetStatus.setBackground(
                    createPillBackground(context, withAlpha(healthColor, 32), 10f));

            holder.btnMinus.setOnClickListener(v -> {
                if (adjustListener != null && sa.missedCount > 0) {
                    adjustListener.onAdjust(sa, -1);
                }
            });

            holder.btnPlus.setOnClickListener(v -> {
                if (adjustListener != null) {
                    adjustListener.onAdjust(sa, 1);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final View cardRoot;
            final View capsuleStepper;
            final CircularProgressRingView ringSubject;
            final TextView tvSubjectName;
            final TextView tvDetails;
            final TextView tvSkipCount;
            final TextView btnMinus;
            final TextView btnPlus;
            final TextView tvBudgetStatus;
            final TextView tvSkipPctBadge;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardRoot = itemView.findViewById(R.id.cardSubjectItem);
                capsuleStepper = itemView.findViewById(R.id.capsuleAttStepper);
                ringSubject = itemView.findViewById(R.id.ringSubjectAttendance);
                ringSubject.setStrokeWidthDp(4.5f);
                ringSubject.setCenterTextSizeSp(11f);
                tvSubjectName = itemView.findViewById(R.id.tvAttSubjectName);
                tvDetails = itemView.findViewById(R.id.tvAttDetails);
                tvSkipCount = itemView.findViewById(R.id.tvAttSkipCount);
                btnMinus = itemView.findViewById(R.id.btnAttMinus);
                btnPlus = itemView.findViewById(R.id.btnAttPlus);
                tvBudgetStatus = itemView.findViewById(R.id.tvAttBudgetStatus);
                tvSkipPctBadge = itemView.findViewById(R.id.tvAttSkipPctBadge);
            }
        }
    }
}
