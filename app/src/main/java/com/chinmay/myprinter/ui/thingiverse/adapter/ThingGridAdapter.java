package com.chinmay.myprinter.ui.thingiverse.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.chinmay.myprinter.R;
import com.chinmay.myprinter.data.model.thingiverse.Thing;

import java.util.ArrayList;
import java.util.List;

public class ThingGridAdapter extends RecyclerView.Adapter<ThingGridAdapter.ThingViewHolder> {

    private List<Thing> things = new ArrayList<>();
    private OnThingClickListener listener;

    public interface OnThingClickListener {
        void onThingClick(Thing thing);
    }

    public ThingGridAdapter(OnThingClickListener listener) {
        this.listener = listener;
    }

    public void setThings(List<Thing> things) {
        this.things = things != null ? things : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ThingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_thing_grid, parent, false);
        return new ThingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThingViewHolder holder, int position) {
        Thing thing = things.get(position);
        holder.bind(thing, listener);
    }

    @Override
    public int getItemCount() {
        return things.size();
    }

    static class ThingViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final TextView name;
        private final TextView creator;
        private final TextView likes;
        private final TextView downloads;

        public ThingViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thing_thumbnail);
            name = itemView.findViewById(R.id.thing_name);
            creator = itemView.findViewById(R.id.thing_creator);
            likes = itemView.findViewById(R.id.thing_likes);
            downloads = itemView.findViewById(R.id.thing_downloads);
        }

        public void bind(Thing thing, OnThingClickListener listener) {
            name.setText(thing.getName());
            creator.setText("by " + thing.getCreator().getName());
            likes.setText("❤ " + formatNumber(thing.getLikeCount()));
            downloads.setText("⬇ " + formatNumber(thing.getDownloadCount()));

            // Load thumbnail with Glide
            Glide.with(itemView.getContext())
                    .load(thing.getThumbnail())
                    .centerCrop()
                    .into(thumbnail);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onThingClick(thing);
                }
            });
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
}
