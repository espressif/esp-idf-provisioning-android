package com.espressif.ui.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.espressif.data.repository.MedicationRepository;
import com.espressif.ui.models.Medication;
import com.espressif.ui.models.Schedule;

public class MedicationAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "MedicationAlarmReceiver";

    public static final String ACTION_REMINDER = "com.espressif.ACTION_MEDICATION_REMINDER";
    public static final String ACTION_MISSED_CHECK = "com.espressif.ACTION_MISSED_MEDICATION_CHECK";
    
    public static final String EXTRA_MEDICATION_ID = "medication_id";
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";
    public static final String EXTRA_PATIENT_ID = "patient_id";
    public static final String EXTRA_MEDICATION_NAME = "MEDICATION_NAME";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "⚡️ BroadcastReceiver activado con acción: " + intent.getAction());
        
        // IMPORTANTE: Crear canales de notificación incluso en el receptor
        NotificationHelper.createNotificationChannels(context);
        
        // Adquirir un wake lock para asegurar que el proceso completa incluso si la pantalla está apagada
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "MediWatch:MedicationAlarmWakeLock");
        wakeLock.acquire(60000); // 60 segundos máximo
        
        try {
            String action = intent.getAction();
            
            Log.d(TAG, "Alarma recibida: " + action);
            
            if (action == null) {
                Log.e(TAG, "Received intent without action");
                return;
            }
            
            String medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID);
            String scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID);
            String patientId = intent.getStringExtra(EXTRA_PATIENT_ID);
            
            Log.d(TAG, "Datos de alarma: medicationId=" + medicationId + 
                        ", scheduleId=" + scheduleId + ", patientId=" + patientId);
            
            if (medicationId == null || scheduleId == null || patientId == null) {
                Log.e(TAG, "Missing required extras in intent");
                return;
            }
            
            switch (action) {
                case ACTION_REMINDER:
                    boolean isAdvanceReminder = intent.getBooleanExtra("IS_ADVANCE_REMINDER", false);
                    handleUpcomingMedicationReminder(context, patientId, medicationId, scheduleId, isAdvanceReminder);
                    break;
                    
                case ACTION_MISSED_CHECK:
                    checkMissedMedication(context, patientId, medicationId, scheduleId);
                    break;
                    
                case "android.intent.action.BOOT_COMPLETED":
                    Log.d(TAG, "🔄 Dispositivo reiniciado, reprogramando recordatorios");
                    // Aquí añadirías código para reprogramar todas las alarmas
                    break;
                    
                default:
                    Log.e(TAG, "Unknown action: " + action);
                    break;
            }
        } finally {
            // Siempre liberar el wake lock
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }
    
    /**
     * Maneja el recordatorio de medicación próxima
     */
    private void handleUpcomingMedicationReminder(Context context, String patientId, 
                                                String medicationId, String scheduleId,
                                                boolean isAdvanceReminder) {
        Log.d(TAG, "Procesando recordatorio para medicación: " + medicationId);
        
        MedicationRepository repository = MedicationRepository.getInstance();
        repository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
            public void onSuccess(Medication medication) {
                if (medication == null) {
                    Log.e(TAG, "No se encontró la medicación: " + medicationId);
                    return;
                }
                
                Schedule schedule = null;
                for (Schedule s : medication.getSchedules().values()) {
                    if (s.getId().equals(scheduleId)) {
                        schedule = s;
                        break;
                    }
                }
                
                if (schedule == null) {
                    Log.e(TAG, "No se encontró el horario: " + scheduleId);
                    return;
                }
                
                // Verificar si el horario sigue activo
                if (!schedule.isActive()) {
                    Log.d(TAG, "El horario ya no está activo, no se muestra notificación");
                    return;
                }
                
                NotificationHelper notificationHelper = new NotificationHelper(context);

                // Verificar si este es un recordatorio anticipado o uno a la hora exacta
                if (isAdvanceReminder) {
                    // Mostrar recordatorio anticipado (30 minutos antes)
                    notificationHelper.showUpcomingMedicationReminder(medication, schedule);
                } else {
                    // Mostrar recordatorio a la hora exacta
                    NotificationHelper.showMedicationReminder(
                        context,
                        "Es hora de tomar su medicación",
                        "Debe tomar " + medication.getPillsPerDose() + " unidad(es) de " + medication.getName() + " ahora.",
                        medication.getId().hashCode()
                    );
                }
                
                // Programar la verificación de medicamento no tomado para 30 minutos después
                NotificationScheduler scheduler = new NotificationScheduler(context);
                scheduler.scheduleMissedMedicationCheck(patientId, medication, schedule);
            }

            public void onError(String errorMessage) {
                Log.e(TAG, "Error al obtener medicación: " + errorMessage);
            }
        });
    }
    
    /**
     * Verifica si una medicación ha sido tomada o no
     */
    private void checkMissedMedication(Context context, String patientId, 
                                      String medicationId, String scheduleId) {
        Log.d(TAG, "Verificando medicación no tomada: " + medicationId);
        
        // Verificar en la base de datos si la medicación ha sido dispensada/tomada
        MedicationRepository repository = MedicationRepository.getInstance();
        repository.checkMedicationTaken(patientId, medicationId, scheduleId, 
                new MedicationRepository.MedicationTakenCallback() {
            public void onResult(boolean isTaken, Medication medication, Schedule schedule) {
                if (!isTaken && medication != null && schedule != null) {
                    // Si no se tomó la medicación, mostrar alerta
                    NotificationHelper notificationHelper = new NotificationHelper(context);
                    notificationHelper.showMissedMedicationAlert(medication, schedule);
                    
                    // Registrar evento de medicación perdida en la base de datos
                    repository.registerMissedMedication(patientId, medicationId, scheduleId, 
                            new MedicationRepository.DatabaseCallback() {
                        public void onSuccess() {
                            Log.d(TAG, "Medicación perdida registrada");
                        }
                        
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Error al registrar medicación perdida: " + errorMessage);
                        }
                    });
                }
            }
            
            public void onError(String errorMessage) {
                Log.e(TAG, "Error al verificar medicación: " + errorMessage);
            }
        });
    }
}