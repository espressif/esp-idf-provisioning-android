package com.espressif.ui.models;

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
    private int totalPills;         // Número total de pastillas en el compartimento
    private int pillsPerDose;       // Número de pastillas por dosis
    private String compartment;     // Compartimento asignado: "A", "B", "C" o "LIQUID"
    private int dosesTaken;         // Dosis tomadas
    private int volumeTaken;        // Volumen tomado (para líquidos)
    private int totalVolume;        // Volumen total (para líquidos)
    private int doseVolume;         // Volumen por dosis (para líquidos)

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

    public int getTotalPills() {
        return totalPills;
    }

    public void setTotalPills(int totalPills) {
        this.totalPills = totalPills;
    }

    public int getPillsPerDose() {
        return pillsPerDose;
    }

    public void setPillsPerDose(int pillsPerDose) {
        this.pillsPerDose = pillsPerDose;
    }
    
    // Nuevos getters y setters para los campos adicionales
    
    public String getCompartment() {
        // Si el compartment no está establecido pero el compartmentNumber sí,
        // inferir el compartimento según el número
        if (compartment == null && compartmentNumber > 0) {
            switch (compartmentNumber) {
                case 1: return "A";
                case 2: return "B";
                case 3: return "C";
                case 4: return "LIQUID";
                default: return null;
            }
        }
        return compartment;
    }

    public void setCompartment(String compartment) {
        this.compartment = compartment;
        
        // Actualizar también el compartmentNumber para mantener consistencia
        if (compartment != null) {
            switch (compartment) {
                case "A": this.compartmentNumber = 1; break;
                case "B": this.compartmentNumber = 2; break;
                case "C": this.compartmentNumber = 3; break;
                case "LIQUID": this.compartmentNumber = 4; break;
            }
        }
    }
    
    public int getDosesTaken() {
        return dosesTaken;
    }
    
    public void setDosesTaken(int dosesTaken) {
        this.dosesTaken = dosesTaken;
    }
    
    public int getVolumeTaken() {
        return volumeTaken;
    }
    
    public void setVolumeTaken(int volumeTaken) {
        this.volumeTaken = volumeTaken;
    }
    
    public int getTotalVolume() {
        return totalVolume;
    }
    
    public void setTotalVolume(int totalVolume) {
        this.totalVolume = totalVolume;
    }
    
    public int getDoseVolume() {
        return doseVolume;
    }
    
    public void setDoseVolume(int doseVolume) {
        this.doseVolume = doseVolume;
    }

    /**
     * Obtiene el número total de dosis programadas para el día
     * @return Total de dosis programadas
     */
    @Exclude
    public int getTotalDoses() {
        int count = 0;
        for (Schedule schedule : getScheduleList()) {
            if (schedule.isActive() && schedule.isForToday()) {
                count++;
            }
        }
        return count;
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
        String typeStr = (type != null) ? type : (MedicationType.PILL); // Default a PILL
        if ("liquid".equalsIgnoreCase(typeStr) || MedicationType.LIQUID.equals(typeStr)) {
            return compartment == 4; // Compartimento líquido
        } else {
            return compartment >= 1 && compartment <= 3; // Compartimentos para pastillas
        }
    }

    /**
     * Asigna un compartimento a este medicamento si es compatible
     */
    @Exclude
    public boolean assignToCompatibleCompartment(int compartment) {
        if (canAssignToCompartment(compartment)) {
            this.compartmentNumber = compartment;
            
            // Actualizar también la propiedad compartment
            switch (compartment) {
                case 1: this.compartment = "A"; break;
                case 2: this.compartment = "B"; break;
                case 3: this.compartment = "C"; break;
                case 4: this.compartment = "LIQUID"; break;
            }
            
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
        result.put("totalPills", totalPills);
        result.put("pillsPerDose", pillsPerDose);
        result.put("compartment", compartment);
        result.put("dosesTaken", dosesTaken);
        result.put("volumeTaken", volumeTaken);
        result.put("totalVolume", totalVolume);
        result.put("doseVolume", doseVolume);
        
        return result;
    }

    /**
     * Calcula las dosis restantes basado en el número de pastillas
     */
    @Exclude
    public int calculateRemainingDoses() {
        if (MedicationType.PILL.equals(type) && pillsPerDose > 0) {
            return totalPills / pillsPerDose;
        }
        return remainingDoses;
    }

    /**
     * Actualiza las dosis restantes basado en el número de pastillas
     */
    @Exclude
    public void updateRemainingDoses() {
        if (MedicationType.PILL.equals(type) && pillsPerDose > 0) {
            this.remainingDoses = totalPills / pillsPerDose;
        }
    }

    /**
     * Disminuye el número de pastillas cuando se dispensa una dosis
     */
    @Exclude
    public void dispenseDose() {
        if (MedicationType.PILL.equals(type)) {
            // Verificar que pillsPerDose sea positivo
            int pillsToDispense = Math.max(1, pillsPerDose);
            
            // Disminuir el total de pastillas, pero no menos de 0
            totalPills = Math.max(0, totalPills - pillsToDispense);
            
            // Incrementar el contador de dosis tomadas
            dosesTaken++;
            
            // Actualizar dosis restantes
            updateRemainingDoses();
        } else if (MedicationType.LIQUID.equals(type)) {
            // Para medicamentos líquidos
            // Asegurar que doseVolume sea positivo
            int volumeToDispense = Math.max(1, doseVolume);
            
            // Disminuir el volumen total, pero no menos de 0
            totalVolume = Math.max(0, totalVolume - volumeToDispense);
            
            // Incrementar el volumen tomado
            volumeTaken += volumeToDispense;
            
            // Actualizar dosis restantes
            remainingDoses = Math.max(0, remainingDoses - 1);
        }
        
        this.updatedAt = System.currentTimeMillis();
    }
}