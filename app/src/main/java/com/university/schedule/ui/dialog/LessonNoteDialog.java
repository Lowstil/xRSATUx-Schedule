package com.university.schedule.ui.dialog;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.LessonNote;
import com.university.schedule.model.ScheduleItem;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.DateUtils;

import java.io.File;
import java.time.LocalDate;

/**
 * Liquid Glass диалог заметки / домашнего задания с поддержкой прикрепления фото.
 */
public final class LessonNoteDialog {

    public interface PhotoCallback {
        void onPhotoReady(@Nullable String localPath);
    }

    public interface PhotoPickerHost {
        void pickPhoto(@NonNull PhotoCallback cb);
    }

    private LessonNoteDialog() {
    }

    public static void show(@NonNull AppCompatActivity activity,
                            @NonNull ScheduleItem item,
                            @Nullable LocalDate date,
                            @Nullable LessonNote existingNote,
                            @NonNull ScheduleRepository repo,
                            @Nullable PhotoPickerHost photoHost,
                            @Nullable Runnable onSaved) {
        if (activity.isFinishing() || date == null) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_lesson_note, null);
        TextView tvSubject = dialogView.findViewById(R.id.tvNoteDialogSubject);
        TextView tvDetails = dialogView.findViewById(R.id.tvNoteDialogDetails);
        EditText etNote = dialogView.findViewById(R.id.etNoteText);
        CheckBox cbCompleted = dialogView.findViewById(R.id.cbNoteCompleted);
        View cardNotePhoto = dialogView.findViewById(R.id.cardNotePhoto);
        ImageView ivNotePhotoPreview = dialogView.findViewById(R.id.ivNotePhotoPreview);
        View btnRemovePhoto = dialogView.findViewById(R.id.btnRemovePhoto);
        MaterialButton btnAttachPhoto = dialogView.findViewById(R.id.btnAttachPhoto);
        View btnDelete = dialogView.findViewById(R.id.btnDeleteNote);
        View btnCancel = dialogView.findViewById(R.id.btnCancelNote);
        View btnSave = dialogView.findViewById(R.id.btnSaveNote);

        tvSubject.setText(item.getSubjectName());
        String det = item.getLessonNumber() + " пара · " + DateUtils.formatDisplayDate(date);
        if (item.getRoom() != null && !item.getRoom().isEmpty()) {
            det += " · ауд. " + item.getRoom();
        }
        tvDetails.setText(det);

        final String[] currentPhotoPath = new String[]{
                existingNote != null ? existingNote.getImagePath() : null
        };

        Runnable updatePhotoUi = () -> {
            if (currentPhotoPath[0] != null && new File(currentPhotoPath[0]).exists()) {
                cardNotePhoto.setVisibility(View.VISIBLE);
                displayImageThumb(ivNotePhotoPreview, currentPhotoPath[0]);
                btnAttachPhoto.setText("Сменить фото");
            } else {
                cardNotePhoto.setVisibility(View.GONE);
                btnAttachPhoto.setText("Прикрепить фото");
            }
        };

        boolean hasContent = existingNote != null
                && ((existingNote.getNoteText() != null && !existingNote.getNoteText().isEmpty())
                || (existingNote.getImagePath() != null && !existingNote.getImagePath().isEmpty()));
        if (hasContent) {
            if (existingNote.getNoteText() != null) {
                etNote.setText(existingNote.getNoteText());
            }
            cbCompleted.setChecked(existingNote.isCompleted());
            cbCompleted.setVisibility(View.VISIBLE);
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            cbCompleted.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);
        }

        updatePhotoUi.run();

        if (photoHost != null) {
            btnAttachPhoto.setOnClickListener(v -> photoHost.pickPhoto(path -> {
                currentPhotoPath[0] = path;
                updatePhotoUi.run();
            }));
        } else {
            btnAttachPhoto.setVisibility(View.GONE);
        }

        btnRemovePhoto.setOnClickListener(v -> {
            currentPhotoPath[0] = null;
            updatePhotoUi.run();
        });

        ivNotePhotoPreview.setOnClickListener(v -> {
            if (currentPhotoPath[0] != null) {
                showFullScreenPhoto(activity, currentPhotoPath[0]);
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDelete.setOnClickListener(v -> {
            String dateIso = date.toString();
            repo.deleteNote(dateIso, item.getLessonNumber());
            if (onSaved != null) onSaved.run();
            dialog.dismiss();
            Toast.makeText(activity, "Заметка удалена", Toast.LENGTH_SHORT).show();
        });

        btnSave.setOnClickListener(v -> {
            String text = etNote.getText() != null ? etNote.getText().toString().trim() : "";
            String dateIso = date.toString();
            if (text.isEmpty() && currentPhotoPath[0] == null) {
                repo.deleteNote(dateIso, item.getLessonNumber());
            } else {
                repo.saveNote(
                        dateIso,
                        item.getLessonNumber(),
                        item.getSubjectName(),
                        text,
                        currentPhotoPath[0],
                        cbCompleted.isChecked()
                );
            }
            if (onSaved != null) onSaved.run();
            dialog.dismiss();
            Toast.makeText(activity, "Заметка сохранена", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, dialogView);
    }

    static void displayImageThumb(@NonNull ImageView iv, @Nullable String path) {
        if (path == null || !new File(path).exists()) {
            iv.setImageDrawable(null);
            return;
        }
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            opts.inSampleSize = calculateInSampleSize(opts, 480, 360);
            opts.inJustDecodeBounds = false;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            iv.setImageBitmap(bmp);
        } catch (Throwable t) {
            iv.setImageDrawable(null);
        }
    }

    static void showFullScreenPhoto(@NonNull AppCompatActivity activity, @Nullable String path) {
        if (path == null || !new File(path).exists() || activity.isFinishing()) return;
        Dialog d = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(activity);
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp != null) {
                iv.setImageBitmap(bmp);
            } else {
                return;
            }
        } catch (Throwable t) {
            return;
        }
        iv.setOnClickListener(v -> d.dismiss());
        d.setContentView(iv);
        d.show();
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
