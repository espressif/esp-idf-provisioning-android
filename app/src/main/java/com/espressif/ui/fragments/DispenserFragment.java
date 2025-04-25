package com.espressif.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.data.repository.MedicationRepository;
import com.espressif.ui.dialogs.ProgressDialogFragment;
import com.espressif.ui.models.Medication;
import com.espressif.ui.models.Schedule;
import com.espressif.mediwatch.R;
import com.espressif.ui.adapters.MedicationAdapter;
import com.espressif.ui.adapters.ScheduleAdapter;
import com.espressif.ui.viewmodels.DispenserViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.espressif.ui.dialogs.DialogMedication;
import com.espressif.ui.dialogs.ScheduleDialogFragment;
import com.espressif.ui.utils.CompartmentManager;
import com.espressif.ui.utils.ErrorHandler;
import com.espressif.ui.utils.ObserverManager;
import com.espressif.ui.viewmodels.MqttViewModel;

import java.util.List;

public class DispenserFragment extends Fragment implements 
        MedicationAdapter.MedicationListener,
        DialogMedication.MedicationDialogListener,
        ScheduleDialogFragment.ScheduleDialogListener {

    private static final String TAG = "DispenserFragment";
    
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
    
    // ID del paciente (en un caso real, esto vendría de la sesión del usuario)
    private final String patientId = "current_user_id";

    private CompartmentManager compartmentManager;

    // ViewModels y gestión de observadores
    private DispenserViewModel dispenserViewModel;
    private MqttViewModel mqttViewModel;
    private ObserverManager observerManager;

    // Mostrar diálogo durante la sincronización
    private ProgressDialogFragment syncProgressDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inicializar el ObserverManager
        observerManager = new ObserverManager();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dispenser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Inicializar ViewModels
        viewModel = new ViewModelProvider(this).get(DispenserViewModel.class);
        dispenserViewModel = new ViewModelProvider(requireActivity()).get(DispenserViewModel.class);
        mqttViewModel = new ViewModelProvider(requireActivity()).get(MqttViewModel.class);
        
        // Inicializar el gestor de compartimentos
        compartmentManager = CompartmentManager.getInstance();
        
        // Configurar vistas
        setupViews(view);
        setupRecyclerView();
        setupFilters();
        setupListeners();
        
        // Configurar observadores (método unificado)
        setupObservers();
        
        // Conectar los ViewModels
        dispenserViewModel.connectWithMqttViewModel(mqttViewModel);
        
        // Iniciar la conexión MQTT
        mqttViewModel.connect();
        
        // Cargar medicamentos
        loadData();
        
        // Configurar manejadores de dispensación
        setupDispenseHandlers();
    }

    private String getCurrentPatientId() {
        return patientId;
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
    }
    
    private void setupRecyclerView() {
        Context context = requireContext();
        adapter = new MedicationAdapter(context, this);
        
        // Configurar el listener para horarios de cada medicamento
        adapter.setScheduleListenerProvider(this::createScheduleListener);
        
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
    
    private void setupObservers() {
        // 1. Observadores de DispenserViewModel
        observerManager.observe(getViewLifecycleOwner(), 
            viewModel.getMedications(), 
            medications -> {
                adapter.setMedications(medications);
                adapter.notifyDataSetChanged();
            }, 
            "medications_list");
        
        observerManager.observe(getViewLifecycleOwner(), 
            viewModel.getUiState(), 
            this::updateUiState, 
            "ui_state");
        
        observerManager.observe(getViewLifecycleOwner(), 
            viewModel.getErrorMessage(), 
            errorMsg -> {
                if (errorMsg != null && !errorMsg.isEmpty()) {
                    showErrorMessage(errorMsg);
                }
            }, 
            "dispenser_errors");
        
        // 2. Observadores de MqttViewModel
        observerManager.observe(getViewLifecycleOwner(), 
            mqttViewModel.getIsSyncingSchedules(), 
            isSyncing -> {
                if (isSyncing) {
                    showSyncingDialog();
                } else {
                    dismissSyncingDialog();
                }
            }, 
            "sync_progress");
        
        observerManager.observe(getViewLifecycleOwner(), 
            mqttViewModel.getErrorMessage(), 
            errorMsg -> {
                if (errorMsg != null && !errorMsg.isEmpty()) {
                    showErrorMessage("Error de conexión: " + errorMsg);
                }
            }, 
            "mqtt_errors");
        
        // 3. Observador para dispensación completada
        observerManager.observe(getViewLifecycleOwner(), 
            mqttViewModel.getMedicationDispensedEvent(), 
            medicationId -> {
                if (medicationId != null && !medicationId.isEmpty()) {
                    // Refrescar inmediatamente después de una dispensación exitosa
                    viewModel.loadMedications(patientId);
                }
            }, 
            "medication_dispensed");
        
        // 4. Status de conexión
        observerManager.observe(getViewLifecycleOwner(), 
            mqttViewModel.getStatusMessage(), 
            status -> {
                updateConnectionStatus(status);
            }, 
            "connection_status");
        
        mqttViewModel.getMedicationDispensedEvent().observe(getViewLifecycleOwner(), medicationId -> {
            if (medicationId != null && !medicationId.isEmpty()) {
                // Actualizar la UI usando tu propio DispenserViewModel
                dispenserViewModel.loadMedications(patientId);
            }
        });
    }
    
    // Método auxiliar para actualizar indicador de conexión
    private void updateConnectionStatus(String status) {
        // Podríamos usar un TextView o un icono para mostrar el estado
        // Este ejemplo es simplificado, adáptalo a tu UI real
        if (status != null && status.equals("Conectado")) {
            Log.d(TAG, "Dispensador conectado");
            // Por ejemplo, cambiar color de un icono a verde
        } else {
            Log.d(TAG, "Dispensador desconectado o no disponible: " + status);
            // Por ejemplo, cambiar color de un icono a rojo
        }
    }
    
    private void loadData() {
        if (patientId == null || patientId.isEmpty()) {
            ErrorHandler.handleError(TAG, ErrorHandler.ERROR_VALIDATION, 
                "ID de paciente no válido", null);
            return;
        }
        
        // Empezar a escuchar cambios en los medicamentos
        viewModel.startListeningForMedications(patientId);
        
        // Utilizar el método correcto del repositorio
        viewModel.getMedicationRepository().getMedications(patientId, new MedicationRepository.DataCallback<List<Medication>>() {
            @Override
            public void onSuccess(List<Medication> medications) {
                // Actualizar el estado de ocupación de los compartimentos
                compartmentManager.refreshOccupation(medications);
            }
            
            @Override
            public void onError(String errorMessage) {
                String formattedError = ErrorHandler.handleDatabaseError(
                    TAG, "loadData", errorMessage);
                showErrorMessage(formattedError);
            }
        });
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
    
    /**
     * Muestra un mensaje de error en la interfaz de usuario
     */
    private void showErrorMessage(String errorMsg) {
        if (errorMsg == null || errorMsg.isEmpty()) return;
        
        // Actualizar el texto de error en la vista de error
        if (tvErrorMessage != null) {
            tvErrorMessage.setText(errorMsg);
        }
        
        // Mostrar snackbar con el error
        View rootView = getView();
        if (rootView != null) {
            Snackbar snackbar = Snackbar.make(rootView, errorMsg, Snackbar.LENGTH_LONG);
            
            // Personalizar el snackbar para errores
            View snackbarView = snackbar.getView();
            snackbarView.setBackgroundColor(requireContext().getResources().getColor(R.color.colorError));
            
            // Mostrar el snackbar
            snackbar.show();
        } else {
            // Fallback a Toast si la vista no está disponible
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
        }
        
        // Registrar el error
        Log.e(TAG, errorMsg);
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
        if (medication == null) {
            ErrorHandler.handleError(TAG, 
                ErrorHandler.ERROR_VALIDATION, 
                "Medicamento no válido para guardar", null);
            return;
        }
        
        try {
            if (medication.getId() == null || medication.getId().isEmpty()) {
                // Es un nuevo medicamento
                viewModel.createMedication(medication);
                
                // Marcar el compartimento como ocupado
                compartmentManager.occupyCompartment(medication.getCompartmentNumber(), medication.getId());
                
                Toast.makeText(requireContext(), "Medicamento añadido correctamente", Toast.LENGTH_SHORT).show();
            } else {
                // Es una actualización - Verificar si cambió el compartimento
                viewModel.getMedicationRepository().getMedication(patientId, medication.getId(), 
                    new MedicationRepository.DataCallback<Medication>() {
                        @Override
                        public void onSuccess(Medication oldMedication) {
                            updateMedicationWithCompartmentCheck(oldMedication, medication);
                        }
                        
                        @Override
                        public void onError(String errorMessage) {
                            String formattedError = ErrorHandler.handleDatabaseError(
                                TAG, "onMedicationSaved", errorMessage);
                            Log.w(TAG, formattedError);
                            
                            // Si hay error al obtener el medicamento anterior, 
                            // simplemente actualizamos el nuevo
                            viewModel.updateMedication(medication);
                        }
                    });
            }
            
            // Sincronizar después de guardar
            synchronizeAfterSave();
            
        } catch (Exception e) {
            String errorMsg = ErrorHandler.handleError(TAG, 
                ErrorHandler.ERROR_UNKNOWN, 
                "Error al guardar medicamento", e);
            showErrorMessage(errorMsg);
        }
    }
    
    // Método auxiliar para actualizar medicamento con verificación de compartimento
    private void updateMedicationWithCompartmentCheck(Medication oldMedication, Medication newMedication) {
        if (oldMedication != null && 
            oldMedication.getCompartmentNumber() != newMedication.getCompartmentNumber()) {
            // Liberar el compartimento anterior
            compartmentManager.freeCompartment(oldMedication.getCompartmentNumber());
            // Ocupar el nuevo compartimento
            compartmentManager.occupyCompartment(newMedication.getCompartmentNumber(), 
                                               newMedication.getId());
        }
        // Actualizar el medicamento
        viewModel.updateMedication(newMedication);
        Toast.makeText(requireContext(), "Medicamento actualizado", Toast.LENGTH_SHORT).show();
    }
    
    // Método auxiliar para sincronizar después de guardar
    private void synchronizeAfterSave() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded() && !isDetached()) {
                // Sincronizar con el dispensador
                Log.d(TAG, "Sincronizando después de guardar medicamento...");
                if (mqttViewModel != null && dispenserViewModel != null) {
                    dispenserViewModel.syncSchedulesWithDispenser(mqttViewModel);
                }
            }
        }, 1500); // Retraso para dar tiempo a Firebase para guardar
    }
    
    @Override
    public void onDialogCancelled() {
        // No necesitamos hacer nada especial cuando se cancela
    }
    
    private void showAddScheduleDialog(Medication medication) {
        if (medication == null || medication.getId() == null) {
            ErrorHandler.handleValidationError(TAG, "medication", "Medicamento no válido para horario");
            Toast.makeText(requireContext(), "Error: medicamento no válido", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ScheduleDialogFragment dialogFragment = ScheduleDialogFragment.newInstance(
                medication.getId(), medication.getName());
        dialogFragment.setListener(this);
        dialogFragment.show(getChildFragmentManager(), "schedule_dialog");
    }
    
    private void showEditScheduleDialog(Medication medication, Schedule schedule) {
        if (medication == null || medication.getId() == null || schedule == null || schedule.getId() == null) {
            ErrorHandler.handleValidationError(TAG, "schedule", "Datos de horario no válidos");
            Toast.makeText(requireContext(), "Error: datos no válidos", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ScheduleDialogFragment dialogFragment = ScheduleDialogFragment.newInstance(
                medication.getId(), medication.getName(), schedule.getId());
        dialogFragment.setListener(this);
        dialogFragment.show(getChildFragmentManager(), "schedule_dialog");
    }
    
    private void confirmDeleteMedication(Medication medication) {
        if (medication != null && medication.getId() != null) {
            // Asegurarnos de liberar el compartimento correctamente
            int compartmentNumber = medication.getCompartmentNumber();
            if (compartmentNumber > 0) {
                // Si conocemos el número de compartimento, lo liberamos directamente
                compartmentManager.freeCompartment(compartmentNumber);
            } else {
                // Si no lo conocemos, intentamos buscarlo por el ID
                compartmentManager.freeMedicationCompartment(medication.getId());
            }
            
            // Eliminar el medicamento
            viewModel.deleteMedication(medication.getId());
            Toast.makeText(requireContext(), "Medicamento eliminado: " + medication.getName(), Toast.LENGTH_SHORT).show();
        }
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
    
    // Método adicional para crear un listener de Schedule
    private ScheduleAdapter.ScheduleListener createScheduleListener(Medication medication) {
        return new ScheduleAdapter.ScheduleListener() {
            @Override
            public void onScheduleClick(Schedule schedule) {
                // Abrir diálogo para editar el horario seleccionado
                showEditScheduleDialog(medication, schedule);
            }
            
            @Override
            public void onScheduleActiveChanged(Schedule schedule, boolean active) {
                // Actualizar el estado activo del horario
                schedule.setActive(active);
                viewModel.saveSchedule(medication.getId(), schedule);
            }
            
            public void onDispenseNowClick(Schedule schedule) {
                // Usar nuestro nuevo método para dispensar con feedback
                dispenseNowWithFeedback(medication, schedule);
            }
        };
    }

    @Override
    public void onScheduleSaved(Schedule schedule) {
        if (schedule != null && schedule.getMedicationId() != null) {
            try {
                // Guardar en la base de datos local
                viewModel.saveSchedule(schedule.getMedicationId(), schedule);
                Toast.makeText(requireContext(), "Horario guardado correctamente", Toast.LENGTH_SHORT).show();
                
                // Retrasar la sincronización para dar tiempo a que Firebase guarde los datos
                synchronizeAfterSave();
            } catch (Exception e) {
                String errorMsg = ErrorHandler.handleError(TAG, 
                    ErrorHandler.ERROR_DATABASE, 
                    "Error al guardar horario", e);
                showErrorMessage(errorMsg);
            }
        } else {
            ErrorHandler.handleValidationError(TAG, "schedule", 
                "Datos de horario incompletos");
        }
    }

    private void refreshCompartmentStatus() {
        // Utilizar el mismo método de repositorio que en loadData
        viewModel.getMedicationRepository().getMedications(patientId, new MedicationRepository.DataCallback<List<Medication>>() {
            @Override
            public void onSuccess(List<Medication> medications) {
                compartmentManager.refreshOccupation(medications);
            }
            
            @Override
            public void onError(String errorMessage) {
                String formattedError = ErrorHandler.handleDatabaseError(
                    TAG, "refreshCompartmentStatus", errorMessage);
                showErrorMessage(formattedError);
            }
        });
    }
    
    // Manejar botón de dispensar
    private void handleDispenseClick(Medication medication, Schedule schedule) {
        try {
            dispenserViewModel.dispenseNow(medication.getId(), schedule.getId(), mqttViewModel);
        } catch (Exception e) {
            String errorMsg = ErrorHandler.handleError(TAG, 
                ErrorHandler.ERROR_UNKNOWN, 
                "Error al dispensar medicación", e);
            showErrorMessage(errorMsg);
        }
    }

    // Manejar botón de sincronizar
    private void handleSyncClick() {
        try {
            // Verificar conexión antes de sincronizar
            if (mqttViewModel.getIsConnected().getValue() != null && 
                mqttViewModel.getIsConnected().getValue()) {
                
                // Mostrar diálogo de progreso
                showSyncingDialog();
                
                // Realizar la sincronización
                dispenserViewModel.syncSchedulesWithDispenser(mqttViewModel);
                
                // Mostrar confirmación después de un tiempo
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded() && !isDetached()) {
                        Snackbar.make(recyclerMedications, 
                            "Sincronización enviada al dispensador", 
                            Snackbar.LENGTH_SHORT).show();
                    }
                }, 1000);
            } else {
                // Mostrar error si no hay conexión
                showErrorMessage("No hay conexión con el dispensador. Compruebe que el dispositivo esté encendido.");
            }
        } catch (Exception e) {
            String errorMsg = ErrorHandler.handleError(TAG, 
                ErrorHandler.ERROR_UNKNOWN, 
                "Error al sincronizar con el dispensador", e);
            showErrorMessage(errorMsg);
        }
    }

    private void showSyncingDialog() {
        if (syncProgressDialog == null) {
            syncProgressDialog = ProgressDialogFragment.newInstance(
                "Sincronización", "Sincronizando horarios con el dispensador...");
            syncProgressDialog.setCancelable(false);
        }
        
        if (!syncProgressDialog.isAdded()) {
            syncProgressDialog.show(getParentFragmentManager(), "SyncDialog");
        }
    }

    private void dismissSyncingDialog() {
        if (syncProgressDialog != null && syncProgressDialog.isAdded()) {
            syncProgressDialog.dismiss();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Iniciar actualización automática
        if (adapter != null) {
            adapter.startAutoUpdate();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        
        // Detener actualización automática cuando el fragmento no está visible
        if (adapter != null) {
            adapter.stopAutoUpdate();
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Remover observadores asociados a la vista
        if (observerManager != null) {
            observerManager.removeObservers("medications_list");
            observerManager.removeObservers("ui_state");
            observerManager.removeObservers("dispenser_errors");
            observerManager.removeObservers("sync_progress");
            observerManager.removeObservers("mqtt_errors");
            observerManager.removeObservers("medication_dispensed");
            observerManager.removeObservers("connection_status");
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Liberar recursos
        if (adapter != null) {
            adapter.release();
        }
        
        dismissSyncingDialog();
        
        // Limpiar todos los observadores restantes
        if (observerManager != null) {
            observerManager.removeAllObservers();
        }
    }
    
    // Añade este método después de setupObservers()
    private void setupDispenseHandlers() {
        // Ya no es necesario este método porque los observadores se configuran en setupObservers()
        // Mantenemos el método para compatibilidad, pero podríamos eliminarlo o fusionarlo con setupObservers
    }

    // También necesitamos crear un método para manejar la dispensación
    public void dispenseNowWithFeedback(Medication medication, Schedule schedule) {
        if (medication == null || schedule == null) {
            ErrorHandler.handleError(TAG, 
                ErrorHandler.ERROR_VALIDATION, 
                "Medicamento o horario no válidos para dispensación", null);
            return;
        }
        
        // Mostrar un diálogo de progreso
        ProgressDialogFragment progressDialog = ProgressDialogFragment.newInstance(
            "Dispensando medicamento", 
            "Dispensando " + medication.getName() + "...");
        progressDialog.setCancelable(false);
        progressDialog.show(getChildFragmentManager(), "dispensing_dialog");
        
        try {
            // Dispensar el medicamento
            viewModel.dispenseNow(medication.getId(), schedule.getId(), mqttViewModel);
            
            // Cerrar el diálogo después de un tiempo y actualizar la UI
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded() && !isDetached()) {
                    progressDialog.dismiss();
                    
                    // Forzar una recarga de datos después de dispensar
                    viewModel.loadMedications(patientId);
                    
                    // Mostrar confirmación
                    Snackbar.make(recyclerMedications, 
                        "Se ha dispensado " + medication.getName(), 
                        Snackbar.LENGTH_SHORT).show();
                }
            }, 1500); // Esperar 1.5 segundos para darle tiempo a la DB
        } catch (Exception e) {
            // Manejar errores inesperados
            progressDialog.dismiss();
            String errorMsg = ErrorHandler.handleError(TAG, 
                ErrorHandler.ERROR_UNKNOWN, 
                "Error al dispensar medicación", e);
            showErrorMessage(errorMsg);
        }
    }
}