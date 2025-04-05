package com.espressif.ui.activities.mqtt_activities;

import android.content.Context;
import android.util.Log;

import com.espressif.data.model.Medication;
import com.espressif.data.model.Schedule;
import com.espressif.data.repository.MedicationRepository;
import com.espressif.data.repository.UserRepository;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Clase para manejar los mensajes MQTT relacionados con el sensor ultrasónico
 * que detecta medicamentos dispensados.
 */
public class UltrasonicSensorHandler {
    private static final String TAG = "UltrasonicSensorHandler";
    
    private final Context context;
    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;
    
    public UltrasonicSensorHandler(Context context) {
        this.context = context;
        this.medicationRepository = MedicationRepository.getInstance();
        this.userRepository = UserRepository.getInstance(context);
    }
    
    /**
     * Procesa un mensaje MQTT recibido del sensor ultrasónico
     */
    public void processUltrasonicMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            Log.d(TAG, "Mensaje del sensor ultrasónico: " + payload);
            
            JSONObject jsonObject = new JSONObject(payload);
            
            // Verificar si es un evento de detección
            if (jsonObject.has("event") && "detection".equals(jsonObject.getString("event"))) {
                handleDetectionEvent(jsonObject);
            }
            // Verificar si es una confirmación de dispensado
            else if (jsonObject.has("event") && "dispensed".equals(jsonObject.getString("event"))) {
                handleDispensedEvent(jsonObject);
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error al procesar mensaje del sensor ultrasónico", e);
        }
    }
    
    /**
     * Maneja un evento de detección del sensor ultrasónico
     */
    private void handleDetectionEvent(JSONObject jsonObject) throws JSONException {
        if (!jsonObject.has("medicationId") || !jsonObject.has("scheduleId")) {
            Log.e(TAG, "Mensaje de detección incompleto: falta medicationId o scheduleId");
            return;
        }
        
        String medicationId = jsonObject.getString("medicationId");
        String scheduleId = jsonObject.getString("scheduleId");
        String patientId = userRepository.getConnectedPatientId();
        
        if (patientId == null || patientId.isEmpty()) {
            Log.e(TAG, "No hay un paciente conectado");
            return;
        }
        
        // Marcar como detectado por el sensor
        medicationRepository.markAsDetectedBySensor(patientId, medicationId, scheduleId, new MedicationRepository.DatabaseCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Medicamento marcado como detectado por el sensor: " + medicationId);
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error al marcar medicamento como detectado: " + errorMessage);
            }
        });
    }
    
    /**
     * Maneja un evento de dispensado (antes de la detección)
     */
    private void handleDispensedEvent(JSONObject jsonObject) throws JSONException {
        if (!jsonObject.has("medicationId") || !jsonObject.has("scheduleId")) {
            Log.e(TAG, "Mensaje de dispensado incompleto: falta medicationId o scheduleId");
            return;
        }
        
        String medicationId = jsonObject.getString("medicationId");
        String scheduleId = jsonObject.getString("scheduleId");
        String patientId = userRepository.getConnectedPatientId();
        
        if (patientId == null || patientId.isEmpty()) {
            Log.e(TAG, "No hay un paciente conectado");
            return;
        }
        
        // Marcar como dispensado (pero aún no detectado)
        medicationRepository.markAsDispensed(patientId, medicationId, scheduleId, new MedicationRepository.DatabaseCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Medicamento marcado como dispensado: " + medicationId);
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error al marcar medicamento como dispensado: " + errorMessage);
            }
        });
    }
}