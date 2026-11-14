package com.university.schedule.util;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Edge-to-edge helper for translucent system bars and safe WindowInsets handling.
 * Supports minSdk 26 through targetSdk 36.
 */
public final class EdgeToEdgeCompat {

    private EdgeToEdgeCompat() {
    }

    /**
     * Enables true edge-to-edge rendering with transparent status and navigation bars
     * and configures system bar icon contrast to match the active DayNight theme.
     */
    public static void enableEdgeToEdge(@Nullable Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;

        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        int nightMask = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = nightMask == Configuration.UI_MODE_NIGHT_YES;
        setLightSystemBars(window, !isNight);
    }

    /**
     * Sets light (dark-colored) or dark (light-colored) status and navigation bar icons.
     *
     * @param lightIcons true for dark icons on a light background; false for light icons on a dark background.
     */
    public static void setLightSystemBars(@Nullable Activity activity, boolean lightIcons) {
        if (activity == null) return;
        setLightSystemBars(activity.getWindow(), lightIcons);
    }

    /**
     * Sets light or dark status and navigation bar icons on the given Window.
     */
    public static void setLightSystemBars(@Nullable Window window, boolean lightIcons) {
        if (window == null) return;
        View decor = window.getDecorView();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decor);
        controller.setAppearanceLightStatusBars(lightIcons);
        controller.setAppearanceLightNavigationBars(lightIcons);
    }

    /**
     * Applies system bar insets as padding on {@code target}, preserving its initial XML padding.
     */
    public static void applySystemBarInsets(@Nullable View target, boolean applyTop, boolean applyBottom) {
        if (target == null) return;
        final int initialLeft = target.getPaddingLeft();
        final int initialTop = target.getPaddingTop();
        final int initialRight = target.getPaddingRight();
        final int initialBottom = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(
                    initialLeft + bars.left,
                    initialTop + (applyTop ? bars.top : 0),
                    initialRight + bars.right,
                    initialBottom + (applyBottom ? bars.bottom : 0)
            );
            return insets;
        });
        requestApplyInsetsWhenAttached(target);
    }

    /**
     * Applies bottom system bar insets as bottom margin on {@code target}, preserving its initial margin.
     */
    public static void applyBottomSystemBarMargin(@Nullable View target) {
        if (target == null) return;
        ViewGroup.LayoutParams lp = target.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams mlp)) return;
        final int initialBottom = mlp.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.LayoutParams currentLp = v.getLayoutParams();
            if (currentLp instanceof ViewGroup.MarginLayoutParams marginLp) {
                marginLp.bottomMargin = initialBottom + bars.bottom;
                v.setLayoutParams(marginLp);
            }
            return insets;
        });
        requestApplyInsetsWhenAttached(target);
    }

    /**
     * Legacy helper: pads the root view with system bar insets.
     */
    public static void applyEdgeToEdgeInsets(@Nullable View root) {
        if (root == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        requestApplyInsetsWhenAttached(root);
    }

    /**
     * Legacy helper: pads the first child of android.R.id.content with system bar insets.
     */
    public static void applyEdgeToEdgeInsets(@Nullable Activity activity) {
        if (activity == null) return;
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup group && group.getChildCount() > 0) {
            applyEdgeToEdgeInsets(group.getChildAt(0));
        }
    }

    private static void requestApplyInsetsWhenAttached(@NonNull View view) {
        if (view.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(view);
        } else {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    v.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(v);
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                }
            });
        }
    }
}
