package com.chinmay.myprinter.ui.thingiverse;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.noties.markwon.Markwon;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.chinmay.myprinter.R;
import com.chinmay.myprinter.data.model.thingiverse.Thing;
import com.chinmay.myprinter.data.model.thingiverse.ThingFile;
import com.chinmay.myprinter.data.model.thingiverse.ThingImage;
import com.chinmay.myprinter.data.repository.ThingiverseRepository;
import com.chinmay.myprinter.slicer.FileDownloader;
import com.chinmay.myprinter.slicer.GCodeUploader;
import com.chinmay.myprinter.slicer.SlicerPreset;
import com.chinmay.myprinter.slicer.SlicerService;
import com.chinmay.myprinter.ui.thingiverse.adapter.ImageCarouselAdapter;
import com.chinmay.myprinter.ui.thingiverse.adapter.ThingFileAdapter;
import com.chinmay.myprinter.util.Constants;
import com.chinmay.myprinter.util.PreferenceManager;

import java.io.File;
import java.util.List;

public class ModelDetailFragment extends Fragment {

    private static final String TAG = "ModelDetailFragment";
    private static final String ARG_THING_ID = "thing_id";
    private static final String ARG_THING_NAME = "thing_name";

    private long thingId;
    private String thingName;

    private ViewPager2 imageViewPager;
    private TextView imageIndicator;
    private TextView modelTitle;
    private TextView modelCreator;
    private TextView modelLikes;
    private TextView modelDownloads;
    private TextView modelViews;
    private TextView modelDescription;
    private RecyclerView filesRecyclerView;
    private Button sliceButton;
    private ProgressBar loadingIndicator;
    private LinearProgressIndicator sliceProgressBar;
    private TextView sliceProgressText;

    private ImageCarouselAdapter imageAdapter;
    private ThingFileAdapter fileAdapter;
    private ThingiverseRepository repository;
    private String apiToken;

    private BroadcastReceiver sliceReceiver;

    public static ModelDetailFragment newInstance(long thingId, String thingName) {
        ModelDetailFragment fragment = new ModelDetailFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_THING_ID, thingId);
        args.putString(ARG_THING_NAME, thingName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        if (getArguments() != null) {
            thingId = getArguments().getLong(ARG_THING_ID);
            thingName = getArguments().getString(ARG_THING_NAME);
            Log.d(TAG, "Arguments: thingId=" + thingId + ", thingName=" + thingName);
        } else {
            Log.e(TAG, "No arguments provided!");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView called");
        try {
            View view = inflater.inflate(R.layout.fragment_model_detail, container, false);
            Log.d(TAG, "Layout inflated successfully");
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout", e);
            throw e;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        try {
            Log.d(TAG, "Initializing views...");
            initViews(view);
            Log.d(TAG, "Setting up repository...");
            setupRepository();
            Log.d(TAG, "Setting up adapters...");
            setupAdapters();
            Log.d(TAG, "Loading model details...");
            loadModelDetails();
            Log.d(TAG, "ModelDetailFragment setup complete");
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
            Toast.makeText(requireContext(), "Error loading model: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initViews(View view) {
        imageViewPager = view.findViewById(R.id.image_viewpager);
        imageIndicator = view.findViewById(R.id.image_indicator);
        modelTitle = view.findViewById(R.id.model_title);
        modelCreator = view.findViewById(R.id.model_creator);
        modelLikes = view.findViewById(R.id.model_likes);
        modelDownloads = view.findViewById(R.id.model_downloads);
        modelViews = view.findViewById(R.id.model_views);
        modelDescription = view.findViewById(R.id.model_description);
        filesRecyclerView = view.findViewById(R.id.files_recycler_view);
        sliceButton = view.findViewById(R.id.slice_button);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        sliceProgressBar = view.findViewById(R.id.slice_progress_bar);
        sliceProgressText = view.findViewById(R.id.slice_progress_text);
    }

    private void setupRepository() {
        PreferenceManager preferenceManager = new PreferenceManager(requireContext());
        apiToken = preferenceManager.getThingiverseToken();
        if (apiToken.isEmpty()) apiToken = Constants.DEFAULT_THINGIVERSE_TOKEN;
        repository = new ThingiverseRepository(apiToken);
    }

    private void setupAdapters() {
        // Image carousel adapter
        imageAdapter = new ImageCarouselAdapter();
        imageViewPager.setAdapter(imageAdapter);
        imageViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateImageIndicator(position);
            }
        });

        // File adapter
        fileAdapter = new ThingFileAdapter(selectedCount -> {
            sliceButton.setEnabled(selectedCount > 0);
            if (selectedCount > 0) {
                sliceButton.setText("Slice " + selectedCount + " File" + (selectedCount > 1 ? "s" : ""));
            } else {
                sliceButton.setText("Slice Selected Files");
            }
        });
        filesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        filesRecyclerView.setAdapter(fileAdapter);

        // Slice button
        sliceButton.setOnClickListener(v -> handleSliceFiles());
    }

    private void loadModelDetails() {
        showLoading(true);

        // Observe thing details
        repository.getThingDetails().observe(getViewLifecycleOwner(), this::displayThingDetails);

        // Observe images
        repository.getImages().observe(getViewLifecycleOwner(), this::displayImages);

        // Observe files
        repository.getFiles().observe(getViewLifecycleOwner(), this::displayFiles);

        // Observe errors
        repository.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showLoading(false);
                Log.e(TAG, "API error: " + error);
                new AlertDialog.Builder(requireContext())
                        .setTitle("API Error")
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        // Observe loading
        repository.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loading != null) {
                showLoading(loading);
            }
        });

        // Load the thing
        repository.getThingDetails(thingId);
    }

    private void displayThingDetails(Thing thing) {
        if (thing == null) return;

        modelTitle.setText(thing.getName());
        modelCreator.setText("by " + thing.getCreator().getName());
        modelLikes.setText("❤ " + formatNumber(thing.getLikeCount()));
        modelDownloads.setText("⬇ " + formatNumber(thing.getDownloadCount()));
        modelViews.setText("👁 " + formatNumber(thing.getViewCount()));

        String description = thing.getDescription();
        if (description != null && !description.isEmpty()) {
            Markwon markwon = Markwon.builder(requireContext())
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(LinkifyPlugin.create())
                    .build();
            markwon.setMarkdown(modelDescription, description);
            modelDescription.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        } else {
            modelDescription.setText("No description available.");
        }
    }

    private void displayImages(List<ThingImage> images) {
        if (images != null && !images.isEmpty()) {
            imageAdapter.setImages(images);
            updateImageIndicator(0);
        }
    }

    private void displayFiles(List<ThingFile> files) {
        if (files != null && !files.isEmpty()) {
            fileAdapter.setFiles(files);
        }
    }

    private void updateImageIndicator(int position) {
        int total = imageAdapter.getItemCount();
        if (total > 0) {
            imageIndicator.setText((position + 1) + " / " + total);
        }
    }

    private void handleSliceFiles() {
        List<ThingFile> selectedFiles = fileAdapter.getSelectedFiles();
        if (selectedFiles.isEmpty()) {
            Toast.makeText(requireContext(), "Please select files to slice", Toast.LENGTH_SHORT).show();
            return;
        }

        ThingFile file = selectedFiles.get(0);
        if (selectedFiles.size() > 1) {
            Toast.makeText(requireContext(),
                    "Slicing first selected file: " + file.getName(), Toast.LENGTH_SHORT).show();
        }

        showPresetDialog(file);
    }

    private void showPresetDialog(ThingFile file) {
        SlicerPreset[] presets = SlicerPreset.getPresets();
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++) {
            names[i] = presets[i].name + "\n" + presets[i].description;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Quality Preset")
                .setSingleChoiceItems(names, 1, null)
                .setPositiveButton("Slice", (dialog, which) -> {
                    android.widget.ListView lv = ((AlertDialog) dialog).getListView();
                    int selected = lv.getCheckedItemPosition();
                    if (selected < 0) selected = 1;
                    startDownloadAndSlice(file, presets[selected]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownloadAndSlice(ThingFile file, SlicerPreset preset) {
        String downloadUrl = file.getDownloadUrl() != null
                ? file.getDownloadUrl() : file.getPublicUrl();
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            Toast.makeText(requireContext(), "No download URL for this file", Toast.LENGTH_LONG).show();
            return;
        }

        File stlDir = new File(requireContext().getFilesDir(), "stl");
        File stlFile = new File(stlDir, file.getName());

        sliceButton.setEnabled(false);
        sliceButton.setText("Downloading…");
        showLoading(true);

        registerSliceReceiver();

        FileDownloader.download(downloadUrl, stlFile, apiToken, new FileDownloader.Callback() {
            @Override
            public void onSuccess(File downloaded) {
                requireActivity().runOnUiThread(() -> {
                    showLoading(false);
                    sliceButton.setText("Slicing…");
                    showSliceProgress(true, 0, "Starting…");
                    launchSliceService(downloaded.getAbsolutePath(), file.getName(), preset);
                });
            }

            @Override
            public void onFailure(String error) {
                requireActivity().runOnUiThread(() -> {
                    showLoading(false);
                    sliceButton.setEnabled(true);
                    sliceButton.setText("Slice Selected Files");
                    unregisterSliceReceiver();
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Download Failed")
                            .setMessage(error)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private void launchSliceService(String stlPath, String fileName, SlicerPreset preset) {
        File gcodeDir = new File(requireContext().getFilesDir(), "gcode");
        gcodeDir.mkdirs();
        String baseName = fileName.replaceFirst("[.][^.]+$", "");
        String gcodePath = new File(gcodeDir, baseName + ".gcode").getAbsolutePath();

        Intent intent = new Intent(requireContext(), SlicerService.class);
        intent.putExtra(SlicerService.EXTRA_STL_PATH,    stlPath);
        intent.putExtra(SlicerService.EXTRA_OUTPUT_PATH, gcodePath);
        intent.putExtra(SlicerService.EXTRA_FILE_NAME,   fileName);
        intent.putExtra(SlicerService.EXTRA_LAYER_HEIGHT, preset.settings.layerHeight);
        intent.putExtra(SlicerService.EXTRA_NOZZLE_DIAM,  preset.settings.nozzleDiameter);
        intent.putExtra(SlicerService.EXTRA_PRINT_SPEED,  preset.settings.printSpeed);
        intent.putExtra(SlicerService.EXTRA_TRAVEL_SPEED, preset.settings.travelSpeed);
        intent.putExtra(SlicerService.EXTRA_INFILL_PCT,   preset.settings.infillPercent);
        intent.putExtra(SlicerService.EXTRA_WALL_COUNT,   preset.settings.wallCount);
        intent.putExtra(SlicerService.EXTRA_NOZZLE_TEMP,  preset.settings.nozzleTemp);
        intent.putExtra(SlicerService.EXTRA_BED_TEMP,     preset.settings.bedTemp);
        requireContext().startForegroundService(intent);
    }

    private void registerSliceReceiver() {
        sliceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (SlicerService.ACTION_PROGRESS.equals(action)) {
                    int pct = intent.getIntExtra(SlicerService.EXTRA_PROGRESS_PCT, 0);
                    String stage = intent.getStringExtra(SlicerService.EXTRA_PROGRESS_STAGE);
                    showSliceProgress(true, pct/10000, stage != null ? stage : "slicing");
                } else if (SlicerService.ACTION_COMPLETE.equals(action)) {
                    String result   = intent.getStringExtra(SlicerService.EXTRA_RESULT);
                    String outPath  = intent.getStringExtra(SlicerService.EXTRA_OUTPUT_PATH);
                    String fileName = intent.getStringExtra(SlicerService.EXTRA_FILE_NAME);
                    unregisterSliceReceiver();
                    showLoading(false);
                    showSliceProgress(false, 0, null);
                    sliceButton.setEnabled(true);
                    sliceButton.setText("Slice Selected Files");

                    if ("OK".equals(result)) {
                        showSliceSuccess(outPath, fileName);
                    } else {
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Slicing Failed")
                                .setMessage(result)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(SlicerService.ACTION_COMPLETE);
        filter.addAction(SlicerService.ACTION_PROGRESS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(sliceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(sliceReceiver, filter);
        }
    }

    private void unregisterSliceReceiver() {
        if (sliceReceiver != null) {
            try { requireContext().unregisterReceiver(sliceReceiver); } catch (Exception ignored) {}
            sliceReceiver = null;
        }
    }

    private void showSliceProgress(boolean visible, int pct, String stage) {
        if (sliceProgressBar == null || sliceProgressText == null) return;
        sliceProgressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        sliceProgressText.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            if (pct > 0) {
                sliceProgressBar.setIndeterminate(false);
                sliceProgressBar.setProgress(pct);
                sliceProgressText.setText(pct + "% — " + stage);
            } else {
                sliceProgressBar.setIndeterminate(true);
                sliceProgressText.setText(stage);
            }
        }
    }

    private void showSliceSuccess(String gcodePath, String fileName) {
        PreferenceManager pm = new PreferenceManager(requireContext());
        AlertDialog.Builder b = new AlertDialog.Builder(requireContext())
                .setTitle("Slicing Complete!");

        if (pm.hasPrinterConfigured()) {
            b.setMessage("G-code ready: " + fileName +
                    "\n\nUpload to the printer and print now, or upload without starting?");
            b.setPositiveButton("Print Now",  (d, w) -> uploadGCode(gcodePath, true));
            b.setNegativeButton("Upload Only", (d, w) -> uploadGCode(gcodePath, false));
        } else {
            b.setMessage("Slicing complete. Configure a printer in Settings to upload.");
            b.setPositiveButton("OK", null);
        }
        b.show();
    }

    private void uploadGCode(String gcodePath, boolean startAfterUpload) {
        PreferenceManager pm = new PreferenceManager(requireContext());
        String printerUrl = "http://" + pm.getPrinterHost() + ":" + pm.getPrinterPort();

        sliceButton.setEnabled(false);
        sliceButton.setText("Uploading…");
        showSliceProgress(true, 0, "Uploading to printer…");

        GCodeUploader.upload(printerUrl, new java.io.File(gcodePath), startAfterUpload,
                new GCodeUploader.Callback() {
                    @Override
                    public void onSuccess(String filename) {
                        requireActivity().runOnUiThread(() -> {
                            showSliceProgress(false, 0, null);
                            sliceButton.setEnabled(true);
                            sliceButton.setText("Slice Selected Files");
                            String msg = startAfterUpload
                                    ? "Printing: " + filename
                                    : "Uploaded: " + filename;
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        requireActivity().runOnUiThread(() -> {
                            showSliceProgress(false, 0, null);
                            sliceButton.setEnabled(true);
                            sliceButton.setText("Slice Selected Files");
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Upload Failed")
                                    .setMessage(error)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unregisterSliceReceiver();
    }

    private void showLoading(boolean loading) {
        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String formatNumber(int number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        } else {
            return String.valueOf(number);
        }
    }
}
