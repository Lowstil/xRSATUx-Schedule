package com.university.schedule.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.university.schedule.R;
import com.university.schedule.model.GroupOrTeacher;

import java.util.List;
import java.util.Objects;

public class SelectionAdapter extends RecyclerView.Adapter<SelectionAdapter.ViewHolder> {
    public interface OnItemSelectedListener { void onItemSelected(GroupOrTeacher item); }

    private List<GroupOrTeacher> items;
    private final OnItemSelectedListener listener;
    private String currentName;
    private String currentType;

    public SelectionAdapter(List<GroupOrTeacher> items, OnItemSelectedListener l) { this.items = items; this.listener = l; }
    public void updateData(List<GroupOrTeacher> n) { this.items = n; notifyDataSetChanged(); }

    /** Текущий выбор пользователя — подсвечивается в списке пометкой "Текущий выбор". */
    public void setCurrentSelection(String name, String type) {
        this.currentName = name;
        this.currentType = type;
        notifyDataSetChanged();
    }

    private boolean isCurrent(GroupOrTeacher it) {
        return currentName != null && currentType != null
                && Objects.equals(currentType, it.getType())
                && currentName.equalsIgnoreCase(it.getName());
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_selection, p, false));
    }
    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        GroupOrTeacher it = items.get(position);
        h.tvName.setText(it.getName());
        boolean current = isCurrent(it);
        h.tvType.setText(current ? "Текущий выбор" : (it.isGroup() ? "Группа" : "Преподаватель"));
        int color = current ? R.color.primary : R.color.text_secondary;
        h.tvType.setTextColor(ContextCompat.getColor(h.itemView.getContext(), color));
        h.tvType.setTypeface(null, current ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onItemSelected(it); });
    }
    @Override public int getItemCount() { return items != null ? items.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvType;
        ViewHolder(@NonNull View v) { super(v); tvName = v.findViewById(R.id.tvName); tvType = v.findViewById(R.id.tvType); }
    }
}