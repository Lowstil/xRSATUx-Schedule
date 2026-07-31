package com.university.schedule.ui.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.util.PrefsManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экран загрузки. ВАЖНО: раньше navigate() вызывался из Handler на главном
 * потоке и делал там же ScheduleRepository.getInstance() (чтение БД) и,
 * в худшем случае, loadFromCache() — полный разбор кэшированного .xlsx
 * расписания И .xlsx переносов через Apache POI плюс запись сотен строк в
 * SQLite. На реальных файлах это занимает больше 5 секунд, и Android
 * показывает "Приложение не отвечает" (ANR). Особенно часто это било по
 * пользователям сразу после обновления БД (schedule/transfers таблицы
 * пересоздаются при onUpgrade, поэтому hasScheduleData() возвращает false и
 * запускается тяжёлый loadFromCache() на первом же холодном старте после
 * обновления приложения). Теперь вся эта работа выполняется в фоновом
 * потоке, а на UI-поток возвращается только готовое решение "куда переходить".
 */
public class SplashActivity extends AppCompatActivity {

    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_splash);
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
            startActivity(new Intent(this, target));
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ex.shutdown();
    }
}
