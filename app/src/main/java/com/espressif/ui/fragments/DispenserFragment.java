package com.espressif.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.ui.models.Medication;
import com.espressif.ui.models.Schedule;
import com.espressif.mediwatch.R;
import com.espressif.ui.activities.mqtt_activities.DeviceConnectionChecker;
import com.espressif.ui.adapters.MedicationAdapter;
import com.espressif.ui.adapters.ScheduleAdapter;
import com.espressif.ui.viewmodels.DispenserViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.espressif.ui.dialogs.DialogMedication;

public class DispenserFragment extends Fragment implements 
        MedicationAdapter.MedicationListener,
        DialogMedication.MedicationDialogListener {

    private DispenserViewModel viewModel;
    private MedicationAdapter adapter;
    
    // Vistas principales
    private RecyclerView recyclerMedications;
    private ExtendedFloatingActionButton fabAddMedication;
    private ChipGroup chipGroupFilters;
    
    // Estados de la UI
    private LinearLayout loadingView;
    private LinearLayout emptyView;
    private LinearLayout errorView;
    private TextView tvErrorMessage;
    private Button btnRetry;
    private Button btnAddFirst;
    
    // Estado de conexión del dispensador
    private ImageView ivDeviceStatus;
    private TextView tvDeviceStatus;
    private TextView tvDeviceDetails;
    
    // ID del paciente (en un caso real, esto vendría de la sesión del usuario)
    private final String patientId = "current_user_id";

    // Agregar este campo a la clase
    private DeviceConnectionChecker connectionChecker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dispenser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Inicializar ViewModel
        viewModel = new ViewModelProvider(this).get(DispenserViewModel.class);
        
        // Configurar vistas
        setupViews(view);
        
        // Configurar RecyclerView y adaptador
        setupRecyclerView();
        
        // Configurar filtros
        setupFilters();
        
        // Configurar listeners para acciones de UI
        setupListeners();
        
        // Observar cambios en los datos
        observeViewModel();
        
        // Cargar datos
        loadData();
    }

    // Agregar este método después de onViewCreated
    @Override
    public void onStart() {
        super.onStart();
        // Iniciar verificación de conexión
        initConnectionChecker();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Liberar recursos del checker
        if (connectionChecker != null) {
            connectionChecker.release();
        }
    }

    private void initConnectionChecker() {
        if (connectionChecker == null) {
            connectionChecker = new DeviceConnectionChecker(requireContext());
        }
        
        // Iniciar primera verificación de conexión
        checkDeviceConnection();
        
        // Programar verificaciones periódicas (cada 30 segundos)
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded() && !isDetached()) {
                    checkDeviceConnection();
                    handler.postDelayed(this, 30000); // 30 segundos
                }
            }
        }, 30000); // Primera repetición en 30 segundos
    }

    private void checkDeviceConnection() {
        if (connectionChecker != null) {
            connectionChecker.checkConnection(new DeviceConnectionChecker.ConnectionCheckListener() {
                @Override
                public void onConnectionCheckResult(boolean isConnected) {
                    if (isAdded() && viewModel != null) {
                        viewModel.updateDispenserConnectionStatus(
                            isConnected, 
                            isConnected ? "Dispensador en línea" : "Dispensador desconectado"
                        );
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    if (isAdded() && viewModel != null) {
                        viewModel.updateDispenserConnectionStatus(false, "Error: " + errorMessage);
                    }
                }
            });
        }
    }
    
    private void setupViews(View view) {
        // Vistas principales
        recyclerMedications = view.findViewById(R.id.recycler_medications);
        fabAddMedication = view.findViewById(R.id.fab_add_medication);
        chipGroupFilters = view.findViewById(R.id.chip_group_filters);
        
        // Estados de la UI
        loadingView = view.findViewById(R.id.loading_view);
        emptyView = view.findViewById(R.id.empty_view);
        errorView = view.findViewById(R.id.error_view);
        tvErrorMessage = view.findViewById(R.id.tv_error_message);
        btnRetry = view.findViewById(R.id.btn_retry);
        btnAddFirst = view.findViewById(R.id.btn_add_first);
        
        // Estado de conexión del dispensador
        ivDeviceStatus = view.findViewById(R.id.iv_device_status);
        tvDeviceStatus = view.findViewById(R.id.tv_device_status);
        tvDeviceDetails = view.findViewById(R.id.tv_device_details);
    }
    
    private void setupRecyclerView() {
        Context context = requireContext();
        adapter = new MedicationAdapter(context, this);
        recyclerMedications.setAdapter(adapter);
        // El LayoutManager ya está definido en el XML con app:layoutManager
    }
    
    private void setupFilters() {
        // Configurar los chips para filtrar por tipo
        Chip chipAll = chipGroupFilters.findViewById(R.id.chip_all);
        Chip chipPills = chipGroupFilters.findViewById(R.id.chip_pills);
        Chip chipLiquids = chipGroupFilters.findViewById(R.id.chip_liquids);
        Chip chipToday = chipGroupFilters.findViewById(R.id.chip_today);
        
        chipAll.setOnClickListener(v -> viewModel.setFilter(DispenserViewModel.FilterType.ALL));
        chipPills.setOnClickListener(v -> viewModel.setFilter(DispenserViewModel.FilterType.PILLS));
        chipLiquids.setOnClickListener(v -> viewModel.setFilter(DispenserViewModel.FilterType.LIQUIDS));
        chipToday.setOnClickListener(v -> viewModel.setFilter(DispenserViewModel.FilterType.TODAY));
    }
    
    private void setupListeners() {
        // FAB para añadir medicamento
        fabAddMedication.setOnClickListener(v -> {
            showAddMedicationDialog();
        });
        
        // Botón para reintentar carga
        btnRetry.setOnClickListener(v -> {
            loadData();
        });
        
        // Botón para añadir primer medicamento (desde vista vacía)
        btnAddFirst.setOnClickListener(v -> {
            showAddMedicationDialog();
        });
    }
    
    private void observeViewModel() {
        // Observar cambios en la lista de medicamentos
        viewModel.getMedications().observe(getViewLifecycleOwner(), medications -> {
            adapter.setMedications(medications);
        });
        
        // Observar cambios en el estado de la UI
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            updateUiState(state);
        });
        
        // Observar mensajes de error
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                showErrorMessage(errorMsg);
            }
        });
        
        // Observar estado de conexión del dispensador
        viewModel.getDispenserConnected().observe(getViewLifecycleOwner(), connected -> {
            updateConnectionStatus(connected, viewModel.getDispenserStatus().getValue());
        });
        
        // Observar status del dispensador
        viewModel.getDispenserStatus().observe(getViewLifecycleOwner(), status -> {
            updateConnectionStatus(viewModel.getDispenserConnected().getValue(), status);
        });
        
        // Observar nivel de batería
        viewModel.getBatteryLevel().observe(getViewLifecycleOwner(), batteryLevel -> {
            updateBatteryLevel(batteryLevel);
        });
    }
    
    private void loadData() {
        // Empezar a escuchar cambios en los medicamentos
        viewModel.startListeningForMedications(patientId);
    }
    
    private void updateUiState(DispenserViewModel.UIState state) {
        // Ocultar todas las vistas de estado
        loadingView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        recyclerMedications.setVisibility(View.GONE);
        
        // Mostrar la vista correspondiente al estado actual
        switch (state) {
            case LOADING:
                loadingView.setVisibility(View.VISIBLE);
                break;
            case EMPTY:
                emptyView.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                errorView.setVisibility(View.VISIBLE);
                break;
            case CONTENT:
                recyclerMedications.setVisibility(View.VISIBLE);
                break;
        }
    }
    
    private void updateConnectionStatus(Boolean connected, String status) {
        if (connected == null || status == null) {
            return;
        }
        
        if (connected) {
            ivDeviceStatus.setImageResource(R.drawable.ic_device_connected);
            ivDeviceStatus.setColorFilter(getResources().getColor(R.color.colorSuccess, null));
            tvDeviceStatus.setText("Dispensador conectado");
            tvDeviceDetails.setText(status);
        } else {
            ivDeviceStatus.setImageResource(R.drawable.ic_device_disconnected);
            ivDeviceStatus.setColorFilter(getResources().getColor(R.color.colorError, null));
            tvDeviceStatus.setText("Dispensador desconectado");
            tvDeviceDetails.setText("Verifica la conexión del dispositivo");
        }
    }
    
    private void updateBatteryLevel(Integer level) {
        if (level == null) return;
        
        // Aquí podrías actualizar un indicador visual de batería
        String statusText = tvDeviceDetails.getText().toString();
        String batteryStatus = " | Batería: " + level + "%";
        
        // Evitar añadir múltiples veces la información de batería
        if (!statusText.contains(" | Batería:")) {
            tvDeviceDetails.setText(statusText + batteryStatus);
        } else {
            // Reemplazar la parte de la batería
            int batteryIndex = statusText.indexOf(" | Batería:");
            if (batteryIndex >= 0) {
                String baseStatus = statusText.substring(0, batteryIndex);
                tvDeviceDetails.setText(baseStatus + batteryStatus);
            }
        }
    }
    
    private void showErrorMessage(String errorMsg) {
        tvErrorMessage.setText(errorMsg);
        Snackbar.make(recyclerMedications, errorMsg, Snackbar.LENGTH_LONG).show();
    }
    
    private void showAddMedicationDialog() {
        DialogMedication dialog = DialogMedication.newInstance();
        dialog.show(getChildFragmentManager(), "dialog_add_medication");
    }
    
    private void showEditMedicationDialog(Medication medication) {
        DialogMedication dialog = DialogMedication.newInstance(medication);
        dialog.show(getChildFragmentManager(), "dialog_edit_medication");
    }
    
    // Implementación de MedicationDialogListener
    
    @Override
    public void onMedicationSaved(Medication medication) {
        if (medication.getId() == null || medication.getId().isEmpty()) {
            // Es un nuevo medicamento
            viewModel.createMedication(medication);
            Toast.makeText(requireContext(), "Medicamento añadido", Toast.LENGTH_SHORT).show();
        } else {
            // Es una actualización
            viewModel.updateMedication(medication);
            Toast.makeText(requireContext(), "Medicamento actualizado", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onDialogCancelled() {
        // No necesitamos hacer nada especial cuando se cancela
    }
    
    private void showAddScheduleDialog(Medication medication) {
        // En el siguiente paso implementaremos los diálogos
        Toast.makeText(requireContext(), "Añadir horario para: " + medication.getName(), Toast.LENGTH_SHORT).show();
    }
    
    private void confirmDeleteMedication(Medication medication) {
        // En la implementación completa, esto mostraría un diálogo de confirmación
        // Por ahora, eliminar directamente
        viewModel.deleteMedication(medication.getId());
        Toast.makeText(requireContext(), "Medicamento eliminado: " + medication.getName(), Toast.LENGTH_SHORT).show();
    }
    
    // Implementación de MedicationListener para manejar clicks en los medicamentos
    
    @Override
    public void onMedicationClick(Medication medication) {
        // Esto podría mostrar detalles o realizar otra acción
        Toast.makeText(requireContext(), "Seleccionado: " + medication.getName(), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onEditMedicationClick(Medication medication) {
        showEditMedicationDialog(medication);
    }
    
    @Override
    public void onDeleteMedicationClick(Medication medication) {
        confirmDeleteMedication(medication);
    }
    
    @Override
    public void onAddScheduleClick(Medication medication) {
        showAddScheduleDialog(medication);
    }
    
    // Método adicional para crear un listener de Schedule (si decides implementarlo en el fragmento)
    private ScheduleAdapter.ScheduleListener createScheduleListener(Medication medication) {
        return new ScheduleAdapter.ScheduleListener() {
            @Override
            public void onScheduleClick(Schedule schedule) {
                // En el siguiente paso, esto abriría un diálogo para editar el horario
                Toast.makeText(requireContext(), "Horario seleccionado: " + schedule.getFormattedTime(), Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onScheduleActiveChanged(Schedule schedule, boolean active) {
                // Actualizar el estado activo del horario
                viewModel.saveSchedule(medication.getId(), schedule);
            }
        };
    }
}