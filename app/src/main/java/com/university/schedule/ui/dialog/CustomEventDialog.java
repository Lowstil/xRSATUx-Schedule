package com.university.schedule.ui.dialog;

import android.app.DatePickerDialog;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.CustomEvent;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.Constants;
import com.university.schedule.util.DateUtils;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Liquid Glass диалог создания личного события / пары (N4).
 */
public final class CustomEventDialog {

    private CustomEventDialog() {
    }

    @Nullable
    public static AlertDialog show(@NonNull AppCompatActivity activity,
                                   @Nullable LocalDate defaultDate,
                                   @Nullable String groupName,
                                   boolean isGroup,
                                   @Nullable Runnable onSaved) {
        if (activity.isFinishing() || activity.isDestroyed()) return null;

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_custom_event, null);
        View rootCard = view.findViewById(R.id.rootCustomEventCard);

        TextView tvTarget = view.findViewById(R.id.tvCustomEventTarget);
        ImageButton btnClose = view.findViewById(R.id.btnCloseCustomEvent);
        MaterialButtonToggleGroup toggleMode = view.findViewById(R.id.toggleCustomMode);
        LinearLayout panelDate = view.findViewById(R.id.panelCustomDate);
        LinearLayout panelRepeat = view.findViewById(R.id.panelCustomRepeat);
        TextView tvSelectedDate = view.findViewById(R.id.tvCustomSelectedDate);
        MaterialButton btnPickDate = view.findViewById(R.id.btnPickCustomDate);
        MaterialButtonToggleGroup toggleDow = view.findViewById(R.id.toggleCustomDow);
        MaterialButtonToggleGroup toggleWeekType = view.findViewById(R.id.toggleCustomWeekType);
        MaterialButtonToggleGroup toggleSlot = view.findViewById(R.id.toggleCustomLessonSlot);

        TextInputEditText etSubject = view.findViewById(R.id.etCustomSubject);
        TextInputEditText etRoom = view.findViewById(R.id.etCustomRoom);
        TextInputEditText etTeacher = view.findViewById(R.id.etCustomTeacher);
        TextInputEditText etNote = view.findViewById(R.id.etCustomNote);

        MaterialButton btnCancel = view.findViewById(R.id.btnCancelCustomEvent);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveCustomEvent);

        String targetLabel = (groupName != null && !groupName.trim().isEmpty())
                ? ((isGroup ? "Группа: " : "Преподаватель: ") + groupName.trim())
                : "Личное расписание";
        tvTarget.setText(targetLabel);

        GlassSurfaceHelper.applyGlassCard(panelDate, 14f, false);

        final LocalDate[] selectedDate = new LocalDate[]{
                defaultDate != null ? defaultDate : DateUtils.todayMoscow()
        };
        tvSelectedDate.setText(DateUtils.formatDisplayDate(selectedDate[0]));

        // Defaults
        toggleMode.check(R.id.btnCustomModeDate);
        int initDow = DateUtils.toScheduleDayOfWeek(selectedDate[0]);
        if (initDow < 1 || initDow > 6) initDow = 1;
        toggleDow.check(dowButtonId(initDow));
        toggleWeekType.check(R.id.btnWeekBoth);
        toggleSlot.check(R.id.btnCustomSlot1);

        toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean isDateMode = checkedId == R.id.btnCustomModeDate;
            panelDate.setVisibility(isDateMode ? View.VISIBLE : View.GONE);
            panelRepeat.setVisibility(isDateMode ? View.GONE : View.VISIBLE);
        });

        btnPickDate.setOnClickListener(v -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            LocalDate cur = selectedDate[0];
            DatePickerDialog dpd = new DatePickerDialog(
                    activity,
                    R.style.ThemeOverlay_UniSchedule_DatePicker,
                    (dp, y, m, d) -> {
                        selectedDate[0] = LocalDate.of(y, m + 1, d);
                        tvSelectedDate.setText(DateUtils.formatDisplayDate(selectedDate[0]));
                    },
                    cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth()
            );
            dpd.show();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .create();

        ExecutorService ex = Executors.newSingleThreadExecutor();
        dialog.setOnDismissListener(d -> ex.shutdown());

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String subject = etSubject.getText() != null ? etSubject.getText().toString().trim() : "";
            if (subject.isEmpty()) {
                etSubject.setError("Введите название");
                return;
            }
            String room = etRoom.getText() != null ? etRoom.getText().toString().trim() : "";
            String teacher = etTeacher.getText() != null ? etTeacher.getText().toString().trim() : "";
            String note = etNote.getText() != null ? etNote.getText().toString().trim() : "";

            CustomEvent event = new CustomEvent();
            event.setGroupName(groupName != null ? groupName.trim() : "");
            event.setGroup(isGroup);
            event.setLessonNumber(slotFromButtonId(toggleSlot.getCheckedButtonId()));
            event.setSubjectName(subject);
            event.setRoom(room);
            event.setTeacherName(teacher);
            event.setNote(note);

            boolean isDateMode = toggleMode.getCheckedButtonId() == R.id.btnCustomModeDate;
            if (isDateMode) {
                event.setDateIso(selectedDate[0].toString());
                int dow = DateUtils.toScheduleDayOfWeek(selectedDate[0]);
                event.setDayOfWeek(dow >= 1 && dow <= 6 ? dow : 1);
                event.setWeekType("");
            } else {
                event.setDateIso(null);
                event.setDayOfWeek(dowFromButtonId(toggleDow.getCheckedButtonId()));
                event.setWeekType(weekTypeFromButtonId(toggleWeekType.getCheckedButtonId()));
            }

            btnSave.setEnabled(false);
            ex.execute(() -> {
                ScheduleRepository.getInstance(activity).addCustomEvent(event);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    Toast.makeText(activity, "Событие добавлено", Toast.LENGTH_SHORT).show();
                    if (onSaved != null) {
                        onSaved.run();
                    }
                });
            });
        });

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, rootCard);

        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            window.setLayout((int) (dm.widthPixels * 0.92f), (int) (dm.heightPixels * 0.82f));
        }

        return dialog;
    }

    private static int dowButtonId(int dow) {
        switch (dow) {
            case 2: return R.id.btnDow2;
            case 3: return R.id.btnDow3;
            case 4: return R.id.btnDow4;
            case 5: return R.id.btnDow5;
            case 6: return R.id.btnDow6;
            default: return R.id.btnDow1;
        }
    }

    private static int dowFromButtonId(int id) {
        if (id == R.id.btnDow2) return 2;
        if (id == R.id.btnDow3) return 3;
        if (id == R.id.btnDow4) return 4;
        if (id == R.id.btnDow5) return 5;
        if (id == R.id.btnDow6) return 6;
        return 1;
    }

    private static String weekTypeFromButtonId(int id) {
        if (id == R.id.btnWeekOdd) return Constants.WEEK_TYPE_ODD;
        if (id == R.id.btnWeekEven) return Constants.WEEK_TYPE_EVEN;
        return "both";
    }

    private static int slotFromButtonId(int id) {
        if (id == R.id.btnCustomSlot2) return 2;
        if (id == R.id.btnCustomSlot3) return 3;
        if (id == R.id.btnCustomSlot4) return 4;
        if (id == R.id.btnCustomSlot5) return 5;
        if (id == R.id.btnCustomSlot6) return 6;
        if (id == R.id.btnCustomSlot7) return 7;
        return 1;
    }
}
