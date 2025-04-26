package com.espressif.ui.dialogs;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context; // Añadir esta línea
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.espressif.data.repository.MedicationRepository;
import com.espressif.data.repository.UserRepository;
import com.espressif.mediwatch.R;
import com.espressif.ui.models.Medication;
import com.espressif.ui.models.Schedule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

// Importar las clases necesarias para notificaciones
import com.espressif.ui.notifications.NotificationHelper;
import com.espressif.ui.notifications.NotificationScheduler;

public class ScheduleDialogFragment extends DialogFragment {

    private static final String TAG = "ScheduleDialog";
    
    // Argumentos para el fragmento
    private static final String ARG_MEDICATION_ID = "medication_id";
    private static final String ARG_MEDICATION_NAME = "medication_name";
    private static final String ARG_SCHEDULE_ID = "schedule_id";
    
    // Componentes de la UI
    private TextView tvDialogTitle;
    private TextView tvMedicationName;
    private TextView tvSelectedTime;
    private LinearLayout layoutTimePicker;
    private RadioGroup rgFrequency;
    private MaterialRadioButton rbDaily, rbWeekdays, rbWeekends, rbCustom;
    private LinearLayout layoutCustomDays;
    private CheckBox cbMonday, cbTuesday, cbWednesday, cbThursday, cbFriday, cbSaturday, cbSunday;
    private SwitchMaterial switchActive;
    private MaterialButton btnCancel, btnSave;
    
    // Añadir una nueva variable para controlar el formato de hora
    private boolean use24HourFormat = true; // Por defecto usamos formato 24h
    private TextView tvHourFormat; // Nuevo TextView para mostrar/cambiar el formato
    
    // Añadir variables de clase para los nuevos componentes
    private MaterialRadioButton rbInterval;
    private LinearLayout layoutIntervalHours;
    private RadioGroup rgInterval;
    private MaterialRadioButton rbInterval4, rbInterval6, rbInterval8, rbInterval12;
    private TextInputEditText etTreatmentDays;
    
    // Datos
    private String medicationId;
    private String medicationName;
    private String scheduleId;
    private Schedule schedule;
    private int hour = 8;
    private int minute = 0;
    private String patientId;
    
    // Repositorios
    private MedicationRepository medicationRepository;
    private UserRepository userRepository;
    
    // Listener para comunicarse con la actividad/fragmento que lo llamó
    private ScheduleDialogListener listener;
    
    // Interface para el listener
    public interface ScheduleDialogListener {
        void onScheduleSaved(Schedule schedule);
    }
    
    /**
     * Crea una nueva instancia del fragmento
     */
    public static ScheduleDialogFragment newInstance(String medicationId, String medicationName) {
        ScheduleDialogFragment fragment = new ScheduleDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MEDICATION_ID, medicationId);
        args.putString(ARG_MEDICATION_NAME, medicationName);
        fragment.setArguments(args);
        return fragment;
    }
    
    /**
     * Crea una nueva instancia del fragmento para editar un horario existente
     */
    public static ScheduleDialogFragment newInstance(String medicationId, String medicationName, String scheduleId) {
        ScheduleDialogFragment fragment = new ScheduleDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MEDICATION_ID, medicationId);
        args.putString(ARG_MEDICATION_NAME, medicationName);
        args.putString(ARG_SCHEDULE_ID, scheduleId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Inicializar repositorios
        try {
            medicationRepository = MedicationRepository.getInstance();
            userRepository = UserRepository.getInstance(requireContext());
            
            // Enfoque mejorado para obtener un ID de paciente válido
            patientId = obtainValidPatientId();
            
            if (patientId == null || patientId.isEmpty()) {
                Log.e(TAG, "ID de paciente no válido. No se pueden guardar horarios.");
                Toast.makeText(requireContext(), "Error: No se puede determinar el paciente", Toast.LENGTH_LONG).show();
                dismiss();
            } else {
                Log.d(TAG, "ID de paciente obtenido correctamente: " + patientId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar repositorios", e);
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error al inicializar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            dismiss();
        }
        
        // Obtener argumentos
        if (getArguments() != null) {
            medicationId = getArguments().getString(ARG_MEDICATION_ID);
            medicationName = getArguments().getString(ARG_MEDICATION_NAME);
            scheduleId = getArguments().getString(ARG_SCHEDULE_ID);
        }
        
        // Estilo personalizado para el diálogo
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_MediWatch_Dialog);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_schedule, container, false);
        
        // Inicializar vistas
        tvDialogTitle = view.findViewById(R.id.tv_dialog_title);
        tvMedicationName = view.findViewById(R.id.tv_medication_name);
        tvSelectedTime = view.findViewById(R.id.tv_selected_time);
        layoutTimePicker = view.findViewById(R.id.layout_time_picker);
        rgFrequency = view.findViewById(R.id.rg_frequency);
        rbDaily = view.findViewById(R.id.rb_daily);
        rbWeekdays = view.findViewById(R.id.rb_weekdays);
        rbWeekends = view.findViewById(R.id.rb_weekends);
        rbCustom = view.findViewById(R.id.rb_custom);
        layoutCustomDays = view.findViewById(R.id.layout_custom_days);
        cbMonday = view.findViewById(R.id.cb_monday);
        cbTuesday = view.findViewById(R.id.cb_tuesday);
        cbWednesday = view.findViewById(R.id.cb_wednesday);
        cbThursday = view.findViewById(R.id.cb_thursday);
        cbFriday = view.findViewById(R.id.cb_friday);
        cbSaturday = view.findViewById(R.id.cb_saturday);
        cbSunday = view.findViewById(R.id.cb_sunday);
        switchActive = view.findViewById(R.id.switch_active);
        btnCancel = view.findViewById(R.id.btn_cancel);
        btnSave = view.findViewById(R.id.btn_save);
        
        // Referencia al nuevo TextView para el formato de hora
        tvHourFormat = view.findViewById(R.id.tv_hour_format);
        
        // Inicializar nuevos componentes
        rbInterval = view.findViewById(R.id.rb_interval);
        layoutIntervalHours = view.findViewById(R.id.layout_interval_hours);
        rgInterval = view.findViewById(R.id.rg_interval);
        rbInterval4 = view.findViewById(R.id.rb_interval_4);
        rbInterval6 = view.findViewById(R.id.rb_interval_6);
        rbInterval8 = view.findViewById(R.id.rb_interval_8);
        rbInterval12 = view.findViewById(R.id.rb_interval_12);
        etTreatmentDays = view.findViewById(R.id.et_treatment_days);
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Configurar título y subtítulo
        if (scheduleId != null) {
            tvDialogTitle.setText("Editar horario");
        } else {
            tvDialogTitle.setText("Nuevo horario");
        }
        
        tvMedicationName.setText(medicationName);
        
        // Inicializar el formato de hora
        updateFormatDisplay(); // Añade esta línea
        
        // Configurar hora seleccionada por defecto
        updateTimeDisplay();
        
        // Si estamos editando un horario existente, cargar sus datos
        if (scheduleId != null) {
            loadSchedule();
        }
        
        setupListeners();
    }
    
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
    
    /**
     * Configura todos los listeners para la interacción con el usuario
     */
    private void setupListeners() {
        // Click en el selector de hora
        layoutTimePicker.setOnClickListener(v -> showTimePickerDialog());
        
        // Cambio en la selección de frecuencia
        rgFrequency.setOnCheckedChangeListener((group, checkedId) -> {
            // Ocultar todos los layouts específicos primero
            layoutCustomDays.setVisibility(View.GONE);
            layoutIntervalHours.setVisibility(View.GONE);
            
            // Mostrar el layout correspondiente según la selección
            if (checkedId == R.id.rb_custom) {
                layoutCustomDays.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.rb_interval) {
                layoutIntervalHours.setVisibility(View.VISIBLE);
            }
        });
        
        // Botones de acción
        btnCancel.setOnClickListener(v -> dismiss());
        
        btnSave.setOnClickListener(v -> {
            if (validateForm()) {
                saveSchedule();
            }
        });
        
        // Click en el formato de hora para cambiar entre 12h/24h
        tvHourFormat.setOnClickListener(v -> {
            use24HourFormat = !use24HourFormat;
            updateFormatDisplay();
            updateTimeDisplay(); // Actualizar la visualización de la hora según el nuevo formato
        });
    }
    
    /**
     * Muestra el diálogo para seleccionar la hora
     */
    private void showTimePickerDialog() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minute) -> {
                    this.hour = hourOfDay;
                    this.minute = minute;
                    updateTimeDisplay();
                },
                hour, minute, use24HourFormat); // Usar el formato elegido
        timePickerDialog.show();
    }
    
    /**
     * Actualiza el texto de la hora seleccionada
     */
    private void updateTimeDisplay() {
        String timeText;
        if (use24HourFormat) {
            timeText = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
        } else {
            // Convertir a formato 12h
            int displayHour = hour % 12;
            if (displayHour == 0) displayHour = 12; // Las 0 horas en formato 12h son las 12 AM
            String amPm = hour >= 12 ? "PM" : "AM";
            timeText = String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm);
        }
        tvSelectedTime.setText(timeText);
    }
    
    /**
     * Carga los datos de un horario existente
     */
    private void loadSchedule() {
        medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
            @Override
            public void onSuccess(Medication data) {
                if (data != null && data.getSchedules() != null) {
                    // Corregido para trabajar con Map<String, Schedule>
                    Map<String, Schedule> schedules = data.getSchedules();
                    // Buscar primero por clave directa
                    if (schedules.containsKey(scheduleId)) {
                        schedule = schedules.get(scheduleId);
                        populateFormWithSchedule(schedule);
                    } else {
                        // Si no se encuentra por clave, buscar en los valores
                        for (Schedule s : schedules.values()) {
                            if (s.getId() != null && s.getId().equals(scheduleId)) {
                                schedule = s;
                                populateFormWithSchedule(schedule);
                                break;
                            }
                        }
                    }
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Toast.makeText(requireContext(), "Error al cargar horario: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Rellena el formulario con los datos del horario
     */
    private void populateFormWithSchedule(Schedule schedule) {
        // Configurar hora
        hour = schedule.getHour();
        minute = schedule.getMinute();
        updateTimeDisplay();
        
        // Configurar días de la semana
        ArrayList<Boolean> days = schedule.getDaysOfWeek();
        if (days != null && days.size() >= 7) {
            // Verificar si es un patrón conocido
            boolean isDaily = true;
            boolean isWeekday = true;
            boolean isWeekend = true;
            
            for (int i = 0; i < 5; i++) { // Lun-Vie
                if (!days.get(i)) {
                    isDaily = false;
                    isWeekday = false;
                }
            }
            
            for (int i = 5; i < 7; i++) { // Sáb-Dom
                if (!days.get(i)) {
                    isDaily = false;
                    isWeekend = false;
                } else {
                    isWeekday = false;
                }
            }
            
            if (isDaily) {
                rbDaily.setChecked(true);
            } else if (isWeekday) {
                rbWeekdays.setChecked(true);
            } else if (isWeekend) {
                rbWeekends.setChecked(true);
            } else {
                rbCustom.setChecked(true);
                layoutCustomDays.setVisibility(View.VISIBLE);
                
                // Marcar los checkboxes correspondientes
                cbMonday.setChecked(days.get(0));
                cbTuesday.setChecked(days.get(1));
                cbWednesday.setChecked(days.get(2));
                cbThursday.setChecked(days.get(3));
                cbFriday.setChecked(days.get(4));
                cbSaturday.setChecked(days.get(5));
                cbSunday.setChecked(days.get(6));
            }
        }
        
        // Configurar estado activo
        switchActive.setChecked(schedule.isActive());
        
        // Si el horario tiene información de formato, usarla
        use24HourFormat = schedule.isUse24HourFormat();
        updateFormatDisplay();
        
        // Verificar si está en modo intervalo
        if (schedule.isIntervalMode()) {
            rbInterval.setChecked(true);
            layoutIntervalHours.setVisibility(View.VISIBLE);
            
            // Configurar el intervalo correcto
            switch (schedule.getIntervalHours()) {
                case 4:
                    rbInterval4.setChecked(true);
                    break;
                case 6:
                    rbInterval6.setChecked(true);
                    break;
                case 12:
                    rbInterval12.setChecked(true);
                    break;
                default:
                    rbInterval8.setChecked(true);
                    break;
            }
            
            // Mostrar días de tratamiento
            etTreatmentDays.setText(String.valueOf(schedule.getTreatmentDays()));
        }
    }
    
    /**
     * Valida que el formulario esté completo
     */
    private boolean validateForm() {
        // Si es personalizado, verificar que al menos un día esté seleccionado
        if (rbCustom.isChecked()) {
            boolean anyDaySelected = cbMonday.isChecked() || cbTuesday.isChecked() || 
                                    cbWednesday.isChecked() || cbThursday.isChecked() || 
                                    cbFriday.isChecked() || cbSaturday.isChecked() || 
                                    cbSunday.isChecked();
            
            if (!anyDaySelected) {
                Toast.makeText(requireContext(), "Por favor, selecciona al menos un día", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Guarda el horario en la base de datos
     */
    private void saveSchedule() {
        try {
            if (schedule == null) {
                schedule = new Schedule();
                schedule.setId(UUID.randomUUID().toString());
            }
            
            // Asignar datos del formulario al objeto Schedule
            schedule.setMedicationId(medicationId);
            schedule.setHour(hour);
            schedule.setMinute(minute);
            schedule.setUse24HourFormat(use24HourFormat); // Guardar el formato de hora
            
            // Configurar los días según la selección
            ArrayList<Boolean> days = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                days.add(false); // Inicializar con falsos
            }
            
            if (rbInterval.isChecked()) {
                // Si es por intervalos, configurar atributos específicos
                int intervalHours = 8; // Valor predeterminado
                if (rbInterval4.isChecked()) intervalHours = 4;
                else if (rbInterval6.isChecked()) intervalHours = 6;
                else if (rbInterval12.isChecked()) intervalHours = 12;
                
                // Obtener días de tratamiento (con validación)
                int treatmentDays = 2; // Valor predeterminado
                try {
                    String daysStr = etTreatmentDays.getText().toString();
                    if (!daysStr.isEmpty()) {
                        treatmentDays = Integer.parseInt(daysStr);
                        if (treatmentDays <= 0) treatmentDays = 1;
                        if (treatmentDays > 30) treatmentDays = 30; // Limitar a un mes como máximo
                    }
                } catch (NumberFormatException e) {
                    // En caso de error, usar el valor predeterminado
                }
                
                // Guardar en el objeto Schedule
                schedule.setIntervalMode(true);
                schedule.setIntervalHours(intervalHours);
                schedule.setTreatmentDays(treatmentDays);
                
                // Activar todos los días para que se generen los horarios correspondientes
                for (int i = 0; i < 7; i++) {
                    days.set(i, true);
                }
                
                // Calcular fecha de finalización del tratamiento
                Calendar endDate = Calendar.getInstance();
                endDate.add(Calendar.DAY_OF_MONTH, treatmentDays);
                schedule.setTreatmentEndDate(endDate.getTimeInMillis());
            } else if (rbDaily.isChecked()) {
                // Todos los días
                for (int i = 0; i < 7; i++) {
                    days.set(i, true);
                }
            } else if (rbWeekdays.isChecked()) {
                // Lunes a viernes
                for (int i = 0; i < 5; i++) {
                    days.set(i, true);
                }
            } else if (rbWeekends.isChecked()) {
                // Sábado y domingo
                days.set(5, true);
                days.set(6, true);
            } else if (rbCustom.isChecked()) {
                // Días personalizados
                days.set(0, cbMonday.isChecked());
                days.set(1, cbTuesday.isChecked());
                days.set(2, cbWednesday.isChecked());
                days.set(3, cbThursday.isChecked());
                days.set(4, cbFriday.isChecked());
                days.set(5, cbSaturday.isChecked());
                days.set(6, cbSunday.isChecked());
            }
            
            schedule.setDaysOfWeek(days);
            schedule.setActive(switchActive.isChecked());
            
            // Calcular próxima fecha programada
            calculateNextScheduledTime(schedule, days);
            
            // Verificación final antes de guardar
            if (!isValidSchedule(schedule)) {
                Toast.makeText(requireContext(), "Datos de horario incompletos o inválidos", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Paso 1: Verificar si hay horarios existentes para eliminarlos
            checkAndRemoveExistingSchedules(schedule, () -> {
                // Paso 2: Luego de verificar y eliminar (si era necesario), continuar con el guardado
                continueWithSave(schedule);
            });
            
        } catch (Exception e) {
            // Capturar cualquier otra excepción no controlada
            if (isAdded() && getContext() != null) {
                Toast.makeText(getContext(), "Error inesperado: " + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace(); 
            }
        }
    }
    
    /**
     * Verifica si hay horarios existentes para este medicamento y los elimina
     * @param newSchedule El nuevo horario a guardar
     * @param onComplete Callback a ejecutar cuando se complete el proceso
     */
    private void checkAndRemoveExistingSchedules(Schedule newSchedule, Runnable onComplete) {
        // Obtener medicamento actual para ver sus horarios
        medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
            @Override
            public void onSuccess(Medication medication) {
                if (medication != null && medication.getSchedules() != null && !medication.getSchedules().isEmpty()) {
                    // Hay horarios existentes, necesitamos eliminarlos
                    
                    // Obtener todos los IDs de horarios excepto el actual (si estamos editando)
                    List<String> schedulesToRemove = new ArrayList<>();
                    for (String id : medication.getSchedules().keySet()) {
                        // Si estamos editando, no eliminar el mismo que estamos editando
                        if (scheduleId == null || !id.equals(scheduleId)) {
                            schedulesToRemove.add(id);
                        }
                    }
                    
                    // Si no hay nada que eliminar, continuar con el guardado
                    if (schedulesToRemove.isEmpty()) {
                        onComplete.run();
                        return;
                    }
                    
                    // Contador para saber cuándo hemos terminado con todas las eliminaciones
                    final int[] counter = {schedulesToRemove.size()};
                    
                    // Eliminar cada horario uno por uno
                    for (String idToRemove : schedulesToRemove) {
                        medicationRepository.deleteSchedule(patientId, medicationId, idToRemove, new MedicationRepository.DatabaseCallback() {
                            @Override
                            public void onSuccess() {
                                counter[0]--;
                                // Cuando todos han sido eliminados, continuar con el guardado
                                if (counter[0] <= 0) {
                                    onComplete.run();
                                }
                            }
                            
                            @Override
                            public void onError(String errorMessage) {
                                // Incluso si falla la eliminación, continuamos (intentamos lo mejor posible)
                                Log.e(TAG, "Error al eliminar horario anterior: " + errorMessage);
                                counter[0]--;
                                if (counter[0] <= 0) {
                                    onComplete.run();
                                }
                            }
                        });
                    }
                } else {
                    // No hay horarios existentes, continuar directamente
                    onComplete.run();
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                // Si hay un error al obtener el medicamento, intentamos guardar de todas formas
                Log.e(TAG, "Error al verificar horarios existentes: " + errorMessage);
                onComplete.run();
            }
        });
    }
    
    /**
     * Continúa con el proceso de guardado después de eliminar horarios existentes
     */
    private void continueWithSave(Schedule schedule) {
        // Notificar primero al listener (código existente)
        if (listener != null) {
            listener.onScheduleSaved(schedule);
        }
        
        // AGREGAR ESTE CÓDIGO AQUÍ: Programar notificaciones para este horario
        if (schedule.isActive()) {
            try {
                // Asegurarnos que el canal de notificación existe
                NotificationHelper.createNotificationChannels(requireContext());
                
                // Obtener el medicamento actual para la notificación
                medicationRepository.getMedication(patientId, medicationId, new MedicationRepository.DataCallback<Medication>() {
                    @Override
                    public void onSuccess(Medication medication) {
                        if (medication != null) {
                            // Programar las notificaciones para este horario
                            NotificationScheduler scheduler = new NotificationScheduler(requireContext());
                            boolean scheduled = scheduler.scheduleReminder(patientId, medication, schedule);
                            
                            if (scheduled) {
                                Log.d(TAG, "✅ Notificación programada correctamente para: " + medication.getName());
                            } else {
                                Log.e(TAG, "❌ No se pudo programar la notificación para: " + medication.getName());
                            }
                        }
                    }
                    
                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "Error al obtener medicamento para programar notificación: " + errorMessage);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error al programar notificación: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "Horario guardado pero inactivo - no se programan notificaciones");
        }
        
        // Luego intentar guardar en Firebase (código existente)
        try {
            medicationRepository.saveSchedule(patientId, medicationId, schedule, new MedicationRepository.DatabaseCallback() {
                @Override
                public void onSuccess() {
                    if (isAdded() && getActivity() != null) {
                        Toast.makeText(getActivity(), "Horario guardado correctamente", Toast.LENGTH_SHORT).show();
                        dismiss();
                    }
                }
                
                @Override
                public void onError(String errorMessage) {
                    if (isAdded() && getActivity() != null) {
                        Toast.makeText(getActivity(), "Advertencia: Cambios locales aplicados, pero hubo un error al sincronizar: " + errorMessage, Toast.LENGTH_LONG).show();
                        dismiss();
                    }
                }
            });
        } catch (Exception e) {
            // Si falla la llamada a Firebase (código existente)
            Toast.makeText(requireContext(), "Error de conexión: Los cambios se aplicarán cuando vuelvas a conectarte", Toast.LENGTH_LONG).show();
            e.printStackTrace();
            dismiss();
        }
    }
    
    // Método auxiliar para calcular la próxima fecha programada
    private void calculateNextScheduledTime(Schedule schedule, ArrayList<Boolean> days) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        
        // Si es modo intervalo, calcular la próxima hora según el intervalo
        if (schedule.isIntervalMode()) {
            // Establecer la hora inicial
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
            calendar.set(java.util.Calendar.MINUTE, minute);
            calendar.set(java.util.Calendar.SECOND, 0);
            calendar.set(java.util.Calendar.MILLISECOND, 0);
            
            // Si la hora ya pasó hoy, la primera dosis será en intervalHours horas
            if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
                calendar.add(java.util.Calendar.HOUR_OF_DAY, schedule.getIntervalHours());
            }
            
            // Calcular fecha de fin del tratamiento
            java.util.Calendar endDate = java.util.Calendar.getInstance();
            endDate.add(java.util.Calendar.DAY_OF_MONTH, schedule.getTreatmentDays());
            schedule.setTreatmentEndDate(endDate.getTimeInMillis());
        } else {
            // Código existente para modo basado en días de la semana
            int currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
            int currentMinute = calendar.get(java.util.Calendar.MINUTE);
            int currentDayOfWeek = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7; // Convertir a índice 0-6 (Lun-Dom)
        
            boolean scheduleForToday = days.get(currentDayOfWeek) && 
                                     (hour > currentHour || (hour == currentHour && minute > currentMinute));
        
            if (scheduleForToday) {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
                calendar.set(java.util.Calendar.MINUTE, minute);
                calendar.set(java.util.Calendar.SECOND, 0);
                calendar.set(java.util.Calendar.MILLISECOND, 0);
            } else {
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
                
                if (foundDay) {
                    calendar.add(java.util.Calendar.DAY_OF_MONTH, daysToAdd);
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
                    calendar.set(java.util.Calendar.MINUTE, minute);
                    calendar.set(java.util.Calendar.SECOND, 0);
                    calendar.set(java.util.Calendar.MILLISECOND, 0);
                } else {
                    calendar.add(java.util.Calendar.YEAR, 1);
                }
            }
        }
        
        schedule.setNextScheduled(calendar.getTimeInMillis());
    }
    
    // Método para validar el schedule
    private boolean isValidSchedule(Schedule schedule) {
        return schedule != null && 
               schedule.getId() != null && 
               !schedule.getId().isEmpty() &&
               schedule.getMedicationId() != null && 
               !schedule.getMedicationId().isEmpty() &&
               schedule.getDaysOfWeek() != null &&
               schedule.getDaysOfWeek().size() == 7;
    }
    
    /**
     * Establece el listener para comunicarse con el fragmento/actividad que lo llamó
     */
    public void setListener(ScheduleDialogListener listener) {
        this.listener = listener;
    }
    
    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    /**
     * Actualiza la visualización del formato de hora (12h o 24h)
     */
    private void updateFormatDisplay() {
        tvHourFormat.setText(use24HourFormat ? "24h" : "AM/PM");
    }

    /**
     * Método que intenta obtener un ID de paciente válido usando múltiples estrategias
     * @return Un ID de paciente válido o null si no se puede obtener
     */
    private String obtainValidPatientId() {
        String id = null;
        
        // Estrategia 1: Intentar obtener el ID seleccionado (la mejor fuente)
        try {
            id = userRepository.getSelectedPatientId();
            Log.d(TAG, "Estrategia 1: ID de paciente seleccionado: " + id);
            
            if (isValidPatientId(id)) {
                Log.d(TAG, "✅ Usando ID seleccionado: " + id);
                return id;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener ID seleccionado", e);
        }
        
        // Estrategia 2: Intentar obtener ID conectado
        try {
            id = userRepository.getConnectedPatientId();
            Log.d(TAG, "Estrategia 2: ID de paciente conectado: " + id);
            
            if (isValidPatientId(id)) {
                Log.d(TAG, "✅ Usando ID conectado: " + id);
                return id;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener ID conectado", e);
        }
        
        // Estrategia 3: Intentar obtener directamente de las preferencias
        try {
            id = userRepository.getPreferencesHelper().getPatientId();
            
            Log.d(TAG, "Estrategia 3: ID de paciente desde preferencias: " + id);
            
            if (isValidPatientId(id)) {
                Log.d(TAG, "✅ Usando ID de preferencias: " + id);
                return id;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener ID desde preferencias", e);
        }
        
        Log.e(TAG, "❌ No se pudo obtener un ID de paciente válido mediante ninguna estrategia");
        return null;
    }
    
    /**
     * Valida si un ID de paciente es válido
     * @param id ID a validar
     * @return true si es válido, false en caso contrario
     */
    private boolean isValidPatientId(String id) {
        return id != null && !id.isEmpty() && !"current_user_id".equals(id);
    }
}