// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class PlaylistListItemAdapter extends RecyclerView.Adapter<PlaylistListItemAdapter.ViewHolder> {
    private Context context;
    private ArrayList<PlaylistListItem> list;
    private OnItemClickListener onItemClickListener;
    private OnItemRemoveClickListener onItemRemoveClickListener;

    public PlaylistListItemAdapter(Context context, ArrayList<PlaylistListItem> list, OnItemClickListener onItemClickListener, OnItemRemoveClickListener onItemRemoveClickListener) {
        this.context = context;
        this.list = list;
        this.onItemClickListener = onItemClickListener;
        this.onItemRemoveClickListener = onItemRemoveClickListener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvTitle;
        public final TextView tvSubtitle;
        public final ImageView ivIcon;
        public final ImageView ivDelete;

        public ViewHolder(View view) {
            super(view);

            tvTitle = view.findViewById(R.id.tv_library_item_title);
            tvSubtitle = view.findViewById(R.id.tv_library_item_subtitle);
            ivIcon = view.findViewById(R.id.iv_library_item_icon);
            ivDelete = view.findViewById(R.id.iv_library_item_delete);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        var v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_library_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tvTitle.setText(list.get(position).name);
        holder.tvSubtitle.setText(String.format(context.getResources().getQuantityText(R.plurals.playlist_list_item_subtitle_count, list.get(position).uris.size()).toString(), list.get(position).uris.size()));
        holder.ivIcon.setImageResource(list.get(position).selected ? R.drawable.ic_ok : R.drawable.ic_playlist);
        holder.ivDelete.setOnClickListener(v -> {
            if (onItemRemoveClickListener != null) onItemRemoveClickListener.onItemRemoveClick(position);
        });
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) onItemClickListener.onItemClick(position);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnItemRemoveClickListener {
        void onItemRemoveClick(int position);
    }
}
