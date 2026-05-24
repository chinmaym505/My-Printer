package com.chinmay.myprinter.ui.home.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chinmay.myprinter.R;
import com.chinmay.myprinter.data.model.GCodeFile;

import java.util.ArrayList;
import java.util.List;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.FileViewHolder> {
    private List<GCodeFile> files;
    private OnFileClickListener listener;
    private OnFileDeleteListener deleteListener;

    public interface OnFileClickListener {
        void onPrintClick(GCodeFile file);
    }

    public interface OnFileDeleteListener {
        void onDeleteClick(GCodeFile file);
    }

    public FileListAdapter() {
        this.files = new ArrayList<>();
    }

    public void setOnFileClickListener(OnFileClickListener listener) {
        this.listener = listener;
    }

    public void setOnFileDeleteListener(OnFileDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void setFiles(List<GCodeFile> files) {
        this.files = files != null ? files : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gcode_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        GCodeFile file = files.get(position);
        holder.bind(file, listener, deleteListener);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        private final TextView fileNameText;
        private final TextView fileSizeText;
        private final Button printButton;
        private final Button deleteButton;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameText = itemView.findViewById(R.id.file_name_text);
            fileSizeText = itemView.findViewById(R.id.file_size_text);
            printButton = itemView.findViewById(R.id.print_button);
            deleteButton = itemView.findViewById(R.id.delete_button);
        }

        public void bind(GCodeFile file, OnFileClickListener listener,
                         OnFileDeleteListener deleteListener) {
            fileNameText.setText(file.getFilename());
            fileSizeText.setText(file.getFormattedSize());

            printButton.setOnClickListener(v -> {
                if (listener != null) listener.onPrintClick(file);
            });

            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onDeleteClick(file);
            });
        }
    }
}
