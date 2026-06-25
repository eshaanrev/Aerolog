package com.example.aerotracker.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.aerotracker.R;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

import android.widget.TextView;

public class LoginFragment extends Fragment {

    public static final String PREFS_NAME = "aerotracker_prefs";
    public static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private FirebaseAuth mAuth;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailField;
    private TextInputEditText passwordField;
    private TextView authErrorText;
    private CircularProgressIndicator authProgress;
    private Button signInBtn;
    private Button registerBtn;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            view.post(() -> Navigation.findNavController(view)
                    .navigate(R.id.action_login_to_onboarding));
            return;
        }

        if (mAuth.getCurrentUser() != null) {
            view.post(() -> Navigation.findNavController(view)
                    .navigate(R.id.action_login_to_history));
            return;
        }

        emailLayout = view.findViewById(R.id.email_layout);
        passwordLayout = view.findViewById(R.id.password_layout);
        emailField = view.findViewById(R.id.email_field);
        passwordField = view.findViewById(R.id.password_field);
        authErrorText = view.findViewById(R.id.auth_error_text);
        authProgress = view.findViewById(R.id.auth_progress);
        signInBtn = view.findViewById(R.id.sign_in_btn);
        registerBtn = view.findViewById(R.id.register_btn);

        signInBtn.setOnClickListener(v -> attemptAuth(view, true));
        registerBtn.setOnClickListener(v -> attemptAuth(view, false));
    }

    private void attemptAuth(View view, boolean isSignIn) {
        clearErrors();

        String email = emailField.getText() != null ? emailField.getText().toString().trim() : "";
        String password = passwordField.getText() != null ? passwordField.getText().toString().trim() : "";

        boolean valid = true;
        if (email.isEmpty()) {
            emailLayout.setError("Email is required");
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError("Enter a valid email address");
            valid = false;
        }
        if (password.isEmpty()) {
            passwordLayout.setError("Password is required");
            valid = false;
        } else if (!isSignIn && password.length() < 6) {
            passwordLayout.setError("Password must be at least 6 characters");
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        if (isSignIn) {
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        setLoading(false);
                        Navigation.findNavController(view).navigate(R.id.action_login_to_history);
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        showAuthError(e, true);
                    });
        } else {
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        setLoading(false);
                        Navigation.findNavController(view).navigate(R.id.action_login_to_history);
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        showAuthError(e, false);
                    });
        }
    }

    /** Maps Firebase auth failures to inline errors under the relevant field. */
    private void showAuthError(Exception e, boolean isSignIn) {
        if (e instanceof FirebaseAuthWeakPasswordException) {
            passwordLayout.setError("Password is too weak — use at least 6 characters");
        } else if (e instanceof FirebaseAuthInvalidUserException) {
            emailLayout.setError("No account found with this email");
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            emailLayout.setError("An account already exists with this email");
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            // Covers both malformed email and wrong password; Firebase no longer
            // distinguishes them for sign-in, so show it under the password field.
            if (isSignIn) {
                passwordLayout.setError("Incorrect email or password");
            } else {
                emailLayout.setError("Enter a valid email address");
            }
        } else {
            authErrorText.setText("Could not connect — check your internet connection and try again.");
            authErrorText.setVisibility(View.VISIBLE);
        }
    }

    private void clearErrors() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        authErrorText.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        authProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        signInBtn.setEnabled(!loading);
        registerBtn.setEnabled(!loading);
    }
}
