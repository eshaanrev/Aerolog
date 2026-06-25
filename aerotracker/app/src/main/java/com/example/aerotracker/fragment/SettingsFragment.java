package com.example.aerotracker.fragment;

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

import com.example.aerotracker.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Profile & settings screen: shows the signed-in account, launch count, and
 * hosts the logout action (with confirmation).
 */
public class SettingsFragment extends Fragment {

    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        TextView emailText = view.findViewById(R.id.user_email_text);
        TextView memberSinceText = view.findViewById(R.id.member_since_text);
        TextView launchCountText = view.findViewById(R.id.launch_count_text);
        Button logoutBtn = view.findViewById(R.id.logout_btn);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            emailText.setText(user.getEmail());
            if (user.getMetadata() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                memberSinceText.setText("Member since "
                        + sdf.format(new Date(user.getMetadata().getCreationTimestamp())));
            }
            FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid()).collection("launches")
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (isAdded()) launchCountText.setText(String.valueOf(snapshot.size()));
                    });
        }

        logoutBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Log out?")
                .setMessage("Your launches stay safely synced to your account.")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    mAuth.signOut();
                    Navigation.findNavController(view).navigate(R.id.action_settings_logout);
                })
                .setNegativeButton("Cancel", null)
                .show());
    }
}
