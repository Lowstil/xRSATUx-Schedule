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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SelectionActivity extends AppCompatActivity implements SelectionAdapter.OnItemSelectedListener {
    private MaterialButtonToggleGroup toggleGroup;
    private SearchView searchView;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvStatus;
    private SelectionAdapter adapter;
    private List<GroupOrTeacher> all = new ArrayList<>();
    private List<GroupOrTeacher> filtered = new ArrayList<>();
    private String type = GroupOrTeacher.TYPE_GROUP;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private ScheduleRepository repo;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_selection);
        repo = ScheduleRepository.getInstance(this);
        toggleGroup = findViewById(R.id.toggleGroup);
        searchView = findViewById(R.id.searchView);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvStatus = findViewById(R.id.tvStatus);

        adapter = new SelectionAdapter(filtered, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        toggleGroup.addOnButtonCheckedListener((g, id, c) -> {
            if (!c) return;
            type = (id == R.id.btnTeachers) ? GroupOrTeacher.TYPE_TEACHER : GroupOrTeacher.TYPE_GROUP;
            loadList();
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override public boolean onQueryTextChange(String q) { filter(q); return true; }
        });

        if (!repo.hasScheduleData()) loadNet(); else loadList();
    }

    private void loadNet() {
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("Загрузка расписания...");
        tvEmpty.setVisibility(View.GONE);
        ex.execute(() -> repo.loadScheduleFromNetwork(new ScheduleRepository.LoadCallback() {
            @Override public void onSuccess() { runOnUiThread(() -> { progressBar.setVisibility(View.GONE); tvStatus.setVisibility(View.GONE); loadList(); }); }
            @Override public void onError(String m) { runOnUiThread(() -> { progressBar.setVisibility(View.GONE); tvStatus.setText("Ошибка: " + m); loadList(); }); }
            @Override public void onProgress(String m) { runOnUiThread(() -> tvStatus.setText(m)); }
        }));
    }

    private void loadList() {
        ex.execute(() -> {
            List<String> names = GroupOrTeacher.TYPE_GROUP.equals(type) ? repo.getAllGroups() : repo.getAllTeachers();
            List<GroupOrTeacher> list = new ArrayList<>();
            for (String n : names) list.add(new GroupOrTeacher(n, type));
            runOnUiThread(() -> { all = list; filter(searchView.getQuery() != null ? searchView.getQuery().toString() : ""); });
        });
    }

    private void filter(String q) {
        filtered.clear();
        if (q == null || q.trim().isEmpty()) filtered.addAll(all);
        else { String l = q.toLowerCase(); for (GroupOrTeacher it : all) if (it.getName().toLowerCase().contains(l)) filtered.add(it); }
        adapter.updateData(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onItemSelected(GroupOrTeacher item) {
        repo.saveUserSelection(item.getType(), item.getName());
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i); finish();
    }

    @Override protected void onDestroy() { super.onDestroy(); ex.shutdown(); }
}