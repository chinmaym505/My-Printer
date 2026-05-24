package com.chinmay.myprinter.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREFS_NAME = "MyPrinterPrefs";
    private static final String KEY_PRINTER_HOST = "printer_host";
    private static final String KEY_PRINTER_PORT = "printer_port";
    private static final String KEY_USE_MOCK = "use_mock";
    private static final String KEY_LAST_CONNECTED = "last_connected";
    private static final String KEY_CAMERA_URL = "camera_url";
    private static final String KEY_THINGIVERSE_TOKEN = "thingiverse_token";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setPrinterHost(String host) {
        prefs.edit().putString(KEY_PRINTER_HOST, host).apply();
    }

    public String getPrinterHost() {
        return prefs.getString(KEY_PRINTER_HOST, "");
    }

    public void setPrinterPort(int port) {
        prefs.edit().putInt(KEY_PRINTER_PORT, port).apply();
    }

    public int getPrinterPort() {
        return prefs.getInt(KEY_PRINTER_PORT, 7125);
    }

    public void setUseMock(boolean useMock) {
        prefs.edit().putBoolean(KEY_USE_MOCK, useMock).apply();
    }

    public boolean isUseMock() {
        return prefs.getBoolean(KEY_USE_MOCK, false);
    }

    public void setLastConnected(boolean connected) {
        prefs.edit().putBoolean(KEY_LAST_CONNECTED, connected).apply();
    }

    public boolean wasLastConnected() {
        return prefs.getBoolean(KEY_LAST_CONNECTED, false);
    }

    public boolean hasPrinterConfigured() {
        String host = getPrinterHost();
        return host != null && !host.isEmpty();
    }

    public void setCameraUrl(String url) {
        prefs.edit().putString(KEY_CAMERA_URL, url).apply();
    }

    public String getCameraUrl() {
        return prefs.getString(KEY_CAMERA_URL, "");
    }

    public void setThingiverseToken(String token) {
        prefs.edit().putString(KEY_THINGIVERSE_TOKEN, token).apply();
    }

    public String getThingiverseToken() {
        return prefs.getString(KEY_THINGIVERSE_TOKEN, "");
    }
}
