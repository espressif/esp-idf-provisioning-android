package com.espressif.ui.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.ui.models.Medication;
import com.espressif.ui.models.MedicationType;
import com.espressif.ui.models.Schedule;
import com.espressif.mediwatch.R;

import java.util.ArrayList;
import java.util.List;

public class MedicationAdapter extends RecyclerView.Adapter<MedicationAdapter.MedicationViewHolder> {

    private List<Medication> medications = new ArrayList<>();
    private final Context context;
    private final MedicationListener listener;

    // Añadir estas variables para manejar la actualización automática
    private final Handler autoUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoUpdateRunnable;
    private boolean autoUpdateEnabled = false;
    private static final long UPDATE_INTERVAL_MS = 60000; // Actualizar cada minuto

    // Añadir esta propiedad a la clase principal
    private ScheduleListenerProvider scheduleListenerProvider;

    public interface MedicationListener {
        void onMedicationClick(Medication medication);
        void onEditMedicationClick(Medication medication);
        void onDeleteMedicationClick(Medication medication);
        void onAddScheduleClick(Medication medication);
    }

    // Añadir esta interfaz para proporcionar listeners de Schedule
    public interface ScheduleListenerProvider {
        ScheduleAdapter.ScheduleListener provideScheduleListener(Medication medication);
    }

    public MedicationAdapter(Context context, MedicationListener listener) {
        this.context = context;
        this.listener = listener;

        // Inicializar el Runnable para actualización automática
        autoUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                // Notificar cambios para refrescar todos los items
                notifyDataSetChanged();

                // Programar la próxima actualización si está habilitado
                if (autoUpdateEnabled) {
                    autoUpdateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        };
    }

    @NonNull
    @Override
    public MedicationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medication, parent, false);
        return new MedicationViewHolder(view);
    }

    // Modificar el método onBindViewHolder para usar el provider
    @Override
    public void onBindViewHolder(@NonNull MedicationViewHolder holder, int position) {
        Medication medication = medications.get(position);
        holder.bind(medication);
        
        // Configurar el listener para los horarios si existe el provider
        if (scheduleListenerProvider != null) {
            ScheduleAdapter scheduleAdapter = holder.getScheduleAdapter();
            scheduleAdapter.setListener(scheduleListenerProvider.provideScheduleListener(medication));
        }
    }

    @Override
    public int getItemCount() {
        return medications.size();
    }

    public void setMedications(List<Medication> medications) {
        this.medications = medications != null ? medications : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<Medication> getMedications() {
        return medications;
    }

    public Medication getMedicationAt(int position) {
        if (position >= 0 && position < medications.size()) {
            return medications.get(position);
        }
        return null;
    }

    /**
     * Comienza la actualización automática de los horarios
     */
    public void startAutoUpdate() {
        if (!autoUpdateEnabled) {
            autoUpdateEnabled = true;
            autoUpdateHandler.removeCallbacks(autoUpdateRunnable); // Remover cualquier callback pendiente
            autoUpdateHandler.post(autoUpdateRunnable); // Iniciar inmediatamente
        }
    }

    /**
     * Detiene la actualización automática
     */
    public void stopAutoUpdate() {
        autoUpdateEnabled = false;
        autoUpdateHandler.removeCallbacks(autoUpdateRunnable);
    }

    /**
     * Debe llamarse en onDestroy() de la Activity/Fragment para liberar recursos
     */
    public void release() {
        stopAutoUpdate();
        autoUpdateHandler.removeCallbacksAndMessages(null);
    }

    // Añadir este método a la clase principal
    public void setScheduleListenerProvider(ScheduleListenerProvider provider) {
        this.scheduleListenerProvider = provider;
    }

    class MedicationViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvDosage;
        private final TextView tvCompartment;
        private final ImageView ivType;
        private final View typeIndicator;
        private final RecyclerView recyclerSchedules;
        private final Button btnAddSchedule;
        private final Button btnEdit;
        private final Button btnDelete;
        private final TextView tvRemainingPills;
        
        private final ScheduleAdapter scheduleAdapter;

        public MedicationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_medication_name);
            tvDosage = itemView.findViewById(R.id.tv_medication_dosage);
            tvCompartment = itemView.findViewById(R.id.tv_compartment);
            ivType = itemView.findViewById(R.id.iv_medication_type);
            typeIndicator = itemView.findViewById(R.id.view_type_indicator);
            recyclerSchedules = itemView.findViewById(R.id.recycler_schedules);
            btnAddSchedule = itemView.findViewById(R.id.btn_add_schedule);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            tvRemainingPills = itemView.findViewById(R.id.tv_dosage_remaining);
            
            // Configurar el RecyclerView anidado para los horarios
            recyclerSchedules.setLayoutManager(new LinearLayoutManager(context));
            scheduleAdapter = new ScheduleAdapter(context);
            recyclerSchedules.setAdapter(scheduleAdapter);
            recyclerSchedules.setNestedScrollingEnabled(false);
            
            // Configurar listeners
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMedicationClick(medications.get(position));
                }
            });
            
            btnEdit.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEditMedicationClick(medications.get(position));
                }
            });
            
            btnDelete.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onDeleteMedicationClick(medications.get(position));
                }
            });
            
            btnAddSchedule.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onAddScheduleClick(medications.get(position));
                }
            });
        }

        public void bind(Medication medication) {
            tvName.setText(medication.getName());
            
            // Formatear texto de dosificación
            String dosageText = String.format("%.1f %s", medication.getAmount(), 
                                MedicationType.getUnitLabel(medication.getUnit()));
            tvDosage.setText(dosageText);
            
            // Configurar indicador de compartimento
            if (medication.getCompartmentNumber() > 0) {
                tvCompartment.setVisibility(View.VISIBLE);
                
                // Código corregido - Asegurar que siempre muestre el número de compartimento correcto
                String compartmentText = "C" + medication.getCompartmentNumber();
                tvCompartment.setText(compartmentText);
                
                // Cambia el color del fondo según el tipo
                GradientDrawable background = (GradientDrawable) tvCompartment.getBackground();
                if (background != null) {
                    background.setColor(MedicationType.getColorForType(medication.getType()));
                }
            } else {
                tvCompartment.setVisibility(View.INVISIBLE);
            }
            
            // Configurar icono según el tipo
            if (MedicationType.LIQUID.equals(medication.getType())) {
                ivType.setImageResource(R.drawable.ic_liquid);
            } else {
                ivType.setImageResource(R.drawable.ic_pill);
            }
            
            // Configurar color del indicador según el tipo
            int color = MedicationType.getColorForType(medication.getType());
            typeIndicator.setBackgroundColor(color);
            ivType.setColorFilter(color);
            
            // Configurar horarios
            scheduleAdapter.setSchedules(medication.getScheduleList());

            // Mostrar información de pastillas/dosis restantes
            if (MedicationType.PILL.equals(medication.getType())) {
                int totalPills = medication.getTotalPills();
                int pillsPerDose = medication.getPillsPerDose();
                
                if (pillsPerDose > 0) {
                    int doses = totalPills / pillsPerDose;
                    String remainingText = String.format("Quedan %d pastillas (%d dosis)", 
                                                        totalPills, doses);
                    tvRemainingPills.setText(remainingText);
                    tvRemainingPills.setVisibility(View.VISIBLE);
                } else {
                    tvRemainingPills.setVisibility(View.GONE);
                }
            } else {
                int doses = medication.getRemainingDoses();
                if (doses > 0) {
                    tvRemainingPills.setText("Quedan " + doses + " dosis");
                    tvRemainingPills.setVisibility(View.VISIBLE);
                } else {
                    tvRemainingPills.setVisibility(View.GONE);
                }
            }
        }

        // Añadir un getter para el adaptador de horarios en ViewHolder
        public ScheduleAdapter getScheduleAdapter() {
            return scheduleAdapter;
        }
    }
}