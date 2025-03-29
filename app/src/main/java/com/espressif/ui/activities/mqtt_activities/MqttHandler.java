package com.espressif.ui.activities.mqtt_activities;

import android.content.Context;
import android.util.Log;
import org.eclipse.paho.client.mqttv3.*;

import com.espressif.AppConstants;

public class MqttHandler {
    private static final String TAG = "MqttHandler";
    private final MqttClient client;
    private final MqttConnectOptions options;
    private final Context context;

    public MqttHandler(Context context, MqttCallback callback) {
        this.context = context;
        try {
            String clientId = MqttClient.generateClientId();
            this.client = new MqttClient(AppConstants.MQTT_BROKER_URL, clientId, null);
            this.client.setCallback(callback);
            
            this.options = new MqttConnectOptions();
            this.options.setCleanSession(true);
            this.options.setConnectionTimeout(30);
            this.options.setKeepAliveInterval(60);
            this.options.setAutomaticReconnect(true);
        } catch (MqttException e) {
            Log.e(TAG, "Error initializing MQTT client", e);
            throw new RuntimeException("Could not initialize MQTT client", e);
        }
    }

    public void connect() throws MqttException {
        if (!client.isConnected()) {
            client.connect(options);
        }
    }

    public void disconnect() {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error disconnecting MQTT client", e);
        }
    }

    public void subscribe(String topic, int qos) throws MqttException {
        if (client.isConnected()) {
            client.subscribe(topic, qos);
        }
    }

    public void publishMessage(String topic, String message) throws MqttException {
        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
        mqttMessage.setQos(1);
        client.publish(topic, mqttMessage);
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }
}