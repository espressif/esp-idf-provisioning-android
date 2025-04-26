package com.espressif.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.espressif.ui.models.Medication;
import com.espressif.ui.models.MedicationType;
import com.espressif.ui.models.Schedule;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repositorio para gestionar medicamentos y horarios en Firebase.
 * Los medicamentos se almacenan dentro del nodo del paciente correspondiente.
 */
public class MedicationRepository {
    
    private static final String TAG = "MedicationRepository";
    private static MedicationRepository instance;
    
    // Referencia a Firebase - patients/[patientId]/medications
    private final DatabaseReference patientsRef;
    
    private MedicationRepository() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        patientsRef = database.getReference("patients");
    }
    
    /**
     * Obtiene una instancia del repositorio (singleton)
     */
    public static synchronized MedicationRepository getInstance() {
        if (instance == null) {
            instance = new MedicationRepository();
        }
        return instance;
    }
    
    /**
     * Agrega un nuevo medicamento a la base de datos
     */
    public void addMedication(Medication medication, DatabaseCallback callback) {
        if (medication.getId() == null) {
            medication.setId(UUID.randomUUID().toString());
        }
        
        String patientId = medication.getPatientId();
        if (patientId == null || patientId.isEmpty() || "current_user_id".equals(patientId)) {
            Log.e(TAG, "Error: ID de paciente inválido para nuevo medicamento: " + patientId);
            callback.onError("El ID del paciente no es válido");
            return;
        }
        
        // Asegurar que los medicamentos de tipo líquido siempre usen el compartimento 4
        if (MedicationType.LIQUID.equals(medication.getType())) {
            medication.setCompartmentNumber(4);
            Log.d(TAG, "Forzando compartimento 4 para medicamento líquido: " + medication.getName());
        }
        
        // Establecer timestamps
        long currentTime = System.currentTimeMillis();
        medication.setCreatedAt(currentTime);
        medication.setUpdatedAt(currentTime);
        
        // Guardar en patients/[patientId]/medications/[medicationId]
        patientsRef.child(patientId)
                .child("medications")
                .child(medication.getId())
                .setValue(medication.toMap())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Medicamento agregado con ID: " + medication.getId());
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al agregar medicamento", e);
                    callback.onError("Error al guardar medicamento: " + e.getMessage());
                });
    }
    
    /**
     * Actualiza un medicamento existente
     */
    public void updateMedication(Medication medication, DatabaseCallback callback) {
        if (medication.getId() == null) {
            callback.onError("ID de medicamento no válido");
            return;
        }
        
        String patientId = medication.getPatientId();
        if (patientId == null || patientId.isEmpty() || "current_user_id".equals(patientId)) {
            Log.e(TAG, "Error: ID de paciente inválido para actualizar medicamento: " + patientId);
            callback.onError("El ID del paciente no es válido");
            return;
        }
        
        // Asegurar que los medicamentos de tipo líquido siempre usen el compartimento 4
        if (MedicationType.LIQUID.equals(medication.getType())) {
            medication.setCompartmentNumber(4);
            Log.d(TAG, "Forzando compartimento 4 para actualización de medicamento líquido: " + medication.getName());
        }
        
        // Actualizar el timestamp
        medication.setUpdatedAt(System.currentTimeMillis());
        
        Map<String, Object> medicationValues = medication.toMap();
        
        // Actualizar en patients/[patientId]/medications/[medicationId]
        patientsRef.child(patientId)
                .child("medications")
                .child(medication.getId())
                .updateChildren(medicationValues)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Medicamento actualizado con ID: " + medication.getId());
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al actualizar medicamento", e);
                    callback.onError("Error al actualizar medicamento: " + e.getMessage());
                });
    }
    
    /**
     * Elimina un medicamento
     */
    public void deleteMedication(String patientId, String medicationId, DatabaseCallback callback) {
        if (medicationId == null || medicationId.isEmpty()) {
            callback.onError("ID de medicamento no válido");
            return;
        }
        
        if (patientId == null || patientId.isEmpty() || "current_user_id".equals(patientId)) {
            Log.e(TAG, "Error: ID de paciente inválido para eliminar medicamento: " + patientId);
            callback.onError("ID del paciente no válido");
            return;
        }
        
        // Eliminar de patients/[patientId]/medications/[medicationId]
        patientsRef.child(patientId)
                .child("medications")
                .child(medicationId)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Medicamento eliminado con ID: " + medicationId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al eliminar medicamento", e);
                    callback.onError("Error al eliminar medicamento: " + e.getMessage());
                });
    }
    
    /**
     * Obtiene todos los medicamentos de un paciente
     */
    public void getMedications(String patientId, DataCallback<List<Medication>> callback) {
        if (patientId == null || patientId.isEmpty() || "current_user_id".equals(patientId)) {
            Log.e(TAG, "Error: ID de paciente inválido para obtener medicamentos: " + patientId);
            callback.onError("ID del paciente no válido");
            return;
        }
        
        // Obtener de patients/[patientId]/medications/
        patientsRef.child(patientId)
                .child("medications")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        List<Medication> medications = new ArrayList<>();
                        
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            try {
                                Medication medication = snapshot.getValue(Medication.class);
                                if (medication != null) {
                                    // Asegurar que los medicamentos líquidos siempre tienen compartimento 4
                                    if (MedicationType.LIQUID.equals(medication.getType()) && 
                                        medication.getCompartmentNumber() != 4) {
                                        medication.setCompartmentNumber(4);
                                    }
                                    
                                    // Asegurar que el medicamento tiene el patientId correcto
                                    if (medication.getPatientId() == null || !medication.getPatientId().equals(patientId)) {
                                        medication.setPatientId(patientId);
                                    }
                                    
                                    medications.add(medication);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error al convertir medicamento", e);
                            }
                        }
                        
                        Log.d(TAG, "Medicamentos cargados: " + medications.size());
                        callback.onSuccess(medications);
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error al cargar medicamentos", databaseError.toException());
                        callback.onError("Error al cargar medicamentos: " + databaseError.getMessage());
                    }
                });
    }
    
    /**
     * Obtiene un medicamento específico
     */
    public void getMedication(String patientId, String medicationId, DataCallback<Medication> callback) {
        if (medicationId == null || medicationId.isEmpty()) {
            callback.onError("ID de medicamento no válido");
            return;
        }
        
        if (patientId == null || patientId.isEmpty() || "current_user_id".equals(patientId)) {
            Log.e(TAG, "Error: ID de paciente inválido para obtener medicamento: " + patientId);
            callback.onError("ID del paciente no válido");
            return;
        }
        
        // Obtener de patients/[patientId]/medications/[medicationId]
        patientsRef.child(patientId)
                .child("medications")
                .child(medicationId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Medication medication = dataSnapshot.getValue(Medication.class);
                        if (medication != null) {
                            // Asegurar que los medicamentos líquidos siempre tienen compartimento 4
                            if (MedicationType.LIQUID.equals(medication.getType()) && 
                                medication.getCompartmentNumber() != 4) {
                                medication.setCompartmentNumber(4);
                            }
                            
                            Log.d(TAG, "Medicamento cargado: " + medication.getName());
                            callback.onSuccess(medication);
                        } else {
                            callback.onError("Medicamento no encontrado");
                        }
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error al cargar medicamento", databaseError.toException());
                        callback.onError("Error al cargar medicamento: " + databaseError.getMessage());
                    }
                });
    }
    
    /**
     * Añade o actualiza un horario para un medicamento
     */
    public void saveSchedule(String patientId, String medicationId, Schedule schedule, DatabaseCallback callback) {
        if (medicationId == null || medicationId.isEmpty()) {
            callback.onError("ID de medicamento no válido");
            return;
        }
        
        if (patientId == null || patientId.isEmpty() || "current_user_id".equals(patientId)) {
            Log.e(TAG, "Error: ID de paciente inválido para guardar horario: " + patientId);
            callback.onError("ID del paciente no válido");
            return;
        }
        
        if (schedule.getId() == null) {
            schedule.setId(UUID.randomUUID().toString());
        }
        
        schedule.setMedicationId(medicationId);
        
        // Guardar en patients/[patientId]/medications/[medicationId]/schedules/[scheduleId]
        patientsRef.child(patientId)
                .child("medications")
                .child(medicationId)
                .child("schedules")
                .child(schedule.getId())
                .setValue(schedule)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Horario guardado con ID: " + schedule.getId());
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al guardar horario", e);
                    callback.onError("Error al guardar horario: " + e.getMessage());
                });
    }
    
    /**
     * Elimina un horario
     */
    public void deleteSchedule(String patientId, String medicationId, String scheduleId, DatabaseCallback callback) {
        if (medicationId == null || medicationId.isEmpty() || scheduleId == null || scheduleId.isEmpty()) {
            callback.onError("IDs no válidos para horario o medicamento");
            return;
        }
        
        if (patientId == null || patientId.isEmpty() || "current_user_id".equals(patientId)) {
            Log.e(TAG, "Error: ID de paciente inválido para eliminar horario: " + patientId);
            callback.onError("ID del paciente no válido");
            return;
        }
        
        // Eliminar de patients/[patientId]/medications/[medicationId]/schedules/[scheduleId]
        patientsRef.child(patientId)
                .child("medications")
                .child(medicationId)
                .child("schedules")
                .child(scheduleId)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Horario eliminado con ID: " + scheduleId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al eliminar horario", e);
                    callback.onError("Error al eliminar horario: " + e.getMessage());
                });
    }
    
    /**
     * Actualiza la confirmación de toma de un medicamento
     */
    public void updateMedicationTaken(String patientId, String medicationId, String scheduleId, boolean taken, DatabaseCallback callback) {
        updateScheduleStatus(patientId, medicationId, scheduleId, taken ? "taken" : "scheduled", callback);
    }
    
    /**
     * Escucha cambios en los medicamentos de un paciente en tiempo real
     */
    public ValueEventListener listenForMedications(String patientId, final DataCallback<List<Medication>> callback) {
        if (patientId == null || patientId.isEmpty() || "current_user_id".equals(patientId)) {
            Log.e(TAG, "Error: ID de paciente inválido para escuchar cambios: " + patientId);
            callback.onError("ID del paciente no válido");
            return null;
        }
        
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<Medication> medications = new ArrayList<>();
                
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        Medication medication = snapshot.getValue(Medication.class);
                        if (medication != null) {
                            // Asegurar que los medicamentos líquidos siempre tienen compartimento 4
                            if (MedicationType.LIQUID.equals(medication.getType()) && 
                                medication.getCompartmentNumber() != 4) {
                                medication.setCompartmentNumber(4);
                            }
                            
                            medications.add(medication);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error al convertir medicamento", e);
                    }
                }
                
                Log.d(TAG, "Cambio en medicamentos detectado, total: " + medications.size());
                callback.onSuccess(medications);
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error en listener de medicamentos", databaseError.toException());
                callback.onError("Error al escuchar cambios: " + databaseError.getMessage());
            }
        };
        
        // Escuchar cambios en patients/[patientId]/medications/
        patientsRef.child(patientId).child("medications").addValueEventListener(listener);
        return listener;
    }
    
    /**
     * Detiene la escucha de cambios en los medicamentos
     */
    public void stopListening(String patientId, ValueEventListener listener) {
        if (patientId != null && listener != null) {
            patientsRef.child(patientId).child("medications").removeEventListener(listener);
        }
    }
    
    /**
     * Actualiza el estado de dispensado y detección por sensor de un medicamento
     */
    public void updateDispensationStatus(String patientId, String medicationId, String scheduleId, 
                                         boolean dispensed, boolean detected, DatabaseCallback callback) {
        if (patientId == null || medicationId == null || scheduleId == null) {
            callback.onError("IDs no válidos");
            return;
        }
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("dispensed", dispensed);
        
        long currentTime = System.currentTimeMillis();
        
        if (dispensed) {
            updates.put("dispensedAt", currentTime);
        }
        
        if (detected) {
            updates.put("detectedBySensor", true);
            updates.put("detectedAt", currentTime);
            updates.put("takingConfirmed", true);
            updates.put("lastTaken", currentTime);
        }
        
        // Actualizar en patients/[patientId]/medications/[medicationId]/schedules/[scheduleId]
        patientsRef.child(patientId)
                .child("medications")
                .child(medicationId)
                .child("schedules")
                .child(scheduleId)
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Estado de dispensación actualizado para horario: " + scheduleId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al actualizar estado de dispensación", e);
                    callback.onError("Error al actualizar estado: " + e.getMessage());
                });
    }

    /**
     * Marca un horario como detectado por el sensor ultrasónico
     */
    public void markAsDetectedBySensor(String patientId, String medicationId, String scheduleId, DatabaseCallback callback) {
        updateDispensationStatus(patientId, medicationId, scheduleId, true, true, callback);
    }

    /**
     * Marca un horario como dispensado pero aún no detectado
     */
    public void markAsDispensed(String patientId, String medicationId, String scheduleId, DatabaseCallback callback) {
        updateScheduleStatus(patientId, medicationId, scheduleId, "dispensed", callback);
    }
    
    /**
     * Interfaz de callback para operaciones de base de datos
     */
    public interface DatabaseCallback {
        void onSuccess();
        void onError(String errorMessage);
    }
    
    /**
     * Interfaz de callback para operaciones que devuelven datos
     */
    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String errorMessage);
    }

    /**
     * Interfaz de callback específica para obtener un solo medicamento
     */
    public interface MedicationCallback {
        void onSuccess(Medication medication);
        void onError(String errorMessage);
    }

    /**
     * Interfaz de callback para verificar si un medicamento ha sido tomado
     */
    public interface MedicationTakenCallback {
        void onResult(boolean isTaken, Medication medication, Schedule schedule);
        void onError(String errorMessage);
    }

    /**
     * Interfaz de callback para obtener múltiples medicamentos
     */
    public interface MedicationsCallback {
        void onSuccess(List<Medication> medications);
        void onError(String errorMessage);
    }

    /**
     * Obtiene un medicamento por su ID
     */
    public void getMedicationById(String patientId, String medicationId, MedicationCallback callback) {
        // Usar el método existente getMedication pero con un adaptador para el callback
        getMedication(patientId, medicationId, new DataCallback<Medication>() {
            @Override
            public void onSuccess(Medication data) {
                callback.onSuccess(data);
            }
            
            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /**
     * Verifica si un medicamento ha sido tomado
     */
    public void checkMedicationTaken(String patientId, String medicationId, String scheduleId, MedicationTakenCallback callback) {
        // Primero obtenemos el medicamento
        getMedication(patientId, medicationId, new DataCallback<Medication>() {
            @Override
            public void onSuccess(Medication medication) {
                // Buscar el horario específico
                Schedule targetSchedule = null;
                boolean isTaken = false;
                
                if (medication != null && medication.getScheduleList() != null) {
                    for (Schedule schedule : medication.getScheduleList()) {
                        if (schedule.getId().equals(scheduleId)) {
                            targetSchedule = schedule;
                            
                            // Verificar si está marcado como tomado
                            // Consideramos que está tomado si:
                            // - Tiene la bandera takingConfirmed = true, o
                            // - Ha sido detectado por el sensor (detectedBySensor = true)
                            isTaken = schedule.isTakingConfirmed() || schedule.isDetectedBySensor();
                            
                            break;
                        }
                    }
                }
                
                if (targetSchedule != null) {
                    callback.onResult(isTaken, medication, targetSchedule);
                } else {
                    callback.onError("Horario no encontrado: " + scheduleId);
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /**
     * Registra un evento de medicación no tomada
     */
    public void registerMissedMedication(String patientId, String medicationId, String scheduleId, DatabaseCallback callback) {
        if (patientId == null || medicationId == null || scheduleId == null) {
            callback.onError("IDs no válidos");
            return;
        }
        
        // Actualizar estado a "missed" en el schedule
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "missed");
        updates.put("missed", true);
        updates.put("missedAt", ServerValue.TIMESTAMP);
        updates.put("statusUpdatedAt", ServerValue.TIMESTAMP);
        
        patientsRef.child(patientId)
                .child("medications")
                .child(medicationId)
                .child("schedules")
                .child(scheduleId)
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Estado de horario actualizado a 'missed' para: " + scheduleId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al actualizar estado de medicación perdida", e);
                    callback.onError("Error al actualizar estado: " + e.getMessage());
                });
    }

    /**
     * Obtiene todos los medicamentos de un paciente
     * Método para mantener compatibilidad con el callback específico para medicamentos
     */
    public void getAllMedications(String patientId, MedicationsCallback callback) {
        // Reutiliza el método existente getMedications pero con el callback especializado
        getMedications(patientId, new DataCallback<List<Medication>>() {
            @Override
            public void onSuccess(List<Medication> medications) {
                callback.onSuccess(medications);
            }
            
            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /**
     * Actualiza el estado de un horario de medicamento
     */
    public void updateScheduleStatus(String patientId, String medicationId, String scheduleId, 
                              String status, DatabaseCallback callback) {
        if (patientId == null || medicationId == null || scheduleId == null) {
            callback.onError("IDs no válidos");
            return;
        }
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("statusUpdatedAt", ServerValue.TIMESTAMP);
        
        // Añadir campos adicionales según el estado
        switch (status) {
            case "dispensed":
                updates.put("dispensed", true);
                updates.put("dispensedAt", ServerValue.TIMESTAMP);
                break;
            case "taken":
                updates.put("takingConfirmed", true);
                updates.put("lastTaken", ServerValue.TIMESTAMP);
                break;
            case "missed":
                updates.put("missed", true);
                updates.put("missedAt", ServerValue.TIMESTAMP);
                break;
            case "scheduled":
                // Estado por defecto, no necesita campos adicionales
                break;
        }
        
        // Actualizar en patients/[patientId]/medications/[medicationId]/schedules/[scheduleId]
        patientsRef.child(patientId)
                .child("medications")
                .child(medicationId)
                .child("schedules")
                .child(scheduleId)
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Estado de horario actualizado a " + status + " para: " + scheduleId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al actualizar estado de horario", e);
                    callback.onError("Error al actualizar estado: " + e.getMessage());
                });
    }
}