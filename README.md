# My Printer

**My Printer** is a robust Android application designed for remote management and monitoring of 3D printers running the **Moonraker (Klipper)** API. It bridges the gap between desktop slicing and mobile convenience by providing a native on-device slicing engine alongside comprehensive printer controls.

## 🚀 Key Features

*   **Native On-Device Slicing**: Includes a custom port of `CuraEngine` to slice STL files directly on your Android device using customizable printer presets.
*   **Remote Printer Management**: Complete control over your printer, including:
    *   Homing (X, Y, Z, and All)
    *   Manual axis movement
    *   Temperature control for nozzle and heat bed
    *   Sending custom G-Code commands
*   **Real-time Monitoring**:
    *   Live print progress and estimated time remaining.
    *   WebSocket-powered real-time temperature telemetry.
    *   Layer-by-layer status updates.
*   **File Management**: Browse, upload, and manage G-Code files stored on your Moonraker instance.
*   **Visual Feedback**: Beautiful telemetry charts for temperature monitoring and status indicators.

## 🛠 Technical Stack

*   **Language**: Java / C++ (via JNI for CuraEngine)
*   **Networking**: 
    *   `Retrofit` & `OkHttp` for REST API communication.
    *   `WebSockets` for real-time printer status updates.
*   **Architecture**: MVVM with `ViewModel`, `LiveData`, and `ViewBinding`.
*   **Database**: `Room` for local persistence of printer profiles and slicer presets.
*   **UI/UX**: `Material Design 3` with `Navigation Component`.
*   **Image Loading**: `Glide` for file thumbnails.
*   **Markdown Support**: `Markwon` for rendering G-Code metadata and logs.

## ⚠️ Current Issues

*   Temp graph timing is buggy

## 📥 Installation

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/chinmaym505/My-Printer.git
    ```
2.  **CuraEngine Build**:
    The project requires a native build of `CuraEngine`. See the `scripts/build_cura_android.sh` for build instructions using the Android NDK.
3.  **Open in Android Studio**:
    Import the project and sync with Gradle.
4.  **Run**:
    Deploy to a physical device or emulator (note: slicing performance is best on physical hardware).

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. 

> **Note**: This project integrates `CuraEngine`, which is also licensed under AGPLv3. Any derivative works or distributions must comply with the terms of this license.

---
*Developed by Chinmay*
