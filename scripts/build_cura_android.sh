#!/usr/bin/env bash
# Cross-compile CuraEngine 5.11.0 for Android arm64-v8a.
# Run from the project root inside WSL2:
#   bash scripts/build_cura_android.sh
#
# Prerequisites (inside WSL2):
#   sudo apt-get install -y python3-pip cmake ninja-build git build-essential pkg-config
#   pip3 install --user "conan>=2.7.0"

set -euo pipefail

CURA_VERSION="5.11.0"
ABI="arm64-v8a"
API_LEVEL=24

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"
BUILD_DIR="$SCRIPT_DIR/cura_build"

# ---------------------------------------------------------------------------
# Find Android NDK
# ---------------------------------------------------------------------------
find_ndk() {
    local candidates=(
        "${ANDROID_NDK_HOME:-}"
        "${NDK_HOME:-}"
        "$HOME/Android/Sdk/ndk/android-ndk-r27c"
        "$HOME/Android/Sdk/ndk/27.0.12077973"
        "$HOME/Android/Sdk/ndk/26.1.10909125"
        "$HOME/Android/Sdk/ndk/25.2.9519653"
    )
    # Only search Windows filesystem if NDK not already found via env vars
    if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -z "${NDK_HOME:-}" ]; then
        local win_ndk_base="/mnt/c/Users"
        if [ -d "$win_ndk_base" ]; then
            while IFS= read -r p; do
                candidates+=("$p")
            done < <(find "$win_ndk_base" -maxdepth 6 \
                          -name "android.toolchain.cmake" 2>/dev/null \
                          -printf "%h\n" | sed 's|/build/cmake||')
        fi
    fi
    for p in "${candidates[@]}"; do
        [ -z "$p" ] && continue
        if [ -f "$p/build/cmake/android.toolchain.cmake" ]; then
            echo "$p"; return 0
        fi
    done
    return 1
}

echo "==> Locating Android NDK..."
NDK_PATH="$(find_ndk)" || {
    echo "ERROR: Android NDK not found. Set ANDROID_NDK_HOME and rerun."
    exit 1
}
echo "    NDK: $NDK_PATH"

# ---------------------------------------------------------------------------
# Conan 2.x
# ---------------------------------------------------------------------------
export PATH="$HOME/.local/bin:$PATH"
if ! command -v conan &>/dev/null; then
    echo "ERROR: conan not found. Run: pip3 install --user 'conan>=2.7.0'"
    exit 1
fi
echo "==> Conan $(conan --version 2>&1 | grep -oP '\d+\.\d+\.\d+')"

# Only ConanCenter needed now (no Ultimaker remote)
conan remote remove ultimaker 2>/dev/null || true

# ---------------------------------------------------------------------------
# Conan profiles
# ---------------------------------------------------------------------------
echo "==> Setting up Conan profiles..."
conan profile detect --name linux-x86_64 --force >/dev/null 2>&1 || true

CLANG_BIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
CLANG_VER=$("$CLANG_BIN" --version 2>&1 | grep -oP '\d+\.\d+' | grep -oP '^\d+')

mkdir -p ~/.conan2/profiles
cat > ~/.conan2/profiles/android-arm64 << PROFILE
[settings]
os=Android
os.api_level=$API_LEVEL
arch=armv8
compiler=clang
compiler.version=$CLANG_VER
compiler.libcxx=c++_static
compiler.cppstd=20
build_type=Release

[conf]
tools.android:ndk_path=$NDK_PATH
PROFILE
echo "    android-arm64 profile (clang $CLANG_VER)"

# ---------------------------------------------------------------------------
# Download Cura settings
# ---------------------------------------------------------------------------
SETTINGS_DIR="$PROJECT_ROOT/app/src/main/assets/cura_settings"
mkdir -p "$SETTINGS_DIR"
for DEF in fdmprinter fdmextruder; do
    FILE="$SETTINGS_DIR/${DEF}.def.json"
    # Always re-download to ensure we have the correct version for CURA_VERSION
    echo "==> Downloading ${DEF}.def.json (Cura $CURA_VERSION)..."
    curl -fsSL "https://raw.githubusercontent.com/Ultimaker/Cura/$CURA_VERSION/resources/definitions/${DEF}.def.json" \
         -o "$FILE"
done

# ---------------------------------------------------------------------------
# Clone CuraEngine 5.11.0
# ---------------------------------------------------------------------------
mkdir -p "$BUILD_DIR" "$OUTPUT_DIR"
cd "$BUILD_DIR"
if [ ! -d "CuraEngine" ]; then
    echo "==> Cloning CuraEngine $CURA_VERSION..."
    git clone --depth=1 --branch "$CURA_VERSION" https://github.com/Ultimaker/CuraEngine.git
else
    echo "==> CuraEngine already cloned."
fi

# ---------------------------------------------------------------------------
# Clone mapbox-wagyu 0.5.0 (header-only)
# ---------------------------------------------------------------------------
if [ ! -d "wagyu" ]; then
    echo "==> Cloning mapbox/wagyu 0.5.0..."
    git clone --depth=1 --branch 0.5.0 https://github.com/mapbox/wagyu.git
else
    echo "==> wagyu already cloned."
fi

# ---------------------------------------------------------------------------
# Clone mapbox/geometry.hpp 2.0.3 (header-only, required by wagyu)
# ---------------------------------------------------------------------------
if [ ! -d "mapbox_geometry" ]; then
    echo "==> Cloning mapbox/geometry.hpp 2.0.3..."
    git clone --depth=1 --branch v2.0.3 https://github.com/mapbox/geometry.hpp.git mapbox_geometry
else
    echo "==> mapbox_geometry already cloned."
fi

# ---------------------------------------------------------------------------
# Patch CMakeLists.txt: clipper → polyclipping
#   ConanCenter has polyclipping/6.4.2 with target polyclipping::polyclipping
#   Headers are the same: <polyclipping/clipper.hpp>
# ---------------------------------------------------------------------------
echo "==> Patching CuraEngine CMakeLists.txt (clipper → polyclipping)..."
sed -i \
    -e 's/find_package(clipper REQUIRED)/find_package(polyclipping REQUIRED)/' \
    -e 's/clipper::clipper/polyclipping::polyclipping/g' \
    -e '/find_package(TBB/d' \
    -e 's|\$<\$<NOT:\$<BOOL:\${EMSCRIPTEN}>>:onetbb::onetbb>||g' \
    CuraEngine/CMakeLists.txt

# ---------------------------------------------------------------------------
# Create cmake_stubs directory with:
#   - standardprojectsettings (cmake helper macros)
#   - scripta (no-op debug-logging stubs)
#   - mapbox-wagyu (header-only, points at cloned source)
# ---------------------------------------------------------------------------
echo "==> Writing cmake stubs..."
STUBS="$BUILD_DIR/cmake_stubs"
mkdir -p "$STUBS/include/scripta"
mkdir -p "$STUBS/include/oneapi/tbb"

# oneapi/tbb/global_control.h stub — CuraEngine only uses tbb::global_control to
# limit thread count; a no-op implementation is sufficient
cat > "$STUBS/include/oneapi/tbb/global_control.h" << 'STUB'
#pragma once
namespace tbb {
    class global_control {
    public:
        enum parameter { max_allowed_parallelism };
        global_control(parameter, int) noexcept {}
    };
}
namespace oneapi { namespace tbb = ::tbb; }
STUB

# standardprojectsettings: provides cmake helper macros only
cat > "$STUBS/standardprojectsettingsConfig.cmake" << 'STUB'
macro(AssureOutOfSourceBuilds)
endmacro()
macro(use_threads target)
    find_package(Threads REQUIRED)
    target_link_libraries(${target} PUBLIC Threads::Threads)
endmacro()
macro(enable_sanitizers target)
endmacro()
macro(set_project_warnings target)
endmacro()
STUB

# scripta: header-only no-op stubs — only used for debug visualization, not slicing
cat > "$STUBS/include/scripta/logger.h" << 'STUB'
#pragma once
#include <string_view>

namespace scripta {

// Accept any accessor (member pointer or lambda) via unconstrained Accessor param.
// CTAD (C++17) deduces Accessor from the second constructor/initialiser argument.
template<typename Accessor>
struct CellVDI {
    std::string_view name;
    Accessor accessor;
};

template<typename Accessor>
struct PointVDI {
    std::string_view name;
    Accessor accessor;
};

template<typename... Args>
inline void log([[maybe_unused]] std::string_view name, Args&&...) noexcept {}

template<typename... Args>
inline void setAll(Args&&...) noexcept {}

} // namespace scripta
STUB

# scripta CMake config: creates the scripta::scripta interface target
cat > "$STUBS/scriptaConfig.cmake" << STUB
add_library(scripta::scripta INTERFACE IMPORTED)
target_include_directories(scripta::scripta INTERFACE "$STUBS/include")
STUB

# mapbox-wagyu CMake config: points at the cloned header-only sources
# Also includes mapbox/geometry.hpp which wagyu depends on for box.hpp etc.
cat > "$STUBS/mapbox-wagyuConfig.cmake" << STUB
add_library(mapbox-wagyu::mapbox-wagyu INTERFACE IMPORTED)
target_include_directories(mapbox-wagyu::mapbox-wagyu INTERFACE
    "$BUILD_DIR/wagyu/include"
    "$BUILD_DIR/mapbox_geometry/include")
STUB

# polyclipping CMake config: downloads Clipper 6.4.2 source at configure time
# and compiles it as a static library using the active (cross) compiler.
# Header layout: polyclipping_src/polyclipping/clipper.hpp
#   → consumers use <polyclipping/clipper.hpp>   (CMAKE_PREFIX_PATH/polyclipping_src/ is on the path)
#   → clipper.cpp uses #include "clipper.hpp"    (polyclipping/ dir is also on its private include path)
cat > "$STUBS/polyclippingConfig.cmake" << 'STUB'
if(TARGET polyclipping::polyclipping)
    return()
endif()
set(_POLY_INC "${CMAKE_CURRENT_LIST_DIR}/polyclipping_src")
set(_POLY_DIR "${_POLY_INC}/polyclipping")
file(MAKE_DIRECTORY "${_POLY_DIR}")
if(NOT EXISTS "${_POLY_DIR}/clipper.hpp")
    message(STATUS "Downloading Clipper 6.4.2 source...")
    foreach(_f clipper.hpp clipper.cpp)
        file(DOWNLOAD
            "https://raw.githubusercontent.com/Ultimaker/CuraEngine/4.13.2/libs/clipper/${_f}"
            "${_POLY_DIR}/${_f}" STATUS _s)
        list(GET _s 0 _code)
        if(_code)
            message(FATAL_ERROR "Failed to download ${_f} (${_s})")
        endif()
    endforeach()
endif()
add_library(polyclipping STATIC "${_POLY_DIR}/clipper.cpp")
target_include_directories(polyclipping
    PUBLIC  "${_POLY_INC}"
    PRIVATE "${_POLY_DIR}")
add_library(polyclipping::polyclipping ALIAS polyclipping)
STUB

# ---------------------------------------------------------------------------
# Simplified conanfile: ConanCenter only, no Ultimaker remote
# ---------------------------------------------------------------------------
cat > "$BUILD_DIR/CuraEngine/conanfile_android.py" << 'CONANFILE'
from conan import ConanFile
from conan.tools.cmake import CMakeToolchain, CMakeDeps, cmake_layout

class CuraEngineAndroid(ConanFile):
    name = "curaengine"
    version = "5.11.0"
    settings = "os", "compiler", "build_type", "arch"

    def configure(self):
        # All Boost usage in CuraEngine is header-only (uuid, geometry, mpl, range, unordered)
        # so skip compiling any Boost libraries — avoids ARM64 assembly build failures
        self.options["boost"].header_only = True

    def requirements(self):
        self.requires("boost/1.86.0")
        self.requires("rapidjson/cci.20230929")
        self.requires("stb/cci.20230920")
        self.requires("spdlog/1.15.1")
        self.requires("fmt/11.1.3")
        self.requires("range-v3/0.12.0")
        self.requires("zlib/1.3.1")
        self.requires("libpng/1.6.48")

    def generate(self):
        deps = CMakeDeps(self)
        deps.generate()
        tc = CMakeToolchain(self)
        tc.variables["CURA_ENGINE_VERSION"] = "5.11.0"
        tc.variables["CURA_ENGINE_HASH"] = "android"
        tc.variables["ENABLE_ARCUS"] = False
        tc.variables["ENABLE_PLUGINS"] = False
        tc.variables["ENABLE_TESTING"] = False
        tc.variables["ENABLE_BENCHMARKS"] = False
        tc.variables["EXTENSIVE_WARNINGS"] = False
        tc.variables["ENABLE_THREADING"] = True
        tc.generate()

    def layout(self):
        cmake_layout(self)
CONANFILE

# ---------------------------------------------------------------------------
# Conan install (ConanCenter only — all standard packages)
# ---------------------------------------------------------------------------
echo "==> Installing dependencies via Conan (first run may take 30-60 min)..."
# Boost's b2 build system looks for 'clang++' on PATH; point it at the NDK's binary
export PATH="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
cd "$BUILD_DIR/CuraEngine"
mkdir -p build_android
conan install conanfile_android.py \
    --profile:build=linux-x86_64 \
    --profile:host=android-arm64 \
    --build=missing \
    -of build_android

# ---------------------------------------------------------------------------
# CMake configure + build
# ---------------------------------------------------------------------------
echo "==> Configuring CMake..."
# Conan 2 cmake_layout() puts generators inside build_android/build/Release/generators/
CONAN_GEN="$BUILD_DIR/CuraEngine/build_android/build/Release/generators"
CMAKE_BUILD="$BUILD_DIR/CuraEngine/build_android/build/Release"
mkdir -p "$CMAKE_BUILD"

cmake "$BUILD_DIR/CuraEngine" \
    -G Ninja \
    -B "$CMAKE_BUILD" \
    -DCMAKE_TOOLCHAIN_FILE="$CONAN_GEN/conan_toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_PREFIX_PATH="$STUBS" \
    -DENABLE_ARCUS=OFF \
    -DENABLE_PLUGINS=OFF \
    -DBUILD_TESTS=OFF \
    -DENABLE_BENCHMARKS=OFF \
    -DEXTENSIVE_WARNINGS=OFF \
    -DCMAKE_EXE_LINKER_FLAGS="-pie -Wl,-z,max-page-size=16384" \
    -DCMAKE_HAVE_LIBC_PTHREAD=ON \
    -DTHREADS_PREFER_PTHREAD_FLAG=ON \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON

echo "==> Compiling..."
ninja -j"$(nproc)" -C "$CMAKE_BUILD" CuraEngine

# ---------------------------------------------------------------------------
# Copy to jniLibs
# ---------------------------------------------------------------------------
BINARY="$CMAKE_BUILD/CuraEngine"
[ -f "$BINARY" ] || { echo "ERROR: binary not found at $BINARY"; exit 1; }

cp "$BINARY" "$OUTPUT_DIR/libCuraEngine.so"
chmod 755 "$OUTPUT_DIR/libCuraEngine.so"

LIBOMP="$(find "$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64" -name "libomp.so" | grep "aarch64" | grep -v "libomp5")"
if [ -n "$LIBOMP" ]; then
    cp "$LIBOMP" "$OUTPUT_DIR/libomp.so"
    echo "    libomp copied"
fi

echo ""
echo "=========================================="
echo " Done!"
echo " Binary : $OUTPUT_DIR/libCuraEngine.so"
echo " Size   : $(du -sh "$OUTPUT_DIR/libCuraEngine.so" | cut -f1)"
echo "=========================================="
echo " Sync project in Android Studio and run the app."
