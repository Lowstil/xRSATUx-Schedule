package com.university.schedule.ui.dialog;

import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.university.schedule.R;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.RoomFormatter;

/**
 * Liquid Glass карточка корпуса РГАТУ имени П.А. Соловьёва (Рыбинск) с адресом,
 * временем перехода и быстрым переходом к свободным аудиториям.
 */
public final class BuildingInfoDialog {

    private static final class BuildingMeta {
        final String address;
        final String walkTime;
        final String description;
        final boolean hasPhysicalRooms;

        BuildingMeta(String address, String walkTime, String description, boolean hasPhysicalRooms) {
            this.address = address;
            this.walkTime = walkTime;
            this.description = description;
            this.hasPhysicalRooms = hasPhysicalRooms;
        }
    }

    private BuildingInfoDialog() {
    }

    public static void show(@NonNull AppCompatActivity activity, @Nullable String rawRoom) {
        if (activity.isFinishing() || activity.isDestroyed() || rawRoom == null || rawRoom.trim().isEmpty()) return;

        boolean isNight = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        RoomFormatter.RoomInfo info = RoomFormatter.parse(rawRoom, isNight);
        String buildingName = info != null && info.building != null ? info.building : rawRoom.trim();
        int accentColor = info != null
                ? info.color
                : ContextCompat.getColor(activity, R.color.glass_accent);

        BuildingMeta meta = resolveBuildingMeta(buildingName);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_building_info, null);
        View rootCard = view.findViewById(R.id.cardBuildingInfoRoot);
        View detailsCard = view.findViewById(R.id.cardBuildingDetails);
        View dotView = view.findViewById(R.id.viewBuildingColorDot);
        TextView tvTitle = view.findViewById(R.id.tvBuildingTitle);
        TextView tvRoomSubtitle = view.findViewById(R.id.tvBuildingRoomSubtitle);
        TextView tvAddress = view.findViewById(R.id.tvBuildingAddress);
        TextView tvWalkTime = view.findViewById(R.id.tvBuildingWalkTime);
        TextView tvDescription = view.findViewById(R.id.tvBuildingDescription);
        ImageButton btnCloseTop = view.findViewById(R.id.btnCloseBuildingInfo);
        MaterialButton btnFreeRooms = view.findViewById(R.id.btnBuildingFreeRooms);
        MaterialButton btnCloseBottom = view.findViewById(R.id.btnCloseBuildingBottom);

        GlassSurfaceHelper.applyGlassCard(detailsCard, 18f, false);

        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(accentColor);
        dotView.setBackground(dotBg);

        tvTitle.setText(buildingName);
        if (info != null && info.room != null && !info.room.isEmpty()) {
            tvRoomSubtitle.setText("Аудитория: " + rawRoom.trim());
        } else {
            tvRoomSubtitle.setText(rawRoom.trim());
        }

        tvAddress.setText(meta.address);
        tvWalkTime.setText(meta.walkTime);
        tvDescription.setText(meta.description);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .create();

        btnCloseTop.setOnClickListener(v -> dialog.dismiss());
        btnCloseBottom.setOnClickListener(v -> dialog.dismiss());

        if (meta.hasPhysicalRooms) {
            btnFreeRooms.setVisibility(View.VISIBLE);
            btnFreeRooms.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                dialog.dismiss();
                RoomTeacherFinderDialog.showForBuilding(activity, buildingName);
            });
        } else {
            btnFreeRooms.setVisibility(View.GONE);
        }

        dialog.show();
        GlassSurfaceHelper.applyGlassDialog(dialog, rootCard);

        Window window = dialog.getWindow();
        if (window != null) {
            int maxWidth = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 440f, activity.getResources().getDisplayMetrics());
            int width = Math.min((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f), maxWidth);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @NonNull
    private static BuildingMeta resolveBuildingMeta(@NonNull String buildingName) {
        switch (buildingName) {
            case "Главный корпус":
                return new BuildingMeta(
                        "г. Рыбинск, ул. Пушкина, д. 53",
                        "До 1-го корпуса ~2 мин · до 3-го ~3 мин · до 2-го ~7 мин пешком",
                        "Главный учебно-административный корпус РГАТУ имени П.А. Соловьёва, ректорат, библиотека и актовый зал.",
                        true
                );
            case "Первый корпус":
                return new BuildingMeta(
                        "г. Рыбинск, ул. Плеханова, д. 2",
                        "До Главного корпуса ~2 мин · до 3-го ~2 мин · до 2-го ~8 мин пешком",
                        "Учебно-лабораторный корпус №1, инженерные и авиадвигателестроительные кафедры.",
                        true
                );
            case "Второй корпус":
                return new BuildingMeta(
                        "г. Рыбинск, ул. Луначарского, д. 12",
                        "До Главного корпуса ~7 мин · до 1-го и 3-го ~8 мин пешком",
                        "Учебный корпус №2, аудитории факультетов и компьютерные классы.",
                        true
                );
            case "Третий корпус":
                return new BuildingMeta(
                        "г. Рыбинск, ул. Плеханова, д. 4",
                        "До Главного корпуса ~3 мин · до 1-го ~2 мин · до 2-го ~8 мин пешком",
                        "Учебно-лабораторный корпус №3, специализированные лаборатории и аудитории.",
                        true
                );
            case "Точка кипения":
                return new BuildingMeta(
                        "г. Рыбинск, ул. Пушкина, д. 53 (Главный корпус)",
                        "Внутри Главного корпуса · переход от 1-го корпуса ~2 мин",
                        "Пространство коллективной работы «Точка кипения — Рыбинск» для проектных сессий, лекций и мероприятий.",
                        false
                );
            case "Большой спортзал":
                return new BuildingMeta(
                        "г. Рыбинск, ул. Пушкина, д. 53 (спортивный корпус)",
                        "Рядом с Главным корпусом ~2 мин пешком",
                        "Спортивный комплекс РГАТУ: занятия по физической культуре и спортивные секции.",
                        false
                );
            case "Онлайн":
                return new BuildingMeta(
                        "Дистанционный формат",
                        "Переход между корпусами не требуется",
                        "Занятие проводится онлайн. Ссылку на подключение уточняйте у преподавателя или в ЭИОС РГАТУ.",
                        false
                );
            case "Место практики":
                return new BuildingMeta(
                        "База производственной / учебной практики",
                        "Время в пути зависит от предприятия",
                        "Занятие проходит на профильном предприятии или в организации. Адрес уточняется у руководителя практики.",
                        false
                );
            default:
                return new BuildingMeta(
                        "РГАТУ имени П.А. Соловьёва, г. Рыбинск",
                        "Уточните расположение аудитории в расписании",
                        "Учебная аудитория или специальное помещение.",
                        true
                );
        }
    }
}
