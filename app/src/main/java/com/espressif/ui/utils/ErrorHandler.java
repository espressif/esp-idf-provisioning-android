package com.espressif.ui.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

/**
 * Manejador centralizado de errores para la aplicación
 */
public class ErrorHandler {
    private static final String TAG = "ErrorHandler";
    
    // Códigos de error internos
    public static final int ERROR_NETWORK = 100;
    public static final int ERROR_DATABASE = 200;
    public static final int ERROR_MQTT = 300;
    public static final int ERROR_VALIDATION = 400;
    public static final int ERROR_PERMISSION = 500;
    public static final int ERROR_UNKNOWN = 999;
    
    /**
     * Procesa y registra un error, devolviendo un mensaje formateado
     * @param context Contexto para el error (clase/módulo)
     * @param errorCode Código de error interno
     * @param message Mensaje descriptivo
     * @param exception Excepción opcional
     * @return Mensaje de error formateado
     */
    public static String handleError(@NonNull String context, int errorCode, String message, Throwable exception) {
        String formattedMessage = String.format("[%s] Error %d: %s", context, errorCode, message);
        
        if (exception != null) {
            Log.e(TAG, formattedMessage, exception);
        } else {
            Log.e(TAG, formattedMessage);
        }
        
        return formattedMessage;
    }
    
    /**
     * Procesa un error y lo publica en un LiveData
     * @param errorLiveData LiveData donde publicar el error
     * @param context Contexto para el error
     * @param errorCode Código de error interno
     * @param message Mensaje descriptivo
     * @param exception Excepción opcional
     */
    public static void publishError(MutableLiveData<String> errorLiveData, @NonNull String context, 
                                   int errorCode, String message, Throwable exception) {
        String formattedMessage = handleError(context, errorCode, message, exception);
        errorLiveData.postValue(formattedMessage);
    }
    
    /**
     * Maneja errores de repositorios de datos
     */
    public static String handleDatabaseError(String context, String operation, String errorMsg) {
        return handleError(context, ERROR_DATABASE, "Error en operación " + operation + ": " + errorMsg, null);
    }
    
    /**
     * Maneja errores de MQTT
     */
    public static String handleMqttError(String context, String action, Exception e) {
        return handleError(context, ERROR_MQTT, "Error en " + action, e);
    }
    
    /**
     * Maneja errores de validación de entrada
     */
    public static String handleValidationError(String context, String field, String message) {
        return handleError(context, ERROR_VALIDATION, "Validación fallida - " + field + ": " + message, null);
    }
}