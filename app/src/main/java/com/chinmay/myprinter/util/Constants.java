package com.chinmay.myprinter.util;

public class Constants {
    // Printer connection
    public static final String DEFAULT_PRINTER_PORT = "7125";
    public static final String PREF_PRINTER_URL = "printer_url";
    public static final String PREF_PRINTER_API_KEY = "printer_api_key";

    // Build volume (Ender 3 V1)
    public static final float BUILD_VOLUME_X = 220f;
    public static final float BUILD_VOLUME_Y = 220f;
    public static final float BUILD_VOLUME_Z = 250f;

    // Temperature defaults
    public static final int DEFAULT_NOZZLE_TEMP = 200;
    public static final int DEFAULT_BED_TEMP = 60;

    // File paths
    public static final String GCODE_DIR = "gcodes";
    public static final String MODELS_DIR = "models";

    // Thingiverse API
    public static final String DEFAULT_THINGIVERSE_TOKEN = "ee7b031de17d151ca00e27952edbbcee";

    private Constants() {
        // Prevent instantiation
    }
}
