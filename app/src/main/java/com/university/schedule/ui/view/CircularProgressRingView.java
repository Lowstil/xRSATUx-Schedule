package com.university.schedule.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.university.schedule.R;

/**
 * Кастомный Liquid Glass индикатор прогресса в виде гладкого кольца с закруглёнными краями,
 * мягким свечением и текстом процента в центре.
 */
public class CircularProgressRingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float animatedProgress = 100f;
    private int targetProgress = 100;
    private float strokeWidthPx;
    private boolean showCenterText = true;
    private ValueAnimator animator;

    public CircularProgressRingView(Context context) {
        super(context);
        init(context);
    }

    public CircularProgressRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CircularProgressRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        strokeWidthPx = dpToPx(8f);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(strokeWidthPx);
        trackPaint.setColor(0x268899BB);

        int defaultRingColor = ContextCompat.getColor(context, R.color.glass_success);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeWidth(strokeWidthPx * 1.75f);
        glowPaint.setColor(withAlpha(defaultRingColor, 52));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setColor(defaultRingColor);

        textPaint.setColor(ContextCompat.getColor(context, R.color.glass_text_primary));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(spToPx(18f));
        textPaint.setFontFeatureSettings("tnum");
    }

    public void setProgress(int percent) {
        setProgress(percent, true);
    }

    public void setProgress(int percent, boolean animate) {
        int clamped = Math.max(0, Math.min(100, percent));
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        targetProgress = clamped;
        if (!animate || Math.abs(animatedProgress - clamped) < 0.5f) {
            animatedProgress = clamped;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(animatedProgress, clamped);
        animator.setDuration(420L);
        animator.setInterpolator(new DecelerateInterpolator(1.8f));
        animator.addUpdateListener(anim -> {
            animatedProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setRingColor(@ColorInt int color) {
        progressPaint.setColor(color);
        glowPaint.setColor(withAlpha(color, 54));
        invalidate();
    }

    public void setTrackColor(@ColorInt int color) {
        trackPaint.setColor(color);
        invalidate();
    }

    public void setTextColor(@ColorInt int color) {
        textPaint.setColor(color);
        invalidate();
    }

    public void setStrokeWidthDp(float dp) {
        strokeWidthPx = dpToPx(dp);
        trackPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setStrokeWidth(strokeWidthPx);
        glowPaint.setStrokeWidth(strokeWidthPx * 1.75f);
        invalidate();
    }

    public void setCenterTextSizeSp(float sp) {
        textPaint.setTextSize(spToPx(sp));
        invalidate();
    }

    public void setShowCenterText(boolean show) {
        this.showCenterText = show;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        int size = Math.min(w, h);
        if (size <= 0) return;

        float glowHalf = (strokeWidthPx * 1.75f) / 2f;
        float inset = Math.max(strokeWidthPx / 2f, glowHalf) + dpToPx(1f);
        float left = getPaddingLeft() + (w - size) / 2f + inset;
        float top = getPaddingTop() + (h - size) / 2f + inset;
        float right = left + size - inset * 2f;
        float bottom = top + size - inset * 2f;
        if (right <= left || bottom <= top) return;

        arcBounds.set(left, top, right, bottom);

        canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint);

        float sweep = (animatedProgress / 100f) * 360f;
        if (sweep > 0.5f) {
            canvas.drawArc(arcBounds, -90f, sweep, false, glowPaint);
            canvas.drawArc(arcBounds, -90f, sweep, false, progressPaint);
        }

        if (showCenterText) {
            String label = Math.round(animatedProgress) + "%";
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float centerX = arcBounds.centerX();
            float baselineY = arcBounds.centerY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, centerX, baselineY, textPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    private static int withAlpha(@ColorInt int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
