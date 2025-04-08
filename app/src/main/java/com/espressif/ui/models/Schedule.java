package com.espressif.ui.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modelo que representa un horario programado para un medicamento.
 */
@IgnoreExtraProperties
public class Schedule {
    private String id;              // ID único
    private String medicationId;    // ID del medicamento asociado
    private int hour;               // Hora (0-23)
    private int minute;             // Minuto (0-59)
    private ArrayList<Boolean> daysOfWeek;   // Días de la semana [lun, mar, mié, jue, vie, sáb, dom]
    private boolean active;         // Estado activo/inactivo
    private boolean takingConfirmed; // Confirmación de toma
    private long lastTaken;         // Timestamp última toma
    private long nextScheduled;     // Timestamp próxima dosis programada
    private boolean dispensed;          // Indica si el medicamento fue dispensado por el dispositivo
    private boolean detectedBySensor;   // Indica si el sensor ultrasónico detectó la pastilla/líquido
    private long dispensedAt;           // Timestamp de cuándo se dispensó el medicamento
    private long detectedAt;            // Timestamp de cuándo el sensor detectó el medicamento
    private boolean use24HourFormat = true; // Por defecto usar formato 24h

    // Constructor vacío requerido para Firebase
    public Schedule() {
        // Constructor vacío necesario para Firebase
    }

    public Schedule(int hour, int minute) {
        this.id = UUID.randomUUID().toString();
        this.hour = hour;
        this.minute = minute;
        this.daysOfWeek = new ArrayList<>(Arrays.asList(new Boolean[7])); // Por defecto, todos los días están desactivados
        this.active = true;
        Arrays.fill(this.daysOfWeek.toArray(new Boolean[0]), false); // Inicialmente ningún día seleccionado
    }

    // Constructor completo
    public Schedule(String id, String medicationId, int hour, int minute, ArrayList<Boolean> daysOfWeek, boolean active) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.medicationId = medicationId;
        this.hour = hour;
        this.minute = minute;
        this.daysOfWeek = daysOfWeek != null ? daysOfWeek : new ArrayList<>(Arrays.asList(new Boolean[7]));
        this.active = active;
        this.takingConfirmed = false;
        
        // Calcular el próximo horario programado
        updateNextScheduledTime();
    }

    // Getters y setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMedicationId() {
        return medicationId;
    }

    public void setMedicationId(String medicationId) {
        this.medicationId = medicationId;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
        updateNextScheduledTime();
    }

    public int getMinute() {
        return minute;
    }

    public void setMinute(int minute) {
        this.minute = minute;
        updateNextScheduledTime();
    }

    public ArrayList<Boolean> getDaysOfWeek() {
        return daysOfWeek;
    }

    public void setDaysOfWeek(ArrayList<Boolean> daysOfWeek) {
        this.daysOfWeek = daysOfWeek;
        updateNextScheduledTime();
    }

    // Opcional: método de conveniencia para establecer desde array booleano
    public void setDaysOfWeekFromArray(boolean[] days) {
        ArrayList<Boolean> daysList = new ArrayList<>();
        for (boolean day : days) {
            daysList.add(day);
        }
        this.daysOfWeek = daysList;
        updateNextScheduledTime();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isTakingConfirmed() {
        return takingConfirmed;
    }

    public void setTakingConfirmed(boolean takingConfirmed) {
        this.takingConfirmed = takingConfirmed;
    }

    public long getLastTaken() {
        return lastTaken;
    }

    public void setLastTaken(long lastTaken) {
        this.lastTaken = lastTaken;
    }

    public long getNextScheduled() {
        return nextScheduled;
    }

    public void setNextScheduled(long nextScheduled) {
        this.nextScheduled = nextScheduled;
    }

    public boolean isDispensed() {
        return dispensed;
    }

    public void setDispensed(boolean dispensed) {
        this.dispensed = dispensed;
        if (dispensed) {
            this.dispensedAt = System.currentTimeMillis();
        }
    }

    public boolean isDetectedBySensor() {
        return detectedBySensor;
    }

    public void setDetectedBySensor(boolean detectedBySensor) {
        this.detectedBySensor = detectedBySensor;
        if (detectedBySensor) {
            this.detectedAt = System.currentTimeMillis();
            // Si el sensor detecta, consideramos que la toma está confirmada
            this.takingConfirmed = true;
            this.lastTaken = System.currentTimeMillis();
        }
    }

    public long getDispensedAt() {
        return dispensedAt;
    }

    public void setDispensedAt(long dispensedAt) {
        this.dispensedAt = dispensedAt;
    }

    public long getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(long detectedAt) {
        this.detectedAt = detectedAt;
    }

    public boolean isUse24HourFormat() {
        return use24HourFormat;
    }

    public void setUse24HourFormat(boolean use24HourFormat) {
        this.use24HourFormat = use24HourFormat;
    }

    // Métodos auxiliares
    @Exclude
    public void setDailySchedule() {
        if (daysOfWeek == null) {
            daysOfWeek = new ArrayList<>(Arrays.asList(new Boolean[7]));
        }
        Arrays.fill(daysOfWeek.toArray(new Boolean[0]), true);
        updateNextScheduledTime();
    }

    @Exclude
    public void setWeekdaySchedule() {
        if (daysOfWeek == null) {
            daysOfWeek = new ArrayList<>(Arrays.asList(new Boolean[7]));
        }
        // Lunes a viernes (0-4)
        for (int i = 0; i < 5; i++) {
            daysOfWeek.set(i, true);
        }
        // Sábado y domingo (5-6)
        daysOfWeek.set(5, false);
        daysOfWeek.set(6, false);
        updateNextScheduledTime();
    }

    @Exclude
    public void setWeekendSchedule() {
        if (daysOfWeek == null) {
            daysOfWeek = new ArrayList<>(Arrays.asList(new Boolean[7]));
        }
        // Lunes a viernes (0-4)
        for (int i = 0; i < 5; i++) {
            daysOfWeek.set(i, false);
        }
        // Sábado y domingo (5-6)
        daysOfWeek.set(5, true);
        daysOfWeek.set(6, true);
        updateNextScheduledTime();
    }

    @Exclude
    public boolean isForToday() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int today = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7; // Convertir a índice 0-6 (Lun-Dom)
        return daysOfWeek != null && daysOfWeek.get(today);
    }

    @Exclude
    private void updateNextScheduledTime() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(java.util.Calendar.MINUTE);
        int currentDayOfWeek = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7; // Convertir a índice 0-6 (Lun-Dom)

        // Si el horario de hoy ya pasó, buscar el próximo día disponible
        boolean scheduleForToday = daysOfWeek != null && daysOfWeek.get(currentDayOfWeek) && 
                                   (hour > currentHour || (hour == currentHour && minute > currentMinute));

        if (scheduleForToday) {
            // Programar para hoy
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
            calendar.set(java.util.Calendar.MINUTE, minute);
            calendar.set(java.util.Calendar.SECOND, 0);
            calendar.set(java.util.Calendar.MILLISECOND, 0);
        } else {
            // Buscar el próximo día disponible
            int daysToAdd = 1;
            int nextDay = (currentDayOfWeek + daysToAdd) % 7;
            
            while (daysOfWeek == null || !daysOfWeek.get(nextDay)) {
                daysToAdd++;
                nextDay = (currentDayOfWeek + daysToAdd) % 7;
                
                if (daysToAdd > 7) {
                    // No hay días programados, usar fecha lejana
                    calendar.add(java.util.Calendar.YEAR, 1);
                    nextScheduled = calendar.getTimeInMillis();
                    return;
                }
            }
            
            // Configurar para el próximo día encontrado
            calendar.add(java.util.Calendar.DAY_OF_MONTH, daysToAdd);
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
            calendar.set(java.util.Calendar.MINUTE, minute);
            calendar.set(java.util.Calendar.SECOND, 0);
            calendar.set(java.util.Calendar.MILLISECOND, 0);
        }
        
        nextScheduled = calendar.getTimeInMillis();
    }

    /**
     * Convierte esta instancia a un Map para guardarlo en Firebase
     */
    @Exclude
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("medicationId", medicationId);
        result.put("hour", hour);
        result.put("minute", minute);
        result.put("daysOfWeek", daysOfWeek);
        result.put("active", active);
        result.put("takingConfirmed", takingConfirmed);
        result.put("lastTaken", lastTaken);
        result.put("nextScheduled", nextScheduled);
        result.put("dispensed", dispensed);
        result.put("detectedBySensor", detectedBySensor);
        result.put("dispensedAt", dispensedAt);
        result.put("detectedAt", detectedAt);
        result.put("use24HourFormat", use24HourFormat);
        
        return result;
    }

    /**
     * Obtiene una representación de la hora en formato legible (HH:MM)
     */
    @Exclude
    public String getFormattedTime() {
        if (use24HourFormat) {
            return String.format("%02d:%02d", hour, minute);
        } else {
            int displayHour = hour % 12;
            if (displayHour == 0) displayHour = 12;
            String amPm = hour >= 12 ? "PM" : "AM";
            return String.format("%d:%02d %s", displayHour, minute, amPm);
        }
    }

    /**
     * Obtiene una representación de los días de la semana como texto
     */
    @Exclude
    public String getFormattedDays() {
        if (daysOfWeek == null) {
            return "Sin días";
        }
        
        StringBuilder sb = new StringBuilder();
        String[] dayNames = {"L", "M", "X", "J", "V", "S", "D"};
        boolean allDays = true;
        boolean noDays = true;
        
        for (int i = 0; i < daysOfWeek.size(); i++) {
            if (daysOfWeek.get(i)) {
                noDays = false;
                sb.append(dayNames[i]).append(" ");
            } else {
                allDays = false;
            }
        }
        
        if (noDays) {
            return "Sin días";
        } else if (allDays) {
            return "Todos los días";
        } else {
            return sb.toString().trim();
        }
    }
}