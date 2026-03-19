// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
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
import kr.pe.sinu.uranus.databinding.PopupMoreBinding;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_NEW_PLAYLIST = "new_playlist";
    public static final String EXTRA_NEW_PLAYLIST_NAME = "new_playlist_name";
    public static final String EXTRA_JUMP_POSITION_INDEX = "jump_position_index";

    private static final int UPDATE_MPS_STATUS_INTERVAL_MS = 450;

    private ActivityMainBinding binding;

    private MediaPlaybackService mps;
    private boolean bound = false;

    private Handler handler;
    private Runnable rUpdateMpsState;
    private Bitmap currentCover = null;

    PopupMoreBinding mwBinding = null;
    private PopupWindow pwMoreWindow = null;
    private BroadcastReceiver mwTimerUpdater = null;

    private boolean isSeeking = false;
    private boolean wasPlayingOnBeginSeek = false;

    private ArrayList<PlaylistItem> pendingPlaylistUpdate = null;
    private String pendingPlaylistNameUpdate = null;
    private int pendingJumpPosIndex = -1;

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
            if (pwMoreWindow.isShowing()) pwMoreWindow.dismiss();
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
                if (pendingJumpPosIndex != -1) {
                    mps.jumpTo(pendingJumpPosIndex);
                    // user wants to play, start playing now
                    mps.play();
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
        var initialRepeatMode = sp.getInt("repeat_mode", MediaPlaybackService.REPEAT_MODE_NO_REPEAT);
        updateRepeatModeIcon(initialRepeatMode);

        playlistResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), (result) -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                var uncastedNewPlaylist = result.getData().getParcelableArrayListExtra(EXTRA_NEW_PLAYLIST);
                var newPlaylistName = result.getData().getStringExtra(EXTRA_NEW_PLAYLIST_NAME);
                if (newPlaylistName == null) newPlaylistName = getString(R.string.common_default_playlist_name);
                var jumpPosIndex = result.getData().getIntExtra(EXTRA_JUMP_POSITION_INDEX, -1);
                if (uncastedNewPlaylist != null) {
                    var newPlaylist = new ArrayList<PlaylistItem>(uncastedNewPlaylist.size());
                    for (var pi : uncastedNewPlaylist) newPlaylist.add((PlaylistItem) pi);
                    // if service is not bound right now, keep the changes as pending - pending changes will be applied when the service is bound
                    if (bound) {
                        mps.setPlaylist(newPlaylist);
                        mps.setCurrentPlaylistName(newPlaylistName);
                        if (jumpPosIndex != -1) {
                            mps.jumpTo(jumpPosIndex);
                            // user wants to play, start playing now
                            mps.play();
                        }
                    } else {
                        pendingPlaylistUpdate = newPlaylist;
                        pendingPlaylistNameUpdate = newPlaylistName;
                        pendingJumpPosIndex = jumpPosIndex;
                    }
                    if (newPlaylist.isEmpty()) {
                        binding.ivMainCover.setImageResource(R.drawable.cover_placeholder);
                        if (currentCover != null && !currentCover.isRecycled()) {
                            currentCover.recycle();
                        }
                        binding.tvMainTitle.setText(R.string.main_placeholder_title);
                        binding.tvMainSubtitle.setText(R.string.main_placeholder_subtitle);
                        binding.tvMainTimeCurrent.setText(R.string.main_placeholder_time);
                        binding.tvMainTimeTotal.setText(R.string.main_placeholder_time);
                        binding.sbMainSeekbar.setProgress(0);
                        binding.sbMainSeekbar.setMax(1);
                    }
                }
            }
        });

        int mwWidthInPx = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320, getResources().getDisplayMetrics());
        int mwElevationInPx = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());

        mwBinding = PopupMoreBinding.inflate(getLayoutInflater(), binding.getRoot(), false);
        pwMoreWindow = new PopupWindow(mwBinding.getRoot(), ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pwMoreWindow.setWidth(mwWidthInPx);
        pwMoreWindow.setElevation(mwElevationInPx);

        mwBinding.ivMoreVolMul.setOnClickListener(v -> {
            mwBinding.tvMoreTitle.setVisibility(View.VISIBLE);
            mwBinding.tvMoreTitle.setText(R.string.main_more_vol_mul);
            mwBinding.llMoreVolMul.setVisibility(View.VISIBLE);
            mwBinding.llMoreTimer.setVisibility(View.GONE);

            int volMulValue = 100;
            if (bound) volMulValue = mps.getVolumeMultiplier();
            else volMulValue = sp.getInt("vol_mul", 100);

            mwBinding.sbMoreVolMul.setProgress(volMulValue);
            mwBinding.tvMoreVolMulValue.setText(String.format(getString(R.string.main_more_vol_mul_value), volMulValue));

            mwBinding.getRoot().post(this::adjustPopupPosition);
        });
        mwBinding.ivMoreTimer.setOnClickListener(v -> {
            mwBinding.tvMoreTitle.setVisibility(View.VISIBLE);
            mwBinding.tvMoreTitle.setText(R.string.main_more_timer);
            mwBinding.llMoreVolMul.setVisibility(View.GONE);
            mwBinding.llMoreTimer.setVisibility(View.VISIBLE);

            updateSleepTimerText();

            mwBinding.getRoot().post(this::adjustPopupPosition);
        });
        mwBinding.sbMoreVolMul.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mwBinding.tvMoreVolMulValue.setText(String.format(getString(R.string.main_more_vol_mul_value), progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (bound) {
                    mps.setVolumeMultiplier(mwBinding.sbMoreVolMul.getProgress(), true);
                } else {
                    sp.edit().putInt("vol_mul", mwBinding.sbMoreVolMul.getProgress()).apply();
                }
            }
        });
        mwBinding.btnMoreTimerAdd5min.setOnClickListener(v -> {
            addSleepTimerTime(5);
            updateSleepTimerText();
        });
        mwBinding.btnMoreTimerAdd15min.setOnClickListener(v -> {
            addSleepTimerTime(15);
            updateSleepTimerText();
        });
        mwBinding.btnMoreTimerAdd30min.setOnClickListener(v -> {
            addSleepTimerTime(30);
            updateSleepTimerText();
        });
        mwBinding.btnMoreTimerAdd60min.setOnClickListener(v -> {
            addSleepTimerTime(60);
            updateSleepTimerText();
        });
        mwBinding.btnMoreTimerCancel.setOnClickListener(v -> {
            if (bound) mps.setSleepTimerTargetMinutes(0);
            updateSleepTimerText();
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
                mps.setRepeatMode(repeatMode, true);
                updateRepeatModeIcon(repeatMode);
            }
        });
        binding.ivMainPlayPause.setOnClickListener(v -> {
            if (bound) {
                if (mps.isPlaying()) mps.pause();
                else if (!mps.play()) Toast.makeText(MainActivity.this, R.string.main_error_playlist_empty, Toast.LENGTH_SHORT).show();
            }
        });
        binding.ivMainSettings.setOnClickListener(v -> {
            Toast.makeText(this, R.string.main_no_settings_yet, Toast.LENGTH_SHORT).show();
        });
        binding.ivMainMore.setOnClickListener(v -> {
            mwBinding.tvMoreTitle.setVisibility(View.GONE);
            mwBinding.llMoreVolMul.setVisibility(View.GONE);
            mwBinding.llMoreTimer.setVisibility(View.GONE);

            mwBinding.getRoot().measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int popupHeight = mwBinding.getRoot().getMeasuredHeight();
            pwMoreWindow.showAsDropDown(binding.ivMainMore, 0, -(popupHeight + binding.ivMainMore.getHeight()));
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

        mwTimerUpdater = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!pwMoreWindow.isShowing() || mwBinding.llMoreTimer.getVisibility() != View.VISIBLE) return;
                updateSleepTimerText();
            }
        };

        for (View v : new View[] {
                binding.ivMainPlaylist,
                binding.ivMainMore,
                binding.ivMainPrevious,
                binding.ivMainRepeat,
                binding.ivMainPlayPause,
                binding.ivMainSettings,
                binding.ivMainNext,
                mwBinding.ivMoreVolMul,
                mwBinding.ivMoreTimer,
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
            return;
        }
        String filename;
        long size;
        long lastModified;
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
                    // do not display album title if album title is unknown
                    binding.tvMainSubtitle.setText(artist);
                } else {
                    binding.tvMainSubtitle.setText(String.format(getString(R.string.main_subtitle_aa_format), artist, currentMeta.album));
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
            // do not display album title if album title is unknown
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
            case MediaPlaybackService.REPEAT_MODE_NO_REPEAT:
                binding.ivMainRepeat.setImageResource(R.drawable.ic_line_end);
                binding.ivMainRepeat.setContentDescription(getString(R.string.acc_repeat_none));
                break;
            case MediaPlaybackService.REPEAT_MODE_REPEAT_ALL:
                binding.ivMainRepeat.setImageResource(R.drawable.ic_repeat);
                binding.ivMainRepeat.setContentDescription(getString(R.string.acc_repeat_all));
                break;
            case MediaPlaybackService.REPEAT_MODE_REPEAT_ONE:
                binding.ivMainRepeat.setImageResource(R.drawable.ic_repeat_one);
                binding.ivMainRepeat.setContentDescription(getString(R.string.acc_repeat_one));
                break;
            case MediaPlaybackService.REPEAT_MODE_SHUFFLE:
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

    private void adjustPopupPosition() {
        // FIXME: now it kinda works but is super janky, find proper way to do this
        mwBinding.getRoot().measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupHeight = mwBinding.getRoot().getMeasuredHeight();
        int[] popupLocation = new int[2];
        mwBinding.getRoot().getLocationOnScreen(popupLocation);
        int[] buttonLocation = new int[2];
        binding.ivMainMore.getLocationOnScreen(buttonLocation);
        int newX = popupLocation[0];
        int newY = buttonLocation[1] - popupHeight;
        pwMoreWindow.update(newX, newY, -1, -1);
    }

    private void addSleepTimerTime(int minutes) {
        if (bound) {
            var nowMinutes = System.currentTimeMillis() / 60000L;
            var currentTimerTargetMinutes = mps.getSleepTimerTargetMinutes();
            if (currentTimerTargetMinutes == 0) currentTimerTargetMinutes = nowMinutes;
            var newTimerTargetMinutes = currentTimerTargetMinutes + minutes;
            if (newTimerTargetMinutes - nowMinutes > 24 * 60) {
                Toast.makeText(this, R.string.main_more_timer_error_too_long, Toast.LENGTH_SHORT).show();
            } else {
                mps.setSleepTimerTargetMinutes(newTimerTargetMinutes);
            }
        }
    }

    private void updateSleepTimerText() {
        if (bound) {
            var currentTimerTargetMinutes = mps.getSleepTimerTargetMinutes();
            if (currentTimerTargetMinutes == 0) {
                mwBinding.tvMoreTimerValue.setText(R.string.main_more_timer_desc_off);
            } else {
                int minutesLeft = (int)(currentTimerTargetMinutes - (System.currentTimeMillis() / 60000L));
                if (minutesLeft >= 60) {
                    int hoursLeft = minutesLeft / 60;
                    minutesLeft = minutesLeft % 60;
                    mwBinding.tvMoreTimerValue.setText(
                            String.format(
                                    getString(R.string.main_more_timer_desc_on_template),
                                    String.format(
                                            getString(R.string.main_more_timer_desc_on_hm),
                                            hoursLeft,
                                            minutesLeft
                                    )
                            )
                    );
                } else {
                    mwBinding.tvMoreTimerValue.setText(
                            String.format(
                                    getString(R.string.main_more_timer_desc_on_template),
                                    String.format(
                                            getString(R.string.main_more_timer_desc_on_m),
                                            minutesLeft
                                    )
                            )
                    );
                }
            }
        } else {
            mwBinding.tvMoreTimerValue.setText(R.string.main_more_timer_desc_off);
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
        try {
            startService(intent);
            bindService(intent, connection, BIND_AUTO_CREATE);
        } catch (Exception ignored) {
            finish();
            return;
        }
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

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mwTimerUpdater, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mwTimerUpdater);
    }
}