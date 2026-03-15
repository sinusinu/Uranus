// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

public class LibraryItem {
    public static final int TYPE_UNSPECIFIED = 0;
    public static final int TYPE_ROOT_FOLDER = 1;
    public static final int TYPE_ROOT_ADD_FOLDER = 2;
    public static final int TYPE_FOLDER_UP = 3;
    public static final int TYPE_FOLDER_MUSIC = 4;
    public static final int TYPE_FOLDER_FOLDER = 5;

    public int type;
    public boolean selected;
    public PlaylistItem item;
    public String title;
    public String subtitle;
    public String target;

    private LibraryItem(int type, boolean selected, PlaylistItem item, String title, String subtitle, String target) {
        this.type = type;
        this.selected = selected;
        this.item = item;
        this.title = title;
        this.subtitle = subtitle;
        this.target = target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LibraryItem)) return false;
        LibraryItem oi = (LibraryItem)o;
        return (type == oi.type) && (item.equals(oi.item)) && (title.equals(oi.title)) && (subtitle.equals(oi.subtitle)) && (target.equals(oi.target));
    }

    public static LibraryItem asRootFolder(String name, String target) {
        return new LibraryItem(TYPE_ROOT_FOLDER, false, null, name, null, target);
    }

    public static LibraryItem asRootAddFolder() {
        return new LibraryItem(TYPE_ROOT_ADD_FOLDER, false, null, null, null, null);
    }

    public static LibraryItem asFolderUp() {
        return new LibraryItem(TYPE_FOLDER_UP, false, null, null, null, null);
    }

    public static LibraryItem asFolderMusic(PlaylistItem item, String name, String filename, String target) {
        return new LibraryItem(TYPE_FOLDER_MUSIC, false, item, name, filename, target);
    }

    public static LibraryItem asFolderFolder(String name, String target) {
        return new LibraryItem(TYPE_FOLDER_FOLDER, false, null, name, null, target);
    }
}
