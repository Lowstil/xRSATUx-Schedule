package com.university.schedule.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.ui.adapter.SelectionAdapter;
import com.university.schedule.ui.glass.GlassSurfaceHelper;
import com.university.schedule.util.AppError;
import com.university.schedule.util.EdgeToEdgeCompat;
import com.university.schedule.util.FavoritesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SelectionActivity extends AppCompatActivity implements SelectionAdapter.OnItemSelectedListener {

    private static final int MODE_GROUPS = 0;
    private static final int MODE_TEACHERS = 1;
    private static final int MODE_FAVORITES = 2;

    private static final String KEY_MODE = "state_mode";
    private static final String KEY_QUERY = "state_query";

    private MaterialButtonToggleGroup toggleGroup;
    private SearchView searchView;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvStatus;
    private SelectionAdapter adapter;

    private List<GroupOrTeacher> all = new ArrayList<>();
    private List<GroupOrTeacher> filtered = new ArrayList<>();
    private int currentMode = MODE_GROUPS;
    private String currentName, currentType;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private ScheduleRepository repo;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        EdgeToEdgeCompat.enableEdgeToEdge(this);
        setContentView(R.layout.activity_selection);

        EdgeToEdgeCompat.applySystemBarInsets(findViewById(R.id.selectionContent), true, false);
        GlassSurfaceHelper.applyGlassCard(findViewById(R.id.toggleCapsule), 999f, false);
        GlassSurfaceHelper.applyGlassCard(findViewById(R.id.searchCapsule), 999f, false);

        repo = ScheduleRepository.getInstance(this);
        currentName = repo.getSelectionName();
        currentType = repo.getSelectionType();

        if (GroupOrTeacher.TYPE_TEACHER.equals(currentType)) {
            currentMode = MODE_TEACHERS;
        }
        String restoredQuery = "";
        if (s != null) {
            currentMode = s.getInt(KEY_MODE, currentMode);
            restoredQuery = s.getString(KEY_QUERY, "");
        }

        toggleGroup = findViewById(R.id.toggleGroup);
        searchView = findViewById(R.id.searchView);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvStatus = findViewById(R.id.tvStatus);

        EdgeToEdgeCompat.applySystemBarInsets(recyclerView, false, true);

        adapter = new SelectionAdapter(filtered, this);
        adapter.setCurrentSelection(currentName, currentType);
        adapter.setOnFavoriteChangeListener(() -> {
            if (currentMode == MODE_FAVORITES) {
                loadList();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        int checkedId;
        if (currentMode == MODE_TEACHERS) {
            checkedId = R.id.btnTeachers;
        } else if (currentMode == MODE_FAVORITES) {
            checkedId = R.id.btnFavorites;
        } else {
            checkedId = R.id.btnGroups;
        }
        toggleGroup.check(checkedId);

        if (!restoredQuery.isEmpty()) {
            searchView.setQuery(restoredQuery, false);
        }

        toggleGroup.addOnButtonCheckedListener((g, id, c) -> {
            if (!c) return;
            if (id == R.id.btnTeachers) {
                currentMode = MODE_TEACHERS;
            } else if (id == R.id.btnFavorites) {
                currentMode = MODE_FAVORITES;
            } else {
                currentMode = MODE_GROUPS;
            }
            loadList();
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) { return false; }

            @Override
            public boolean onQueryTextChange(String q) {
                filter(q);
                return true;
            }
        });

        if (!repo.hasScheduleData()) {
            loadNet();
        } else {
            loadList();
        }
    }

    private void loadNet() {
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("Загрузка расписания...");
        tvEmpty.setVisibility(View.GONE);

        ex.execute(() -> repo.loadScheduleFromNetwork(new ScheduleRepository.LoadCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setVisibility(View.GONE);
                    loadList();
                });
            }

            @Override
            public void onError(AppError error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText(error.userMessage);
                    loadList();
                });
            }

            @Override
            public void onProgress(String m) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tvStatus.setText(m);
                });
            }
        }));
    }

    private void loadList() {
        final int modeSnapshot = currentMode;
        ex.execute(() -> {
            List<GroupOrTeacher> list = new ArrayList<>();
            if (modeSnapshot == MODE_FAVORITES) {
                list.addAll(FavoritesManager.getFavorites(this));
            } else if (modeSnapshot == MODE_TEACHERS) {
                List<String> names = repo.getAllTeachers();
                for (String n : names) list.add(new GroupOrTeacher(n, GroupOrTeacher.TYPE_TEACHER));
            } else {
                List<String> names = repo.getAllGroups();
                for (String n : names) list.add(new GroupOrTeacher(n, GroupOrTeacher.TYPE_GROUP));
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                all = list;
                filter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
            });
        });
    }

    private void filter(String q) {
        filtered.clear();
        String query = (q != null) ? q.trim().toLowerCase(Locale.ROOT) : "";
        for (GroupOrTeacher it : all) {
            if (query.isEmpty() || it.getName().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(it);
            }
        }
        adapter.updateData(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        if (currentMode == MODE_FAVORITES && filtered.isEmpty()) {
            tvEmpty.setText("В избранном пока пусто.\nНажмите на звёздочку рядом с группой или преподавателем.");
        } else {
            tvEmpty.setText(R.string.empty_list);
        }
    }

    @Override
    public void onItemSelected(GroupOrTeacher it) {
        repo.saveSelection(it.getType(), it.getName());
        FavoritesManager.addFavorite(this, it.getType(), it.getName());
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_MODE, currentMode);
        if (searchView != null && searchView.getQuery() != null) {
            outState.putString(KEY_QUERY, searchView.getQuery().toString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ex.shutdown();
    }
}
