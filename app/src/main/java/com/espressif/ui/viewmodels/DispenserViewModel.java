package com.espressif.ui.viewmodels;

import android.app.Application;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.espressif.ui.models.Medication;
import com.espressif.ui.models.MedicationType;
import com.espressif.ui.models.Schedule;
import com.espressif.data.repository.MedicationRepository;
import com.espressif.ui.activities.mqtt_activities.MqttHandler;
import com.espressif.ui.activities.mqtt_activities.DeviceConnectionChecker;
import com.espressif.ui.utils.CompartmentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DispenserViewModel extends AndroidViewModel {

    private static final String TAG = "DispenserViewModel";
    
    // Repositorio para acceso a datos
    private final MedicationRepository medicationRepository;
    
    // Ejecutor para operaciones asíncronas
    private final Executor executor;
    
    // LiveData para los medicamentos
    private final MutableLiveData<List<Medication>> medications = new MutableLiveData<>();
    private final MutableLiveData<UIState> uiState = new MutableLiveData<>(UIState.LOADING);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    // Filtros
    private FilterType currentFilter = FilterType.ALL;
    private String patientId;
    
    // Estado del dispensador
    private final MutableLiveData<Boolean> dispenserConnected = new MutableLiveData<>(false);
    private final MutableLiveData<String> dispenserStatus = new MutableLiveData<>("Desconectado");
    
    private CompartmentManager compartmentManager;

    public DispenserViewModel(@NonNull Application application) {
        super(application);
        medicationRepository = MedicationRepository.getInstance();
        executor = Executors.newSingleThreadExecutor();
        compartmentManager = CompartmentManager.getInstance();
    }
    
    /**
     * Carga los medicamentos del paciente
     */
    public void loadMedications(String patientId) {
        this.patientId = patientId;
        
        if (patientId == null || patientId.isEmpty()) {
            setErrorState("ID del paciente no válido");
            return;
        }
        
        setLoadingState();
        
        medicationRepository.getMedications(patientId, new MedicationRepository.DataCallback<List<Medication>>() {
            @Override
            public void onSuccess(List<Medication> data) {
                compartmentManager.refreshOccupation(data);
                updateMedicationsList(data);
            }

            @Override
            public void onError(String errorMessage) {
                setErrorState(errorMessage);
            }
        });
    }
    
    /**
     * Inicia la escucha de cambios en los medicamentos
     */
    public void startListeningForMedications(String patientId) {
        this.patientId = patientId;
        
        if (patientId == null || patientId.isEmpty()) {
            setErrorState("ID del paciente no válido");
            return;
        }
        
        setLoadingState();
        
        medicationRepository.listenForMedications(patientId, new MedicationRepository.DataCallback<List<Medication>>() {
            @Override
            public void onSuccess(List<Medication> data) {
                updateMedicationsList(data);
            }

            @Override
            public void onError(String errorMessage) {
                setErrorState(errorMessage);
            }
        });
    }
    
    /**
     * Aplica filtros a la lista de medicamentos
     */
    private void updateMedicationsList(List<Medication> medicationList) {
        if (medicationList == null) {
            medicationList = new ArrayList<>();
        }
        
        // Aplicar filtros
        List<Medication> filteredList = new ArrayList<>();
        
        for (Medication medication : medicationList) {
            boolean include = true;
            
            switch (currentFilter) {
                case ALL:
                    // Incluir todos
                    break;
                case PILLS:
                    include = MedicationType.PILL.equals(medication.getType());
                    break;
                case LIQUIDS:
                    include = MedicationType.LIQUID.equals(medication.getType());
                    break;
                case TODAY:
                    include = hasTodaySchedule(medication);
                    break;
            }
            
            if (include) {
                filteredList.add(medication);
            }
        }
        
        // Ordenar por próximo horario
        Collections.sort(filteredList, new Comparator<Medication>() {
            @Override
            public int compare(Medication m1, Medication m2) {
                long nextTime1 = getNextScheduledTime(m1);
                long nextTime2 = getNextScheduledTime(m2);
                
                // Si no hay horarios, mover al final
                if (nextTime1 == Long.MAX_VALUE && nextTime2 == Long.MAX_VALUE) {
                    return m1.getName().compareTo(m2.getName());
                } else if (nextTime1 == Long.MAX_VALUE) {
                    return 1;
                } else if (nextTime2 == Long.MAX_VALUE) {
                    return -1;
                }
                
                return Long.compare(nextTime1, nextTime2);
            }
        });
        
        medications.postValue(filteredList);
        
        if (filteredList.isEmpty()) {
            uiState.postValue(UIState.EMPTY);
        } else {
            uiState.postValue(UIState.CONTENT);
        }
    }
    
    /**
     * Obtiene el próximo horario programado para un medicamento
     */
    private long getNextScheduledTime(Medication medication) {
        long nextTime = Long.MAX_VALUE;
        
        for (Schedule schedule : medication.getScheduleList()) {
            if (schedule.isActive() && schedule.getNextScheduled() < nextTime) {
                nextTime = schedule.getNextScheduled();
            }
        }
        
        return nextTime;
    }
    
    /**
     * Verifica si un medicamento tiene horarios para hoy
     */
    private boolean hasTodaySchedule(Medication medication) {
        for (Schedule schedule : medication.getScheduleList()) {
            if (schedule.isActive() && schedule.isForToday()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Cambia el filtro actual
     */
    public void setFilter(FilterType filterType) {
        this.currentFilter = filterType;
        
        // Recargar con el nuevo filtro
        if (patientId != null) {
            medicationRepository.getMedications(patientId, new MedicationRepository.DataCallback<List<Medication>>() {
                @Override
                public void onSuccess(List<Medication> data) {
                    updateMedicationsList(data);
                }
    
                @Override
                public void onError(String errorMessage) {
                    // Mantener datos existentes y mostrar error
                    DispenserViewModel.this.errorMessage.postValue(errorMessage);
                }
            });
        }
    }
    
    /**
     * Crea un nuevo medicamento
     */
    public void createMedication(Medication medication) {
        if (patientId == null) {
            errorMessage.postValue("No hay un paciente seleccionado");
            return;
        }
        
        medication.setPatientId(patientId);
        
        executor.execute(() -> {
            medicationRepository.addMedication(medication, new MedicationRepository.DatabaseCallback() {
                @Override
                public void onSuccess() {
                    // La lista se actualizará automáticamente si se está escuchando
                    Log.d(TAG, "Medicamento creado: " + medication.getName());
                    // Aquí podríamos enviar una notificación MQTT al dispensador
                }
    
                @Override
                public void onError(String errorMsg) {
                    errorMessage.postValue("Error al crear medicamento: " + errorMsg);
                }
            });
        });
    }
    
    /**
     * Actualiza un medicamento existente
     */
    public void updateMedication(Medication medication) {
        if (patientId == null) {
            errorMessage.postValue("No hay un paciente seleccionado");
            return;
        }
        
        medication.setPatientId(patientId);
        
        executor.execute(() -> {
            medicationRepository.updateMedication(medication, new MedicationRepository.DatabaseCallback() {
                @Override
                public void onSuccess() {
                    // La lista se actualizará automáticamente si se está escuchando
                    Log.d(TAG, "Medicamento actualizado: " + medication.getName());
                    // Aquí podríamos enviar una notificación MQTT al dispensador
                }
    
                @Override
                public void onError(String errorMsg) {
                    errorMessage.postValue("Error al actualizar medicamento: " + errorMsg);
                }
            });
        });
    }
    
    /**
     * Elimina un medicamento
     */
    public void deleteMedication(String medicationId) {
        if (patientId == null || medicationId == null) {
            errorMessage.postValue("ID no válido");
            return;
        }
        
        executor.execute(() -> {
            medicationRepository.deleteMedication(patientId, medicationId, new MedicationRepository.DatabaseCallback() {
                @Override
                public void onSuccess() {
                    // La lista se actualizará automáticamente si se está escuchando
                    Log.d(TAG, "Medicamento eliminado: " + medicationId);
                    // Aquí podríamos enviar una notificación MQTT al dispensador
                }
    
                @Override
                public void onError(String errorMsg) {
                    errorMessage.postValue("Error al eliminar medicamento: " + errorMsg);
                }
            });
        });
    }
    
    /**
     * Guarda un horario para un medicamento
     */
    public void saveSchedule(String medicationId, Schedule schedule) {
        if (patientId == null || medicationId == null) {
            errorMessage.postValue("ID no válido");
            return;
        }
        
        executor.execute(() -> {
            medicationRepository.saveSchedule(patientId, medicationId, schedule, new MedicationRepository.DatabaseCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Horario guardado para medicamento: " + medicationId);
                    // Aquí podríamos enviar una notificación MQTT al dispensador
                }
    
                @Override
                public void onError(String errorMsg) {
                    errorMessage.postValue("Error al guardar horario: " + errorMsg);
                }
            });
        });
    }
    
    /**
     * Elimina un horario
     */
    public void deleteSchedule(String medicationId, String scheduleId) {
        if (patientId == null || medicationId == null || scheduleId == null) {
            errorMessage.postValue("ID no válido");
            return;
        }
        
        executor.execute(() -> {
            medicationRepository.deleteSchedule(patientId, medicationId, scheduleId, new MedicationRepository.DatabaseCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Horario eliminado: " + scheduleId);
                    // Aquí podríamos enviar una notificación MQTT al dispensador
                }
    
                @Override
                public void onError(String errorMsg) {
                    errorMessage.postValue("Error al eliminar horario: " + errorMsg);
                }
            });
        });
    }
    
    /**
     * Confirma la toma de un medicamento en un horario específico
     */
    public void confirmMedicationTaken(String medicationId, String scheduleId, boolean taken) {
        if (patientId == null || medicationId == null || scheduleId == null) {
            errorMessage.postValue("ID no válido");
            return;
        }
        
        executor.execute(() -> {
            medicationRepository.updateMedicationTaken(patientId, medicationId, scheduleId, taken, new MedicationRepository.DatabaseCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Estado de toma actualizado: " + medicationId + ", " + scheduleId + " = " + taken);
                }
    
                @Override
                public void onError(String errorMsg) {
                    errorMessage.postValue("Error al actualizar estado de toma: " + errorMsg);
                }
            });
        });
    }
    
    /**
     * Simula una dispensación manual
     * Esto sería reemplazado por una comunicación real con el dispensador vía MQTT
     */
    public void dispenseNow(String medicationId, String scheduleId) {
        if (patientId == null || medicationId == null || scheduleId == null) {
            errorMessage.postValue("ID no válido");
            return;
        }
        
        // TODO: Enviar comando MQTT al dispensador
        Log.d(TAG, "Enviando comando para dispensar: " + medicationId);
        
        // Por ahora simulamos la dispensación actualizando el estado
        executor.execute(() -> {
            medicationRepository.markAsDispensed(patientId, medicationId, scheduleId, new MedicationRepository.DatabaseCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Medicamento marcado como dispensado: " + medicationId);
                }
    
                @Override
                public void onError(String errorMsg) {
                    errorMessage.postValue("Error al marcar dispensación: " + errorMsg);
                }
            });
        });
    }
    
    /**
     * Actualizará el estado de conexión con el dispensador
     * Esto será llamado desde la lógica de MQTT
     */
    public void updateDispenserConnectionStatus(boolean connected, String status) {
        dispenserConnected.postValue(connected);
        dispenserStatus.postValue(status);
    }
    
    // Métodos para cambiar el estado de la UI
    private void setLoadingState() {
        uiState.setValue(UIState.LOADING);
    }
    
    private void setErrorState(String error) {
        errorMessage.setValue(error);
        uiState.setValue(UIState.ERROR);
    }
    
    // Getters para los LiveData
    public LiveData<List<Medication>> getMedications() {
        return medications;
    }
    
    public LiveData<UIState> getUiState() {
        return uiState;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<Boolean> getDispenserConnected() {
        return dispenserConnected;
    }
    
    public LiveData<String> getDispenserStatus() {
        return dispenserStatus;
    }
    
    // Enum para estados de la UI
    public enum UIState {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }
    
    // Enum para filtros
    public enum FilterType {
        ALL,
        PILLS,
        LIQUIDS,
        TODAY
    }

    // 1. AÑADIR un método para obtener el repositorio (si no existe ya):
    public MedicationRepository getMedicationRepository() {
        return medicationRepository;
    }

    /**
     * Sincroniza el estado entre MqttViewModel y DispenserViewModel
     * Esto debe ser llamado desde el Fragment o Activity principal
     */
    public void connectWithMqttViewModel(MqttViewModel mqttViewModel) {
        // Observar el estado de conexión
        mqttViewModel.getIsConnected().observeForever(connected -> {
            updateDispenserConnectionStatus(connected, 
                    connected ? "Conectado" : "Desconectado");
        });
        
        // Observar el mensaje de estado
        mqttViewModel.getStatusMessage().observeForever(status -> {
            updateDispenserConnectionStatus(
                    mqttViewModel.getIsConnected().getValue() != null && 
                    mqttViewModel.getIsConnected().getValue(),
                    status
            );
        });
        
        // Observar mensajes de error
        mqttViewModel.getErrorMessage().observeForever(error -> {
            if (error != null && !error.isEmpty()) {
                errorMessage.setValue(error);
            }
        });
    }
    
    /**
     * Simula una dispensación manual - Actualizado para usar MqttViewModel
     */
    public void dispenseNow(String medicationId, String scheduleId, MqttViewModel mqttViewModel) {
        if (patientId == null || medicationId == null || scheduleId == null) {
            errorMessage.postValue("ID no válido");
            return;
        }
        
        // Enviar comando MQTT al dispensador
        mqttViewModel.dispenseNow(medicationId, scheduleId);
        
        // Actualizar el conteo de pastillas
        updatePillCount(medicationId);
        
        // También actualizar la base de datos con el estado de dispensación
        executor.execute(() -> {
            medicationRepository.markAsDispensed(patientId, medicationId, scheduleId, new MedicationRepository.DatabaseCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Estado de dispensación actualizado correctamente");
                }
                
                @Override
                public void onError(String errorMsg) {
                    errorMessage.postValue("Error al actualizar estado de dispensación: " + errorMsg);
                }
            });
        });
    }

    /**
     * Sincroniza los horarios con el dispensador consultando directamente el repositorio
     */
    public void syncSchedulesWithDispenser(MqttViewModel mqttViewModel) {
        if (patientId == null || patientId.isEmpty()) {
            errorMessage.postValue("ID del paciente no válido");
            return;
        }
        
        // Obtener los medicamentos directamente del repositorio
        Log.d(TAG, "Solicitando medicamentos actualizados para sincronizar");
        
        medicationRepository.getMedications(patientId, new MedicationRepository.DataCallback<List<Medication>>() {
            @Override
            public void onSuccess(List<Medication> data) {
                if (data == null || data.isEmpty()) {
                    Log.e(TAG, "No hay medicamentos para sincronizar");
                    errorMessage.postValue("No hay medicamentos para sincronizar");
                    return;
                }
                
                Log.d(TAG, "Enviando " + data.size() + " medicamentos para sincronización MQTT");
                mqttViewModel.syncSchedules(data);
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error al obtener medicamentos para sincronizar: " + errorMessage);
                DispenserViewModel.this.errorMessage.postValue("Error al sincronizar: " + errorMessage);
            }
        });
    }

    /**
     * Actualiza el conteo de pastillas después de dispensar
     */
    private void updatePillCount(String medicationId) {
        if (patientId == null || medicationId == null) {
            return;
        }
        
        // Obtener el medicamento actual
        executor.execute(() -> {
            medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
                @Override
                public void onSuccess(Medication medication) {
                    if (medication != null) {
                        // Llamar al método dispenseDose que disminuye los contadores
                        medication.dispenseDose();
                        
                        // Guardar los cambios en la base de datos
                        updateMedication(medication);
                        
                        Log.d(TAG, "Pastillas actualizadas para " + medication.getName() + 
                              ". Quedan: " + medication.getTotalPills() + " pastillas, " +
                              medication.calculateRemainingDoses() + " dosis.");
                    }
                }
                
                @Override
                public void onError(String errorMsg) {
                    Log.e(TAG, "Error al obtener medicamento para actualizar conteo: " + errorMsg);
                    // Notificar al usuario - usar la variable de la clase, no el parámetro
                    DispenserViewModel.this.errorMessage.postValue("Error al actualizar conteo de pastillas: " + errorMsg);
                }
            });
        });
    }

    /**
     * Actualiza la cantidad total de pastillas para un medicamento (recarga)
     */
    public void refillMedication(String medicationId, int newTotalPills) {
        if (patientId == null || medicationId == null) {
            errorMessage.postValue("ID no válido");
            return;
        }
        
        executor.execute(() -> {
            medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
                @Override
                public void onSuccess(Medication medication) {
                    if (medication != null) {
                        // Actualizar el total de pastillas
                        medication.setTotalPills(newTotalPills);
                        medication.updateRemainingDoses();
                        
                        // Guardar los cambios
                        updateMedication(medication);
                        
                        Log.d(TAG, "Compartimento rellenado para " + medication.getName() + 
                              ". Nuevo total: " + newTotalPills);
                    }
                }
                
                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error al obtener medicamento para rellenar: " + errorMessage);
                    DispenserViewModel.this.errorMessage.postValue("Error al rellenar: " + errorMessage);
                }
            });
        });
    }
}