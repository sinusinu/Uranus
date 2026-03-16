// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;

public class PlaylistItemAdapter extends RecyclerView.Adapter<PlaylistItemAdapter.ViewHolder> {
    private ArrayList<PlaylistItem> list;
    private HashSet<Integer> selected;
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;

    public PlaylistItemAdapter(ArrayList<PlaylistItem> list, HashSet<Integer> selected, OnItemClickListener onItemClickListener, OnItemLongClickListener onItemLongClickListener) {
        this.list = list;
        this.selected = selected;
        this.onItemClickListener = onItemClickListener;
        this.onItemLongClickListener = onItemLongClickListener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvTitle;
        public final TextView tvSubtitle;
        public final ImageView ivIcon;

        public ViewHolder(View view) {
            super(view);

            tvTitle = view.findViewById(R.id.tv_playlist_item_title);
            tvSubtitle = view.findViewById(R.id.tv_playlist_item_subtitle);
            ivIcon = view.findViewById(R.id.iv_playlist_item_icon);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        var v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_playlist_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tvTitle.setText(list.get(position).title);
        holder.tvSubtitle.setText(list.get(position).filename);
        if (selected.contains(position)) {
            holder.ivIcon.setImageResource(R.drawable.ic_ok);
            holder.itemView.setBackgroundResource(R.drawable.background_selected_selectable);
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_music);
            holder.itemView.setBackgroundResource(R.drawable.background_selectable);
        }
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) onItemClickListener.onItemClick(position);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (onItemLongClickListener != null) {
                onItemLongClickListener.onItemLongClick(position);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }
}
