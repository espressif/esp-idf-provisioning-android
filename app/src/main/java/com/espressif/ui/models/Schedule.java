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
    private boolean intervalMode = false;
    private int intervalHours = 0;
    private int treatmentDays = 0;
    private long treatmentEndDate = 0;
    private String status = "scheduled"; // Valor por defecto: scheduled, dispensed, taken, missed
    private long statusUpdatedAt;
    private boolean missed;  // Añadir esta variable

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

    public boolean isIntervalMode() {
        return intervalMode;
    }

    public void setIntervalMode(boolean intervalMode) {
        this.intervalMode = intervalMode;
    }

    public int getIntervalHours() {
        return intervalHours;
    }

    public void setIntervalHours(int intervalHours) {
        this.intervalHours = intervalHours;
    }

    public int getTreatmentDays() {
        return treatmentDays;
    }

    public void setTreatmentDays(int treatmentDays) {
        this.treatmentDays = treatmentDays;
    }

    public long getTreatmentEndDate() {
        return treatmentEndDate;
    }

    public void setTreatmentEndDate(long treatmentEndDate) {
        this.treatmentEndDate = treatmentEndDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public void setStatusUpdatedAt(long statusUpdatedAt) {
        this.statusUpdatedAt = statusUpdatedAt;
    }

    public void setMissed(boolean missed) {
        this.missed = missed;
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
        long currentTime = calendar.getTimeInMillis();
        
        // Si estamos en modo de intervalos, calcular basado en intervalos horarios
        if (intervalMode && intervalHours > 0) {
            // Definir constantes para evitar cálculos repetidos
            final long HOUR_IN_MILLIS = 60 * 60 * 1000;
            
            // Si no hay hora de inicio previa, usar la hora configurada hoy
            if (lastTaken <= 0) {
                // Configurar el calendario para la hora configurada hoy
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
                calendar.set(java.util.Calendar.MINUTE, minute);
                calendar.set(java.util.Calendar.SECOND, 0);
                calendar.set(java.util.Calendar.MILLISECOND, 0);
                
                // Si la hora configurada ya pasó hoy, calcular la próxima dosis según el intervalo
                if (calendar.getTimeInMillis() < currentTime) {
                    // Calcular cuántas horas han pasado desde la hora configurada
                    long timeSinceConfigured = currentTime - calendar.getTimeInMillis();
                    long intervalMillis = intervalHours * HOUR_IN_MILLIS;
                    
                    // Calcular cuántos intervalos completos han pasado
                    long intervalsPassed = (timeSinceConfigured / intervalMillis) + 1;
                    
                    // Calcular la próxima dosis sumando los intervalos correctos
                    calendar.setTimeInMillis(calendar.getTimeInMillis() + (intervalsPassed * intervalMillis));
                }
            } else {
                // Ya hay una dosis tomada anteriormente, calcular la próxima basada en esa
                calendar.setTimeInMillis(lastTaken);
                calendar.add(java.util.Calendar.HOUR_OF_DAY, intervalHours);
                
                // Si la próxima dosis ya pasó, recalcular desde el momento actual
                if (calendar.getTimeInMillis() < currentTime) {
                    long timeSinceLastDose = currentTime - lastTaken;
                    long intervalMillis = intervalHours * HOUR_IN_MILLIS;
                    
                    // Calcular cuántos intervalos completos han pasado desde la última dosis
                    long intervalsPassed = (timeSinceLastDose / intervalMillis) + 1;
                    
                    // Calcular la próxima dosis sumando los intervalos correctos
                    calendar.setTimeInMillis(lastTaken + (intervalsPassed * intervalMillis));
                }
            }
            
            // Verificar que no exceda la fecha de fin del tratamiento
            if (treatmentEndDate > 0 && calendar.getTimeInMillis() > treatmentEndDate) {
                // Si excede, usar el fin del tratamiento
                calendar.setTimeInMillis(treatmentEndDate);
            }
        } else {
            // Código existente para el modo basado en días de la semana
            int currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
            int currentMinute = calendar.get(java.util.Calendar.MINUTE);
            int currentDayOfWeek = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7; // Convertir a índice 0-6 (Lun-Dom)

            boolean scheduleForToday = daysOfWeek != null && 
                                     daysOfWeek.size() > currentDayOfWeek &&
                                     daysOfWeek.get(currentDayOfWeek) && 
                                     (hour > currentHour || (hour == currentHour && minute > currentMinute));

            if (scheduleForToday) {
                // Programar para hoy
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
                calendar.set(java.util.Calendar.MINUTE, minute);
                calendar.set(java.util.Calendar.SECOND, 0);
                calendar.set(java.util.Calendar.MILLISECOND, 0);
            } else {
                // Buscar el próximo día disponible
                if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
                    int daysToAdd = 1;
                    int nextDay = (currentDayOfWeek + daysToAdd) % 7;
                    
                    boolean foundDay = false;
                    int loopCount = 0;
                    
                    while (!foundDay && loopCount < 7) {
                        if (nextDay < daysOfWeek.size() && daysOfWeek.get(nextDay)) {
                            foundDay = true;
                        } else {
                            daysToAdd++;
                            nextDay = (currentDayOfWeek + daysToAdd) % 7;
                        }
                        loopCount++;
                    }
                    
                    if (foundDay) {
                        calendar.add(java.util.Calendar.DAY_OF_MONTH, daysToAdd);
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
                        calendar.set(java.util.Calendar.MINUTE, minute);
                        calendar.set(java.util.Calendar.SECOND, 0);
                        calendar.set(java.util.Calendar.MILLISECOND, 0);
                    } else {
                        // No se encontró ningún día activo, usar el día siguiente como fallback
                        calendar.add(java.util.Calendar.DAY_OF_MONTH, 1);
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
                        calendar.set(java.util.Calendar.MINUTE, minute);
                        calendar.set(java.util.Calendar.SECOND, 0);
                        calendar.set(java.util.Calendar.MILLISECOND, 0);
                    }
                }
            }
        }
        
        // Guardar el próximo tiempo programado
        this.nextScheduled = calendar.getTimeInMillis();
    }

    /**
     * Verifica si el medicamento fue dispensado recientemente (en los últimos 5 minutos)
     * @return true si el medicamento fue dispensado recientemente
     */
    @Exclude
    public boolean wasRecentlyDispensed() {
        // Si está dispensado y la dispensación fue hace menos de 5 minutos
        return dispensed && 
               dispensedAt > 0 && 
               System.currentTimeMillis() - dispensedAt < 300000; // 5 minutos en milisegundos
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
        
        // Añadir propiedades de intervalo
        result.put("intervalMode", intervalMode);
        result.put("intervalHours", intervalHours);
        result.put("treatmentDays", treatmentDays);
        result.put("treatmentEndDate", treatmentEndDate);
        
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

    @Exclude
    public boolean isScheduled() {
        return "scheduled".equals(status);
    }

    @Exclude
    public boolean isDispensed() {
        return "dispensed".equals(status) || dispensed;
    }

    @Exclude
    public boolean isTaken() {
        return "taken".equals(status) || takingConfirmed;
    }

    @Exclude
    public boolean isMissed() {
        return "missed".equals(status) || missed;
    }
}