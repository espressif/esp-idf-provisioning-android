package com.espressif.ui.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.espressif.data.repository.MedicationRepository;
import com.espressif.data.repository.UserRepository;
import com.espressif.ui.models.Medication;

import java.util.List;

/**
 * BroadcastReceiver para reprogramar alarmas después de reiniciar el dispositivo
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed, reprogramando notificaciones");
            
            // Obtener el ID del paciente usando el método seguro
            UserRepository userRepository = UserRepository.getInstance(context);
            String patientId = userRepository.getSelectedPatientId();
            
            if (patientId == null || patientId.isEmpty()) {
                Log.d(TAG, "No hay paciente conectado o ID válido, no se reprograman notificaciones");
                return;
            }
            
            // Registrar el ID del paciente obtenido para depuración
            Log.d(TAG, "Reprogramando notificaciones para paciente ID: " + patientId);
            
            // Obtener todos los medicamentos y programar sus notificaciones
            MedicationRepository medicationRepository = MedicationRepository.getInstance();
            medicationRepository.getAllMedications(patientId, new MedicationRepository.MedicationsCallback() {
                public void onSuccess(List<Medication> medications) {
                    NotificationScheduler scheduler = new NotificationScheduler(context);
                    scheduler.rescheduleMedicationReminders(patientId, medications);
                }
                
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error al obtener medicamentos: " + errorMessage);
                }
            });
        }
    }
}