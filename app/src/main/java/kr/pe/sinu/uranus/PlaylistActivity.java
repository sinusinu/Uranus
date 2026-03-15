// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
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

import kr.pe.sinu.uranus.databinding.ActivityPlaylistBinding;

public class PlaylistActivity extends AppCompatActivity {
    public static final String EXTRA_PLAYLIST = "playlist";
    public static final String EXTRA_PLAYLIST_NAME = "playlist_name";
    public static final String EXTRA_URIS_TO_ADD = "uris_to_add";
    public static final String EXTRA_PLAYLIST_TO_LOAD = "playlist_to_load";

    ActivityPlaylistBinding binding;

    ArrayList<PlaylistItem> playlist;
    PlaylistItemAdapter adapter;

    String playlistName;

    View viewSaveDialog;

    private ActivityResultLauncher<Intent> addResult;
    private ActivityResultLauncher<Intent> loadResult;

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityPlaylistBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ll_playlist), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        playlist = new ArrayList<>();
        adapter = new PlaylistItemAdapter(playlist);

        binding.rvPlaylistList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPlaylistList.setAdapter(adapter);
        if (binding.rvPlaylistList.getItemAnimator() != null) {
            ((SimpleItemAnimator)binding.rvPlaylistList.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        addResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), (result) -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                var uncastedUris = result.getData().getParcelableArrayListExtra(EXTRA_URIS_TO_ADD);
                if (uncastedUris != null) {
                    var uris = new ArrayList<Uri>(uncastedUris.size());
                    for (var u : uncastedUris) if (u instanceof Uri) uris.add((Uri)u);
                    setPlaylistFromUri(uris, true);
                }
            }
        });
        loadResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), (result) -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                var playlistToLoad = result.getData().getStringExtra(EXTRA_PLAYLIST_TO_LOAD);
                loadSavedPlaylist(playlistToLoad);
            }
        });

        binding.ivPlaylistAdd.setOnClickListener(v -> {
            addResult.launch(new Intent(this, LibraryActivity.class));
            overridePendingTransition(R.anim.anim_slide_exit, R.anim.anim_fade_exit);
        });
        binding.ivPlaylistClear.setOnClickListener(v -> {
            var ab = new AlertDialog.Builder(this)
                    .setMessage(R.string.playlist_warning_clear_message)
                    .setPositiveButton(R.string.common_yes, (d, i) -> {
                        playlist.clear();
                        playlistName = getString(R.string.common_default_playlist_name);
                        updateSubtitle();
                        adapter.notifyDataSetChanged();
                    })
                    .setNegativeButton(R.string.common_no, null)
                    .create();
            ab.show();
        });
        binding.ivPlaylistLoad.setOnClickListener(v -> {
            var intent = new Intent(this, PlaylistListActivity.class);
            intent.putExtra(PlaylistListActivity.EXTRA_PLAYLIST_IS_NOT_EMPTY, !playlist.isEmpty());
            loadResult.launch(intent);
            overridePendingTransition(R.anim.anim_slide_exit, R.anim.anim_fade_exit);
        });
        binding.ivPlaylistSave.setOnClickListener(v -> {
            if (playlist.isEmpty()) {
                Toast.makeText(this, R.string.playlist_warning_cannot_save_empty_playlist, Toast.LENGTH_SHORT).show();
                return;
            }
            viewSaveDialog = getLayoutInflater().inflate(R.layout.dialog_playlist_save, null);
            ((EditText)viewSaveDialog.findViewById(R.id.edt_playlist_save_name)).setText(playlistName);
            var ab = new AlertDialog.Builder(this)
                    .setView(viewSaveDialog)
                    .setPositiveButton(R.string.common_save, null)
                    .setNegativeButton(R.string.common_cancel, null)
                    .create();
            ab.show();
            ab.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(vv -> {
                var newPlaylistName = ((EditText)viewSaveDialog.findViewById(R.id.edt_playlist_save_name)).getText().toString().trim();
                if (newPlaylistName.isEmpty()) {
                    Toast.makeText(this, R.string.playlist_save_error_empty_name, Toast.LENGTH_SHORT).show();
                } else {
                    playlistName = newPlaylistName;
                    var eggCheck = Util.toCrc32(playlistName.toLowerCase());
                    if (playlistName.length() == 17 && eggCheck.equals("52a176c3")) {
                        playlistName = playlistName.toLowerCase();
                        playlistName = "" +
                                playlistName.charAt(5) +
                                playlistName.charAt(7) +
                                playlistName.charAt(14) +
                                playlistName.charAt(13) +
                                playlistName.charAt(14) +
                                playlistName.charAt(7) +
                                playlistName.charAt(14) +
                                playlistName.charAt(13);
                    }
                    updateSubtitle();
                    saveCurrentPlaylist();
                    ab.dismiss();
                }
            });
        });
        binding.ivPlaylistOk.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.putParcelableArrayListExtra(MainActivity.EXTRA_NEW_PLAYLIST, playlist);
            intent.putExtra(MainActivity.EXTRA_NEW_PLAYLIST_NAME, playlistName);
            setResult(RESULT_OK, intent);
            finish();
        });
        binding.ivPlaylistCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        for (View v : new View[] {
                binding.ivPlaylistAdd,
                binding.ivPlaylistClear,
                binding.ivPlaylistSave,
                binding.ivPlaylistLoad,
                binding.ivPlaylistOk,
                binding.ivPlaylistCancel,
        }) {
            TooltipCompat.setTooltipText(v, v.getContentDescription());
        }

        playlistName = getIntent().getStringExtra(EXTRA_PLAYLIST_NAME);
        if (playlistName == null) playlistName = getString(R.string.common_default_playlist_name);

        var prevPlaylist = getIntent().getParcelableArrayListExtra(EXTRA_PLAYLIST);
        if (prevPlaylist != null && !prevPlaylist.isEmpty()) {
            for (var upi : prevPlaylist) playlist.add((PlaylistItem)upi);
            updateSubtitle();
            adapter.notifyDataSetChanged();
        }
    }

    private void updateSubtitle() {
        binding.tvPlaylistSubtitle.setText(String.format(getResources().getQuantityString(R.plurals.playlist_subtitle_count, playlist.size()), playlistName, playlist.size()));
    }

    private void saveCurrentPlaylist() {
        ArrayList<SavedPlaylist> savedPlaylists = new ArrayList<>();
        File f = new File(getFilesDir(), "saved_playlists.json");
        if (f.exists()) {
            var spr = Util.readString(f);
            try {
                JSONObject spj = new JSONObject(spr);
                var spjks = spj.keys();
                while (spjks.hasNext()) {
                    var spjk = spjks.next();
                    var spl = spj.getJSONArray(spjk);
                    var splj = new SavedPlaylist(spjk);
                    for (int i = 0; i < spl.length(); i++) {
                        var spi = spl.getString(i);
                        splj.uris.add(spi);
                    }
                    savedPlaylists.add(splj);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        var currentPlaylist = new SavedPlaylist(playlistName);
        for (var pi : playlist) currentPlaylist.uris.add(pi.uriSource);

        int idx = -1;
        for (int i = 0; i < savedPlaylists.size(); i++) {
            if (savedPlaylists.get(i).name.equals(playlistName)) {
                idx = i;
                break;
            }
        }
        if (idx == -1) {
            // add as new playlist
            savedPlaylists.add(currentPlaylist);
        } else {
            savedPlaylists.set(idx, currentPlaylist);
        }

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
        Toast.makeText(this, String.format(getString(R.string.playlist_save_saved), playlistName), Toast.LENGTH_SHORT).show();
    }

    private void loadSavedPlaylist(String name) {
        ArrayList<SavedPlaylist> savedPlaylists = new ArrayList<>();
        File f = new File(getFilesDir(), "saved_playlists.json");
        if (f.exists()) {
            var spr = Util.readString(f);
            try {
                JSONObject spj = new JSONObject(spr);
                var spjks = spj.keys();
                while (spjks.hasNext()) {
                    var spjk = spjks.next();
                    var spl = spj.getJSONArray(spjk);
                    var splj = new SavedPlaylist(spjk);
                    for (int i = 0; i < spl.length(); i++) {
                        var spi = spl.getString(i);
                        splj.uris.add(spi);
                    }
                    savedPlaylists.add(splj);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        SavedPlaylist tp = null;
        for (var p : savedPlaylists) {
            if (p.name.equals(name)) {
                tp = p;
                break;
            }
        }
        if (tp == null) {
            // TODO: show error
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (String u : tp.uris) uris.add(Uri.parse(u));
        // TODO: verify uris before feeding into spfu func

        setPlaylistFromUri(uris, false);
    }

    private static class SavedPlaylist {
        String name;
        ArrayList<String> uris;

        public SavedPlaylist(String name) {
            this.name = name;
            uris = new ArrayList<>();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.anim_fade_enter, R.anim.anim_slide_enter);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setPlaylistFromUri(ArrayList<Uri> playlistUris, boolean append) {
        MediaMetadataCache mmc = MediaMetadataCache.getInstance();
        if (!append) playlist.clear();
        long[] sizes = new long[playlistUris.size()];
        long[] lastModifiedTss = new long[playlistUris.size()];
        for (int i = 0; i < playlistUris.size(); i++) {
            var uri = playlistUris.get(i);
            try (var cursor = getContentResolver().query(uri, new String[] {
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            }, null, null, null)) {
                if (cursor == null) {
                    continue;
                }
                cursor.moveToFirst();
                sizes[i] = cursor.getLong(0);
                lastModifiedTss[i] = cursor.getLong(1);
            }
        }
        for (int i = 0; i < playlistUris.size(); i++) {
            var uri = playlistUris.get(i);
            if (!mmc.isMediaMetadataCached(uri, sizes[i], lastModifiedTss[i])) {
                binding.rvPlaylistList.setVisibility(View.INVISIBLE);
                binding.llPlaylistPbrContainer.setVisibility(View.VISIBLE);
                break;
            }
        }
        new Thread(() -> {
            for (int i = 0; i < playlistUris.size(); i++) {
                var uri = playlistUris.get(i);
                String filename = Util.getFilenameWithoutExtFromUri(PlaylistActivity.this, uri);
                var mm = mmc.getMediaMetadata(PlaylistActivity.this, filename, uri, sizes[i], lastModifiedTss[i]);
                var pi = new PlaylistItem(uri.toString(), filename, mm.title, mm.artist, mm.album);
                playlist.add(pi);
            }
            runOnUiThread(() -> {
                binding.rvPlaylistList.setVisibility(View.VISIBLE);
                binding.llPlaylistPbrContainer.setVisibility(View.GONE);
                updateSubtitle();
                adapter.notifyDataSetChanged();
            });
        }).start();
    }
}