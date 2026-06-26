package com.chinmay.myprinter.slicer;

import android.content.Context;
import android.util.Log;

import com.chinmay.myprinter.util.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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
    private volatile boolean shouldStripPreheat = false;

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
            if (fdmPrinter == null) return "fdmprinter.def.json not found.\nRun: curl -s https://raw.githubusercontent.com/Ultimaker/Cura/5.11.0/resources/definitions/fdmprinter.def.json -o app/src/main/assets/cura_settings/fdmprinter.def.json";

            File fdmExtruder = extractAssetIfMissing("cura_settings/fdmextruder.def.json",
                                                     "cura_settings/fdmextruder.def.json");
            if (fdmExtruder == null) return "fdmextruder.def.json not found.\nRun: curl -s https://raw.githubusercontent.com/Ultimaker/Cura/5.11.0/resources/definitions/fdmextruder.def.json -o app/src/main/assets/cura_settings/fdmextruder.def.json";

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

            if (shouldStripPreheat) stripCuraEnginePreheat(outputGcodePath);
            fixFilamentUsedHeader(outputGcodePath);

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
        if (dest.exists() && dest.length() > 0) {
            // Re-extract if the bundled asset changed size (e.g. updated def.json)
            try {
                android.content.res.AssetFileDescriptor afd = context.getAssets().openFd(assetPath);
                long assetSize = afd.getLength();
                afd.close();
                if (dest.length() == assetSize) return dest;
            } catch (IOException ignored) { }
        }
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
        cmd.add("--force-read-parent"); // load values of settings that have child-settings

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
        cmd.add("-s"); cmd.add("wall_line_count="         + s.wallCount);
        cmd.add("-s"); cmd.add("top_layers="              + topBottomLayers(s.layerHeight));
        cmd.add("-s"); cmd.add("bottom_layers="           + (s.bottomLayers > 0 ? s.bottomLayers : topBottomLayers(s.layerHeight)));
        cmd.add("-s"); cmd.add("infill_sparse_density="   + s.infillPercent);
        cmd.add("-s"); cmd.add("infill_pattern="          + s.infillPattern);
        cmd.add("-s"); cmd.add("inset_direction="         + s.insetDirection);

        // Wall line width thresholds — fdmprinter.def.json default_value=0.34mm but the Python
        // "value" expression evaluates to 0.20mm (line_width * wall_add_middle_threshold / 100
        // = 0.4 * 50/100 = 0.20).  CuraEngine never evaluates the Python expression, so it uses
        // 0.34mm and drops wall lines that desktop Cura would keep.  Those dropped lines are the
        // "holes in the walls" on narrow upper sections.  Set to match desktop Cura's value.
        cmd.add("-s"); cmd.add("min_wall_line_width=0.2");
        cmd.add("-s"); cmd.add("min_even_wall_line_width=0.2");
        cmd.add("-s"); cmd.add("min_odd_wall_line_width=0.2");
        // Transition length: Python gives 4*line_width/3 = 0.533mm; default_value=0.4mm.
        // Shorter transition = more abrupt wall-count changes = more visible seam at transitions.
        cmd.add("-s"); cmd.add("wall_transition_length=0.533");

        // Temperatures
        cmd.add("-s"); cmd.add("material_print_temperature="         + (int)s.nozzleTemp);
        cmd.add("-s"); cmd.add("material_print_temperature_layer_0=" + (int)s.nozzleTempLayer0);
        cmd.add("-s"); cmd.add("material_initial_print_temperature="  + (int)s.nozzleTempLayer0);
        cmd.add("-s"); cmd.add("material_final_print_temperature="    + (int)s.nozzleTemp);
        cmd.add("-s"); cmd.add("material_bed_temperature="           + (int)s.bedTemp);
        cmd.add("-s"); cmd.add("material_bed_temperature_layer_0="   + (int)s.bedTemp);

        // Material properties (no material profile loaded, provide defaults for PLA)
        cmd.add("-s"); cmd.add("material_shrinkage_percentage_xy=100");
        cmd.add("-s"); cmd.add("material_shrinkage_percentage_z=100");
        cmd.add("-s"); cmd.add("material_shrinkage_percentage=100");

        // Flow
        cmd.add("-s"); cmd.add("material_flow="         + s.materialFlow);
        cmd.add("-s"); cmd.add("material_flow_layer_0=" + s.materialFlowLayer0);

        // Speeds
        int wall0Speed = s.speedWall0 > 0 ? (int)s.speedWall0 : (int)(s.printSpeed * 0.6f);
        cmd.add("-s"); cmd.add("speed_print="           + (int)s.printSpeed);
        cmd.add("-s"); cmd.add("speed_travel="          + (int)s.travelSpeed);
        cmd.add("-s"); cmd.add("speed_layer_0="         + (int)s.speedLayer0);
        cmd.add("-s"); cmd.add("speed_print_layer_0="   + (int)s.speedLayer0);
        cmd.add("-s"); cmd.add("speed_travel_layer_0="  + "100");
        cmd.add("-s"); cmd.add("speed_infill="          + (int)(s.printSpeed * 1.2f));
        cmd.add("-s"); cmd.add("speed_wall_0="          + wall0Speed);
        cmd.add("-s"); cmd.add("speed_wall_x="          + (int)(s.printSpeed * 0.8f));
        cmd.add("-s"); cmd.add("speed_topbottom="       + (int)(s.printSpeed * 0.8f));

        // Retraction
        cmd.add("-s"); cmd.add("retraction_enable=true");
        cmd.add("-s"); cmd.add("retraction_amount="             + s.retractionAmount);
        cmd.add("-s"); cmd.add("retraction_speed=45");
        cmd.add("-s"); cmd.add("retraction_combing="            + s.retractionCombing);
        cmd.add("-s"); cmd.add("retraction_hop_enabled="        + s.retractionHopEnabled);
        cmd.add("-s"); cmd.add("retraction_extrusion_window="   + s.retractionExtrusionWindow);
        // Don't retract on very short travels — reduces wear and startup blobs
        cmd.add("-s"); cmd.add("retraction_min_travel=1.5");
        // Retract before the outer wall so the restart happens inside, not on the visible face
        cmd.add("-s"); cmd.add("travel_retract_before_outer_wall=true");
        // Bowden tubes leave a small pressure void after retraction; a tiny extra prime
        // compensates so the first mm of each new segment isn't under-extruded.
        cmd.add("-s"); cmd.add("retraction_extra_prime_amount=0.1");

        // Wall / skin bonding — overlap skin and infill into the perimeter walls so
        // there are no gaps between regions, especially in the upper layers where the
        // cross-section is small and each travel move has more impact per unit of wall length.
        cmd.add("-s"); cmd.add("skin_overlap=15");   // 15% of line width (default 5%)
        cmd.add("-s"); cmd.add("infill_overlap=15"); // 15% of line width (default 10%)

        // Cooling
        cmd.add("-s"); cmd.add("cool_fan_enabled=true");
        cmd.add("-s"); cmd.add("cool_fan_speed=100");
        cmd.add("-s"); cmd.add("cool_fan_speed_0=0");
        cmd.add("-s"); cmd.add("cool_min_layer_time=10");
        // fdmprinter.def.json sets cool_min_temperature via a Python "value" expression
        // ("material_print_temperature") that CuraEngine never evaluates — it falls back
        // to default_value=0, which causes M104 commands that ramp toward 0°C on fast
        // layers.  Pin it to the nozzle temp so CuraEngine never lowers temperature.
        cmd.add("-s"); cmd.add("cool_min_temperature=" + (int)s.nozzleTemp);
        // CuraEngine slows upper layers to meet cool_min_layer_time.  The default floor
        // (10 mm/s) is far too slow for a Bowden extruder — tube pressure collapses and
        // extrusion becomes inconsistent, causing holes specifically in the upper half of
        // prints where the cross-section is small enough to trigger speed reduction.
        cmd.add("-s"); cmd.add("cool_min_speed=25");

        // Surface quality
        cmd.add("-s"); cmd.add("ironing_enabled="       + s.ironingEnabled);
        cmd.add("-s"); cmd.add("ironing_monotonic="     + s.ironingMonotonic);
        cmd.add("-s"); cmd.add("roofing_layer_count="   + s.roofingLayerCount);
        cmd.add("-s"); cmd.add("roofing_material_flow=" + s.roofingMaterialFlow);

        // Adhesion
        cmd.add("-s"); cmd.add("adhesion_type="         + s.adhesionType);

        // Supports
        cmd.add("-s"); cmd.add("support_enable=false");

        // Mesh simplification
        cmd.add("-s"); cmd.add("meshfix_maximum_resolution=" + s.meshfixMaxResolution);
        cmd.add("-s"); cmd.add("meshfix_maximum_deviation=0.1");

        // Fill gaps in thin-walled regions (e.g. upper sections where geometry narrows)
        cmd.add("-s"); cmd.add("fill_outline_gaps=true");
        cmd.add("-s"); cmd.add("travel_avoid_other_parts=false");
        cmd.add("-s"); cmd.add("travel_avoid_supports=false");

        // Seam
        cmd.add("-s"); cmd.add("z_seam_type="   + s.zSeamType);
        cmd.add("-s"); cmd.add("z_seam_corner=" + s.zSeamCorner);

        // Extruder 0: base extruder defaults, then model
        cmd.add("-e0");
        cmd.add("-j"); cmd.add(fdmExtruder.getAbsolutePath());
        // fdmextruder.def.json defaults to 2.85mm filament (Ultimaker standard).
        // Override here in the extruder context so CuraEngine calculates correct E values.
        cmd.add("-s"); cmd.add("material_diameter=1.75");
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

        int nozzleTempLayer0 = (int)s.nozzleTempLayer0;

        // If the user's start G-code already handles temperature (via macros or explicit
        // M109/M190), we will strip CuraEngine's auto-generated preheat block from the output.
        shouldStripPreheat = hasTemperatureControl(startGcode);

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

    // Strip the M140/M105/M190/M104/M105/M109 block that CuraEngine writes before
    // machine_start_gcode when bed/nozzle temperatures are non-zero.  We only call
    // this when the user's start G-code already handles heating itself, so that block
    // is redundant.  Strategy: drop any preheat lines that appear before the first
    // real (non-comment, non-preheat) G-code line; everything from that line onward
    // is kept verbatim.
    private void stripCuraEnginePreheat(String gcodePath) {
        File gcodeFile = new File(gcodePath);
        if (!gcodeFile.exists()) return;

        File tmp = new File(gcodePath + ".pre.tmp");
        boolean preheatZoneDone = false;

        try (BufferedReader r = new BufferedReader(
                     new InputStreamReader(new FileInputStream(gcodeFile), StandardCharsets.UTF_8));
             PrintWriter w = new PrintWriter(
                     new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (preheatZoneDone) {
                    w.println(line);
                    continue;
                }
                String t = line.trim();
                if (t.isEmpty() || t.charAt(0) == ';') {
                    w.println(line); // header comment — keep, stay in preheat zone
                } else if (isPreheatLine(t)) {
                    // silently drop CuraEngine-generated preheat command
                } else {
                    preheatZoneDone = true; // machine_start_gcode begins here
                    w.println(line);
                }
            }
        } catch (IOException e) {
            tmp.delete();
            Log.e(TAG, "stripCuraEnginePreheat failed: " + e.getMessage());
            return;
        }

        if (!gcodeFile.delete() || !tmp.renameTo(gcodeFile)) {
            tmp.delete();
            Log.e(TAG, "stripCuraEnginePreheat: could not replace file");
        }
    }

    private static boolean isPreheatLine(String t) {
        // Strip inline comment before checking
        int semi = t.indexOf(';');
        String cmd = (semi >= 0 ? t.substring(0, semi) : t).trim();
        if (cmd.equals("T0")) return true;
        if (!cmd.startsWith("M")) return false;
        String word = cmd.contains(" ") ? cmd.substring(0, cmd.indexOf(' ')) : cmd;
        return word.equals("M140") || word.equals("M104") ||
               word.equals("M190") || word.equals("M109") ||
               word.equals("M105");
    }

    private String escapeJsonString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // CuraEngine writes ";Filament used: 0m" in the header before slicing and never
    // seeks back to update it. Walk the G-code once to find the peak E value, then
    // rewrite the file replacing that one line.
    private void fixFilamentUsedHeader(String gcodePath) {
        File gcodeFile = new File(gcodePath);
        if (!gcodeFile.exists()) return;

        float maxE = 0f;
        // Match the E parameter on any G-code word line (not inside comments)
        Pattern eVal = Pattern.compile("(?<![A-Za-z])E(-?[0-9]+(?:\\.[0-9]+)?)");

        try {
            // Pass 1: find peak positive E value
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(gcodeFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == ';') continue;
                    Matcher m = eVal.matcher(line);
                    while (m.find()) {
                        float e = Float.parseFloat(m.group(1));
                        if (e > maxE) maxE = e;
                    }
                }
            }

            if (maxE <= 0f) return;

            String fixed = String.format(Locale.US, ";Filament used: %.5fm", maxE / 1000f);

            // Pass 2: stream-rewrite so we never load the whole file into RAM
            File tmp = new File(gcodePath + ".tmp");
            try (BufferedReader r = new BufferedReader(
                         new InputStreamReader(new FileInputStream(gcodeFile), StandardCharsets.UTF_8));
                 PrintWriter w = new PrintWriter(
                         new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    w.println(line.startsWith(";Filament used:") ? fixed : line);
                }
            }

            if (!gcodeFile.delete() || !tmp.renameTo(gcodeFile)) {
                tmp.delete();
                Log.e(TAG, "fixFilamentUsedHeader: could not replace output file");
            }

        } catch (IOException e) {
            Log.e(TAG, "fixFilamentUsedHeader failed: " + e.getMessage());
        }
    }
}
