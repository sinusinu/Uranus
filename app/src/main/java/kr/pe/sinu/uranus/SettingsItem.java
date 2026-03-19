package kr.pe.sinu.uranus;

import android.content.SharedPreferences;

public class SettingsItem {
    public static final int TYPE_BUTTON = 0;
    public static final int TYPE_CHECKBOX = 1;
    public static final int TYPE_CATEGORY = 2;

    public String title;
    public String desc;
    public int type;
    public String key;
    public int value;

    public SettingsItem(String title, String desc, int type, String key) {
        this.title = title;
        this.desc = desc;
        this.type = type;
        this.key = key;
    }

    public void fetch(SharedPreferences sp) {
        if (this.type == TYPE_CHECKBOX) this.value = sp.getInt(key, 0);
    }

    public void set(SharedPreferences sp, int value) {
        if (this.type == TYPE_CHECKBOX) {
            this.value = value;
            sp.edit().putInt(key, value).apply();
        }
    }
}
