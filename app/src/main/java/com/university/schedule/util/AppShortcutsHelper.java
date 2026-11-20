package com.university.schedule.util;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.university.schedule.R;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.ui.activity.SplashActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Управляет ярлыками приложения (App Shortcuts) по долгому нажатию на иконку:
 * - 3 статических ярлыка объявлены в {@code res/xml/shortcuts.xml}
 *   («Свободные аудитории», «Где преподаватель?», «Хаб домашек»).
 * - 1 динамический ярлык синхронизируется с главным избранным из {@link FavoritesManager},
 *   чтобы уложиться в лимит лаунчера Android (4 ярлыка).
 */
public final class AppShortcutsHelper {

    private static final String TAG = "AppShortcutsHelper";

    public static final String ACTION_FREE_ROOMS = "com.university.schedule.action.SHORTCUT_FREE_ROOMS";
    public static final String ACTION_FIND_TEACHER = "com.university.schedule.action.SHORTCUT_FIND_TEACHER";
    public static final String ACTION_HOMEWORK = "com.university.schedule.action.SHORTCUT_HOMEWORK";
    public static final String ACTION_OPEN_FAVORITE = "com.university.schedule.action.SHORTCUT_OPEN_FAVORITE";

    public static final String EXTRA_SHORTCUT_ACTION = "extra_shortcut_action";
    public static final String EXTRA_FAVORITE_TYPE = "extra_favorite_type";
    public static final String EXTRA_FAVORITE_NAME = "extra_favorite_name";

    private static final String DYNAMIC_FAVORITE_ID = "shortcut_favorite_top";
    private static final int STATIC_SHORTCUT_COUNT = 3;
    private static final int LAUNCHER_SHORTCUT_CAP = 4;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private AppShortcutsHelper() {
    }

    public static boolean isShortcutAction(@Nullable String action) {
        return ACTION_FREE_ROOMS.equals(action)
                || ACTION_FIND_TEACHER.equals(action)
                || ACTION_HOMEWORK.equals(action)
                || ACTION_OPEN_FAVORITE.equals(action);
    }

    @Nullable
    public static String extractShortcutAction(@Nullable Intent intent) {
        if (intent == null) return null;
        String extraAction = intent.getStringExtra(EXTRA_SHORTCUT_ACTION);
        if (isShortcutAction(extraAction)) {
            return extraAction;
        }
        String intentAction = intent.getAction();
        if (isShortcutAction(intentAction)) {
            return intentAction;
        }
        return null;
    }

    public static boolean hasShortcutRequest(@Nullable Intent intent) {
        return extractShortcutAction(intent) != null;
    }

    public static void copyShortcutExtras(@Nullable Intent source, @NonNull Intent target) {
        if (source == null) return;
        String action = extractShortcutAction(source);
        if (action == null) return;
        target.putExtra(EXTRA_SHORTCUT_ACTION, action);
        if (ACTION_OPEN_FAVORITE.equals(action)) {
            String favType = source.getStringExtra(EXTRA_FAVORITE_TYPE);
            String favName = source.getStringExtra(EXTRA_FAVORITE_NAME);
            if (favType != null) {
                target.putExtra(EXTRA_FAVORITE_TYPE, favType);
            }
            if (favName != null) {
                target.putExtra(EXTRA_FAVORITE_NAME, favName);
            }
        }
    }

    public static void syncDynamicShortcutsAsync(@Nullable Context context) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        EXECUTOR.execute(() -> syncDynamicShortcuts(appCtx));
    }

    public static void syncDynamicShortcutsAsync(@Nullable Context context,
                                                 @Nullable List<GroupOrTeacher> favorites) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        final List<GroupOrTeacher> snapshot = favorites != null
                ? new ArrayList<>(favorites)
                : Collections.emptyList();
        EXECUTOR.execute(() -> syncDynamicShortcuts(appCtx, snapshot));
    }

    public static void syncDynamicShortcuts(@Nullable Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        List<GroupOrTeacher> favorites = FavoritesManager.getFavorites(appCtx);
        syncDynamicShortcuts(appCtx, favorites);
    }

    public static void syncDynamicShortcuts(@Nullable Context context,
                                            @Nullable List<GroupOrTeacher> favorites) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        try {
            int maxAllowed = ShortcutManagerCompat.getMaxShortcutCountPerActivity(appCtx);
            int effectiveLimit = Math.min(LAUNCHER_SHORTCUT_CAP, maxAllowed);
            int dynamicBudget = Math.max(0, effectiveLimit - STATIC_SHORTCUT_COUNT);

            if (dynamicBudget == 0 || favorites == null || favorites.isEmpty()) {
                ShortcutManagerCompat.setDynamicShortcuts(appCtx, Collections.emptyList());
                return;
            }

            List<ShortcutInfoCompat> dynamicShortcuts = new ArrayList<>();
            int count = Math.min(dynamicBudget, favorites.size());
            for (int i = 0; i < count; i++) {
                GroupOrTeacher fav = favorites.get(i);
                if (fav == null || fav.getName() == null || fav.getName().trim().isEmpty()) {
                    continue;
                }
                String name = fav.getName().trim();
                String type = fav.getType() != null ? fav.getType() : GroupOrTeacher.TYPE_GROUP;

                Intent intent = new Intent(appCtx, SplashActivity.class);
                intent.setAction(ACTION_OPEN_FAVORITE);
                intent.putExtra(EXTRA_SHORTCUT_ACTION, ACTION_OPEN_FAVORITE);
                intent.putExtra(EXTRA_FAVORITE_TYPE, type);
                intent.putExtra(EXTRA_FAVORITE_NAME, name);

                String id = (i == 0) ? DYNAMIC_FAVORITE_ID : (DYNAMIC_FAVORITE_ID + "_" + i);
                ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(appCtx, id)
                        .setShortLabel(name)
                        .setLongLabel("★ " + name)
                        .setIcon(IconCompat.createWithResource(appCtx, R.drawable.ic_shortcut_favorite))
                        .setIntent(intent)
                        .setRank(i)
                        .build();
                dynamicShortcuts.add(shortcut);
            }

            ShortcutManagerCompat.setDynamicShortcuts(appCtx, dynamicShortcuts);
        } catch (Exception e) {
            Log.e(TAG, "Не удалось синхронизировать динамические ярлыки", e);
        }
    }
}
