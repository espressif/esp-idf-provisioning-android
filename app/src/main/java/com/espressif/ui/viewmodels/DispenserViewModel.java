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

    // Datos de compartimentos para los gráficos
    public static class CompartmentData {
        private int taken;
        private int total;
        
        public CompartmentData(int taken, int total) {
            this.taken = taken;
            this.total = total;
        }
        
        public int getTaken() { return taken; }
        public int getTotal() { return total; }
        public int getRemaining() { return total - taken; }
    }
    
    // LiveData para cada compartimento
    private final MutableLiveData<CompartmentData> compartmentA = new MutableLiveData<>(new CompartmentData(0, 4));
    private final MutableLiveData<CompartmentData> compartmentB = new MutableLiveData<>(new CompartmentData(0, 3));
    private final MutableLiveData<CompartmentData> compartmentC = new MutableLiveData<>(new CompartmentData(0, 2));
    private final MutableLiveData<CompartmentData> compartmentLiquid = new MutableLiveData<>(new CompartmentData(0, 300));
    
    // Getters para LiveData de compartimentos
    public LiveData<CompartmentData> getCompartmentA() { return compartmentA; }
    public LiveData<CompartmentData> getCompartmentB() { return compartmentB; }
    public LiveData<CompartmentData> getCompartmentC() { return compartmentC; }
    public LiveData<CompartmentData> getCompartmentLiquid() { return compartmentLiquid; }
    
    // Métodos para actualizar datos de compartimentos
    public void updateCompartmentA(int taken, int total) {
        compartmentA.setValue(new CompartmentData(taken, total));
    }
    
    public void updateCompartmentB(int taken, int total) {
        compartmentB.setValue(new CompartmentData(taken, total));
    }
    
    public void updateCompartmentC(int taken, int total) {
        compartmentC.setValue(new CompartmentData(taken, total));
    }
    
    public void updateCompartmentLiquid(int taken, int total) {
        compartmentLiquid.setValue(new CompartmentData(taken, total));
    }

    /**
     * Actualiza los datos de compartimentos basándose en los medicamentos
     * Esta versión mejorada hace log de lo que está procesando
     */
    public void updateCompartmentStatus(List<Medication> medications) {
        if (medications == null || medications.isEmpty()) {
            Log.d(TAG, "updateCompartmentStatus: lista de medicamentos vacía o nula");
            // Valores por defecto para evitar nulls
            updateCompartmentA(0, 1);
            updateCompartmentB(0, 1);
            updateCompartmentC(0, 1);
            updateCompartmentLiquid(0, 1);
            return;
        }
        
        int takenA = 0, totalA = 0;
        int takenB = 0, totalB = 0;
        int takenC = 0, totalC = 0;
        int takenLiquid = 0, totalLiquid = 0;
        
        Log.d(TAG, "Procesando " + medications.size() + " medicamentos para actualizar compartimentos");
        
        for (Medication medication : medications) {
            // Filtrar solo los de hoy para el conteo
            if (!hasTodaySchedule(medication)) {
                continue;
            }
            
            String compartment = medication.getCompartment();
            if (compartment == null) {
                Log.d(TAG, "Medicamento sin compartimento asignado: " + medication.getName());
                continue;
            }
            
            // Contabilizar según el compartimento asignado
            switch (compartment) {
                case "A":
                    if (medication.getType() == MedicationType.PILL) {
                        takenA += medication.getDosesTaken();
                        totalA += medication.getTotalDoses();
                        Log.d(TAG, "Compartimento A: " + medication.getName() + ", tomadas: " + 
                               medication.getDosesTaken() + ", total: " + medication.getTotalDoses() +
                               ", total pastillas: " + medication.getTotalPills());
                    }
                    break;
                case "B":
                    if (medication.getType() == MedicationType.PILL) {
                        takenB += medication.getDosesTaken();
                        totalB += medication.getTotalDoses();
                        Log.d(TAG, "Compartimento B: " + medication.getName() + ", tomadas: " + 
                               medication.getDosesTaken() + ", total: " + medication.getTotalDoses() +
                               ", total pastillas: " + medication.getTotalPills());
                    }
                    break;
                case "C":
                    if (medication.getType() == MedicationType.PILL) {
                        takenC += medication.getDosesTaken();
                        totalC += medication.getTotalDoses();
                        Log.d(TAG, "Compartimento C: " + medication.getName() + ", tomadas: " + 
                               medication.getDosesTaken() + ", total: " + medication.getTotalDoses() +
                               ", total pastillas: " + medication.getTotalPills());
                    }
                    break;
                case "LIQUID":
                    if (medication.getType() == MedicationType.LIQUID) {
                        takenLiquid += medication.getVolumeTaken();
                        totalLiquid += medication.getTotalVolume();
                        Log.d(TAG, "Compartimento LIQUID: " + medication.getName() + ", tomado: " + 
                               medication.getVolumeTaken() + ", total: " + medication.getTotalVolume());
                    }
                    break;
            }
        }
        
        // Establecer valores mínimos para evitar divisiones por cero
        totalA = Math.max(totalA, 1);
        totalB = Math.max(totalB, 1);
        totalC = Math.max(totalC, 1);
        totalLiquid = Math.max(totalLiquid, 1);
        
        // Actualizar los LiveData
        Log.d(TAG, "Actualizando compartimento A: " + takenA + "/" + totalA);
        updateCompartmentA(takenA, totalA);
        
        Log.d(TAG, "Actualizando compartimento B: " + takenB + "/" + totalB);
        updateCompartmentB(takenB, totalB);
        
        Log.d(TAG, "Actualizando compartimento C: " + takenC + "/" + totalC);
        updateCompartmentC(takenC, totalC);
        
        Log.d(TAG, "Actualizando compartimento LIQUID: " + takenLiquid + "/" + totalLiquid);
        updateCompartmentLiquid(takenLiquid, totalLiquid);
    }
    
    /**
     * Actualiza los datos de un compartimento específico cuando se toma una medicación
     */
    public void updateCompartmentAfterDispense(Medication medication) {
        if (medication == null) return;
        
        String compartment = medication.getCompartment();
        if (compartment == null) return;
        
        switch (compartment) {
            case "A":
                CompartmentData dataA = compartmentA.getValue();
                if (dataA != null) {
                    updateCompartmentA(dataA.getTaken() + 1, dataA.getTotal());
                }
                break;
            case "B":
                CompartmentData dataB = compartmentB.getValue();
                if (dataB != null) {
                    updateCompartmentB(dataB.getTaken() + 1, dataB.getTotal());
                }
                break;
            case "C":
                CompartmentData dataC = compartmentC.getValue();
                if (dataC != null) {
                    updateCompartmentC(dataC.getTaken() + 1, dataC.getTotal());
                }
                break;
            case "LIQUID":
                CompartmentData dataL = compartmentLiquid.getValue();
                if (dataL != null && medication.getType() == MedicationType.LIQUID) {
                    int doseVolume = medication.getDoseVolume();
                    updateCompartmentLiquid(dataL.getTaken() + doseVolume, dataL.getTotal());
                }
                break;
        }
    }

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
        
        // Actualizar los datos de los compartimentos
        updateCompartmentStatus(medicationList);
        
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
    /**
     * Simula una dispensación manual - Optimización
     */
    public void dispenseNow(String medicationId, String scheduleId, MqttViewModel mqttViewModel) {
        if (patientId == null || medicationId == null || scheduleId == null) {
            errorMessage.postValue("ID no válido");
            return;
        }
        
        // Enviar comando MQTT al dispensador
        if (mqttViewModel != null) {
            mqttViewModel.dispenseNow(medicationId, scheduleId);
        }
        
        executor.execute(() -> {
            // Primero obtener el medicamento actual
            medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
                @Override
                public void onSuccess(Medication medication) {
                    if (medication != null) {
                        // Guardar la referencia al medicamento
                        final Medication medicationRef = medication;
                        
                        Log.d(TAG, "Antes de dispensar: " + medication.getName() + 
                              ", Pills: " + medication.getTotalPills() + 
                              ", Doses taken: " + medication.getDosesTaken());
                        
                        // Actualizar el conteo de pastillas
                        medication.dispenseDose();
                        
                        Log.d(TAG, "Después de dispensar: " + medication.getName() + 
                              ", Pills: " + medication.getTotalPills() + 
                              ", Doses taken: " + medication.getDosesTaken());
                        
                        // Actualizar en la base de datos - IMPORTANTE: Hacerlo en la DB ANTES de actualizar la UI
                        medicationRepository.updateMedication(medication, new MedicationRepository.DatabaseCallback() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Pastillas actualizadas para " + medicationRef.getName() + 
                                      ". Quedan: " + medicationRef.getTotalPills() + " pastillas, " +
                                      medicationRef.calculateRemainingDoses() + " dosis.");
                                
                                // Actualizar el compartimento correspondiente
                                updateCompartmentAfterDispense(medicationRef);
                                
                                // Marcar como dispensado en el horario
                                medicationRepository.markAsDispensed(patientId, medicationId, scheduleId, new MedicationRepository.DatabaseCallback() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "Estado de dispensación actualizado correctamente");
                                        
                                        // FORZAR una recarga completa para actualizar la UI
                                        loadMedications(patientId);
                                    }
                                    
                                    @Override
                                    public void onError(String errorMsg) {
                                        errorMessage.postValue("Error al actualizar estado de dispensación: " + errorMsg);
                                    }
                                });
                            }
                            
                            @Override
                            public void onError(String errorMsg) {
                                errorMessage.postValue("Error al actualizar medicamento: " + errorMsg);
                            }
                        });
                    } else {
                        errorMessage.postValue("No se encontró el medicamento con ID: " + medicationId);
                    }
                }
                
                @Override
                public void onError(String errorMsg) {
                    errorMessage.postValue("Error al obtener medicamento: " + errorMsg);
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

    // Añadir getter para patientId
    public String getPatientId() {
        return patientId;
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