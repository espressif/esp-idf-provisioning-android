package com.espressif.ui.viewmodels;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
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

import com.espressif.ui.fragments.DispenserFragment;
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

    private String deviceName;  // Añadir esta variable
    
    public MqttViewModel(@NonNull Application application) {
        super(application);
        this.notificationHelper = new NotificationHelper(application);
        this.notificationScheduler = new NotificationScheduler(application);
        
        // Inicialización robusta del UserRepository
        try {
            this.userRepository = UserRepository.getInstance(application);
            
            // Validar si podemos obtener un ID de paciente
            String testId = getValidPatientId();
            if (testId != null) {
                Log.d(TAG, "✅ Iniciado con ID de paciente válido: " + testId);
            } else {
                Log.w(TAG, "⚠️ Iniciado sin ID de paciente válido, se intentará obtener después");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar UserRepository: " + e.getMessage(), e);
        }
        
        // Obtener el nombre del dispositivo de SharedPreferences
        SharedPreferences prefs = application.getSharedPreferences(
            AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        deviceName = prefs.getString(AppConstants.KEY_DEVICE_NAME, null);
        
        if (deviceName == null) {
            Log.e(TAG, "No se encontró nombre de dispositivo");
            return;
        }
        
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
                
                // Enviar el nombre del paciente tras una conexión exitosa
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isConnected.getValue() == Boolean.TRUE) {
                        sendPatientNameToDevice();
                    }
                }, 1000); // Pequeño retraso para asegurar que la conexión es estable
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
        if (deviceName == null) {
            Log.e(TAG, "No hay nombre de dispositivo configurado para suscripción");
            return;
        }

        try {
            // Suscribirse a los tópicos específicos del dispositivo
            mqttHandler.subscribe(
                AppConstants.buildTopic(AppConstants.MQTT_TOPIC_DEVICE_CONFIRMATION, deviceName), 1);
            mqttHandler.subscribe(
                AppConstants.buildTopic(AppConstants.MQTT_TOPIC_DEVICE_STATUS, deviceName), 1);
            mqttHandler.subscribe(
                AppConstants.buildTopic(AppConstants.MQTT_TOPIC_DEVICE_TELEMETRY, deviceName), 1);
            mqttHandler.subscribe(
                AppConstants.buildTopic(AppConstants.MQTT_TOPIC_DEVICE_RESPONSE, deviceName), 1);
            mqttHandler.subscribe(
                AppConstants.buildTopic(AppConstants.MQTT_TOPIC_DEVICE_TAKEN, deviceName), 1);

            Log.d(TAG, "Suscrito a tópicos para dispositivo: " + deviceName);
        } catch (MqttException e) {
            Log.e(TAG, "Error al suscribirse a tópicos: " + e.getMessage());
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
        
        // Solo registrar mensajes relevantes
        if (topic.equals(AppConstants.MQTT_TOPIC_DEVICE_RESPONSE) || 
            topic.equals(AppConstants.MQTT_TOPIC_DEVICE_STATUS) ||
            topic.equals(AppConstants.MQTT_TOPIC_DEVICE_CONFIRMATION) ||
            topic.equals(AppConstants.MQTT_TOPIC_DEVICE_TAKEN)) {  // Añadir este tópico
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
            
            // AÑADIR ESTA CONDICIÓN para mensajes de medicamentos tomados
            if (topic.equals(AppConstants.MQTT_TOPIC_DEVICE_TAKEN)) {
                processMedicationTaken(json);
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
            
            // Si es una confirmación de medicamento dispensado, emitir evento
            if (json.has("medicationId") && json.has("scheduleId") && json.has("dispensed") && json.getBoolean("dispensed")) {
                String medicationId = json.getString("medicationId");
                String scheduleId = json.getString("scheduleId");
                
                // Emitir evento de dispensación sin cálculos de pastillas
                notifyMedicationDispensed(medicationId);
                
                // Cancelar notificaciones relacionadas con este medicamento
                notificationHelper.cancelUpcomingReminder(medicationId, scheduleId);
                notificationHelper.cancelMissedMedicationAlert(medicationId, scheduleId);
                
                Log.d(TAG, "Medicamento dispensado: " + medicationId);
            }
            
            // Si es una confirmación de medicamento tomado manualmente
            if (json.has("medicationId") && json.has("scheduleId") && json.has("taken") && json.getBoolean("taken")) {
                String medicationId = json.getString("medicationId");
                String scheduleId = json.getString("scheduleId");
                
                // Emitir evento de dispensación sin cálculos de pastillas
                notifyMedicationDispensed(medicationId);
                
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
        
        // Añadir esta nueva sección para confirmación de medicamento tomado
        if (json.has("medicationId") && json.has("scheduleId") && json.has("taken") && json.getBoolean("taken")) {
            String medicationId = json.getString("medicationId");
            String scheduleId = json.getString("scheduleId");
            
            Log.d(TAG, "🔔 Confirmación de medicamento tomado recibida: " + medicationId);
            
            // Actualizar el estado del medicamento en la base de datos
            String patientId = getValidPatientId();
            if (patientId == null) {
                Log.e(TAG, "❌ No se puede procesar confirmación: ID de paciente inválido");
                return;
            }
            
            // Obtener el repositorio de medicamentos
            MedicationRepository medicationRepository = MedicationRepository.getInstance();
            
            // Buscar el medicamento y actualizar el estado de 'takingConfirmed'
            medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
                @Override
                public void onSuccess(Medication medication) {
                    if (medication != null) {
                        // Buscar el horario específico
                        for (Schedule schedule : medication.getScheduleList()) {
                            if (schedule.getId().equals(scheduleId)) {
                                // Actualizar el estado
                                medicationRepository.updateScheduleStatus(patientId, medicationId, scheduleId, "taken", 
                                    new MedicationRepository.DatabaseCallback() {
                                        @Override
                                        public void onSuccess() {
                                            Log.d(TAG, "✅ Estado de toma actualizado para horario: " + scheduleId);
                                            
                                            // Notificar para actualización de UI
                                            notifyMedicationDispensed(medicationId);
                                            
                                            // Cancelar notificaciones relacionadas
                                            notificationHelper.cancelUpcomingReminder(medicationId, scheduleId);
                                            notificationHelper.cancelMissedMedicationAlert(medicationId, scheduleId);
                                        }
                                        
                                        @Override
                                        public void onError(String message) {
                                            Log.e(TAG, "Error al actualizar estado de toma: " + message);
                                        }
                                    });
                                break;
                            }
                        }
                    }
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error al obtener medicamento para confirmar toma: " + message);
                }
            });
        }
    }
    
    /**
     * Procesa mensajes de confirmación de medicamento tomado
     */
    private void processMedicationTaken(JSONObject json) throws JSONException {
        // Verificar si el mensaje tiene la información necesaria
        if (json.has("medicationId") && json.has("scheduleId")) {
            String medicationId = json.getString("medicationId");
            String scheduleId = json.getString("scheduleId");
            
            Log.d(TAG, "🔔 Confirmación de medicamento tomado recibida: " + medicationId);
            
            // Actualizar el estado del medicamento en la base de datos
            String patientId = getValidPatientId();
            if (patientId == null) {
                Log.e(TAG, "❌ No se puede procesar medicamento tomado: ID de paciente inválido");
                return;
            }
            
            // Obtener el repositorio de medicamentos
            MedicationRepository medicationRepository = MedicationRepository.getInstance();
            
            // Buscar el medicamento y actualizar el estado de 'takingConfirmed'
            medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
                @Override
                public void onSuccess(Medication medication) {
                    if (medication != null) {
                        // Buscar el horario específico
                        for (Schedule schedule : medication.getScheduleList()) {
                            if (schedule.getId().equals(scheduleId)) {
                                // Actualizar el estado
                                medicationRepository.updateScheduleStatus(patientId, medicationId, scheduleId, "taken", 
                                    new MedicationRepository.DatabaseCallback() {
                                        @Override
                                        public void onSuccess() {
                                            Log.d(TAG, "✅ Estado de toma actualizado para horario: " + scheduleId);
                                            
                                            // Notificar para actualización de UI
                                            notifyMedicationDispensed(medicationId);
                                            
                                            // Cancelar notificaciones relacionadas
                                            notificationHelper.cancelUpcomingReminder(medicationId, scheduleId);
                                            notificationHelper.cancelMissedMedicationAlert(medicationId, scheduleId);
                                        }
                                        
                                        @Override
                                        public void onError(String message) {
                                            Log.e(TAG, "Error al actualizar estado de toma: " + message);
                                        }
                                    });
                                break;
                            }
                        }
                    }
                }
                
                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error al obtener medicamento para confirmar toma: " + message);
                }
            });
        } else {
            Log.e(TAG, "❌ Mensaje de confirmación de toma incompleto: falta medicationId o scheduleId");
        }
    }
    
    /**
     * Actualiza el conteo de medicación tras dispensación remota
     */
    private void updateMedicationCount(String medicationId) {
        String patientId = getValidPatientId();
        if (patientId == null) {
            ErrorHandler.handleError(TAG, ErrorHandler.ERROR_VALIDATION, 
                              "No se pudo obtener patientId válido para actualizar medicación", null);
            return;
        }
        
        // IMPORTANTE: Emitir evento directamente sin actualizar conteos
        Log.d(TAG, "🚀 Emitiendo evento de dispensación para: " + medicationId);
        medicationDispensedEvent.postValue(medicationId);
        
        // Notificar directamente sin actualizar conteo
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                DispenserFragment fragment = DispenserFragment.getInstance();
                if (fragment != null) {
                    Log.d(TAG, "💥 FORZANDO actualización directa vía fragment");
                    fragment.actualizarMedicamento(medicationId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error al llamar actualizarMedicamento: " + e.getMessage());
            }
        }, 250);
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
        
        // Intentar obtener ID de paciente primero de los medicamentos si está disponible
        String patientId = obtainPatientIdFromMedications(medications);
        
        if (patientId == null) {
            // Si no se pudo obtener de los medicamentos, intentar obtenerlo del repositorio
            patientId = getValidPatientId();
            
            if (patientId == null) {
                Log.e(TAG, "⛔ ID de paciente inválido obtenido del repositorio: null");
                errorMessage.postValue("No se puede sincronizar: ID de paciente inválido");
                return;
            }
        }
        
        // Ahora tenemos un ID de paciente válido, podemos continuar
        Log.d(TAG, "✅ ID de paciente válido obtenido para sincronización: " + patientId);
        
        isSyncingSchedules.postValue(true);
        
        try {
            // Crear estructura de mensaje según el formato requerido
            JSONObject message = new JSONObject();
            message.put("type", "command");
            message.put("timestamp", System.currentTimeMillis());
            
            // Crear payload
            JSONObject payload = new JSONObject();
            payload.put("cmd", "syncSchedules");
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("autoDispense", true);
            payload.put("patientId", patientId);
            
            // Agregar los medicamentos al payload
            JSONArray medsArray = new JSONArray();
            for (Medication medication : medications) {
                // Solo incluir medicamentos que tengan al menos un horario activo
                boolean tieneHorariosActivos = false;
                List<Schedule> schedules = medication.getScheduleList();
                for (Schedule schedule : schedules) {
                    if (schedule.isActive()) {
                        tieneHorariosActivos = true;
                        break;
                    }
                }
                
                if (tieneHorariosActivos) {
                    // Crear objeto JSON para este medicamento
                    JSONObject medObj = new JSONObject();
                    medObj.put("id", medication.getId());
                    medObj.put("name", medication.getName());
                    
                    // Añadir información del tipo de medicamento
                    medObj.put("type", medication.getType().toString().toLowerCase());
                    medObj.put("compartment", medication.getCompartmentNumber());
                    
                    // Añadir dosis por defecto (ya que no existe el método getDosageValue())
                    medObj.put("pillsPerDose", 1);  // Valor por defecto
                    
                    // Añadir pillsTotal por defecto
                    medObj.put("totalPills", 30);  // Valor por defecto si no está disponible
                    
                    // Crear array de horarios
                    JSONArray schedulesArray = new JSONArray();
                    for (Schedule schedule : schedules) {
                        if (schedule.isActive()) {
                            JSONObject scheduleObj = new JSONObject();
                            scheduleObj.put("id", schedule.getId());
                            
                            // Convertir hora y minuto a minutos desde medianoche
                            int timeInMinutes = schedule.getHour() * 60 + schedule.getMinute();
                            scheduleObj.put("time", timeInMinutes);
                            
                            // Modo de intervalo (por defecto false)
                            scheduleObj.put("intervalMode", schedule.isIntervalMode());
                            
                            // Para los días, crear un array con los días configurados
                            JSONArray daysArray = new JSONArray();
                            ArrayList<Boolean> daysOfWeek = schedule.getDaysOfWeek();
                            if (daysOfWeek != null && daysOfWeek.size() >= 7) {
                                for (int i = 0; i < 7; i++) {
                                    if (daysOfWeek.get(i)) {
                                        daysArray.put(i + 1); // Convertir a formato 1-7 (Lun-Dom)
                                    }
                                }
                            } else {
                                // Si no hay días configurados, incluir todos
                                for (int i = 1; i <= 7; i++) {
                                    daysArray.put(i);
                                }
                            }
                            scheduleObj.put("days", daysArray);
                            
                            schedulesArray.put(scheduleObj);
                        }
                    }
                    
                    medObj.put("schedules", schedulesArray);
                    
                    // Añadir a la lista de medicamentos
                    medsArray.put(medObj);
                }
            }
            
            // Añadir el array de medicamentos al payload
            payload.put("medications", medsArray);
            
            // Añadir payload al mensaje
            message.put("payload", payload);
            
            // Publicar mensaje
            mqttHandler.publishMessage(AppConstants.MQTT_TOPIC_DEVICE_COMMANDS, message.toString());
            
            // Confirmar envío exitoso
            Log.d(TAG, "📱→🤖 SYNC: Mensaje de sincronización enviado exitosamente");
            
            // Programar notificaciones para cada medicamento
            scheduleNotificationsForMedications(patientId, medications);
            
        } catch (JSONException e) {
            Log.e(TAG, "❌ SYNC ERROR: Error al crear mensaje JSON: " + e.getMessage(), e);
            errorMessage.postValue("Error al sincronizar: " + e.getMessage());
            isSyncingSchedules.postValue(false);
        } catch (MqttException e) {
            Log.e(TAG, "❌ SYNC ERROR: Error al publicar mensaje MQTT: " + e.getMessage(), e);
            errorMessage.postValue("Error al sincronizar: " + e.getMessage());
            isSyncingSchedules.postValue(false);
        } catch (Exception e) {
            Log.e(TAG, "❌ SYNC ERROR: Error inesperado: " + e.getMessage(), e);
            errorMessage.postValue("Error inesperado: " + e.getMessage());
            isSyncingSchedules.postValue(false);
        }
    }

    /**
     * Obtiene ID de paciente desde la lista de medicamentos
     */
    private String obtainPatientIdFromMedications(List<Medication> medications) {
        if (medications == null || medications.isEmpty()) {
            return null;
        }
        
        // Intentar obtener un ID válido de cualquiera de los medicamentos
        for (Medication medication : medications) {
            String patientId = medication.getPatientId();
            if (isValidPatientId(patientId)) {
                return patientId;
            }
        }
        
        return null;
    }

    /**
     * Programa notificaciones para una lista de medicamentos
     */
    private void scheduleNotificationsForMedications(String patientId, List<Medication> medications) {
        for (Medication medication : medications) {
            for (Schedule schedule : medication.getScheduleList()) {
                if (schedule.isActive()) {
                    boolean scheduled = notificationScheduler.scheduleReminder(patientId, medication, schedule);
                    if (scheduled) {
                        Log.d(TAG, "✅ Notificación programada para: " + medication.getName());
                    }
                } else {
                    // Cancelar recordatorios para horarios desactivados
                    notificationScheduler.cancelReminders(medication.getId(), schedule.getId());
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

    // Reemplazar el método sendDispenseCommand con esta versión que no usa publishMessage:
    public void sendDispenseCommand(String medicationId, String scheduleId) {
        if (medicationId == null || scheduleId == null) {
            Log.e(TAG, "No se puede enviar comando de dispensación: ID nulo");
            return;
        }
        
        Log.d(TAG, "📡 Enviando comando para dispensar medicación: " + medicationId);
        
        // Construir el topic y payload
        String patientId = getValidPatientId();
        if (patientId == null) {
            Log.e(TAG, "❌ No se puede enviar comando: patientId inválido");
            return;
        }
        
        try {
            // Topic para el comando de dispensar
            String topic = "devices/dispenser/" + patientId + "/command";
            
            // Crear payload JSON con información de dispensación
            JSONObject payload = new JSONObject();
            payload.put("action", "dispense");
            payload.put("medicationId", medicationId);
            payload.put("scheduleId", scheduleId);
            payload.put("timestamp", System.currentTimeMillis());
            
            // Usar el método existente para publicar un mensaje
            // (Asumiendo que hay un método disponible en MqttViewModel o MqttHandler)
            if (mqttHandler != null) {
                mqttHandler.publishMessage(topic, payload.toString());
                Log.d(TAG, "✅ Comando de dispensación enviado correctamente para: " + medicationId);
            } else {
                Log.e(TAG, "❌ No se pudo enviar comando MQTT: mqttHandler es null");
            }
            
            // Notificar sobre dispensación para actualizar UI
            notifyMedicationDispensed(medicationId);
        } catch (Exception e) {
            Log.e(TAG, "Error al enviar comando de dispensación: " + e.getMessage());
        }
    }

    // Añadir este método público a MqttViewModel
    public void notifyMedicationDispensed(String medicationId) {
        if (medicationId == null || medicationId.isEmpty()) {
            return;
        }
        
        // Aquí usamos setValue porque estamos en el hilo principal
        medicationDispensedEvent.setValue(medicationId);
        Log.d(TAG, "🔔 Evento de dispensación emitido para: " + medicationId);
    }

    /**
     * Valida un ID de paciente
     * @param patientId ID a validar
     * @return true si el ID es válido, false si es nulo, vacío o "current_user_id"
     */
    private boolean isValidPatientId(String patientId) {
        return patientId != null && !patientId.isEmpty() && !"current_user_id".equals(patientId);
    }

    /**
     * Obtiene ID de paciente válido del repositorio utilizando múltiples estrategias
     * @return ID de paciente validado o null si no hay ID válido
     */
    private String getValidPatientId() {
        String patientId = null;
        
        // Estrategia 1: Obtener ID de paciente seleccionado
        patientId = userRepository.getSelectedPatientId();
        if (isValidPatientId(patientId)) {
            Log.d(TAG, "Usando ID de paciente seleccionado: " + patientId);
            return patientId;
        }
        
        // Estrategia 2: Obtener ID de paciente conectado
        patientId = userRepository.getConnectedPatientId();
        if (isValidPatientId(patientId)) {
            Log.d(TAG, "Usando ID de paciente conectado: " + patientId);
            return patientId;
        }
        
        // Estrategia 3: Acceso directo a preferencias
        try {
            patientId = userRepository.getPreferencesHelper().getPatientId();
            if (isValidPatientId(patientId)) {
                Log.d(TAG, "Usando ID de paciente desde preferencias: " + patientId);
                return patientId;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al acceder a preferencias: " + e.getMessage());
        }
        
        Log.e(TAG, "⛔ No se pudo obtener un ID de paciente válido mediante ninguna estrategia");
        return null;
    }

    /**
     * Envía el nombre del paciente al dispositivo
     * Esto actualizará el nombre que muestra el dispositivo
     */
    public void sendPatientNameToDevice() {
        try {
            // Obtener ID y nombre del paciente conectado
            String patientId = getValidPatientId();
            if (patientId == null) {
                Log.e(TAG, "⛔ No se puede enviar nombre: ID de paciente inválido");
                return;
            }
            
            // Obtener el nombre del paciente desde el UserRepository
            String patientName = null;
            
            if (AppConstants.USER_TYPE_PATIENT.equals(userRepository.getUserType())) {
                // Si es paciente, usar su propio nombre
                patientName = userRepository.getPreferencesHelper().getUserName();
            } else {
                // Si es familiar, usar el nombre del paciente conectado
                patientName = userRepository.getPreferencesHelper().getConnectedPatientName();
            }
            
            // Si aún no tenemos nombre, usar valor por defecto
            if (patientName == null || patientName.isEmpty()) {
                patientName = "Paciente " + patientId;
            }
            
            // Crear mensaje JSON con la nueva estructura
            JSONObject message = new JSONObject();
            message.put("type", "command");
            message.put("timestamp", System.currentTimeMillis());
            
            // Crear payload
            JSONObject payload = new JSONObject();
            payload.put("cmd", "setPatientName");
            payload.put("patientName", patientName);
            payload.put("patientId", patientId);
            
            // Añadir payload al mensaje principal
            message.put("payload", payload);
            
            // Publicar mensaje
            mqttHandler.publishMessage(AppConstants.MQTT_TOPIC_DEVICE_COMMANDS, message.toString());
            Log.d(TAG, "📱→🤖 Nombre de paciente enviado: " + patientName);
            
        } catch (JSONException | MqttException e) {
            Log.e(TAG, "❌ Error al enviar nombre de paciente: " + e.getMessage(), e);
            errorMessage.postValue("Error al enviar nombre: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "❌ Error inesperado al enviar nombre: " + e.getMessage(), e);
        }
    }

    public void publishMessage(String topicTemplate, String message) {
        if (deviceName == null) {
            Log.e(TAG, "No hay nombre de dispositivo configurado para publicar");
            return;
        }
        
        try {
            String topic = AppConstants.buildTopic(topicTemplate, deviceName);
            mqttHandler.publishMessage(topic, message);
            Log.d(TAG, "Mensaje publicado en: " + topic);
        } catch (MqttException e) {
            Log.e(TAG, "Error al publicar mensaje: " + e.getMessage());
            errorMessage.postValue("Error al publicar: " + e.getMessage());
        }
    }

    public void updateDeviceName(String newDeviceName) {
        if (newDeviceName == null || newDeviceName.isEmpty()) {
            Log.e(TAG, "❌ Nombre de dispositivo inválido");
            return;
        }

        Log.d(TAG, "Actualizando nombre de dispositivo: " + newDeviceName);
        this.deviceName = newDeviceName;
        
        // Guardar en SharedPreferences para persistencia
        SharedPreferences prefs = getApplication().getSharedPreferences(
            AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        prefs.edit().putString(AppConstants.KEY_DEVICE_NAME, newDeviceName).apply();
        
        // Reconectar con nuevo nombre
        if (mqttHandler != null && mqttHandler.isConnected()) {
            disconnect();
            connect(); // Esto llamará a subscribeToTopics() con el nuevo nombre
        } else {
            connect(); // Primera conexión
        }
    }
}