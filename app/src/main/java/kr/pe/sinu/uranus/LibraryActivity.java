// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Base64;
import android.util.Log;
import android.view.View;
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
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import kr.pe.sinu.uranus.databinding.ActivityLibraryBinding;

public class LibraryActivity extends AppCompatActivity {
    public static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList("mp3", "m4a", "flac", "wav", "ogg"));

    ActivityLibraryBinding binding;

    ArrayList<LibraryItem> displayList;
    LibraryItemAdapter adapter;
    LibraryItemClickListener itemClickListener;
    LibraryItemRemoveClickListener itemRemoveClickListener;

    ActivityResultLauncher<Uri> addFolderResult;
    ArrayList<LibraryFolder> folders;
    Uri currentTreeUri = null;
    Stack<Uri> subtreeUris;

    ArrayList<Uri> selectedFiles;
    MediaMetadataCache mmCache;

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityLibraryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ll_library), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        subtreeUris = new Stack<>();
        selectedFiles = new ArrayList<>();
        mmCache = MediaMetadataCache.getInstance();

        folders = new ArrayList<>();
        loadFolders();

        addFolderResult = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri == null) return;

                // get folder name
                var df = DocumentFile.fromTreeUri(LibraryActivity.this, uri);
                if (df == null) return;
                String dirName = df.getName();

                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                folders.add(new LibraryFolder(dirName, uri));
                saveFolders();
                refreshDisplayList();
            }
        );

        displayList = new ArrayList<>();
        itemClickListener = new LibraryItemClickListener();
        itemRemoveClickListener = new LibraryItemRemoveClickListener();
        adapter = new LibraryItemAdapter(displayList, itemClickListener, itemRemoveClickListener);
        if (binding.rvLibraryList.getItemAnimator() != null) {
            ((SimpleItemAnimator)binding.rvLibraryList.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        binding.rvLibraryList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLibraryList.setAdapter(adapter);

        binding.ivLibrarySelectAll.setOnClickListener(v -> {
            if (currentTreeUri == null) return;

            // add non-selected items while checking if everything here is already selected
            boolean isEverythingSelected = true;
            for (var d : displayList) {
                if (d.type == LibraryItem.TYPE_FOLDER_MUSIC) {
                    Uri uri = Uri.parse(d.target);
                    if (!selectedFiles.contains(uri)) {
                        isEverythingSelected = false;
                        d.selected = true;
                        selectedFiles.add(uri);
                    }
                }
            }

            // if everything was already selected, deselect everything
            if (isEverythingSelected) {
                for (var d : displayList) {
                    if (d.type == LibraryItem.TYPE_FOLDER_MUSIC) {
                        Uri uri = Uri.parse(d.target);
                        d.selected = false;
                        selectedFiles.remove(uri);
                    }
                }
            }

            updateSelectCountText();
            adapter.notifyDataSetChanged();
        });
        binding.ivLibraryOk.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.putParcelableArrayListExtra(PlaylistActivity.EXTRA_URIS_TO_ADD, selectedFiles);
            setResult(RESULT_OK, intent);
            finish();
        });
        binding.ivLibraryCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        for (View v : new View[] {
                binding.ivLibrarySelectAll,
                binding.ivLibraryOk,
                binding.ivLibraryCancel,
        }) {
            TooltipCompat.setTooltipText(v, v.getContentDescription());
        }

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentTreeUri == null) {
                    // on root, finish activity
                    finish();
                    return;
                }
                if (subtreeUris.empty()) {
                    // can't go up, go back to root menu
                    currentTreeUri = null;
                    refreshDisplayList();
                } else {
                    // can go up
                    currentTreeUri = subtreeUris.pop();
                    refreshDisplayList();
                }
            }
        });

        refreshDisplayList();
    }

    // folder.json structure
    // - array of
    //   - name: folder name
    //   - uri: (base64 encoded (uri as string)) as hex string
    private void loadFolders() {
        var foldersFile = new File(getFilesDir(), "folders.json");
        if (foldersFile.exists()) {
            try {
                String foldersFileRaw = Util.readString(foldersFile);
                JSONArray foldersArray = new JSONArray(foldersFileRaw);
                var grantedUris = getContentResolver().getPersistedUriPermissions();
                boolean someFolderRevoked = false;
                for (int i = 0; i < foldersArray.length(); i++) {
                    JSONObject folderJson = foldersArray.getJSONObject(i);
                    String folderName = folderJson.getString("name");
                    String folderUriEnc = folderJson.getString("uri");
                    Uri folderUri = Uri.parse(new String(Base64.decode(folderUriEnc, Base64.DEFAULT), StandardCharsets.UTF_8));

                    // check permission
                    boolean granted = false;
                    for (var grantedUri : grantedUris) {
                        if (grantedUri.getUri().equals(folderUri) && grantedUri.isReadPermission()) {
                            granted = true;
                            break;
                        }
                    }
                    if (!granted) {
                        someFolderRevoked = true;
                        continue;
                    }

                    // check existence
                    DocumentFile df = DocumentFile.fromTreeUri(this, folderUri);
                    if (df == null || !df.exists() || !df.isDirectory()) {
                        someFolderRevoked = true;
                        continue;
                    }

                    folders.add(new LibraryFolder(folderName, folderUri));
                }
                if (someFolderRevoked) {
                    Toast.makeText(this, R.string.library_error_access_to_folder_revoked, Toast.LENGTH_LONG).show();
                    saveFolders();
                }
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.library_error_folder_list_corrupted, Toast.LENGTH_LONG).show();
                folders.clear();
                saveFolders();
            }
        }
    }

    private void saveFolders() {
        JSONArray foldersArray = new JSONArray();
        for (var u : folders) {
            JSONObject folderJson = new JSONObject();
            try {
                folderJson.put("name", u.name);
                folderJson.put("uri", Base64.encodeToString(u.uri.toString().getBytes(StandardCharsets.UTF_8), Base64.DEFAULT));
            } catch (JSONException ignored) {
                continue;
            }
            foldersArray.put(folderJson);
        }
        var foldersFile = new File(getFilesDir(), "folders.json");
        Util.writeString(foldersFile, foldersArray.toString());
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refreshDisplayList() {
        displayList.clear();
        if (currentTreeUri == null) {
            for (var f : folders) {
                displayList.add(LibraryItem.asRootFolder(f.name, f.uri.toString()));
            }
            displayList.add(LibraryItem.asRootAddFolder());
            adapter.notifyDataSetChanged();
        } else {
            displayList.add(LibraryItem.asFolderUp());

            new Thread(() -> {
                boolean mmCacheMissed = false;

                // i really don't understand how this thing works...
                Uri childUri = null;
                if (subtreeUris.empty()) {
                    Uri rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(currentTreeUri, DocumentsContract.getTreeDocumentId(currentTreeUri));
                    childUri = DocumentsContract.buildChildDocumentsUriUsingTree(currentTreeUri, DocumentsContract.getDocumentId(rootDocumentUri));
                } else {
                    childUri = DocumentsContract.buildChildDocumentsUriUsingTree(currentTreeUri, DocumentsContract.getDocumentId(subtreeUris.peek()));
                }

                try (var cursor = getContentResolver().query(childUri, new String[] {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                }, null, null, null)) {
                    if (cursor == null) {
                        Log.e("Uranus", "display list fetch: cursor is null?");
                        return;
                    }

                    while (cursor.moveToNext()) {
                        String docId = cursor.getString(0);
                        String name = cursor.getString(1);
                        String mime = cursor.getString(2);
                        long size = cursor.getLong(3);
                        long lastModified = cursor.getLong(4);
                        boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);

                        if (isDir && ".thumbnails".equals(name)) continue;

                        if (isDir) {
                            Uri subtreeUri = DocumentsContract.buildDocumentUriUsingTree(currentTreeUri, docId);
                            displayList.add(LibraryItem.asFolderFolder(name, subtreeUri.toString()));
                        } else {
                            if (ALLOWED_EXTENSIONS.contains(Util.getFileExt(name))) {
                                Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(currentTreeUri, docId);
                                if (!mmCacheMissed && !mmCache.isMediaMetadataCached(fileUri, size, lastModified)) {
                                    mmCacheMissed = true;
                                    runOnUiThread(() -> {
                                        binding.rvLibraryList.setVisibility(View.GONE);
                                        binding.llLibraryPbrContainer.setVisibility(View.VISIBLE);
                                    });
                                }
                                var mm = mmCache.getMediaMetadata(LibraryActivity.this, name, fileUri, size, lastModified);
                                var li = LibraryItem.asFolderMusic(null, mm.title, name, fileUri.toString());
                                if (selectedFiles.contains(fileUri)) li.selected = true;
                                displayList.add(li);
                            }
                        }
                    }
                }

                runOnUiThread(() -> {
                    binding.rvLibraryList.setVisibility(View.VISIBLE);
                    binding.llLibraryPbrContainer.setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                });
            }).start();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.anim_fade_enter, R.anim.anim_slide_enter);
    }

    private class LibraryFolder {
        public String name;
        public Uri uri;

        public LibraryFolder(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    public class LibraryItemClickListener implements LibraryItemAdapter.OnItemClickListener {
        @Override
        public void onItemClick(int position) {
            var item = displayList.get(position);
            switch (item.type) {
                case LibraryItem.TYPE_ROOT_ADD_FOLDER:
                    addFolderResult.launch(null);
                    break;
                case LibraryItem.TYPE_ROOT_FOLDER:
                    currentTreeUri = Uri.parse(item.target);
                    refreshDisplayList();
                    break;
                case LibraryItem.TYPE_FOLDER_UP:
                    if (subtreeUris.empty()) {
                        // can't go up, go back to root menu
                        currentTreeUri = null;
                        refreshDisplayList();
                    } else {
                        // can go up
                        currentTreeUri = subtreeUris.pop();
                        refreshDisplayList();
                    }
                    break;
                case LibraryItem.TYPE_FOLDER_MUSIC:
                    Uri targetFile = Uri.parse(item.target);
                    var indexOfIt = selectedFiles.indexOf(targetFile);
                    if (indexOfIt == -1) {
                        selectedFiles.add(targetFile);
                        item.selected = true;
                    } else {
                        selectedFiles.remove(indexOfIt);
                        item.selected = false;
                    }
                    updateSelectCountText();
                    adapter.notifyItemChanged(position);
                    break;
                case LibraryItem.TYPE_FOLDER_FOLDER:
                    Uri targetSubtreeUri = Uri.parse(item.target);
                    subtreeUris.push(targetSubtreeUri);
                    currentTreeUri = targetSubtreeUri;
                    refreshDisplayList();
                    break;
            }
        }
    }

    private void updateSelectCountText() {
        binding.tvLibrarySelected.setText(String.format(getResources().getQuantityText(R.plurals.library_selected_count, selectedFiles.size()).toString(), selectedFiles.size()));
    }

    public class LibraryItemRemoveClickListener implements LibraryItemAdapter.OnItemRemoveClickListener {
        @Override
        public void onItemRemoveClick(int position) {
            var item = displayList.get(position);
            if (item.type == LibraryItem.TYPE_ROOT_FOLDER) {
                AlertDialog ab = new AlertDialog.Builder(LibraryActivity.this)
                        .setTitle(String.format(getString(R.string.library_warning_remove_folder_title), item.title))
                        .setMessage(R.string.library_warning_remove_folder_message)
                        .setPositiveButton(R.string.common_yes, (d, v) -> {
                            getContentResolver().releasePersistableUriPermission(Uri.parse(item.target), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            folders.remove(position);
                            saveFolders();
                            refreshDisplayList();
                        })
                        .setNegativeButton(R.string.common_no, null)
                        .create();
                ab.show();
            }
        }
    }
}