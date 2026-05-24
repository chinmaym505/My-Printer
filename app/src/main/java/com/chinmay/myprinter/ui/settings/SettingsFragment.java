package com.chinmay.myprinter.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.chinmay.myprinter.BuildConfig;
import com.chinmay.myprinter.R;
import com.chinmay.myprinter.data.source.printer.ConnectionCallback;
import com.chinmay.myprinter.data.source.printer.moonraker.MoonrakerClient;
import com.chinmay.myprinter.util.PreferenceManager;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsFragment extends Fragment {
    private TextInputEditText hostInput;
    private TextInputEditText portInput;
    private TextInputEditText cameraUrlInput;
    private TextInputEditText thingiverseTokenInput;
    private SwitchMaterial mockModeSwitch;
    private Button testConnectionButton;
    private Button saveSettingsButton;
    private TextView connectionStatusLabel;

    private PreferenceManager preferenceManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        preferenceManager = new PreferenceManager(requireContext());
        loadSettings();
        setupClickListeners();
    }

    private void initViews(View view) {
        hostInput = view.findViewById(R.id.printer_host_input);
        portInput = view.findViewById(R.id.printer_port_input);
        cameraUrlInput = view.findViewById(R.id.camera_url_input);
        thingiverseTokenInput = view.findViewById(R.id.thingiverse_token_input);
        mockModeSwitch = view.findViewById(R.id.mock_mode_switch);
        testConnectionButton = view.findViewById(R.id.test_connection_button);
        saveSettingsButton = view.findViewById(R.id.save_settings_button);
        connectionStatusLabel = view.findViewById(R.id.connection_status_label);

        // Hide mock mode switch in release builds
        if (!BuildConfig.ENABLE_MOCK_PRINTER) {
            mockModeSwitch.setVisibility(View.GONE);
        }
    }

    private void loadSettings() {
        String host = preferenceManager.getPrinterHost();
        int port = preferenceManager.getPrinterPort();
        String cameraUrl = preferenceManager.getCameraUrl();
        String thingiverseToken = preferenceManager.getThingiverseToken();
        boolean useMock = preferenceManager.isUseMock();

        if (host != null && !host.isEmpty()) {
            hostInput.setText(host);
        }
        portInput.setText(String.valueOf(port));

        if (cameraUrl != null && !cameraUrl.isEmpty()) {
            cameraUrlInput.setText(cameraUrl);
        }

        if (thingiverseToken != null && !thingiverseToken.isEmpty()) {
            thingiverseTokenInput.setText(thingiverseToken);
        }

        mockModeSwitch.setChecked(useMock);
    }

    private void setupClickListeners() {
        testConnectionButton.setOnClickListener(v -> testConnection());
        saveSettingsButton.setOnClickListener(v -> saveAndConnect());

        mockModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Enable/disable host and port inputs based on mock mode
            hostInput.setEnabled(!isChecked);
            portInput.setEnabled(!isChecked);
            testConnectionButton.setEnabled(!isChecked);
        });
    }

    private void testConnection() {
        String host = getHostInput();
        int port = getPortInput();

        if (host.isEmpty()) {
            Toast.makeText(getContext(), "Please enter printer host", Toast.LENGTH_SHORT).show();
            return;
        }

        connectionStatusLabel.setText("Testing connection...");
        testConnectionButton.setEnabled(false);

        MoonrakerClient client = new MoonrakerClient();
        client.connect(host + ":" + port, "", new ConnectionCallback() {
            @Override
            public void onConnected() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        connectionStatusLabel.setText("✓ Connection successful!");
                        connectionStatusLabel.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
                        testConnectionButton.setEnabled(true);
                        Toast.makeText(getContext(), "Connected successfully!", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onDisconnected() {
                // Not needed for test connection
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        connectionStatusLabel.setText("✗ Connection failed: " + error);
                        connectionStatusLabel.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                        testConnectionButton.setEnabled(true);
                        Toast.makeText(getContext(), "Connection failed: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void saveAndConnect() {
        String host = getHostInput();
        int port = getPortInput();
        String cameraUrl = getCameraUrlInput();
        String thingiverseToken = getThingiverseTokenInput();
        boolean useMock = mockModeSwitch.isChecked();

        if (!useMock && host.isEmpty()) {
            Toast.makeText(getContext(), "Please enter printer host", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save settings
        preferenceManager.setPrinterHost(host);
        preferenceManager.setPrinterPort(port);
        preferenceManager.setCameraUrl(cameraUrl);
        preferenceManager.setThingiverseToken(thingiverseToken);
        preferenceManager.setUseMock(useMock);

        Toast.makeText(getContext(), "Settings saved! Please restart to apply changes.", Toast.LENGTH_LONG).show();
    }

    private String getHostInput() {
        return hostInput.getText() != null ? hostInput.getText().toString().trim() : "";
    }

    private int getPortInput() {
        String portStr = portInput.getText() != null ? portInput.getText().toString().trim() : "7125";
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return 7125;
        }
    }

    private String getCameraUrlInput() {
        return cameraUrlInput.getText() != null ? cameraUrlInput.getText().toString().trim() : "";
    }

    private String getThingiverseTokenInput() {
        return thingiverseTokenInput.getText() != null ? thingiverseTokenInput.getText().toString().trim() : "";
    }
}
