package com.espressif.data.source.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.espressif.AppConstants;

public class SharedPreferencesHelper {

    private static SharedPreferencesHelper instance;
    private final SharedPreferences preferences;
    private final SharedPreferences.Editor editor;

    private SharedPreferencesHelper(Context context) {
        preferences = context.getSharedPreferences(AppConstants.PREF_NAME_USER, Context.MODE_PRIVATE);
        editor = preferences.edit();
    }

    public static synchronized SharedPreferencesHelper getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPreferencesHelper(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Métodos para userType
     */
    public void saveUserType(String userType) {
        Log.d("SharedPrefsHelper", "Guardando tipo de usuario: " + userType);
        preferences.edit().putString(AppConstants.KEY_USER_TYPE, userType).apply();
    }

    public String getUserType() {
        String userType = preferences.getString(AppConstants.KEY_USER_TYPE, null);
        Log.d("SharedPrefsHelper", "Obteniendo tipo de usuario: " + userType);
        return userType;
    }

    /**
     * Métodos para estado de login
     */
    public void setLoggedIn(boolean isLoggedIn) {
        Log.d("SharedPrefsHelper", "Estableciendo estado de login: " + isLoggedIn);
        preferences.edit().putBoolean(AppConstants.KEY_IS_LOGGED_IN, isLoggedIn).apply();
    }

    public boolean isLoggedIn() {
        boolean isLoggedIn = preferences.getBoolean(AppConstants.KEY_IS_LOGGED_IN, false);
        Log.d("SharedPrefsHelper", "Verificando estado de login: " + isLoggedIn);
        return isLoggedIn;
    }

    /**
     * Métodos para provisioning
     */
    public void setProvisioningCompleted(boolean completed) {
        Log.d("SharedPrefsHelper", "Estableciendo provisioning completado: " + completed);
        preferences.edit().putBoolean(AppConstants.KEY_PROVISIONING_COMPLETED, completed).apply();
    }

    public boolean hasCompletedProvisioning() {
        boolean hasCompleted = preferences.getBoolean(AppConstants.KEY_PROVISIONING_COMPLETED, false);
        Log.d("SharedPrefsHelper", "Verificando provisioning completado: " + hasCompleted);
        return hasCompleted;
    }

    /**
     * Métodos para información del usuario
     */
    public void saveUserInfo(String name, String email) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(AppConstants.KEY_USER_NAME, name);
        editor.putString(AppConstants.KEY_USER_EMAIL, email);
        editor.apply();
    }

    public String getUserName() {
        return preferences.getString(AppConstants.KEY_USER_NAME, null);
    }

    public String getUserEmail() {
        return preferences.getString(AppConstants.KEY_USER_EMAIL, null);
    }

    /**
     * Métodos para IDs de paciente
     */
    
    /**
     * Guarda el ID único del paciente (para usuarios tipo paciente)
     * @param patientId ID único del paciente
     */
    public void savePatientId(String patientId) {
        if (patientId != null && !patientId.isEmpty()) {
            editor.putString(AppConstants.KEY_PATIENT_ID, patientId)
                 .putString(AppConstants.KEY_CONNECTED_PATIENT_ID, patientId)
                 .apply();
            Log.d("SharedPrefsHelper", "ID de paciente guardado: " + patientId);
        }
    }

    /**
     * Obtiene el ID único del paciente
     * @return El ID del paciente o null si no está disponible
     */
    public String getPatientId() {
        String id = preferences.getString(AppConstants.KEY_PATIENT_ID, null);
        // No devolver IDs inválidos
        return ("current_user_id".equals(id)) ? null : id;
    }

    public void clearPatientId() {
        editor.remove(AppConstants.KEY_PATIENT_ID)
             .remove(AppConstants.KEY_CONNECTED_PATIENT_ID)
             .apply();
    }

    /**
     * Guarda el ID del paciente al que está conectado un familiar
     * @param patientId ID del paciente al que se conectará
     */
    public void saveConnectedPatientId(String patientId) {
        // No guardar IDs inválidos
        if (patientId == null || patientId.isEmpty() || "current_user_id".equals(patientId)) {
            Log.w("SharedPrefsHelper", "Intento de guardar ID de paciente conectado inválido: " + patientId);
            return;
        }
        
        Log.d("SharedPrefsHelper", "Guardando ID de paciente conectado: " + patientId);
        preferences.edit().putString(AppConstants.KEY_CONNECTED_PATIENT_ID, patientId).apply();
    }

    /**
     * Obtiene el ID del paciente al que está conectado un familiar
     * @return ID del paciente conectado o null si no hay conexión
     */
    public String getConnectedPatientId() {
        String patientId = preferences.getString(AppConstants.KEY_CONNECTED_PATIENT_ID, null);
        
        // Si el valor almacenado es el valor problemático, devolver null
        if ("current_user_id".equals(patientId)) {
            Log.w("SharedPrefsHelper", "Se encontró un ID de paciente conectado inválido almacenado, devolviendo null");
            return null;
        }
        
        Log.d("SharedPrefsHelper", "Obteniendo ID de paciente conectado: " + patientId);
        return patientId;
    }

    /**
     * Guarda el email del paciente conectado (información adicional)
     */
    public void saveConnectedPatientEmail(String email) {
        preferences.edit().putString(AppConstants.KEY_CONNECTED_PATIENT_EMAIL, email).apply();
    }

    /**
     * Obtiene el email del paciente conectado
     */
    public String getConnectedPatientEmail() {
        return preferences.getString(AppConstants.KEY_CONNECTED_PATIENT_EMAIL, null);
    }

    /**
     * Guarda el nombre del paciente conectado
     */
    public void saveConnectedPatientName(String name) {
        preferences.edit().putString(AppConstants.KEY_CONNECTED_PATIENT_NAME, name).apply();
    }

    /**
     * Obtiene el nombre del paciente conectado
     */
    public String getConnectedPatientName() {
        return preferences.getString(AppConstants.KEY_CONNECTED_PATIENT_NAME, null);
    }

    /**
     * Guarda el ID del dispositivo conectado
     * @param deviceId ID del dispositivo
     */
    public void saveConnectedDeviceId(String deviceId) {
        Log.d("SharedPrefsHelper", "Guardando ID de dispositivo conectado: " + deviceId);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(AppConstants.KEY_CONNECTED_DEVICE_ID, deviceId);
        editor.apply();
    }

    /**
     * Obtiene el ID del dispositivo conectado
     * @return ID del dispositivo conectado o null si no hay ninguno
     */
    public String getConnectedDeviceId() {
        String deviceId = preferences.getString(AppConstants.KEY_CONNECTED_DEVICE_ID, null);
        Log.d("SharedPrefsHelper", "Obteniendo ID de dispositivo conectado: " + deviceId);
        return deviceId;
    }

    /**
     * Guarda el nombre del dispositivo ESP32 (PROV_XXXXXX)
     * @param deviceName Nombre único del dispositivo
     */
    public void saveDeviceName(String deviceName) {
        if (deviceName != null && !deviceName.isEmpty()) {
            Log.d("SharedPrefsHelper", "Guardando nombre de dispositivo ESP32: " + deviceName);
            editor.putString(AppConstants.KEY_DEVICE_NAME, deviceName).apply();
        }
    }

    /**
     * Obtiene el nombre del dispositivo ESP32
     * @return Nombre del dispositivo o null si no está configurado
     */
    public String getDeviceName() {
        String deviceName = preferences.getString(AppConstants.KEY_DEVICE_NAME, null);
        Log.d("SharedPrefsHelper", "Obteniendo nombre de dispositivo ESP32: " + deviceName);
        return deviceName;
    }

    /**
     * Limpiar datos de usuario (logout)
     */
    public void clearUserData() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(AppConstants.KEY_IS_LOGGED_IN);
        editor.remove(AppConstants.KEY_USER_NAME);
        editor.remove(AppConstants.KEY_USER_EMAIL);
        editor.remove(AppConstants.KEY_USER_TYPE);
        editor.remove(AppConstants.KEY_PATIENT_ID);
        editor.remove(AppConstants.KEY_CONNECTED_PATIENT_ID);
        editor.remove(AppConstants.KEY_CONNECTED_PATIENT_NAME);
        editor.remove(AppConstants.KEY_CONNECTED_PATIENT_EMAIL);
        editor.apply();
    }

    /**
     * Acceso directo al objeto SharedPreferences
     */
    public SharedPreferences getPreferences() {
        return preferences;
    }

    /**
     * Guarda un valor booleano genérico
     */
    public void putBoolean(String key, boolean value) {
        Log.d("SharedPrefsHelper", "Guardando booleano '" + key + "': " + value);
        preferences.edit().putBoolean(key, value).apply();
    }

    /**
     * Obtiene un valor booleano genérico
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        boolean value = preferences.getBoolean(key, defaultValue);
        Log.d("SharedPrefsHelper", "Obteniendo booleano '" + key + "': " + value);
        return value;
    }

    /**
     * Limpia TODOS los datos de preferencias (reset completo)
     */
    public void clearAllData() {
        Log.d("SharedPrefsHelper", "Borrando TODOS los datos de preferencias");
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
    }

    /**
     * Método utilitario para validar IDs de paciente
     * @param patientId ID a validar
     * @return true si el ID es válido, false en caso contrario
     */
    public boolean isValidPatientId(String patientId) {
        return patientId != null && !patientId.isEmpty() && !"current_user_id".equals(patientId);
    }
}