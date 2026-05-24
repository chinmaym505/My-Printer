package com.chinmay.myprinter.ui.thingiverse;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chinmay.myprinter.R;
import com.chinmay.myprinter.data.model.thingiverse.Thing;
import com.chinmay.myprinter.data.repository.ThingiverseRepository;
import com.chinmay.myprinter.ui.thingiverse.adapter.ThingGridAdapter;
import com.chinmay.myprinter.util.Constants;
import com.chinmay.myprinter.util.PreferenceManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class ThingiverseBrowserFragment extends Fragment {

    private static final String TAG = "ThingiverseBrowser";
    private static final int PAGE = 1;
    private static final int PER_PAGE = 30;

    private SearchView searchView;
    private ChipGroup filterChips;
    private RecyclerView recyclerView;
    private ProgressBar loadingIndicator;
    private TextView errorText;

    private ThingGridAdapter adapter;
    private ThingiverseRepository repository;
    private String apiToken;

    private enum FilterType {
        POPULAR, NEWEST, FEATURED
    }

    private FilterType currentFilter = FilterType.POPULAR;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_thingiverse_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        searchView = view.findViewById(R.id.search_view);
        filterChips = view.findViewById(R.id.filter_chips);
        recyclerView = view.findViewById(R.id.models_recycler_view);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        errorText = view.findViewById(R.id.error_text);

        // Load API token from PreferenceManager, use default if not set
        PreferenceManager preferenceManager = new PreferenceManager(requireContext());
        apiToken = preferenceManager.getThingiverseToken();

        // Use default token if user hasn't set a custom one
        if (apiToken.isEmpty()) {
            apiToken = Constants.DEFAULT_THINGIVERSE_TOKEN;
        }

        // Initialize repository
        repository = new ThingiverseRepository(apiToken);

        // Set up RecyclerView
        setupRecyclerView();

        // Set up SearchView
        setupSearchView();

        // Set up filter chips
        setupFilterChips(view);

        // Observe LiveData
        observeData();

        // Load initial data
        loadData();
    }

    private void setupRecyclerView() {
        adapter = new ThingGridAdapter(thing -> {
            // Navigate to ModelDetailFragment
            Log.d(TAG, "Thing clicked: " + thing.getName() + " (ID: " + thing.getId() + ")");
            try {
                Bundle args = new Bundle();
                args.putLong("thing_id", thing.getId());
                args.putString("thing_name", thing.getName());
                Log.d(TAG, "Attempting navigation to model detail...");
                Navigation.findNavController(requireView())
                        .navigate(R.id.action_models_to_detail, args);
                Log.d(TAG, "Navigation successful!");
            } catch (Exception e) {
                Log.e(TAG, "Navigation error: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                e.printStackTrace();

                // Show error on UI thread
                requireActivity().runOnUiThread(() -> {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Navigation Error")
                            .setMessage("Error type: " + e.getClass().getSimpleName() + "\n\nMessage: " + e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });

        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerView.setAdapter(adapter);
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (!query.isEmpty()) {
                    searchThings(query);
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    private void setupFilterChips(View view) {
        Chip chipPopular = view.findViewById(R.id.chip_popular);
        Chip chipNewest = view.findViewById(R.id.chip_newest);
        Chip chipFeatured = view.findViewById(R.id.chip_featured);

        filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chip_popular) {
                    currentFilter = FilterType.POPULAR;
                } else if (checkedId == R.id.chip_newest) {
                    currentFilter = FilterType.NEWEST;
                } else if (checkedId == R.id.chip_featured) {
                    currentFilter = FilterType.FEATURED;
                }
                loadData();
            }
        });
    }

    private void observeData() {
        repository.getThings().observe(getViewLifecycleOwner(), things -> {
            if (things != null) {
                adapter.setThings(things);
                showContent();
            }
        });

        repository.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loading != null && loading) {
                showLoading();
            }
        });

        repository.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showError(error);
            }
        });
    }

    private void loadData() {
        // Clear search
        searchView.setQuery("", false);
        searchView.clearFocus();

        switch (currentFilter) {
            case POPULAR:
                repository.getPopularThings(PAGE, PER_PAGE);
                break;
            case NEWEST:
                repository.getNewestThings(PAGE, PER_PAGE);
                break;
            case FEATURED:
                repository.getFeaturedThings(PAGE, PER_PAGE);
                break;
        }
    }

    private void searchThings(String query) {
        repository.searchThings(query, PAGE, PER_PAGE);
    }

    private void showLoading() {
        loadingIndicator.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingIndicator.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loadingIndicator.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(message);
    }
}
