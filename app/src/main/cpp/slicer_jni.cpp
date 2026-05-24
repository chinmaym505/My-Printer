#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <string>
#include "stl_parser.h"
#include "slicer.h"
#include "gcode_writer.h"

#define TAG "SlicerJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::atomic<int>  g_progress{0};
static std::atomic<bool> g_cancel{false};

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_chinmay_myprinter_slicer_SlicerEngine_sliceNative(
        JNIEnv* env, jobject,
        jstring jStl, jstring jOut,
        jfloat layerH, jfloat nozzleDia, jfloat printSpd, jfloat travelSpd,
        jint infillPct, jint wallCnt,
        jfloat nozzleT, jfloat bedT) {

    g_progress.store(0);
    g_cancel.store(false);

    const char* cs = env->GetStringUTFChars(jStl, nullptr);
    const char* co = env->GetStringUTFChars(jOut, nullptr);
    std::string stlPath(cs), outPath(co);
    env->ReleaseStringUTFChars(jStl, cs);
    env->ReleaseStringUTFChars(jOut, co);

    LOGI("Slicing %s -> %s", stlPath.c_str(), outPath.c_str());

    STLModel model;
    std::string err;
    if (!parseSTL(stlPath, model, err)) {
        LOGE("Parse error: %s", err.c_str());
        return env->NewStringUTF(("Error: " + err).c_str());
    }
    LOGI("Triangles: %zu  BBox z: %.2f..%.2f",
         model.triangles.size(), model.bbox.min.z, model.bbox.max.z);

    // Centre on bed
    float cx = (model.bbox.max.x + model.bbox.min.x) / 2.0f;
    float cy = (model.bbox.max.y + model.bbox.min.y) / 2.0f;
    float ox = 110.0f - cx, oy = 110.0f - cy, oz = -model.bbox.min.z;
    for (auto& t : model.triangles) {
        for (int i = 0; i < 3; i++) { t.v[i].x += ox; t.v[i].y += oy; t.v[i].z += oz; }
    }
    model.bbox.min.x += ox; model.bbox.max.x += ox;
    model.bbox.min.y += oy; model.bbox.max.y += oy;
    model.bbox.min.z += oz; model.bbox.max.z += oz;

    SlicerConfig cfg;
    cfg.layerHeight    = layerH;
    cfg.nozzleDiameter = nozzleDia;
    cfg.printSpeed     = printSpd;
    cfg.travelSpeed    = travelSpd;
    cfg.infillPercent  = infillPct;
    cfg.wallCount      = wallCnt;
    cfg.nozzleTemp     = nozzleT;
    cfg.bedTemp        = bedT;

    GCodeWriter writer(outPath, cfg);
    if (!writer.isOpen()) {
        LOGE("Cannot open output: %s", outPath.c_str());
        return env->NewStringUTF("Error: cannot open output file");
    }

    writer.writeStartGCode();

    float modelH = model.bbox.max.z - model.bbox.min.z;
    int totalLayers = std::max(1, (int)(modelH / layerH) + 1);
    float infillSpacing = (infillPct > 0) ? (nozzleDia / (infillPct / 100.0f)) : 1e6f;

    LOGI("Layers: %d  infill spacing: %.2f mm", totalLayers, infillSpacing);

    for (int n = 0; n < totalLayers; n++) {
        if (g_cancel.load()) {
            LOGI("Cancelled at layer %d", n);
            return env->NewStringUTF("Cancelled");
        }

        float z = model.bbox.min.z + layerH * (n + 0.5f);
        auto segs     = sliceLayer(model, z);
        auto contours = connectSegments(segs);
        auto infill   = generateInfill(contours, infillSpacing, n, model.bbox);

        writer.writeLayer(n, z, contours, infill, n == 0);
        g_progress.store((n * 100) / totalLayers);
    }

    writer.writeEndGCode();
    g_progress.store(100);
    LOGI("Done");

    return env->NewStringUTF("OK");
}

JNIEXPORT jint JNICALL
Java_com_chinmay_myprinter_slicer_SlicerEngine_getProgress(JNIEnv*, jobject) {
    return g_progress.load();
}

JNIEXPORT void JNICALL
Java_com_chinmay_myprinter_slicer_SlicerEngine_cancelSlicing(JNIEnv*, jobject) {
    g_cancel.store(true);
}

} // extern "C"
