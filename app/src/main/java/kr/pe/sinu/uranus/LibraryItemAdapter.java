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

public class LibraryItemAdapter extends RecyclerView.Adapter<LibraryItemAdapter.ViewHolder> {
    private final ArrayList<LibraryItem> list;
    private final OnItemClickListener onItemClickListener;
    private final OnItemRemoveClickListener onItemRemoveClickListener;

    public LibraryItemAdapter(ArrayList<LibraryItem> list, OnItemClickListener onItemClickListener, OnItemRemoveClickListener onItemRemoveClickListener) {
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
        var item = list.get(position);
        switch (item.type) {
            case LibraryItem.TYPE_ROOT_FOLDER:
                holder.itemView.setBackgroundResource(R.drawable.background_selectable);
                holder.tvSubtitle.setVisibility(View.VISIBLE);
                holder.ivIcon.setImageResource(R.drawable.ic_folder);
                holder.tvTitle.setText(item.title);
                holder.tvSubtitle.setText(R.string.library_item_folder_subtitle);
                holder.ivDelete.setVisibility(View.VISIBLE);
                holder.ivDelete.setOnClickListener(v -> {
                    if (onItemRemoveClickListener != null) onItemRemoveClickListener.onItemRemoveClick(position);
                });
                break;
            case LibraryItem.TYPE_ROOT_ADD_FOLDER:
                holder.itemView.setBackgroundResource(R.drawable.background_selectable);
                holder.ivIcon.setImageResource(R.drawable.ic_add);
                holder.tvTitle.setText(R.string.library_item_add_folder_title);
                holder.tvSubtitle.setVisibility(View.GONE);
                holder.ivDelete.setVisibility(View.GONE);
                break;
            case LibraryItem.TYPE_FOLDER_UP:
                holder.itemView.setBackgroundResource(R.drawable.background_selectable);
                holder.ivIcon.setImageResource(R.drawable.ic_arrow_upward);
                holder.tvTitle.setText(R.string.library_item_up_title);
                holder.tvSubtitle.setText(R.string.library_item_up_subtitle);
                holder.tvSubtitle.setVisibility(View.VISIBLE);
                holder.ivDelete.setVisibility(View.GONE);
                break;
            case LibraryItem.TYPE_FOLDER_MUSIC:
                if (item.selected) {
                    holder.ivIcon.setImageResource(R.drawable.ic_ok);
                    holder.itemView.setBackgroundResource(R.drawable.background_selected_selectable);
                } else {
                    holder.ivIcon.setImageResource(R.drawable.ic_music);
                    holder.itemView.setBackgroundResource(R.drawable.background_selectable);
                }
                holder.tvTitle.setText(item.title);
                holder.tvSubtitle.setText(item.subtitle);
                holder.tvSubtitle.setVisibility(View.VISIBLE);
                holder.ivDelete.setVisibility(View.GONE);
                break;
            case LibraryItem.TYPE_FOLDER_FOLDER:
                holder.itemView.setBackgroundResource(R.drawable.background_selectable);
                holder.ivIcon.setImageResource(R.drawable.ic_folder);
                holder.tvTitle.setText(item.title);
                holder.tvSubtitle.setText(R.string.library_item_folder_subtitle);
                holder.tvSubtitle.setVisibility(View.VISIBLE);
                holder.ivDelete.setVisibility(View.GONE);
                break;
            default:
                holder.itemView.setBackgroundResource(R.drawable.background_selectable);
                holder.ivDelete.setVisibility(View.GONE);
                holder.tvSubtitle.setVisibility(View.VISIBLE);
                break;
        }
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
