#!/usr/bin/env bash
# Cross-compile CuraEngine 4.13.1 for Android arm64-v8a using the Android NDK.
# Run this script from the project root inside WSL2:
#   bash scripts/build_cura_android.sh
#
# Prerequisites (install inside WSL2):
#   sudo apt-get install -y cmake ninja-build git unzip build-essential

set -euo pipefail

CURA_VERSION="4.13.1"
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
        "$HOME/Android/Sdk/ndk/27.0.12077973"
        "$HOME/Android/Sdk/ndk/26.1.10909125"
        "$HOME/Android/Sdk/ndk/25.2.9519653"
    )
    # Also search the Windows host via /mnt/c (WSL2)
    local win_ndk_base="/mnt/c/Users"
    if [ -d "$win_ndk_base" ]; then
        while IFS= read -r p; do
            candidates+=("$p")
        done < <(find "$win_ndk_base" -maxdepth 6 \
                      -name "android.toolchain.cmake" 2>/dev/null \
                      -printf "%h\n" | sed 's|/build/cmake||' | head -5)
    fi

    for p in "${candidates[@]}"; do
        [ -z "$p" ] && continue
        if [ -f "$p/build/cmake/android.toolchain.cmake" ]; then
            echo "$p"
            return 0
        fi
    done
    return 1
}

echo "==> Locating Android NDK..."
NDK_PATH="$(find_ndk)" || {
    echo ""
    echo "ERROR: Could not find the Android NDK."
    echo "  Option 1: Install inside WSL2:"
    echo "    wget https://dl.google.com/android/repository/android-ndk-r27c-linux.zip"
    echo "    unzip android-ndk-r27c-linux.zip -d ~/Android/Sdk/ndk/"
    echo "    export ANDROID_NDK_HOME=~/Android/Sdk/ndk/android-ndk-r27c"
    echo "  Option 2: Set ANDROID_NDK_HOME to your existing NDK path and rerun."
    exit 1
}
echo "    NDK: $NDK_PATH"
TOOLCHAIN="$NDK_PATH/build/cmake/android.toolchain.cmake"

# ---------------------------------------------------------------------------
# Clone CuraEngine
# ---------------------------------------------------------------------------
mkdir -p "$BUILD_DIR" "$OUTPUT_DIR"

# Download Cura base settings if not already present
SETTINGS_DIR="$PROJECT_ROOT/app/src/main/assets/cura_settings"
mkdir -p "$SETTINGS_DIR"
if [ ! -f "$SETTINGS_DIR/fdmprinter.def.json" ]; then
    echo "==> Downloading fdmprinter.def.json (base settings)..."
    curl -s "https://raw.githubusercontent.com/Ultimaker/Cura/$CURA_VERSION/resources/definitions/fdmprinter.def.json" \
         -o "$SETTINGS_DIR/fdmprinter.def.json"
fi

if [ ! -f "$SETTINGS_DIR/fdmextruder.def.json" ]; then
    echo "==> Downloading fdmextruder.def.json (extruder settings)..."
    curl -s "https://raw.githubusercontent.com/Ultimaker/Cura/$CURA_VERSION/resources/definitions/fdmextruder.def.json" \
         -o "$SETTINGS_DIR/fdmextruder.def.json"
fi

cd "$BUILD_DIR"

if [ ! -d "CuraEngine" ]; then
    echo "==> Cloning CuraEngine $CURA_VERSION..."
    git clone --depth=1 --branch "$CURA_VERSION" \
        https://github.com/Ultimaker/CuraEngine.git
else
    echo "==> CuraEngine already cloned, skipping."
fi

# ---------------------------------------------------------------------------
# Patch: remove -lpthread (Android libc includes pthread; no separate lib)
# ---------------------------------------------------------------------------
echo "==> Patching CMakeLists.txt for Android (removing -lpthread)..."
sed -i 's/\bpthread\b//g' CuraEngine/CMakeLists.txt

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
echo "==> Configuring CMake..."
mkdir -p CuraEngine/build_android
cd CuraEngine/build_android

cmake .. \
    -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$API_LEVEL" \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DENABLE_ARCUS=OFF \
    -DBUILD_TESTS=OFF \
    -DCMAKE_CXX_STANDARD=17 \
    -DCMAKE_CXX_FLAGS="-O2 -fPIE -fvisibility=hidden" \
    -DCMAKE_EXE_LINKER_FLAGS="-pie -static-libstdc++"

echo "==> Compiling (this takes a few minutes)..."
ninja -j"$(nproc)" CuraEngine

# ---------------------------------------------------------------------------
# Copy output
# ---------------------------------------------------------------------------
BINARY="$BUILD_DIR/CuraEngine/build_android/CuraEngine"
if [ ! -f "$BINARY" ]; then
    echo "ERROR: Build succeeded but binary not found at $BINARY"
    exit 1
fi

cp "$BINARY" "$OUTPUT_DIR/libCuraEngine.so"
chmod 755 "$OUTPUT_DIR/libCuraEngine.so"

# Bundle libomp.so — CuraEngine links it dynamically and Android won't find it otherwise
LIBOMP="$(find "$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64" -name "libomp.so" | grep "aarch64" | head -1)"
if [ -n "$LIBOMP" ]; then
    cp "$LIBOMP" "$OUTPUT_DIR/libomp.so"
    echo " libomp: $OUTPUT_DIR/libomp.so"
else
    echo "WARNING: libomp.so not found in NDK — CuraEngine may fail to start on device"
fi

echo ""
echo "=========================================="
echo " Done!"
echo " Binary: $OUTPUT_DIR/libCuraEngine.so"
echo " Size:   $(du -sh "$OUTPUT_DIR/CuraEngine" | cut -f1)"
echo "=========================================="
echo " Now sync the project in Android Studio and run the app."
