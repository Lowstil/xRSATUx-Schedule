package com.university.schedule.ui.glass;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.university.schedule.R;

import java.util.Random;

/**
 * Full-bleed animated mesh gradient backdrop for the Liquid Glass UI.
 * <ul>
 *     <li>API 33+: AGSL {@link RuntimeShader} with slow organic blobs and 2% film grain.</li>
 *     <li>API 26-32: Smooth multi-stop {@link RadialGradient} layers animated via {@link ValueAnimator}
 *     with zero per-frame allocations and a tiled 2% grain overlay.</li>
 * </ul>
 * Automatically respects DayNight colors, power-save mode, and the user's
 * {@code reduced_transparency} preference in {@code schedule_prefs}.
 */
public class AmbientBackdropView extends View {

    private static final String PREFS_NAME = "schedule_prefs";
    private static final String KEY_REDUCED_TRANSPARENCY = "reduced_transparency";
    private static final long ANIMATION_PERIOD_MS = 22_000L;

    private static final String AGSL_MESH_SHADER =
            "uniform float2 uResolution;\n" +
            "uniform float uTime;\n" +
            "uniform float uWarmth;\n" +
            "uniform float4 uBaseColor;\n" +
            "uniform float4 uBlob1Color;\n" +
            "uniform float4 uBlob2Color;\n" +
            "uniform float4 uBlob3Color;\n" +
            "uniform float4 uWarmColor;\n" +
            "float hash(float2 p) {\n" +
            "    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);\n" +
            "}\n" +
            "half4 main(float2 fragCoord) {\n" +
            "    float2 uv = fragCoord / max(uResolution, float2(1.0));\n" +
            "    float aspect = uResolution.x / max(uResolution.y, 1.0);\n" +
            "    float2 p = float2(uv.x * aspect, uv.y);\n" +
            "    float t = uTime;\n" +
            "    float2 c1 = float2(0.25 * aspect + 0.18 * sin(t * 0.9), 0.22 + 0.14 * cos(t * 0.7));\n" +
            "    float2 c2 = float2(0.78 * aspect + 0.16 * cos(t * 0.8), 0.38 + 0.18 * sin(t * 0.6));\n" +
            "    float2 c3 = float2(0.45 * aspect + 0.20 * sin(t * 0.5 + 1.4), 0.80 + 0.12 * cos(t * 0.85));\n" +
            "    float w1 = exp(-3.2 * dot(p - c1, p - c1));\n" +
            "    float w2 = exp(-2.8 * dot(p - c2, p - c2));\n" +
            "    float w3 = exp(-3.0 * dot(p - c3, p - c3));\n" +
            "    float4 col = uBaseColor;\n" +
            "    col = mix(col, uBlob1Color, clamp(w1 * 0.85, 0.0, 1.0));\n" +
            "    col = mix(col, uBlob2Color, clamp(w2 * 0.75, 0.0, 1.0));\n" +
            "    col = mix(col, uBlob3Color, clamp(w3 * 0.75, 0.0, 1.0));\n" +
            "    col = mix(col, uWarmColor, clamp(uWarmth * (0.25 + 0.35 * w2), 0.0, 0.60));\n" +
            "    float grain = (hash(fragCoord + fract(uTime) * 17.0) - 0.5) * 0.02;\n" +
            "    col.rgb = clamp(col.rgb + grain, 0.0, 1.0);\n" +
            "    return half4(col);\n" +
            "}\n";

    private int baseColor;
    private int blob1Color;
    private int blob2Color;
    private int blob3Color;
    private int warmColor;

    private final Paint agslPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blob1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blob2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blob3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint warmPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grainPaint = new Paint();

    private final Matrix blob1Matrix = new Matrix();
    private final Matrix blob2Matrix = new Matrix();
    private final Matrix blob3Matrix = new Matrix();
    private final Matrix warmMatrix = new Matrix();

    @Nullable
    private RadialGradient blob1Shader;
    @Nullable
    private RadialGradient blob2Shader;
    @Nullable
    private RadialGradient blob3Shader;
    @Nullable
    private RadialGradient warmShader;

    @Nullable
    private RuntimeShader runtimeShader;
    private boolean useAgsl;

    @Nullable
    private ValueAnimator phaseAnimator;
    @Nullable
    private ValueAnimator warmthAnimator;

    private float phaseRad = 0f;
    private float warmth = 0f;
    private boolean lessonActive = false;

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    public AmbientBackdropView(@NonNull Context context) {
        this(context, null);
    }

    public AmbientBackdropView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AmbientBackdropView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        loadThemeColors(context);
        initGrainPaint();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                runtimeShader = new RuntimeShader(AGSL_MESH_SHADER);
                agslPaint.setShader(runtimeShader);
                useAgsl = true;
            } catch (RuntimeException ignored) {
                useAgsl = false;
                runtimeShader = null;
            }
        }

        prefListener = (prefs, key) -> {
            if (KEY_REDUCED_TRANSPARENCY.equals(key)) {
                reloadPreferences();
            }
        };
    }

    private void loadThemeColors(@NonNull Context context) {
        baseColor = ContextCompat.getColor(context, R.color.glass_backdrop_base);
        blob1Color = ContextCompat.getColor(context, R.color.glass_backdrop_blob1);
        blob2Color = ContextCompat.getColor(context, R.color.glass_backdrop_blob2);
        blob3Color = ContextCompat.getColor(context, R.color.glass_backdrop_blob3);
        warmColor = ContextCompat.getColor(context, R.color.glass_backdrop_blob_warm);
    }

    private void initGrainPaint() {
        final int size = 64;
        Bitmap noise = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Random random = new Random(42L);
        int[] pixels = new int[size * size];
        for (int i = 0; i < pixels.length; i++) {
            int luma = random.nextBoolean() ? 255 : 0;
            // ~2% alpha (5 / 255) to dither 8-bit gradient banding
            pixels[i] = Color.argb(5, luma, luma, luma);
        }
        noise.setPixels(pixels, 0, size, 0, 0, size, size);
        BitmapShader shader = new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        grainPaint.setShader(shader);
    }

    /**
     * Re-reads {@code reduced_transparency} from {@code schedule_prefs} and theme colors,
     * stops or starts the ambient animation cleanly, and triggers a redraw.
     */
    public void reloadPreferences() {
        loadThemeColors(getContext());
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            float radius = Math.max(w, h) * 0.65f;
            blob1Shader = createBlobShader(radius, blob1Color);
            blob2Shader = createBlobShader(radius * 0.95f, blob2Color);
            blob3Shader = createBlobShader(radius * 0.90f, blob3Color);
            warmShader = createBlobShader(radius * 0.85f, warmColor);

            blob1Paint.setShader(blob1Shader);
            blob2Paint.setShader(blob2Shader);
            blob3Paint.setShader(blob3Shader);
            warmPaint.setShader(warmShader);
        }

        if (warmthAnimator == null || !warmthAnimator.isRunning()) {
            warmth = lessonActive ? 1f : 0f;
        }
        updateAnimationState();
        invalidate();
    }

    /**
     * Subtly shifts the backdrop warmth when a lesson is currently active.
     */
    public void setLessonActive(boolean active) {
        if (this.lessonActive == active) return;
        this.lessonActive = active;
        float target = active ? 1f : 0f;

        if (warmthAnimator != null) {
            warmthAnimator.cancel();
        }
        if (shouldReduceMotionOrTransparency()) {
            warmth = target;
            invalidate();
            return;
        }
        warmthAnimator = ValueAnimator.ofFloat(warmth, target);
        warmthAnimator.setDuration(750L);
        warmthAnimator.addUpdateListener(anim -> {
            warmth = (float) anim.getAnimatedValue();
            invalidate();
        });
        warmthAnimator.start();
    }

    private boolean shouldReduceMotionOrTransparency() {
        Context ctx = getContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_REDUCED_TRANSPARENCY, false)) {
            return true;
        }
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        float radius = Math.max(w, h) * 0.65f;
        blob1Shader = createBlobShader(radius, blob1Color);
        blob2Shader = createBlobShader(radius * 0.95f, blob2Color);
        blob3Shader = createBlobShader(radius * 0.90f, blob3Color);
        warmShader = createBlobShader(radius * 0.85f, warmColor);

        blob1Paint.setShader(blob1Shader);
        blob2Paint.setShader(blob2Shader);
        blob3Paint.setShader(blob3Shader);
        warmPaint.setShader(warmShader);
    }

    private static RadialGradient createBlobShader(float radius, int centerColor) {
        int mid = ColorUtils.setAlphaComponent(centerColor, 175);
        int edge = ColorUtils.setAlphaComponent(centerColor, 0);
        return new RadialGradient(
                0f, 0f, radius,
                new int[]{centerColor, mid, edge},
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP
        );
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        boolean reduced = shouldReduceMotionOrTransparency();

        if (useAgsl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null && !reduced) {
            drawAgsl(canvas, w, h);
            return;
        }

        drawFallbackCanvas(canvas, w, h, reduced);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void drawAgsl(@NonNull Canvas canvas, int w, int h) {
        if (runtimeShader == null) return;
        runtimeShader.setFloatUniform("uResolution", (float) w, (float) h);
        runtimeShader.setFloatUniform("uTime", phaseRad);
        runtimeShader.setFloatUniform("uWarmth", warmth);
        setColorUniform(runtimeShader, "uBaseColor", baseColor);
        setColorUniform(runtimeShader, "uBlob1Color", blob1Color);
        setColorUniform(runtimeShader, "uBlob2Color", blob2Color);
        setColorUniform(runtimeShader, "uBlob3Color", blob3Color);
        setColorUniform(runtimeShader, "uWarmColor", warmColor);
        canvas.drawRect(0f, 0f, w, h, agslPaint);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private static void setColorUniform(@NonNull RuntimeShader shader, @NonNull String name, int color) {
        shader.setFloatUniform(
                name,
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
                Color.alpha(color) / 255f
        );
    }

    private void drawFallbackCanvas(@NonNull Canvas canvas, int w, int h, boolean reduced) {
        canvas.drawColor(baseColor);

        float t = reduced ? 0f : phaseRad;
        float x1 = w * (0.22f + 0.14f * (float) Math.sin(t * 0.9));
        float y1 = h * (0.20f + 0.12f * (float) Math.cos(t * 0.7));

        float x2 = w * (0.80f + 0.12f * (float) Math.cos(t * 0.8));
        float y2 = h * (0.40f + 0.15f * (float) Math.sin(t * 0.6));

        float x3 = w * (0.45f + 0.16f * (float) Math.sin(t * 0.5 + 1.4));
        float y3 = h * (0.82f + 0.10f * (float) Math.cos(t * 0.85));

        if (blob1Shader != null) {
            blob1Matrix.setTranslate(x1, y1);
            blob1Shader.setLocalMatrix(blob1Matrix);
            canvas.drawRect(0f, 0f, w, h, blob1Paint);
        }
        if (blob2Shader != null) {
            blob2Matrix.setTranslate(x2, y2);
            blob2Shader.setLocalMatrix(blob2Matrix);
            canvas.drawRect(0f, 0f, w, h, blob2Paint);
        }
        if (blob3Shader != null) {
            blob3Matrix.setTranslate(x3, y3);
            blob3Shader.setLocalMatrix(blob3Matrix);
            canvas.drawRect(0f, 0f, w, h, blob3Paint);
        }
        if (warmth > 0.01f && warmShader != null) {
            warmPaint.setAlpha(Math.round(warmth * 170f));
            warmMatrix.setTranslate(x2 * 0.85f, y1 * 1.35f);
            warmShader.setLocalMatrix(warmMatrix);
            canvas.drawRect(0f, 0f, w, h, warmPaint);
        }
        if (!reduced) {
            canvas.drawRect(0f, 0f, w, h, grainPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefListener);
        updateAnimationState();
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefListener);
        stopAnimations();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimationState();
    }

    private void updateAnimationState() {
        if (!isAttachedToWindow() || getVisibility() != VISIBLE || shouldReduceMotionOrTransparency()) {
            stopAnimations();
            return;
        }
        if (phaseAnimator == null) {
            phaseAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2.0));
            phaseAnimator.setDuration(ANIMATION_PERIOD_MS);
            phaseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            phaseAnimator.setRepeatMode(ValueAnimator.RESTART);
            phaseAnimator.setInterpolator(new LinearInterpolator());
            phaseAnimator.addUpdateListener(anim -> {
                phaseRad = (float) anim.getAnimatedValue();
                invalidate();
            });
        }
        if (!phaseAnimator.isStarted()) {
            phaseAnimator.start();
        }
    }

    private void stopAnimations() {
        if (phaseAnimator != null) {
            phaseAnimator.cancel();
        }
        if (warmthAnimator != null) {
            warmthAnimator.cancel();
        }
    }
}
