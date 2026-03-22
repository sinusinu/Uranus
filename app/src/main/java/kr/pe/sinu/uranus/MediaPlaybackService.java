// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import static android.content.Intent.ACTION_TIME_TICK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaStyleNotificationHelper;

import java.util.ArrayList;

public class MediaPlaybackService extends Service {
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "kr.pe.sinu.uranus.notification";
    private static final String ACTION_NOTIFICATION_DISMISSED = "kr.pe.sinu.uranus.NOTIFICATION_DISMISSED";

    public static final int REPEAT_MODE_NO_REPEAT = 0;
    public static final int REPEAT_MODE_REPEAT_ALL = 1;
    public static final int REPEAT_MODE_REPEAT_ONE = 2;
    public static final int REPEAT_MODE_SHUFFLE = 3;

    public class LocalBinder extends Binder {
        public MediaPlaybackService getService() {
            return MediaPlaybackService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    private MediaSession mediaSession;
    private ExoPlayer player;

    private SharedPreferences sp;
    private int repeatMode = REPEAT_MODE_NO_REPEAT;
    private int volumeMultiplier = 100;

    private long sleepTimerTargetMinutes = 0;
    private BroadcastReceiver sleepTimeCheckReceiver;

    private ArrayList<PlaylistItem> playlist;
    private String playlistName;

    private MpsEventListener eventListener;
    private NotificationDismissedReceiver dismissedReceiver;

    // FIXME: notification not updating properly when using "Play from" feature?

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(), true)
                .build();

        player.setPlayWhenReady(false);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED && repeatMode == REPEAT_MODE_NO_REPEAT) {
                    // playback ended with repeat mode set to no repeat
                    // seek back to the beginning of the last track, keep the player paused
                    // this should make pressing play button on this state play the last song again
                    player.setPlayWhenReady(false);
                    player.seekTo(playlist.size() - 1, 0);
                    player.prepare();
                }
                updateNotification();
                if (eventListener != null) eventListener.onMediaChanged();
            }

            @Override
            public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
                updateNotification();
                if (eventListener != null) eventListener.onMediaChanged();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e("Uranus", "Player error! " + error.getMessage());
            }
        });

        mediaSession = new MediaSession.Builder(this, player)
                .build();

        playlist = new ArrayList<>();
        playlistName = getString(R.string.common_default_playlist_name);

        sp = getSharedPreferences("kr.pe.sinu.uranus.prefs", MODE_PRIVATE);
        setRepeatMode(sp.getInt("repeat_mode", REPEAT_MODE_NO_REPEAT), false);
        setVolumeMultiplier(Math.clamp(sp.getInt("vol_mul", 100), 0, 100), false);

        sleepTimeCheckReceiver = new SleepTimerCheckReceiver();
        ContextCompat.registerReceiver(this, sleepTimeCheckReceiver, new IntentFilter(ACTION_TIME_TICK), ContextCompat.RECEIVER_NOT_EXPORTED);

        dismissedReceiver = new NotificationDismissedReceiver();
        ContextCompat.registerReceiver(this, dismissedReceiver, new IntentFilter(ACTION_NOTIFICATION_DISMISSED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        eventListener = null;
        if (sleepTimerTargetMinutes == 0 && playlist.isEmpty()) {
            stopSelf();
        }
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mediaSession.release();
        player.release();
        try { unregisterReceiver(dismissedReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(sleepTimeCheckReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    public ArrayList<PlaylistItem> getPlaylist() {
        return new ArrayList<>(playlist);
    }

    public void setPlaylist(ArrayList<PlaylistItem> newPlaylist) {
        boolean newPlaylistContainsCurrentSong = false;
        var currentPlayingIndex = getCurrentPlayingIndex();
        if (currentPlayingIndex != -1) {
            var currentPlayingItem = playlist.get(currentPlayingIndex);
            for (var newItems : newPlaylist) {
                if (newItems.uriSource.equals(currentPlayingItem.uriSource)) {
                    newPlaylistContainsCurrentSong = true;
                    break;
                }
            }
        }
        playlist.clear();
        if (newPlaylist.isEmpty()) {
            player.setMediaItems(new ArrayList<>(), true);
            player.stop();
            return;
        }
        playlist.addAll(newPlaylist);
        ArrayList<MediaItem> playerPlaylist = new ArrayList<>(newPlaylist.size());
        for (var pi : newPlaylist) playerPlaylist.add(MediaItem.fromUri(pi.uriSource));
        player.setMediaItems(playerPlaylist, !newPlaylistContainsCurrentSong);
    }

    public boolean play() {
        if (playlist.isEmpty()) return false;
        if (player.getPlaybackState() != Player.STATE_READY) player.prepare();
        if (!player.getPlayWhenReady()) player.setPlayWhenReady(true);
        return true;
    }

    public void pause() {
        if (!player.isPlaying() || !player.getPlayWhenReady()) return;
        player.pause();
    }

    public void goPrevious() { player.seekToPrevious(); }

    public void goNext() { player.seekToNext(); }

    public void jumpTo(int index) { player.seekTo(index, 0); }

    public void setCurrentPlaylistName(String newName) { playlistName = newName; }

    public String getCurrentPlaylistName() { return playlistName; }

    public boolean isPlaying() { return player.isPlaying(); }

    public int getPlaybackState() { return player.getPlaybackState(); }

    public boolean getPlayWhenReady() { return player.getPlayWhenReady(); }

    public void seekTo(int position) { player.seekTo(position); }

    public long getCurrentPosition() { return player.getCurrentPosition(); }

    public long getDuration() { return player.getDuration(); }

    public int getCurrentPlayingIndex() {
        if (player.getPlaybackState() != Player.STATE_READY && player.getPlaybackState() != Player.STATE_BUFFERING) return -1;
        return player.getCurrentMediaItemIndex();
    }

    public int getRepeatMode() { return repeatMode; }

    public void setRepeatMode(int repeatMode, boolean save) {
        this.repeatMode = repeatMode;
        if (save) sp.edit().putInt("repeat_mode", repeatMode).apply();

        switch (repeatMode) {
            case REPEAT_MODE_NO_REPEAT:
                player.setRepeatMode(Player.REPEAT_MODE_OFF);
                player.setShuffleModeEnabled(false);
                break;
            case REPEAT_MODE_REPEAT_ALL:
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                player.setShuffleModeEnabled(false);
                break;
            case REPEAT_MODE_REPEAT_ONE:
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
                player.setShuffleModeEnabled(false);
                break;
            case REPEAT_MODE_SHUFFLE:
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                player.setShuffleModeEnabled(true);
                break;
        }
    }

    public int getVolumeMultiplier() {
        return volumeMultiplier;
    }

    public void setVolumeMultiplier(int percent, boolean save) {
        volumeMultiplier = percent;
        player.setVolume(volumeMultiplier / 100f);
        if (save) sp.edit().putInt("vol_mul", percent).apply();
    }

    public long getSleepTimerTargetMinutes() {
        return sleepTimerTargetMinutes;
    }

    public void setSleepTimerTargetMinutes(long timestamp) {
        sleepTimerTargetMinutes = timestamp;
    }

    // TODO: remove this function on release
    public void emergencyEscape() { stopSelf(); }

    public MpsMeta getMpsCurrentMeta() {
        if (player.getPlaybackState() != Player.STATE_READY) return null;
        var currentPlayingIndex = getCurrentPlayingIndex();
        var nowPlayingMetadata = player.getMediaMetadata();
        String title = (nowPlayingMetadata.title != null ? nowPlayingMetadata.title.toString() : Util.stripFileExt(playlist.get(currentPlayingIndex).filename));
        String artist = "??unk";
        if (nowPlayingMetadata.artist != null) artist = nowPlayingMetadata.artist.toString();
        String album = "??unk";
        if (nowPlayingMetadata.albumTitle != null) album = nowPlayingMetadata.albumTitle.toString();
        return new MpsMeta(title, artist, album, Uri.parse(playlist.get(currentPlayingIndex).uriSource));
    }

    public static class MpsMeta {
        String title;
        String artist;
        String album;
        Uri uri;

        public MpsMeta(String title, String artist, String album, Uri uri) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.uri = uri;
        }
    }

    public MpsState getMpsState() {
        if (player.getPlaybackState() == Player.STATE_READY || player.getPlaybackState() == Player.STATE_BUFFERING) {
            return new MpsState(
                    player.getDuration(),
                    player.getCurrentPosition(),
                    player.getPlayWhenReady()
            );
        } else {
            return null;
        }
    }

    public static class MpsState {
        public long duration;
        public long position;
        public boolean playButtonIsPlay;

        public MpsState(long duration, long position, boolean playButtonIsPlay) {
            this.duration = duration;
            this.position = position;
            this.playButtonIsPlay = playButtonIsPlay;
        }
    }

    public void unloadCache() {
        MediaMetadataCache.getInstance().unloadCache();
    }

    public void setEventListener(MpsEventListener eventListener) { this.eventListener = eventListener; }

    @OptIn(markerClass = UnstableApi.class)
    private Notification buildNotification() {
        createNotificationChannel();

        Intent dismissIntent = new Intent(ACTION_NOTIFICATION_DISMISSED);
        dismissIntent.setPackage(getPackageName()); // required for implicit intents on newer APIs

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession))
                .setDeleteIntent(PendingIntent.getBroadcast(this, 0, dismissIntent, PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(false)
                .build();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void updateNotification() {
        Intent dismissIntent = new Intent(ACTION_NOTIFICATION_DISMISSED);
        dismissIntent.setPackage(getPackageName()); // required for implicit intents on newer APIs

        var notifBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession))
                .setDeleteIntent(PendingIntent.getBroadcast(this, 0, dismissIntent, PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(false);

        var nowPlayingMetadata = player.getMediaMetadata();
        if (getCurrentPlayingIndex() != -1 && nowPlayingMetadata != MediaMetadata.EMPTY && nowPlayingMetadata.title == null) {
            notifBuilder.setContentTitle(Util.stripFileExt(playlist.get(getCurrentPlayingIndex()).filename));
        }
        var notif = notifBuilder.build();
        var nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notif);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.service_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.service_desc));
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public interface MpsEventListener {
        void onMediaChanged();
        void onMpsExiting();
    }

    public class NotificationDismissedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (eventListener != null) eventListener.onMpsExiting();
            stopSelf();
        }
    }

    public class SleepTimerCheckReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (sleepTimerTargetMinutes == 0) return;

            var nowMins = System.currentTimeMillis() / 60000L;
            if (sleepTimerTargetMinutes <= nowMins) {
                eventListener.onMpsExiting();
                stopSelf();
            }
        }
    }
}