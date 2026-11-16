package com.university.schedule.ui.dialog;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.university.schedule.R;
import com.university.schedule.data.db.NoteDao;
import com.university.schedule.model.LessonNote;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.DateUtils;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Liquid Glass хаб всех домашних заданий и заметок к парам (B1).
 */
public final class HomeworkHubDialog {

    private static final int FILTER_PENDING = 0;
    private static final int FILTER_ALL = 1;
    private static final int FILTER_DONE = 2;

    private HomeworkHubDialog() {
    }

    public static void show(@NonNull AppCompatActivity activity, @Nullable Runnable onNotesChanged) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_homework_hub, null);
        View rootCard = dialogView.findViewById(R.id.cardHomeworkHubRoot);
        View filterCapsule = dialogView.findViewById(R.id.capsuleHomeworkFilter);
        TextView tvPendingBadge = dialogView.findViewById(R.id.tvHomeworkPendingBadge);
        ImageButton btnCloseTop = dialogView.findViewById(R.id.btnCloseHomeworkHub);
        MaterialButton btnCloseBottom = dialogView.findViewById(R.id.btnCloseHomeworkBottom);

        TextView btnPending = dialogView.findViewById(R.id.btnHwFilterPending);
        TextView btnAll = dialogView.findViewById(R.id.btnHwFilterAll);
        TextView btnDone = dialogView.findViewById(R.id.btnHwFilterDone);

        RecyclerView rvNotes = dialogView.findViewById(R.id.rvHomeworkNotes);
        LinearLayout layoutEmpty = dialogView.findViewById(R.id.layoutHomeworkEmpty);
        TextView tvEmptyTitle = dialogView.findViewById(R.id.tvHomeworkEmptyTitle);

        GlassSurfaceHelper.applyGlassCard(filterCapsule, 999f, false);
        rvNotes.setLayoutManager(new LinearLayoutManager(activity));

        NoteDao noteDao = new NoteDao(activity);
        ExecutorService ex = Executors.newSingleThreadExecutor();
        List<LessonNote> allNotes = new ArrayList<>();
        final int[] currentFilter = new int[]{FILTER_PENDING};

        final HomeworkAdapter[] adapterRef = new HomeworkAdapter[1];

        Runnable applyFilterAndRender = () -> {
            updateFilterSegments(activity, currentFilter[0], btnPending, btnAll, btnDone);

            int pendingCount = 0;
            List<LessonNote> filtered = new ArrayList<>();
            for (LessonNote n : allNotes) {
                if (!n.isCompleted()) pendingCount++;
                if (currentFilter[0] == FILTER_PENDING && !n.isCompleted()) {
                    filtered.add(n);
                } else if (currentFilter[0] == FILTER_DONE && n.isCompleted()) {
                    filtered.add(n);
                } else if (currentFilter[0] == FILTER_ALL) {
                    filtered.add(n);
                }
            }

            tvPendingBadge.setText(String.valueOf(pendingCount));
            tvPendingBadge.setBackground(createPillBackground(
                    activity, ContextCompat.getColor(activity, R.color.glass_capsule), 999f));

            if (filtered.isEmpty()) {
                rvNotes.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
                if (currentFilter[0] == FILTER_PENDING && !allNotes.isEmpty()) {
                    tvEmptyTitle.setText("Все задания выполнены!");
                } else if (currentFilter[0] == FILTER_DONE) {
                    tvEmptyTitle.setText("Выполненных заданий пока нет");
                } else {
                    tvEmptyTitle.setText("Заданий пока нет");
                }
            } else {
                rvNotes.setVisibility(View.VISIBLE);
                layoutEmpty.setVisibility(View.GONE);
                adapterRef[0].setData(filtered);
            }
        };

        Runnable reloadFromDb = () -> ex.execute(() -> {
            List<LessonNote> loaded = noteDao.getAllNotes();
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                allNotes.clear();
                allNotes.addAll(loaded);
                applyFilterAndRender.run();
            });
        });

        adapterRef[0] = new HomeworkAdapter(activity, new HomeworkAdapter.Listener() {
            @Override
            public void onToggleCompleted(LessonNote note, boolean completed) {
                ex.execute(() -> {
                    noteDao.setCompleted(note.getDate(), note.getLessonNumber(), completed);
                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        note.setCompleted(completed);
                        applyFilterAndRender.run();
                        if (onNotesChanged != null) onNotesChanged.run();
                    });
                });
            }

            @Override
            public void onDelete(LessonNote note) {
                ex.execute(() -> {
                    noteDao.delete(note.getDate(), note.getLessonNumber());
                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        allNotes.remove(note);
                        applyFilterAndRender.run();
                        if (onNotesChanged != null) onNotesChanged.run();
                        Toast.makeText(activity, "Заметка удалена", Toast.LENGTH_SHORT).show();
                    });
                });
            }

            @Override
            public void onPhotoClick(String path) {
                LessonNoteDialog.showFullScreenPhoto(activity, path);
            }
        });
        rvNotes.setAdapter(adapterRef[0]);

        View.OnClickListener filterClick = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (v.getId() == R.id.btnHwFilterPending) currentFilter[0] = FILTER_PENDING;
            else if (v.getId() == R.id.btnHwFilterDone) currentFilter[0] = FILTER_DONE;
            else currentFilter[0] = FILTER_ALL;
            applyFilterAndRender.run();
        };
        btnPending.setOnClickListener(filterClick);
        btnAll.setOnClickListener(filterClick);
        btnDone.setOnClickListener(filterClick);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .create();

        dialog.setOnDismissListener(d -> ex.shutdown());
        btnCloseTop.setOnClickListener(v -> dialog.dismiss());
        btnCloseBottom.setOnClickListener(v -> dialog.dismiss());

        reloadFromDb.run();

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, rootCard);

        Window window = dialog.getWindow();
        if (window != null) {
            int maxWidth = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 460f, activity.getResources().getDisplayMetrics());
            int width = Math.min((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f), maxWidth);
            int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.84f);
            window.setLayout(width, height);
        }
    }

    private static void updateFilterSegments(AppCompatActivity activity, int activeFilter,
                                             TextView btnPending, TextView btnAll, TextView btnDone) {
        int accent = ContextCompat.getColor(activity, R.color.glass_accent);
        int inactiveText = ContextCompat.getColor(activity, R.color.glass_text_secondary);

        applySegmentStyle(activity, btnPending, activeFilter == FILTER_PENDING, accent, inactiveText);
        applySegmentStyle(activity, btnAll, activeFilter == FILTER_ALL, accent, inactiveText);
        applySegmentStyle(activity, btnDone, activeFilter == FILTER_DONE, accent, inactiveText);
    }

    private static void applySegmentStyle(AppCompatActivity activity, TextView pill, boolean selected,
                                          @ColorInt int accentColor, @ColorInt int inactiveTextColor) {
        if (selected) {
            pill.setBackground(createPillBackground(activity, accentColor, 999f));
            pill.setTextColor(Color.WHITE);
        } else {
            pill.setBackground(null);
            pill.setTextColor(inactiveTextColor);
        }
    }

    private static GradientDrawable createPillBackground(AppCompatActivity activity,
                                                         @ColorInt int fillColor,
                                                         float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, radiusDp, activity.getResources().getDisplayMetrics()));
        return gd;
    }

    private static class HomeworkAdapter extends RecyclerView.Adapter<HomeworkAdapter.ViewHolder> {

        interface Listener {
            void onToggleCompleted(LessonNote note, boolean completed);
            void onDelete(LessonNote note);
            void onPhotoClick(String path);
        }

        private final AppCompatActivity activity;
        private final List<LessonNote> items = new ArrayList<>();
        private final Listener listener;

        HomeworkAdapter(AppCompatActivity activity, Listener listener) {
            this.activity = activity;
            this.listener = listener;
        }

        void setData(List<LessonNote> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(activity).inflate(R.layout.item_homework_note, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LessonNote note = items.get(position);
            boolean done = note.isCompleted();

            GlassSurfaceHelper.applyGlassCard(holder.cardRoot, 16f, !done);

            String subject = note.getSubjectName() != null && !note.getSubjectName().isEmpty()
                    ? note.getSubjectName()
                    : "Предмет";
            holder.tvSubject.setText(subject);
            setStrikeThrough(holder.tvSubject, done);
            holder.tvSubject.setAlpha(done ? 0.65f : 1f);

            String dateDisplay = formatMetaDate(note.getDate());
            holder.tvMeta.setText(dateDisplay + " · " + note.getLessonNumber() + " пара");

            String text = note.getNoteText() != null ? note.getNoteText().trim() : "";
            if (text.isEmpty()) {
                holder.tvText.setVisibility(View.GONE);
            } else {
                holder.tvText.setVisibility(View.VISIBLE);
                holder.tvText.setText(text);
                setStrikeThrough(holder.tvText, done);
                holder.tvText.setAlpha(done ? 0.6f : 1f);
            }

            String imgPath = note.getImagePath();
            if (imgPath != null && new File(imgPath).exists()) {
                holder.ivPhotoThumb.setVisibility(View.VISIBLE);
                LessonNoteDialog.displayImageThumb(holder.ivPhotoThumb, imgPath);
                holder.ivPhotoThumb.setOnClickListener(v -> listener.onPhotoClick(imgPath));
            } else {
                holder.ivPhotoThumb.setVisibility(View.GONE);
                holder.ivPhotoThumb.setOnClickListener(null);
            }

            holder.cbDone.setOnCheckedChangeListener(null);
            holder.cbDone.setChecked(done);
            holder.cbDone.setOnCheckedChangeListener((buttonView, isChecked) -> {
                buttonView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                listener.onToggleCompleted(note, isChecked);
            });

            holder.btnDelete.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                listener.onDelete(note);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private static void setStrikeThrough(TextView tv, boolean strike) {
            if (strike) {
                tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }
        }

        private static String formatMetaDate(String isoDate) {
            if (isoDate == null || isoDate.isEmpty()) return "";
            try {
                return DateUtils.formatDisplayDate(LocalDate.parse(isoDate));
            } catch (Exception e) {
                return isoDate;
            }
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final View cardRoot;
            final MaterialCheckBox cbDone;
            final TextView tvSubject;
            final TextView tvMeta;
            final TextView tvText;
            final ImageView ivPhotoThumb;
            final ImageButton btnDelete;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardRoot = itemView.findViewById(R.id.cardHwItemRoot);
                cbDone = itemView.findViewById(R.id.cbHwDone);
                tvSubject = itemView.findViewById(R.id.tvHwSubject);
                tvMeta = itemView.findViewById(R.id.tvHwMeta);
                tvText = itemView.findViewById(R.id.tvHwText);
                ivPhotoThumb = itemView.findViewById(R.id.ivHwPhotoThumb);
                btnDelete = itemView.findViewById(R.id.btnHwDelete);
            }
        }
    }
}
