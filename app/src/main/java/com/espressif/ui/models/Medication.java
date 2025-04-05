package com.espressif.data.model;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo que representa un medicamento en el sistema.
 */
@IgnoreExtraProperties
public class Medication {
    private String id;              // ID único
    private String name;            // Nombre del medicamento
    private String type;            // Tipo: píldora, líquido, etc.
    private double amount;          // Cantidad
    private String unit;            // Unidad: ml, mg, pastillas, etc.
    private String notes;           // Notas adicionales
    private String patientId;       // ID del paciente asociado
    private Map<String, Schedule> schedules; // Horarios programados por ID
    private long createdAt;         // Timestamp de creación
    private long updatedAt;         // Timestamp de última modificación
    private int compartmentNumber;  // Número de compartimento en el dispensador (1-6)
    private int remainingDoses;     // Dosis restantes en el dispensador

    // Constructor vacío requerido para Firebase
    public Medication() {
        // Constructor vacío necesario para Firebase
    }

    public Medication(String id, String name, String type, double amount, String unit) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.amount = amount;
        this.unit = unit;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.schedules = new HashMap<>();
    }

    // Getters y setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public Map<String, Schedule> getSchedules() {
        return schedules;
    }

    public void setSchedules(Map<String, Schedule> schedules) {
        this.schedules = schedules;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getCompartmentNumber() {
        return compartmentNumber;
    }

    public void setCompartmentNumber(int compartmentNumber) {
        this.compartmentNumber = compartmentNumber;
    }

    public int getRemainingDoses() {
        return remainingDoses;
    }

    public void setRemainingDoses(int remainingDoses) {
        this.remainingDoses = remainingDoses;
    }

    // Métodos de ayuda para trabajar con horarios
    @Exclude
    public void addSchedule(Schedule schedule) {
        if (schedules == null) {
            schedules = new HashMap<>();
        }
        schedule.setMedicationId(id);
        schedules.put(schedule.getId(), schedule);
        this.updatedAt = System.currentTimeMillis();
    }

    @Exclude
    public void removeSchedule(String scheduleId) {
        if (schedules != null) {
            schedules.remove(scheduleId);
            this.updatedAt = System.currentTimeMillis();
        }
    }

    @Exclude
    public List<Schedule> getScheduleList() {
        if (schedules == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(schedules.values());
    }

    /**
     * Verifica si este medicamento puede asignarse al compartimento especificado
     */
    @Exclude
    public boolean canAssignToCompartment(int compartment) {
        return MedicationType.isCompatibleWithCompartment(this.type, compartment);
    }

    /**
     * Asigna un compartimento a este medicamento si es compatible
     */
    @Exclude
    public boolean assignToCompatibleCompartment(int compartment) {
        if (canAssignToCompartment(compartment)) {
            this.compartmentNumber = compartment;
            return true;
        }
        return false;
    }

    /**
     * Convierte esta instancia a un Map para guardarlo en Firebase
     */
    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("name", name);
        result.put("type", type);
        result.put("amount", amount);
        result.put("unit", unit);
        result.put("notes", notes);
        result.put("patientId", patientId);
        result.put("schedules", schedules);
        result.put("createdAt", createdAt);
        result.put("updatedAt", updatedAt);
        result.put("compartmentNumber", compartmentNumber);
        result.put("remainingDoses", remainingDoses);
        
        return result;
    }
}