#include "slicer.h"
#include <cmath>
#include <algorithm>

static float dist2(const Vec2& a, const Vec2& b) {
    float dx = a.x - b.x, dy = a.y - b.y;
    return dx*dx + dy*dy;
}

static Vec2 edgeIntersect(const Vec3& v0, const Vec3& v1, float z) {
    float t = (z - v0.z) / (v1.z - v0.z);
    return Vec2(v0.x + t * (v1.x - v0.x), v0.y + t * (v1.y - v0.y));
}

std::vector<Segment2D> sliceLayer(const STLModel& model, float z) {
    std::vector<Segment2D> segs;
    segs.reserve(model.triangles.size() / 4);

    for (const auto& tri : model.triangles) {
        int above = 0;
        for (int i = 0; i < 3; i++) if (tri.v[i].z >= z) above++;
        if (above == 0 || above == 3) continue;

        Vec2 pts[2];
        int found = 0;
        for (int i = 0; i < 3 && found < 2; i++) {
            int j = (i + 1) % 3;
            bool ai = tri.v[i].z >= z, aj = tri.v[j].z >= z;
            if (ai != aj) pts[found++] = edgeIntersect(tri.v[i], tri.v[j], z);
        }

        if (found == 2) {
            Segment2D s;
            s.p0 = pts[0]; s.p1 = pts[1];
            segs.push_back(s);
        }
    }
    return segs;
}

std::vector<std::vector<Vec2>> connectSegments(std::vector<Segment2D>& segs, float eps) {
    std::vector<std::vector<Vec2>> contours;
    float eps2 = eps * eps;

    for (size_t si = 0; si < segs.size(); si++) {
        if (segs[si].used) continue;

        std::vector<Vec2> contour;
        segs[si].used = true;
        contour.push_back(segs[si].p0);
        contour.push_back(segs[si].p1);

        bool extended = true;
        while (extended) {
            extended = false;
            const Vec2& tail = contour.back();

            for (auto& seg : segs) {
                if (seg.used) continue;
                if (dist2(seg.p0, tail) < eps2) {
                    contour.push_back(seg.p1);
                    seg.used = true; extended = true; break;
                } else if (dist2(seg.p1, tail) < eps2) {
                    contour.push_back(seg.p0);
                    seg.used = true; extended = true; break;
                }
            }
        }

        if (contour.size() >= 3) contours.push_back(std::move(contour));
    }
    return contours;
}

std::vector<std::vector<Vec2>> generateInfill(const std::vector<std::vector<Vec2>>& contours,
                                               float spacing, int layerNum,
                                               const BoundingBox& bbox) {
    std::vector<std::vector<Vec2>> lines;
    if (spacing <= 0.0f || contours.empty()) return lines;

    bool horiz = (layerNum % 2 == 0);
    float lo, hi;

    if (horiz) {
        lo = bbox.min.y - 1.0f; hi = bbox.max.y + 1.0f;
        for (float y = lo; y <= hi; y += spacing) {
            std::vector<float> xs;
            for (const auto& contour : contours) {
                size_t n = contour.size();
                for (size_t i = 0, j = n - 1; i < n; j = i++) {
                    const Vec2& p0 = contour[j], &p1 = contour[i];
                    if ((p0.y <= y) == (p1.y <= y)) continue;
                    float t = (y - p0.y) / (p1.y - p0.y);
                    xs.push_back(p0.x + t * (p1.x - p0.x));
                }
            }
            std::sort(xs.begin(), xs.end());
            for (size_t i = 0; i + 1 < xs.size(); i += 2) {
                lines.push_back({Vec2(xs[i], y), Vec2(xs[i+1], y)});
            }
        }
    } else {
        lo = bbox.min.x - 1.0f; hi = bbox.max.x + 1.0f;
        for (float x = lo; x <= hi; x += spacing) {
            std::vector<float> ys;
            for (const auto& contour : contours) {
                size_t n = contour.size();
                for (size_t i = 0, j = n - 1; i < n; j = i++) {
                    const Vec2& p0 = contour[j], &p1 = contour[i];
                    if ((p0.x <= x) == (p1.x <= x)) continue;
                    float t = (x - p0.x) / (p1.x - p0.x);
                    ys.push_back(p0.y + t * (p1.y - p0.y));
                }
            }
            std::sort(ys.begin(), ys.end());
            for (size_t i = 0; i + 1 < ys.size(); i += 2) {
                lines.push_back({Vec2(x, ys[i]), Vec2(x, ys[i+1])});
            }
        }
    }
    return lines;
}
