package com.espressif.ui.viewmodels;

import android.app.Application;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.espressif.AppConstants;
import com.espressif.data.repository.MedicationRepository;
import com.espressif.ui.activities.mqtt_activities.CustomMqttMessage;
import com.espressif.ui.activities.mqtt_activities.DeviceConnectionChecker;
import com.espressif.ui.activities.mqtt_activities.MqttHandler;
import com.espressif.ui.dialogs.ProgressDialogFragment;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// Añadir estas importaciones
import org.json.JSONArray;
import com.espressif.ui.models.Medication;
import com.espressif.ui.models.Schedule;

import java.util.concurrent.atomic.AtomicBoolean;

// Añadir en las importaciones:
import com.espressif.ui.notifications.NotificationHelper;
import com.espressif.ui.notifications.NotificationScheduler;
import com.espressif.data.repository.UserRepository;

// Añadir estas importaciones en la sección de imports
import com.espressif.data.repository.MedicationRepository;
import com.espressif.data.repository.MedicationRepository.DataCallback;
import com.espressif.data.repository.MedicationRepository.DatabaseCallback;
import com.espressif.ui.utils.ErrorHandler;
import com.espressif.ui.utils.ObserverManager;

public class MqttViewModel extends AndroidViewModel {
    private static final String TAG = "MqttViewModel";
    
    // Handler MQTT y comprobador de conexión
    private MqttHandler mqttHandler;
    private DeviceConnectionChecker deviceConnectionChecker;
    
    // LiveData para estados
    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("Desconectado");
    private final MutableLiveData<Boolean> isSyncingSchedules = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    // Control de reconexión
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private final int RECONNECT_DELAY_MS = 10000; // 10 segundos
    
    // Añadir como atributos de la clase:
    private NotificationHelper notificationHelper;
    private NotificationScheduler notificationScheduler;
    private UserRepository userRepository;
    
    // Añadir:
    private final MutableLiveData<String> medicationDispensedEvent = new MutableLiveData<>();

    public LiveData<String> getMedicationDispensedEvent() {
        return medicationDispensedEvent;
    }
    
    // Agregar estos atributos:
    private ObserverManager observerManager = new ObserverManager();

    public MqttViewModel(@NonNull Application application) {
        super(application);
        this.notificationHelper = new NotificationHelper(application);
        this.notificationScheduler = new NotificationScheduler(application);
        this.userRepository = UserRepository.getInstance(application);
        initializeMqtt();
    }
    
    /**
     * Inicializa la conexión MQTT
     */
    private void initializeMqtt() {
        // El constructor actual de MqttHandler solo acepta contexto y callback
        mqttHandler = new MqttHandler(getApplication(), new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                Log.e(TAG, "Conexión MQTT perdida", cause);
                isConnected.postValue(false);
                statusMessage.postValue("Desconectado");
                
                // Programar reconexión
                scheduleReconnect();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                processIncomingMessage(topic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // No es necesario manejar esto para esta implementación
            }
        });
        
        mqttHandler.initialize();
        deviceConnectionChecker = new DeviceConnectionChecker(getApplication());
    }
    
    /**
     * Conecta al broker MQTT
     */
    public void connect() {
        try {
            if (mqttHandler != null && !mqttHandler.isConnected()) {
                mqttHandler.connect();
                subscribeToTopics();
                checkConnection();
            }
        } catch (MqttException e) {
            String errorMsg = ErrorHandler.handleMqttError(TAG, "connect", e);
            errorMessage.postValue(errorMsg);
        }
    }
    
    /**
     * Desconecta del broker MQTT
     */
    public void disconnect() {
        if (mqttHandler != null) {
            mqttHandler.disconnect();
        }
        
        isConnected.postValue(false);
        statusMessage.postValue("Desconectado");
    }
    
    /**
     * Suscribe a los tópicos relevantes
     */
    private void subscribeToTopics() {
        try {
            // Usar tópicos definidos en AppConstants
            mqttHandler.subscribe(AppConstants.MQTT_TOPIC_DEVICE_STATUS, 1);
            mqttHandler.subscribe(AppConstants.MQTT_TOPIC_DEVICE_COMMANDS, 1);
            mqttHandler.subscribe(AppConstants.MQTT_TOPIC_DEVICE_TELEMETRY, 1);
            mqttHandler.subscribe(AppConstants.MQTT_TOPIC_DEVICE_RESPONSE, 1);
            
            // Añadir la suscripción al tópico de confirmación de medicamentos
            mqttHandler.subscribe(AppConstants.MQTT_TOPIC_DEVICE_CONFIRMATION, 1);
        } catch (MqttException e) {
            Log.e(TAG, "Error al suscribirse a tópicos", e);
            errorMessage.postValue("Error de suscripción: " + e.getMessage());
        }
    }
    
    /**
     * Verifica la conexión con el dispensador
     */
    public void checkConnection() {
        deviceConnectionChecker.checkConnection(new DeviceConnectionChecker.ConnectionCheckListener() {
            @Override
            public void onConnectionCheckResult(boolean isDeviceConnected) {
                isConnected.postValue(isDeviceConnected);
                statusMessage.postValue(isDeviceConnected ? "Conectado" : "Dispensador no disponible");
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error en verificación de conexión: " + message);
                errorMessage.postValue("Error: " + message);
                
                // Mantener como desconectado
                isConnected.postValue(false);
                statusMessage.postValue("Error de conexión");
            }
        });
    }
    
    /**
     * Programa una reconexión automática
     */
    private void scheduleReconnect() {
        if (isReconnecting.getAndSet(true)) {
            return; // Ya está programada una reconexión
        }
        
        reconnectHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Intentando reconexión...");
                    connect();
                    isReconnecting.set(false);
                } catch (Exception e) {
                    Log.e(TAG, "Error en reconexión", e);
                    // Programar otro intento
                    isReconnecting.set(false);
                    scheduleReconnect();
                }
            }
        }, RECONNECT_DELAY_MS);
    }
    
    /**
     * Procesa los mensajes MQTT entrantes - versión con logs reducidos
     */
    private void processIncomingMessage(String topic, MqttMessage mqttMessage) {
        String payload = new String(mqttMessage.getPayload());
        
        // Solo registrar mensajes relevantes para sincronización
        if (topic.equals(AppConstants.MQTT_TOPIC_DEVICE_RESPONSE) || 
            topic.equals(AppConstants.MQTT_TOPIC_DEVICE_STATUS) ||
            topic.equals(AppConstants.MQTT_TOPIC_DEVICE_CONFIRMATION)) {
            Log.d(TAG, "🤖→📱 RECIBIDO [" + topic + "]: " + payload);
        }
        
        try {
            // Intentar procesar como JSON
            JSONObject json = new JSONObject(payload);
            
            // Procesar mensaje de confirmación de medicamentos
            if (topic.equals(AppConstants.MQTT_TOPIC_DEVICE_CONFIRMATION)) {
                processMedConfirmation(json);
                return;
            }
            
            // Intentar interpretar como CustomMqttMessage si contiene un campo "type"
            if (json.has("type")) {
                CustomMqttMessage customMessage = CustomMqttMessage.fromJson(payload);
                String messageType = customMessage.getType();
                
                if ("command".equals(messageType)) {
                    // Procesar como comando
                    processCommandResponse(json);
                } else if ("status".equals(messageType)) {
                    // Procesar como estado
                    String status = customMessage.getStatus();
                    boolean isDeviceConnected = "online".equals(status);
                    
                    isConnected.postValue(isDeviceConnected);
                    statusMessage.postValue(isDeviceConnected ? "Conectado" : "Dispensador no disponible");
                }
            } 
            // Procesar mensajes específicos de tópicos
            else if (topic.equals(AppConstants.MQTT_TOPIC_DEVICE_STATUS)) {
                processStatusMessage(json);
            } else if (topic.equals(AppConstants.MQTT_TOPIC_DEVICE_RESPONSE)) {
                processResponseMessage(json);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error procesando mensaje JSON", e);
        }
    }
    
    /**
     * Procesa mensajes de estado del dispensador
     */
    private void processStatusMessage(JSONObject json) throws JSONException {
        if (json.has("status")) {
            String status = json.getString("status");
            boolean isDeviceConnected = "online".equals(status);
            
            isConnected.postValue(isDeviceConnected);
            statusMessage.postValue(isDeviceConnected ? "Conectado" : "Dispensador no disponible");
        }
    }
    
    /**
     * Procesa respuestas a comandos
     */
    private void processCommandResponse(JSONObject json) throws JSONException {
        if (json.has("payload")) {
            JSONObject payload = json.getJSONObject("payload");
            
            if (payload.has("cmd") && "syncSchedules".equals(payload.getString("cmd"))) {
                // Respuesta a syncSchedules
                isSyncingSchedules.postValue(false);
            }
        }
    }
    
    /**
     * Procesa mensajes de respuesta generales
     */
    private void processResponseMessage(JSONObject json) throws JSONException {
        if (json.has("command") && "syncSchedules".equals(json.getString("command"))) {
            boolean success = json.optBoolean("success", false);
            if (success) {
                Log.d(TAG, "✅ SYNC: Sincronización confirmada por el dispensador");
                isSyncingSchedules.postValue(false);
            } else {
                String errorMsg = json.optString("error", "Error desconocido");
                Log.e(TAG, "❌ SYNC ERROR: " + errorMsg);
                errorMessage.postValue("Error de sincronización: " + errorMsg);
                isSyncingSchedules.postValue(false);
            }
        }
    }
    
    /**
     * Procesa mensajes de confirmación de sincronización y dispensación de medicamentos
     */
    private void processMedConfirmation(JSONObject json) throws JSONException {
        boolean success = json.optBoolean("success", false);
        if (success) {
            Log.d(TAG, "✅ SYNC: Sincronización confirmada por el dispensador: " + json.optString("message", ""));
            isSyncingSchedules.postValue(false);
            
            // Si es una confirmación de medicamento dispensado, actualizar conteo
            if (json.has("medicationId") && json.has("scheduleId") && json.has("dispensed") && json.getBoolean("dispensed")) {
                String medicationId = json.getString("medicationId");
                String scheduleId = json.getString("scheduleId");
                
                // AQUÍ ESTÁ LA SOLUCIÓN: Actualizar el conteo de pastillas
                updateMedicationCount(medicationId);
                
                // Cancelar notificaciones relacionadas con este medicamento
                notificationHelper.cancelUpcomingReminder(medicationId, scheduleId);
                notificationHelper.cancelMissedMedicationAlert(medicationId, scheduleId);
                
                Log.d(TAG, "Medicamento dispensado y conteo actualizado: " + medicationId);
            }
            
            // Si es una confirmación de medicamento tomado manualmente
            if (json.has("medicationId") && json.has("scheduleId") && json.has("taken") && json.getBoolean("taken")) {
                String medicationId = json.getString("medicationId");
                String scheduleId = json.getString("scheduleId");
                
                // También actualizar conteo para medicamentos tomados manualmente
                updateMedicationCount(medicationId);
                
                // Cancelar notificaciones relacionadas con este medicamento
                notificationHelper.cancelUpcomingReminder(medicationId, scheduleId);
                notificationHelper.cancelMissedMedicationAlert(medicationId, scheduleId);
                
                Log.d(TAG, "Notificaciones canceladas para medicamento tomado: " + medicationId);
            }
        } else {
            String errorMsg = json.optString("message", "Error desconocido");
            Log.e(TAG, "❌ SYNC ERROR: " + errorMsg);
            errorMessage.postValue("Error de sincronización: " + errorMsg);
            isSyncingSchedules.postValue(false);
        }
    }
    
    /**
     * Actualiza el conteo de medicación tras dispensación remota
     */
    private void updateMedicationCount(String medicationId) {
        String patientId = userRepository.getConnectedPatientId();
        if (patientId == null || patientId.isEmpty()) {
            ErrorHandler.handleError(TAG, ErrorHandler.ERROR_VALIDATION, 
                               "No se pudo obtener patientId para actualizar medicación", null);
            return;
        }
        
        MedicationRepository medicationRepository = MedicationRepository.getInstance();
        
        medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
            @Override
            public void onSuccess(Medication medication) {
                if (medication == null) {
                    Log.e(TAG, "Medicamento no encontrado: " + medicationId);
                    return;
                }
                
                // Registrar solo valores iniciales
                int originalPills = medication.getTotalPills();
                
                // Intentar actualizar usando el método centralizado
                boolean dispensed = medication.dispenseDose();
                
                if (!dispensed && originalPills > 0) {
                    // Forzar dispensación si fue reportada por dispositivo
                    int pillsToDispense = Math.max(1, medication.getPillsPerDose());
                    medication.setTotalPills(Math.max(0, originalPills - pillsToDispense));
                    medication.setDosesTaken(medication.getDosesTaken() + 1);
                    medication.updateRemainingDoses();
                    dispensed = true;
                    
                    Log.d(TAG, "Dispensación forzada por reporte del dispositivo: " + medication.getName());
                }
                
                if (dispensed) {
                    // Un solo log con formato consistente
                    Log.d(TAG, String.format("Actualizado %s: %d → %d pastillas, dosis: %d", 
                          medication.getName(), originalPills, medication.getTotalPills(), 
                          medication.getDosesTaken()));
                    
                    medicationRepository.updateMedication(medication, new MedicationRepository.DatabaseCallback() {
                        @Override
                        public void onSuccess() {
                            notifyMedicationUpdated(medication.getId());
                            
                            // Forzar actualización de UI usando LiveData
                            new Handler(Looper.getMainLooper()).post(() -> {
                                try {
                                    // Notificar con el ID del medicamento
                                    medicationDispensedEvent.setValue(medication.getId());
                                    Log.d(TAG, "Evento de medicación dispensada emitido: " + medication.getName());
                                } catch (Exception e) {
                                    Log.e(TAG, "Error al notificar dispensación: " + e.getMessage());
                                }
                            });
                        }
                        
                        @Override
                        public void onError(String message) {
                            Log.e(TAG, "Error al actualizar DB: " + message);
                        }
                    });
                }
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Error al obtener medicamento: " + message);
            }
        });
        
        // Esta llamada se conserva para asegurar una rápida notificación inicial
        medicationDispensedEvent.postValue(medicationId);
    }
    
    /**
     * Notifica a otros ViewModels que una medicación ha sido actualizada
     */
    private void notifyMedicationUpdated(String medicationId) {
        // Usar LiveData para comunicación entre ViewModels
        MutableLiveData<String> medicationUpdated = new MutableLiveData<>();
        medicationUpdated.setValue(medicationId);
        
        // O usar un evento global con EventBus
        // EventBus.getDefault().post(new MedicationUpdatedEvent(medicationId));
        
        // Forzar actualización de la UI
        new Handler(Looper.getMainLooper()).post(() -> {
            // Este es un enfoque simple pero efectivo para forzar una actualización
            // Normalmente se realizaría mediante patrón observer con LiveData
            Log.d(TAG, "Solicitando actualización de UI para medicamento: " + medicationId);
            
            // Si tienes acceso al DispenserViewModel, puedes llamar a:
            // dispenserViewModel.loadMedications(patientId);
        });
    }
    
    /**
     * Envía comando para dispensar medicación
     */
    public void dispenseNow(String medicationId, String scheduleId) {
        try {
            // Usar CustomMqttMessage para crear el comando
            CustomMqttMessage message = CustomMqttMessage.createCommand("dispense");
            message.addPayload("medicationId", medicationId);
            message.addPayload("scheduleId", scheduleId);
            
            mqttHandler.publishMessage(AppConstants.MQTT_TOPIC_DEVICE_COMMANDS, message.toString());
            Log.d(TAG, "Comando de dispensación enviado: " + medicationId);
        } catch (JSONException | MqttException e) {
            Log.e(TAG, "Error al enviar comando de dispensación", e);
            errorMessage.postValue("Error al dispensar: " + e.getMessage());
        }
    }
    
    /**
     * Sincroniza todos los horarios con el dispensador y programa notificaciones
     */
    public void syncSchedules(List<Medication> medications) {
        if (medications == null || medications.isEmpty()) {
            Log.d(TAG, "syncSchedules: No hay medicamentos para sincronizar");
            return;
        }
        
        // Reducir a un solo log inicial con información relevante
        Log.d(TAG, "📱→🤖 SYNC: Iniciando sincronización de " + medications.size() + " medicamentos");
        
        isSyncingSchedules.postValue(true);
        
        try {
            // Crear el mensaje usando CustomMqttMessage
            CustomMqttMessage syncMessage = CustomMqttMessage.createCommand("syncSchedules");
            
            // Crear array de medicamentos para añadir al payload
            JSONArray medicationsArray = new JSONArray();
            
            for (Medication medication : medications) {
                JSONObject medicationObj = new JSONObject();
                medicationObj.put("id", medication.getId());
                medicationObj.put("name", medication.getName());
                medicationObj.put("compartment", medication.getCompartmentNumber());
                medicationObj.put("type", medication.getType());
                
                // AÑADIR INFORMACIÓN DE DOSIFICACIÓN
                medicationObj.put("pillsPerDose", medication.getPillsPerDose());
                medicationObj.put("totalPills", medication.getTotalPills());
                
                JSONArray schedulesArray = new JSONArray();
                int activeSchedulesCount = 0;
                
                for (Schedule schedule : medication.getScheduleList()) {
                    // SOLO INCLUIR HORARIOS ACTIVOS
                    if (schedule.isActive()) {
                        JSONObject scheduleObj = new JSONObject();
                        scheduleObj.put("id", schedule.getId());
                        
                        // Calcular los minutos a partir de hora y minuto
                        int timeInMinutes = schedule.getHour() * 60 + schedule.getMinute();
                        scheduleObj.put("time", timeInMinutes);
                        
                        // AÑADIR INFORMACIÓN DE MODO INTERVALO
                        scheduleObj.put("intervalMode", schedule.isIntervalMode());
                        if (schedule.isIntervalMode()) {
                            scheduleObj.put("intervalHours", schedule.getIntervalHours());
                            scheduleObj.put("treatmentDays", schedule.getTreatmentDays());
                        }
                        
                        // Convertir ArrayList<Boolean> a un array de índices de días activos
                        JSONArray daysArray = new JSONArray();
                        ArrayList<Boolean> daysOfWeek = schedule.getDaysOfWeek();
                        if (daysOfWeek != null) {
                            for (int i = 0; i < daysOfWeek.size(); i++) {
                                if (daysOfWeek.get(i)) {
                                    daysArray.put(i + 1);
                                }
                            }
                        }
                        scheduleObj.put("days", daysArray);
                        schedulesArray.put(scheduleObj);
                        activeSchedulesCount++;
                    }
                }
                
                medicationObj.put("schedules", schedulesArray);
                medicationsArray.put(medicationObj);
                
                // Log resumido por medicamento
                Log.d(TAG, "📱→🤖 SYNC: " + medication.getName() + " [Compartimento " + 
                      medication.getCompartmentNumber() + "] - " + activeSchedulesCount + " horarios activos");
            }
            
            // Añadir el array de medicamentos al mensaje
            syncMessage.addPayload("medications", medicationsArray);
            syncMessage.addPayload("timestamp", System.currentTimeMillis());
            syncMessage.addPayload("autoDispense", true);
            
            // Solo mostrar el tópico, no el mensaje completo que puede ser muy largo
            Log.d(TAG, "📱→🤖 SYNC: Enviando al tópico: " + AppConstants.MQTT_TOPIC_DEVICE_COMMANDS);
            
            // Publicar el mensaje
            mqttHandler.publishMessage(AppConstants.MQTT_TOPIC_DEVICE_COMMANDS, syncMessage.toString());
            
            // Confirmar envío exitoso
            Log.d(TAG, "📱→🤖 SYNC: Mensaje de sincronización enviado exitosamente");
            
        } catch (JSONException | MqttException e) {
            Log.e(TAG, "❌ SYNC ERROR: " + e.getMessage(), e);
            errorMessage.postValue("Error al sincronizar: " + e.getMessage());
            isSyncingSchedules.postValue(false);
        }
        
        // Programar notificaciones para cada medicamento
        String patientId = userRepository.getConnectedPatientId();
        if (patientId != null && !patientId.isEmpty()) {
            for (Medication medication : medications) {
                for (Schedule schedule : medication.getScheduleList()) {
                    if (schedule.isActive()) {
                        notificationScheduler.scheduleReminder(patientId, medication, schedule);
                    } else {
                        // Cancelar recordatorios para horarios desactivados
                        notificationScheduler.cancelReminders(medication.getId(), schedule.getId());
                    }
                }
            }
        }
    }
    
    // Getters para LiveData
    public LiveData<Boolean> getIsConnected() {
        return isConnected;
    }
    
    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }
    
    public LiveData<Boolean> getIsSyncingSchedules() {
        return isSyncingSchedules;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Obtiene el MqttHandler para uso externo si es necesario
     */
    public MqttHandler getMqttHandler() {
        return mqttHandler;
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        
        // Liberar recursos
        reconnectHandler.removeCallbacksAndMessages(null);
        
        if (deviceConnectionChecker != null) {
            deviceConnectionChecker.release();
        }
        
        if (mqttHandler != null) {
            mqttHandler.disconnect();
        }
        
        // Remover todos los observadores
        observerManager.removeAllObservers();
    }
}