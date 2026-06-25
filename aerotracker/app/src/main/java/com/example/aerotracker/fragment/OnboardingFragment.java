package com.example.aerotracker.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import com.example.aerotracker.R;
import com.example.aerotracker.adapter.OnboardingAdapter;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * First-launch walkthrough: three swipeable pages, skippable, shown only once
 * (tracked via SharedPreferences). Popping back returns to LoginFragment.
 */
public class OnboardingFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewPager2 pager = view.findViewById(R.id.onboarding_pager);
        TabLayout dots = view.findViewById(R.id.page_dots);
        Button skipBtn = view.findViewById(R.id.skip_btn);
        Button nextBtn = view.findViewById(R.id.next_btn);

        OnboardingAdapter.Page[] pages = new OnboardingAdapter.Page[]{
                new OnboardingAdapter.Page(R.drawable.ic_rocket,
                        "Log Every Flight",
                        "Aerolog is your model rocketry flight log. Record rocket, motor, altitude, "
                                + "duration, recovery and photos for every launch — then get an instant "
                                + "analysis of how the flight went."),
                new OnboardingAdapter.Page(R.drawable.ic_location,
                        "GPS & Angle Capture",
                        "When you create a launch, your phone's GPS records the exact launch site and "
                                + "elevation, and the rotation sensor measures your launch rod angle — just "
                                + "hold the phone against the rod and tap Lock Angle."),
                new OnboardingAdapter.Page(R.drawable.ic_cloud,
                        "Synced to the Cloud",
                        "Every launch is saved securely to your personal cloud account, including photos. "
                                + "Sign in on any device and your full launch history comes with you."),
        };
        pager.setAdapter(new OnboardingAdapter(pages));
        new TabLayoutMediator(dots, pager, (tab, position) -> {}).attach();

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                nextBtn.setText(position == pages.length - 1 ? "Get Started" : "Next");
            }
        });

        nextBtn.setOnClickListener(v -> {
            int current = pager.getCurrentItem();
            if (current < pages.length - 1) {
                pager.setCurrentItem(current + 1);
            } else {
                finishOnboarding(view);
            }
        });
        skipBtn.setOnClickListener(v -> finishOnboarding(view));
    }

    private void finishOnboarding(View view) {
        requireContext().getSharedPreferences(LoginFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(LoginFragment.KEY_ONBOARDING_DONE, true)
                .apply();
        Navigation.findNavController(view).popBackStack();
    }
}
