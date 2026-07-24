package com.university.schedule.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.util.PrefsManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(this::navigate, 800);
    }

    private void navigate() {
        PrefsManager prefs = new PrefsManager(this);
        ScheduleRepository repo = ScheduleRepository.getInstance(this);
        Class<?> target;
        if (prefs.hasSelection() && repo.hasScheduleData()) {
            target = MainActivity.class;
        } else if (prefs.hasSelection()) {
            target = repo.loadFromCache() ? MainActivity.class : SelectionActivity.class;
        } else {
            target = SelectionActivity.class;
        }
        startActivity(new Intent(this, target));
        finish();
    }
}