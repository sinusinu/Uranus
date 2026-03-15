// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

import kr.pe.sinu.uranus.databinding.ActivityPlaylistListBinding;

public class PlaylistListActivity extends AppCompatActivity {
    public static final String EXTRA_PLAYLIST_IS_NOT_EMPTY = "playlist_is_not_empty";

    ArrayList<PlaylistListItem> savedPlaylists;
    PlaylistListItemAdapter adapter;
    PlaylistListItemClickListener itemClickListener;
    PlaylistListItemRemoveClickListener itemRemoveClickListener;

    ActivityPlaylistListBinding binding;
    int selected = -1;

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityPlaylistListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.llPlaylistList, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        itemClickListener = new PlaylistListItemClickListener();
        itemRemoveClickListener = new PlaylistListItemRemoveClickListener();

        savedPlaylists = new ArrayList<>();
        adapter = new PlaylistListItemAdapter(this, savedPlaylists, itemClickListener, itemRemoveClickListener);
        binding.rvPlaylistList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPlaylistList.setAdapter(adapter);
        if (binding.rvPlaylistList.getItemAnimator() != null) {
            ((SimpleItemAnimator)binding.rvPlaylistList.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        var showReplaceWarning = getIntent().getBooleanExtra(EXTRA_PLAYLIST_IS_NOT_EMPTY, false);
        binding.tvPlaylistListWarningReplace.setVisibility(showReplaceWarning ? View.VISIBLE : View.INVISIBLE);

        binding.ivPlaylistListOk.setOnClickListener(v -> {
            if (selected == -1) {
                Toast.makeText(this, R.string.playlist_list_warning_no_selection, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent();
            intent.putExtra(PlaylistActivity.EXTRA_PLAYLIST_TO_LOAD, savedPlaylists.get(selected).name);
            setResult(RESULT_OK, intent);
            finish();
        });
        binding.ivPlaylistListCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        loadSavedPlaylists();
        adapter.notifyDataSetChanged();
    }

    private void loadSavedPlaylists() {
        savedPlaylists.clear();
//        File f = new File(getFilesDir(), "saved_playlists.json");
//        if (f.exists()) {
//            var spr = Util.readString(f);
//            try {
//                JSONObject spj = new JSONObject(spr);
//                var spjks = spj.keys();
//                while (spjks.hasNext()) {
//                    var spjk = spjks.next();
//                    var sp = spj.getJSONArray(spjk);
//                    savedPlaylists.add(new PlaylistListItem(spjk, sp.));
//                }
//            } catch (JSONException e) {
//                savedPlaylists.clear();
//            }
//        }

        File f = new File(getFilesDir(), "saved_playlists.json");
        if (f.exists()) {
            var spr = Util.readString(f);
            try {
                JSONObject spj = new JSONObject(spr);
                var spjks = spj.keys();
                while (spjks.hasNext()) {
                    var spjk = spjks.next();
                    var spl = spj.getJSONArray(spjk);
                    var spll = new ArrayList<String>();
                    for (int i = 0; i < spl.length(); i++) {
                        var spi = spl.getString(i);
                        spll.add(spi);
                    }
                    var splj = new PlaylistListItem(spjk, spll);
                    savedPlaylists.add(splj);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        binding.tvPlaylistListSubtitle.setText(String.format(getResources().getQuantityString(R.plurals.playlist_list_subtitle_count, savedPlaylists.size()), savedPlaylists.size()));
    }

    private void saveSavedPlaylist() {
        File f = new File(getFilesDir(), "saved_playlists.json");

        var newPlaylistsJson = new JSONObject();
        try {
            for (var sp : savedPlaylists) {
                var pj = new JSONArray();
                for (var spi : sp.uris) pj.put(spi);
                newPlaylistsJson.put(sp.name, pj);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        Util.writeString(f, newPlaylistsJson.toString());
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.anim_fade_enter, R.anim.anim_slide_enter);
    }

    public class PlaylistListItemClickListener implements PlaylistListItemAdapter.OnItemClickListener {
        @Override
        public void onItemClick(int position) {
            var prevSelect = selected;
            if (prevSelect != -1) {
                savedPlaylists.get(prevSelect).selected = false;
                adapter.notifyItemChanged(prevSelect);
            }
            selected = position;
            savedPlaylists.get(selected).selected = true;
            adapter.notifyItemChanged(selected);
        }
    }

    public class PlaylistListItemRemoveClickListener implements PlaylistListItemAdapter.OnItemRemoveClickListener {
        @Override
        public void onItemRemoveClick(int position) {
            @SuppressLint("NotifyDataSetChanged")
            var ab = new AlertDialog.Builder(PlaylistListActivity.this)
                    .setMessage(String.format(getString(R.string.playlist_list_warning_remove_list_message), savedPlaylists.get(position).name))
                    .setPositiveButton(R.string.common_yes, (d, i) -> {
                        if (selected == position) selected = -1;
                        else if (selected > position) selected--;
                        savedPlaylists.remove(position);
                        saveSavedPlaylist();
                        adapter.notifyDataSetChanged();
                    })
                    .setNegativeButton(R.string.common_no, null)
                    .create();
            ab.show();
        }
    }
}