package com.minibar.port;

import android.content.SharedPreferences;

public final class Prefs {
    public static final String FILE = "settings";

    public static final String K_HIDE = "prefs_key_systemui_notification_center_mini_window_bar_hide_enabled";
    public static final String K_WIDTH_ENABLED = "prefs_key_systemui_notification_center_mini_window_bar_width_enabled";
    public static final String K_WIDTH = "prefs_key_systemui_notification_center_mini_window_bar_width";
    public static final String K_HEIGHT_ENABLED = "prefs_key_systemui_notification_center_mini_window_bar_height_enabled";
    public static final String K_HEIGHT = "prefs_key_systemui_notification_center_mini_window_bar_height";
    public static final String K_TRIGGER_ENABLED = "prefs_key_systemui_notification_center_mini_window_trigger_threshold_enabled";
    public static final String K_TRIGGER = "prefs_key_systemui_notification_center_mini_window_trigger_threshold";
    public static final String K_MAX_TRIGGER_ENABLED = "prefs_key_systemui_notification_center_mini_window_max_trigger_threshold_enabled";
    public static final String K_MAX_TRIGGER = "prefs_key_systemui_notification_center_mini_window_max_trigger_threshold";

    public static final float DEFAULT_WIDTH = 60.36f;
    public static final float DEFAULT_HEIGHT = 3.64f;
    public static final float DEFAULT_TRIGGER = 15f;
    public static final float DEFAULT_MAX_TRIGGER = 60f;

    private static SharedPreferences sPrefs;

    private Prefs() {
    }

    static void attach(SharedPreferences prefs) {
        sPrefs = prefs;
    }

    static SharedPreferences prefs() {
        return sPrefs;
    }

    public static boolean hide() {
        return sPrefs == null || sPrefs.getBoolean(K_HIDE, true);
    }

    public static boolean widthEnabled() {
        return sPrefs != null && sPrefs.getBoolean(K_WIDTH_ENABLED, false);
    }

    public static float width() {
        return sPrefs == null ? DEFAULT_WIDTH : sPrefs.getFloat(K_WIDTH, DEFAULT_WIDTH);
    }

    public static boolean heightEnabled() {
        return sPrefs != null && sPrefs.getBoolean(K_HEIGHT_ENABLED, false);
    }

    public static float height() {
        return sPrefs == null ? DEFAULT_HEIGHT : sPrefs.getFloat(K_HEIGHT, DEFAULT_HEIGHT);
    }

    public static boolean triggerEnabled() {
        return sPrefs != null && sPrefs.getBoolean(K_TRIGGER_ENABLED, false);
    }

    public static float trigger() {
        return sPrefs == null ? DEFAULT_TRIGGER : sPrefs.getFloat(K_TRIGGER, DEFAULT_TRIGGER);
    }

    public static boolean maxTriggerEnabled() {
        return sPrefs != null && sPrefs.getBoolean(K_MAX_TRIGGER_ENABLED, false);
    }

    public static float maxTrigger() {
        return sPrefs == null ? DEFAULT_MAX_TRIGGER : sPrefs.getFloat(K_MAX_TRIGGER, DEFAULT_MAX_TRIGGER);
    }
}
