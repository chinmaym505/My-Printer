#pragma once
#include "stl_parser.h"
#include <vector>
#include <atomic>

struct Vec2 {
    float x, y;
    Vec2() : x(0), y(0) {}
    Vec2(float x, float y) : x(x), y(y) {}
};

struct Segment2D {
    Vec2 p0, p1;
    bool used = false;
};

struct SlicerConfig {
    float layerHeight     = 0.20f;
    float nozzleDiameter  = 0.40f;
    float filamentDiam    = 1.75f;
    float printSpeed      = 50.0f;
    float travelSpeed     = 150.0f;
    float firstLayerSpeed = 25.0f;
    int   infillPercent   = 20;
    int   wallCount       = 2;
    float nozzleTemp      = 200.0f;
    float bedTemp         = 60.0f;
    float retractLength   = 5.0f;
    float retractSpeed    = 45.0f;
    float buildX          = 220.0f;
    float buildY          = 220.0f;
    float buildZ          = 250.0f;
};

std::vector<Segment2D> sliceLayer(const STLModel& model, float z);
std::vector<std::vector<Vec2>> connectSegments(std::vector<Segment2D>& segments, float eps = 0.01f);
std::vector<std::vector<Vec2>> generateInfill(const std::vector<std::vector<Vec2>>& contours,
                                               float spacing, int layerNum,
                                               const BoundingBox& bbox);
