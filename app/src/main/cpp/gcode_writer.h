#pragma once
#include "slicer.h"
#include <fstream>
#include <string>

class GCodeWriter {
public:
    GCodeWriter(const std::string& path, const SlicerConfig& cfg);
    ~GCodeWriter();

    bool isOpen() const { return file_.is_open(); }
    void writeStartGCode();
    void writeEndGCode();
    void writeLayer(int layerNum, float z,
                    const std::vector<std::vector<Vec2>>& contours,
                    const std::vector<std::vector<Vec2>>& infill,
                    bool firstLayer);

private:
    void travel(float x, float y);
    void extrude(float x, float y, float speed);
    void retract();
    void prime();
    float calcE(float dist);

    std::ofstream file_;
    SlicerConfig cfg_;
    float cx_ = 0, cy_ = 0, cz_ = 0, ce_ = 0;
    bool retracted_ = true;
    static constexpr float PI = 3.14159265f;
};
