// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.Player;

import java.io.File;
import java.util.ArrayList;

import kr.pe.sinu.uranus.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_NEW_PLAYLIST = "new_playlist";
    public static final String EXTRA_NEW_PLAYLIST_NAME = "new_playlist_name";

    private static final int UPDATE_MPS_STATUS_INTERVAL_MS = 450;

    private ActivityMainBinding binding;

    private MediaPlaybackService mps;
    private boolean bound = false;

    private Handler handler;
    private Runnable rUpdateMpsState;
    private Bitmap currentCover = null;

    private boolean isSeeking = false;
    private boolean wasPlayingOnBeginSeek = false;

    private ArrayList<PlaylistItem> pendingPlaylistUpdate = null;
    private String pendingPlaylistNameUpdate = null;

    private ActivityResultLauncher<Intent> playlistResult;

    public MpsEventListener mpsEventListener;
    public class MpsEventListener implements MediaPlaybackService.MpsEventListener {
        @Override
        public void onMediaChanged() {
            updateMpsState();
            updateMediaMetadata();
        }

        @Override
        public void onMpsExiting() {
            finish();
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaPlaybackService.LocalBinder binder = (MediaPlaybackService.LocalBinder)service;
            mps = binder.getService();
            mps.setEventListener(mpsEventListener);
            bound = true;

            if (pendingPlaylistUpdate != null) {
                mps.setPlaylist(pendingPlaylistUpdate);
                pendingPlaylistUpdate = null;
                if (pendingPlaylistNameUpdate != null) {
                    mps.setCurrentPlaylistName(pendingPlaylistNameUpdate);
                    pendingPlaylistNameUpdate = null;
                }
            }

            updateRepeatMode();
            updateMediaMetadata();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mps = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ll_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SharedPreferences sp = getSharedPreferences("kr.pe.sinu.uranus.prefs", MODE_PRIVATE);
        var initialRepeatMode = sp.getInt("repeat_mode", MediaPlaybackService.REPEATMODE_NO_REPEAT);
        updateRepeatModeIcon(initialRepeatMode);

        playlistResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), (result) -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                var uncastedNewPlaylist = result.getData().getParcelableArrayListExtra(EXTRA_NEW_PLAYLIST);
                var newPlaylistName = result.getData().getStringExtra(EXTRA_NEW_PLAYLIST_NAME);
                if (newPlaylistName == null) newPlaylistName = getString(R.string.common_default_playlist_name);
                if (uncastedNewPlaylist != null) {
                    var newPlaylist = new ArrayList<PlaylistItem>(uncastedNewPlaylist.size());
                    for (var pi : uncastedNewPlaylist) newPlaylist.add((PlaylistItem) pi);
                    // probably not bound on here but better safe than sorry
                    if (bound) {
                        mps.setPlaylist(newPlaylist);
                        mps.setCurrentPlaylistName(newPlaylistName);
                    } else {
                        pendingPlaylistUpdate = newPlaylist;
                        pendingPlaylistNameUpdate = newPlaylistName;
                    }
                }
            }
        });

        binding.ivMainPlaylist.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlaylistActivity.class);
            if (bound) {
                intent.putParcelableArrayListExtra(PlaylistActivity.EXTRA_PLAYLIST, mps.getPlaylist());
                intent.putExtra(PlaylistActivity.EXTRA_PLAYLIST_NAME, mps.getCurrentPlaylistName());
            }
            playlistResult.launch(intent);
            overridePendingTransition(R.anim.anim_slide_exit, R.anim.anim_fade_exit);
        });
        binding.ivMainPrevious.setOnClickListener(v -> {
            if (bound) mps.goPrevious();
        });
        binding.ivMainNext.setOnClickListener(v -> {
            if (bound) mps.goNext();
        });
        binding.ivMainRepeat.setOnClickListener(v -> {
            if (bound) {
                var repeatMode = mps.getRepeatMode();
                repeatMode += 1;
                if (repeatMode == 4) repeatMode = 0;
                mps.setRepeatMode(repeatMode);
                updateRepeatModeIcon(repeatMode);
            }
        });
        binding.ivMainPlayPause.setOnClickListener(v -> {
            if (bound) {
                if (mps.isPlaying()) mps.pause();
                else mps.play();
            }
        });
        binding.ivMainSettings.setOnClickListener(v -> {
            Toast.makeText(this, R.string.main_no_settings_yet, Toast.LENGTH_SHORT).show();
        });
        binding.ivMainMore.setOnClickListener(v -> {
            if (bound) mps.emergencyEscape();
            finish();
        });

        binding.sbMainSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (!bound || (mps.getPlaybackState() != Player.STATE_READY)) return;
                beginSeek();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!bound || (mps.getPlaybackState() != Player.STATE_READY)) return;
                endSeek();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isSeeking) {
                    binding.tvMainTimeCurrent.setText(Util.toTimestamp(progress / 1000));
                }
            }
        });

        for (View v : new View[] {
                binding.ivMainPlaylist,
                binding.ivMainMore,
                binding.ivMainPrevious,
                binding.ivMainRepeat,
                binding.ivMainPlayPause,
                binding.ivMainSettings,
                binding.ivMainNext,
        }) {
            TooltipCompat.setTooltipText(v, v.getContentDescription());
        }

        mpsEventListener = new MpsEventListener();

        handler = new Handler(Looper.getMainLooper());
        rUpdateMpsState = () -> {
            if (bound) updateMpsState();
            handler.postDelayed(rUpdateMpsState, UPDATE_MPS_STATUS_INTERVAL_MS);
        };
    }

    private void updateMpsState() {
        var mpsState = mps.getMpsState();
        if (mpsState != null) {
            if (!isSeeking) {
                binding.tvMainTimeTotal.setText(Util.toTimestamp(mpsState.duration / 1000));
                binding.tvMainTimeCurrent.setText(Util.toTimestamp(mpsState.position / 1000));
                binding.sbMainSeekbar.setMax((int) mpsState.duration);
                binding.sbMainSeekbar.setProgress((int) mpsState.position);
            }
            binding.ivMainPlayPause.setImageResource(mpsState.playButtonIsPlay ? R.drawable.ic_pause : R.drawable.ic_play);
        } else {
            if (!isSeeking) {
                binding.tvMainTimeTotal.setText(R.string.main_placeholder_time);
                binding.tvMainTimeCurrent.setText(R.string.main_placeholder_time);
                if (mps.getPlaybackState() != Player.STATE_READY && mps.getPlaybackState() != Player.STATE_BUFFERING) {
                    binding.sbMainSeekbar.setMax(1);
                    binding.sbMainSeekbar.setProgress(0);
                }
            }
            binding.ivMainPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void updateMediaMetadata() {
        Log.d("Uranus", "activity is updating metadata...");
        var currentMeta = mps.getMpsCurrentMeta();
        if (currentMeta == null) {
            Log.w("Uranus", "mps meta is null, will be ignored");
//            binding.tvMainTitle.setText(R.string.main_placeholder_title);
//            binding.tvMainSubtitle.setText(R.string.main_placeholder_subtitle);
//            if (currentCover != null && !currentCover.isRecycled()) {
//                binding.ivMainCover.setImageBitmap(null);
//                currentCover.recycle();
//            }
//            binding.ivMainCover.setImageResource(R.drawable.cover_placeholder);
            return;
        }
        String filename = "";
        long size = 0;
        long lastModified = 0;
        try (var cursor = getContentResolver().query(currentMeta.uri, new String[] {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        }, null, null, null)) {
            if (cursor == null) {
                // fallback to service provided meta
                Log.w("Uranus", "cannot retrieve file info - falling back to service provided meta");
                binding.tvMainTitle.setText(currentMeta.title);
                var artist = currentMeta.artist;
                if (artist.equals("??unk")) artist = getString(R.string.main_unknown_artist);
                var album = currentMeta.album;
                if (album.equals("??unk")) {
                    // do not display album title if unknown
                    binding.tvMainSubtitle.setText(currentMeta.artist);
                } else {
                    binding.tvMainSubtitle.setText(String.format(getString(R.string.main_subtitle_aa_format), currentMeta.artist, currentMeta.album));
                }
                binding.ivMainCover.setImageResource(R.drawable.cover_placeholder);
                return;
            }
            cursor.moveToFirst();
            filename = cursor.getString(0);
            size = cursor.getLong(1);
            lastModified = cursor.getLong(2);
        }
        var dotPos = filename.lastIndexOf('.');
        if (dotPos > 0) {
            var ext = filename.substring(dotPos + 1);
            for (var allowedExt : LibraryActivity.ALLOWED_EXTENSIONS) {
                if (ext.equals(allowedExt)) {
                    filename = filename.substring(0, dotPos);
                    break;
                }
            }
        }
        var mmc = MediaMetadataCache.getInstance();
        var mm = mmc.getMediaMetadata(MainActivity.this, filename, currentMeta.uri, size, lastModified);
        String artist = mm.artist;
        if (artist.equals("??unk")) artist = getString(R.string.main_unknown_artist);
        String album = mm.album;
        binding.tvMainTitle.setText(mm.title);
        if (album.equals("??unk")) {
            // do not display album title if unknown
            binding.tvMainSubtitle.setText(artist);
        } else {
            binding.tvMainSubtitle.setText(String.format(getString(R.string.main_subtitle_aa_format), artist, album));
        }
        if (currentCover != null && !currentCover.isRecycled()) {
            binding.ivMainCover.setImageBitmap(null);
            currentCover.recycle();
        }
        if (mm.hasCover) {
            var f = new File(getCacheDir(), MediaMetadataCache.getCacheKey(currentMeta.uri, size, lastModified) + ".webp");
            if (f.exists()) {
                currentCover = BitmapFactory.decodeFile(f.getAbsolutePath());
                binding.ivMainCover.setImageBitmap(currentCover);
            } else {
                binding.ivMainCover.setImageResource(R.drawable.cover_placeholder);
            }
        } else {
            binding.ivMainCover.setImageResource(R.drawable.cover_placeholder);
        }
    }

    private void updateRepeatMode() {
        if (!bound) return;
        var repeatMode = mps.getRepeatMode();
        updateRepeatModeIcon(repeatMode);
    }

    private void updateRepeatModeIcon(int repeatMode) {
        switch (repeatMode) {
            case MediaPlaybackService.REPEATMODE_NO_REPEAT:
                binding.ivMainRepeat.setImageResource(R.drawable.ic_line_end);
                binding.ivMainRepeat.setContentDescription(getString(R.string.acc_repeat_none));
                break;
            case MediaPlaybackService.REPEATMODE_REPEAT_ALL:
                binding.ivMainRepeat.setImageResource(R.drawable.ic_repeat);
                binding.ivMainRepeat.setContentDescription(getString(R.string.acc_repeat_all));
                break;
            case MediaPlaybackService.REPEATMODE_REPEAT_ONE:
                binding.ivMainRepeat.setImageResource(R.drawable.ic_repeat_one);
                binding.ivMainRepeat.setContentDescription(getString(R.string.acc_repeat_one));
                break;
            case MediaPlaybackService.REPEATMODE_SHUFFLE:
                binding.ivMainRepeat.setImageResource(R.drawable.ic_shuffle);
                binding.ivMainRepeat.setContentDescription(getString(R.string.acc_repeat_shuffle));
                break;
        }
        TooltipCompat.setTooltipText(binding.ivMainRepeat, binding.ivMainRepeat.getContentDescription());
    }

    private void beginSeek() {
        if (isSeeking) return;
        isSeeking = true;
        if (mps.getPlaybackState() == Player.STATE_READY && mps.getPlayWhenReady()) {
            wasPlayingOnBeginSeek = true;
            mps.pause();
        } else {
            wasPlayingOnBeginSeek = false;
        }
    }

    private void endSeek() {
        if (!isSeeking) return;
        isSeeking = false;
        if (mps.getPlaybackState() == Player.STATE_READY) {
            mps.seekTo(binding.sbMainSeekbar.getProgress());
            if (wasPlayingOnBeginSeek) mps.play();
        }
    }

    @Override
    public void finish() {
        super.finish();
        if (bound) {
            mps.unloadCache();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MediaPlaybackService.class);
        startService(intent);
        bindService(intent, connection, BIND_AUTO_CREATE);
        rUpdateMpsState.run();
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(rUpdateMpsState);
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }
}