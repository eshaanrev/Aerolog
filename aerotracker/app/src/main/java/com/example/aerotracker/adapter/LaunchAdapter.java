package com.example.aerotracker.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aerotracker.R;
import com.example.aerotracker.model.Launch;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LaunchAdapter extends RecyclerView.Adapter<LaunchAdapter.LaunchViewHolder> {

    public interface OnLaunchClickListener {
        void onLaunchClick(Launch launch);
    }

    private List<Launch> launches = new ArrayList<>();
    private final OnLaunchClickListener listener;

    public LaunchAdapter(OnLaunchClickListener listener) {
        this.listener = listener;
    }

    public void setLaunches(List<Launch> launches) {
        this.launches = launches;
        notifyDataSetChanged();
    }

    public Launch getLaunchAt(int position) {
        if (position < 0 || position >= launches.size()) return null;
        return launches.get(position);
    }

    @NonNull
    @Override
    public LaunchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_launch, parent, false);
        return new LaunchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LaunchViewHolder holder, int position) {
        holder.bind(launches.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return launches.size();
    }

    static class LaunchViewHolder extends RecyclerView.ViewHolder {
        private final TextView rocketName;
        private final TextView motorType;
        private final TextView date;
        private final TextView location;
        private final TextView maxAltitude;
        private final Chip outcomeChip;
        private final View accentStrip;

        LaunchViewHolder(@NonNull View itemView) {
            super(itemView);
            rocketName = itemView.findViewById(R.id.rocket_name);
            motorType = itemView.findViewById(R.id.motor_type);
            date = itemView.findViewById(R.id.launch_date);
            location = itemView.findViewById(R.id.launch_location);
            maxAltitude = itemView.findViewById(R.id.max_altitude);
            outcomeChip = itemView.findViewById(R.id.outcome_chip);
            accentStrip = itemView.findViewById(R.id.accent_strip);
        }

        void bind(Launch launch, OnLaunchClickListener listener) {
            rocketName.setText(launch.getRocketName());
            motorType.setText(launch.getMotorType());

            if (launch.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                date.setText(sdf.format(launch.getTimestamp().toDate()));
            } else {
                date.setText("");
            }

            location.setText(launch.getLocationString());
            maxAltitude.setText(String.format(Locale.getDefault(), "%.0f ft AGL", launch.getMaxAltitude()));

            String outcome = launch.getOutcome();
            outcomeChip.setText(outcome);
            int chipColor;
            if ("Success".equals(outcome)) {
                chipColor = R.color.success_green;
            } else if ("Failed".equals(outcome)) {
                chipColor = R.color.color_primary;
            } else {
                chipColor = R.color.partial_orange;
            }
            outcomeChip.setChipBackgroundColor(
                    ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(), chipColor)));
            outcomeChip.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    "Failed".equals(outcome) ? R.color.white : R.color.black));

            accentStrip.setBackgroundColor(
                    ContextCompat.getColor(itemView.getContext(), chipColor));

            itemView.setOnClickListener(v -> listener.onLaunchClick(launch));
        }
    }
}
