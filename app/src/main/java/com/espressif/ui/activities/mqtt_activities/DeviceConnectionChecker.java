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
import com.espressif.data.source.local.SharedPreferencesHelper;

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
    private final Context context;
    
    // Interfaz para notificar el resultado de la verificación
    public interface ConnectionCheckListener {
        void onConnectionCheckResult(boolean isConnected);
        void onError(String errorMessage);
    }
    
    public DeviceConnectionChecker(Context context) {
        this.context = context;
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

                // Filtrar mensajes de tópicos no relevantes
                if (!topic.startsWith("/device/status") && !topic.startsWith("/device/commands")) {
                    Log.d(TAG, "Mensaje ignorado de tópico no relevante: " + topic);
                    return;
                }

                // Auto-detección: Si recibimos un mensaje en /device/status y no tenemos
                // un dispositivo guardado, intentamos extraer un ID y guardarlo
                if (topic.contains("/device/status")) {
                    try {
                        JSONObject json = new JSONObject(payload);
                        Log.d(TAG, "Procesando mensaje de status para auto-detección: " + json.toString());
                        SharedPreferencesHelper prefsHelper = SharedPreferencesHelper.getInstance(context);
                        String savedDeviceId = prefsHelper.getConnectedDeviceId();

                        if (savedDeviceId == null || savedDeviceId.isEmpty()) {
                            String deviceId = "";
                            if (json.has("deviceId")) {
                                deviceId = json.getString("deviceId");
                            } else if (json.has("ip")) {
                                deviceId = "esp32-" + json.getString("ip").replace(".", "-");
                                Log.d(TAG, "Usando IP para generar deviceId: " + deviceId);
                            } else {
                                deviceId = "esp32-" + UUID.randomUUID().toString().substring(0, 8);
                                Log.d(TAG, "Usando UUID para generar deviceId: " + deviceId);
                            }

                            Log.d(TAG, "¡Auto-detección exitosa! Guardando dispositivo con ID: " + deviceId);
                            prefsHelper.saveConnectedDeviceId(deviceId);
                            prefsHelper.setProvisioningCompleted(true);
                            notifyListenerAndCleanup(true);
                            return;
                        } else {
                            Log.d(TAG, "Ya existe un dispositivo guardado con ID: " + savedDeviceId + ", ignorando auto-detección");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error procesando mensaje de status para auto-detección", e);
                        Log.e(TAG, "Mensaje problemático: " + payload);
                    }
                }

                // Verificar si es nuestro propio mensaje PING
                if (payload.contains("\"type\":\"ping\"") && payload.contains("\"clientId\":\"" + clientId + "\"")) {
                    return; // Ignorar nuestros propios mensajes ping
                }

                // Verificar si es un PONG para nosotros
                if (payload.contains("\"type\":\"pong\"") && payload.contains("\"clientId\":\"" + clientId + "\"")) {
                    boolean isOnline = payload.contains("\"status\":\"online\"") || !payload.contains("\"status\":\"offline\"");
                    notifyListenerAndCleanup(isOnline);
                    return;
                }

                // Intentar con la clase CustomMqttMessage
                try {
                    CustomMqttMessage message = CustomMqttMessage.fromJson(payload);

                    // Verificar si el mensaje es de tipo "pong"
                    if ("pong".equals(message.getType())) {
                        String msgClientId = message.getClientId();

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
        Log.d(TAG, "Iniciando verificación de conexión de dispositivo...");
        
        // Evitar verificaciones concurrentes
        if (isChecking.getAndSet(true)) {
            listener.onError("Ya hay una verificación en progreso");
            return;
        }
        
        this.currentListener = listener;
        SharedPreferencesHelper prefsHelper = SharedPreferencesHelper.getInstance(context);
        
        try {
            // Conectar al broker MQTT siempre
            if (!mqttHandler.isConnected()) {
                mqttHandler.connect();
            }
            
            // Suscribirse a múltiples tópicos, independientemente de si ya tenemos un ID guardado
            mqttHandler.subscribe(AppConstants.MQTT_TOPIC_DEVICE_COMMANDS, 1);
            mqttHandler.subscribe(AppConstants.MQTT_TOPIC_DEVICE_STATUS, 1);
            mqttHandler.subscribe("/device/#", 1);
            
            // Crear una suscripción especial para detectar cualquier mensaje de status
            mqttHandler.subscribe("/device/status", 1);
            
            // Verificar si hay un dispositivo previamente conectado
            String savedDeviceId = prefsHelper.getConnectedDeviceId();
            
            if (savedDeviceId == null || savedDeviceId.isEmpty()) {
                Log.d(TAG, "No se encontró ningún dispositivo guardado, intentando descubrimiento automático");
                
                // NUEVO: Configurar un espera para escuchar posibles mensajes de estado
                // que nos permitan detectar dispositivos automáticamente
                this.timeoutRunnable = new Runnable() {
                    @Override
                    public void run() {
                        ConnectionCheckListener listenerToNotify = currentListener;
                        if (listenerToNotify != null) {
                            // Limpiar referencias
                            currentListener = null;
                            timeoutRunnable = null;
                            isChecking.set(false);
                            
                            Log.d(TAG, "No se detectó ningún dispositivo automáticamente");
                            listenerToNotify.onConnectionCheckResult(false);
                        }
                    }
                };
                
                // Programar un timeout más largo para dar tiempo a que lleguen mensajes
                handler.postDelayed(timeoutRunnable, AppConstants.MQTT_CONNECTION_TIMEOUT_MS * 2);
                
                // No enviamos ping aquí, solo esperamos mensajes que puedan llegar
                // Los mensajes serán manejados por messageArrived en el MqttCallback
                
            } else {
                // Tenemos un ID guardado, verificar usando ping como antes
                Log.d(TAG, "Encontrado dispositivo guardado con ID: " + savedDeviceId);
                
                // Crear mensaje ping con CustomMqttMessage
                CustomMqttMessage pingMessage = CustomMqttMessage.createPing(savedDeviceId, true);
                pingMessage.addPayload("clientId", clientId);
                
                // Añadir el clientId también al nivel raíz para compatibilidad
                JSONObject jsonObj = new JSONObject(pingMessage.toString());
                jsonObj.put("clientId", clientId);
                
                // Publicar el mensaje de ping
                mqttHandler.publishMessage(AppConstants.MQTT_TOPIC_DEVICE_COMMANDS, jsonObj.toString());
                
                // Configurar timeout normal
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
            }
            
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