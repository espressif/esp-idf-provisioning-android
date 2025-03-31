package com.espressif.ui.activities.mqtt_activities;

import org.json.JSONException;
import org.json.JSONObject;

public class CustomMqttMessage {
    private final JSONObject json;

    // Constructor para crear mensajes de un tipo específico
    public CustomMqttMessage(String type) {
        json = new JSONObject();
        try {
            json.put("type", type);
            json.put("timestamp", System.currentTimeMillis());
            json.put("payload", new JSONObject());
        } catch (JSONException e) {
            throw new RuntimeException("Error creating MqttMessage", e);
        }
    }

    // Constructor para deserializar mensajes recibidos
    // Cambia la firma para evitar la duplicación
    public CustomMqttMessage(JSONObject jsonObject) {
        this.json = jsonObject;
    }
    
    // Método estático para crear desde una cadena JSON
    public static CustomMqttMessage fromJson(String jsonString) throws JSONException {
        return new CustomMqttMessage(new JSONObject(jsonString));
    }

    public void addPayload(String key, Object value) throws JSONException {
        json.getJSONObject("payload").put(key, value);
    }

    // Modifica los métodos createXXX para evitar ambigüedad
    public static CustomMqttMessage createPing(String deviceId, boolean isFinder) throws JSONException {
        // Crear un JSONObject directamente
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("type", "ping");
        jsonObj.put("timestamp", System.currentTimeMillis());
        jsonObj.put("payload", new JSONObject());
        jsonObj.put("finder", isFinder);
        if (deviceId != null) {
            jsonObj.put("deviceId", deviceId);
        }
        return new CustomMqttMessage(jsonObj);
    }

    public static CustomMqttMessage createPong(JSONObject pingMessage) throws JSONException {
        // Crear un JSONObject directamente
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("type", "pong");
        jsonObj.put("timestamp", System.currentTimeMillis());
        jsonObj.put("payload", new JSONObject());
        if (pingMessage.has("finder") && pingMessage.getBoolean("finder")) {
            jsonObj.put("status", "online");
        }
        return new CustomMqttMessage(jsonObj);
    }

    public static CustomMqttMessage createCommand(String cmd) throws JSONException {
        // Crear un JSONObject directamente
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("type", "command");
        jsonObj.put("timestamp", System.currentTimeMillis());
        jsonObj.put("payload", new JSONObject());
        jsonObj.getJSONObject("payload").put("cmd", cmd);
        return new CustomMqttMessage(jsonObj);
    }

    public String getType() throws JSONException {
        return json.getString("type");
    }

    public boolean isPong() throws JSONException {
        return "pong".equals(getType());
    }

    public String getStatus() throws JSONException {
        return json.optString("status", "unknown");
    }

    public boolean isOnline() throws JSONException {
        return "online".equals(getStatus());
    }

    public String getClientId() throws JSONException {
        // Primero buscar en el payload
        if (json.has("payload") && json.getJSONObject("payload").has("clientId")) {
            return json.getJSONObject("payload").getString("clientId");
        }
        // Si no está en el payload, buscar en la raíz
        else if (json.has("clientId")) {
            return json.getString("clientId");
        }
        return null;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}