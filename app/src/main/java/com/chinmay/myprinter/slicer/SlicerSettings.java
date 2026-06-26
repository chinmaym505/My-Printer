package com.chinmay.myprinter.slicer;

public class SlicerSettings {
    // Layer / geometry
    public float layerHeight          = 0.20f;
    public float initialLayerHeight   = 0.28f;  // thicker first layer improves bed adhesion
    public float nozzleDiameter       = 0.40f;
    public int   wallCount            = 3;
    public int   bottomLayers         = -1;     // -1 = auto (~1 mm / layerHeight)
    public String infillPattern       = "lines";
    public int   infillPercent        = 20;
    public String insetDirection      = "inside_out";

    // Speed (mm/s)
    public float printSpeed           = 50.0f;
    public float travelSpeed          = 150.0f;
    public float speedLayer0          = 25.0f;
    public float speedWall0           = -1.0f;  // -1 = 60% of printSpeed

    // Temperature
    public float nozzleTemp           = 220.0f;
    public float nozzleTempLayer0     = 220.0f;
    public float bedTemp              = 80.0f;

    // Flow
    public float materialFlow         = 100.0f;
    public float materialFlowLayer0   = 100.0f;

    // Retraction
    public float   retractionAmount          = 5.0f;   // Bowden (Ender 3 V1) needs 4-6 mm
    public String  retractionCombing         = "all";   // keep nozzle inside model during travel
    public boolean retractionHopEnabled      = false;
    public float   retractionExtrusionWindow = 1.0f;

    // Surface quality
    public boolean ironingEnabled     = false;
    public boolean ironingMonotonic   = false;
    public int     roofingLayerCount  = 2;
    public float   roofingMaterialFlow = 100.0f;

    // Adhesion / supports
    public String adhesionType        = "none";

    // Seam
    public String zSeamType           = "back";
    public String zSeamCorner         = "z_seam_corner_weighted";

    // Mesh simplification
    public float meshfixMaxResolution  = 0.5f;
}
