// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class PlaylistItem implements Parcelable {
    String uriSource;
    String filename;
    String title;
    String artist;
    String album;

    protected PlaylistItem(Parcel in) {
        uriSource = in.readString();
        filename = in.readString();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
    }

    public PlaylistItem(String uriSource, String filename, String title, String artist, String album) {
        this.uriSource = uriSource;
        this.filename = filename;
        this.title = title;
        this.artist = artist;
        this.album = album;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(uriSource);
        dest.writeString(filename);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
    }

    public static final Creator<PlaylistItem> CREATOR = new Creator<PlaylistItem>() {
        @Override
        public PlaylistItem createFromParcel(Parcel in) {
            return new PlaylistItem(in);
        }

        @Override
        public PlaylistItem[] newArray(int size) {
            return new PlaylistItem[size];
        }
    };
}
