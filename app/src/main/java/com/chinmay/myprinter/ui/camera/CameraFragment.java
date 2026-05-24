package com.chinmay.myprinter.ui.camera;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.chinmay.myprinter.R;
import com.chinmay.myprinter.util.PreferenceManager;

public class CameraFragment extends Fragment {
    private CameraView cameraView;
    private TextView cameraStatusText;
    private PreferenceManager preferenceManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        preferenceManager = new PreferenceManager(requireContext());

        cameraView = view.findViewById(R.id.camera_view);
        cameraStatusText = view.findViewById(R.id.camera_status_text);
        Button refreshButton = view.findViewById(R.id.refresh_button);
        Button settingsButton = view.findViewById(R.id.settings_button);

        refreshButton.setOnClickListener(v -> refreshCamera());
        settingsButton.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.navigation_settings);
        });

        refreshCamera();
    }

    private void refreshCamera() {
        String cameraUrl = preferenceManager.getCameraUrl();

        if (cameraUrl != null && !cameraUrl.isEmpty()) {
            cameraView.setStreamUrl(cameraUrl);
            cameraStatusText.setText("Camera: Connected");
        } else {
            cameraStatusText.setText("Camera: Not configured. Go to Settings to set camera URL.");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (cameraView != null) {
            cameraView.stopStream();
        }
    }
}
