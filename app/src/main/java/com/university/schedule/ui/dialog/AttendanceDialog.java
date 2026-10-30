package com.university.schedule.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.util.AttendanceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Диалог статистики посещаемости и учёта пропусков ("энок").
 */
public class AttendanceDialog {

    public interface OnAttendanceChangedListener {
        void onAttendanceChanged();
    }

    public static void show(Context context, OnAttendanceChangedListener listener) {
        ScheduleRepository repo = ScheduleRepository.getInstance(context);
        String groupName = repo.getSelectionName();
        if (groupName == null || groupName.isEmpty()) {
            return;
        }

        AttendanceManager mgr = new AttendanceManager(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_attendance, null);

        TextView tvSubtitle = dialogView.findViewById(R.id.tvAttendanceGroupSubtitle);
        tvSubtitle.setText("Группа: " + groupName);

        ImageButton btnCloseHeader = dialogView.findViewById(R.id.btnCloseAttendanceHeader);
        MaterialButton btnClose = dialogView.findViewById(R.id.btnCloseAttendance);

        TextView tvOverallPercent = dialogView.findViewById(R.id.tvOverallPercent);
        ProgressBar pbOverall = dialogView.findViewById(R.id.pbOverallAttendance);
        TextView tvOverallSummary = dialogView.findViewById(R.id.tvOverallSummaryText);

        RecyclerView rvSubjects = dialogView.findViewById(R.id.rvAttendanceSubjects);
        TextView tvEmpty = dialogView.findViewById(R.id.tvAttendanceEmpty);

        rvSubjects.setLayoutManager(new LinearLayoutManager(context));
        AttendanceSubjectAdapter adapter = new AttendanceSubjectAdapter(context, new ArrayList<>(), (subject, delta) -> {
            mgr.adjustManualSkips(groupName, subject.subjectName, delta);
            updateUi(context, mgr, groupName, tvOverallPercent, pbOverall, tvOverallSummary, rvSubjects, tvEmpty, null);
            if (listener != null) listener.onAttendanceChanged();
        });
        rvSubjects.setAdapter(adapter);

        updateUi(context, mgr, groupName, tvOverallPercent, pbOverall, tvOverallSummary, rvSubjects, tvEmpty, adapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .create();

        btnCloseHeader.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.94);
            int height = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.88);
            window.setLayout(width, height);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.70f);
        }
    }

    private static void updateUi(Context context, AttendanceManager mgr, String groupName,
                                 TextView tvOverallPercent, ProgressBar pbOverall, TextView tvOverallSummary,
                                 RecyclerView rvSubjects, TextView tvEmpty, AttendanceSubjectAdapter adapter) {
        AttendanceManager.OverallAttendance overall = mgr.getOverallStats(groupName);
        List<AttendanceManager.SubjectAttendance> subjects = mgr.getSubjectStatsList(groupName);

        tvOverallPercent.setText(overall.attendancePercent + "%");
        pbOverall.setProgress(overall.attendancePercent);

        if (overall.attendancePercent >= 80) {
            tvOverallPercent.setTextColor(ContextCompat.getColor(context, R.color.primary));
            pbOverall.setProgressDrawable(ContextCompat.getDrawable(context, R.drawable.bg_progress_green));
        } else if (overall.attendancePercent >= 60) {
            tvOverallPercent.setTextColor(ContextCompat.getColor(context, R.color.progress_segment_orange));
            pbOverall.setProgressDrawable(ContextCompat.getDrawable(context, R.drawable.bg_progress_orange));
        } else {
            tvOverallPercent.setTextColor(ContextCompat.getColor(context, R.color.error));
        }

        tvOverallSummary.setText("Посещено: " + overall.attendedCount + " из " + overall.totalPlanned
                + " пар · " + AttendanceManager.formatSkips(overall.missedCount));

        if (subjects.isEmpty()) {
            rvSubjects.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            rvSubjects.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            if (adapter != null) {
                adapter.setData(subjects);
            } else if (rvSubjects.getAdapter() instanceof AttendanceSubjectAdapter) {
                ((AttendanceSubjectAdapter) rvSubjects.getAdapter()).setData(subjects);
            }
        }
    }

    private interface OnAdjustListener {
        void onAdjust(AttendanceManager.SubjectAttendance subject, int delta);
    }

    private static class AttendanceSubjectAdapter extends RecyclerView.Adapter<AttendanceSubjectAdapter.ViewHolder> {
        private final Context context;
        private final List<AttendanceManager.SubjectAttendance> items;
        private final OnAdjustListener adjustListener;

        AttendanceSubjectAdapter(Context context, List<AttendanceManager.SubjectAttendance> items, OnAdjustListener adjustListener) {
            this.context = context;
            this.items = items;
            this.adjustListener = adjustListener;
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
            holder.tvSubjectName.setText(sa.subjectName);
            holder.tvPercentBadge.setText(sa.attendancePercent + "%");
            holder.pbSubject.setProgress(sa.attendancePercent);

            if (sa.attendancePercent >= 80) {
                holder.tvPercentBadge.setTextColor(ContextCompat.getColor(context, R.color.primary));
                holder.pbSubject.setProgressDrawable(ContextCompat.getDrawable(context, R.drawable.bg_progress_green));
            } else if (sa.attendancePercent >= 60) {
                holder.tvPercentBadge.setTextColor(ContextCompat.getColor(context, R.color.progress_segment_orange));
                holder.pbSubject.setProgressDrawable(ContextCompat.getDrawable(context, R.drawable.bg_progress_orange));
            } else {
                holder.tvPercentBadge.setTextColor(ContextCompat.getColor(context, R.color.error));
            }

            String details = "Посещено " + sa.attendedCount + " из " + sa.totalPlanned
                    + " · " + AttendanceManager.formatSkips(sa.missedCount);
            holder.tvDetails.setText(details);

            if (sa.hasWarning()) {
                holder.tvWarningBadge.setVisibility(View.VISIBLE);
                holder.tvWarningBadge.setText("🟡 Внимание: уже " + AttendanceManager.formatSkips(sa.missedCount) + ", пора ходить!");
            } else {
                holder.tvWarningBadge.setVisibility(View.GONE);
            }

            holder.btnMinus.setOnClickListener(v -> {
                if (adjustListener != null) adjustListener.onAdjust(sa, -1);
            });

            holder.btnPlus.setOnClickListener(v -> {
                if (adjustListener != null) adjustListener.onAdjust(sa, 1);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSubjectName, tvPercentBadge, tvDetails, tvWarningBadge;
            ProgressBar pbSubject;
            MaterialButton btnMinus, btnPlus;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvSubjectName = itemView.findViewById(R.id.tvAttSubjectName);
                tvPercentBadge = itemView.findViewById(R.id.tvAttPercentBadge);
                tvDetails = itemView.findViewById(R.id.tvAttDetails);
                tvWarningBadge = itemView.findViewById(R.id.tvAttWarningBadge);
                pbSubject = itemView.findViewById(R.id.pbAttSubject);
                btnMinus = itemView.findViewById(R.id.btnAttMinus);
                btnPlus = itemView.findViewById(R.id.btnAttPlus);
            }
        }
    }
}
