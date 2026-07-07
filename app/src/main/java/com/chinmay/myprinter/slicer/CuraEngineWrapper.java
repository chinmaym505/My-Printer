package com.chinmay.myprinter.slicer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Base64;
import android.util.Log;

import com.chinmay.myprinter.util.PreferenceManager;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
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
import java.util.Arrays;
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
            injectThumbnail(stlPath, outputGcodePath);

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
        cmd.add("-s"); cmd.add("layer_height_0="          + s.initialLayerHeight);
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
        // With inset_direction=inside_out the toolhead goes inner→outer wall with no retraction
        // in between. If the inner wall is much faster than the outer wall, PA drains pressure
        // at that transition and the outer wall starts under-extruded. Cap inner walls at 2×
        // outer wall speed so the PA delta stays small (≤ 2:1 ratio is comfortable for PA).
        int wallXSpeed = Math.min((int)(s.printSpeed * 0.8f), wall0Speed * 2);
        cmd.add("-s"); cmd.add("speed_print="           + (int)s.printSpeed);
        cmd.add("-s"); cmd.add("speed_travel="          + (int)s.travelSpeed);
        cmd.add("-s"); cmd.add("speed_layer_0="         + (int)s.speedLayer0);
        cmd.add("-s"); cmd.add("speed_print_layer_0="   + (int)s.speedLayer0);
        cmd.add("-s"); cmd.add("speed_travel_layer_0="  + "100");
        cmd.add("-s"); cmd.add("speed_infill="          + (int)(s.printSpeed * 1.2f));
        cmd.add("-s"); cmd.add("speed_wall_0="          + wall0Speed);
        cmd.add("-s"); cmd.add("speed_wall_x="          + wallXSpeed);
        cmd.add("-s"); cmd.add("speed_topbottom="       + (int)(s.printSpeed * 0.8f));

        // Retract before the outer wall so the restart happens inside, not on the visible face
        cmd.add("-s"); cmd.add("travel_retract_before_outer_wall=true");
        // Wipe 0.2 mm back along the just-printed outer wall before lifting/retracting.
        // Smears the end-of-path pressure blob onto the inner surface rather than leaving
        // it as a pressure spike that bleeds into the next restart.
        cmd.add("-s"); cmd.add("outer_wall_wipe_dist=0.2");

        // Wall / skin bonding — overlap skin and infill into the perimeter walls so
        // there are no gaps between regions, especially in the upper layers where the
        // cross-section is small and each travel move has more impact per unit of wall length.
        cmd.add("-s"); cmd.add("skin_overlap=15");   // 15% of line width (default 5%)
        cmd.add("-s"); cmd.add("infill_overlap=15"); // 15% of line width (default 10%)

        // Cooling
        cmd.add("-s"); cmd.add("cool_fan_enabled=true");
        cmd.add("-s"); cmd.add("cool_fan_speed=100");
        cmd.add("-s"); cmd.add("cool_fan_speed_0=0");
        // Match Cura's default (5s). Our previous 10s was too aggressive: the Benchy hull
        // top and cabin walls triggered it while Cura's GCode showed no slowdown at the same layers.
        cmd.add("-s"); cmd.add("cool_min_layer_time=5");
        // fdmprinter.def.json sets cool_min_temperature via a Python "value" expression
        // ("material_print_temperature") that CuraEngine never evaluates — it falls back
        // to default_value=0, which causes M104 commands that ramp toward 0°C on fast
        // layers.  Pin it to the nozzle temp so CuraEngine never lowers temperature.
        cmd.add("-s"); cmd.add("cool_min_temperature=" + (int)s.nozzleTemp);
        // When layer time DOES force a slowdown, keep wall speed close to the retraction
        // prime speed (45 mm/s) so the PA delta on the first wall move after a restart
        // is small. 40 mm/s → ratio ≈ 1.1:1, vs 25 mm/s → ratio 1.8:1.
        cmd.add("-s"); cmd.add("cool_min_speed=40");

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

        // Klipper owns motion planning; CuraEngine must not bake per-segment speed ramps.
        // ender3.def.json has machine_acceleration=500 and machine_max_jerk_xy=10, which
        // cause CuraEngine to output a different F value for every wall segment based on
        // segment length. Klipper's Pressure Advance fires on each feedrate change, so
        // those varying Fs trigger PA under-extrusion precisely at wall transitions → holes.
        // Setting these to arbitrarily high values forces CuraEngine to output constant
        // target feedrates (matching Cura desktop behaviour) and lets Klipper+PA work correctly.
        cmd.add("-s"); cmd.add("machine_acceleration=100000");
        cmd.add("-s"); cmd.add("machine_max_jerk_xy=500");
        cmd.add("-s"); cmd.add("machine_max_jerk_z=10");
        cmd.add("-s"); cmd.add("machine_max_jerk_e=500");

        // Extruder 0: base extruder defaults, then model
        cmd.add("-e0");
        cmd.add("-j"); cmd.add(fdmExtruder.getAbsolutePath());
        // fdmextruder.def.json defaults to 2.85mm filament (Ultimaker standard).
        // Override here in the extruder context so CuraEngine calculates correct E values.
        cmd.add("-s"); cmd.add("material_diameter=1.75");

        // Retraction MUST be in the extruder context — CuraEngine reads these per-extruder
        // and ignores global overrides for them.  Also: retraction_retract_speed and
        // retraction_prime_speed have Python value="retraction_speed" which CuraEngine never
        // evaluates, so their default_value=25 would be used unless we set them explicitly.
        cmd.add("-s"); cmd.add("retraction_enable=true");
        cmd.add("-s"); cmd.add("retraction_amount="           + s.retractionAmount);
        cmd.add("-s"); cmd.add("retraction_speed=45");
        cmd.add("-s"); cmd.add("retraction_retract_speed=45");
        cmd.add("-s"); cmd.add("retraction_prime_speed=45");
        cmd.add("-s"); cmd.add("retraction_combing="          + s.retractionCombing);
        cmd.add("-s"); cmd.add("retraction_min_travel=1.5");
        cmd.add("-s"); cmd.add("retraction_extrusion_window=" + s.retractionExtrusionWindow);
        // Bowden tube hysteresis means the prime after retraction delivers less filament
        // than commanded. 0.4mm compensates for the slack without over-extruding on short paths.
        cmd.add("-s"); cmd.add("retraction_extra_prime_amount=0.4");
        // retraction_hop_enabled default_value=False; retraction_hop default_value=1mm.
        // Must be in extruder context to take effect.  0.2mm matches Cura Fast #2.
        cmd.add("-s"); cmd.add("retraction_hop_enabled="      + s.retractionHopEnabled);
        cmd.add("-s"); cmd.add("retraction_hop=0.2");

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

    // -----------------------------------------------------------------------
    // Thumbnail generation
    // -----------------------------------------------------------------------

    private static final class ThumbnailEntry {
        final int w, h;
        final String base64;
        ThumbnailEntry(int w, int h, String base64) { this.w = w; this.h = h; this.base64 = base64; }
    }

    private void injectThumbnail(String stlPath, String gcodePath) {
        try {
            List<float[][]> tris = parseBinaryStl(stlPath);
            if (tris.isEmpty()) { Log.d(TAG, "Thumbnail: no triangles parsed"); return; }

            ThumbnailEntry small  = encodeThumbnail(tris, 32,  32);
            ThumbnailEntry medium = encodeThumbnail(tris, 300, 300);
            prependThumbnails(gcodePath, small, medium);
            Log.d(TAG, "Thumbnail injected (32x32 + 300x300)");
        } catch (Exception e) {
            Log.e(TAG, "Thumbnail generation failed: " + e.getMessage());
        }
    }

    // Parse binary STL only (ASCII STL returns empty list).
    private List<float[][]> parseBinaryStl(String stlPath) throws IOException {
        List<float[][]> out = new ArrayList<>();
        File f = new File(stlPath);
        if (!f.exists() || f.length() < 84) return out;

        try (DataInputStream dis = new DataInputStream(
                new java.io.BufferedInputStream(new FileInputStream(f)))) {
            byte[] hdr = new byte[80];
            dis.readFully(hdr);
            // Reject ASCII STL (starts with "solid ")
            if (new String(hdr, 0, 6, StandardCharsets.US_ASCII).equalsIgnoreCase("solid ")) return out;

            int count = Integer.reverseBytes(dis.readInt());
            int max   = Math.min(count, 60_000); // cap to keep memory reasonable

            for (int i = 0; i < max; i++) {
                // 4 rows of xyz floats: [0]=normal, [1..3]=vertices
                float[][] tri = new float[4][3];
                for (int r = 0; r < 4; r++) {
                    tri[r][0] = Float.intBitsToFloat(Integer.reverseBytes(dis.readInt()));
                    tri[r][1] = Float.intBitsToFloat(Integer.reverseBytes(dis.readInt()));
                    tri[r][2] = Float.intBitsToFloat(Integer.reverseBytes(dis.readInt()));
                }
                dis.readShort(); // attribute byte count
                out.add(tri);
            }
        }
        return out;
    }

    private ThumbnailEntry encodeThumbnail(List<float[][]> tris, int w, int h) {
        Bitmap bmp = renderIsometric(tris, w, h);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 90, baos);
        bmp.recycle();
        String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        return new ThumbnailEntry(w, h, b64);
    }

    private Bitmap renderIsometric(List<float[][]> tris, int width, int height) {
        // Camera: rotate 45° around Z (vertical axis), then tilt 30° above horizontal.
        // For FDM models, Z is up, XY is the bed plane.
        double az = Math.toRadians(45), el = Math.toRadians(30);
        float cAz = (float) Math.cos(az), sAz = (float) Math.sin(az);
        float cEl = (float) Math.cos(el), sEl = (float) Math.sin(el);

        // Diffuse light direction (view space, pointing top-right-front)
        float[] light = vecNorm(new float[]{0.4f, -0.2f, 0.9f});

        int n = tris.size();

        // Find model centroid for centering
        float cx = 0, cy = 0, cz = 0;
        for (float[][] t : tris) {
            cx += t[1][0] + t[2][0] + t[3][0];
            cy += t[1][1] + t[2][1] + t[3][1];
            cz += t[1][2] + t[2][2] + t[3][2];
        }
        cx /= n * 3f; cy /= n * 3f; cz /= n * 3f;

        // Project all triangles; accumulate 2D bounding box
        float pMinX = Float.MAX_VALUE, pMaxX = -Float.MAX_VALUE;
        float pMinY = Float.MAX_VALUE, pMaxY = -Float.MAX_VALUE;

        float[][] sx = new float[n][3];  // screen X per vertex
        float[][] sy = new float[n][3];  // screen Y per vertex
        float[]   depth = new float[n];  // centroid depth for sort
        int[]     color = new int[n];

        for (int i = 0; i < n; i++) {
            float[][] t = tris.get(i);
            float dSum = 0;
            for (int v = 0; v < 3; v++) {
                float[] s = isoProject(t[v+1][0]-cx, t[v+1][1]-cy, t[v+1][2]-cz,
                                       cAz, sAz, cEl, sEl);
                sx[i][v] = s[0]; sy[i][v] = s[1];
                dSum += s[2];
                if (s[0] < pMinX) pMinX = s[0]; if (s[0] > pMaxX) pMaxX = s[0];
                if (s[1] < pMinY) pMinY = s[1]; if (s[1] > pMaxY) pMaxY = s[1];
            }
            depth[i] = dSum / 3f;

            // Transform normal for lighting
            float[] tn = isoProject(t[0][0], t[0][1], t[0][2], cAz, sAz, cEl, sEl);
            float diffuse = Math.max(0, vecDot(new float[]{tn[0], tn[1], tn[2]}, light));
            int lum = (int) (60 + 180 * (0.3f + 0.7f * diffuse));
            lum = Math.min(255, lum);
            color[i] = Color.rgb(lum, lum, lum);
        }

        // Sort back-to-front (painter's algorithm)
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Float.compare(depth[a], depth[b]));

        // Scale to fit bitmap with 8% padding
        float rangeX = pMaxX - pMinX, rangeY = pMaxY - pMinY;
        float scale  = 0.84f * Math.min(width  / (rangeX > 0 ? rangeX : 1f),
                                        height / (rangeY > 0 ? rangeY : 1f));
        float offX   = width  * 0.5f - (pMinX + pMaxX) * 0.5f * scale;
        float offY   = height * 0.5f - (pMinY + pMaxY) * 0.5f * scale;

        Bitmap bmp    = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.rgb(30, 30, 30));

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        Path path = new Path();

        for (int idx : order) {
            path.reset();
            path.moveTo(sx[idx][0] * scale + offX, sy[idx][0] * scale + offY);
            path.lineTo(sx[idx][1] * scale + offX, sy[idx][1] * scale + offY);
            path.lineTo(sx[idx][2] * scale + offX, sy[idx][2] * scale + offY);
            path.close();
            fill.setColor(color[idx]);
            canvas.drawPath(path, fill);
        }
        return bmp;
    }

    // Returns [screenX, screenY, depth] after azimuth + elevation rotation.
    private static float[] isoProject(float x, float y, float z,
                                       float cAz, float sAz, float cEl, float sEl) {
        // Rotate around Z by azimuth
        float rx = x * cAz - y * sAz;
        float ry = x * sAz + y * cAz;
        float rz = z;
        // Rotate around new X by elevation (tilts camera above horizon)
        float tx = rx;
        float ty = ry * cEl - rz * sEl;   // depth (into screen)
        float tz = ry * sEl + rz * cEl;   // vertical on screen
        return new float[]{tx, -tz, ty};  // screen: X right, Y down, depth positive = far
    }

    private static float[] vecNorm(float[] v) {
        float len = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
        if (len > 0) { v[0] /= len; v[1] /= len; v[2] /= len; }
        return v;
    }

    private static float vecDot(float[] a, float[] b) {
        return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
    }

    // Write thumbnail blocks at the top of the GCode file.
    // Moonraker scans from the beginning, so prepending is always safe.
    private void prependThumbnails(String gcodePath, ThumbnailEntry... entries) throws IOException {
        File gcodeFile = new File(gcodePath);
        File tmp       = new File(gcodePath + ".thumb.tmp");

        try (PrintWriter w = new PrintWriter(
                 new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8));
             BufferedReader r = new BufferedReader(
                 new InputStreamReader(new FileInputStream(gcodeFile), StandardCharsets.UTF_8))) {

            for (ThumbnailEntry e : entries) {
                w.printf("; thumbnail begin %dx%d %d%n", e.w, e.h, e.base64.length());
                for (int i = 0; i < e.base64.length(); i += 76) {
                    w.print("; ");
                    w.println(e.base64.substring(i, Math.min(i + 76, e.base64.length())));
                }
                w.println("; thumbnail end");
                w.println(";");
            }

            String line;
            while ((line = r.readLine()) != null) w.println(line);
        }

        if (!gcodeFile.delete() || !tmp.renameTo(gcodeFile)) {
            tmp.delete();
            Log.e(TAG, "prependThumbnails: could not replace file");
        }
    }
}
