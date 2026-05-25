package com.chinmay.myprinter.slicer;

import android.content.Context;
import android.util.Log;

import com.chinmay.myprinter.util.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// CuraEngine is shipped as jniLibs/arm64-v8a/libCuraEngine.so so the package
// installer extracts it to nativeLibraryDir (which is executable, unlike filesDir).

public class CuraEngineWrapper {

    public interface ProgressListener {
        void onProgress(int percent, String stage);
    }

    private static final String TAG    = "CuraEngineWrapper";
    private static final String LIB_NAME = "libCuraEngine.so";

    private final Context context;
    private final AtomicInteger progress  = new AtomicInteger(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Process process;

    public CuraEngineWrapper(Context context) {
        this.context = context.getApplicationContext();
    }

    // Returns null on success, an error string on failure.
    public String slice(String stlPath, String outputGcodePath,
                        SlicerSettings s, ProgressListener listener) {
        cancelled.set(false);
        progress.set(0);

        try {
            File binary = getBinary();
            if (binary == null) {
                return "CuraEngine binary not found.\n\n" +
                       "Run scripts/build_cura_android.sh in WSL2, then " +
                       "rebuild and reinstall the app.";
            }

            File fdmPrinter = extractAssetIfMissing("cura_settings/fdmprinter.def.json",
                                                   "cura_settings/fdmprinter.def.json");
            if (fdmPrinter == null) return "fdmprinter.def.json not found.\nRun: curl -s https://raw.githubusercontent.com/Ultimaker/Cura/4.13.1/resources/definitions/fdmprinter.def.json -o app/src/main/assets/cura_settings/fdmprinter.def.json";

            File fdmExtruder = extractAssetIfMissing("cura_settings/fdmextruder.def.json",
                                                     "cura_settings/fdmextruder.def.json");
            if (fdmExtruder == null) return "fdmextruder.def.json not found.\nRun: curl -s https://raw.githubusercontent.com/Ultimaker/Cura/4.13.1/resources/definitions/fdmextruder.def.json -o app/src/main/assets/cura_settings/fdmextruder.def.json";

            File machineSettings = extractAssetIfMissing("cura_settings/ender3.def.json",
                                                         "cura_settings/ender3.def.json");
            if (machineSettings == null) return "Failed to extract ender3.def.json";

            List<String> cmd = buildCommand(binary, fdmPrinter, fdmExtruder, machineSettings, stlPath, outputGcodePath, s);
            Log.d(TAG, "Running: " + cmd);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            // Tell the dynamic linker where to find libomp.so and any other
            // NDK libs bundled alongside libCuraEngine.so
            pb.environment().put("LD_LIBRARY_PATH",
                    context.getApplicationInfo().nativeLibraryDir);
            pb.environment().put("OMP_NUM_THREADS",
                    String.valueOf(Runtime.getRuntime().availableProcessors()));
            process = pb.start();

            // Read output, parse progress, and keep last lines for error reporting
            Pattern pct   = Pattern.compile("[Pp]rogress.*?(\\d+)%");
            Pattern stage = Pattern.compile("[Pp]rogress:\\s*(\\S+)");
            List<String> lastLines = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled.get()) {
                        process.destroy();
                        return "Cancelled";
                    }
                    String l = line.trim();
                    if (!l.isEmpty()) {
                        Log.d(TAG, l);
                        lastLines.add(l);
                        if (lastLines.size() > 30) lastLines.remove(0);
                        Matcher m = pct.matcher(l);
                        if (m.find()) {
                            int p = Integer.parseInt(m.group(1));
                            progress.set(p);
                            if (listener != null) {
                                Matcher sm = stage.matcher(l);
                                String stageName = sm.find() ? sm.group(1) : "slicing";
                                listener.onProgress(p, stageName);
                            }
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = String.join("\n", lastLines);
                return "CuraEngine exited with code " + exitCode + "\n\n" + output;
            }

            if (!new File(outputGcodePath).exists()) {
                return "CuraEngine finished but no G-code file was produced.";
            }

            progress.set(100);
            return null; // success

        } catch (IOException e) {
            return "IO error: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Interrupted";
        }
    }

    public void cancel() {
        cancelled.set(true);
        if (process != null) process.destroy();
    }

    public int getProgress() { return progress.get(); }

    // -----------------------------------------------------------------------

    private File getBinary() {
        // Installed by the package manager from jniLibs/arm64-v8a/libCuraEngine.so
        // into nativeLibraryDir, which is mounted executable (unlike filesDir).
        File binary = new File(context.getApplicationInfo().nativeLibraryDir, LIB_NAME);
        if (!binary.exists()) {
            Log.e(TAG, "Binary not found at " + binary.getAbsolutePath());
            return null;
        }
        Log.d(TAG, "Using CuraEngine at " + binary.getAbsolutePath());
        return binary;
    }

    private File extractAssetIfMissing(String assetPath, String destRelPath) {
        File dest = new File(context.getFilesDir(), destRelPath);
        if (dest.exists() && dest.length() > 0) return dest;
        return extractAsset(assetPath, destRelPath);
    }

    private File extractAsset(String assetPath, String destRelPath) {
        File dest = new File(context.getFilesDir(), destRelPath);
        dest.getParentFile().mkdirs();
        try (InputStream in = context.getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return dest;
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract asset " + assetPath + ": " + e.getMessage());
            return null;
        }
    }

    private List<String> buildCommand(File binary, File fdmPrinter, File fdmExtruder,
                                      File machineSettings,
                                      String stlPath, String gcodePath,
                                      SlicerSettings s) {
        List<String> cmd = new ArrayList<>();
        cmd.add(binary.getAbsolutePath());
        cmd.add("slice");
        cmd.add("-p");

        // Global: all FDM defaults, then Ender 3 machine overrides
        cmd.add("-j"); cmd.add(fdmPrinter.getAbsolutePath());
        cmd.add("-j"); cmd.add(machineSettings.getAbsolutePath());

        // User-defined start/end G-code overrides (loaded after machine settings)
        File gcodeOverride = buildGcodeOverrideJson(s);
        if (gcodeOverride != null) {
            cmd.add("-j"); cmd.add(gcodeOverride.getAbsolutePath());
        }

        // Quality settings
        cmd.add("-s"); cmd.add("layer_height="            + s.layerHeight);
        cmd.add("-s"); cmd.add("layer_height_0="          + Math.max(s.layerHeight, 0.28f));
        cmd.add("-s"); cmd.add("wall_line_count="          + s.wallCount);
        cmd.add("-s"); cmd.add("top_layers="              + topBottomLayers(s.layerHeight));
        cmd.add("-s"); cmd.add("bottom_layers="           + topBottomLayers(s.layerHeight));
        cmd.add("-s"); cmd.add("infill_sparse_density="   + s.infillPercent);
        cmd.add("-s"); cmd.add("infill_pattern=lines");

        // Temperatures
        cmd.add("-s"); cmd.add("material_print_temperature="         + (int)s.nozzleTemp);
        cmd.add("-s"); cmd.add("material_print_temperature_layer_0=" + (int)(s.nozzleTemp + 5));
        cmd.add("-s"); cmd.add("material_initial_print_temperature="  + (int)s.nozzleTemp);
        cmd.add("-s"); cmd.add("material_final_print_temperature="    + (int)s.nozzleTemp);
        cmd.add("-s"); cmd.add("material_bed_temperature="           + (int)s.bedTemp);
        cmd.add("-s"); cmd.add("material_bed_temperature_layer_0="   + (int)s.bedTemp);

        // Speeds
        cmd.add("-s"); cmd.add("speed_print="           + (int)s.printSpeed);
        cmd.add("-s"); cmd.add("speed_travel="          + (int)s.travelSpeed);
        cmd.add("-s"); cmd.add("speed_layer_0="         + "25");
        cmd.add("-s"); cmd.add("speed_print_layer_0="   + "25");
        cmd.add("-s"); cmd.add("speed_travel_layer_0="  + "100");
        cmd.add("-s"); cmd.add("speed_infill="          + (int)(s.printSpeed * 1.2f));
        cmd.add("-s"); cmd.add("speed_wall_0="          + (int)(s.printSpeed * 0.6f));
        cmd.add("-s"); cmd.add("speed_wall_x="          + (int)(s.printSpeed * 0.8f));
        cmd.add("-s"); cmd.add("speed_topbottom="       + (int)(s.printSpeed * 0.8f));

        // Retraction
        cmd.add("-s"); cmd.add("retraction_enable=true");
        cmd.add("-s"); cmd.add("retraction_amount=" + s.retractionAmount);
        cmd.add("-s"); cmd.add("retraction_speed=45");
        cmd.add("-s"); cmd.add("retraction_combing=noskin");

        // Cooling
        cmd.add("-s"); cmd.add("cool_fan_enabled=true");
        cmd.add("-s"); cmd.add("cool_fan_speed=100");
        cmd.add("-s"); cmd.add("cool_fan_speed_0=0");
        cmd.add("-s"); cmd.add("cool_min_layer_time=10");

        // Adhesion
        cmd.add("-s"); cmd.add("adhesion_type=none");

        // Supports
        cmd.add("-s"); cmd.add("support_enable=false");

        // Speed — mesh simplification (major slicer compute saving)
        cmd.add("-s"); cmd.add("meshfix_maximum_resolution=0.5");
        cmd.add("-s"); cmd.add("meshfix_maximum_deviation=0.1");

        // Skip expensive gap-fill and avoid-other-parts travel planning
        cmd.add("-s"); cmd.add("fill_outline_gaps=false");
        cmd.add("-s"); cmd.add("travel_avoid_other_parts=false");
        cmd.add("-s"); cmd.add("travel_avoid_supports=false");

        // Seam: back wall is fastest to compute (no corner analysis)
        cmd.add("-s"); cmd.add("z_seam_type=back");

        // Extruder 0: base extruder defaults, then model
        cmd.add("-e0");
        cmd.add("-j"); cmd.add(fdmExtruder.getAbsolutePath());
        cmd.add("-l"); cmd.add(stlPath);
        cmd.add("-o"); cmd.add(gcodePath);

        return cmd;
    }

    private int topBottomLayers(float layerHeight) {
        // Aim for ~1mm of top/bottom solid layers
        return Math.max(4, (int)Math.ceil(1.0f / layerHeight));
    }

    private File buildGcodeOverrideJson(SlicerSettings s) {
        PreferenceManager pm = new PreferenceManager(context);
        String startGcode = pm.getStartGcode();
        String endGcode = pm.getEndGcode();

        int nozzleTempLayer0 = (int)(s.nozzleTemp + 5);

        if (!hasTemperatureControl(startGcode)) {
            startGcode = "M190 S" + (int)s.bedTemp + "\n" +
                         "M109 S" + nozzleTempLayer0 + "\n" +
                         startGcode;
        }

        startGcode = substituteGcodeMacros(startGcode, s, nozzleTempLayer0);
        endGcode   = substituteGcodeMacros(endGcode,   s, nozzleTempLayer0);

        String json = "{\n" +
            "  \"name\": \"CustomGCode\",\n" +
            "  \"version\": 2,\n" +
            "  \"metadata\": {\"type\": \"machine\", \"extends\": \"fdmprinter\"},\n" +
            "  \"overrides\": {\n" +
            "    \"machine_start_gcode\": {\"default_value\": \"" + escapeJsonString(startGcode) + "\"},\n" +
            "    \"machine_end_gcode\":   {\"default_value\": \"" + escapeJsonString(endGcode)   + "\"}\n" +
            "  }\n" +
            "}";

        File dest = new File(context.getFilesDir(), "cura_settings/custom_gcode.def.json");
        dest.getParentFile().mkdirs();
        try (java.io.OutputStream out = new FileOutputStream(dest)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            return dest;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write custom G-code JSON: " + e.getMessage());
            return null;
        }
    }

    private String substituteGcodeMacros(String template, SlicerSettings s, int nozzleTempLayer0) {
        return template
            .replace("{nozzle_temp_layer_0}", String.valueOf(nozzleTempLayer0))
            .replace("{nozzle_temp}",         String.valueOf((int)s.nozzleTemp))
            .replace("{bed_temp_layer_0}",    String.valueOf((int)s.bedTemp))
            .replace("{bed_temp}",            String.valueOf((int)s.bedTemp));
    }

    private boolean hasTemperatureControl(String gcodeTemplate) {
        return gcodeTemplate.contains("{nozzle_temp") ||
               gcodeTemplate.contains("{bed_temp")    ||
               gcodeTemplate.contains("M109")         ||
               gcodeTemplate.contains("M190")         ||
               gcodeTemplate.contains("M104 ")        ||
               gcodeTemplate.contains("M140 ");
    }

    private String escapeJsonString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
