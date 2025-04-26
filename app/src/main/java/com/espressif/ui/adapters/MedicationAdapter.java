package com.espressif.ui.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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

    // Añadir este método al MedicationAdapter para actualizar un medicamento específico
    public void updateMedication(Medication updatedMedication) {
        if (updatedMedication == null || updatedMedication.getId() == null) {
            return;
        }
        
        // Buscar y actualizar este medicamento específico
        for (int i = 0; i < medications.size(); i++) {
            Medication medication = medications.get(i);
            if (medication.getId() != null && medication.getId().equals(updatedMedication.getId())) {
                // Reemplazar el medicamento con la versión actualizada
                medications.set(i, updatedMedication);
                
                // Notificar cambios solo en este elemento
                notifyItemChanged(i);
                return;
            }
        }
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

        // Reemplazar TODO el método bind() con esta versión corregida:
        public void bind(Medication medication) {
            // Agregar log al inicio para diagnóstico
            Log.d("MedicationAdapter", "📌 bind() llamado para: " + medication.getName() + 
                  " | ID: " + medication.getId());
            
            tvName.setText(medication.getName());
            
            // Formatear texto de dosificación
            String dosageText = String.format("%.1f %s", medication.getAmount(), 
                                MedicationType.getUnitLabel(medication.getUnit()));
            tvDosage.setText(dosageText);
            
            // Configurar indicador de compartimento
            if (medication.getCompartmentNumber() > 0) {
                tvCompartment.setVisibility(View.VISIBLE);
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

            // AQUÍ: Mostrar dosis restantes
            tvRemainingPills.setVisibility(View.VISIBLE);
            
            // Calcular dosis restantes y mostrarlas según el tipo
            int remainingDoses = medication.calculateRemainingDoses();
            
            if (MedicationType.PILL.equals(medication.getType()) || "pill".equalsIgnoreCase(medication.getType())) {
                // Para píldoras: mostrar dosis y cantidad de pastillas
                if (medication.getPillsPerDose() > 0) {
                    tvRemainingPills.setText(String.format("Quedan %d dosis (%d pastillas)", 
                        remainingDoses, medication.getTotalPills()));
                } else {
                    tvRemainingPills.setText(String.format("Disponible: %d pastillas", 
                        medication.getTotalPills()));
                }
            } else if (MedicationType.LIQUID.equals(medication.getType()) || "liquid".equalsIgnoreCase(medication.getType())) {
                // Para líquidos: mostrar dosis y volumen
                if (medication.getDoseVolume() > 0) {
                    tvRemainingPills.setText(String.format("Quedan %d dosis (%d ml)", 
                        remainingDoses, medication.getTotalVolume()));
                } else {
                    tvRemainingPills.setText(String.format("Disponible: %d ml", 
                        medication.getTotalVolume()));
                }
            } else {
                // Caso por defecto
                tvRemainingPills.setText(String.format("Dosis restantes: %d", remainingDoses));
            }
            
            // Cambiar color según cantidad restante
            if (remainingDoses <= 2) {
                tvRemainingPills.setTextColor(ContextCompat.getColor(context, R.color.colorRed));
            } else {
                tvRemainingPills.setTextColor(ContextCompat.getColor(context, R.color.design_default_color_secondary_variant));
            }
            
            // Log de finalización
            Log.d("MedicationAdapter", "✅ UI actualizada para: " + medication.getName() + 
                  " con " + remainingDoses + " dosis restantes");
        }

        // Añadir un getter para el adaptador de horarios en ViewHolder
        public ScheduleAdapter getScheduleAdapter() {
            return scheduleAdapter;
        }
    }
}