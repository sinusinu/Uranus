// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
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
import java.util.Collections;
import java.util.HashSet;

import kr.pe.sinu.uranus.databinding.ActivityPlaylistBinding;

public class PlaylistActivity extends AppCompatActivity {
    public static final String EXTRA_PLAYLIST = "playlist";
    public static final String EXTRA_PLAYLIST_NAME = "playlist_name";
    public static final String EXTRA_URIS_TO_ADD = "uris_to_add";
    public static final String EXTRA_PLAYLIST_TO_LOAD = "playlist_to_load";

    public static final String FILENAME_SAVED_PLAYLISTS_JSON = "saved_playlists.json";

    ActivityPlaylistBinding binding;

    ArrayList<PlaylistItem> playlist;
    HashSet<Integer> selected;
    PlaylistItemAdapter adapter;
    PlaylistItemClickListener onItemClickListener;
    PlaylistItemLongClickListener onItemLongClickListener;

    boolean isLoading = false;
    boolean shouldCancelLoading = false;

    String playlistName;

    View viewSaveDialog;

    int eggPreCond = 0;

    private ActivityResultLauncher<Intent> addResult;
    private ActivityResultLauncher<Intent> loadResult;

    @SuppressLint({"NotifyDataSetChanged", "InflateParams"})
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
        selected = new HashSet<>();
        onItemClickListener = new PlaylistItemClickListener();
        onItemLongClickListener = new PlaylistItemLongClickListener();
        adapter = new PlaylistItemAdapter(playlist, selected, onItemClickListener, onItemLongClickListener);

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
            if (isLoading) return;
            addResult.launch(new Intent(this, LibraryActivity.class));
            overridePendingTransition(R.anim.anim_slide_exit, R.anim.anim_fade_exit);
        });
        binding.ivPlaylistClear.setOnClickListener(v -> {
            if (isLoading) return;
            var ab = new AlertDialog.Builder(this)
                    .setMessage(R.string.playlist_warning_clear_message)
                    .setPositiveButton(R.string.common_yes, (d, i) -> {
                        playlist.clear();
                        playlistName = getString(R.string.common_default_playlist_name);
                        updateSubtitle();
                        adapter.notifyDataSetChanged();
                    })
                    .setNegativeButton(R.string.common_no, null)
                    .setOnDismissListener(d -> eggPreCond++)
                    .create();
            ab.show();
        });
        binding.ivPlaylistLoad.setOnClickListener(v -> {
            if (isLoading) return;
            var intent = new Intent(this, PlaylistListActivity.class);
            intent.putExtra(PlaylistListActivity.EXTRA_PLAYLIST_IS_NOT_EMPTY, !playlist.isEmpty());
            loadResult.launch(intent);
            overridePendingTransition(R.anim.anim_slide_exit, R.anim.anim_fade_exit);
        });
        binding.ivPlaylistSave.setOnClickListener(v -> {
            if (isLoading) return;
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
                    if (eggPreCond >= 5) {
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
                    }
                    updateSubtitle();
                    saveCurrentPlaylist();
                    ab.dismiss();
                }
            });
        });
        binding.ivPlaylistSelRemove.setOnClickListener(v -> {
            for (int i = playlist.size() - 1; i >= 0; i--) {
                if (selected.contains(i)) playlist.remove(i);
            }
            selected.clear();
            updateSubtitle();
            updateControlBar();
            adapter.notifyDataSetChanged();
        });
        binding.ivPlaylistSelDeselectAll.setOnClickListener(v -> {
            selected.clear();
            updateControlBar();
            adapter.notifyDataSetChanged();
        });
        binding.ivPlaylistSelMoveUp.setOnClickListener(v -> {
            moveSelectionUp();
            adapter.notifyDataSetChanged();
        });
        binding.ivPlaylistSelMoveDown.setOnClickListener(v -> {
            moveSelectionDown();
            adapter.notifyDataSetChanged();
        });
        binding.ivPlaylistOk.setOnClickListener(v -> {
            if (isLoading) return;
            Intent intent = new Intent();
            intent.putParcelableArrayListExtra(MainActivity.EXTRA_NEW_PLAYLIST, playlist);
            intent.putExtra(MainActivity.EXTRA_NEW_PLAYLIST_NAME, playlistName);
            setResult(RESULT_OK, intent);
            finish();
        });
        binding.ivPlaylistCancel.setOnClickListener(v -> {
            shouldCancelLoading = true;
            setResult(RESULT_CANCELED);
            finish();
        });

        for (View v : new View[] {
                binding.ivPlaylistAdd,
                binding.ivPlaylistClear,
                binding.ivPlaylistSave,
                binding.ivPlaylistLoad,
                binding.ivPlaylistSelRemove,
                binding.ivPlaylistSelDeselectAll,
                binding.ivPlaylistSelMoveUp,
                binding.ivPlaylistSelMoveDown,
                binding.ivPlaylistOk,
                binding.ivPlaylistCancel,
        }) {
            TooltipCompat.setTooltipText(v, v.getContentDescription());
        }

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!selected.isEmpty()) {
                    // deselect all
                    selected.clear();
                    updateControlBar();
                    adapter.notifyDataSetChanged();
                } else {
                    if (isLoading) shouldCancelLoading = true;
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        });

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
        File f = new File(getFilesDir(), FILENAME_SAVED_PLAYLISTS_JSON);
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
        File f = new File(getFilesDir(), FILENAME_SAVED_PLAYLISTS_JSON);
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
            Toast.makeText(this, R.string.playlist_error_playlist_inaccessible, Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        var cr = getContentResolver();
        boolean complete = true;
        for (String u : tp.uris) {
            var uri = Uri.parse(u);
            boolean fileExists;
            try (var c = cr.query(uri, null, null, null, null)) {
                fileExists = (c != null) && (c.getCount() > 0);
            } catch (Exception ignored) {
                fileExists = false;
            }
            if (fileExists) uris.add(uri);
            else complete = false;
        }

        if (!complete) {
            Toast.makeText(this, R.string.playlist_error_some_files_inaccessible, Toast.LENGTH_SHORT).show();
        }

        playlistName = name;
        updateSubtitle();
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
        isLoading = true;
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
            if (!mmc.isMediaMetadataCached(this, uri, sizes[i], lastModifiedTss[i])) {
                if (shouldCancelLoading) return;
                binding.rvPlaylistList.setVisibility(View.INVISIBLE);
                binding.llPlaylistPbrContainer.setVisibility(View.VISIBLE);
                break;
            }
        }
        if (!shouldCancelLoading) {
            new Thread(() -> {
                for (int i = 0; i < playlistUris.size(); i++) {
                    var uri = playlistUris.get(i);
                    String filename = Util.getFilenameFromUri(PlaylistActivity.this, uri);
                    var mm = mmc.getMediaMetadata(PlaylistActivity.this, filename, uri, sizes[i], lastModifiedTss[i]);
                    var pi = new PlaylistItem(uri.toString(), filename, mm.title, mm.artist, mm.album);
                    playlist.add(pi);
                }
                if (!shouldCancelLoading) {
                    runOnUiThread(() -> {
                        binding.rvPlaylistList.setVisibility(View.VISIBLE);
                        binding.llPlaylistPbrContainer.setVisibility(View.GONE);
                        updateSubtitle();
                        adapter.notifyDataSetChanged();
                        isLoading = false;
                    });
                }
            }).start();
        }
    }

    private void moveSelectionUp() {
        if (playlist.size() < 2 || selected.isEmpty()) return;
        HashSet<Integer> newSelected = new HashSet<>();
        for (int i = 0; i < playlist.size(); i++) {
            if (i == 0 && selected.contains(i)) {
                // first item is selected - don't move, but keep the selection tracked
                newSelected.add(i);
            } else if (selected.contains(i)) {
                if (!newSelected.contains(i - 1)) {
                    // this item is selected and can be moved up
                    Collections.swap(playlist, i, i - 1);
                    newSelected.add(i - 1);
                } else {
                    // this item is selected but cannot be moved up
                    newSelected.add(i);
                }
            }
        }
        selected.clear();
        selected.addAll(newSelected);
    }

    private void moveSelectionDown() {
        if (playlist.size() < 2 || selected.isEmpty()) return;
        HashSet<Integer> newSelected = new HashSet<>();
        for (int i = playlist.size() - 1; i >= 0; i--) {
            if (i == playlist.size() - 1 && selected.contains(i)) {
                // last item is selected - don't move, but keep the selection tracked
                newSelected.add(i);
            } else if (selected.contains(i)) {
                if (!newSelected.contains(i + 1)) {
                    // this item is selected and can be moved down
                    Collections.swap(playlist, i, i + 1);
                    newSelected.add(i + 1);
                } else {
                    // this item is selected but cannot be moved down
                    newSelected.add(i);
                }
            }
        }
        selected.clear();
        selected.addAll(newSelected);
    }

    private void updateControlBar() {
        if (selected.isEmpty()) {
            binding.ivPlaylistAdd.setVisibility(View.VISIBLE);
            binding.ivPlaylistClear.setVisibility(View.VISIBLE);
            binding.ivPlaylistLoad.setVisibility(View.VISIBLE);
            binding.ivPlaylistSave.setVisibility(View.VISIBLE);
            binding.ivPlaylistSelRemove.setVisibility(View.GONE);
            binding.ivPlaylistSelDeselectAll.setVisibility(View.GONE);
            binding.ivPlaylistSelMoveUp.setVisibility(View.GONE);
            binding.ivPlaylistSelMoveDown.setVisibility(View.GONE);
        } else {
            binding.ivPlaylistAdd.setVisibility(View.GONE);
            binding.ivPlaylistClear.setVisibility(View.GONE);
            binding.ivPlaylistLoad.setVisibility(View.GONE);
            binding.ivPlaylistSave.setVisibility(View.GONE);
            binding.ivPlaylistSelRemove.setVisibility(View.VISIBLE);
            binding.ivPlaylistSelDeselectAll.setVisibility(View.VISIBLE);
            binding.ivPlaylistSelMoveUp.setVisibility(View.VISIBLE);
            binding.ivPlaylistSelMoveDown.setVisibility(View.VISIBLE);
        }
    }

    public class PlaylistItemClickListener implements PlaylistItemAdapter.OnItemClickListener {
        @Override
        public void onItemClick(int position) {
            var isItemSelected = selected.contains(position);
            if (!isItemSelected) selected.add(position);
            else selected.remove(position);
            updateControlBar();
            adapter.notifyItemChanged(position);
        }
    }

    public class PlaylistItemLongClickListener implements PlaylistItemAdapter.OnItemLongClickListener {
        @Override
        public void onItemLongClick(int position) {
            var ab = new AlertDialog.Builder(PlaylistActivity.this)
                    .setTitle(String.format(getString(R.string.playlist_confirm_ok_and_start_from_title), playlist.get(position).title))
                    .setMessage(R.string.playlist_confirm_ok_and_start_from_message)
                    .setPositiveButton(R.string.common_yes, (d, i) -> {
                        Intent intent = new Intent();
                        intent.putParcelableArrayListExtra(MainActivity.EXTRA_NEW_PLAYLIST, playlist);
                        intent.putExtra(MainActivity.EXTRA_NEW_PLAYLIST_NAME, playlistName);
                        intent.putExtra(MainActivity.EXTRA_JUMP_POSITION_INDEX, position);
                        setResult(RESULT_OK, intent);
                        finish();
                    })
                    .setNegativeButton(R.string.common_no, null)
                    .create();
            ab.show();
        }
    }
}