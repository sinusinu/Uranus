// Copyright 2026 Woohyun Shin (sinusinu)
// SPDX-License-Identifier: GPL-3.0-only

package kr.pe.sinu.uranus;

import java.util.ArrayList;

public class PlaylistListItem {
    public String name;
    public ArrayList<String> uris;
    public boolean selected;

    public PlaylistListItem(String name, ArrayList<String> uris) {
        this.name = name;
        this.uris = uris;
        selected = false;
    }
}
