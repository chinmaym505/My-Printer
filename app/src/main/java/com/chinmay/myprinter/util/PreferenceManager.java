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
    private static final String KEY_START_GCODE = "start_gcode";
    private static final String KEY_END_GCODE   = "end_gcode";

    public static final String DEFAULT_START_GCODE =
            "G28 ; Home all axes\n" +
            "G1 Z5 F3000 ; Lift nozzle\n" +
            "M190 S{bed_temp_layer_0} ; Wait for bed\n" +
            "M109 S{nozzle_temp_layer_0} ; Wait for nozzle\n" +
            "G1 X0.1 Y20 Z0.3 F5000.0\n" +
            "G1 X0.1 Y200.0 Z0.3 F1500.0 E15\n" +
            "G1 X0.4 Y200.0 Z0.3 F5000.0\n" +
            "G1 X0.4 Y20 Z0.3 F1500.0 E30\n" +
            "G92 E0\n" +
            "G1 Z2.0 F3000\n";

    public static final String DEFAULT_END_GCODE =
            "G91 ; Relative positioning\n" +
            "G1 E-2 F2700 ; Retract\n" +
            "G1 E-2 Z0.2 F2400\n" +
            "G1 X5 Y5 F3000 ; Wipe\n" +
            "G1 Z10\n" +
            "G90 ; Absolute positioning\n" +
            "G1 X0 Y220\n" +
            "M106 S0 ; Fan off\n" +
            "M104 S0 ; Hotend off\n" +
            "M140 S0 ; Bed off\n" +
            "M84 X Y E ; Motors off\n";

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

    public void setStartGcode(String gcode) {
        prefs.edit().putString(KEY_START_GCODE, gcode).apply();
    }

    public String getStartGcode() {
        return prefs.getString(KEY_START_GCODE, DEFAULT_START_GCODE);
    }

    public void setEndGcode(String gcode) {
        prefs.edit().putString(KEY_END_GCODE, gcode).apply();
    }

    public String getEndGcode() {
        return prefs.getString(KEY_END_GCODE, DEFAULT_END_GCODE);
    }
}
