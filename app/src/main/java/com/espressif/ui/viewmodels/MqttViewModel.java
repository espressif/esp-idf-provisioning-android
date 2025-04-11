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
    
    public MqttViewModel(@NonNull Application application) {
        super(application);
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
            Log.e(TAG, "Error al conectar a MQTT", e);
            errorMessage.postValue("Error al conectar: " + e.getMessage());
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
     * Procesa los mensajes MQTT entrantes
     */
    private void processIncomingMessage(String topic, MqttMessage mqttMessage) {
        String payload = new String(mqttMessage.getPayload());
        Log.d(TAG, "Mensaje recibido en tópico " + topic + ": " + payload);
        
        try {
            // Intentar procesar como JSON
            JSONObject json = new JSONObject(payload);
            
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
                isSyncingSchedules.postValue(false);
            } else {
                String errorMsg = json.optString("error", "Error desconocido");
                errorMessage.postValue("Error de sincronización: " + errorMsg);
                isSyncingSchedules.postValue(false);
            }
        }
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
     * Sincroniza todos los horarios con el dispensador
     */
    public void syncSchedules(List<Medication> medications) {
        if (medications == null || medications.isEmpty()) {
            return;
        }
        
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
                
                // En Schedule.java se usa getCompartment(), no getCompartmentNumber()
                medicationObj.put("compartment", medication.getCompartmentNumber());
                
                medicationObj.put("type", medication.getType().toString());
                
                JSONArray schedulesArray = new JSONArray();
                for (Schedule schedule : medication.getScheduleList()) {
                    JSONObject scheduleObj = new JSONObject();
                    scheduleObj.put("id", schedule.getId());
                    
                    // Calcular los minutos a partir de hora y minuto
                    int timeInMinutes = schedule.getHour() * 60 + schedule.getMinute();
                    scheduleObj.put("time", timeInMinutes);
                    
                    // Convertir ArrayList<Boolean> a un array de índices de días activos
                    JSONArray daysArray = new JSONArray();
                    ArrayList<Boolean> daysOfWeek = schedule.getDaysOfWeek();
                    if (daysOfWeek != null) {
                        for (int i = 0; i < daysOfWeek.size(); i++) {
                            if (daysOfWeek.get(i)) {
                                daysArray.put(i + 1); // Se agrega +1 para que los días sean 1-7 en lugar de 0-6
                            }
                        }
                    }
                    scheduleObj.put("days", daysArray);
                    schedulesArray.put(scheduleObj);
                }
                
                medicationObj.put("schedules", schedulesArray);
                medicationsArray.put(medicationObj);
            }
            
            // Añadir el array de medicamentos al mensaje
            syncMessage.addPayload("medications", medicationsArray);
            
            // Publicar el mensaje
            mqttHandler.publishMessage(AppConstants.MQTT_TOPIC_DEVICE_COMMANDS, syncMessage.toString());
            
            // Simular una respuesta después de un tiempo para demostración
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                isSyncingSchedules.postValue(false);
            }, 3000);
            
        } catch (JSONException | MqttException e) {
            Log.e(TAG, "Error al sincronizar horarios", e);
            errorMessage.postValue("Error al sincronizar: " + e.getMessage());
            isSyncingSchedules.postValue(false);
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
    }
}