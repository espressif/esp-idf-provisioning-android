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
import com.espressif.ui.viewmodels.MqttViewModel;

import java.util.List;

public class DispenserFragment extends Fragment implements 
        MedicationAdapter.MedicationListener,
        DialogMedication.MedicationDialogListener,
        ScheduleDialogFragment.ScheduleDialogListener {

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

    // Añade las siguientes declaraciones de variables en la clase DispenserFragment
    private DispenserViewModel dispenserViewModel;
    private MqttViewModel mqttViewModel;

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
        
        // Inicializar el gestor de compartimentos
        compartmentManager = CompartmentManager.getInstance();
        
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
        
        // Configurar el DispenserViewModel
        dispenserViewModel = new ViewModelProvider(requireActivity()).get(DispenserViewModel.class);
        
        // Configurar el MqttViewModel
        mqttViewModel = new ViewModelProvider(requireActivity()).get(MqttViewModel.class);
        
        // Conectar los ViewModels
        dispenserViewModel.connectWithMqttViewModel(mqttViewModel);
        
        // Configurar observadores para la interfaz de usuario
        setupObservers();
        
        // Iniciar la conexión MQTT
        mqttViewModel.connect();
        
        // Cargar medicamentos
        String patientId = getCurrentPatientId();
        dispenserViewModel.loadMedications(patientId);
        
        // Agregar nuestros nuevos manejadores de dispensación
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
    }
    
    private void loadData() {
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
                // Manejar error si es necesario
                showErrorMessage("Error al cargar medicamentos: " + errorMessage);
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
            
            // Marcar el compartimento como ocupado
            compartmentManager.occupyCompartment(medication.getCompartmentNumber(), medication.getId());
            
            Toast.makeText(requireContext(), "Medicamento añadido", Toast.LENGTH_SHORT).show();
        } else {
            // Es una actualización
            // Verificar si cambió el compartimento
            viewModel.getMedicationRepository().getMedication(patientId, medication.getId(), 
                new MedicationRepository.DataCallback<Medication>() {
                    @Override
                    public void onSuccess(Medication oldMedication) {
                        if (oldMedication != null && 
                            oldMedication.getCompartmentNumber() != medication.getCompartmentNumber()) {
                            // Liberar el compartimento anterior
                            compartmentManager.freeCompartment(oldMedication.getCompartmentNumber());
                            // Ocupar el nuevo compartimento
                            compartmentManager.occupyCompartment(medication.getCompartmentNumber(), 
                                                                medication.getId());
                        }
                        // Actualizar el medicamento
                        viewModel.updateMedication(medication);
                    }
                    
                    @Override
                    public void onError(String errorMessage) {
                        // Si hay error al obtener el medicamento anterior, 
                        // simplemente actualizamos el nuevo
                        viewModel.updateMedication(medication);
                    }
                });
            
            Toast.makeText(requireContext(), "Medicamento actualizado", Toast.LENGTH_SHORT).show();
        }
        
        // Añadir esta sección para sincronizar después de guardar
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded() && !isDetached()) {
                // Sincronizar con el dispensador
                Log.d("DispenserFragment", "Sincronizando después de guardar medicamento...");
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
    
    // Método adicional para crear un listener de Schedule (si decides implementarlo en el fragmento)
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
            
            // Añadir un método para manejar la dispensación
            @Override
            public void onDispenseNowClick(Schedule schedule) {
                // Usar nuestro nuevo método para dispensar con feedback
                dispenseNowWithFeedback(medication, schedule);
            }
        };
    }

    @Override
    public void onScheduleSaved(Schedule schedule) {
        if (schedule != null && schedule.getMedicationId() != null) {
            // Guardar en la base de datos local
            viewModel.saveSchedule(schedule.getMedicationId(), schedule);
            Toast.makeText(requireContext(), "Horario guardado correctamente", Toast.LENGTH_SHORT).show();
            
            // Retrasar la sincronización para dar tiempo a que Firebase guarde los datos
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded() && !isDetached()) {
                    // FORZAR LA SINCRONIZACIÓN con el dispensador
                    Log.d("DispenserFragment", "Iniciando sincronización con dispensador...");
                    dispenserViewModel.syncSchedulesWithDispenser(mqttViewModel);
                }
            }, 1500);
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
                // Opcional: manejar error
                showErrorMessage("Error al actualizar estado de compartimentos: " + errorMessage);
            }
        });
    }
    
    private void setupObservers() {
        // Configurar los observadores existentes de dispenserViewModel...
        
        // Añadir observadores para MqttViewModel
        mqttViewModel.getIsSyncingSchedules().observe(getViewLifecycleOwner(), isSyncing -> {
            if (isSyncing) {
                showSyncingDialog();
            } else {
                dismissSyncingDialog();
            }
        });
    }

    // Manejar botón de dispensar
    private void handleDispenseClick(Medication medication, Schedule schedule) {
        dispenserViewModel.dispenseNow(medication.getId(), schedule.getId(), mqttViewModel);
    }

    // Manejar botón de sincronizar
    private void handleSyncClick() {
        dispenserViewModel.syncSchedulesWithDispenser(mqttViewModel);
    }

    // Mostrar diálogo durante la sincronización
    private ProgressDialogFragment syncProgressDialog;

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
    public void onDestroy() {
        super.onDestroy();
        
        // Liberar recursos
        if (adapter != null) {
            adapter.release();
        }
        
        dismissSyncingDialog();
    }
    
    // Añade este método después de setupObservers()
    private void setupDispenseHandlers() {
        // Observar cambios en el conjunto de datos para actualizar la UI
        viewModel.getMedications().observe(getViewLifecycleOwner(), medications -> {
            adapter.setMedications(medications);
            // Después de actualizar el adaptador, notificar cambios
            adapter.notifyDataSetChanged();
        });
    }

    // También necesitamos crear un método para manejar la dispensación
    public void dispenseNowWithFeedback(Medication medication, Schedule schedule) {
        // Mostrar un diálogo de progreso
        ProgressDialogFragment progressDialog = ProgressDialogFragment.newInstance(
            "Dispensando medicamento", 
            "Dispensando " + medication.getName() + "...");
        progressDialog.setCancelable(false);
        progressDialog.show(getChildFragmentManager(), "dispensing_dialog");
        
        // Dispensar el medicamento
        viewModel.dispenseNow(medication.getId(), schedule.getId(), mqttViewModel);
        
        // Cerrar el diálogo después de un tiempo y actualizar la UI
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded() && !isDetached()) {
                progressDialog.dismiss();
                
                // Forzar una recarga de datos después de dispensar
                viewModel.loadMedications(patientId);
                
                // Notificar al adaptador de cambios
                adapter.notifyDataSetChanged();
                
                // Mostrar confirmación
                Snackbar.make(recyclerMedications, 
                    "Se ha dispensado " + medication.getName(), 
                    Snackbar.LENGTH_SHORT).show();
            }
        }, 1500); // Esperar 1.5 segundos para darle tiempo a la DB
    }
}