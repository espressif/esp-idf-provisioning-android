package com.espressif.ui.utils;

import com.espressif.ui.models.Medication;
import com.espressif.ui.models.MedicationType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clase para gestionar la asignación y liberación de compartimentos del dispensador.
 * - Compartimentos 1-3: Para píldoras
 * - Compartimento 4: Para líquidos
 */
public class CompartmentManager {
    
    // Singleton
    private static CompartmentManager instance;
    
    // Mapa que asocia IDs de medicamentos con números de compartimento
    private final Map<String, Integer> medicationCompartments;
    
    // Estado de ocupación de los compartimentos (true = ocupado)
    private final boolean[] occupiedCompartments;
    
    // Cantidad total de compartimentos (3 para píldoras, 1 para líquidos)
    private static final int TOTAL_PILL_COMPARTMENTS = 3;
    private static final int TOTAL_LIQUID_COMPARTMENTS = 1;
    private static final int TOTAL_COMPARTMENTS = TOTAL_PILL_COMPARTMENTS + TOTAL_LIQUID_COMPARTMENTS;
    
    // Constructor privado (patrón Singleton)
    private CompartmentManager() {
        medicationCompartments = new HashMap<>();
        occupiedCompartments = new boolean[TOTAL_COMPARTMENTS + 1]; // +1 porque los compartimentos se numeran desde 1
    }
    
    // Método para obtener la instancia única
    public static synchronized CompartmentManager getInstance() {
        if (instance == null) {
            instance = new CompartmentManager();
        }
        return instance;
    }
    
    // Actualiza el estado de ocupación basado en la lista actual de medicamentos
    public void refreshOccupation(List<Medication> medications) {
        // Resetear todos los compartimentos a desocupados
        for (int i = 1; i <= TOTAL_COMPARTMENTS; i++) {
            occupiedCompartments[i] = false;
        }
        medicationCompartments.clear();
        
        // Marcar como ocupados los compartimentos de los medicamentos existentes
        if (medications != null) {
            for (Medication medication : medications) {
                int compartment = medication.getCompartmentNumber();
                String medicationId = medication.getId();
                
                if (compartment > 0 && compartment <= TOTAL_COMPARTMENTS && medicationId != null) {
                    occupiedCompartments[compartment] = true;
                    medicationCompartments.put(medicationId, compartment);
                }
            }
        }
    }
    
    // Obtiene los compartimentos disponibles según el tipo de medicamento
    public List<Integer> getAvailableCompartments(String medicationType) {
        List<Integer> availableCompartments = new ArrayList<>();
        
        if (medicationType.equals(MedicationType.PILL)) {
            // Compartimentos 1-3 para píldoras
            for (int i = 1; i <= TOTAL_PILL_COMPARTMENTS; i++) {
                if (!occupiedCompartments[i]) {
                    availableCompartments.add(i);
                }
            }
        } else if (medicationType.equals(MedicationType.LIQUID)) {
            // Compartimento 4 para líquidos
            int liquidCompartment = TOTAL_PILL_COMPARTMENTS + 1; // Compartimento 4
            if (!occupiedCompartments[liquidCompartment]) {
                availableCompartments.add(liquidCompartment);
            }
        }
        
        return availableCompartments;
    }
    
    // Marca un compartimento como ocupado
    public void occupyCompartment(int compartmentNumber, String medicationId) {
        if (compartmentNumber > 0 && compartmentNumber <= TOTAL_COMPARTMENTS && medicationId != null) {
            occupiedCompartments[compartmentNumber] = true;
            medicationCompartments.put(medicationId, compartmentNumber);
        }
    }
    
    // Libera un compartimento específico
    public void freeCompartment(int compartmentNumber) {
        if (compartmentNumber > 0 && compartmentNumber <= TOTAL_COMPARTMENTS) {
            occupiedCompartments[compartmentNumber] = false;
            
            // También eliminar cualquier medicamento que esté usando este compartimento
            String medicationToRemove = null;
            for (Map.Entry<String, Integer> entry : medicationCompartments.entrySet()) {
                if (entry.getValue() == compartmentNumber) {
                    medicationToRemove = entry.getKey();
                    break;
                }
            }
            
            if (medicationToRemove != null) {
                medicationCompartments.remove(medicationToRemove);
            }
        }
    }
    
    // Libera el compartimento asignado a un medicamento específico
    public void freeMedicationCompartment(String medicationId) {
        if (medicationId != null && medicationCompartments.containsKey(medicationId)) {
            int compartment = medicationCompartments.get(medicationId);
            occupiedCompartments[compartment] = false;
            medicationCompartments.remove(medicationId);
        }
    }
    
    // Verificar si un compartimento está ocupado
    public boolean isCompartmentOccupied(int compartmentNumber) {
        return compartmentNumber > 0 && compartmentNumber <= TOTAL_COMPARTMENTS && 
               occupiedCompartments[compartmentNumber];
    }
    
    // Obtener el compartimento asignado a un medicamento
    public int getMedicationCompartment(String medicationId) {
        return medicationCompartments.getOrDefault(medicationId, -1);
    }

    /**
     * Verifica si hay compartimentos disponibles para un tipo específico de medicamento
     * @param medicationType Tipo de medicamento (PILL o LIQUID)
     * @return true si hay al menos un compartimento disponible
     */
    public boolean hasAvailableCompartments(String medicationType) {
        return !getAvailableCompartments(medicationType).isEmpty();
    }

    /**
     * Verifica si un compartimento está disponible para un tipo específico de medicamento
     * @param compartmentNumber Número de compartimento a verificar
     * @param medicationType Tipo de medicamento (PILL o LIQUID)
     * @return true si el compartimento está disponible para ese tipo de medicamento
     */
    public boolean isCompartmentAvailableForType(int compartmentNumber, String medicationType) {
        // Verificar si es un número de compartimento válido
        if (compartmentNumber <= 0 || compartmentNumber > TOTAL_COMPARTMENTS) {
            return false;
        }
        
        // Verificar si el compartimento ya está ocupado
        if (occupiedCompartments[compartmentNumber]) {
            return false;
        }
        
        // Verificar compatibilidad con el tipo
        if (medicationType.equals(MedicationType.PILL)) {
            return compartmentNumber <= TOTAL_PILL_COMPARTMENTS;
        } else if (medicationType.equals(MedicationType.LIQUID)) {
            return compartmentNumber == TOTAL_PILL_COMPARTMENTS + 1;
        }
        
        return false;
    }
}