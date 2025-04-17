package com.espressif.ui.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Clase de utilidad para formatear fechas y horas
 */
public class DateTimeUtils {
    
    private static final SimpleDateFormat timeFormat = 
            new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    private static final SimpleDateFormat dateTimeFormat = 
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    
    /**
     * Formatea una marca de tiempo en formato hora (HH:mm)
     */
    public static String formatTime(long timestamp) {
        return timeFormat.format(new Date(timestamp));
    }
    
    /**
     * Formatea una marca de tiempo en formato fecha y hora (dd/MM/yyyy HH:mm)
     */
    public static String formatDateTime(long timestamp) {
        return dateTimeFormat.format(new Date(timestamp));
    }
}