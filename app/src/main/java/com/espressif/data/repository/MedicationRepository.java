package com.espressif.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.espressif.ui.models.Medication;
import com.espressif.ui.models.Schedule;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repositorio para gestionar medicamentos y horarios en Firebase.
 */
public class MedicationRepository {
    
    private static final String TAG = "MedicationRepository";
    private static MedicationRepository instance;
    
    // Referencia a Firebase
    private final DatabaseReference medicationsRef;
    
    private MedicationRepository() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        medicationsRef = database.getReference("medications");
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
        if (patientId == null || patientId.isEmpty()) {
            callback.onError("El ID del paciente es requerido");
            return;
        }
        
        // Guardar en la ruta: medications/{patientId}/{medicationId}
        medicationsRef.child(patientId)
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
        if (patientId == null || patientId.isEmpty()) {
            callback.onError("El ID del paciente es requerido");
            return;
        }
        
        // Actualizar el timestamp
        medication.setUpdatedAt(System.currentTimeMillis());
        
        Map<String, Object> medicationValues = medication.toMap();
        
        medicationsRef.child(patientId)
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
        if (medicationId == null || patientId == null) {
            callback.onError("IDs no válidos");
            return;
        }
        
        medicationsRef.child(patientId)
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
        if (patientId == null || patientId.isEmpty()) {
            callback.onError("ID del paciente no válido");
            return;
        }
        
        medicationsRef.child(patientId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        List<Medication> medications = new ArrayList<>();
                        
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            try {
                                Medication medication = snapshot.getValue(Medication.class);
                                if (medication != null) {
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
        if (patientId == null || medicationId == null) {
            callback.onError("IDs no válidos");
            return;
        }
        
        medicationsRef.child(patientId)
                .child(medicationId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Medication medication = dataSnapshot.getValue(Medication.class);
                        if (medication != null) {
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
        if (patientId == null || medicationId == null) {
            callback.onError("IDs no válidos");
            return;
        }
        
        if (schedule.getId() == null) {
            schedule.setId(UUID.randomUUID().toString());
        }
        
        schedule.setMedicationId(medicationId);
        
        medicationsRef.child(patientId)
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
        if (patientId == null || medicationId == null || scheduleId == null) {
            callback.onError("IDs no válidos");
            return;
        }
        
        medicationsRef.child(patientId)
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
        if (patientId == null || medicationId == null || scheduleId == null) {
            callback.onError("IDs no válidos");
            return;
        }
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("takingConfirmed", taken);
        
        if (taken) {
            updates.put("lastTaken", System.currentTimeMillis());
        }
        
        medicationsRef.child(patientId)
                .child(medicationId)
                .child("schedules")
                .child(scheduleId)
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Estado de toma actualizado para horario: " + scheduleId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al actualizar estado de toma", e);
                    callback.onError("Error al actualizar estado: " + e.getMessage());
                });
    }
    
    /**
     * Escucha cambios en los medicamentos de un paciente en tiempo real
     */
    public ValueEventListener listenForMedications(String patientId, final DataCallback<List<Medication>> callback) {
        if (patientId == null || patientId.isEmpty()) {
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
        
        medicationsRef.child(patientId).addValueEventListener(listener);
        return listener;
    }
    
    /**
     * Detiene la escucha de cambios en los medicamentos
     */
    public void stopListening(String patientId, ValueEventListener listener) {
        if (patientId != null && listener != null) {
            medicationsRef.child(patientId).removeEventListener(listener);
        }
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
        
        medicationsRef.child(patientId)
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
        updateDispensationStatus(patientId, medicationId, scheduleId, true, false, callback);
    }
}