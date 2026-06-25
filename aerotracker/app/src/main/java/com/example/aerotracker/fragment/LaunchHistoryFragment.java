package com.example.aerotracker.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.aerotracker.R;
import com.example.aerotracker.adapter.LaunchAdapter;
import com.example.aerotracker.model.Launch;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LaunchHistoryFragment extends Fragment {

    private static final int SORT_NEWEST = 0;
    private static final int SORT_OLDEST = 1;
    private static final int SORT_ALTITUDE = 2;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private LaunchAdapter adapter;
    private ListenerRegistration listenerRegistration;

    private final List<Launch> masterLaunches = new ArrayList<>();
    private int sortMode = SORT_NEWEST;
    private String outcomeFilter = null; // null = show all
    private boolean firstLoadDone = false;

    private SwipeRefreshLayout swipeRefresh;
    private CircularProgressIndicator progress;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private Button emptyCreateBtn;
    private TextView fabTip;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_launch_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        progress = view.findViewById(R.id.history_progress);
        emptyState = view.findViewById(R.id.empty_state);
        emptyTitle = view.findViewById(R.id.empty_title);
        emptyMessage = view.findViewById(R.id.empty_message);
        emptyCreateBtn = view.findViewById(R.id.empty_create_btn);
        fabTip = view.findViewById(R.id.fab_tip);

        setupMenu(view);

        RecyclerView recyclerView = view.findViewById(R.id.launches_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new LaunchAdapter(launch -> {
            Bundle args = new Bundle();
            args.putBoolean("isEditable", false);
            args.putString("launchId", launch.getId());
            Navigation.findNavController(view).navigate(R.id.action_history_to_detail, args);
        });
        recyclerView.setAdapter(adapter);
        attachSwipeToDelete(recyclerView);

        FloatingActionButton fab = view.findViewById(R.id.fab_new_launch);
        fab.setOnClickListener(v -> navigateToNewLaunch(view));
        emptyCreateBtn.setOnClickListener(v -> navigateToNewLaunch(view));

        // Snapshot listener keeps data live; pull-to-refresh gives users an
        // explicit way to confirm they're seeing the latest server state.
        swipeRefresh.setColorSchemeResources(R.color.color_secondary);
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.color_surface_variant);
        swipeRefresh.setOnRefreshListener(this::refreshFromServer);

        progress.setVisibility(View.VISIBLE);
        loadLaunches();
        observeSaveResult(view);
    }

    /**
     * Shows a confirmation Snackbar after the new-launch/detail screen reports a
     * save, update or delete back through the navigation result handle.
     */
    private void observeSaveResult(View view) {
        NavController nav = Navigation.findNavController(view);
        NavBackStackEntry entry = nav.getCurrentBackStackEntry();
        if (entry == null) return;
        entry.getSavedStateHandle()
                .getLiveData(NewLaunchFragment.RESULT_LAUNCH_MESSAGE, "")
                .observe(getViewLifecycleOwner(), message -> {
                    if (message == null || message.isEmpty() || getView() == null) return;
                    Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
                    entry.getSavedStateHandle().set(NewLaunchFragment.RESULT_LAUNCH_MESSAGE, "");
                });
    }

    private void navigateToNewLaunch(View view) {
        Bundle args = new Bundle();
        args.putBoolean("isEditable", true);
        args.putString("launchId", "");
        Navigation.findNavController(view).navigate(R.id.action_history_to_new_launch, args);
    }

    private void setupMenu(View view) {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menu.clear();
                menuInflater.inflate(R.menu.menu_history, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.action_settings) {
                    Navigation.findNavController(view).navigate(R.id.action_history_to_settings);
                    return true;
                } else if (id == R.id.sort_newest) {
                    sortMode = SORT_NEWEST;
                } else if (id == R.id.sort_oldest) {
                    sortMode = SORT_OLDEST;
                } else if (id == R.id.sort_altitude) {
                    sortMode = SORT_ALTITUDE;
                } else if (id == R.id.filter_all) {
                    outcomeFilter = null;
                } else if (id == R.id.filter_success) {
                    outcomeFilter = "Success";
                } else if (id == R.id.filter_partial) {
                    outcomeFilter = "Partial";
                } else if (id == R.id.filter_failed) {
                    outcomeFilter = "Failed";
                } else {
                    return false;
                }
                menuItem.setChecked(true);
                applyListChanges();
                return true;
            }
        }, getViewLifecycleOwner());
    }

    private void loadLaunches() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();
        listenerRegistration = db.collection("users")
                .document(userId)
                .collection("launches")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    progress.setVisibility(View.GONE);
                    if (e != null || snapshots == null) {
                        if (!firstLoadDone && getView() != null) {
                            Snackbar.make(getView(),
                                    "Couldn't load launches — check your connection",
                                    Snackbar.LENGTH_LONG).show();
                        }
                        return;
                    }
                    firstLoadDone = true;
                    masterLaunches.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        masterLaunches.add(doc.toObject(Launch.class));
                    }
                    applyListChanges();
                });
    }

    private void refreshFromServer() {
        if (mAuth.getCurrentUser() == null) {
            swipeRefresh.setRefreshing(false);
            return;
        }
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("launches")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        masterLaunches.clear();
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            masterLaunches.add(doc.toObject(Launch.class));
                        }
                        applyListChanges();
                    }
                    swipeRefresh.setRefreshing(false);
                });
    }

    /** Applies the active filter and sort to the master list, and updates empty states. */
    private void applyListChanges() {
        List<Launch> visible = new ArrayList<>();
        for (Launch launch : masterLaunches) {
            if (outcomeFilter == null || outcomeFilter.equals(launch.getOutcome())) {
                visible.add(launch);
            }
        }

        if (sortMode == SORT_ALTITUDE) {
            Collections.sort(visible, Comparator.comparingDouble(Launch::getMaxAltitude).reversed());
        } else if (sortMode == SORT_OLDEST) {
            Collections.reverse(visible); // master list arrives newest-first
        }

        adapter.setLaunches(visible);

        boolean noLaunchesAtAll = firstLoadDone && masterLaunches.isEmpty();
        boolean filteredEmpty = firstLoadDone && !masterLaunches.isEmpty() && visible.isEmpty();

        if (noLaunchesAtAll) {
            emptyTitle.setText("No launches yet");
            emptyMessage.setText("Your flight log is empty. Head out to the field and record your first rocket launch — GPS, angle, photo and all.");
            emptyCreateBtn.setVisibility(View.VISIBLE);
        } else if (filteredEmpty) {
            emptyTitle.setText("No matching launches");
            emptyMessage.setText("No launches match this filter. Choose \"Show all outcomes\" to see your full flight log.");
            emptyCreateBtn.setVisibility(View.GONE);
        }
        emptyState.setVisibility(noLaunchesAtAll || filteredEmpty ? View.VISIBLE : View.GONE);
        fabTip.setVisibility(noLaunchesAtAll ? View.VISIBLE : View.GONE);
    }

    private void attachSwipeToDelete(RecyclerView recyclerView) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                Launch launch = adapter.getLaunchAt(position);
                if (launch == null) {
                    adapter.notifyItemChanged(position);
                    return;
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Delete launch?")
                        .setMessage("\"" + launch.getRocketName() + "\" will be permanently removed from your flight log.")
                        .setPositiveButton("Delete", (dialog, which) -> deleteLaunch(launch))
                        .setNegativeButton("Cancel", (dialog, which) ->
                                adapter.notifyItemChanged(position))
                        .setOnCancelListener(dialog -> adapter.notifyItemChanged(position))
                        .show();
            }
        }).attachToRecyclerView(recyclerView);
    }

    private void deleteLaunch(Launch launch) {
        if (mAuth.getCurrentUser() == null || launch.getId() == null) return;
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("launches")
                .document(launch.getId())
                .delete()
                .addOnSuccessListener(unused -> {
                    if (getView() != null) {
                        Snackbar.make(getView(), "Launch deleted", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    applyListChanges(); // restore the swiped row
                    if (getView() != null) {
                        Snackbar.make(getView(), "Delete failed: " + e.getMessage(),
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listenerRegistration != null) listenerRegistration.remove();
    }
}
