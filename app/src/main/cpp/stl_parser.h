#pragma once
#include <vector>
#include <string>
#include <cfloat>
#include <algorithm>

struct Vec3 {
    float x, y, z;
    Vec3() : x(0), y(0), z(0) {}
    Vec3(float x, float y, float z) : x(x), y(y), z(z) {}
};

struct Triangle {
    Vec3 normal;
    Vec3 v[3];
};

struct BoundingBox {
    Vec3 min, max;
    BoundingBox() : min(FLT_MAX, FLT_MAX, FLT_MAX), max(-FLT_MAX, -FLT_MAX, -FLT_MAX) {}
    void expand(const Vec3& p) {
        min.x = std::min(min.x, p.x); min.y = std::min(min.y, p.y); min.z = std::min(min.z, p.z);
        max.x = std::max(max.x, p.x); max.y = std::max(max.y, p.y); max.z = std::max(max.z, p.z);
    }
};

struct STLModel {
    std::vector<Triangle> triangles;
    BoundingBox bbox;
};

bool parseSTL(const std::string& path, STLModel& model, std::string& error);
