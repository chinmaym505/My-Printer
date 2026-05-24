package com.chinmay.myprinter.ui.thingiverse.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chinmay.myprinter.R;
import com.chinmay.myprinter.data.model.thingiverse.ThingFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ThingFileAdapter extends RecyclerView.Adapter<ThingFileAdapter.FileViewHolder> {

    private List<ThingFile> files = new ArrayList<>();
    private Set<Long> selectedFileIds = new HashSet<>();
    private OnSelectionChangedListener listener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public ThingFileAdapter(OnSelectionChangedListener listener) {
        this.listener = listener;
    }

    public void setFiles(List<ThingFile> files) {
        this.files = files != null ? files : new ArrayList<>();
        selectedFileIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(0);
        }
    }

    public List<ThingFile> getSelectedFiles() {
        List<ThingFile> selected = new ArrayList<>();
        for (ThingFile file : files) {
            if (selectedFileIds.contains(file.getId())) {
                selected.add(file);
            }
        }
        return selected;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_thing_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        ThingFile file = files.get(position);
        holder.bind(file, selectedFileIds.contains(file.getId()));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    class FileViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkbox;
        private final TextView fileName;
        private final TextView fileSize;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.file_checkbox);
            fileName = itemView.findViewById(R.id.file_name);
            fileSize = itemView.findViewById(R.id.file_size);
        }

        public void bind(ThingFile file, boolean isSelected) {
            fileName.setText(file.getName());
            fileSize.setText(file.getFormattedSizeString());
            checkbox.setChecked(isSelected);

            // Handle checkbox clicks
            checkbox.setOnCheckedChangeListener(null); // Clear previous listener
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedFileIds.add(file.getId());
                } else {
                    selectedFileIds.remove(file.getId());
                }
                if (listener != null) {
                    listener.onSelectionChanged(selectedFileIds.size());
                }
            });

            // Make whole item clickable
            itemView.setOnClickListener(v -> checkbox.setChecked(!checkbox.isChecked()));
        }
    }
}
