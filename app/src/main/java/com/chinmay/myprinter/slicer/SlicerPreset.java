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
        draft.layerHeight      = 0.28f;
        draft.printSpeed       = 60.0f;
        draft.infillPercent    = 15;
        draft.nozzleTemp       = 205.0f;
        draft.nozzleTempLayer0 = 205.0f;

        SlicerSettings std = new SlicerSettings();
        std.nozzleTemp         = 220.0f;
        std.nozzleTempLayer0   = 220.0f;

        SlicerSettings hd = new SlicerSettings();
        hd.layerHeight         = 0.12f;
        hd.printSpeed          = 40.0f;
        hd.infillPercent       = 25;
        hd.nozzleTemp          = 220.0f;
        hd.nozzleTempLayer0    = 220.0f;

        SlicerSettings fast2 = new SlicerSettings();
        fast2.printSpeed              = 300.0f;
        fast2.speedLayer0             = 50.0f;
        fast2.speedWall0              = 50.0f;
        fast2.wallCount               = 4;
        fast2.bottomLayers            = 7;
        fast2.infillPattern           = "gyroid";
        fast2.insetDirection          = "outside_in";
        fast2.nozzleTemp              = 220.0f;
        fast2.nozzleTempLayer0        = 220.0f;
        fast2.bedTemp                 = 65.0f;
        fast2.materialFlow            = 105.0f;
        fast2.materialFlowLayer0      = 110.0f;
        fast2.retractionAmount        = 5.0f;
        fast2.retractionCombing       = "all";
        fast2.retractionHopEnabled    = true;
        fast2.retractionExtrusionWindow = 1.0f;
        fast2.ironingEnabled          = true;
        fast2.ironingMonotonic        = true;
        fast2.roofingLayerCount       = 3;
        fast2.roofingMaterialFlow     = 110.0f;
        fast2.adhesionType            = "skirt";
        fast2.zSeamType               = "sharpest_corner";
        fast2.zSeamCorner             = "z_seam_corner_inner";
        fast2.meshfixMaxResolution    = 0.3f;

        return new SlicerPreset[]{
            new SlicerPreset("Draft — Fast", "0.28mm · 15% infill · ~60 mm/s",    draft),
            new SlicerPreset("Standard",     "0.20mm · 20% infill · ~50 mm/s",    std),
            new SlicerPreset("High Detail",  "0.12mm · 25% infill · ~40 mm/s",    hd),
            new SlicerPreset("Fast #2",      "0.20mm · gyroid · 300 mm/s · skirt", fast2),
        };
    }
}
