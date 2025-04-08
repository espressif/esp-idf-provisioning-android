package com.espressif.ui.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.espressif.mediwatch.R;
import com.espressif.ui.models.Medication;
import com.espressif.ui.models.MedicationType;
import com.espressif.ui.utils.CompartmentManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.UUID;

public class DialogMedication extends DialogFragment {

    public interface MedicationDialogListener {
        void onMedicationSaved(Medication medication);
        void onDialogCancelled();
    }

    private MedicationDialogListener listener;
    private Medication editingMedication;
    private boolean isEditMode = false;

    // Vistas del diálogo
    private TextView tvDialogTitle;
    private TextInputLayout tilMedicationName;
    private TextInputEditText etMedicationName;
    private RadioGroup rgMedicationType;
    private RadioButton rbPill;
    private RadioButton rbLiquid;
    private TextInputLayout tilAmount;
    private TextInputEditText etAmount;
    private TextInputLayout tilNotes;
    private TextInputEditText etNotes;
    private RadioGroup rgCompartment;
    private MaterialButton btnCancel;
    private MaterialButton btnSave;

    // Constantes para el número total de compartimentos por tipo
    private static final int MAX_PILL_COMPARTMENTS = 3;
    private static final int MAX_LIQUID_COMPARTMENTS = 1;

    private CompartmentManager compartmentManager;

    public static DialogMedication newInstance() {
        return new DialogMedication();
    }

    public static DialogMedication newInstance(Medication medication) {
        DialogMedication dialog = new DialogMedication();
        dialog.editingMedication = medication;
        dialog.isEditMode = true;
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            if (getParentFragment() != null) {
                listener = (MedicationDialogListener) getParentFragment();
            } else {
                listener = (MedicationDialogListener) context;
            }
        } catch (ClassCastException e) {
            throw new ClassCastException("Calling fragment must implement MedicationDialogListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_medication, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar el gestor de compartimentos
        compartmentManager = CompartmentManager.getInstance();

        // Inicializar vistas
        initViews(view);

        // Escuchar cambios en el tipo de medicamento para actualizar compartimentos
        setupTypeChangeListener();

        // Configurar botones
        setupButtons();

        // Si estamos en modo edición, rellenar con datos del medicamento
        if (isEditMode && editingMedication != null) {
            fillDataForEdit();
        } else {
            // En modo creación, mostrar los compartimentos para pastillas por defecto
            updateCompartmentOptions(MedicationType.PILL);
        }
    }

    private void initViews(View view) {
        tvDialogTitle = view.findViewById(R.id.tv_dialog_title);
        tilMedicationName = view.findViewById(R.id.til_medication_name);
        etMedicationName = view.findViewById(R.id.et_medication_name);
        rgMedicationType = view.findViewById(R.id.rg_medication_type);
        rbPill = view.findViewById(R.id.rb_pill);
        rbLiquid = view.findViewById(R.id.rb_liquid);
        tilAmount = view.findViewById(R.id.til_amount);
        etAmount = view.findViewById(R.id.et_amount);
        tilNotes = view.findViewById(R.id.til_notes);
        etNotes = view.findViewById(R.id.et_notes);
        rgCompartment = view.findViewById(R.id.rg_compartment);
        btnCancel = view.findViewById(R.id.btn_cancel);
        btnSave = view.findViewById(R.id.btn_save);

        // Configurar título según modo
        tvDialogTitle.setText(isEditMode ? R.string.edit_medication : R.string.add_medication);
        
        // Ajustar etiqueta para el campo de cantidad según el tipo seleccionado
        updateAmountFieldLabel();
        
        // Ocultar el campo de dosis totales si existe (para asegurarnos)
        View totalDosesView = view.findViewById(R.id.til_total_doses);
        if (totalDosesView != null) {
            totalDosesView.setVisibility(View.GONE);
        }
    }

    private void updateAmountFieldLabel() {
        // Cambiar el label del campo de cantidad según el tipo
        if (rbPill.isChecked()) {
            tilAmount.setHint("Número de pastillas/cápsulas por dosis");
        } else {
            tilAmount.setHint("Cantidad por dosis (ml)");
        }
    }

    private void setupTypeChangeListener() {
        rgMedicationType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_pill) {
                updateCompartmentOptions(MedicationType.PILL);
                updateAmountFieldLabel();
            } else if (checkedId == R.id.rb_liquid) {
                updateCompartmentOptions(MedicationType.LIQUID);
                updateAmountFieldLabel();
            }
        });
    }

    private void updateCompartmentOptions(String type) {
        rgCompartment.removeAllViews();
        
        // Obtener compartimentos disponibles según el tipo
        List<Integer> availableCompartments;
        
        if (isEditMode && editingMedication != null) {
            // En modo edición, incluir siempre el compartimento actual como disponible
            availableCompartments = compartmentManager.getAvailableCompartments(type);
            int currentCompartment = editingMedication.getCompartmentNumber();
            
            if (currentCompartment > 0 && 
                !availableCompartments.contains(currentCompartment) && 
                MedicationType.isCompatibleWithCompartment(type, currentCompartment)) {
                availableCompartments.add(currentCompartment);
            }
        } else {
            // En modo creación, mostrar solo compartimentos libres
            availableCompartments = compartmentManager.getAvailableCompartments(type);
        }
        
        // Si no hay compartimentos disponibles, mostrar mensaje
        if (availableCompartments.isEmpty()) {
            TextView tvNoCompartments = new TextView(requireContext());
            tvNoCompartments.setText(type.equals(MedicationType.PILL) ? 
                    "No hay compartimentos para pastillas disponibles" : 
                    "El compartimento para líquidos está ocupado");
            tvNoCompartments.setTextAppearance(R.style.TextAppearance_MaterialComponents_Body2);
            tvNoCompartments.setTextColor(getResources().getColor(R.color.colorError));
            rgCompartment.addView(tvNoCompartments);
            
            // Deshabilitar botón de guardar
            btnSave.setEnabled(false);
            return;
        }
        
        // Habilitar botón de guardar
        btnSave.setEnabled(true);
        
        // Añadir opciones para compartimentos disponibles
        for (int compartmentNumber : availableCompartments) {
            addCompartmentOption(compartmentNumber);
        }
        
        // Seleccionar el compartimento adecuado
        selectCurrentCompartment();
    }

    // Método auxiliar para crear el RadioButton de compartimento
    private void addCompartmentOption(int compartmentNumber) {
        MaterialRadioButton radioButton = new MaterialRadioButton(requireContext());
        radioButton.setId(View.generateViewId());
        radioButton.setText("Compartimento " + compartmentNumber);
        radioButton.setTag(compartmentNumber); // Guardamos el número para identificarlo después
        
        // Configurar el layout para que los radiobuttons se distribuyan equitativamente
        RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 8, 0, 8); // Añadir algo de espacio entre opciones
        rgCompartment.addView(radioButton, params);
        
        // Si estamos editando, seleccionar el compartimento actual
        if (isEditMode && editingMedication != null && 
                editingMedication.getCompartmentNumber() == compartmentNumber) {
            radioButton.setChecked(true);
        }
    }

    // Método para seleccionar el compartimento actual o por defecto
    private void selectCurrentCompartment() {
        // Si no hay botones, no hay nada que seleccionar
        if (rgCompartment.getChildCount() == 0) return;
        
        // Buscar el botón que coincide con el compartimento en modo edición
        boolean foundSelected = false;
        if (isEditMode && editingMedication != null) {
            for (int i = 0; i < rgCompartment.getChildCount(); i++) {
                RadioButton rb = (RadioButton) rgCompartment.getChildAt(i);
                int compartmentNumber = (int) rb.getTag();
                if (compartmentNumber == editingMedication.getCompartmentNumber()) {
                    rb.setChecked(true);
                    foundSelected = true;
                    break;
                }
            }
        }
        
        // Si no se encontró (o no estamos en modo edición), seleccionar el primero
        if (!foundSelected) {
            ((RadioButton) rgCompartment.getChildAt(0)).setChecked(true);
        }
    }

    private void setupButtons() {
        btnCancel.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDialogCancelled();
            }
            dismiss();
        });

        btnSave.setOnClickListener(v -> {
            if (validateInputs()) {
                saveMedication();
            }
        });
    }

    private boolean validateInputs() {
        boolean isValid = true;

        // Validar nombre
        String name = etMedicationName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            tilMedicationName.setError("El nombre es obligatorio");
            isValid = false;
        } else {
            tilMedicationName.setError(null);
        }

        // Validar cantidad
        String amountStr = etAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            tilAmount.setError("La cantidad es obligatoria");
            isValid = false;
        } else {
            try {
                float amount = Float.parseFloat(amountStr);
                if (amount <= 0) {
                    tilAmount.setError("La cantidad debe ser mayor que cero");
                    isValid = false;
                } else {
                    tilAmount.setError(null);
                }
            } catch (NumberFormatException e) {
                tilAmount.setError("Valor no válido");
                isValid = false;
            }
        }

        return isValid;
    }

    private void fillDataForEdit() {
        // Establecer el nombre
        etMedicationName.setText(editingMedication.getName());
        
        // Establecer el tipo
        if (MedicationType.PILL.equals(editingMedication.getType())) {
            rbPill.setChecked(true);
        } else {
            rbLiquid.setChecked(true);
        }
        
        // Establecer la cantidad
        etAmount.setText(String.valueOf(editingMedication.getAmount()));
        
        // Establecer notas
        etNotes.setText(editingMedication.getNotes());
        
        // Establecer compartimento (se maneja en updateCompartmentOptions)
        updateCompartmentOptions(editingMedication.getType());
    }

    private void saveMedication() {
        String name = etMedicationName.getText().toString().trim();
        float amount = Float.parseFloat(etAmount.getText().toString().trim());
        String notes = etNotes.getText().toString().trim();
        
        // Determinar el tipo seleccionado
        String type = rbPill.isChecked() ? MedicationType.PILL : MedicationType.LIQUID;
        
        // Determinar el compartimento seleccionado
        int selectedCompartment = 0;
        for (int i = 0; i < rgCompartment.getChildCount(); i++) {
            RadioButton rb = (RadioButton) rgCompartment.getChildAt(i);
            if (rb.isChecked()) {
                // Obtenemos el valor del tag que representa el número real del compartimento
                selectedCompartment = (int) rb.getTag();
                break;
            }
        }
        
        // Crear o actualizar el objeto Medication
        Medication medication;
        if (isEditMode && editingMedication != null) {
            // Actualizar medicamento existente
            medication = editingMedication;
            medication.setName(name);
            medication.setType(type);
            medication.setAmount(amount);
            medication.setUnit(type.equals(MedicationType.PILL) ? "" : "ml"); // Sin unidad para pastillas, ml para líquidos
            medication.setNotes(notes);
            medication.setCompartmentNumber(selectedCompartment);
            // Mantener el valor de dosis restantes en edición
        } else {
            // Crear nuevo medicamento
            medication = new Medication();
            medication.setId(UUID.randomUUID().toString());
            medication.setName(name);
            medication.setType(type);
            medication.setAmount(amount);
            medication.setUnit(type.equals(MedicationType.PILL) ? "" : "ml"); // Sin unidad para pastillas, ml para líquidos
            medication.setNotes(notes);
            medication.setCompartmentNumber(selectedCompartment);
            medication.setRemainingDoses(0); // Inicializar en 0, se actualizará en otro lugar
        }
        
        // Si estamos editando, liberar el compartimento anterior si cambió
        if (isEditMode && editingMedication != null) {
            int oldCompartment = editingMedication.getCompartmentNumber();
            if (oldCompartment != selectedCompartment) {
                compartmentManager.freeCompartment(oldCompartment);
            }
        }
        
        // Marcar el nuevo compartimento como ocupado
        compartmentManager.occupyCompartment(selectedCompartment, medication.getId());
        
        // Notificar al listener
        if (listener != null) {
            listener.onMedicationSaved(medication);
        }
        
        // Cerrar el diálogo
        dismiss();
    }

    // Opcional: Añadir un método para actualizar la UI con un mensaje
    private void showMessage(String message) {
        View rootView = getView();
        if (rootView != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
        }
    }
}