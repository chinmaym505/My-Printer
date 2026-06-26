package com.chinmay.myprinter.ui.home;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chinmay.myprinter.BuildConfig;
import com.chinmay.myprinter.R;
import com.chinmay.myprinter.slicer.SlicerService;
import com.chinmay.myprinter.data.model.PrinterState;
import com.chinmay.myprinter.data.model.PrinterStatus;
import com.chinmay.myprinter.data.repository.PrinterRepository;
import com.chinmay.myprinter.data.source.printer.MockPrinterClient;
import com.chinmay.myprinter.data.source.printer.PrinterClient;
import com.chinmay.myprinter.data.source.printer.moonraker.MoonrakerClient;
import com.chinmay.myprinter.data.source.printer.moonraker.MoonrakerClient.TemperatureHistoryPoint;
import com.chinmay.myprinter.ui.camera.CameraView;
import com.chinmay.myprinter.ui.common.ViewModelFactory;
import com.chinmay.myprinter.ui.home.adapter.FileListAdapter;
import com.chinmay.myprinter.util.Constants;
import com.chinmay.myprinter.util.PreferenceManager;
import com.google.android.material.card.MaterialCardView;

public class HomeFragment extends Fragment {
    private static final String KEY_GRAPH_DATA_SAVED = "graph_data_saved";
    private HomeViewModel viewModel;
    private FileListAdapter fileAdapter;

    // Saved graph data
    private Bundle savedGraphData;

    // Handler for periodic UI updates
    private Handler uiUpdateHandler;
    private Runnable uiUpdateRunnable;
    private PrinterStatus latestStatus;

    // Views
    private View connectionIndicator;
    private TextView connectionStatusText;
    private TextView printerStateText;
    private TextView nozzleTempText;
    private TextView bedTempText;
    private ProgressBar nozzleTempProgress;
    private ProgressBar bedTempProgress;
    private TextView positionText;
    private MaterialCardView printProgressCard;
    private TextView printFilenameText;
    private ProgressBar printProgressBar;
    private TextView printProgressText;
    private TextView printLayerText;
    private TextView printDurationText;
    private TextView printTimeRemainingText;
    private TextView printFinishTimeText;
    private Button pausePrintButton;
    private Button cancelPrintButton;
    private View movementControlsLayout;
    private Button homeAllButton;
    private Button heatNozzleButton;
    private Button heatBedButton;
    private Button moveXPlus;
    private Button moveXMinus;
    private Button moveYPlus;
    private Button moveYMinus;
    private Button moveZPlus;
    private Button moveZMinus;
    private Button cooldownNozzleButton;
    private Button cooldownBedButton;
    private TemperatureGraphView temperatureGraph;
    private EditText fileSearchBar;
    private RecyclerView filesRecyclerView;
    private ImageView printThumbnail;
    private Button reconnectButton;
    private BroadcastReceiver filesChangedReceiver;
    private CameraView cameraView;
    private TextView cameraStatusText;
    private Button refreshCameraButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupViewModel();
        setupRecyclerView();
        setupClickListeners();
        observeViewModel();
        setupPeriodicUpdates();

        // Restore graph data if available
        if (savedGraphData != null && savedGraphData.getBoolean(KEY_GRAPH_DATA_SAVED, false)) {
            restoreGraphData(savedGraphData);
        }

        // Auto-connect based on preferences
        PreferenceManager preferenceManager = new PreferenceManager(requireContext());
        if (!viewModel.isConnected()) {
            if (BuildConfig.ENABLE_MOCK_PRINTER && preferenceManager.isUseMock()) {
                // Connect to mock printer
                viewModel.connect("mock://printer", "");
            } else if (preferenceManager.hasPrinterConfigured()) {
                // Connect to real printer
                String host = preferenceManager.getPrinterHost();
                int port = preferenceManager.getPrinterPort();
                viewModel.connect(host + ":" + port, "");
            }
        }
    }

    private void initViews(View view) {
        connectionIndicator = view.findViewById(R.id.connection_indicator);
        connectionStatusText = view.findViewById(R.id.connection_status_text);
        printerStateText = view.findViewById(R.id.printer_state_text);
        nozzleTempText = view.findViewById(R.id.nozzle_temp_text);
        bedTempText = view.findViewById(R.id.bed_temp_text);
        nozzleTempProgress = view.findViewById(R.id.nozzle_temp_progress);
        bedTempProgress = view.findViewById(R.id.bed_temp_progress);
        positionText = view.findViewById(R.id.position_text);
        printProgressCard = view.findViewById(R.id.print_progress_card);
        printFilenameText = view.findViewById(R.id.print_filename_text);
        printProgressBar = view.findViewById(R.id.print_progress_bar);
        printProgressText = view.findViewById(R.id.print_progress_text);
        printLayerText = view.findViewById(R.id.print_layer_text);
        printDurationText = view.findViewById(R.id.print_duration_text);
        printTimeRemainingText = view.findViewById(R.id.print_time_remaining_text);
        printFinishTimeText = view.findViewById(R.id.print_finish_time_text);
        pausePrintButton = view.findViewById(R.id.pause_print_button);
        cancelPrintButton = view.findViewById(R.id.cancel_print_button);
        movementControlsLayout = view.findViewById(R.id.movement_controls_layout);
        homeAllButton = view.findViewById(R.id.home_all_button);
        heatNozzleButton = view.findViewById(R.id.heat_nozzle_button);
        heatBedButton = view.findViewById(R.id.heat_bed_button);
        moveXPlus = view.findViewById(R.id.move_x_plus);
        moveXMinus = view.findViewById(R.id.move_x_minus);
        moveYPlus = view.findViewById(R.id.move_y_plus);
        moveYMinus = view.findViewById(R.id.move_y_minus);
        moveZPlus = view.findViewById(R.id.move_z_plus);
        moveZMinus = view.findViewById(R.id.move_z_minus);
        cooldownNozzleButton = view.findViewById(R.id.cooldown_nozzle_button);
        cooldownBedButton = view.findViewById(R.id.cooldown_bed_button);
        temperatureGraph = view.findViewById(R.id.temperature_graph);
        fileSearchBar = view.findViewById(R.id.file_search_bar);
        filesRecyclerView = view.findViewById(R.id.files_recycler_view);
        printThumbnail = view.findViewById(R.id.print_thumbnail);
        reconnectButton = view.findViewById(R.id.reconnect_button);
        cameraView = view.findViewById(R.id.camera_view);
        cameraStatusText = view.findViewById(R.id.camera_status_text);
        refreshCameraButton = view.findViewById(R.id.refresh_camera_button);
    }

    private void setupViewModel() {
        PreferenceManager preferenceManager = new PreferenceManager(requireContext());

        // Determine which client to use based on preferences
        PrinterClient printerClient;
        if (BuildConfig.ENABLE_MOCK_PRINTER && preferenceManager.isUseMock()) {
            printerClient = new MockPrinterClient();
        } else if (preferenceManager.hasPrinterConfigured()) {
            printerClient = new MoonrakerClient();
        } else {
            // No configuration - use mock for now
            printerClient = new MockPrinterClient();
        }

        PrinterRepository repository = new PrinterRepository(printerClient);
        ViewModelFactory factory = new ViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);
    }

    private void setupRecyclerView() {
        fileAdapter = new FileListAdapter();
        filesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        filesRecyclerView.setAdapter(fileAdapter);

        fileSearchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                fileAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        fileAdapter.setOnFileClickListener(file -> {
            viewModel.startPrint(file.getFilename());
            Toast.makeText(getContext(), "Starting print: " + file.getFilename(), Toast.LENGTH_SHORT).show();
        });

        fileAdapter.setOnFileDeleteListener(file ->
            new AlertDialog.Builder(requireContext())
                .setTitle("Delete File")
                .setMessage("Delete \"" + file.getFilename() + "\"?\nThis cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> viewModel.deleteFile(file.getFilename()))
                .setNegativeButton("Cancel", null)
                .show()
        );
    }

    private void setupClickListeners() {
        homeAllButton.setOnClickListener(v -> {
            viewModel.homeAll();
            Toast.makeText(getContext(), "Homing all axes...", Toast.LENGTH_SHORT).show();
        });

        heatNozzleButton.setOnClickListener(v -> showTemperatureDialog("Nozzle", "nozzle", Constants.DEFAULT_NOZZLE_TEMP));

        heatBedButton.setOnClickListener(v -> showTemperatureDialog("Bed", "bed", Constants.DEFAULT_BED_TEMP));

        cooldownNozzleButton.setOnClickListener(v -> {
            viewModel.setTemperature("nozzle", 0);
            Toast.makeText(getContext(), "Cooling nozzle", Toast.LENGTH_SHORT).show();
        });

        cooldownBedButton.setOnClickListener(v -> {
            viewModel.setTemperature("bed", 0);
            Toast.makeText(getContext(), "Cooling bed", Toast.LENGTH_SHORT).show();
        });

        pausePrintButton.setOnClickListener(v -> {
            if (pausePrintButton.getText().equals("Pause")) {
                viewModel.pausePrint();
                pausePrintButton.setText("Resume");
            } else {
                viewModel.resumePrint();
                pausePrintButton.setText("Pause");
            }
        });

        cancelPrintButton.setOnClickListener(v -> {
            viewModel.cancelPrint();
            Toast.makeText(getContext(), "Print cancelled", Toast.LENGTH_SHORT).show();
        });

        // Movement controls
        moveXPlus.setOnClickListener(v -> {
            viewModel.moveAxis("X", 10);
            Toast.makeText(getContext(), "Moving X+10", Toast.LENGTH_SHORT).show();
        });

        moveXMinus.setOnClickListener(v -> {
            viewModel.moveAxis("X", -10);
            Toast.makeText(getContext(), "Moving X-10", Toast.LENGTH_SHORT).show();
        });

        moveYPlus.setOnClickListener(v -> {
            viewModel.moveAxis("Y", 10);
            Toast.makeText(getContext(), "Moving Y+10", Toast.LENGTH_SHORT).show();
        });

        moveYMinus.setOnClickListener(v -> {
            viewModel.moveAxis("Y", -10);
            Toast.makeText(getContext(), "Moving Y-10", Toast.LENGTH_SHORT).show();
        });

        moveZPlus.setOnClickListener(v -> {
            viewModel.moveAxis("Z", 10);
            Toast.makeText(getContext(), "Moving Z+10", Toast.LENGTH_SHORT).show();
        });

        moveZMinus.setOnClickListener(v -> {
            viewModel.moveAxis("Z", -10);
            Toast.makeText(getContext(), "Moving Z-10", Toast.LENGTH_SHORT).show();
        });

        refreshCameraButton.setOnClickListener(v -> refreshCamera());

        reconnectButton.setOnClickListener(v -> {
            connectionStatusText.setText("Connecting…");
            connectionIndicator.setBackgroundResource(android.R.drawable.presence_away);
            reconnectButton.setEnabled(false);
            PreferenceManager pm = new PreferenceManager(requireContext());
            if (BuildConfig.ENABLE_MOCK_PRINTER && pm.isUseMock()) {
                viewModel.connect("mock://printer", "");
            } else if (pm.hasPrinterConfigured()) {
                viewModel.connect(pm.getPrinterHost() + ":" + pm.getPrinterPort(), "");
            } else {
                Toast.makeText(getContext(), "No printer configured in Settings", Toast.LENGTH_SHORT).show();
                reconnectButton.setEnabled(true);
            }
        });
    }

    private void observeViewModel() {
        viewModel.getPrinterStatus().observe(getViewLifecycleOwner(), this::updateStatus);
        viewModel.getFiles().observe(getViewLifecycleOwner(), files -> {
            fileAdapter.setFiles(files);
        });
        viewModel.getConnectionStatus().observe(getViewLifecycleOwner(), connected -> {
            if (connected) {
                connectionStatusText.setText("Connected");
                connectionIndicator.setBackgroundResource(android.R.drawable.presence_online);
                reconnectButton.setVisibility(View.GONE);
                reconnectButton.setEnabled(true);
                setControlsEnabled(true);
                viewModel.refreshFiles();
            } else {
                connectionStatusText.setText("Disconnected");
                connectionIndicator.setBackgroundResource(android.R.drawable.presence_offline);
                reconnectButton.setVisibility(View.VISIBLE);
                reconnectButton.setEnabled(true);
                setControlsEnabled(false);
                fileAdapter.setFiles(new java.util.ArrayList<>());
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getTemperatureHistory().observe(getViewLifecycleOwner(), this::populateTemperatureHistory);
        viewModel.getThumbnailUrl().observe(getViewLifecycleOwner(), url -> {
            if (url != null && !url.isEmpty() && printThumbnail != null) {
                Glide.with(this).load(url).into(printThumbnail);
                printThumbnail.setVisibility(View.VISIBLE);
            }
        });
    }

    private void populateTemperatureHistory(java.util.List<TemperatureHistoryPoint> history) {
        if (history == null || history.isEmpty() || temperatureGraph == null) return;

        // Only keep the last 720 points (12 min at 1 Hz) so the first live addDataPoint()
        // never causes a massive trim that wipes the graph.
        int start = Math.max(0, history.size() - 720);
        int size  = history.size() - start;

        ArrayList<Float> nozzleTemps   = new ArrayList<>(size);
        ArrayList<Float> nozzleTargets = new ArrayList<>(size);
        ArrayList<Float> bedTemps      = new ArrayList<>(size);
        ArrayList<Float> bedTargets    = new ArrayList<>(size);
        ArrayList<Long>  timestamps    = new ArrayList<>(size);

        for (int i = start; i < history.size(); i++) {
            TemperatureHistoryPoint point = history.get(i);
            nozzleTemps.add(point.nozzleTemp);
            nozzleTargets.add(point.nozzleTarget);
            bedTemps.add(point.bedTemp);
            bedTargets.add(point.bedTarget);
            timestamps.add(point.timestamp);
        }

        temperatureGraph.restoreData(nozzleTemps, nozzleTargets, bedTemps, bedTargets, timestamps);
    }

    private void updateStatus(PrinterStatus status) {
        if (status == null) return;

        // Store latest status for periodic updates
        latestStatus = status;

        // Update state
        printerStateText.setText(status.getState().name());

        // Update temperatures
        int nozzleCurrent = (int) status.getNozzleTemp().getCurrent();
        int nozzleTarget = (int) status.getNozzleTemp().getTarget();
        nozzleTempText.setText(String.format("%d°C / %d°C", nozzleCurrent, nozzleTarget));
        nozzleTempProgress.setProgress(nozzleCurrent);

        int bedCurrent = (int) status.getBedTemp().getCurrent();
        int bedTarget = (int) status.getBedTemp().getTarget();
        bedTempText.setText(String.format("%d°C / %d°C", bedCurrent, bedTarget));
        bedTempProgress.setProgress(bedCurrent);

        // Update temperature graph
        temperatureGraph.addDataPoint(
                status.getNozzleTemp().getCurrent(),
                status.getNozzleTemp().getTarget(),
                status.getBedTemp().getCurrent(),
                status.getBedTemp().getTarget()
        );

        // Update position
        positionText.setText(String.format("X: %.1f  Y: %.1f  Z: %.1f",
                status.getPosition().getX(),
                status.getPosition().getY(),
                status.getPosition().getZ()));

        // Update print progress
        if (status.getState() == PrinterState.PRINTING || status.getState() == PrinterState.PAUSED) {
            printProgressCard.setVisibility(View.VISIBLE);
            movementControlsLayout.setVisibility(View.GONE); // Hide movement controls when printing

            printFilenameText.setText(status.getCurrentFilename());
            printProgressBar.setProgress(status.getPrintProgress());
            printProgressText.setText(status.getPrintProgress() + "%");

            // Only show layer info if available
            if (status.getTotalLayers() > 0) {
                printLayerText.setText(String.format("Layer %d/%d",
                        status.getCurrentLayer(), status.getTotalLayers()));
                printLayerText.setVisibility(View.VISIBLE);
            } else {
                printLayerText.setVisibility(View.GONE);
            }

            printDurationText.setText("Duration: " + status.getFormattedDuration());
            printTimeRemainingText.setText("Remaining: " + status.getFormattedTimeRemaining());

            // Format finish time with date
            String finishDate = status.getFormattedEstimatedFinishDate();
            String finishTime = status.getFormattedEstimatedFinishTime();
            if (!finishDate.isEmpty()) {
                printFinishTimeText.setText("Finish: " + finishDate + " at " + finishTime);
            } else {
                printFinishTimeText.setText("Finish: " + finishTime);
            }

            if (status.getState() == PrinterState.PAUSED) {
                pausePrintButton.setText("Resume");
            } else {
                pausePrintButton.setText("Pause");
            }

            // Start periodic UI updates if printing (not paused)
            if (status.getState() == PrinterState.PRINTING && uiUpdateHandler != null) {
                uiUpdateHandler.removeCallbacks(uiUpdateRunnable);
                uiUpdateHandler.postDelayed(uiUpdateRunnable, 1000);
            }
        } else {
            printProgressCard.setVisibility(View.GONE);
            movementControlsLayout.setVisibility(View.VISIBLE);
            if (printThumbnail != null) printThumbnail.setVisibility(View.GONE);

            // Stop periodic updates when not printing
            if (uiUpdateHandler != null) {
                uiUpdateHandler.removeCallbacks(uiUpdateRunnable);
            }
        }
    }

    private void showTemperatureDialog(String heaterName, String heaterType, int defaultTemp) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Set " + heaterName + " Temperature");

        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Temperature (°C)");
        input.setText(String.valueOf(defaultTemp));
        builder.setView(input);

        builder.setPositiveButton("Set", (dialog, which) -> {
            String tempStr = input.getText().toString();
            if (!tempStr.isEmpty()) {
                try {
                    int temp = Integer.parseInt(tempStr);
                    int maxTemp = heaterType.equals("nozzle") ? 300 : 120;

                    if (temp < 0 || temp > maxTemp) {
                        Toast.makeText(getContext(),
                                "Temperature must be between 0 and " + maxTemp + "°C",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    viewModel.setTemperature(heaterType, temp);
                    Toast.makeText(getContext(),
                            "Heating " + heaterName.toLowerCase() + " to " + temp + "°C",
                            Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), "Invalid temperature", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void refreshCamera() {
        PreferenceManager preferenceManager = new PreferenceManager(requireContext());
        String cameraUrl = preferenceManager.getCameraUrl();

        if (cameraUrl != null && !cameraUrl.isEmpty()) {
            cameraView.setStreamUrl(cameraUrl);
            cameraStatusText.setText("Camera: Connected");
        } else {
            cameraStatusText.setText("Camera: Not configured. Go to Settings.");
        }
    }

    private void setupPeriodicUpdates() {
        uiUpdateHandler = new Handler(Looper.getMainLooper());
        uiUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                // Update UI with latest status if printing
                if (latestStatus != null && latestStatus.getState() == PrinterState.PRINTING) {
                    updatePrintProgress(latestStatus);
                }
                // Schedule next update
                if (latestStatus != null && latestStatus.getState() == PrinterState.PRINTING) {
                    uiUpdateHandler.postDelayed(this, 1000); // Update every second
                }
            }
        };
    }

    private void updatePrintProgress(PrinterStatus status) {
        // Update only the time-related fields that need frequent updates
        printDurationText.setText("Duration: " + status.getFormattedDuration());
        printTimeRemainingText.setText("Remaining: " + status.getFormattedTimeRemaining());

        // Update finish time
        String finishDate = status.getFormattedEstimatedFinishDate();
        String finishTime = status.getFormattedEstimatedFinishTime();
        if (!finishDate.isEmpty()) {
            printFinishTimeText.setText("Finish: " + finishDate + " at " + finishTime);
        } else {
            printFinishTimeText.setText("Finish: " + finishTime);
        }
    }

    private void setControlsEnabled(boolean enabled) {
        heatNozzleButton.setEnabled(enabled);
        cooldownNozzleButton.setEnabled(enabled);
        heatBedButton.setEnabled(enabled);
        cooldownBedButton.setEnabled(enabled);
        homeAllButton.setEnabled(enabled);
        moveXPlus.setEnabled(enabled);
        moveXMinus.setEnabled(enabled);
        moveYPlus.setEnabled(enabled);
        moveYMinus.setEnabled(enabled);
        moveZPlus.setEnabled(enabled);
        moveZMinus.setEnabled(enabled);
        pausePrintButton.setEnabled(enabled);
        cancelPrintButton.setEnabled(enabled);
        filesRecyclerView.setAlpha(enabled ? 1.0f : 0.4f);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCamera();
        if (viewModel != null) viewModel.refreshFiles();

        filesChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (viewModel != null) viewModel.refreshFiles();
            }
        };
        IntentFilter filter = new IntentFilter(SlicerService.ACTION_FILES_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(filesChangedReceiver, filter,
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(filesChangedReceiver, filter);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (filesChangedReceiver != null) {
            try { requireContext().unregisterReceiver(filesChangedReceiver); } catch (Exception ignored) {}
            filesChangedReceiver = null;
        }

        if (temperatureGraph != null) {
            saveGraphData();
        }

        if (cameraView != null) {
            cameraView.stopStream();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Stop periodic updates and clean up handler
        if (uiUpdateHandler != null) {
            uiUpdateHandler.removeCallbacks(uiUpdateRunnable);
            uiUpdateHandler = null;
        }
    }

    private void saveGraphData() {
        savedGraphData = new Bundle();
        savedGraphData.putBoolean(KEY_GRAPH_DATA_SAVED, true);

        // Save temperature graph data as float arrays
        ArrayList<Float> nozzleTemps = new ArrayList<>(temperatureGraph.getNozzleTemps());
        ArrayList<Float> nozzleTargets = new ArrayList<>(temperatureGraph.getNozzleTargets());
        ArrayList<Float> bedTemps = new ArrayList<>(temperatureGraph.getBedTemps());
        ArrayList<Float> bedTargets = new ArrayList<>(temperatureGraph.getBedTargets());
        ArrayList<Long> timestamps = new ArrayList<>(temperatureGraph.getTimestamps());

        savedGraphData.putSerializable("nozzleTemps", nozzleTemps);
        savedGraphData.putSerializable("nozzleTargets", nozzleTargets);
        savedGraphData.putSerializable("bedTemps", bedTemps);
        savedGraphData.putSerializable("bedTargets", bedTargets);
        savedGraphData.putSerializable("timestamps", timestamps);
    }

    @SuppressWarnings("unchecked")
    private void restoreGraphData(Bundle bundle) {
        if (temperatureGraph != null) {
            ArrayList<Float> nozzleTemps = (ArrayList<Float>) bundle.getSerializable("nozzleTemps");
            ArrayList<Float> nozzleTargets = (ArrayList<Float>) bundle.getSerializable("nozzleTargets");
            ArrayList<Float> bedTemps = (ArrayList<Float>) bundle.getSerializable("bedTemps");
            ArrayList<Float> bedTargets = (ArrayList<Float>) bundle.getSerializable("bedTargets");
            ArrayList<Long> timestamps = (ArrayList<Long>) bundle.getSerializable("timestamps");

            temperatureGraph.restoreData(nozzleTemps, nozzleTargets, bedTemps, bedTargets, timestamps);
        }
    }
}
