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
import com.espressif.ui.utils.ErrorHandler;
import com.espressif.ui.utils.ObserverManager;

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
    
    // Gestor de observadores
    private ObserverManager observerManager = new ObserverManager();

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
     */
    public void updateCompartmentStatus(List<Medication> medications) {
        if (medications == null || medications.isEmpty()) {
            Log.d(TAG, "updateCompartmentStatus: no hay medicamentos disponibles");
            // Valores por defecto para evitar nulls
            updateCompartmentA(0, 1);
            updateCompartmentB(0, 1);
            updateCompartmentC(0, 1);
            updateCompartmentLiquid(0, 1);
            return;
        }
        
        int[] takenA = {0}, totalA = {0};
        int[] takenB = {0}, totalB = {0};
        int[] takenC = {0}, totalC = {0};
        int[] takenLiquid = {0}, totalLiquid = {0};
        
        // Un solo log resumido al inicio
        Log.d(TAG, "Procesando " + medications.size() + " medicamentos para actualizar compartimentos");

        for (Medication medication : medications) {
            String compartment = medication.getCompartment();
            if (compartment == null) {
                Log.d(TAG, "Medicamento sin compartimento: " + medication.getName());
                continue;
            }
            
            // Contabilizar según el compartimento asignado utilizando el método helper
            switch (compartment) {
                case "A": updateCompartmentForMedication(compartment, medication, takenA, totalA); break;
                case "B": updateCompartmentForMedication(compartment, medication, takenB, totalB); break;
                case "C": updateCompartmentForMedication(compartment, medication, takenC, totalC); break;
                case "LIQUID": updateCompartmentForMedication(compartment, medication, takenLiquid, totalLiquid); break;
            }
        }
        
        // Establecer valores mínimos para evitar divisiones por cero
        totalA[0] = Math.max(totalA[0], 1);
        totalB[0] = Math.max(totalB[0], 1);
        totalC[0] = Math.max(totalC[0], 1);
        totalLiquid[0] = Math.max(totalLiquid[0], 1);
        
        // Actualizar los LiveData con un único patrón de log
        updateCompartmentA(takenA[0], totalA[0]);
        updateCompartmentB(takenB[0], totalB[0]);
        updateCompartmentC(takenC[0], totalC[0]);
        updateCompartmentLiquid(takenLiquid[0], totalLiquid[0]);
        
        // Log final resumiendo todo el proceso
        Log.d(TAG, String.format("Compartimentos actualizados: A=%d/%d, B=%d/%d, C=%d/%d, L=%d/%d", 
              takenA[0], totalA[0], takenB[0], totalB[0], takenC[0], totalC[0], takenLiquid[0], totalLiquid[0]));
    }

    /**
     * Actualiza los datos de un compartimento específico cuando se toma una medicación
     */
    public void updateCompartmentAfterDispense(Medication medication) {
        if (medication == null || medication.getCompartment() == null) return;
        
        String compartment = medication.getCompartment();
        
        // Un solo log resumido para la operación
        Log.d(TAG, "Actualizando compartimento " + compartment + " tras dispensar " + medication.getName());
        
        MutableLiveData<CompartmentData> compartmentLiveData = null;
        
        // Determinar qué compartimento actualizar
        switch (compartment) {
            case "A": compartmentLiveData = compartmentA; break;
            case "B": compartmentLiveData = compartmentB; break;
            case "C": compartmentLiveData = compartmentC; break;
            case "LIQUID": compartmentLiveData = compartmentLiquid; break;
            default:
                Log.w(TAG, "Compartimento desconocido: " + compartment);
                return;
        }
        
        // Si encontramos el compartimento, actualizarlo
        if (compartmentLiveData != null) {
            CompartmentData currentData = compartmentLiveData.getValue();
            if (currentData != null) {
                CompartmentData newData = new CompartmentData(
                    currentData.getTaken() + 1, 
                    Math.max(0, currentData.getTotal() - 1)
                );
                compartmentLiveData.setValue(newData);
            }
        }
        
        // Después de actualizar, validar consistencia
        validateCompartmentData();
    }

    /**
     * Asegura que los datos de los compartimentos sean consistentes
     */
    private void validateCompartmentData() {
        // Asegurar que taken nunca exceda total
        CompartmentData dataA = compartmentA.getValue();
        if (dataA != null && dataA.getTaken() > dataA.getTotal()) {
            updateCompartmentA(dataA.getTotal(), dataA.getTotal());
        }
        
        CompartmentData dataB = compartmentB.getValue();
        if (dataB != null && dataB.getTaken() > dataB.getTotal()) {
            updateCompartmentB(dataB.getTotal(), dataB.getTotal());
        }
        
        CompartmentData dataC = compartmentC.getValue();
        if (dataC != null && dataC.getTaken() > dataC.getTotal()) {
            updateCompartmentC(dataC.getTotal(), dataC.getTotal());
        }
        
        CompartmentData dataLiquid = compartmentLiquid.getValue();
        if (dataLiquid != null && dataLiquid.getTaken() > dataLiquid.getTotal()) {
            updateCompartmentLiquid(dataLiquid.getTotal(), dataLiquid.getTotal());
        }
    }

    // Agregar al principio de la clase
    private com.espressif.data.repository.UserRepository userRepository;

    // Modificar el constructor para inicializar userRepository
    public DispenserViewModel(@NonNull Application application) {
        super(application);
        medicationRepository = MedicationRepository.getInstance();
        executor = Executors.newSingleThreadExecutor();
        compartmentManager = CompartmentManager.getInstance();
        userRepository = com.espressif.data.repository.UserRepository.getInstance(application);
    }
    
    /**
     * Carga los medicamentos del paciente
     */
    public void loadMedications(String patientId) {
        // Validar que no se use el valor problemático
        if ("current_user_id".equals(patientId)) {
            String errorMsg = "ID de paciente inválido (current_user_id)";
            Log.e(TAG, "⛔ " + errorMsg);
            setErrorState(errorMsg);
            return;
        }
        
        this.patientId = patientId;
        
        if (patientId == null || patientId.isEmpty()) {
            String errorMsg = ErrorHandler.handleValidationError(TAG, "patientId", "ID del paciente no válido");
            setErrorState(errorMsg);
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
            public void onError(String errorMsg) {
                String formattedError = ErrorHandler.handleDatabaseError(TAG, "loadMedications", errorMsg);
                setErrorState(formattedError);
            }
        });
    }
    
    /**
     * Inicia la escucha de cambios en los medicamentos
     */
    public void startListeningForMedications(String patientId) {
        // Añadir esta validación
        if ("current_user_id".equals(patientId)) {
            String errorMsg = "ID de paciente inválido (current_user_id)";
            Log.e(TAG, "⛔ " + errorMsg);
            setErrorState(errorMsg);
            return;
        }
        
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
     * Envía solicitud de dispensación y actualiza inventario local
     */
    public void dispenseNow(String medicationId, String scheduleId, MqttViewModel mqttViewModel) {
        if (patientId == null || medicationId == null || scheduleId == null) {
            ErrorHandler.publishError(errorMessage, TAG, ErrorHandler.ERROR_VALIDATION, 
                                 "ID no válido para dispensación", null);
            return;
        }
        
        // Enviar comando MQTT al dispensador
        if (mqttViewModel != null) {
            mqttViewModel.dispenseNow(medicationId, scheduleId);
        }
        
        Handler mainHandler = new Handler(Looper.getMainLooper());
        
        executor.execute(() -> {
            try {
                // Obtener el medicamento actual
                medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
                    @Override
                    public void onSuccess(Medication medication) {
                        if (medication == null) {
                            errorMessage.postValue("Medicamento no encontrado: " + medicationId);
                            return;
                        }
                        
                        // Un solo log para dispensación
                        Log.d(TAG, "DISPENSANDO: " + medication.getName());
                        
                        // Marcar como dispensado en la base de datos sin actualizar conteo
                        medicationRepository.markAsDispensed(patientId, medicationId, scheduleId, 
                            new MedicationRepository.DatabaseCallback() {
                                @Override
                                public void onSuccess() {
                                    // Actualizar UI 
                                    mainHandler.post(() -> loadMedications(patientId));
                                }
                                
                                @Override
                                public void onError(String errorMsg) {
                                    errorMessage.postValue("Error al marcar dispensación: " + errorMsg);
                                }
                            });
                    }
                    
                    @Override
                    public void onError(String errorMsg) {
                        errorMessage.postValue("Error al obtener medicamento: " + errorMsg);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error en dispensación: " + e.getMessage(), e);
                errorMessage.postValue("Error inesperado: " + e.getMessage());
            }
        });
    }

    // Añade este nuevo callback
    public interface DispenseCallback {
        void onSuccess();
        void onError(String message);
    }

    // Modifica el método dispenseNow para usar el callback
    public void dispenseNow(String medicationId, String scheduleId, MqttViewModel mqttViewModel, DispenseCallback callback) {
        // LOG 1: Inicio de dispensación en ViewModel
        Log.d(TAG, "🚀 ViewModel iniciando dispensación: " + medicationId);
        
        // Validación de entrada
        if (patientId == null || medicationId == null) {
            // LOG 2: Error de validación
            Log.e(TAG, "❌ Error de validación: patientId o medicationId nulo");
            if (callback != null) {
                callback.onError("ID de medicamento o paciente no válido");
            }
            return;
        }
        
        // Verificar medicamento antes de dispensar
        medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
            @Override
            public void onSuccess(Medication medication) {
                if (medication == null) {
                    // LOG 3: Medicamento no encontrado
                    Log.e(TAG, "❌ Medicamento no encontrado: " + medicationId);
                    if (callback != null) {
                        callback.onError("Medicamento no encontrado");
                    }
                    return;
                }
                
                // Aplicar la lógica de dispensación
                boolean dispensed = medication.dispenseDose();
                
                if (!dispensed) {
                    Log.e(TAG, "⚠️ No hay suficiente medicamento para dispensar");
                    if (callback != null) {
                        callback.onError("No hay suficiente medicamento para dispensar");
                    }
                    return;
                }
                
                // Actualizar el medicamento en la base de datos después de dispensar
                medicationRepository.updateMedication(medication, new MedicationRepository.DatabaseCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "✅ Medicamento actualizado tras dispensación");
                        
                        // Marcar el horario como dispensado
                        medicationRepository.markAsDispensed(patientId, medicationId, scheduleId, 
                            new MedicationRepository.DatabaseCallback() {
                                @Override
                                public void onSuccess() {
                                    Log.d(TAG, "✓ Horario marcado como dispensado");
                                    
                                    // Actualizar UI después de dispensar
                                    updateCompartmentAfterDispense(medication);
                                    
                                    // Enviar comando MQTT si se proporcionó el viewmodel
                                    if (mqttViewModel != null) {
                                        try {
                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                mqttViewModel.notifyMedicationDispensed(medicationId);
                                                Log.d(TAG, "📡 Notificación enviada vía MqttViewModel");
                                            });
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error al notificar dispensación: " + e.getMessage());
                                        }
                                    }
                                    
                                    // Llamar al callback de éxito
                                    if (callback != null) {
                                        callback.onSuccess();
                                    }
                                }
                                
                                @Override
                                public void onError(String message) {
                                    Log.e(TAG, "❌ Error al marcar dispensación: " + message);
                                    if (callback != null) {
                                        callback.onError("Error al marcar dispensación: " + message);
                                    }
                                }
                            });
                    }
                    
                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "❌ Error al actualizar medicamento tras dispensar: " + message);
                        if (callback != null) {
                            callback.onError("Error al actualizar inventario: " + message);
                        }
                    }
                });
            }
            
            @Override
            public void onError(String message) {
                // LOG 13: Error al cargar
                Log.e(TAG, "❌ Error al cargar medicamento: " + message);
                if (callback != null) {
                    callback.onError("Error al cargar medicamento: " + message);
                }
            }
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

    // Obtener el repositorio
    public MedicationRepository getMedicationRepository() {
        return medicationRepository;
    }

    /**
     * Obtiene el ID del paciente seleccionado de manera segura
     * @return ID del paciente válido o null si no hay ninguno seleccionado
     */
    public String getSelectedPatientId() {
        String id = userRepository.getSelectedPatientId();
        if (!isValidPatientId(id)) {
            Log.e(TAG, "⛔ ID de paciente inválido obtenido del repositorio: " + id);
            return null;
        }
        return id;
    }

    /**
     * Método obsoleto, usar getSelectedPatientId() en su lugar
     * @deprecated Este método podría devolver un ID no válido como "current_user_id"
     */
    @Deprecated
    public String getPatientId() {
        Log.w(TAG, "⚠️ Llamada a método getPatientId() obsoleto, usar getSelectedPatientId() en su lugar");
        if ("current_user_id".equals(patientId)) {
            Log.e(TAG, "⛔ Intento de acceder a ID de paciente problemático: current_user_id");
            return null;
        }
        return patientId;
    }
    
    /**
     * Actualiza la cantidad total de pastillas para un medicamento (recarga)
     */
    public void refillMedication(String medicationId, int newTotalPills) {
        if (patientId == null || medicationId == null) {
            ErrorHandler.publishError(errorMessage, TAG, ErrorHandler.ERROR_VALIDATION, 
                                 "ID no válido para recarga", null);
            return;
        }
        
        if (newTotalPills < 0) {
            ErrorHandler.publishError(errorMessage, TAG, ErrorHandler.ERROR_VALIDATION, 
                                 "La cantidad a recargar debe ser mayor o igual a cero", null);
            return;
        }
        
        executor.execute(() -> {
            medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
                @Override
                public void onSuccess(Medication medication) {
                    if (medication != null) {
                        // Registrar valor original para el log
                        int originalPills = medication.getTotalPills();
                        
                        // Actualizar el total de pastillas sin cálculos adicionales
                        medication.setTotalPills(newTotalPills);
                        
                        // Guardar los cambios
                        medicationRepository.updateMedication(medication, new MedicationRepository.DatabaseCallback() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, String.format("Compartimento rellenado para %s: %d → %d pastillas", 
                                      medication.getName(), originalPills, newTotalPills));
                                
                                // Actualizar UI en el hilo principal
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    // Actualizar datos visuales de los compartimentos
                                    loadMedications(patientId);
                                });
                            }
                            
                            @Override
                            public void onError(String errorMsg) {
                                ErrorHandler.publishError(errorMessage, TAG, ErrorHandler.ERROR_DATABASE, 
                                                     "Error al guardar recarga: " + errorMsg, null);
                            }
                        });
                    } else {
                        ErrorHandler.publishError(errorMessage, TAG, ErrorHandler.ERROR_DATABASE, 
                                             "Medicamento no encontrado para recarga", null);
                    }
                }
                
                @Override
                public void onError(String errorMessage) {
                    ErrorHandler.publishError(DispenserViewModel.this.errorMessage, TAG, ErrorHandler.ERROR_DATABASE,
                                         "Error al obtener medicamento para rellenar: " + errorMessage, null);
                }
            });
        });
    }

    /**
     * Actualiza el medicamento cuando se recibe una dispensación remota desde MQTT
     */
    public void updateMedicationFromRemote(String medicationId, int newTotalPills, int newDosesTaken) {
        if (patientId == null || medicationId == null) {
            Log.e(TAG, "No se puede actualizar medicamento: ID faltante");
            return;
        }
        
        Log.d(TAG, "⬇️ Actualizando medicamento desde dispensador remoto: " + medicationId);
        
        executor.execute(() -> {
            medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
                @Override
                public void onSuccess(Medication medication) {
                    if (medication == null) {
                        Log.e(TAG, "Medicamento no encontrado: " + medicationId);
                        return;
                    }
                    
                    // Registrar valores originales
                    int originalPills = medication.getTotalPills();
                    
                    // Actualizar con los nuevos valores sin cálculos adicionales
                    medication.setTotalPills(newTotalPills);
                    medication.setDosesTaken(newDosesTaken);
                    
                    // Guardar cambios
                    medicationRepository.updateMedication(medication, new MedicationRepository.DatabaseCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "✅ Medicamento actualizado desde dispensador: " + medication.getName() + 
                                  " - Pills: " + newTotalPills + 
                                  " - Cambio: " + (originalPills - newTotalPills) + " pastillas");
                            
                            // Actualizar UI en el hilo principal
                            new Handler(Looper.getMainLooper()).post(() -> {
                                loadMedications(patientId);
                            });
                        }
                        
                        @Override
                        public void onError(String errorMsg) {
                            Log.e(TAG, "Error al actualizar medicamento desde dispensador: " + errorMsg);
                        }
                    });
                }
                
                @Override
                public void onError(String errorMsg) {
                    Log.e(TAG, "Error al obtener medicamento para actualización remota: " + errorMsg);
                }
            });
        });
    }

    /**
     * Método helper para actualizar los datos de un compartimento
     */
    private void updateCompartmentForMedication(String compartment, Medication medication, 
                                      int[] takenRef, int[] totalRef) {
        if (medication == null || compartment == null) return;
        
        // Verificar compatibilidad tipo-compartimento
        boolean isPill = medication.getType().equals(MedicationType.PILL);
        boolean isLiquid = medication.getType().equals(MedicationType.LIQUID);
        
        if ((isLiquid && !compartment.equals("LIQUID")) || 
            (isPill && compartment.equals("LIQUID"))) {
            return;
        }
        
        // Incrementar contador de dosis tomadas de forma simple
        takenRef[0] += medication.getDosesTaken();
        
        // Usar una lógica simple para las dosis totales
        if (isPill) {
            totalRef[0] = Math.max(totalRef[0], medication.getTotalPills());
        } else if (isLiquid) {
            totalRef[0] = Math.max(totalRef[0], medication.getTotalVolume());
        }
    }

    /**
     * Sincroniza el estado entre MqttViewModel y DispenserViewModel
     * usando administración centralizada de observadores
     */
    public void connectWithMqttViewModel(MqttViewModel mqttViewModel) {
        // Crear un ID único para este grupo de observers
        String connectionObserverId = "dispenser_mqtt_connection_" + System.currentTimeMillis();
        
        // Observar el estado de conexión
        observerManager.observeForever(mqttViewModel.getIsConnected(), connected -> {
            updateDispenserConnectionStatus(connected, 
                    connected ? "Conectado" : "Desconectado");
        }, connectionObserverId);
        
        // Observar el mensaje de estado
        observerManager.observeForever(mqttViewModel.getStatusMessage(), status -> {
            updateDispenserConnectionStatus(
                    mqttViewModel.getIsConnected().getValue() != null && 
                    mqttViewModel.getIsConnected().getValue(),
                    status
            );
        }, connectionObserverId);
        
        // Observar mensajes de error
        observerManager.observeForever(mqttViewModel.getErrorMessage(), error -> {
            if (error != null && !error.isEmpty()) {
                errorMessage.setValue(error);
            }
        }, connectionObserverId);
    }
    
    /**
     * Sincroniza los horarios con el dispensador consultando directamente el repositorio
     */
    public void syncSchedulesWithDispenser(MqttViewModel mqttViewModel) {
        // Usar getSelectedPatientId() en lugar de patientId directamente
        String safePatientId = getSelectedPatientId();
        if (safePatientId == null) {
            errorMessage.postValue("ID del paciente no válido");
            return;
        }
        
        // Usar safePatientId en lugar de patientId
        medicationRepository.getMedications(safePatientId, new MedicationRepository.DataCallback<List<Medication>>() {
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

    // Sobrescribir onCleared para limpiar todos los observadores
    @Override
    protected void onCleared() {
        super.onCleared();
        observerManager.removeAllObservers();
    }

    /**
     * Fuerza recarga y actualización completa tras dispensación
     */
    public void forceUpdateAfterDispense(String medicationId) {
        String safePatientId = getSelectedPatientId();
        if (safePatientId == null) return;
        
        Log.d(TAG, "⚡ Forzando actualización tras dispensación: " + medicationId);
        
        // Recargar todo de la DB con ID seguro
        medicationRepository.getMedications(safePatientId, new MedicationRepository.DataCallback<List<Medication>>() {
            @Override
            public void onSuccess(List<Medication> medications) {
                updateMedicationsList(medications);
                updateCompartmentStatus(medications);
                
                // Buscar el medicamento específico para actualizar su compartimento
                for (Medication med : medications) {
                    if (med.getId() != null && med.getId().equals(medicationId)) {
                        updateCompartmentAfterDispense(med);
                        Log.d(TAG, "✅ Compartimento actualizado para: " + med.getName() + 
                              " [" + med.getCompartment() + "]");
                        break;
                    }
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error en actualización forzada: " + errorMessage);
            }
        });
    }

    // Añadir esta propiedad a la clase DispenserViewModel (al inicio con otras propiedades)
    private MutableLiveData<String> medicationDispensedEvent = new MutableLiveData<>();

    // Añadir este método getter
    public LiveData<String> getMedicationDispensedEvent() {
        return medicationDispensedEvent;
    }

    /**
     * Valida un ID de paciente
     * @param id ID a validar
     * @return true si el ID es válido, false si es nulo, vacío o "current_user_id"
     */
    private boolean isValidPatientId(String id) {
        return id != null && !id.isEmpty() && !"current_user_id".equals(id);
    }
}