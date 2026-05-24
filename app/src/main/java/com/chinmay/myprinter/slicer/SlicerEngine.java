package com.chinmay.myprinter.slicer;

public class SlicerEngine {

    static {
        System.loadLibrary("slicer_jni");
    }

    public native String sliceNative(String stlPath, String outputPath,
            float layerHeight, float nozzleDiameter,
            float printSpeed, float travelSpeed,
            int infillPercent, int wallCount,
            float nozzleTemp, float bedTemp);

    public native int  getProgress();
    public native void cancelSlicing();

    public String slice(String stlPath, String outputPath, SlicerSettings s) {
        return sliceNative(stlPath, outputPath,
                s.layerHeight, s.nozzleDiameter,
                s.printSpeed,  s.travelSpeed,
                s.infillPercent, s.wallCount,
                s.nozzleTemp, s.bedTemp);
    }
}
