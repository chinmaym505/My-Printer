#include "stl_parser.h"
#include <fstream>
#include <cstring>
#include <cstdint>
#include <cstdio>

static bool isBinarySTL(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;

    char header[80];
    f.read(header, 80);
    if (f.gcount() < 80) return false;

    uint32_t triCount = 0;
    f.read(reinterpret_cast<char*>(&triCount), 4);
    if (f.gcount() < 4) return false;

    f.seekg(0, std::ios::end);
    std::streamsize fileSize = f.tellg();
    std::streamsize expected = 80 + 4 + (std::streamsize)triCount * 50;

    return fileSize == expected && triCount > 0;
}

static bool parseBinary(const std::string& path, STLModel& model) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;

    char header[80];
    f.read(header, 80);

    uint32_t triCount = 0;
    f.read(reinterpret_cast<char*>(&triCount), 4);

    model.triangles.reserve(triCount);

    for (uint32_t i = 0; i < triCount; i++) {
        Triangle tri;
        f.read(reinterpret_cast<char*>(&tri.normal), 12);
        f.read(reinterpret_cast<char*>(&tri.v[0]), 12);
        f.read(reinterpret_cast<char*>(&tri.v[1]), 12);
        f.read(reinterpret_cast<char*>(&tri.v[2]), 12);
        uint16_t attr = 0;
        f.read(reinterpret_cast<char*>(&attr), 2);

        for (int j = 0; j < 3; j++) model.bbox.expand(tri.v[j]);
        model.triangles.push_back(tri);
    }
    return true;
}

static bool parseASCII(const std::string& path, STLModel& model) {
    std::ifstream f(path);
    if (!f) return false;

    std::string line;
    Triangle tri;
    int vIdx = 0;

    while (std::getline(f, line)) {
        const char* s = line.c_str();
        while (*s == ' ' || *s == '\t') s++;

        if (strncmp(s, "facet normal", 12) == 0) {
            sscanf(s, "facet normal %f %f %f", &tri.normal.x, &tri.normal.y, &tri.normal.z);
            vIdx = 0;
        } else if (strncmp(s, "vertex", 6) == 0 && vIdx < 3) {
            sscanf(s, "vertex %f %f %f", &tri.v[vIdx].x, &tri.v[vIdx].y, &tri.v[vIdx].z);
            model.bbox.expand(tri.v[vIdx]);
            vIdx++;
        } else if (strncmp(s, "endfacet", 8) == 0 && vIdx == 3) {
            model.triangles.push_back(tri);
        }
    }
    return !model.triangles.empty();
}

bool parseSTL(const std::string& path, STLModel& model, std::string& error) {
    bool ok = isBinarySTL(path) ? parseBinary(path, model) : parseASCII(path, model);
    if (!ok) {
        error = "Failed to parse STL file";
        return false;
    }
    if (model.triangles.empty()) {
        error = "STL file contains no triangles";
        return false;
    }
    return true;
}
