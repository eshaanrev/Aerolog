package com.example.aerotracker.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aerotracker.R;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.PageViewHolder> {

    public static class Page {
        public final int iconRes;
        public final String title;
        public final String description;

        public Page(int iconRes, String title, String description) {
            this.iconRes = iconRes;
            this.title = title;
            this.description = description;
        }
    }

    private final Page[] pages;

    public OnboardingAdapter(Page[] pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        Page page = pages[position];
        holder.icon.setImageResource(page.iconRes);
        holder.title.setText(page.title);
        holder.description.setText(page.description);
    }

    @Override
    public int getItemCount() {
        return pages.length;
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView description;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.page_icon);
            title = itemView.findViewById(R.id.page_title);
            description = itemView.findViewById(R.id.page_description);
        }
    }
}
