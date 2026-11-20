package com.university.schedule.ui.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.AppError;
import com.university.schedule.util.AppShortcutsHelper;
import com.university.schedule.util.EdgeToEdgeCompat;
import com.university.schedule.util.PrefsManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экран загрузки. Вся тяжёлая инициализация выполняется в фоновом потоке,
 * а на UI-поток возвращается только решение о навигации.
 */
public class SplashActivity extends AppCompatActivity {

    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        EdgeToEdgeCompat.enableEdgeToEdge(this);
        setContentView(R.layout.activity_splash);
        EdgeToEdgeCompat.applySystemBarInsets(findViewById(R.id.splashContent), true, true);
        GlassSurfaceHelper.applyGlassCard(findViewById(R.id.splashCard), 28f, true);
        ex.execute(this::resolveDestination);
    }

    private void resolveDestination() {
        PrefsManager p = new PrefsManager(this);
        ScheduleRepository r = ScheduleRepository.getInstance(this);
        final Class<?> target;
        if (p.hasSelection() && r.hasScheduleData()) {
            target = MainActivity.class;
        } else if (p.hasSelection()) {
            target = r.loadFromCache() ? MainActivity.class : SelectionActivity.class;
        } else {
            target = SelectionActivity.class;
        }
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Intent nextIntent = new Intent(this, target);
            if (target == MainActivity.class && AppShortcutsHelper.hasShortcutRequest(getIntent())) {
                AppShortcutsHelper.copyShortcutExtras(getIntent(), nextIntent);
                nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
            startActivity(nextIntent);
            finish();
        });

        if (p.hasSelection()) {
            r.maybeAutoRefresh(new ScheduleRepository.LoadCallback() {
                @Override public void onSuccess() { }
                @Override public void onError(AppError error) { }
                @Override public void onProgress(String message) { }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ex.shutdown();
    }
}
