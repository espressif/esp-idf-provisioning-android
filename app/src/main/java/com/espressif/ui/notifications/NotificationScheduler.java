package com.espressif.ui.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.espressif.ui.models.Medication;
import com.espressif.ui.models.Schedule;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class NotificationScheduler {
    
    private static final String TAG = "NotificationScheduler";
    private static final int REMINDER_TIME_MINUTES = 30; // 30 minutos antes
    private static final int MISSED_CHECK_GRACE_PERIOD_MINUTES = 30; // 30 minutos después
    
    private final Context context;
    private final AlarmManager alarmManager;
    
    public NotificationScheduler(Context context) {
        this.context = context;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        
        // Verificar si la app está en la lista de optimización de batería
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.getPackageName());
            Log.d(TAG, "¿La app ignora optimizaciones de batería? " + isIgnoringBatteryOptimizations);
            if (!isIgnoringBatteryOptimizations) {
                Log.w(TAG, "ADVERTENCIA: La app está sujeta a optimizaciones de batería, las alarmas pueden no funcionar correctamente");
            }
        }
        
        // Verificar permisos para Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                Log.d(TAG, "La app puede programar alarmas exactas");
            } else {
                Log.e(TAG, "ERROR: La app NO puede programar alarmas exactas");
            }
        }
    }
    
    /**
     * Valida un ID de paciente
     * @param patientId ID a validar
     * @return true si el ID es válido, false si es nulo, vacío o "current_user_id"
     */
    private boolean isValidPatientId(String patientId) {
        return patientId != null && !patientId.isEmpty() && !"current_user_id".equals(patientId);
    }

    /**
     * Programa un recordatorio para un medicamento
     */
    public boolean scheduleReminder(String patientId, Medication medication, Schedule schedule) {
        // Validar datos necesarios
        if (medication == null || schedule == null) {
            Log.e(TAG, "No se puede programar recordatorio: datos nulos");
            return false;
        }
        
        // Validar ID de paciente
        if (!isValidPatientId(patientId)) {
            Log.e(TAG, "No se puede programar recordatorio: ID de paciente inválido: " + patientId);
            return false;
        }

        try {
            // Obtener tiempo programado
            long scheduledTime = schedule.getNextScheduled();
            
            // Si la hora ya pasó, calcular la próxima aparición
            if (scheduledTime < System.currentTimeMillis()) {
                Log.d(TAG, "La hora programada ya pasó, calculando próxima aparición");
                
                // Calcular la próxima fecha programada manualmente
                Calendar calendar = Calendar.getInstance();
                ArrayList<Boolean> days = schedule.getDaysOfWeek();
                
                if (schedule.isIntervalMode()) {
                    // Si es modo intervalo, calcular próxima hora
                    calendar.set(Calendar.HOUR_OF_DAY, schedule.getHour());
                    calendar.set(Calendar.MINUTE, schedule.getMinute());
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    
                    // Avanzar por intervalos hasta encontrar un tiempo futuro
                    while (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                        calendar.add(Calendar.HOUR_OF_DAY, schedule.getIntervalHours());
                    }
                } else {
                    // Si es modo días de la semana
                    int currentDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                    
                    // Buscar el próximo día activo
                    int daysToAdd = 1;
                    int nextDay = (currentDayOfWeek + daysToAdd) % 7;
                    
                    boolean foundDay = false;
                    while (!foundDay && daysToAdd <= 7) {
                        if (days.get(nextDay)) {
                            foundDay = true;
                        } else {
                            daysToAdd++;
                            nextDay = (currentDayOfWeek + daysToAdd) % 7;
                        }
                    }
                    
                    // Configurar la fecha para el día encontrado
                    if (foundDay) {
                        calendar.add(Calendar.DAY_OF_MONTH, daysToAdd);
                        calendar.set(Calendar.HOUR_OF_DAY, schedule.getHour());
                        calendar.set(Calendar.MINUTE, schedule.getMinute());
                        calendar.set(Calendar.SECOND, 0);
                        calendar.set(Calendar.MILLISECOND, 0);
                    } else {
                        // Si no se encuentra un día activo, avanzar un año (error fallback)
                        calendar.add(Calendar.YEAR, 1);
                    }
                }
                
                // Actualizar el Schedule con la nueva fecha
                schedule.setNextScheduled(calendar.getTimeInMillis());
                scheduledTime = schedule.getNextScheduled();
            }

            if (scheduledTime > System.currentTimeMillis()) {
                Log.d(TAG, "Programando notificación para " + medication.getName() + " a las " + 
                      new java.util.Date(scheduledTime).toString());
                
                // Crear intent para la notificación
                Intent intent = new Intent(context, MedicationAlarmReceiver.class);
                intent.setAction(MedicationAlarmReceiver.ACTION_MEDICATION_REMINDER);
                intent.putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_ID, medication.getId());
                intent.putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME, medication.getName());
                intent.putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULE_ID, schedule.getId());
                intent.putExtra(MedicationAlarmReceiver.EXTRA_PATIENT_ID, patientId);
                intent.putExtra("IS_ADVANCE_REMINDER", false); // Esta es una notificación a la hora exacta
                
                // Crear un ID único para el PendingIntent
                int requestCode = (medication.getId() + schedule.getId()).hashCode();
                
                // Crear PendingIntent
                PendingIntent pendingIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pendingIntent = PendingIntent.getBroadcast(
                            context,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    pendingIntent = PendingIntent.getBroadcast(
                            context,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }
                
                // Obtener AlarmManager
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                
                if (alarmManager != null) {
                    // Programar la alarma con la API apropiada
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                scheduledTime,
                                pendingIntent);
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        alarmManager.setExact(
                                AlarmManager.RTC_WAKEUP,
                                scheduledTime,
                                pendingIntent);
                    } else {
                        alarmManager.set(
                                AlarmManager.RTC_WAKEUP,
                                scheduledTime,
                                pendingIntent);
                    }
                    
                    // Después de programar la notificación principal, programar la anticipada
                    scheduleAdvanceReminder(patientId, medication, schedule);
                    
                    return true;
                }
            } else {
                Log.e(TAG, "No se pudo programar: la hora calculada está en el pasado");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al programar recordatorio: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Programa un recordatorio anticipado 30 minutos antes
     */
    public boolean scheduleAdvanceReminder(String patientId, Medication medication, Schedule schedule) {
        // Validar datos necesarios
        if (medication == null || schedule == null) {
            return false;
        }
        
        // Validar ID de paciente
        if (!isValidPatientId(patientId)) {
            Log.e(TAG, "No se puede programar recordatorio anticipado: ID de paciente inválido: " + patientId);
            return false;
        }

        try {
            // Calcular tiempo 30 minutos antes
            long advanceTime = schedule.getNextScheduled() - (REMINDER_TIME_MINUTES * 60 * 1000);
            
            // Si ya pasó el tiempo anticipado, no programar
            if (advanceTime <= System.currentTimeMillis()) {
                return false;
            }

            Log.d(TAG, "Programando notificación anticipada para " + medication.getName() + " 30 minutos antes");
            
            // Crear intent para la notificación anticipada
            Intent intent = new Intent(context, MedicationAlarmReceiver.class);
            intent.setAction(MedicationAlarmReceiver.ACTION_MEDICATION_REMINDER);
            intent.putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_ID, medication.getId());
            intent.putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME, medication.getName());
            intent.putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULE_ID, schedule.getId());
            intent.putExtra(MedicationAlarmReceiver.EXTRA_PATIENT_ID, patientId);
            intent.putExtra("IS_ADVANCE_REMINDER", true);
            
            // Crear ID único diferente al de la hora exacta
            int requestCode = ("advance_" + medication.getId() + schedule.getId()).hashCode();
            
            // Crear PendingIntent
            PendingIntent pendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
            }
            
            // Programar la alarma
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        advanceTime,
                        pendingIntent);
                Log.d(TAG, "Alarma anticipada programada para: " + new java.util.Date(advanceTime));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        advanceTime,
                        pendingIntent);
            } else {
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        advanceTime,
                        pendingIntent);
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error al programar recordatorio anticipado: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Programa una verificación automática para detectar medicación no tomada
     */
    public void scheduleMissedMedicationCheck(String patientId, Medication medication, Schedule schedule) {
        // Validar datos necesarios
        if (medication == null || schedule == null || !schedule.isActive()) {
            return;
        }
        
        // Validar ID de paciente
        if (!isValidPatientId(patientId)) {
            Log.e(TAG, "No se puede programar verificación de medicación perdida: ID de paciente inválido: " + patientId);
            return;
        }
        
        Calendar calendar = Calendar.getInstance();
        
        // Configurar para 30 minutos después de la hora programada
        calendar.set(Calendar.HOUR_OF_DAY, schedule.getHour());
        calendar.set(Calendar.MINUTE, schedule.getMinute());
        calendar.add(Calendar.MINUTE, MISSED_CHECK_GRACE_PERIOD_MINUTES);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        
        // Si la hora ya pasó, no programamos verificación
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            return;
        }
        
        Log.d(TAG, String.format("Programando verificación para medicación %s a las %02d:%02d",
              medication.getName(), calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)));
        
        scheduleAlarm(patientId, medication, schedule, calendar.getTimeInMillis(),
                MedicationAlarmReceiver.ACTION_MISSED_CHECK);
    }
    
    /**
     * Programa una alarma usando AlarmManager
     */
    private void scheduleAlarm(String patientId, Medication medication, Schedule schedule,
                              long triggerAtMillis, String action) {
        // Validar ID de paciente
        if (!isValidPatientId(patientId)) {
            Log.e(TAG, "No se puede programar alarma: ID de paciente inválido: " + patientId);
            return;
        }
        
        Intent intent = new Intent(context, MedicationAlarmReceiver.class);
        intent.setAction(action);
        intent.putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_ID, medication.getId());
        intent.putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULE_ID, schedule.getId());
        intent.putExtra(MedicationAlarmReceiver.EXTRA_PATIENT_ID, patientId);
        
        // Generar un código de solicitud único basado en los IDs
        int requestCode = generateRequestCode(medication.getId(), schedule.getId(), action);
        
        // Usar FLAG_IMMUTABLE para Android 12+
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, flags);
        
        // Usar setExactAndAllowWhileIdle para asegurar que la alarma se dispare incluso en modo Doze
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            Log.d(TAG, "Alarma programada exactamente para: " + new java.util.Date(triggerAtMillis).toString());
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            Log.d(TAG, "Alarma programada exactamente (API < 23) para: " + new java.util.Date(triggerAtMillis).toString());
        }
    }
    
    /**
     * Reprograma todos los recordatorios para una lista de medicamentos
     */
    public void rescheduleMedicationReminders(String patientId, List<Medication> medications) {
        // Validar datos necesarios
        if (medications == null || medications.isEmpty()) {
            return;
        }
        
        // Validar ID de paciente
        if (!isValidPatientId(patientId)) {
            Log.e(TAG, "No se puede reprogramar recordatorios: ID de paciente inválido: " + patientId);
            return;
        }
        
        Log.d(TAG, "Reprogramando recordatorios para " + medications.size() + " medicamentos de paciente ID: " + patientId);
        
        for (Medication medication : medications) {
            if (medication.getScheduleList() != null) {
                for (Schedule schedule : medication.getScheduleList()) {
                    if (schedule.isActive()) {
                        scheduleReminder(patientId, medication, schedule);
                    } else {
                        cancelReminders(medication.getId(), schedule.getId());
                    }
                }
            }
        }
    }
    
    /**
     * Cancela recordatorios para un medicamento específico
     */
    public void cancelReminders(String medicationId, String scheduleId) {
        // Cancelar recordatorio
        Intent reminderIntent = new Intent(context, MedicationAlarmReceiver.class);
        reminderIntent.setAction(MedicationAlarmReceiver.ACTION_MEDICATION_REMINDER);
        
        PendingIntent reminderPendingIntent = PendingIntent.getBroadcast(
                context, 
                generateRequestCode(medicationId, scheduleId, MedicationAlarmReceiver.ACTION_MEDICATION_REMINDER),
                reminderIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        alarmManager.cancel(reminderPendingIntent);
        
        // Cancelar verificación de medicación no tomada
        Intent missedIntent = new Intent(context, MedicationAlarmReceiver.class);
        missedIntent.setAction(MedicationAlarmReceiver.ACTION_MISSED_CHECK);
        
        PendingIntent missedPendingIntent = PendingIntent.getBroadcast(
                context, 
                generateRequestCode(medicationId, scheduleId, MedicationAlarmReceiver.ACTION_MISSED_CHECK), 
                missedIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        alarmManager.cancel(missedPendingIntent);
        
        Log.d(TAG, "Recordatorios cancelados para medicación: " + medicationId + ", horario: " + scheduleId);
    }
    
    /**
     * Genera un código de solicitud único para PendingIntents
     */
    private int generateRequestCode(String medicationId, String scheduleId, String action) {
        int baseCode = MedicationAlarmReceiver.ACTION_MEDICATION_REMINDER.equals(action) ? 10000 : 20000;
        int medHash = medicationId.hashCode();
        int schedHash = scheduleId.hashCode();
        
        return baseCode + Math.abs((medHash * 31 + schedHash) % 10000);
    }

    /**
     * Programar un recordatorio de medicación para una hora específica
     */
    public static void scheduleMedicationReminder(Context context, String medicationName, long triggerTimeMillis) {
        // Registrar información de diagnóstico
        checkBatteryOptimizations(context);
        checkExactAlarmPermission(context);
        
        // Crear intent para el receptor
        Intent intent = new Intent(context, MedicationAlarmReceiver.class);
        intent.setAction(MedicationAlarmReceiver.ACTION_MEDICATION_REMINDER);
        intent.putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME, medicationName);
        
        // Crear un ID único basado en nombre+tiempo
        int requestCode = (medicationName + triggerTimeMillis).hashCode();
        
        // Crear PendingIntent
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        // Obtener AlarmManager
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        
        // Programar la alarma con la API adecuada según versión Android
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMillis,
                        pendingIntent);
                
                Log.d(TAG, "Alarma programada para " + medicationName + " a las " + triggerTimeMillis + 
                        " (usando setExactAndAllowWhileIdle)");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMillis,
                        pendingIntent);
                
                Log.d(TAG, "Alarma programada para " + medicationName + " a las " + triggerTimeMillis + 
                        " (usando setExact)");
            } else {
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMillis,
                        pendingIntent);
                
                Log.d(TAG, "Alarma programada para " + medicationName + " a las " + triggerTimeMillis + 
                        " (usando set)");
            }
        } else {
            Log.e(TAG, "Error: No se pudo obtener AlarmManager");
        }
    }

    /**
     * Verificar optimizaciones de batería
     */
    private static void checkBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.getPackageName());
            
            Log.d(TAG, "¿La app ignora optimizaciones de batería? " + isIgnoringBatteryOptimizations);
            
            if (!isIgnoringBatteryOptimizations) {
                Log.w(TAG, "ADVERTENCIA: La app está sujeta a optimizaciones de batería, las alarmas pueden no funcionar correctamente");
            }
        }
    }

    /**
     * Verificar permiso de alarmas exactas
     */
    private static void checkExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            boolean canScheduleExactAlarms = alarmManager.canScheduleExactAlarms();
            
            Log.d(TAG, "La app puede programar alarmas exactas: " + canScheduleExactAlarms);
            
            if (!canScheduleExactAlarms) {
                Log.e(TAG, "ERROR: La app no tiene permiso para programar alarmas exactas");
            }
        }
    }
}