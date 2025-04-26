package com.espressif.ui.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import java.util.concurrent.CompletableFuture;

import com.espressif.data.repository.MedicationRepository;
import com.espressif.ui.models.Medication;
import com.espressif.ui.models.Schedule;

public class MedicationAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "MedicationAlarmReceiver";
    
    // Definir tiempos constantes
    private static final long WAKELOCK_TIMEOUT_MS = 30000; // 30 segundos

    // Unificar nombre de acción para evitar confusión
    public static final String ACTION_MEDICATION_REMINDER = "com.espressif.ACTION_MEDICATION_REMINDER";
    // Para compatibilidad con código existente
    public static final String ACTION_REMINDER = ACTION_MEDICATION_REMINDER;
    
    public static final String ACTION_MISSED_CHECK = "com.espressif.ACTION_MISSED_MEDICATION_CHECK";
    
    public static final String EXTRA_MEDICATION_ID = "medication_id";
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";
    public static final String EXTRA_PATIENT_ID = "patient_id";
    public static final String EXTRA_MEDICATION_NAME = "MEDICATION_NAME";
    
    // Para facilitar pruebas unitarias
    private static MedicationRepository medicationRepositoryInstance;
    
    /**
     * Permite inyectar un repositorio personalizado (útil para pruebas)
     */
    public static void setMedicationRepository(MedicationRepository repository) {
        medicationRepositoryInstance = repository;
    }
    
    /**
     * Obtiene el repositorio de medicamentos (real o mock)
     */
    private MedicationRepository getMedicationRepository() {
        if (medicationRepositoryInstance != null) {
            return medicationRepositoryInstance;
        }
        return MedicationRepository.getInstance();
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "⚡️ BroadcastReceiver activado con acción: " + intent.getAction());
        
        // IMPORTANTE: Crear canales de notificación incluso en el receptor
        NotificationHelper.createNotificationChannels(context);
        
        // Adquirir un wake lock para asegurar que el proceso completa incluso si la pantalla está apagada
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "MediWatch:MedicationAlarmWakeLock");
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
        
        try {
            String action = intent.getAction();
            Log.d(TAG, "Alarma recibida: " + action);
            
            if (action == null) {
                Log.e(TAG, "Received intent without action");
                return;
            }
            
            // Procesar alarma de medicación
            if (action.equals(ACTION_MEDICATION_REMINDER)) {
                handleMedicationReminder(context, intent);
                return;
            }
            
            // Procesar otras acciones
            handleOtherActions(context, intent, action);
            
        } catch (Exception e) {
            handleCriticalError(context, "Error procesando alarma: " + e.getMessage(), e);
        } finally {
            // Siempre liberar el wake lock
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "WakeLock liberado");
            }
        }
    }
    
    /**
     * Maneja la acción de recordatorio de medicación
     */
    private void handleMedicationReminder(Context context, Intent intent) {
        String medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID);
        String scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID);
        String medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME);
        String patientId = intent.getStringExtra(EXTRA_PATIENT_ID);
        
        // Validar ID de paciente
        if ("current_user_id".equals(patientId) || patientId == null || patientId.isEmpty()) {
            Log.e(TAG, "ID de paciente inválido o no encontrado en la alarma: " + patientId);
            return;
        }
        
        // Mostrar notificación de recordatorio
        showMedicationReminder(context, medicationId, scheduleId, medicationName);
        
        // Si tu sistema está configurado para dispensación automática,
        // actualizar el conteo aquí
        if (medicationId != null && scheduleId != null) {
            // Verificar si la dispensación automática está habilitada para este medicamento
            checkAndHandleAutomaticDispensation(context, patientId, medicationId, scheduleId);
            
            // Programar verificación de medicación perdida
            scheduleMissedCheck(context, patientId, medicationId, scheduleId);
        }
    }
    
    /**
     * Verifica si la dispensación automática está habilitada y la maneja
     */
    private void checkAndHandleAutomaticDispensation(Context context, String patientId, 
                                                  String medicationId, String scheduleId) {
        getMedicationRepository().getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
            @Override
            public void onSuccess(Medication medication) {
                if (medication != null) {
                    Schedule matchingSchedule = findMatchingSchedule(medication, scheduleId);
                    
                    // Aquí verificaríamos si la dispensación automática está habilitada
                    // Por ahora, asumiremos que siempre lo está
                    if (matchingSchedule != null) {
                        handleScheduledDispensation(context, patientId, medicationId, scheduleId);
                    }
                }
            }
            
            @Override
            public void onError(String message) {
                handleError(context, "Error al verificar dispensación automática: " + message);
            }
        });
    }
    
    /**
     * Muestra la notificación de recordatorio de medicación
     */
    private void showMedicationReminder(Context context, String medicationId, String scheduleId, String medicationName) {
        NotificationHelper notificationHelper = new NotificationHelper(context);
        
        // Crear un título y mensaje adecuados para la notificación
        String title = "Es hora de tomar su medicamento";
        String message = medicationName != null ? 
                        "Por favor tome ahora: " + medicationName : 
                        "Es momento de tomar su medicación programada";
        
        // Generar ID para la notificación
        int notificationId;
        if (medicationName != null) {
            notificationId = medicationName.hashCode();
        } else {
            notificationId = (medicationId + scheduleId).hashCode();
        }
        
        // Ahora llamar al método con los parámetros correctos
        notificationHelper.showMedicationReminder(context, title, message, notificationId);
    }
    
    /**
     * Programa la verificación de medicación perdida
     */
    private void scheduleMissedCheck(Context context, String patientId, String medicationId, String scheduleId) {
        // Validar ID
        if (!isValidPatientId(patientId)) {
            Log.e(TAG, "Intento de programar verificación con ID de paciente inválido: " + patientId);
            return;
        }
        
        getMedicationRepository().getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
            @Override
            public void onSuccess(Medication medication) {
                Schedule matchingSchedule = findMatchingSchedule(medication, scheduleId);
                
                if (matchingSchedule != null) {
                    NotificationScheduler scheduler = new NotificationScheduler(context);
                    scheduler.scheduleMissedMedicationCheck(patientId, medication, matchingSchedule);
                }
            }
            
            @Override
            public void onError(String message) {
                handleError(context, "Error al obtener medicación para verificación: " + message);
            }
        });
    }
    
    /**
     * Encuentra el horario correspondiente al ID proporcionado
     */
    private Schedule findMatchingSchedule(Medication medication, String scheduleId) {
        if (medication == null || medication.getScheduleList() == null) {
            return null;
        }
        
        for (Schedule schedule : medication.getScheduleList()) {
            if (scheduleId.equals(schedule.getId())) {
                return schedule;
            }
        }
        return null;
    }
    
    /**
     * Maneja otras acciones del receptor
     */
    private void handleOtherActions(Context context, Intent intent, String action) {
        String medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID);
        String scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID);
        String patientId = intent.getStringExtra(EXTRA_PATIENT_ID);
        
        if (medicationId == null || scheduleId == null) {
            Log.e(TAG, "Falta medicationId o scheduleId en el intent");
            return;
        }
        
        // Validar ID de paciente
        if ("current_user_id".equals(patientId) || patientId == null || patientId.isEmpty()) {
            Log.e(TAG, "ID de paciente inválido o no encontrado en la alarma: " + patientId);
            return;
        }
        
        switch (action) {
            case ACTION_MISSED_CHECK:
                checkMissedMedication(context, patientId, medicationId, scheduleId);
                break;
                
            case "android.intent.action.BOOT_COMPLETED":
                Log.d(TAG, "🔄 Dispositivo reiniciado, reprogramando recordatorios");
                // Implementación futura: reprogramar todas las alarmas
                break;
                
            default:
                Log.e(TAG, "Unknown action: " + action);
                break;
        }
    }
    
    /**
     * Verifica si una medicación ha sido tomada o no
     */
    private void checkMissedMedication(Context context, String patientId, 
                                      String medicationId, String scheduleId) {
        Log.d(TAG, "Verificando medicación no tomada: " + medicationId);
        
        getMedicationRepository().checkMedicationTaken(patientId, medicationId, scheduleId, 
                new MedicationRepository.MedicationTakenCallback() {
            @Override
            public void onResult(boolean isTaken, Medication medication, Schedule schedule) {
                if (!isTaken && medication != null && schedule != null) {
                    showMissedMedicationAlert(context, medication, schedule);
                    registerMissedMedication(context, patientId, medicationId, scheduleId);
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                handleError(context, "Error al verificar medicación: " + errorMessage);
            }
        });
    }
    
    /**
     * Muestra una alerta de medicación perdida
     */
    private void showMissedMedicationAlert(Context context, Medication medication, Schedule schedule) {
        NotificationHelper notificationHelper = new NotificationHelper(context);
        notificationHelper.showMissedMedicationAlert(medication, schedule);
    }
    
    /**
     * Registra un evento de medicación perdida en la base de datos
     */
    private void registerMissedMedication(Context context, String patientId, 
                                         String medicationId, String scheduleId) {
        getMedicationRepository().registerMissedMedication(patientId, medicationId, scheduleId, 
                new MedicationRepository.DatabaseCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Medicación perdida registrada");
            }
            
            @Override
            public void onError(String errorMessage) {
                handleError(context, "Error al registrar medicación perdida: " + errorMessage);
            }
        });
    }
    
    /**
     * Maneja la dispensación automática cuando llega la hora programada
     */
    private void handleScheduledDispensation(Context context, String patientId, String medicationId, String scheduleId) {
        getMedicationRepository().getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
            @Override
            public void onSuccess(Medication medication) {
                if (medication != null) {
                    // Actualizar el conteo de medicamentos
                    boolean dispensed = medication.dispenseDose();
                    
                    if (dispensed) {
                        // Si se dispensó exitosamente, actualizar en la base de datos
                        getMedicationRepository().updateMedication(medication, new MedicationRepository.DatabaseCallback() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Conteo de medicamento actualizado tras dispensación automática: " + medication.getName());
                                
                                // También marcar el horario como dispensado
                                getMedicationRepository().markAsDispensed(patientId, medicationId, scheduleId, 
                                    new MedicationRepository.DatabaseCallback() {
                                        @Override
                                        public void onSuccess() {
                                            Log.d(TAG, "Horario marcado como dispensado automáticamente");
                                        }
                                        
                                        @Override
                                        public void onError(String errorMessage) {
                                            handleError(context, "Error al marcar dispensación: " + errorMessage);
                                        }
                                    });
                            }
                            
                            @Override
                            public void onError(String errorMessage) {
                                handleError(context, "Error al actualizar conteo de medicamento: " + errorMessage);
                            }
                        });
                    } else {
                        // Si no se pudo dispensar (por ejemplo, no hay suficiente medicamento)
                        Log.w(TAG, "No se pudo dispensar el medicamento automáticamente: " + medication.getName());
                        // Enviar una notificación diferente indicando que no hay suficiente medicamento
                        showLowMedicationAlert(context, medication);
                    }
                }
            }
            
            @Override
            public void onError(String message) {
                handleError(context, "Error al obtener medicación para dispensación: " + message);
            }
        });
    }
    
    /**
     * Muestra una alerta cuando el medicamento está por agotarse
     */
    private void showLowMedicationAlert(Context context, Medication medication) {
        NotificationHelper notificationHelper = new NotificationHelper(context);
        String title = "Medicamento insuficiente";
        String message = "No hay suficiente " + medication.getName() + " para dispensar la dosis completa. Por favor recargue pronto.";
        int notificationId = ("low_" + medication.getId()).hashCode();
        notificationHelper.showMedicationReminder(context, title, message, notificationId);
    }
    
    /**
     * Maneja errores no críticos
     */
    private void handleError(Context context, String errorMessage) {
        Log.e(TAG, errorMessage);
        // Aquí podrías implementar lógica adicional para errores específicos
    }
    
    /**
     * Maneja errores críticos con notificación al usuario
     */
    private void handleCriticalError(Context context, String errorMessage, Exception e) {
        // Registrar error detallado
        Log.e(TAG, errorMessage, e);
        
        try {
            // Para errores críticos, notificar al usuario
            NotificationHelper notificationHelper = new NotificationHelper(context);
            
            // Enviar evento de telemetría para monitoreo
            // Analytics.logError("medication_alarm_error", errorMessage);
        } catch (Exception ex) {
            // En caso de error al mostrar notificación, al menos registrar
            Log.e(TAG, "Error al mostrar notificación de error", ex);
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
}