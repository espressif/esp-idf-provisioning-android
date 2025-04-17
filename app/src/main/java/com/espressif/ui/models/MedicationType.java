package com.espressif.ui.models;

/**
 * Constantes para los tipos de medicamentos y unidades disponibles.
 * Simplificado para reflejar las capacidades reales del dispensador:
 * - 3 compartimentos para pastillas/cápsulas
 * - 1 compartimento para líquidos
 */
public class MedicationType {
    
    // Tipos básicos de medicamentos según el dispensador
    public static final String PILL = "pill";       // Para cualquier tipo de pastilla/cápsula/tableta
    public static final String LIQUID = "liquid";   // Para medicamentos líquidos
    
    // Unidades de medida relevantes
    public static final String UNIT = "unit";       // Unidades (pastillas, cápsulas)
    public static final String MILLIGRAM = "mg";    // Para dosificación
    public static final String MILLILITER = "ml";   // Para líquidos
    
    // Colores asociados a cada tipo (para UI)
    public static final int COLOR_PILL = 0xFF4CAF50;    // Verde
    public static final int COLOR_LIQUID = 0xFF2196F3;  // Azul
    
    /**
     * Obtiene el color asociado a un tipo de medicamento
     */
    public static int getColorForType(String type) {
        if (LIQUID.equals(type)) {
            return COLOR_LIQUID;
        } else {
            return COLOR_PILL; // Por defecto, tratar como pastilla
        }
    }
    
    /**
     * Obtiene el nombre localizado para un tipo de medicamento
     */
    public static String getTypeLabel(String type) {
        if (LIQUID.equals(type)) {
            return "Líquido";
        } else {
            return "Pastilla/Cápsula";
        }
    }
    
    /**
     * Obtiene el nombre localizado para una unidad
     */
    public static String getUnitLabel(String unit) {
        switch (unit) {
            case UNIT:
                return "Unidades";
            case MILLIGRAM:
                return "mg";
            case MILLILITER:
                return "ml";
            default:
                return unit;
        }
    }
    
    /**
     * Verifica si un medicamento es compatible con el compartimento especificado.
     * Compartimentos 1-3 son para pastillas, compartimento 4 es para líquidos.
     */
    public static boolean isCompatibleWithCompartment(String type, int compartment) {
        if (compartment < 1 || compartment > 4) {
            return false; // Compartimento inválido
        }
        
        if (compartment <= 3) {
            // Compartimentos 1-3: sólo pastillas
            return PILL.equals(type);
        } else {
            // Compartimento 4: sólo líquidos
            return LIQUID.equals(type);
        }
    }
    
    /**
     * Devuelve el número máximo de compartimentos disponibles para un tipo
     */
    public static int getMaxCompartmentsForType(String type) {
        if (LIQUID.equals(type)) {
            return 1; // Solo hay un compartimento para líquidos (el #4)
        } else {
            return 3; // Hay tres compartimentos para pastillas (1, 2 y 3)
        }
    }
    
    /**
     * Devuelve el primer compartimento disponible para un tipo
     */
    public static int getFirstCompartmentForType(String type) {
        if (LIQUID.equals(type)) {
            return 4; // El compartimento de líquidos es el #4
        } else {
            return 1; // Los compartimentos de pastillas empiezan en el #1
        }
    }

    
}