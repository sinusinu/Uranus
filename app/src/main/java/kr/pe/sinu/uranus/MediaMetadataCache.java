// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

public class MediaMetadataCache {
    private static final String FILENAME_KNOWN_TAGS_JSON = "known_tags.json";

    private static MediaMetadataCache instance;

    private boolean isCacheLoaded = false;
    private final HashMap<String, CacheableMediaMetadata> cache = new HashMap<>();

    private MediaMetadataCache() {}

    public static MediaMetadataCache getInstance() {
        if (instance == null) instance = new MediaMetadataCache();
        return instance;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isMediaMetadataCached(Context context, Uri uri, long size, long lastModified) {
        loadCacheIfNot(context);
        return cache.containsKey(getCacheKey(uri, size, lastModified));
    }

    public CacheableMediaMetadata getMediaMetadata(Context context, String filename, Uri uri, long size, long lastModified) {
        loadCacheIfNot(context);

        String cacheId = getCacheKey(uri, size, lastModified);

        synchronized (cache) {
            if (cache.containsKey(cacheId)) return cache.get(cacheId);
        }

        String title;
        String artist;
        String album;

        // this is too slow...
        try (var mmr = new MediaMetadataRetriever()) {
            mmr.setDataSource(context, uri);
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (title == null) title = Util.stripFileExt(filename);
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (artist == null) artist = "??unk";
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (album == null) album = "??unk";
            String coverFilename = null;
            var art = mmr.getEmbeddedPicture();
            if (art != null) {
                var artHash = Util.toMd5(art);
                coverFilename = artHash + ".webp";
                var artFile = new File(context.getCacheDir(), artHash + ".webp");
                if (!artFile.exists()) {
                    try {
                        var artBitmapFull = BitmapFactory.decodeByteArray(art, 0, art.length);
                        var artOriginalWidth = artBitmapFull.getWidth();
                        var artOriginalHeight = artBitmapFull.getHeight();
                        Bitmap artBitmap;
                        if (artOriginalWidth > 512) {
                            var artScaledHeight = (int) (512f * (artOriginalHeight / (float) artOriginalWidth));
                            artBitmap = Bitmap.createScaledBitmap(artBitmapFull, 512, artScaledHeight, true);
                            artBitmapFull.recycle();
                        } else if (artOriginalHeight > 512) {
                            var artScaledWidth = (int) (512f * (artOriginalWidth / (float) artOriginalHeight));
                            artBitmap = Bitmap.createScaledBitmap(artBitmapFull, artScaledWidth, 512, true);
                            artBitmapFull.recycle();
                        } else {
                            artBitmap = artBitmapFull;
                        }
                        artBitmapFull = null;
                        try (var fos = new FileOutputStream(artFile)) {
                            artBitmap.compress(Bitmap.CompressFormat.WEBP, 85, fos);
                        }
                        artBitmap.recycle();
                        artBitmap = null;
                    } catch (Exception e) {
                        Log.w("Uranus", "Failed to parse embedded picture!");
                        Log.w("Uranus", e.toString());
                        coverFilename = null;
                    }
                }
            }
            CacheableMediaMetadata mm = new CacheableMediaMetadata(title, artist, album, size, coverFilename);
            synchronized (cache) {
                cache.put(cacheId, mm);
            }
            saveCacheAsync(context);
            return mm;
        } catch (Exception e) {
            Log.e("Uranus", "Failed to parse file! returning empty");
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            CacheableMediaMetadata mm = new CacheableMediaMetadata(filename, "??unk", "??unk", 0, null);
            synchronized (cache) {
                cache.put(cacheId, mm);
            }
            saveCacheAsync(context);
            return mm;
        }
    }

    public static String getCacheKey(Uri uri, long size, long lastModified) {
        if (uri.getPath() == null) return "0_0_0";
        return Util.toCrc32(uri.getPath()) + "_" + size + "_" + lastModified;
    }

    public void unloadCache() {
        synchronized (cache) {
            cache.clear();
            isCacheLoaded = false;
        }
    }

    private void loadCacheIfNot(Context context) {
        if (!isCacheLoaded) {
            isCacheLoaded = true;
            File f = new File(context.getCacheDir(), FILENAME_KNOWN_TAGS_JSON);
            if (f.exists()) {
                var c = Util.readString(f);
                synchronized (cache) {
                    try {
                        JSONObject j = new JSONObject(c);
                        var ji = j.keys();

                        while (ji.hasNext()) {
                            var k = ji.next();
                            JSONObject ko = j.getJSONObject(k);
                            CacheableMediaMetadata cmm = new CacheableMediaMetadata(
                                    ko.getString("title"),
                                    ko.getString("artist"),
                                    ko.getString("album"),
                                    ko.getLong("size"),
                                    ko.getString("cover")
                            );
                            cache.put(k, cmm);
                        }
                    } catch (JSONException e) {
                        cache.clear();
                    }
                }
            }
        }
    }

    private void saveCacheAsync(Context context) {
        final Context fContext = context;
        new Thread(() -> {
            boolean error = false;
            var o = new JSONObject();
            synchronized (cache) {
                for (Map.Entry<String, CacheableMediaMetadata> e : cache.entrySet()) {
                    try {
                        o.put(e.getKey(), e.getValue().toJSONObject());
                    } catch (JSONException ex) {
                        error = true;
                        break;
                    }
                }
            }
            if (!error) {
                File f = new File(context.getCacheDir(), FILENAME_KNOWN_TAGS_JSON);
                Util.writeString(f, o.toString());
            }
        }).start();
    }

    public static class CacheableMediaMetadata {
        String title;
        String artist;
        String album;
        long size;
        @Deprecated boolean hasCover = false;
        String cover;

        public CacheableMediaMetadata(String title, String artist, String album, long size, String cover) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.size = size;
            this.cover = cover;
        }

        public JSONObject toJSONObject() {
            JSONObject ret = new JSONObject();
            try {
                ret.put("title", title);
                ret.put("artist", artist);
                ret.put("album", album);
                ret.put("size", size);
                ret.put("cover", cover);
            } catch (JSONException e) {
                return null;
            }
            return ret;
        }
    }
}
