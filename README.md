# My Printer

**My Printer** is an Android application for managing and monitoring 3D printers running **Klipper / Moonraker**. It combines a native on-device slicing engine with full printer control so you can go from STL to a running print without leaving your phone.

---

## Features

### On-Device Slicing
- Slices STL files directly on the device using a custom Android port of **CuraEngine 5.11.0**
- Four built-in presets: **Draft**, **Standard**, **High Detail**, **Fast #2** (gyroid infill, 300 mm/s)
- Preset settings: layer height, print/travel speeds, wall count, infill pattern & density, temperatures, retraction, Z-hop, ironing, adhesion type, seam placement
- Generates a **Moonraker-compatible G-code thumbnail** (32×32 + 300×300 PNG embedded in the file header) so Fluidd/Mainsail display a preview
- Uploads sliced G-code directly to the printer over the Moonraker API

### Printer Control & Monitoring
- Connects to **Moonraker** via REST + WebSocket for real-time updates
- Live temperature display with nozzle and bed graphs
- Full print control: start, pause, resume, cancel
- Axis homing (X / Y / Z / All) and manual jog
- Custom G-code terminal
- Print progress bar, layer counter, elapsed time, and ETA

### File Management
- Browse, search, and delete G-code files stored on the printer
- Searchable file list with Material 3 styled search bar

### Live Notifications
- **Print progress** — persistent notification with progress bar, layer info (`Layer 45/120 · 37%`), and estimated finish time. Updates silently on every status poll. Actions: **Pause / Resume** and **Cancel** (work even when the app is backgrounded)
- **Manual heating** — appears automatically when a heater target is set outside of a print. Shows current vs target temperature for nozzle and bed with a live progress bar. Title changes to **"Ready to print"** when both heaters are within 2 °C of target. Action: **Turn off** (sends `M104 S0` + `M140 S0`)
- **Print complete / error** — alerting notification with sound on job completion or error

### 3D Model Browser
- Integrated Thingiverse browser (search, grid view, model detail, image carousel)
- Download STL files and slice directly from the model detail view

---

## Technical Stack

| Layer | Technology |
|---|---|
| Language | Java (app), C++ (CuraEngine native) |
| Architecture | MVVM — `ViewModel`, `LiveData`, `ViewBinding` |
| Networking | `Retrofit` + `OkHttp` (REST), `OkHttp WebSocket` (real-time) |
| UI | Material Design 3, Navigation Component |
| Image loading | Glide |
| Local storage | Room |
| Native slicing | CuraEngine 5.11.0, cross-compiled for `arm64-v8a` via Android NDK |
| Native page size | 16 KB ELF segment alignment (`-Wl,-z,max-page-size=16384`) for Android 15+ compatibility |

---

## Building

### 1. Build CuraEngine (WSL2 or linux environment required)

```bash
bash scripts/build_cura_android.sh
```

Prerequisites inside WSL2 or your Linux environment:
```bash
sudo apt-get install -y python3-pip cmake ninja-build git build-essential pkg-config
pip3 install --user "conan>=2.7.0"
```

The script cross-compiles CuraEngine 5.11.0 for `arm64-v8a` using the Android NDK, copies `libCuraEngine.so` and `libomp.so` into `app/src/main/jniLibs/arm64-v8a/`, and downloads the required Cura settings definitions.

### 2. Open in Android Studio

Import the project and let Gradle sync. AGP automatically re-aligns all prebuilt `.so` files to 16 KB page boundaries during the APK build.

### 3. Run

Deploy to a physical device (recommended — slicing performance on emulators is poor). The app requires Android 7.0+ (API 24).

---

## Printer Compatibility

Tested with:
- **Ender 3 V1** running **Klipper + Moonraker** on an **Ubuntu laptop**

The slicer presets and default G-code are tuned for a stock Ender 3 V1 (220 × 220 × 250 mm build volume, 0.4 mm nozzle, Bowden extruder). The connection layer speaks the Moonraker JSON-RPC protocol so any Klipper printer should work.

---

## License

Licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

This project integrates CuraEngine, which is also licensed under AGPLv3. Any derivative works or distributions must comply with the terms of that license.

---

*Developed by Chinmay*
