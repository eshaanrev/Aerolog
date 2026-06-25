package com.example.aerotracker.fragment;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.aerotracker.R;
import com.example.aerotracker.analysis.AnalysisResult;
import com.example.aerotracker.analysis.Finding;
import com.example.aerotracker.analysis.LaunchAnalyzer;
import com.example.aerotracker.model.Launch;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.chip.Chip;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Doubles as the New Launch form (editable) and the Launch Detail screen
 * (read-only) depending on the {@code isEditable}/{@code launchId} nav args.
 *
 * In detail mode it renders the stored {@link LaunchAnalyzer} verdict; on save
 * it captures GPS + launch angle, validates input inline, runs the analyzer,
 * and stores the launch and its analysis in Firestore.
 */
public class NewLaunchFragment extends Fragment implements SensorEventListener {

    // Result key used to flash a success/deleted Snackbar on the history screen.
    static final String RESULT_LAUNCH_MESSAGE = "launch_result_message";
    private static final String KEY_NEW_LAUNCH_HINTS = "new_launch_hints_shown";

    private boolean isEditable;
    private String launchId;

    private FusedLocationProviderClient fusedLocationClient;
    private SensorManager sensorManager;
    private Sensor rotationSensor;

    private double latitude;
    private double longitude;
    private double elevation;
    private double launchAngle;
    private String locationString = "";
    private String existingPhotoUrl = "";
    // The photo chosen for this launch (from camera or gallery). Held at field
    // scope so it's still valid when Save is tapped, not just inside the picker
    // callback.
    private Uri selectedPhotoUri;
    // FileProvider URI handed to the camera; promoted to selectedPhotoUri only
    // after the capture succeeds and the file actually has content.
    private Uri pendingCameraUri;
    private boolean measuringAngle = false;

    private TextView latitudeText;
    private TextView longitudeText;
    private TextView elevationText;
    private TextView angleText;
    private TextInputLayout rocketNameLayout;
    private TextInputLayout maxAltitudeLayout;
    private TextInputEditText rocketNameEdit;
    private TextInputEditText motorTypeEdit;
    private TextInputEditText weatherNotesEdit;
    private TextInputEditText maxAltitudeEdit;
    private TextInputEditText flightDurationEdit;
    private TextInputEditText recoveryConditionEdit;
    private TextInputEditText notesEdit;
    private RadioGroup outcomeGroup;
    private RadioButton successRadio;
    private RadioButton partialRadio;
    private RadioButton failedRadio;
    private Button measureAngleBtn;
    private Button addPhotoBtn;
    private ImageView photoPreview;

    // GPS feedback
    private TextView gpsHint;
    private LinearProgressIndicator gpsProgress;
    private TextView gpsStatusText;
    private Button grantLocationBtn;

    // Angle feedback
    private TextView angleHint;
    private TextView angleStatusText;

    // Photo hint
    private TextView photoHint;

    // Analysis card
    private MaterialCardView analysisCard;
    private Chip verdictChip;
    private TextView analysisNominalText;
    private ViewGroup findingsContainer;

    // Saving overlay
    private View savingOverlay;
    private TextView savingText;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> photoPickerLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            isEditable = getArguments().getBoolean("isEditable", true);
            launchId = getArguments().getString("launchId", "");
        }

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        sensorManager = (SensorManager) requireActivity()
                .getSystemService(android.content.Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                        fetchLocation();
                    } else {
                        showLocationDenied();
                    }
                });

        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedPhotoUri = uri;
                        photoPreview.setVisibility(View.VISIBLE);
                        Glide.with(this).load(uri).into(photoPreview);
                    }
                });

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (Boolean.TRUE.equals(success) && pendingCameraUri != null
                            && photoFileHasContent(pendingCameraUri)) {
                        selectedPhotoUri = pendingCameraUri;
                        photoPreview.setVisibility(View.VISIBLE);
                        Glide.with(this).load(selectedPhotoUri).into(photoPreview);
                    } else {
                        // Capture cancelled or the file came back empty — discard it
                        // so we never try to upload a zero-byte image.
                        if (Boolean.TRUE.equals(success) && getView() != null) {
                            Snackbar.make(getView(),
                                    "The photo didn't save correctly — please try again.",
                                    Snackbar.LENGTH_LONG).show();
                        }
                        pendingCameraUri = null;
                    }
                });

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (Boolean.TRUE.equals(granted)) {
                        launchCamera();
                    } else if (getView() != null) {
                        Snackbar.make(getView(), "Camera permission is needed to take a photo. "
                                + "You can choose one from your gallery instead.",
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_new_launch, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupMenu();

        boolean creatingNew = isEditable && (launchId == null || launchId.isEmpty());
        if (creatingNew) {
            maybeShowFirstVisitHints();
            prepareAngleCapture();
            requestLocationAndFetch();
        } else {
            setFieldsReadOnly();
            measureAngleBtn.setVisibility(View.GONE);
            addPhotoBtn.setVisibility(View.GONE);
            if (launchId != null && !launchId.isEmpty()) loadLaunchData();
        }

        measureAngleBtn.setOnClickListener(v -> {
            if (measuringAngle) {
                stopMeasuringAngle();
            } else {
                startMeasuringAngle();
            }
        });

        addPhotoBtn.setOnClickListener(v -> showPhotoSourceChooser());
        grantLocationBtn.setOnClickListener(v -> requestLocationAndFetch());
    }

    private void bindViews(View view) {
        latitudeText = view.findViewById(R.id.latitude_text);
        longitudeText = view.findViewById(R.id.longitude_text);
        elevationText = view.findViewById(R.id.elevation_text);
        angleText = view.findViewById(R.id.angle_text);
        rocketNameLayout = view.findViewById(R.id.rocket_name_layout);
        maxAltitudeLayout = view.findViewById(R.id.max_altitude_layout);
        rocketNameEdit = view.findViewById(R.id.rocket_name_edit);
        motorTypeEdit = view.findViewById(R.id.motor_type_edit);
        weatherNotesEdit = view.findViewById(R.id.weather_notes_edit);
        maxAltitudeEdit = view.findViewById(R.id.max_altitude_edit);
        flightDurationEdit = view.findViewById(R.id.flight_duration_edit);
        recoveryConditionEdit = view.findViewById(R.id.recovery_condition_edit);
        notesEdit = view.findViewById(R.id.notes_edit);
        outcomeGroup = view.findViewById(R.id.outcome_group);
        successRadio = view.findViewById(R.id.success_radio);
        partialRadio = view.findViewById(R.id.partial_radio);
        failedRadio = view.findViewById(R.id.failed_radio);
        measureAngleBtn = view.findViewById(R.id.measure_angle_btn);
        addPhotoBtn = view.findViewById(R.id.add_photo_btn);
        photoPreview = view.findViewById(R.id.photo_preview);

        gpsHint = view.findViewById(R.id.gps_hint);
        gpsProgress = view.findViewById(R.id.gps_progress);
        gpsStatusText = view.findViewById(R.id.gps_status_text);
        grantLocationBtn = view.findViewById(R.id.grant_location_btn);

        angleHint = view.findViewById(R.id.angle_hint);
        angleStatusText = view.findViewById(R.id.angle_status_text);

        photoHint = view.findViewById(R.id.photo_hint);

        analysisCard = view.findViewById(R.id.analysis_card);
        verdictChip = view.findViewById(R.id.verdict_chip);
        analysisNominalText = view.findViewById(R.id.analysis_nominal_text);
        findingsContainer = view.findViewById(R.id.findings_container);

        savingOverlay = view.findViewById(R.id.saving_overlay);
        savingText = view.findViewById(R.id.saving_text);
    }

    /** Brief contextual hints near GPS, angle and photo on the first new-launch visit. */
    private void maybeShowFirstVisitHints() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(LoginFragment.PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_NEW_LAUNCH_HINTS, false)) return;
        gpsHint.setVisibility(View.VISIBLE);
        angleHint.setVisibility(View.VISIBLE);
        photoHint.setVisibility(View.VISIBLE);
        prefs.edit().putBoolean(KEY_NEW_LAUNCH_HINTS, true).apply();
    }

    /** Disables angle capture gracefully when the device has no rotation sensor. */
    private void prepareAngleCapture() {
        if (rotationSensor == null) {
            measureAngleBtn.setEnabled(false);
            angleStatusText.setText("This device has no rotation sensor, so launch angle "
                    + "can't be measured. You can still log the launch.");
            angleStatusText.setVisibility(View.VISIBLE);
        }
    }

    private void setupMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menu.clear();
                if (isEditable) {
                    menuInflater.inflate(R.menu.menu_save, menu);
                } else if (launchId != null && !launchId.isEmpty()) {
                    menuInflater.inflate(R.menu.menu_edit_launch, menu);
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.action_save) {
                    saveLaunch();
                    return true;
                } else if (id == R.id.action_edit) {
                    enableEditMode();
                    return true;
                } else if (id == R.id.action_delete) {
                    confirmDelete();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner());
    }

    private void enableEditMode() {
        isEditable = true;
        setFieldsEditable();
        analysisCard.setVisibility(View.GONE); // recomputed and re-shown after the next save
        measureAngleBtn.setVisibility(View.VISIBLE);
        addPhotoBtn.setVisibility(View.VISIBLE);
        prepareAngleCapture();
        requireActivity().invalidateOptionsMenu();
        requestLocationAndFetch();
    }

    private void confirmDelete() {
        if (launchId == null || launchId.isEmpty()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete launch?")
                .setMessage("This launch will be permanently removed from your flight log.")
                .setPositiveButton("Delete", (dialog, which) -> deleteLaunch())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteLaunch() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();
        showSavingOverlay("Deleting launch…");
        db.collection("users").document(userId).collection("launches").document(launchId)
                .delete()
                .addOnSuccessListener(unused -> finishWithMessage("Launch deleted"))
                .addOnFailureListener(e -> {
                    hideSavingOverlay();
                    if (getView() != null) {
                        Snackbar.make(getView(), "Delete failed: " + e.getMessage(),
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void setFieldsReadOnly() {
        setFieldsEnabled(false);
    }

    private void setFieldsEditable() {
        setFieldsEnabled(true);
    }

    private void setFieldsEnabled(boolean enabled) {
        rocketNameEdit.setEnabled(enabled);
        motorTypeEdit.setEnabled(enabled);
        weatherNotesEdit.setEnabled(enabled);
        maxAltitudeEdit.setEnabled(enabled);
        flightDurationEdit.setEnabled(enabled);
        recoveryConditionEdit.setEnabled(enabled);
        notesEdit.setEnabled(enabled);
        successRadio.setEnabled(enabled);
        partialRadio.setEnabled(enabled);
        failedRadio.setEnabled(enabled);
    }

    // ---- GPS ----------------------------------------------------------------

    private void requestLocationAndFetch() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void fetchLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showLocationDenied();
            return;
        }

        showGpsLoading();
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (getView() == null) return;
                    gpsProgress.setVisibility(View.GONE);
                    if (location == null) {
                        showGpsError("Couldn't get a GPS fix. Move to an open area with clear sky "
                                + "and tap to retry — this is optional, you can still save.");
                        return;
                    }
                    gpsStatusText.setVisibility(View.GONE);
                    grantLocationBtn.setVisibility(View.GONE);

                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    elevation = location.getAltitude();

                    latitudeText.setText(String.format(Locale.getDefault(), "%.6f°", latitude));
                    longitudeText.setText(String.format(Locale.getDefault(), "%.6f°", longitude));
                    elevationText.setText(String.format(Locale.getDefault(), "%.1f m", elevation));

                    resolveLocationString(latitude, longitude);
                })
                .addOnFailureListener(e -> {
                    if (getView() == null) return;
                    showGpsError("Location services are unavailable right now. Tap to retry — "
                            + "this is optional, you can still save.");
                });
    }

    private void showGpsLoading() {
        gpsProgress.setVisibility(View.VISIBLE);
        gpsStatusText.setVisibility(View.GONE);
        grantLocationBtn.setVisibility(View.GONE);
    }

    private void showLocationDenied() {
        if (getView() == null) return;
        gpsProgress.setVisibility(View.GONE);
        gpsStatusText.setText("Location permission denied. Grant access to capture your launch "
                + "site automatically — this is optional, you can still save the launch.");
        gpsStatusText.setVisibility(View.VISIBLE);
        grantLocationBtn.setVisibility(View.VISIBLE);
    }

    private void showGpsError(String message) {
        gpsProgress.setVisibility(View.GONE);
        gpsStatusText.setText(message);
        gpsStatusText.setVisibility(View.VISIBLE);
        grantLocationBtn.setText("Retry");
        grantLocationBtn.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private void resolveLocationString(double lat, double lon) {
        try {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String city = addr.getLocality() != null ? addr.getLocality() : "";
                String state = addr.getAdminArea() != null ? addr.getAdminArea() : "";
                locationString = city.isEmpty() ? state : city + ", " + state;
            }
        } catch (IOException e) {
            locationString = String.format(Locale.getDefault(), "%.4f, %.4f", lat, lon);
        }
    }

    // ---- Photo capture ------------------------------------------------------

    /** Lets the user pick between the camera and the gallery for the launch photo. */
    private void showPhotoSourceChooser() {
        CharSequence[] options = {"Take photo", "Choose from gallery"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add launch photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestCameraThenCapture();
                    } else {
                        photoPickerLauncher.launch("image/*");
                    }
                })
                .show();
    }

    /**
     * The CAMERA permission is declared in the manifest, so on devices that honor
     * that declaration we must hold it at runtime before firing the capture intent.
     */
    private void requestCameraThenCapture() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        Uri uri = createCameraImageUri();
        if (uri == null) {
            if (getView() != null) {
                Snackbar.make(getView(), "Couldn't prepare the camera — please try again.",
                        Snackbar.LENGTH_LONG).show();
            }
            return;
        }
        pendingCameraUri = uri;
        takePictureLauncher.launch(uri);
    }

    /**
     * Creates the empty temp file the camera will write into and wraps it in a
     * FileProvider {@code content://} URI. The authority and the directory both
     * have to line up with the {@code <provider>} declaration and file_paths.xml.
     */
    @Nullable
    private Uri createCameraImageUri() {
        try {
            File picturesDir = new File(
                    requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "launch_photos");
            if (!picturesDir.exists() && !picturesDir.mkdirs()) {
                return null;
            }
            File imageFile = File.createTempFile(
                    "launch_" + System.currentTimeMillis(), ".jpg", picturesDir);
            return FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", imageFile);
        } catch (IOException e) {
            return null;
        }
    }

    /** Guards against uploading a zero-byte image if the camera returned nothing. */
    private boolean photoFileHasContent(Uri uri) {
        try (ParcelFileDescriptor pfd =
                     requireContext().getContentResolver().openFileDescriptor(uri, "r")) {
            return pfd != null && pfd.getStatSize() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    // ---- Launch angle -------------------------------------------------------

    private void startMeasuringAngle() {
        if (rotationSensor == null) return;
        measuringAngle = true;
        measureAngleBtn.setText("Lock Angle");
        angleStatusText.setText("Hold the phone flat against the launch rod, then tap Lock Angle.");
        angleStatusText.setVisibility(View.VISIBLE);
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
    }

    private void stopMeasuringAngle() {
        measuringAngle = false;
        measureAngleBtn.setText("Measure Angle");
        angleStatusText.setVisibility(View.GONE);
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);
        launchAngle = Math.abs(Math.toDegrees(orientation[1]));
        angleText.setText(String.format(Locale.getDefault(), "%.1f°", launchAngle));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ---- Detail load + analysis --------------------------------------------

    private void loadLaunchData() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("launches").document(launchId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (getView() == null || !doc.exists()) return;
                    Launch launch = doc.toObject(Launch.class);
                    if (launch == null) return;

                    rocketNameEdit.setText(launch.getRocketName());
                    motorTypeEdit.setText(launch.getMotorType());
                    weatherNotesEdit.setText(launch.getWeatherNotes());
                    flightDurationEdit.setText(launch.getFlightDuration());
                    recoveryConditionEdit.setText(launch.getRecoveryCondition());
                    notesEdit.setText(launch.getNotes());
                    maxAltitudeEdit.setText(String.valueOf((long) launch.getMaxAltitude()));

                    String outcome = launch.getOutcome();
                    if ("Success".equals(outcome)) successRadio.setChecked(true);
                    else if ("Partial".equals(outcome)) partialRadio.setChecked(true);
                    else if ("Failed".equals(outcome)) failedRadio.setChecked(true);

                    latitude = launch.getLatitude();
                    longitude = launch.getLongitude();
                    elevation = launch.getElevation();
                    launchAngle = launch.getLaunchAngle();
                    locationString = launch.getLocationString() != null ? launch.getLocationString() : "";
                    latitudeText.setText(String.format(Locale.getDefault(), "%.6f°", latitude));
                    longitudeText.setText(String.format(Locale.getDefault(), "%.6f°", longitude));
                    elevationText.setText(String.format(Locale.getDefault(), "%.1f m", elevation));
                    angleText.setText(String.format(Locale.getDefault(), "%.1f°", launchAngle));

                    // photoUrl now holds an absolute local file path. The file only
                    // exists on the device that captured it, so if it's missing
                    // (e.g. viewing on another device) we hide the preview instead
                    // of trying to load a nonexistent file.
                    String path = launch.getPhotoUrl();
                    if (path != null && !path.isEmpty()) {
                        existingPhotoUrl = path;
                        File photoFile = new File(path);
                        if (photoFile.exists()) {
                            photoPreview.setVisibility(View.VISIBLE);
                            Glide.with(this).load(photoFile).into(photoPreview);
                        } else {
                            photoPreview.setVisibility(View.GONE);
                        }
                    } else {
                        photoPreview.setVisibility(View.GONE);
                    }

                    // Render the analysis fresh from the loaded data so detail always
                    // reflects the current rule set, even for launches saved earlier.
                    renderAnalysis(LaunchAnalyzer.analyze(launch));
                })
                .addOnFailureListener(e -> {
                    if (getView() != null) {
                        Snackbar.make(getView(), "Couldn't load this launch — check your connection",
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    /** Populates the Launch Analysis card with a color-coded verdict and findings. */
    private void renderAnalysis(AnalysisResult result) {
        analysisCard.setVisibility(View.VISIBLE);

        verdictChip.setText(result.getVerdict());
        int verdictColor;
        boolean lightText;
        if (AnalysisResult.VERDICT_NOMINAL.equals(result.getVerdict())) {
            verdictColor = R.color.success_green;
            lightText = false;
        } else if (AnalysisResult.VERDICT_NEEDS_REVIEW.equals(result.getVerdict())) {
            verdictColor = R.color.color_primary; // red
            lightText = true;
        } else {
            verdictColor = R.color.partial_orange;
            lightText = false;
        }
        verdictChip.setChipBackgroundColor(
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), verdictColor)));
        verdictChip.setTextColor(ContextCompat.getColor(requireContext(),
                lightText ? R.color.white : R.color.black));

        findingsContainer.removeAllViews();
        if (result.isNominal()) {
            analysisNominalText.setVisibility(View.VISIBLE);
            findingsContainer.setVisibility(View.GONE);
            return;
        }

        analysisNominalText.setVisibility(View.GONE);
        findingsContainer.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Finding finding : result.getFindings()) {
            View row = inflater.inflate(R.layout.item_finding, findingsContainer, false);
            ((TextView) row.findViewById(R.id.finding_title)).setText(finding.getTitle());
            ((TextView) row.findViewById(R.id.finding_explanation)).setText(finding.getExplanation());
            row.findViewById(R.id.finding_severity_strip).setBackgroundColor(
                    ContextCompat.getColor(requireContext(),
                            finding.isMajor() ? R.color.color_primary : R.color.partial_orange));
            findingsContainer.addView(row);
        }
    }

    // ---- Save ---------------------------------------------------------------

    private String selectedOutcome() {
        if (failedRadio.isChecked()) return "Failed";
        if (partialRadio.isChecked()) return "Partial";
        return "Success";
    }

    private void saveLaunch() {
        rocketNameLayout.setError(null);
        maxAltitudeLayout.setError(null);

        String rocketName = rocketNameEdit.getText() != null
                ? rocketNameEdit.getText().toString().trim() : "";
        if (rocketName.isEmpty()) {
            rocketNameLayout.setError("Rocket name is required");
            rocketNameEdit.requestFocus();
            return;
        }

        String altStr = maxAltitudeEdit.getText() != null
                ? maxAltitudeEdit.getText().toString().trim() : "";
        double maxAlt = 0.0;
        if (!altStr.isEmpty()) {
            try {
                maxAlt = Double.parseDouble(altStr);
            } catch (NumberFormatException e) {
                maxAltitudeLayout.setError("Enter a valid number");
                maxAltitudeEdit.requestFocus();
                return;
            }
            if (maxAlt < 0) {
                maxAltitudeLayout.setError("Altitude can't be negative");
                maxAltitudeEdit.requestFocus();
                return;
            }
        }

        String motorType = motorTypeEdit.getText() != null
                ? motorTypeEdit.getText().toString().trim() : "";
        String outcome = selectedOutcome();

        if (selectedPhotoUri != null) {
            savePhotoLocallyThenSave(rocketName, motorType, outcome, maxAlt);
        } else {
            writeToFirestore(rocketName, motorType, outcome, maxAlt, existingPhotoUrl);
        }
    }

    /**
     * Copies the chosen photo into the app's permanent internal storage, then
     * writes the launch (with the local file path in photoUrl) to Firestore.
     * The copy runs off the main thread so a large image doesn't jank the UI.
     */
    private void savePhotoLocallyThenSave(String rocketName, String motorType,
                                          String outcome, double maxAlt) {
        showSavingOverlay("Saving photo…");
        final Uri source = selectedPhotoUri;
        final Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            String savedPath = null;
            try {
                savedPath = copyPhotoToInternalStorage(appContext, source);
            } catch (IOException ignored) {
                // savedPath stays null and is handled as a failure below.
            }
            final String result = savedPath;
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (getView() == null) return;
                if (result == null) {
                    hideSavingOverlay();
                    Snackbar.make(getView(),
                            "Couldn't save the photo to this device — please try again.",
                            Snackbar.LENGTH_LONG).show();
                } else {
                    writeToFirestore(rocketName, motorType, outcome, maxAlt, result);
                }
            });
        }).start();
    }

    /**
     * Copies the photo behind {@code source} into files/launch_photos and returns
     * the absolute path of the persisted copy. Internal storage survives reboots
     * and isn't auto-cleaned, so the file stays put for later viewing.
     */
    private String copyPhotoToInternalStorage(Context context, Uri source) throws IOException {
        File photosDir = new File(context.getFilesDir(), "launch_photos");
        if (!photosDir.exists() && !photosDir.mkdirs()) {
            throw new IOException("Could not create the photo directory");
        }
        File dest = new File(photosDir, "launch_" + UUID.randomUUID() + ".jpg");
        try (InputStream in = context.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IOException("Could not open the selected photo");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
        return dest.getAbsolutePath();
    }

    private void writeToFirestore(String rocketName, String motorType, String outcome,
                                  double maxAlt, String photoUrl) {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        if (savingOverlay.getVisibility() != View.VISIBLE) showSavingOverlay("Saving launch…");
        else savingText.setText("Saving launch…");

        String flightDuration = flightDurationEdit.getText() != null
                ? flightDurationEdit.getText().toString().trim() : "";
        String recoveryCondition = recoveryConditionEdit.getText() != null
                ? recoveryConditionEdit.getText().toString().trim() : "";

        Map<String, Object> data = new HashMap<>();
        data.put("rocketName", rocketName);
        data.put("motorType", motorType);
        data.put("latitude", latitude);
        data.put("longitude", longitude);
        data.put("elevation", elevation);
        data.put("locationString", locationString);
        data.put("launchAngle", launchAngle);
        data.put("photoUrl", photoUrl != null ? photoUrl : "");
        data.put("weatherNotes", weatherNotesEdit.getText() != null
                ? weatherNotesEdit.getText().toString().trim() : "");
        data.put("maxAltitude", maxAlt);
        data.put("flightDuration", flightDuration);
        data.put("recoveryCondition", recoveryCondition);
        data.put("notes", notesEdit.getText() != null
                ? notesEdit.getText().toString().trim() : "");
        data.put("outcome", outcome);

        // Run the on-device analysis and store the result alongside the launch.
        Launch forAnalysis = new Launch();
        forAnalysis.setMotorType(motorType);
        forAnalysis.setLaunchAngle(launchAngle);
        forAnalysis.setMaxAltitude(maxAlt);
        forAnalysis.setFlightDuration(flightDuration);
        forAnalysis.setRecoveryCondition(recoveryCondition);
        forAnalysis.setOutcome(outcome);
        AnalysisResult analysis = LaunchAnalyzer.analyze(forAnalysis);
        data.put("analysisVerdict", analysis.getVerdict());
        data.put("analysis", analysis.toMap());

        boolean isUpdate = launchId != null && !launchId.isEmpty();
        if (isUpdate) {
            db.collection("users").document(userId).collection("launches").document(launchId)
                    .update(data)
                    .addOnSuccessListener(unused -> finishWithMessage("Launch updated"))
                    .addOnFailureListener(this::onSaveFailed);
        } else {
            data.put("timestamp", new Timestamp(new Date()));
            db.collection("users").document(userId).collection("launches")
                    .add(data)
                    .addOnSuccessListener(ref -> finishWithMessage("Launch saved"))
                    .addOnFailureListener(this::onSaveFailed);
        }
    }

    private void onSaveFailed(Exception e) {
        hideSavingOverlay();
        if (getView() != null) {
            Snackbar.make(getView(), "Save failed — check your connection and try again",
                    Snackbar.LENGTH_LONG).show();
        }
    }

    /** Hands a confirmation message to the history screen, then navigates back. */
    private void finishWithMessage(String message) {
        hideSavingOverlay();
        if (getView() == null) return;
        NavController nav = Navigation.findNavController(requireView());
        NavBackStackEntry previous = nav.getPreviousBackStackEntry();
        if (previous != null) {
            previous.getSavedStateHandle().set(RESULT_LAUNCH_MESSAGE, message);
        }
        nav.navigateUp();
    }

    private void showSavingOverlay(String message) {
        savingText.setText(message);
        savingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideSavingOverlay() {
        if (savingOverlay != null) savingOverlay.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        sensorManager.unregisterListener(this);
    }
}
