package com.espressif.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
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

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder> {

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
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        Schedule schedule = schedules.get(position);
        holder.bind(schedule);
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

    class ScheduleViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTime;
        private final TextView tvDays;
        private final ImageView ivStatus;
        private final TextView tvStatusText;
        private final SwitchMaterial switchActive;

        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvDays = itemView.findViewById(R.id.tv_days);
            ivStatus = itemView.findViewById(R.id.iv_status);
            tvStatusText = itemView.findViewById(R.id.tv_status_text);
            switchActive = itemView.findViewById(R.id.switch_active);
            
            // Click listener para el item
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onScheduleClick(schedules.get(position));
                }
            });
            
            // Listener para el switch de activación
            switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    Schedule schedule = schedules.get(position);
                    if (schedule.isActive() != isChecked) {
                        schedule.setActive(isChecked);
                        listener.onScheduleActiveChanged(schedule, isChecked);
                    }
                }
            });
        }

        public void bind(Schedule schedule) {
            tvTime.setText(schedule.getFormattedTime());
            tvDays.setText(schedule.getFormattedDays());
            
            // Configurar el switch sin disparar el listener
            switchActive.setOnCheckedChangeListener(null);
            switchActive.setChecked(schedule.isActive());
            switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION && listener != null) {
                    Schedule currentSchedule = schedules.get(getAdapterPosition());
                    if (currentSchedule.isActive() != isChecked) {
                        currentSchedule.setActive(isChecked);
                        listener.onScheduleActiveChanged(currentSchedule, isChecked);
                    }
                }
            });
            
            // Estado de la toma
            updateStatusIndicator(schedule);
        }
        
        private void updateStatusIndicator(Schedule schedule) {
            long now = System.currentTimeMillis();
            
            if (!schedule.isActive()) {
                // Horario desactivado
                ivStatus.setImageResource(R.drawable.ic_schedule_disabled);
                ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.colorTextSecondary));
                tvStatusText.setText("Desactivado");
                return;
            }
            
            if (schedule.isDetectedBySensor()) {
                // Medicamento dispensado y detectado
                ivStatus.setImageResource(R.drawable.ic_checkbox_on);
                ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.colorSuccess));
                
                // Mostrar cuándo fue tomado
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                String timeLabel = sdf.format(new Date(schedule.getDetectedAt()));
                tvStatusText.setText("Tomado a las " + timeLabel);
                return;
            }
            
            if (schedule.isDispensed() && !schedule.isDetectedBySensor()) {
                // Dispensado pero no detectado
                ivStatus.setImageResource(R.drawable.ic_dispensed);
                ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.colorPending));
                tvStatusText.setText("Dispensado");
                return;
            }
            
            if (schedule.getNextScheduled() < now) {
                // Programado pero no tomado (atrasado)
                ivStatus.setImageResource(R.drawable.ic_schedule_missed);
                ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.colorError));
                tvStatusText.setText("Programado");
                return;
            }
            
            // Programado para el futuro
            ivStatus.setImageResource(R.drawable.ic_schedule_pending);
            ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.colorPending));
            
            // Mostrar cuándo está programado
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String timeLabel = sdf.format(new Date(schedule.getNextScheduled()));
            tvStatusText.setText("Próxima dosis: " + timeLabel);
        }
    }
}