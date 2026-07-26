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
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(this::navigate, 600);
    }
    private void navigate() {
        PrefsManager p = new PrefsManager(this);
        ScheduleRepository r = ScheduleRepository.getInstance(this);
        Class<?> t;
        if (p.hasSelection() && r.hasScheduleData()) t = MainActivity.class;
        else if (p.hasSelection()) t = r.loadFromCache() ? MainActivity.class : SelectionActivity.class;
        else t = SelectionActivity.class;
        startActivity(new Intent(this, t));
        finish();
    }
}