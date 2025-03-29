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

import com.espressif.AppConstants;
import com.espressif.ui.activities.mqtt_activities.CustomMqttMessage;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class DeviceConnectionChecker {
    private static final String TAG = "DeviceConnectionChecker";
    
    private final MqttHandler mqttHandler;
    private final Handler handler;
    private final String clientId;
    
    // Interfaz para notificar el resultado de la verificación
    public interface ConnectionCheckListener {
        void onConnectionCheckResult(boolean isConnected);
        void onError(String errorMessage);
    }
    
    public DeviceConnectionChecker(Context context) {
        this.handler = new Handler(Looper.getMainLooper());
        
        // Crear ID de cliente único para este checker
        this.clientId = UUID.randomUUID().toString().substring(0, 8);
        
        // Configurar el MQTT Handler con un callback específico
        this.mqttHandler = new MqttHandler(context, new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                Log.e(TAG, "MQTT connection lost", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) {
                if (topic.equals(AppConstants.MQTT_TOPIC_DEVICE_STATUS)) {
                    try {
                        // Convertir el mensaje MQTT a nuestro propio formato usando el nuevo método estático
                        String payload = new String(mqttMessage.getPayload());
                        CustomMqttMessage message = CustomMqttMessage.fromJson(payload);
                        
                        // Verificar si este mensaje es para nosotros (basado en el clientId)
                        if (message.isPong() && clientId.equals(message.getClientId())) {
                            // Si recibimos un pong con nuestro clientId, cancelar el timeout y notificar éxito
                            boolean isOnline = message.isOnline();
                            Log.d(TAG, "Received PONG response. Device is " + 
                                  (isOnline ? "online" : "offline"));
                            
                            // Pasar el resultado al listener actual
                            if (currentListener != null) {
                                currentListener.onConnectionCheckResult(isOnline);
                                currentListener = null; // Evitar llamadas duplicadas
                            }
                            
                            // Cancelar el timeout
                            handler.removeCallbacksAndMessages(null);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing received message", e);
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // No necesitamos manejar esto
            }
        });
    }
    
    private ConnectionCheckListener currentListener;
    private final AtomicBoolean isChecking = new AtomicBoolean(false);
    
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
            
            // Suscribirse al tema de estado
            mqttHandler.subscribe(AppConstants.MQTT_TOPIC_DEVICE_STATUS, 1);
            
            // Enviar mensaje ping
            CustomMqttMessage pingMessage = CustomMqttMessage.createPing(null, true);
            
            // Añadir el clientId al mensaje ping para identificar nuestra solicitud
            pingMessage.addPayload("clientId", clientId);
            
            // Publicar el mensaje en el tópico de estado
            mqttHandler.publishMessage(AppConstants.MQTT_TOPIC_DEVICE_STATUS, pingMessage.toString());
            
            Log.d(TAG, "Ping sent to device, waiting for response with clientId " + clientId);
            
            // Configurar timeout
            handler.postDelayed(() -> {
                if (currentListener != null) {
                    Log.d(TAG, "Connection check timeout");
                    currentListener.onConnectionCheckResult(false);
                    currentListener = null;
                }
                isChecking.set(false);
            }, AppConstants.MQTT_CONNECTION_TIMEOUT_MS);
            
        } catch (MqttException | JSONException e) {
            Log.e(TAG, "Error during connection check", e);
            if (currentListener != null) {
                currentListener.onError("Error: " + e.getMessage());
                currentListener = null;
            }
            isChecking.set(false);
        }
    }
    
    /**
     * Liberar recursos
     */
    public void release() {
        handler.removeCallbacksAndMessages(null);
        mqttHandler.disconnect();
    }
}