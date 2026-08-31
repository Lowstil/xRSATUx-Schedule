package com.university.schedule.ui.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.util.AppError;
import com.university.schedule.util.PrefsManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экран загрузки. ВАЖНО: раньше navigate() вызывался из Handler на главном
 * потоке и делал там же ScheduleRepository.getInstance() (чтение БД) и,
 * в худшем случае, loadFromCache() — полный разбор кэшированного .xlsx
 * расписания И .xlsx переносов через Apache POI плюс запись сотен строк в
 * SQLite. На реальных файлах это занимает больше 5 секунд, и Android
 * показывает "Приложение не отвечает" (ANR). Теперь вся эта работа
 * выполняется в фоновом потоке, а на UI-поток возвращается только готовое
 * решение "куда переходить".
 *
 * Задача 5 (автообновление раз в сутки): после того как решение о переходе
 * принято и НЕЗАВИСИМО от него, отдельно проверяем ScheduleRepository.
 * shouldAutoRefresh() и, если пора, запускаем скачивание в фоне — уже ПОСЛЕ
 * того, как пользователь увидел расписание, чтобы не задерживать запуск
 * приложения ожиданием сети. Если автообновление успешно завершится, пока
 * MainActivity уже открыт, пользователь просто увидит обновлённые данные
 * при следующем действии (смене недели/дня) — навязчивых уведомлений об
 * этом фоновом обновлении намеренно нет.
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

        // Автообновление раз в сутки — запускается ПОСЛЕ решения о навигации
        // (пользователь уже увидит расписание без задержки на сеть), но всё
        // ещё в этом же фоновом потоке Splash, отдельно от UI. Если выбор
        // группы/преподавателя ещё не сделан — обновлять нечего, пропускаем.
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
