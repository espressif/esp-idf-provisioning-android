package com.espressif.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.ui.models.Schedule;
import com.espressif.mediwatch.R;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private List<Schedule> schedules = new ArrayList<>();
    private final Context context;
    private ScheduleListener listener;

    public interface ScheduleListener {
        void onScheduleClick(Schedule schedule);
        void onScheduleActiveChanged(Schedule schedule, boolean active);
    }

    public ScheduleAdapter(Context context) {
        this.context = context;
    }
    
    public ScheduleAdapter(Context context, ScheduleListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        view.setTag(R.id.tag_adapter, this); // Guardar referencia al adapter
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Schedule schedule = schedules.get(position);
        
        // Decidir qué vista mostrar según el tipo de horario
        if (schedule.isIntervalMode()) {
            // Mostrar vista de intervalos
            holder.layoutStandardSchedule.setVisibility(View.GONE);
            holder.layoutIntervalSchedule.setVisibility(View.VISIBLE);
            
            // Configurar la hora de inicio
            String timeText = formatTime(schedule.getHour(), schedule.getMinute(), schedule.isUse24HourFormat());
            holder.tvIntervalTime.setText(timeText);
            
            // Configurar información del intervalo
            StringBuilder intervalInfo = new StringBuilder();
            intervalInfo.append("Cada ").append(schedule.getIntervalHours()).append(" horas");
            
            // Calcular días restantes si la fecha de fin está en el futuro
            if (schedule.getTreatmentEndDate() > System.currentTimeMillis()) {
                long daysRemaining = (schedule.getTreatmentEndDate() - System.currentTimeMillis()) / (24 * 60 * 60 * 1000) + 1;
                if (daysRemaining > 0) {
                    intervalInfo.append(" • ")
                               .append(daysRemaining)
                               .append(" día")
                               .append(daysRemaining > 1 ? "s" : "")
                               .append(" restante")
                               .append(daysRemaining > 1 ? "s" : "");
                }
            }
            
            holder.tvIntervalInfo.setText(intervalInfo.toString());
        } else {
            // Mostrar vista estándar
            holder.layoutStandardSchedule.setVisibility(View.VISIBLE);
            holder.layoutIntervalSchedule.setVisibility(View.GONE);
            
            // Código existente para formatear hora
            String timeText = formatTime(schedule.getHour(), schedule.getMinute(), schedule.isUse24HourFormat());
            holder.tvTime.setText(timeText);
            
            // Código existente para mostrar días
            String daysText = formatDaysOfWeek(schedule.getDaysOfWeek());
            holder.tvDays.setText(daysText);
        }
        
        // Código común para ambos tipos (estado, switch, etc.)
        holder.switchActive.setChecked(schedule.isActive());
        updateStatusIndicator(holder, schedule);
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    public void setSchedules(List<Schedule> schedules) {
        this.schedules = schedules != null ? schedules : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setListener(ScheduleListener listener) {
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        // Referencias a las vistas
        LinearLayout layoutStandardSchedule;
        TextView tvTime;
        TextView tvDays;
        
        LinearLayout layoutIntervalSchedule;
        TextView tvIntervalTime;
        TextView tvIntervalInfo;
        
        ImageView ivStatus;
        TextView tvStatusText;
        SwitchMaterial switchActive;
        
        // Referencia al adaptador
        private final ScheduleAdapter adapter;
        
        // Constructor con todas las referencias a vistas
        public ViewHolder(View itemView) {
            super(itemView);
            
            // Vista estándar
            layoutStandardSchedule = itemView.findViewById(R.id.layout_standard_schedule);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvDays = itemView.findViewById(R.id.tv_days);
            
            // Vista de intervalo
            layoutIntervalSchedule = itemView.findViewById(R.id.layout_interval_schedule);
            tvIntervalTime = itemView.findViewById(R.id.tv_interval_time);
            tvIntervalInfo = itemView.findViewById(R.id.tv_interval_info);
            
            // Elementos comunes
            ivStatus = itemView.findViewById(R.id.iv_status);
            tvStatusText = itemView.findViewById(R.id.tv_status_text);
            switchActive = itemView.findViewById(R.id.switch_active);
            
            // Guardar referencia al adaptador padre para acceder a sus métodos/variables
            adapter = (ScheduleAdapter) itemView.getTag(R.id.tag_adapter);
            
            // Click listener para el item
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && adapter.listener != null) {
                    adapter.listener.onScheduleClick(adapter.schedules.get(position));
                }
            });
            
            // Listener para el switch de activación
            switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && adapter.listener != null) {
                    Schedule schedule = adapter.schedules.get(position);
                    if (schedule.isActive() != isChecked) {
                        schedule.setActive(isChecked);
                        adapter.listener.onScheduleActiveChanged(schedule, isChecked);
                    }
                }
            });
        }
    }
    
    /**
     * Convierte el array de días de la semana a un texto legible
     * @param days Array de booleanos representando los días (Lun-Dom)
     * @return Texto descriptivo de los días seleccionados
     */
    private String formatDaysOfWeek(ArrayList<Boolean> days) {
        if (days == null || days.size() < 7) {
            return "Horario sin días configurados";
        }
        
        // Verificar patrones comunes
        boolean allTrue = true;
        boolean weekdaysOnly = true;
        boolean weekendsOnly = true;
        
        // Verificar días laborables (Lun-Vie)
        for (int i = 0; i < 5; i++) {
            if (!days.get(i)) {
                allTrue = false;
                weekdaysOnly = false;
            }
        }
        
        // Verificar fin de semana (Sáb-Dom)
        for (int i = 5; i < 7; i++) {
            if (!days.get(i)) {
                allTrue = false;
                weekendsOnly = false;
            } else {
                weekdaysOnly = false;
            }
        }
        
        // Devolver texto según el patrón
        if (allTrue) {
            return "Todos los días";
        } else if (weekdaysOnly) {
            return "Lunes a viernes";
        } else if (weekendsOnly) {
            return "Sábados y domingos";
        } else {
            // Lista específica de días
            StringBuilder result = new StringBuilder();
            String[] dayNames = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
            
            for (int i = 0; i < 7; i++) {
                if (days.get(i)) {
                    if (result.length() > 0) {
                        result.append(", ");
                    }
                    result.append(dayNames[i]);
                }
            }
            
            if (result.length() == 0) {
                return "Ningún día seleccionado";
            }
            
            return result.toString();
        }
    }
    
    /**
     * Formatea hora y minutos en formato legible
     * @param hour Hora (0-23)
     * @param minute Minutos (0-59)
     * @param use24HourFormat Si es true, usa formato 24h, si no, usa 12h (AM/PM)
     * @return Hora formateada (ej. "08:30" o "8:30 AM")
     */
    private String formatTime(int hour, int minute, boolean use24HourFormat) {
        if (use24HourFormat) {
            // Formato 24 horas: "08:30"
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
        } else {
            // Formato 12 horas: "8:30 AM"
            int displayHour = hour % 12;
            if (displayHour == 0) displayHour = 12; // Las 0 horas en formato 12h son las 12 AM
            String amPm = hour >= 12 ? "PM" : "AM";
            return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm);
        }
    }
    
    private void updateStatusIndicator(ViewHolder holder, Schedule schedule) {
        long now = System.currentTimeMillis();
        
        if (!schedule.isActive()) {
            // Horario desactivado
            holder.ivStatus.setImageResource(R.drawable.ic_schedule_disabled);
            holder.ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.colorTextSecondary));
            holder.tvStatusText.setText("Desactivado");
            return;
        }
        
        // Usar el campo status como fuente principal
        String status = schedule.getStatus();
        
        // También considerar los campos antiguos para compatibilidad con datos existentes
        if ("taken".equals(status) || schedule.isTakingConfirmed()) {
            holder.ivStatus.setImageResource(R.drawable.ic_checkbox_on);
            holder.ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.colorSuccess));
            
            // Mostrar cuándo fue tomado
            long takenTime = schedule.getStatusUpdatedAt() > 0 ? 
                            schedule.getStatusUpdatedAt() : schedule.getLastTaken();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String timeLabel = sdf.format(new Date(takenTime));
            holder.tvStatusText.setText("Tomado a las " + timeLabel);
        } 
        else if ("dispensed".equals(status) || schedule.wasRecentlyDispensed()) {
            holder.ivStatus.setImageResource(R.drawable.ic_dispensed);
            holder.ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.colorSuccess));
            holder.tvStatusText.setText("Dispensado");
        }
        else if ("missed".equals(status) || (schedule.getNextScheduled() < now && !schedule.isTakingConfirmed())) {
            holder.ivStatus.setImageResource(R.drawable.ic_schedule_missed);
            holder.ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.colorError));
            holder.tvStatusText.setText("Pendiente (retrasado)");
        }
        else {
            // Estado programado o por defecto
            holder.ivStatus.setImageResource(R.drawable.ic_schedule_pending);
            holder.ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.colorPending));
            
            // Calcular tiempo restante
            String countdown = formatCountdown(schedule.getNextScheduled() - now);
            holder.tvStatusText.setText(countdown);
        }
    }
    
    /**
     * Formatea un tiempo en milisegundos a una cadena de cuenta regresiva concisa
     * @param timeInMillis Tiempo en milisegundos
     * @return Texto formateado (ej: "En 2d 3h", "En 45m", etc.)
     */
    private String formatCountdown(long timeInMillis) {
        if (timeInMillis <= 0) {
            return "Ahora";
        }
        
        // Constantes de tiempo en milisegundos
        final long MINUTE_IN_MILLIS = 60 * 1000;
        final long HOUR_IN_MILLIS = 60 * MINUTE_IN_MILLIS;
        final long DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS;
        
        StringBuilder result = new StringBuilder("En ");
        
        // Calcular días, horas y minutos
        long days = timeInMillis / DAY_IN_MILLIS;
        timeInMillis %= DAY_IN_MILLIS;
        
        long hours = timeInMillis / HOUR_IN_MILLIS;
        timeInMillis %= HOUR_IN_MILLIS;
        
        long minutes = timeInMillis / MINUTE_IN_MILLIS;
        
        // Usar formato abreviado para evitar superposiciones
        if (days > 0) {
            result.append(days).append("d");
            
            // Si faltan más de 2 días, no mostrar las horas
            if (days <= 2 && hours > 0) {
                result.append(" ").append(hours).append("h");
            }
        } else if (hours > 0) {
            result.append(hours).append("h");
            
            // Añadir minutos solo si son relevantes
            if (hours < 10 && minutes > 0) {
                result.append(" ").append(minutes).append("m");
            }
        } else if (minutes > 0) {
            result.append(minutes).append("m");
        } else {
            result = new StringBuilder("<1m");
        }
        
        return result.toString();
    }
}