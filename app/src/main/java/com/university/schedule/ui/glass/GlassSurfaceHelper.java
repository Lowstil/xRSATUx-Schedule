package com.university.schedule.ui.glass;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.card.MaterialCardView;
import com.university.schedule.R;

/**
 * Utility for applying Liquid Glass surfaces, 135° specular borders, soft glows,
 * and cross-window blur dialogs across API 26–36.
 */
public final class GlassSurfaceHelper {

    private static final String PREFS_NAME = "schedule_prefs";
    private static final String KEY_REDUCED_TRANSPARENCY = "reduced_transparency";

    private GlassSurfaceHelper() {
    }

    /**
     * Applies Liquid Glass styling (translucent fill, 1dp 135° specular border,
     * rounded outline clipping, and API 28+ tinted spot shadow) to {@code view}.
     */
    public static void applyGlassCard(@Nullable View view, float cornerRadiusDp, boolean highlight) {
        if (view == null) return;
        Context ctx = view.getContext();
        GradientDrawable glass = createGlassDrawable(ctx, cornerRadiusDp, true, highlight);
        int accent = ContextCompat.getColor(ctx, R.color.glass_accent);
        int spot = highlight
                ? ColorUtils.setAlphaComponent(accent, 115)
                : ColorUtils.setAlphaComponent(accent, 42);
        int ambient = ColorUtils.setAlphaComponent(Color.BLACK, 28);
        applyCardBackground(view, cornerRadiusDp, glass, highlight ? 8f : 4f, spot, ambient);
    }

    /**
     * Applies state-aware Liquid Glass highlighting for lesson cards:
     * <ul>
     *     <li>{@code 0} = normal card</li>
     *     <li>{@code 1} = current (LIVE) lesson with emerald/indigo-tinted frosted fill,
     *     1.5dp specular gradient border, and soft emerald spot glow</li>
     *     <li>{@code 2} = next (Следующая) lesson with sky-blue/indigo-tinted frosted fill,
     *     1.5dp specular gradient border, and subtle blue spot glow</li>
     * </ul>
     */
    public static void applyLessonStateGlass(@Nullable View view, float cornerDp, int state) {
        if (view == null) return;
        if (state != 1 && state != 2) {
            applyGlassCard(view, cornerDp, false);
            return;
        }

        Context ctx = view.getContext();
        boolean reduced = isReducedTransparency(ctx);
        boolean night = isNightMode(ctx);
        int baseFill = ContextCompat.getColor(ctx,
                reduced ? R.color.glass_surface_fallback : R.color.glass_surface_strong);

        int fillColor;
        int strokeStart;
        int strokeMid;
        int strokeEnd;
        int spotShadow;
        int ambientShadow;
        float elevationDp;

        if (state == 1) {
            // Current (LIVE): emerald + subtle indigo frosted tint
            int emerald = 0xFF34D399;
            int indigo = 0xFF6C8CFF;
            int emeraldAlpha = night ? (reduced ? 34 : 46) : (reduced ? 24 : 34);
            int indigoAlpha = night ? 18 : 12;
            fillColor = ColorUtils.compositeColors(
                    ColorUtils.setAlphaComponent(emerald, emeraldAlpha), baseFill);
            fillColor = ColorUtils.compositeColors(
                    ColorUtils.setAlphaComponent(indigo, indigoAlpha), fillColor);

            strokeStart = 0xAA34D399;
            strokeMid = 0x406C8CFF;
            strokeEnd = 0xAA34D399;
            spotShadow = ColorUtils.setAlphaComponent(0xFF10B981, night ? 145 : 120);
            ambientShadow = ColorUtils.setAlphaComponent(0xFF10B981, 46);
            elevationDp = 8f;
        } else {
            // Next (Следующая): sky-blue + subtle indigo frosted tint
            int sky = 0xFF60A5FA;
            int indigo = 0xFF818CF8;
            int skyAlpha = night ? (reduced ? 30 : 40) : (reduced ? 22 : 30);
            int indigoAlpha = night ? 14 : 10;
            fillColor = ColorUtils.compositeColors(
                    ColorUtils.setAlphaComponent(sky, skyAlpha), baseFill);
            fillColor = ColorUtils.compositeColors(
                    ColorUtils.setAlphaComponent(indigo, indigoAlpha), fillColor);

            strokeStart = 0x9960A5FA;
            strokeMid = 0x33818CF8;
            strokeEnd = 0x8060A5FA;
            spotShadow = ColorUtils.setAlphaComponent(0xFF3B82F6, night ? 120 : 96);
            ambientShadow = ColorUtils.setAlphaComponent(0xFF3B82F6, 36);
            elevationDp = 6f;
        }

        GradientDrawable glass = new SpecularGlassDrawable(
                fillColor,
                dpToPx(ctx, cornerDp),
                dpToPx(ctx, 1.5f),
                strokeStart,
                strokeMid,
                strokeEnd
        );
        applyCardBackground(view, cornerDp, glass, elevationDp, spotShadow, ambientShadow);
    }

    private static void applyCardBackground(
            @NonNull View view,
            float cornerRadiusDp,
            @NonNull GradientDrawable background,
            float elevationDp,
            int spotShadowColor,
            int ambientShadowColor) {
        Context ctx = view.getContext();
        float radiusPx = dpToPx(ctx, cornerRadiusDp);
        float elevationPx = dpToPx(ctx, elevationDp);

        if (view instanceof MaterialCardView card) {
            card.setCardBackgroundColor(Color.TRANSPARENT);
            card.setStrokeWidth(0);
            card.setRadius(radiusPx);
            card.setCardElevation(elevationPx);
            card.setBackground(background);
        } else {
            view.setBackground(background);
            view.setElevation(elevationPx);
            view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            view.setClipToOutline(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setOutlineSpotShadowColor(spotShadowColor);
            view.setOutlineAmbientShadowColor(ambientShadowColor);
        }
    }

    /**
     * Configures a {@link Dialog} with a transparent window, 0.45f dim,
     * API 31+ cross-window background/behind blur (when enabled by system),
     * and 28dp Liquid Glass styling on {@code rootCard}.
     */
    public static void applyGlassDialog(@Nullable Dialog dialog, @Nullable View rootCard) {
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                WindowManager.LayoutParams lp = window.getAttributes();
                lp.dimAmount = 0.45f;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    WindowManager wm = (WindowManager) dialog.getContext()
                            .getSystemService(Context.WINDOW_SERVICE);
                    boolean blurEnabled = wm != null && wm.isCrossWindowBlurEnabled()
                            && !isReducedTransparency(dialog.getContext());
                    if (blurEnabled) {
                        window.setBackgroundBlurRadius(60);
                        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                        lp.setBlurBehindRadius(40);
                    }
                }
                window.setAttributes(lp);
            }
        }

        if (rootCard != null) {
            applyGlassCard(rootCard, 28f, false);
        }
    }

    /**
     * Creates a {@link GradientDrawable} with a translucent Liquid Glass fill and
     * a 1dp 135° specular border gradient ({@code glass_stroke_start} →
     * {@code glass_stroke_mid} → {@code glass_stroke_end}).
     */
    @NonNull
    public static GradientDrawable createGlassDrawable(
            @NonNull Context ctx,
            float cornerRadiusDp,
            boolean elevated,
            boolean highlight) {

        boolean reduced = isReducedTransparency(ctx);
        int fillColor;
        if (reduced) {
            fillColor = ContextCompat.getColor(ctx, R.color.glass_surface_fallback);
        } else if (elevated) {
            fillColor = ContextCompat.getColor(ctx, R.color.glass_surface_strong);
        } else {
            fillColor = ContextCompat.getColor(ctx, R.color.glass_surface);
        }

        if (highlight) {
            int accent = ContextCompat.getColor(ctx, R.color.glass_accent);
            fillColor = ColorUtils.compositeColors(
                    ColorUtils.setAlphaComponent(accent, reduced ? 28 : 38),
                    fillColor
            );
        }

        int strokeStart = ContextCompat.getColor(ctx, R.color.glass_stroke_start);
        int strokeMid = ContextCompat.getColor(ctx, R.color.glass_stroke_mid);
        int strokeEnd = ContextCompat.getColor(ctx, R.color.glass_stroke_end);

        if (!isNightMode(ctx) && !highlight) {
            // Crisp top-left white specular glint fading into cool-slate rim in Light mode
            strokeStart = 0xCCFFFFFF;
            strokeEnd = 0x5994A3B8;
        }

        if (highlight) {
            int accent = ContextCompat.getColor(ctx, R.color.glass_accent);
            strokeStart = ColorUtils.compositeColors(
                    ColorUtils.setAlphaComponent(accent, 130),
                    strokeStart
            );
        }

        float cornerPx = dpToPx(ctx, cornerRadiusDp);
        float strokePx = dpToPx(ctx, 1f);

        return new SpecularGlassDrawable(
                fillColor,
                cornerPx,
                strokePx,
                strokeStart,
                strokeMid,
                strokeEnd
        );
    }

    private static boolean isReducedTransparency(@NonNull Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_REDUCED_TRANSPARENCY, false);
    }

    private static boolean isNightMode(@NonNull Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static float dpToPx(@NonNull Context ctx, float dp) {
        return dp * ctx.getResources().getDisplayMetrics().density;
    }

    /**
     * {@link GradientDrawable} subclass that paints a 135° linear-gradient specular stroke
     * around the rounded glass surface.
     */
    private static final class SpecularGlassDrawable extends GradientDrawable {

        private final float cornerPx;
        private final float strokePx;
        private final int strokeStart;
        private final int strokeMid;
        private final int strokeEnd;
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF strokeRect = new RectF();
        private int lastW = -1;
        private int lastH = -1;

        SpecularGlassDrawable(
                int fillColor,
                float cornerPx,
                float strokePx,
                int strokeStart,
                int strokeMid,
                int strokeEnd) {
            setShape(RECTANGLE);
            setColor(fillColor);
            setCornerRadius(cornerPx);
            this.cornerPx = cornerPx;
            this.strokePx = strokePx;
            this.strokeStart = strokeStart;
            this.strokeMid = strokeMid;
            this.strokeEnd = strokeEnd;
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokePx);
        }

        @Override
        protected void onBoundsChange(@NonNull Rect r) {
            super.onBoundsChange(r);
            int w = r.width();
            int h = r.height();
            if (w > 0 && h > 0 && (w != lastW || h != lastH)) {
                lastW = w;
                lastH = h;
                strokePaint.setShader(new LinearGradient(
                        r.left, r.top, r.right, r.bottom,
                        new int[]{strokeStart, strokeMid, strokeEnd},
                        new float[]{0f, 0.45f, 1f},
                        Shader.TileMode.CLAMP
                ));
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            super.draw(canvas);
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float half = strokePx * 0.5f;
            strokeRect.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
            float r = Math.max(0f, cornerPx - half);
            canvas.drawRoundRect(strokeRect, r, r, strokePaint);
        }
    }
}
