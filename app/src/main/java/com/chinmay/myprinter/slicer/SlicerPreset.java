package com.chinmay.myprinter.slicer;

public class SlicerPreset {
    public final String name;
    public final String description;
    public final SlicerSettings settings;

    private SlicerPreset(String name, String description, SlicerSettings s) {
        this.name = name;
        this.description = description;
        this.settings = s;
    }

    public static SlicerPreset[] getPresets() {
        SlicerSettings draft = new SlicerSettings();
        draft.layerHeight     = 0.28f;
        draft.printSpeed      = 60.0f;
        draft.infillPercent   = 15;
        draft.nozzleTemp      = 205.0f;
        draft.nozzleTempLayer0 = 205.0f;

        SlicerSettings std = new SlicerSettings();
        std.nozzleTemp       = 220.0f;
        std.nozzleTempLayer0 = 220.0f;

        SlicerSettings hd = new SlicerSettings();
        hd.layerHeight      = 0.12f;
        hd.printSpeed       = 40.0f;
        hd.infillPercent    = 25;


        return new SlicerPreset[]{
            new SlicerPreset("Draft — Fast",    "0.28mm · 15% infill · ~60 mm/s", draft),
            new SlicerPreset("Standard",         "0.20mm · 20% infill · ~50 mm/s", std),
            new SlicerPreset("High Detail",      "0.12mm · 25% infill · ~40 mm/s", hd),
        };
    }
}
