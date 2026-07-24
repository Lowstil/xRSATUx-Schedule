package com.university.schedule.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.university.schedule.R;
import com.university.schedule.model.GroupOrTeacher;

import java.util.List;

public class SelectionAdapter extends RecyclerView.Adapter<SelectionAdapter.ViewHolder> {

    public interface OnItemSelectedListener {
        void onItemSelected(GroupOrTeacher item);
    }

    private List<GroupOrTeacher> items;
    private final OnItemSelectedListener listener;

    public SelectionAdapter(List<GroupOrTeacher> items, OnItemSelectedListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void updateData(List<GroupOrTeacher> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selection, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        GroupOrTeacher item = items.get(position);
        h.tvName.setText(item.getName());
        h.tvType.setText(item.isGroup() ? "Группа" : "Преподаватель");
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemSelected(item);
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvType;
        ViewHolder(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvType = v.findViewById(R.id.tvType);
        }
    }
}