package com.espressif.ui.activities.mqtt_activities;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import com.espressif.AppConstants;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class DeviceConnectionChecker {
    private static final String TAG = "DeviceConnectionChecker";
    
    private final MqttHandler mqttHandler;
    private final Handler handler;
    private final String clientId;
    private Runnable timeoutRunnable;
    private ConnectionCheckListener currentListener;
    private final AtomicBoolean isChecking = new AtomicBoolean(false);
    
    // Interfaz para notificar el resultado de la verificación
    public interface ConnectionCheckListener {
        void onConnectionCheckResult(boolean isConnected);
        void onError(String errorMessage);
    }
    
    public DeviceConnectionChecker(Context context) {
        this.handler = new Handler(Looper.getMainLooper());
        this.clientId = UUID.randomUUID().toString().substring(0, 8);
        
        this.mqttHandler = new MqttHandler(context, new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                Log.e(TAG, "MQTT connection lost", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) {
                String payload = new String(mqttMessage.getPayload());
                
                // Verificar si es nuestro propio mensaje PING
                if (payload.contains("\"type\":\"ping\"") && payload.contains("\"clientId\":\"" + clientId + "\"")) {
                    return; // Ignorar nuestros propios mensajes ping
                }
                
                // Verificar si es un PONG para nosotros usando String.contains (método rápido)
                if (payload.contains("\"type\":\"pong\"") && payload.contains("\"clientId\":\"" + clientId + "\"")) {
                    // Determinar si está online
                    boolean isOnline = payload.contains("\"status\":\"online\"") || !payload.contains("\"status\":\"offline\"");
                    
                    // Notificar al listener
                    notifyListenerAndCleanup(isOnline);
                    return;
                }
                
                // Intentar con la clase CustomMqttMessage
                try {
                    CustomMqttMessage message = CustomMqttMessage.fromJson(payload);
                    
                    // Solo procesamos si es un mensaje PONG
                    if (message.isPong()) {
                        String msgClientId = message.getClientId();
                        
                        // Verificar si el mensaje es para esta instancia
                        if (clientId.equals(msgClientId)) {
                            notifyListenerAndCleanup(message.isOnline());
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error al procesar mensaje", e);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // No necesitamos implementar esto
            }
        });
    }
    
    /**
     * Notifica al listener y limpia recursos
     */
    private void notifyListenerAndCleanup(boolean isConnected) {
        ConnectionCheckListener listenerToNotify = currentListener;
        
        if (listenerToNotify != null) {
            // Limpiar variables
            currentListener = null;
            
            // Cancelar el timeout
            if (timeoutRunnable != null) {
                handler.removeCallbacks(timeoutRunnable);
                timeoutRunnable = null;
            }
            
            isChecking.set(false);
            
            // Notificar en el hilo principal
            handler.post(() -> {
                listenerToNotify.onConnectionCheckResult(isConnected);
            });
        }
    }
    
    /**
     * Verificar si el dispositivo ESP32 está conectado
     * @param listener El listener que recibirá el resultado
     */
    public void checkConnection(ConnectionCheckListener listener) {
        // Evitar verificaciones concurrentes
        if (isChecking.getAndSet(true)) {
            listener.onError("Ya hay una verificación en progreso");
            return;
        }
        
        this.currentListener = listener;
        
        try {
            // Conectar al broker MQTT
            if (!mqttHandler.isConnected()) {
                mqttHandler.connect();
            }
            
            // Suscribirse a múltiples tópicos
            mqttHandler.subscribe(AppConstants.MQTT_TOPIC_DEVICE_COMMANDS, 1);
            mqttHandler.subscribe(AppConstants.MQTT_TOPIC_DEVICE_STATUS, 1);
            mqttHandler.subscribe("/device/#", 1);
            
            // Crear mensaje ping con CustomMqttMessage
            CustomMqttMessage pingMessage = CustomMqttMessage.createPing(null, true);
            pingMessage.addPayload("clientId", clientId);
            
            // Añadir el clientId también al nivel raíz para compatibilidad
            JSONObject jsonObj = new JSONObject(pingMessage.toString());
            jsonObj.put("clientId", clientId);
            
            // Publicar el mensaje de ping
            mqttHandler.publishMessage(AppConstants.MQTT_TOPIC_DEVICE_COMMANDS, jsonObj.toString());
            
            // Configurar timeout
            this.timeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    ConnectionCheckListener listenerToNotify = currentListener;
                    if (listenerToNotify != null) {
                        // Limpiar referencias
                        currentListener = null;
                        timeoutRunnable = null;
                        isChecking.set(false);
                        
                        // Notificar timeout
                        listenerToNotify.onConnectionCheckResult(false);
                    }
                }
            };
            
            // Programar el timeout
            handler.postDelayed(timeoutRunnable, AppConstants.MQTT_CONNECTION_TIMEOUT_MS);
            
        } catch (MqttException | JSONException e) {
            Log.e(TAG, "Error en la verificación de conexión", e);
            if (currentListener != null) {
                listener.onError("Error: " + e.getMessage());
                currentListener = null;
            }
            isChecking.set(false);
        }
    }
    
    /**
     * Liberar recursos
     */
    public void release() {
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        handler.removeCallbacksAndMessages(null);
        mqttHandler.disconnect();
    }
}