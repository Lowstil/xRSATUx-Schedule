package com.university.schedule.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.university.schedule.R;
import com.university.schedule.data.ScheduleRepository;
import com.university.schedule.model.GroupOrTeacher;
import com.university.schedule.ui.adapter.SelectionAdapter;
import com.university.schedule.util.AppError;
import com.university.schedule.util.EdgeToEdgeCompat;
import com.university.schedule.util.FavoritesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SelectionActivity extends AppCompatActivity implements SelectionAdapter.OnItemSelectedListener {

    private static final int MODE_GROUPS = 0;
    private static final int MODE_TEACHERS = 1;
    private static final int MODE_FAVORITES = 2;

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
        setContentView(R.layout.activity_selection);
        EdgeToEdgeCompat.applyEdgeToEdgeInsets(this);

        repo = ScheduleRepository.getInstance(this);
        currentName = repo.getSelectionName();
        currentType = repo.getSelectionType();

        if (GroupOrTeacher.TYPE_TEACHER.equals(currentType)) {
            currentMode = MODE_TEACHERS;
        }

        toggleGroup = findViewById(R.id.toggleGroup);
        searchView = findViewById(R.id.searchView);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvStatus = findViewById(R.id.tvStatus);

        adapter = new SelectionAdapter(filtered, this);
        adapter.setCurrentSelection(currentName, currentType);
        adapter.setOnFavoriteChangeListener(() -> {
            if (currentMode == MODE_FAVORITES) {
                loadList();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        int checkedId = (currentMode == MODE_TEACHERS) ? R.id.btnTeachers : R.id.btnGroups;
        toggleGroup.check(checkedId);

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
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setVisibility(View.GONE);
                    loadList();
                });
            }

            @Override
            public void onError(AppError error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText(error.userMessage);
                    loadList();
                });
            }

            @Override
            public void onProgress(String m) {
                runOnUiThread(() -> tvStatus.setText(m));
            }
        }));
    }

    private void loadList() {
        ex.execute(() -> {
            List<GroupOrTeacher> list = new ArrayList<>();
            if (currentMode == MODE_FAVORITES) {
                list.addAll(FavoritesManager.getFavorites(this));
            } else if (currentMode == MODE_TEACHERS) {
                List<String> names = repo.getAllTeachers();
                for (String n : names) list.add(new GroupOrTeacher(n, GroupOrTeacher.TYPE_TEACHER));
            } else {
                List<String> names = repo.getAllGroups();
                for (String n : names) list.add(new GroupOrTeacher(n, GroupOrTeacher.TYPE_GROUP));
            }

            runOnUiThread(() -> {
                all = list;
                filter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
            });
        });
    }

    private void filter(String q) {
        filtered.clear();
        String query = (q != null) ? q.trim().toLowerCase() : "";
        for (GroupOrTeacher it : all) {
            if (query.isEmpty() || it.getName().toLowerCase().contains(query)) {
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
        // Добавляем выбранную группу в избранное автоматически, если там еще нет
        FavoritesManager.addFavorite(this, it.getType(), it.getName());
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ex.shutdown();
    }
}
