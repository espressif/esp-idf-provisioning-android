package com.espressif.ui.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.espressif.mediwatch.R;
import com.espressif.ui.activities.MainActivity;
import com.espressif.ui.models.Medication;
import com.espressif.ui.models.Schedule;

public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    
    // Canales de notificación
    public static final String CHANNEL_ID_REMINDERS = "medication_reminders";
    public static final String CHANNEL_ID_ALERTS = "medication_alerts";
    public static final String CHANNEL_MEDICATION_REMINDERS = "medication_reminders";
    public static final String CHANNEL_MISSED_MEDICATIONS = "missed_medications";
    
    // Tipos de notificación
    public static final int NOTIFICATION_TYPE_UPCOMING = 1;
    public static final int NOTIFICATION_TYPE_MISSED = 2;
    
    // ID base para notificaciones - usamos diferentes rangos para diferentes tipos
    private static final int REMINDER_NOTIFICATION_ID_BASE = 1000;
    private static final int MISSED_NOTIFICATION_ID_BASE = 2000;
    
    private final Context context;
    private final NotificationManager notificationManager;
    
    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels();
    }
    
    /**
     * Crea los canales de notificación (solo necesario en Android 8.0+)
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para recordatorios de medicación (30 minutos antes)
            NotificationChannel reminderChannel = new NotificationChannel(
                    CHANNEL_ID_REMINDERS,
                    "Recordatorios de medicación",
                    NotificationManager.IMPORTANCE_HIGH);
            reminderChannel.setDescription("Recordatorios antes de la hora programada de medicación");
            reminderChannel.enableLights(true);
            reminderChannel.setLightColor(Color.BLUE);
            reminderChannel.enableVibration(true);
            reminderChannel.setShowBadge(true);
            
            // Canal para alertas de medicación perdida
            NotificationChannel alertChannel = new NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    "Alertas de medicación",
                    NotificationManager.IMPORTANCE_HIGH);
            alertChannel.setDescription("Alertas de medicación no tomada");
            alertChannel.enableLights(true);
            alertChannel.setLightColor(Color.RED);
            alertChannel.enableVibration(true);
            alertChannel.setShowBadge(true);
            
            notificationManager.createNotificationChannel(reminderChannel);
            notificationManager.createNotificationChannel(alertChannel);
        }
    }
    
    /**
     * Crea canales de notificación para Android 8.0+
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = 
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            
            // Canal para recordatorios de medicación
            NotificationChannel medicationChannel = new NotificationChannel(
                    CHANNEL_MEDICATION_REMINDERS,
                    "Recordatorios de medicación",
                    NotificationManager.IMPORTANCE_HIGH);
            
            medicationChannel.setDescription("Notificaciones para recordar tomar medicamentos");
            medicationChannel.enableLights(true);
            medicationChannel.enableVibration(true);
            medicationChannel.setBypassDnd(true); // Importante: Ignorar modo No molestar
            
            // Canal para medicamentos perdidos
            NotificationChannel missedChannel = new NotificationChannel(
                    CHANNEL_MISSED_MEDICATIONS,
                    "Medicamentos no tomados",
                    NotificationManager.IMPORTANCE_HIGH);
            
            missedChannel.setDescription("Alertas de medicamentos que no fueron tomados a tiempo");
            missedChannel.enableLights(true);
            missedChannel.enableVibration(true);
            missedChannel.setBypassDnd(true);
            
            // Crear los canales
            notificationManager.createNotificationChannel(medicationChannel);
            notificationManager.createNotificationChannel(missedChannel);
            
            Log.d(TAG, "Canales de notificación creados correctamente");
        }
    }

    /**
     * Genera una notificación de recordatorio (30 minutos antes)
     */
    public void showUpcomingMedicationReminder(Medication medication, Schedule schedule) {
        // Construir intent para abrir la app cuando se toca la notificación
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("medicationId", medication.getId());
        intent.putExtra("scheduleId", schedule.getId());
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                generateRequestCode(NOTIFICATION_TYPE_UPCOMING, medication.getId(), schedule.getId()),
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Construir la notificación con icono en color normal/verde
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Recordatorio de medicación")
                .setContentText("Es hora de prepararse para tomar: " + medication.getName())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("En 30 minutos deberá tomar " + medication.getPillsPerDose() + 
                                 " unidad(es) de " + medication.getName() + 
                                 ". Por favor, esté atento al dispensador."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setColor(ContextCompat.getColor(context, R.color.colorGreen)) // Tinte verde para recordatorios
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                
        // Mostrar la notificación
        int notificationId = generateNotificationId(NOTIFICATION_TYPE_UPCOMING, medication.getId(), schedule.getId());
        notificationManager.notify(notificationId, builder.build());
    }
    
    /**
     * Genera una notificación de medicación no tomada
     */
    public void showMissedMedicationAlert(Medication medication, Schedule schedule) {
        // Construir intent para abrir la app cuando se toca la notificación
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("medicationId", medication.getId());
        intent.putExtra("scheduleId", schedule.getId());
        intent.putExtra("showMissed", true);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                generateRequestCode(NOTIFICATION_TYPE_MISSED, medication.getId(), schedule.getId()),
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Construir la notificación con el mismo icono pero en color rojo para alertas
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("¡Medicación pendiente!")
                .setContentText("No ha tomado: " + medication.getName())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("No ha tomado su dosis programada de " + medication.getName() + 
                                 ". Por favor tome su medicamento lo antes posible o contacte a su cuidador."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setColor(ContextCompat.getColor(context, R.color.colorRed)) // Tinte rojo para alertas
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                
        // Mostrar la notificación
        int notificationId = generateNotificationId(NOTIFICATION_TYPE_MISSED, medication.getId(), schedule.getId());
        notificationManager.notify(notificationId, builder.build());
    }
    
    /**
     * Muestra una notificación de recordatorio de medicación
     */
    public static void showMedicationReminder(Context context, String title, String message, int notificationId) {
        // Intent para abrir la app al tocar la notificación
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        // Crear PendingIntent con flags apropiados según versión Android
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getActivity(context, notificationId, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            pendingIntent = PendingIntent.getActivity(context, notificationId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }
        
        // Construir notificación
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MEDICATION_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification_medication_foreground) // Asegúrate de tener este icono en drawable
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setDefaults(NotificationCompat.DEFAULT_ALL);
        
        // Mostrar la notificación
        NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        notificationManager.notify(notificationId, builder.build());
        
        Log.d(TAG, "Notificación mostrada con ID: " + notificationId);
    }

    /**
     * Cancela una notificación de recordatorio
     */
    public void cancelUpcomingReminder(String medicationId, String scheduleId) {
        int notificationId = generateNotificationId(NOTIFICATION_TYPE_UPCOMING, medicationId, scheduleId);
        notificationManager.cancel(notificationId);
    }
    
    /**
     * Cancela una notificación de medicación no tomada
     */
    public void cancelMissedMedicationAlert(String medicationId, String scheduleId) {
        int notificationId = generateNotificationId(NOTIFICATION_TYPE_MISSED, medicationId, scheduleId);
        notificationManager.cancel(notificationId);
    }
    
    /**
     * Genera un ID único para una notificación
     */
    private int generateNotificationId(int type, String medicationId, String scheduleId) {
        int baseId = (type == NOTIFICATION_TYPE_UPCOMING) ? 
                     REMINDER_NOTIFICATION_ID_BASE : 
                     MISSED_NOTIFICATION_ID_BASE;
        
        // Usamos hash codes para generar un ID único pero consistente
        int medHash = medicationId.hashCode();
        int schedHash = scheduleId.hashCode();
        
        return baseId + Math.abs((medHash * 31 + schedHash) % 1000);
    }
    
    /**
     * Genera un código de solicitud único para PendingIntents
     */
    private int generateRequestCode(int type, String medicationId, String scheduleId) {
        // Similar al ID de notificación pero con una base diferente
        int baseCode = (type == NOTIFICATION_TYPE_UPCOMING) ? 3000 : 4000;
        int medHash = medicationId.hashCode();
        int schedHash = scheduleId.hashCode();
        
        return baseCode + Math.abs((medHash * 31 + schedHash) % 1000);
    }
}