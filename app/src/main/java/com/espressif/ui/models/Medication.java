package com.espressif.ui.models;

import android.util.Log;

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

    /**
     * Establece el ID del paciente al que pertenece este medicamento
     * @param patientId ID único del paciente
     */
    public void setPatientId(String patientId) {
        // Validar que no se use un ID problemático
        if ("current_user_id".equals(patientId)) {
            Log.w("Medication", "Intento de asignar ID de paciente inválido: current_user_id");
            return; // No asignar el valor problemático
        }
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
        
        // Verificar que el ID de paciente no sea el valor problemático
        if (!"current_user_id".equals(patientId)) {
            result.put("patientId", patientId);
        } else {
            Log.w("Medication", "Se evitó guardar ID de paciente inválido en toMap()");
        }
        
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
     * Verifica si el ID de paciente es válido
     * @return true si el ID de paciente es válido, false en caso contrario
     */
    @Exclude
    public boolean hasValidPatientId() {
        return patientId != null && !patientId.isEmpty() && !"current_user_id".equals(patientId);
    }

    /**
     * Calcula las dosis restantes basado en el total y la cantidad por dosis
     * @return Número de dosis completas que pueden dispensarse aún
     */
    @Exclude
    public int calculateRemainingDoses() {
        // Si es píldora, calcular basado en totalPills y pillsPerDose
        if (MedicationType.PILL.equals(type) || "pill".equalsIgnoreCase(type)) {
            if (pillsPerDose <= 0) return 0;
            return totalPills / pillsPerDose;
        }
        // Si es líquido, calcular basado en totalVolume y doseVolume
        else if (MedicationType.LIQUID.equals(type) || "liquid".equalsIgnoreCase(type)) {
            if (doseVolume <= 0) return 0;
            return totalVolume / doseVolume;
        }
        return remainingDoses; // Valor por defecto si no se puede calcular
    }

    /**
     * Actualiza las dosis restantes basado en el inventario actual
     */
    @Exclude
    public void updateRemainingDoses() {
        this.remainingDoses = calculateRemainingDoses();
    }

    /**
     * Disminuye el número de pastillas/volumen cuando se dispensa una dosis
     * @return boolean - true si la dispensación fue exitosa
     */
    @Exclude
    public boolean dispenseDose() {
        // Registrar operación de dispensación
        Log.d("Medication", "🧪 dispenseDose() llamado para: " + name);
        
        // Actualizar según el tipo de medicamento
        if (MedicationType.PILL.equals(type) || "pill".equalsIgnoreCase(type)) {
            // Verificar si hay suficientes pastillas
            if (totalPills < pillsPerDose) {
                Log.w("Medication", "⚠️ No hay suficientes pastillas para dispensar: " 
                      + totalPills + " disponibles, " + pillsPerDose + " necesarias");
                return false;
            }
            
            // Actualizar inventario
            totalPills -= pillsPerDose;
            dosesTaken++;
        } 
        else if (MedicationType.LIQUID.equals(type) || "liquid".equalsIgnoreCase(type)) {
            // Verificar si hay suficiente volumen
            if (totalVolume < doseVolume) {
                Log.w("Medication", "⚠️ No hay suficiente líquido para dispensar: " 
                      + totalVolume + " disponibles, " + doseVolume + " necesarios");
                return false;
            }
            
            // Actualizar inventario
            totalVolume -= doseVolume;
            volumeTaken += doseVolume;
            dosesTaken++;
        }
        
        // Actualizar timestamp
        this.updatedAt = System.currentTimeMillis();
        
        // Actualizar las dosis restantes
        updateRemainingDoses();
        
        Log.d("Medication", "✅ Dispensación exitosa de " + name + 
              ", quedan " + remainingDoses + " dosis");
        return true;
    }

    /**
     * Verifica la consistencia de los datos y corrige cualquier problema
     */
    @Exclude
    public void validateConsistency() {
        // Validar el ID de paciente
        if ("current_user_id".equals(patientId)) {
            Log.w("Medication", "ID de paciente inválido detectado durante validación");
            patientId = null; // Limpiar el valor problemático
        }
        
        // Resto del método original sin cambios
        // Validar que pillsPerDose sea positivo para pastillas
        if (MedicationType.PILL.equals(type) && pillsPerDose <= 0) {
            pillsPerDose = 1;
        }
        
        // Validar que doseVolume sea positivo para líquidos
        if (MedicationType.LIQUID.equals(type) && doseVolume <= 0) {
            doseVolume = 1;
        }
        
        // Validar totalPills y dosesTaken
        if (totalPills < 0) {
            totalPills = 0;
        }
        
        if (dosesTaken < 0) {
            dosesTaken = 0;
        }
        
        if (totalVolume < 0) {
            totalVolume = 0;
        }
        
        if (volumeTaken < 0) {
            volumeTaken = 0;
        }
        
        // Actualizar las dosis restantes
        updateRemainingDoses();
        
        // Asegurar que el compartimento es consistente con el número
        if (compartment == null && compartmentNumber > 0) {
            switch (compartmentNumber) {
                case 1: compartment = "A"; break;
                case 2: compartment = "B"; break;
                case 3: compartment = "C"; break;
                case 4: compartment = "LIQUID"; break;
            }
        } else if (compartment != null && compartmentNumber <= 0) {
            switch (compartment) {
                case "A": compartmentNumber = 1; break;
                case "B": compartmentNumber = 2; break;
                case "C": compartmentNumber = 3; break;
                case "LIQUID": compartmentNumber = 4; break;
            }
        }
        
        // Validar consistencia de tipo y compartimento
        if (MedicationType.LIQUID.equals(type) && compartmentNumber != 4) {
            compartmentNumber = 4;
            compartment = "LIQUID";
        }
    }
}